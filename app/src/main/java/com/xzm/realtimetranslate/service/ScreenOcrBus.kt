package com.xzm.realtimetranslate.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Minimal process-wide bus between the UI and [ScreenTextSessionService].
 * Kept separate from [SessionBus] on purpose — the screen-region OCR mode must
 * never share state with the audio subtitle mode.
 */
object ScreenOcrBus {
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
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<Command>(extraBufferCapacity = 8)
    val commands: SharedFlow<Command> = _commands.asSharedFlow()

    sealed class Command {
        data object Stop : Command()
    }

    fun setStatus(status: Status, message: String = "") {
        _state.value = _state.value.copy(status = status, message = message)
    }

    fun setPreview(input: String? = null, output: String? = null) {
        _state.value = _state.value.copy(
            inputPreview = input ?: _state.value.inputPreview,
            outputPreview = output ?: _state.value.outputPreview,
        )
    }

    suspend fun stop() {
        _commands.emit(Command.Stop)
    }

    fun reset() {
        _state.value = UiState()
    }
}
