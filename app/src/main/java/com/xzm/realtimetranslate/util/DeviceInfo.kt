package com.xzm.realtimetranslate.util

import android.content.Context
import android.os.Build
import java.util.Locale

/**
 * The device facts worth having in a bug report.
 *
 * Deliberately excludes every persistent identifier (no IMEI, no `ANDROID_ID`, no serial):
 * the report is a plain text file the user forwards through WeChat or mail, and none of
 * those are needed to explain a translation failure.
 *
 * The ROM name is included because this app rides on audio capture, overlays and
 * foreground services — all of which behave differently on MIUI/HyperOS, and "works on
 * my phone" is usually a ROM difference rather than a code one.
 */
object DeviceInfo {

    fun describe(context: Context): String = buildString {
        append("device: ${Build.MANUFACTURER}/${Build.BRAND} ${Build.MODEL} (${Build.DEVICE})\n")
        append("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) abis=${Build.SUPPORTED_ABIS.joinToString(",")}\n")
        append("build: ${Build.DISPLAY} (${Build.ID})\n")
        val rom = romName()
        if (rom != null) append("rom: $rom\n")
        append("locale: ${Locale.getDefault().toLanguageTag()}\n")
        append("screen: ${screenSpec(context)}\n")
    }

    /** `MIUI 14` / `HyperOS 1.0` and friends, when the device reports one. */
    private fun romName(): String? {
        val miui = systemProperty("ro.miui.ui.version.name")
        val hyper = systemProperty("ro.mi.os.version.name") ?: systemProperty("ro.mi.os.version.incremental")
        return when {
            hyper != null -> "HyperOS $hyper"
            miui != null -> "MIUI $miui"
            else -> systemProperty("ro.build.version.emui")?.let { "EMUI $it" }
        }
    }

    /**
     * Read through reflection: `android.os.SystemProperties` is a hidden API, but reading
     * a `ro.*` property needs no permission and is the only way to see the ROM version.
     * Any failure (including a future block) is not worth reporting to the user.
     */
    private fun systemProperty(key: String): String? = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        clazz.getMethod("get", String::class.java).invoke(null, key) as? String
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun screenSpec(context: Context): String {
        val config = context.resources.configuration
        return "${config.screenWidthDp}x${config.screenHeightDp}dp @ ${config.densityDpi}dpi"
    }
}
