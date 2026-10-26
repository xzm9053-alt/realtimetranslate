package com.xzm.realtimetranslate.util

import com.xzm.realtimetranslate.audio.CaptureState
import com.xzm.realtimetranslate.data.AudioSourceMode

/**
 * 一行式的采集结论，写进诊断报告。
 *
 * 这是「采集器没起来」「投影没激活」「只是安静」三种情况在日志里长得一模一样的直接修复：
 * 它们在这里被写成三个不同的字串。
 */
object AudioCaptureSummary {

    fun describe(
        mode: AudioSourceMode,
        state: CaptureState,
        peakRms: Double,
        projectionActivated: Boolean?,
    ): String = buildString {
        append("source: ").append(mode.name).append('\n')
        append("state: ").append(state).append('\n')
        append("peakRms: ").append("%.1f".format(peakRms)).append('\n')
        if (mode.needsMediaProjection) {
            append("projectionActivated: ")
            append(
                when (projectionActivated) {
                    true -> "yes"
                    false -> "no (投影未被激活——部分 ROM 在此情况下不开放音频)"
                    null -> "unknown (本次进程还没跑过内录)"
                },
            ).append('\n')
        }
    }
}
