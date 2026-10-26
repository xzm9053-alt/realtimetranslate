package com.xzm.realtimetranslate.audio

import kotlin.math.log10

/**
 * 采集链路的五种状态。用户看到的那行提示就是这个枚举，而不是一个笼统的「没声音」。
 *
 * [DIGITAL_SILENCE] 与 [QUIET] 的区别是关键：前者是 PCM 在流但每个采样都精确为 0
 * （投影没被真正激活、或源 App 拒绝内录），后者只是没人说话。
 * 把两者混为一谈是上一版误报的根源。
 */
enum class CaptureState {
    /** 采集器已启动，还没读到第一包 PCM。 */
    STARTING,

    /** 收到实际声音。 */
    FLOWING,

    /** 收到数据，但能量低于阈值 —— 只是安静。 */
    QUIET,

    /** 收到的每个采样都是 0 —— 数字静音。 */
    DIGITAL_SILENCE,

    /** `read()` 持续返回 0，采集器活着但不给数据。 */
    STALLED,
}

/**
 * RMS → 可显示电平。
 *
 * 必须是对数刻度：真实语音的 RMS 常在 50~3000，而满量程是 32768。
 * 线性归一化会让整个可用区间挤在条子最左边几个像素里，看不出变化。
 */
object AudioLevel {

    /** 条子底部对应的 dBFS。低于它就不再区分了。 */
    const val FLOOR_DBFS = -60.0

    /**
     * 条子顶部对应的 dBFS。
     *
     * 不是 0 dBFS：那是数字满量程，真实语音永远到不了，条子上半截会白白浪费。
     * 实测人声峰值约 −20 dBFS，取 −10 让正常说话就能占到条子的七成。
     */
    const val TOP_DBFS = -10.0

    private const val SILENCE_DBFS = -120.0
    private const val FULL_SCALE = 32768.0

    fun dbfs(rms: Double): Double =
        if (rms <= 0.0) SILENCE_DBFS else 20.0 * log10(rms / FULL_SCALE)

    /** 0f 为底部（安静或数字静音），1f 为 [TOP_DBFS]。 */
    fun normalize(rms: Double): Float =
        ((dbfs(rms) - FLOOR_DBFS) / (TOP_DBFS - FLOOR_DBFS)).coerceIn(0.0, 1.0).toFloat()
}
