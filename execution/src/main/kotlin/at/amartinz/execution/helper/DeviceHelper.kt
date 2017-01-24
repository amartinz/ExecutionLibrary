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

import android.os.Build

object DeviceHelper {
    enum class Arch {
        ARM,
        ARM64,
        MIPS,
        MIPS64,
        X86,
        X86_64,
        UNKNOWN
    }

    fun detectArch(): Arch {
        val is64Bit = is64Bit()
        val supportedAbis = getSupportedAbis().toLowerCase()
        if (supportedAbis.contains("x86")) {
            if (is64Bit) {
                return Arch.X86_64
            } else {
                return Arch.X86
            }
        } else if (supportedAbis.contains("mips")) {
            if (is64Bit) {
                return Arch.MIPS64
            } else {
                return Arch.MIPS
            }
        } else if (supportedAbis.contains("arm")) {
            if (is64Bit) {
                return Arch.ARM64
            } else {
                return Arch.ARM
            }
        }
        return Arch.UNKNOWN
    }

    fun getSupportedAbis(): String {
        val supportedAbis: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val abis = StringBuilder()
            var added = false
            Build.SUPPORTED_ABIS.forEach {
                if (added) {
                    abis.append(", ")
                }
                abis.append(it)
                added = true
            }
            supportedAbis = abis.toString()
        } else {
            supportedAbis = String.format("%s, %s", Build.CPU_ABI, Build.CPU_ABI2);
        }
        return supportedAbis
    }

    fun is64Bit(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
        }
        return false
    }
}
