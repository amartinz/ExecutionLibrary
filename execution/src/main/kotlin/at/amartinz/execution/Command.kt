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

import android.content.Context
import timber.log.Timber
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class Command(vararg commands: String, val id: Int = 0, val timeout: Int = Shell.DEFAULT_TIMEOUT) : CommandListener {
    private var exitCode: Int = 0

    private val commands: ArrayList<String>

    @get:Synchronized var isExecuting: Boolean = false
        private set
    @get:Synchronized var isFinished: Boolean = false
        private set
    @get:Synchronized var isTerminated: Boolean = false
        private set

    var totalOutput: Int = 0
    var totalOutputProcessed: Int = 0

    private var outputType = OUTPUT_NONE
    private var outputBuilder: StringBuilder? = null
    var outputList: MutableList<String>? = null

    val output: String?
        get() = if (outputBuilder != null) outputBuilder.toString().trim { it <= ' ' } else null

    private val commandLock = ReentrantLock()
    private val commandCondition: Condition = commandLock.newCondition()

    companion object {
        val OUTPUT_NONE = -1
        val OUTPUT_ALL = 1
        val OUTPUT_STRING = 2
        val OUTPUT_STRING_NEWLINE = 3
        val OUTPUT_LIST = 4

        fun callBusyBox(context: Context, applet: String? = null, args: String? = null): Command {
            val command = BusyBox.callApplet(context, applet, args)
            return Command(command)
        }
    }

    init {
        this.commands = ArrayList(commands.toList())
    }

    fun getCommands(): ArrayList<String> {
        synchronized(this) {
            return commands
        }
    }

    operator fun plus(command: String): Command {
        commands.add(command)
        return this
    }

    fun waitFor(): Command {
        while (!isFinished) {
            commandLock.withLock {
                try {
                    commandCondition.await(timeout.toLong(), TimeUnit.MILLISECONDS)
                } catch (ignored: Exception) {
                }
            }

            if (!isFinished && !isExecuting) {
                Timber.w("Reached timeout and we are still executing!")
                if (ShellManager.RAMPAGE) {
                    throw RuntimeException("Something is really wrong")
                }
                break
            }
        }
        return this
    }

    @Synchronized fun setOutputType(outputType: Int): Command {
        this.outputType = outputType
        when (this.outputType) {
            OUTPUT_NONE -> {
                outputBuilder = null
                outputList = null
            }
            OUTPUT_ALL -> {
                outputBuilder = StringBuilder()
                outputList = ArrayList<String>()
            }
            OUTPUT_STRING, OUTPUT_STRING_NEWLINE -> {
                outputBuilder = StringBuilder()
                outputList = null
            }
            OUTPUT_LIST -> {
                outputBuilder = null
                outputList = ArrayList<String>()
            }
        }
        return this
    }

    @Synchronized fun getExitCode(): Int {
        return this.exitCode
    }

    fun setExitCode(code: Int) {
        synchronized(this) {
            exitCode = code
        }
    }

    fun doOutput(id: Int, line: String) {
        totalOutput++
        synchronized(this) {
            onCommandOutput(id, line)
        }
    }

    @Synchronized fun resetCommand(): Command {
        this.isFinished = false
        this.totalOutput = 0
        this.totalOutputProcessed = 0
        this.isExecuting = false
        this.isTerminated = false
        this.exitCode = -1
        this.outputBuilder?.setLength(0)
        return this
    }

    fun commandFinished() {
        if (!isTerminated) {
            synchronized(this) {
                onCommandCompleted(id, exitCode)

                Timber.v("finished command with id \"%s\"", id)
                finishCommand()
            }
        }
    }

    fun finishCommand() {
        commandLock.withLock {
            isExecuting = false
            isFinished = true
            commandCondition.signalAll()
        }
    }

    fun terminate(reason: String) {
        synchronized(this) {
            onCommandTerminated(id, reason)
        }

        Timber.w("command \"%s\" did not finish because it was terminated!\n%s", id, reason)
        setExitCode(-1)
        isTerminated = true
        finishCommand()
    }

    fun startExecution() {
        val executionMonitor = ExecutionMonitor()
        executionMonitor.priority = Thread.MIN_PRIORITY
        executionMonitor.start()
        isExecuting = true
    }

    override fun onCommandCompleted(id: Int, exitCode: Int) {
        // needs to be overwritten to implement
        setExitCode(exitCode)
    }

    override fun onCommandTerminated(id: Int, reason: String) {
        // needs to be overwritten to implement
        Timber.v("terminated command with id \"%s\": %s", id, reason)
    }

    override fun onCommandOutput(id: Int, line: String) {
        // needs to be overwritten to implement
        // WARNING: do not forget to call super!
        if (outputBuilder != null) {
            outputBuilder!!.append(line)
            if (outputType == OUTPUT_STRING_NEWLINE) {
                outputBuilder!!.append('\n')
            }
        }
        if (outputList != null) {
            outputList!!.add(line)
        }
        totalOutputProcessed++
    }

    private inner class ExecutionMonitor : Thread() {
        override fun run() {
            if (timeout <= 0) {
                return
            }

            while (!isFinished) {
                commandLock.withLock {
                    try {
                        commandCondition.await(timeout.toLong(), TimeUnit.MILLISECONDS)
                    } catch (ignored: InterruptedException) {
                    }
                }

                if (!isFinished) {
                    terminate("Timeout exception")
                }
            }
        }
    }
}
