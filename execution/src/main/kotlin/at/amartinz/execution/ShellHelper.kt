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

import android.text.TextUtils
import java.io.File
import java.util.*

object ShellHelper {
    val path: List<String>
        get() {
            val path = System.getenv("PATH")
            if (TextUtils.isEmpty(path)) {
                return Collections.emptyList()
            }
            return Arrays.asList(*path.split(":".toRegex()).dropLastWhile(String::isEmpty).toTypedArray())
        }

    fun findBinary(binaryName: String, searchPathsList: List<String>? = null): List<String> {
        var searchPaths = searchPathsList
        val foundLocations = ArrayList<String>()
        if (searchPaths == null) {
            searchPaths = path
        }
        searchPaths = searchPaths.map { if (it.endsWith("/")) it else String.format("%s/", it) }

        for (searchPath in searchPaths) {
            val expectedBinary = File(searchPath, binaryName)
            if (expectedBinary.exists()) {
                foundLocations.add(searchPath)
            }
        }

        if (!foundLocations.isEmpty()) {
            return foundLocations
        }


        val busybox = if (RootCheck.isRooted())
            RootShell.fireAndBlockString("which busybox")
        else
            NormalShell.fireAndBlockString("which busybox")

        if (busybox.isNullOrBlank()) {
            return foundLocations
        }

        if (busybox!!.endsWith("/busybox")) {
            foundLocations.add(0, busybox.trim { it <= ' ' })
        }

        return foundLocations
    }
}
