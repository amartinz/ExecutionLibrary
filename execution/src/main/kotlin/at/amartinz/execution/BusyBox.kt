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
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import at.amartinz.execution.helper.DeviceHelper
import timber.log.Timber
import java.io.File

object BusyBox {
    private val EXECUTABLE_NAME_BUSYBOX = "busybox"
    private val EXECUTABLE_NAME_TOYBOX = "toybox"

    // my favorite
    private val PLAY_STORE_BUSYBOX = "https://play.google.com/store/apps/details?id=stericson.busybox"

    private val PATH_BUSYBOX = arrayOf("/system/bin/busybox", "/system/xbin/busybox")

    private val PATH_TOYBOX = arrayOf("/system/bin/toybox", "/system/xbin/toybox")

    private var sHasBusyBox: Boolean? = null
    private var sBusyBoxPath: String? = null

    fun asFile(context: Context): File {
        return File(context.filesDir, EXECUTABLE_NAME_BUSYBOX)
    }

    fun isAvailable(context: Context): Boolean {
        return Installer.binaryExists(context, EXECUTABLE_NAME_BUSYBOX)
    }

    fun callApplet(context: Context, applet: String? = null, args: String? = null): String {
        val busyBox = asFile(context)

        if (TextUtils.isEmpty(applet)) {
            Timber.w("You will just print the BusyBox help, but i bet you know already...")
        }

        var cmd = String.format("%s %s", busyBox.absolutePath, applet)
        if (TextUtils.isEmpty(args)) {
            Timber.v("No args specified, returning -> %s", cmd)
            return cmd
        }

        cmd = String.format("%s %s", cmd, args)
        Timber.v("Calling applet \"%s\" with args -> %s", applet, args)
        return cmd
    }

    fun installIncludedBusyBox(context: Context): Boolean {
        val detectedArch = DeviceHelper.detectArch()
        val busyBoxResId: Int
        when (detectedArch) {
            DeviceHelper.Arch.ARM, DeviceHelper.Arch.ARM64 -> {
                busyBoxResId = R.raw.busybox_arm
            }
            DeviceHelper.Arch.MIPS, DeviceHelper.Arch.MIPS64 -> {
                busyBoxResId = R.raw.busybox_mips
            }
            DeviceHelper.Arch.X86, DeviceHelper.Arch.X86_64 -> {
                busyBoxResId = R.raw.busybox_x86
            }
            DeviceHelper.Arch.UNKNOWN -> {
                busyBoxResId = 0
            }
        }
        if (busyBoxResId != 0) {
            val extractedBinary = Installer.extractBinary(context, "busybox", busyBoxResId)
            Timber.v("Extracted binary '%s' (%s) for arch '%s' -> %s", "busybox", busyBoxResId, detectedArch, extractedBinary)
            return extractedBinary
        }

        Timber.w("Arch '%s' is not supported right now!", detectedArch)
        return false
    }

    /**
     * @return The path of the busybox binary or null if none found
     */
    val busyBoxPath: String?
        get() {
            return PATH_BUSYBOX.firstOrNull { File(it).exists() }
        }

    /**
     * @return The path of the toybox binary or null if none found
     */
    val toyBoxPath: String?
        get() {
            return PATH_TOYBOX.firstOrNull { File(it).exists() }
        }

    val isActuallyToybox: Boolean
        get() = !TextUtils.isEmpty(sBusyBoxPath) && sBusyBoxPath!!.endsWith(EXECUTABLE_NAME_TOYBOX)

    fun isAvailableSystem(forceCheck: Boolean = false): Boolean {
        if (!forceCheck && sHasBusyBox != null) {
            return sHasBusyBox ?: false
        }

        val busyBoxPath = busyBoxPath
        if (!TextUtils.isEmpty(busyBoxPath)) {
            Timber.d("Found BusyBox path: %s", busyBoxPath)

            sHasBusyBox = true
            sBusyBoxPath = busyBoxPath
            return true
        }

        Timber.d("No BusyBox binary found, trying with ToyBox")

        val toyBoxPath = toyBoxPath
        if (!TextUtils.isEmpty(toyBoxPath)) {
            Timber.d("Found ToyBox path: %s", toyBoxPath)

            sHasBusyBox = true
            sBusyBoxPath = toyBoxPath
            return true
        }

        Timber.d("no BusyBox nor ToyBox binary found, trying with hit and miss")

        val busyBoxLocation = ShellHelper.findBinary(EXECUTABLE_NAME_BUSYBOX, null)[0]
        if (!TextUtils.isEmpty(busyBoxLocation)) {
            Timber.d("Found BusyBox path: %s", busyBoxLocation)

            sHasBusyBox = true
            sBusyBoxPath = if (busyBoxLocation.endsWith("/"))
                String.format("%s%s", busyBoxLocation, EXECUTABLE_NAME_BUSYBOX)
            else
                String.format("%s/%s", busyBoxLocation, EXECUTABLE_NAME_BUSYBOX)
            return true
        }

        Timber.d("no BusyBox via hit and miss found, come on ToyBox...")

        val toyBoxLocation = ShellHelper.findBinary(EXECUTABLE_NAME_TOYBOX, null)[0]
        if (!TextUtils.isEmpty(toyBoxLocation)) {
            Timber.d("Found ToyBox path: %s", toyBoxLocation)

            sHasBusyBox = true
            sBusyBoxPath = if (toyBoxLocation.endsWith("/"))
                String.format("%s%s", toyBoxLocation, EXECUTABLE_NAME_TOYBOX)
            else
                String.format("%s/%s", toyBoxLocation, EXECUTABLE_NAME_TOYBOX)
            return true
        }

        Timber.d("alright, i give up. no BusyBox or ToyBox detected.")

        sHasBusyBox = false
        sBusyBoxPath = null
        return false
    }

    fun callAppletSystem(applet: String, args: String? = null): String? {
        if (!isAvailableSystem() || TextUtils.isEmpty(sBusyBoxPath)) {
            return null
        }

        if (TextUtils.isEmpty(applet)) {
            Timber.w("You will just print the BusyBox help, but i bet you know already...")
        }

        var cmd = String.format("%s %s", sBusyBoxPath, applet)
        if (TextUtils.isEmpty(args)) {
            Timber.v("No args specified, returning -> %s", cmd)
            return cmd
        }

        cmd = String.format("%s %s", cmd, args)
        Timber.v("Calling applet \"%s\" with args -> %s", applet, args)
        return cmd
    }

    fun offerBusyBox(context: Context) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_BUSYBOX))
        i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        try {
            context.startActivity(i)
        } catch (ignored: Exception) {
        }
    }
}
