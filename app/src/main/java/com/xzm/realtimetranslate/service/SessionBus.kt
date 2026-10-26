package com.xzm.realtimetranslate.service

import com.xzm.realtimetranslate.audio.CaptureState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight process-wide bus between UI and SubtitleSessionService.
 */
object SessionBus {
    enum class Status {
        Idle,
        Starting,
        Running,
        Error,
        Stopped,
    }

    data class UiState(
        val status: Status = Status.Idle,
        val message: String = "",
        val inputPreview: String = "",
        val outputPreview: String = "",
        /** Full session transcripts retained after stop for MD export. */
        val lastInputFull: String = "",
        val lastOutputFull: String = "",
        val canExport: Boolean = false,
        /** 0f..1f，对数刻度的实时音量，供字幕页电平条使用。 */
        val audioLevel: Float = 0f,
        /** 采集链路状态；区分「安静」与「数字静音」。 */
        val captureState: CaptureState = CaptureState.STARTING,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    sealed class Command {
        data object Stop : Command()
    }

    fun update(transform: (UiState) -> UiState) {
        _state.value = transform(_state.value)
    }

    fun setStatus(status: Status, message: String = "") {
        _state.value = _state.value.copy(status = status, message = message)
    }

    fun setAudio(level: Float, state: CaptureState) {
        if (state == CaptureState.STARTING) peakRmsValue = 0.0
        _state.value = _state.value.copy(audioLevel = level, captureState = state)
    }

    /** 本次会话的峰值 RMS，写进诊断报告用。 */
    @Volatile
    private var peakRmsValue: Double = 0.0

    fun noteRms(rms: Double) {
        if (rms > peakRmsValue) peakRmsValue = rms
    }

    fun peakRms(): Double = peakRmsValue

    /** 投影是否真正被激活过（建了 VirtualDisplay）。 */
    @Volatile
    var projectionActivated: Boolean? = null

    fun setPreview(input: String? = null, output: String? = null) {
        _state.value = _state.value.copy(
            inputPreview = input ?: _state.value.inputPreview,
            outputPreview = output ?: _state.value.outputPreview,
        )
    }

    fun markSessionFinished(inputFull: String, outputFull: String, message: String) {
        _state.value = _state.value.copy(
            status = Status.Stopped,
            message = message,
            inputPreview = outputFull.ifBlank { inputFull }.takeLast(200),
            outputPreview = outputFull.takeLast(400),
            lastInputFull = inputFull,
            lastOutputFull = outputFull,
            canExport = inputFull.isNotBlank() || outputFull.isNotBlank(),
        )
    }

    fun clearExport() {
        _state.value = _state.value.copy(canExport = false)
    }

    suspend fun stop() {
        _commands.emit(Command.Stop)
    }

    fun tryStop(): Boolean = _commands.tryEmit(Command.Stop)

    fun reset() {
        _state.value = UiState()
    }
}
