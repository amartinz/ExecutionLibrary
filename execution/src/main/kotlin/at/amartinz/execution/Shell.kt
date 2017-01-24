/*
 * The MIT License
 *
 * Copyright (c) 2016 - 2017 Alexander Martinz <alex@amartinz.at>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package at.amartinz.execution

import at.amartinz.execution.exceptions.RootDeniedException
import timber.log.Timber
import java.io.*
import java.lang.reflect.Field
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

abstract class Shell @Throws(IOException::class, TimeoutException::class, RootDeniedException::class) constructor(var isRoot: Boolean) {
    companion object {
        val DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(5).toInt()

        private val ENCODING = "UTF-8"
        private val TOKEN = "Y#*N^W^T@#@G"

        fun fireAndBlockInternal(command: Command, shell: Shell): Command {
            return shell.add(command).waitFor()
        }

        fun fireAndBlockStringInternal(command: Command, shell: Shell): Command {
            return shell.add(command.setOutputType(Command.OUTPUT_STRING)).waitFor()
        }

        fun fireAndBlockStringNewlineInternal(command: Command, shell: Shell): Command {
            return shell.add(command.setOutputType(Command.OUTPUT_STRING_NEWLINE)).waitFor()
        }

        fun fireAndBlockListInternal(command: Command, shell: Shell): Command {
            return shell.add(command.setOutputType(Command.OUTPUT_LIST)).waitFor()
        }

        fun fireAndForgetInternal(command: Command, shell: Shell) {
            val addedCommand = shell.add(command)
            thread(name = "FireAndForget", block = {
                addedCommand.waitFor()
                shell.close()
            })
        }
    }

    var shellTimeout = DEFAULT_TIMEOUT

    var isCleaning: Boolean = false
    var isClosed: Boolean = false
    var isExecuting: Boolean = false

    var shouldClose: Boolean = false

    var error: String? = null

    private val process: Process

    private val inputStreamReader: BufferedReader
    private val errorStreamReader: BufferedReader
    private val outputStream: OutputStreamWriter

    private val commands = ArrayList<Command>()
    private val maxCommands = 1000
    private var totalExecuted: Int = 0
    private var toWrite: Int = 0
    private var totalRead: Int = 0
    private var toRead: Int = 0

    private val commandLock = ReentrantLock()
    private val commandCondition: Condition = commandLock.newCondition()

    init {
        val cmd = if (isRoot) "su" else "/system/bin/sh"

        process = Runtime.getRuntime().exec(cmd)
        inputStreamReader = BufferedReader(InputStreamReader(process.inputStream, ENCODING))
        errorStreamReader = BufferedReader(InputStreamReader(process.errorStream, ENCODING))
        outputStream = OutputStreamWriter(process.outputStream, ENCODING)

        startWorker()
    }

    fun add(command: String): Command {
        return add(Command(command))
    }

    fun add(command: Command): Command {
        if (shouldClose) {
            throw IllegalStateException("Unable to add commands to a closed shell")
        }

        while (isCleaning) {
            // wait until we are done cleaning
        }
        command.resetCommand()
        commands.add(command)

        notifyThreads()
        return command
    }

    protected fun notifyThreads() {
        thread(name = "NotifyThread", block = {
            commandLock.withLock {
                try {
                    commandCondition.signal()
                } catch (exc: Exception) {
                    Timber.e(exc, "Could not signal command condition")
                }
            }
        }).start()
    }

    fun close() {
        if (isClosed) {
            Timber.d("Shell already closed - %s", this)
            return
        }

        var count = 0
        while (isExecuting) {
            Timber.v("Waiting on shell to finish executing before closing...")
            count++

            // fail safe
            if (count > 10000) {
                break
            }
        }

        commandLock.withLock {
            this.shouldClose = true
            this.notifyThreads()
        }

        Timber.v("Shell closed - %s", this)
    }

    private fun closeStreams() {
        inputStreamReader.closeQuietly()
        errorStreamReader.closeQuietly()
        outputStream.closeQuietly()
    }

    @Synchronized private fun cleanupCommands() {
        this.isCleaning = true
        val toClean = Math.abs(this.maxCommands - this.maxCommands / 4)
        Timber.v("Cleaning up: %s", toClean)

        for (i in 0..toClean - 1) {
            this.commands.removeAt(0)
        }

        this.toWrite = this.commands.size - 1
        this.toRead = this.toWrite
        this.isCleaning = false
    }

    protected class InputThread(val shell: Shell) : Thread("Shell Input") {
        override fun run() {
            try {
                while (true) {
                    shell.commandLock.withLock {
                        while (!shell.shouldClose && shell.toWrite >= shell.commands.size) {
                            shell.isExecuting = false
                            Timber.d("Waiting for new commands to process...")
                            shell.commandCondition.await()
                        }
                    }

                    if (shell.toWrite >= shell.maxCommands) {
                        if (shell.toRead != shell.toWrite) {
                            Timber.v("Waiting for r/w to catch up before cleanup")
                        }
                        while (shell.toRead != shell.toWrite) {
                            // wait
                        }
                        shell.cleanupCommands()
                    }

                    if (shell.toWrite < shell.commands.size) {
                        shell.isExecuting = true
                        val cmd = shell.commands[shell.toWrite]
                        cmd.startExecution()

                        val toExecute = cmd.getCommands()
                        toExecute
                                .filterNot(String::isNullOrBlank)
                                .map { it + ";\n" }
                                .forEach { shell.outputStream.write(it) }

                        val line = String.format("\necho %s %s $?\n", TOKEN, shell.totalExecuted)
                        shell.outputStream.write(line)
                        shell.outputStream.flush()
                        shell.toWrite++
                        shell.totalExecuted++
                    } else if (shell.shouldClose) {
                        shell.isExecuting = false
                        shell.outputStream.write("\nexit 0\n")
                        shell.outputStream.flush()
                        return
                    }
                }
            } catch (e: IOException) {
                Timber.e(e, "IOException | InterruptedException")
            } catch (e: InterruptedException) {
                Timber.e(e, "IOException | InterruptedException")
            } finally {
                shell.toWrite = 0
                shell.closeStreams()
            }
        }
    }

    protected class OutputThread(val shell: Shell) : Thread("Shell Output") {
        override fun run() {
            var command: Command? = null

            try {
                //as long as there is something to read, we will keep reading.
                while (!shell.shouldClose || shell.inputStreamReader.ready() || shell.toRead < shell.commands.size) {
                    var outputLine: String = shell.inputStreamReader.readLine() ?: break

                    // EOF, shell closed?

                    if (command == null) {
                        if (shell.toRead >= shell.commands.size) {
                            if (shell.shouldClose) {
                                break
                            }
                            continue
                        }
                        command = shell.commands[shell.toRead]
                    }

                    val pos = outputLine.indexOf(TOKEN)
                    if (pos == -1) {
                        // send the doOutput for the implementer to process
                        command.doOutput(command.id, outputLine)
                    } else if (pos > 0) {
                        // token is suffix of doOutput, send doOutput part to implementer
                        command.doOutput(command.id, outputLine.substring(0, pos))
                    }

                    if (pos < 0) {
                        continue
                    }

                    outputLine = outputLine.substring(pos)
                    val fields = outputLine.split(" ".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()

                    if (fields.size < 2) {
                        continue
                    }

                    var id = 0
                    try {
                        id = Integer.parseInt(fields[1])
                    } catch (ignored: NumberFormatException) {
                    }

                    var exitCode = -1
                    try {
                        exitCode = Integer.parseInt(fields[2])
                    } catch (ignored: NumberFormatException) {
                    }

                    if (id != shell.totalRead) {
                        continue
                    }
                    shell.processErrors(command)

                    command.setExitCode(exitCode)
                    command.commandFinished()
                    command = null

                    shell.toRead++
                    shell.totalRead++
                }

                try {
                    shell.process.waitFor()
                    shell.process.destroy()
                } catch (ignored: Exception) {
                }

                while (shell.toRead < shell.commands.size) {
                    if (command == null) {
                        command = shell.commands[shell.toRead]
                    }

                    if (command.totalOutput < command.totalOutputProcessed) {
                        command.terminate("Did not process all doOutput!")
                    } else {
                        command.terminate("Unexpected termination!")
                    }

                    command = null
                    shell.toRead++
                }
                shell.toRead = 0
            } catch (e: IOException) {
                Timber.e(e, "IOException")
            } finally {
                shell.closeStreams()
                shell.isClosed = true
            }
        }
    }

    fun processErrors(command: Command?) {
        try {
            while (errorStreamReader.ready() && command != null) {
                val line = errorStreamReader.readLine() ?: break
                command.doOutput(command.id, line)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error while processing errors. Can you see the irony?")
        }

    }

    fun startWorker() {
        val worker = Worker(this)
        worker.start()
        try {
            worker.join(shellTimeout.toLong())

            when (worker.exitCode) {
                Worker.EXIT_TIMEOUT -> {
                    try {
                        process.destroy()
                    } catch (ignored: Exception) {
                    }

                    closeStreams()

                    throw TimeoutException(error)
                }

                Worker.EXIT_ERROR -> {
                    try {
                        process.destroy()
                    } catch (ignored: Exception) {
                    }

                    closeStreams()

                    throw RootDeniedException(error)
                }

                Worker.EXIT_SUCCESS -> {
                    val inputThread = InputThread(this)
                    inputThread.priority = Thread.NORM_PRIORITY
                    inputThread.start()

                    val outputThread = OutputThread(this)
                    outputThread.priority = Thread.NORM_PRIORITY
                    outputThread.start()
                }
            }
        } catch (ie: InterruptedException) {
            worker.interrupt()
            Thread.currentThread().interrupt()
            throw TimeoutException()
        }
    }

    protected class Worker(val shell: Shell) : Thread("Shell Worker") {
        companion object {
            private val OPENING = "echo Opening\n"

            val EXIT_TIMEOUT = -10239
            val EXIT_ERROR = -10339
            val EXIT_SUCCESS = 1
        }

        var exitCode: Int = EXIT_TIMEOUT

        override fun run() {
            try {
                shell.outputStream.write(OPENING)
                shell.outputStream.flush()

                // Check if we get "Opening" returned to check if we have properly opened a shell
                while (true) {
                    val line = shell.inputStreamReader.readLine()
                    if (line == null) {
                        // we are done and still did not get our "Opening" so something is fishy
                        throw EOFException()
                    } else if ("" == line) {
                        // let's continue checking
                    } else if ("Opening" == line) {
                        this.exitCode = EXIT_SUCCESS
                        setupShellOom()
                        break
                    }
                }

                shell.error = "Unknown error occurred"
            } catch (ioe: IOException) {
                this.exitCode = EXIT_ERROR

                val sb = StringBuilder()
                sb.append("Could not open shell -> ").append(ioe.message).append('\n')
                if (shell.isRoot) {
                    sb.append("Maybe root got denied?\n")
                }
                shell.error = sb.toString()
            }
        }

        private fun setupShellOom() {
            // we need the shell process' pid
            var pid: Int

            val processClass = shell.process.javaClass
            var field: Field?
            try {
                field = processClass.getDeclaredField("pid")
            } catch (e: NoSuchFieldException) {
                try {
                    field = processClass.getDeclaredField("id")
                } catch (e1: NoSuchFieldException) {
                    field = null
                }

            }

            if (field != null) {
                field.isAccessible = true
                try {
                    pid = field.get(shell.process) as Int
                } catch (iae: IllegalAccessException) {
                    Timber.d(iae, "IllegalAccessException")
                    pid = -1
                }
            } else {
                pid = -1
            }

            if (pid == -1) {
                Timber.d("could not get pid via reflection!")
                return
            }

            try {
                setupOomAdj(pid)
            } catch (e: Exception) {
                Timber.d(e, "Could not set shell oom adj for pid %s!", pid)
            }

            try {
                setupOomScoreAdj(pid)
            } catch (e: Exception) {
                Timber.d(e, "Could not set shell oom score adj for pid %s!", pid)
            }

        }

        private fun setupOomAdj(pid: Int) {
            shell.outputStream.write("(echo -17 > /proc/$pid/oom_adj) &> /dev/null\n")
            shell.outputStream.write("(echo -17 > /proc/$$/oom_adj) &> /dev/null\n")
            shell.outputStream.flush()
        }

        private fun setupOomScoreAdj(pid: Int) {
            shell.outputStream.write("(echo -1000 > /proc/$pid/oom_score_adj) &> /dev/null\n")
            shell.outputStream.write("(echo -1000 > /proc/$$/oom_score_adj) &> /dev/null\n")
            shell.outputStream.flush()
        }
    }
}
