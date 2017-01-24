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

import android.support.annotation.WorkerThread
import android.text.TextUtils
import timber.log.Timber
import java.io.File

object RootCheck {
    private val PATH_SU = arrayOf("/system/bin/su", "/system/xbin/su", "/system/bin/.ext/.su", "/system/xbin/sugote",
            // "system less" root (supersu)
            "/su/bin/su")

    private var isRooted: Boolean? = null
    private var isRootGranted: Boolean? = null
    private var suVersion: String? = null

    /**
     * @return The path of the su binary or null if none found
     */
    val suPath: String?
        get() {
            return PATH_SU.firstOrNull { File(it).exists() }
        }

    /**
     * @param forceCheck Whether to use the cached result or force a new check
     * *
     * @return true if the device is rooted, false if not
     */
    @JvmOverloads fun isRooted(forceCheck: Boolean = false): Boolean {
        if (!forceCheck && isRooted != null) {
            return isRooted ?: false
        }

        val suPath = suPath
        if (!TextUtils.isEmpty(suPath)) {
            Timber.v("Found su path: %s", suPath)
            isRooted = true
            return true
        }
        Timber.v("no su binary found, trying with hit and miss")

        // fire and forget id, just for fun
        RootShell.fireAndForget("id")

        val rootShell = ShellManager.get().getRootShell()
        isRooted = rootShell != null
        Timber.v("is rooted: %s", isRooted)
        return isRooted!!
    }

    /**
     * @param forceCheck Whether to use the cached result or force a new check
     * *
     * @return true if the device is rooted and root is granted, false if not
     */
    @WorkerThread fun isRootGranted(forceCheck: Boolean = false): Boolean {
        if (!forceCheck && isRootGranted != null) {
            return isRootGranted ?: false
        }

        // no root available means we can not get root granted as well
        if (!isRooted()) {
            isRootGranted = false
            return false
        }

        val result = RootShell.fireAndBlockString("id")
        if (result.isNullOrBlank()) {
            // we did not get any result, means the shell did not work
            isRootGranted = false
            return false
        }

        isRootGranted = result?.contains("uid=0") ?: false
        return isRootGranted ?: false
    }

    @WorkerThread fun getSuVersion(forceCheck: Boolean = false): String {
        var version = ""
        if (forceCheck || suVersion == null) {
            version = if (isRooted()) NormalShell.fireAndBlockString("su -v") ?: "" else "-"
            if (TextUtils.isEmpty(version)) {
                version = RootShell.fireAndBlockString("su -v") ?: ""
            }
        }
        suVersion = if (TextUtils.isEmpty(version)) "-" else version
        return suVersion ?: "-"
    }
}
