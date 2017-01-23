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

import android.os.SystemClock
import at.amartinz.execution.exceptions.RootDeniedException
import timber.log.Timber
import java.io.IOException
import java.util.*
import java.util.concurrent.TimeoutException

class ShellManager private constructor() {
    companion object {
        private var sInstance: ShellManager? = null

        var RAMPAGE = false

        private val random by lazy { Random(SystemClock.elapsedRealtime() - SystemClock.currentThreadTimeMillis()) }
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

    val rootShell: RootShell?
        get() = getRootShell(false)

    fun getRootShell(newShell: Boolean): RootShell? {
        var rootShell: RootShell?

        synchronized(rootShells) {
            if (!newShell && rootShells.size > 0) {
                rootShell = rootShells[random.nextInt(rootShells.size)]
                if (rootShell != null) {
                    return rootShell
                }
            }
        }

        rootShell = createRootShell()
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

    val normalShell: NormalShell?
        get() = getNormalShell(false)

    fun getNormalShell(newShell: Boolean): NormalShell? {
        var normalShell: NormalShell?

        synchronized(normalShells) {
            if (!newShell && normalShells.size > 0) {
                normalShell = normalShells[random.nextInt(normalShells.size)]
                if (normalShell != null) {
                    return normalShell
                }
            }
        }

        normalShell = createNormalShell()
        if (normalShell != null) {
            synchronized(normalShells) {
                normalShells.add(normalShell)
            }
        }
        return normalShell
    }

    private fun createNormalShell(): NormalShell? {
        try {
            return NormalShell()
        } catch (e: IOException) {
            Timber.e(e, "Error creating new shell")
        } catch (e: TimeoutException) {
            Timber.e(e, "Error creating new shell")
        } catch (e: RootDeniedException) {
            Timber.e(e, "Error creating new shell")
        }

        return null
    }

    fun cleanupRootShells() {
        synchronized(rootShells) {
            if (rootShells.size > 0) {
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
            if (normalShells.size > 0) {
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
