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
import java.io.IOException
import java.util.concurrent.TimeoutException

class RootShell @Throws(IOException::class, TimeoutException::class, RootDeniedException::class)
constructor() : Shell(true) {
    companion object {
        fun fireAndBlock(command: String): String? {
            return fireAndBlock(Command(command))
        }

        fun fireAndBlock(command: Command): String? {
            val shell = ShellManager.get().getRootShell() ?: return null
            return Shell.fireAndBlockInternal(command, shell).output
        }

        fun fireAndBlockString(command: String): String? {
            return fireAndBlockString(Command(command))
        }

        fun fireAndBlockString(command: Command): String? {
            val shell = ShellManager.get().getRootShell() ?: return null
            return Shell.fireAndBlockStringInternal(command, shell).output
        }

        fun fireAndBlockStringNewline(command: String): String? {
            return fireAndBlockStringNewline(Command(command))
        }

        fun fireAndBlockStringNewline(command: Command): String? {
            val shell = ShellManager.get().getRootShell() ?: return null
            return Shell.fireAndBlockStringNewlineInternal(command, shell).output
        }

        fun fireAndBlockList(command: String): List<String>? {
            return fireAndBlockList(Command(command))
        }

        fun fireAndBlockList(command: Command): List<String>? {
            val shell = ShellManager.get().getRootShell() ?: return null
            return Shell.fireAndBlockListInternal(command, shell).outputList
        }

        fun fireAndForget(command: String) {
            fireAndForget(Command(command))
        }

        fun fireAndForget(command: Command) {
            val shell = ShellManager.get().getRootShell() ?: return
            Shell.fireAndForgetInternal(command, shell)
        }
    }
}
