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
import android.preference.PreferenceManager
import android.text.TextUtils
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream

object Installer {
    fun extractBinary(context: Context, binaryName: String, binaryResId: Int, versionKey: String? = null, versionNew: Int = -1): Boolean {
        val filesDir = context.filesDir
        if (!filesDir.exists()) {
            filesDir.mkdirs()
        }

        val binary = File(filesDir, binaryName)
        val checkVersion = binary.exists() && versionNew != -1 && !TextUtils.isEmpty(versionKey)

        // if we get binary versioning, also check if we have a newer version available
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        if (checkVersion) {
            if (prefs.getInt(versionKey, 0) >= versionNew) {
                return false
            }
        }

        val bis = BufferedInputStream(context.resources.openRawResource(binaryResId))
        binary.createNewFile()
        val bos = BufferedOutputStream(FileOutputStream(binary))
        try {
            val buffer = ByteArray(bis.available())
            while (true) {
                val byteCount = bis.read(buffer)
                if (byteCount < 0) break
                bos.write(buffer, 0, byteCount)
            }
        } finally {
            bos.closeQuietly()
            bis.closeQuietly()
        }

        // make it executable!
        binary.setExecutable(true)

        // update binary version if successfully extracted binary
        Timber.v("Successfully extracted %s version \"%s\"", binaryName, versionNew)
        prefs.edit().putInt(versionKey, versionNew).apply()
        return true
    }

    fun binaryExists(context: Context, name: String): Boolean {
        val filesDir = context.filesDir
        if (!filesDir.exists()) {
            return false
        }

        val binary = File(filesDir, name)
        return binary.exists() && binary.canExecute()
    }
}
