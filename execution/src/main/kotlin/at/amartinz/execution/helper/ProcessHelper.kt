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

package at.amartinz.execution.helper

import android.text.TextUtils

import at.amartinz.execution.NormalShell

object ProcessHelper {
    val INVALID = -1

    fun getUidFromPid(pid: Int): Int {
        var result = getUidFromPidPerStat(pid)
        if (result == INVALID) {
            result = getUidFromPidPerStatusFile(pid)
        }
        return result
    }

    fun getUidFromPidPerStat(pid: Int): Int {
        val path = String.format("/proc/%s", pid)
        val cmd = String.format("stat -c %%u %s", path)
        val result = NormalShell.fireAndBlockString(cmd)

        if (!TextUtils.isEmpty(result)) {
            try {
                return Integer.parseInt(result!!.trim { it <= ' ' })
            } catch (ignored: NumberFormatException) {
            }

        }
        return INVALID
    }

    fun getUidFromPidPerStatusFile(pid: Int): Int {
        val path = String.format("/proc/%s/status", pid)
        val cmd = String.format("cat %s", path)
        val result = NormalShell.fireAndBlockList(cmd)

        if (result == null || result.size < 5) {
            return INVALID
        }

        var uidLine = result.firstOrNull { it.toLowerCase().startsWith("uid") } ?: ""

        // Uid: 10102 10102 10102 10102
        uidLine = uidLine.replace("\t", " ")
        val splitted = uidLine.split(" ".toRegex()).dropLastWhile(String::isEmpty).toTypedArray()
        if (splitted.size < 2) {
            return INVALID
        }

        try {
            return Integer.parseInt(splitted[1])
        } catch (ignored: Exception) {
        }

        // try as a fallback
        try {
            return Integer.parseInt(splitted[2])
        } catch (ignored: Exception) {
        }

        return INVALID
    }
}
