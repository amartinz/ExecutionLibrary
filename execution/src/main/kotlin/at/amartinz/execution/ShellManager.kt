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
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException

class ShellManager private constructor() {
    companion object {
        private var sInstance: ShellManager? = null

        var RAMPAGE = false

        private val rootShells = Collections.synchronizedList(ArrayList<RootShell>())
        private val normalShells = Collections.synchronizedList(ArrayList<NormalShell>())

        fun get(): ShellManager {
            if (sInstance == null) {
                sInstance = ShellManager()
            }
            return (sInstance as ShellManager)
        }
    }

    init {
        cleanupShells()
    }

    fun getRootShell(): RootShell? {
        val rootShell = createRootShell()
        if (rootShell != null) {
            synchronized(rootShells) {
                rootShells.add(rootShell)
            }
        }
        return rootShell
    }

    private fun createRootShell(): RootShell? {
        var exception: Exception? = null
        try {
            return RootShell()
        } catch (e: IOException) {
            exception = e
        } catch (e: TimeoutException) {
            exception = e
        } catch (e: RootDeniedException) {
            exception = e
        } finally {
            if (exception != null) {
                Timber.e(exception, "Error creating new root shell")
            }
        }

        return null
    }

    fun getNormalShell(): NormalShell? {
        val normalShell = createNormalShell()
        if (normalShell != null) {
            synchronized(normalShells) {
                normalShells.add(normalShell)
            }
        }
        return normalShell
    }

    private fun createNormalShell(): NormalShell? {
        var exception: Exception? = null
        try {
            return NormalShell()
        } catch (e: IOException) {
            exception = e
        } catch (e: TimeoutException) {
            exception = e
        } catch (e: RootDeniedException) {
            exception = e
        } finally {
            if (exception != null) {
                Timber.e(exception, "Error creating new root shell")
            }
        }

        return null
    }

    fun closeShell(shell: Shell?) {
        if (shell == null) {
            return
        }

        if (shell is NormalShell) {
            shell.close()
            synchronized(normalShells) {
                val removedShell = normalShells.remove(shell)
                Timber.v("Removed shell '%s' - %s", shell, removedShell)
            }
        } else if (shell is RootShell) {
            shell.close()
            synchronized(rootShells) {
                val removedShell = rootShells.remove(shell)
                Timber.v("Removed shell '%s' - %s", shell, removedShell)
            }
        } else {
            Timber.wtf("Something here is fishy...")
        }
    }

    fun cleanupRootShells() {
        synchronized(rootShells) {
            if (rootShells.isNotEmpty()) {
                val rootShellIterator = rootShells.iterator()
                while (rootShellIterator.hasNext()) {
                    val rootShell = rootShellIterator.next()
                    rootShell?.close()
                    rootShellIterator.remove()
                }
                rootShells.clear()
            }
        }
    }

    fun cleanupNormalShells() {
        synchronized(normalShells) {
            if (normalShells.isNotEmpty()) {
                val normalShellIterator = normalShells.iterator()
                while (normalShellIterator.hasNext()) {
                    val normalShell = normalShellIterator.next()
                    normalShell?.close()
                    normalShellIterator.remove()
                }
                normalShells.clear()
            }
        }
    }

    val normalShellCount: Int
        get() = synchronized(normalShells) {
            return normalShells.size
        }

    val rootShellCount: Int
        get() = synchronized(rootShells) {
            return rootShells.size
        }

    fun cleanupShells() {
        cleanupRootShells()
        cleanupNormalShells()
    }

    fun onDestroy() {
        cleanupShells()
    }
}
