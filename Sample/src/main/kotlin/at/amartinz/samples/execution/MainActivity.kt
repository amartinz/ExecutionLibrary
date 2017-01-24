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

package at.amartinz.samples.execution

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.support.v7.app.AppCompatActivity
import at.amartinz.execution.*
import at.amartinz.execution.helper.DeviceHelper
import timber.log.Timber

class MainActivity : AppCompatActivity() {
    val handlerThread = HandlerThread("Background Shell Thread")
    val handler by lazy {
        handlerThread.start()
        Handler(handlerThread.looper)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        handler.post { testBusyBoxInstallation() }
        handler.post { testNormalShell() }
        handler.post { testRootShell() }
        handler.post { testBusyBox() }
    }

    override fun onDestroy() {
        // we sure to close all shells
        ShellManager.get().onDestroy()

        handlerThread.quit()

        super.onDestroy()
    }

    fun testBusyBoxInstallation() {
        val is64Bit = DeviceHelper.is64Bit()
        val supportedAbis = DeviceHelper.getSupportedAbis()
        val detectedArch = DeviceHelper.detectArch()
        Timber.d("Is 64 bit -> %s", is64Bit)
        Timber.d("Supported ABIs -> %s", supportedAbis)
        Timber.d("Detected arch -> %s", detectedArch)

        // can be done generically by installer for any file
        var busyBoxAvailable = Installer.binaryExists(this, "busybox")
        Timber.d("BusyBox exists -> %s", busyBoxAvailable)

        val busyBoxFile = BusyBox.asFile(this)
        Timber.d("Deleting BusyBox -> %s", busyBoxFile.delete())

        // should be always false, as we just deleted it
        busyBoxAvailable = busyBoxFile.exists()
        Timber.d("BusyBox exists -> %s", busyBoxAvailable)

        // available == installed + executable
        busyBoxAvailable = BusyBox.isAvailable(this)
        Timber.d("BusyBox available -> %s", busyBoxAvailable)

        // not versioning, always install
        BusyBox.installIncludedBusyBox(this)

        // should be available, as it got freshly installed
        busyBoxAvailable = BusyBox.isAvailable(this)
        Timber.d("BusyBox available -> %s", busyBoxAvailable)

        // not executable -> not available
        Timber.d("Removing executable bit -> %s", busyBoxFile.setExecutable(false))
        busyBoxAvailable = BusyBox.isAvailable(this)
        Timber.d("BusyBox available -> %s", busyBoxAvailable)

        // existing + executable -> available
        Timber.d("Readding executable bit -> %s", busyBoxFile.setExecutable(true))
        busyBoxAvailable = BusyBox.isAvailable(this)
        Timber.d("BusyBox available -> %s", busyBoxAvailable)
    }

    fun testNormalShell() {
        var command = BusyBox.callApplet(this, "")
        var result = NormalShell.fireAndBlockStringNewline(command)
        Timber.d("Ran '%s' and got:\n%s", command, result)

        command = BusyBox.callApplet(this, "id")
        result = NormalShell.fireAndBlockString(command)
        Timber.d("Ran '%s' and got:\n%s", command, result)

        val cmd: Command = Command.callBusyBox(this, "echo", "\"Hey!\"") +
                BusyBox.callApplet(this, "echo", "You can also append commands this way") +
                BusyBox.callApplet(this, "echo", "-n $'\n'") +
                BusyBox.callApplet(this, "echo", "-e \"Cool, isn't it?\"")
        result = NormalShell.fireAndBlockStringNewline(cmd)
        Timber.d("Ran '%s' and got:\n%s", cmd.getCommands(), result)

        // you can clean up shells when you do not use them anymore
        // at least you should clear them all onDestroy()
        ShellManager.get().cleanupNormalShells()
    }

    fun testRootShell() {
        // root checks are cached, pass in "true" to force a check any bypass the cache
        Timber.d("Is root available -> %s", RootCheck.isRooted())

        // you can also get the path of su, if available
        Timber.d("Root path -> %s", RootCheck.suPath)

        // get the version of su, just in case you care (eg blacklist special / malicious root apps)
        Timber.d("Root version -> %s", RootCheck.getSuVersion())

        // this issues a command as root and will show the "allow root?" prompt
        // means, this should get executed in a background thread, as it is blocking
        Timber.d("Is root granted -> %s", RootCheck.isRootGranted())
    }

    fun testBusyBox() {
        val normalShell = ShellManager.get().getNormalShell()
        Timber.d("Got a new NormalShell -> %s", normalShell)
        if (normalShell == null) {
            Timber.w("NormalShell is null, so all commands will be skipped!")
        }

        val rootShell = ShellManager.get().getRootShell()
        Timber.d("Got a new RootShell -> %s", rootShell)
        if (rootShell == null) {
            Timber.w("RootShell is null, so all commands will be skipped!")
        }

        var command = Command.callBusyBox(this, "ls", "-la").setOutputType(Command.OUTPUT_STRING_NEWLINE)
        executeAndPrint(command, normalShell)

        command = Command.callBusyBox(this, "mount").setOutputType(Command.OUTPUT_STRING_NEWLINE)
        executeAndPrint(command, normalShell)

        command = Command.callBusyBox(this, "cat", "/proc/cmdline").setOutputType(Command.OUTPUT_STRING_NEWLINE)
        executeAndPrint(command, normalShell)
        executeAndPrint(command, rootShell)

        // we can close shells immediatly too
        ShellManager.get().closeShell(normalShell)
        ShellManager.get().closeShell(rootShell)
    }

    fun executeAndPrint(command: Command, shell: Shell?) {
        val result = shell?.add(command)?.waitFor()?.output ?: ""
        if (result.isNotBlank()) {
            Timber.d("Executed '${command.getCommands()}' and got:\n$result")
        }
    }
}
