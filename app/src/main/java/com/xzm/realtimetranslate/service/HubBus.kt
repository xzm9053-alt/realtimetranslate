package com.xzm.realtimetranslate.service

import com.xzm.realtimetranslate.data.ScreenOcrMode
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 悬浮球运行模式枚举。
 */
enum class HubMode {
    IDLE,
    VOICE,
    SCREEN_REGION,
    FULLSCREEN,
    COMIC,
}

enum class StaticModeTarget {
    CAMERA,
    IMAGE,
    DOCUMENT,
}

sealed class HubCommand {
    data object TogglePause : HubCommand()
    data object StopCurrentMode : HubCommand()
    data class SwitchMode(val mode: HubMode) : HubCommand()
    data class SetSubtitleWindowVisible(val visible: Boolean) : HubCommand()
    data object CloseHub : HubCommand()
    data class LaunchStaticMode(val target: StaticModeTarget) : HubCommand()
    data object ReselectRegion : HubCommand()
    data object ToggleOutline : HubCommand()
}

data class HubUiState(
    val isHubEnabled: Boolean = false,
    val mode: HubMode = HubMode.IDLE,
    val isPaused: Boolean = false,
    val isSubtitleWindowVisible: Boolean = true,
)

/**
 * 通用悬浮球进程级总线，跨服务与 UI 调度模式切换与命令。
 */
object HubBus {
    private val _state = MutableStateFlow(HubUiState())
    val state: StateFlow<HubUiState> = _state.asStateFlow()

    private val _commands = MutableSharedFlow<HubCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<HubCommand> = _commands.asSharedFlow()

    fun setHubEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(isHubEnabled = enabled)
    }

    fun setMode(mode: HubMode) {
        _state.value = _state.value.copy(mode = mode)
    }

    fun setPaused(paused: Boolean) {
        _state.value = _state.value.copy(isPaused = paused)
    }

    fun setSubtitleWindowVisible(visible: Boolean) {
        _state.value = _state.value.copy(isSubtitleWindowVisible = visible)
    }

    fun postCommand(cmd: HubCommand) {
        _commands.tryEmit(cmd)
    }

    suspend fun emitCommand(cmd: HubCommand) {
        _commands.emit(cmd)
    }

    fun syncFromSession(sessionStatus: SessionBus.Status) {
        val newMode = when (sessionStatus) {
            SessionBus.Status.Running, SessionBus.Status.Starting -> HubMode.VOICE
            else -> if (_state.value.mode == HubMode.VOICE) HubMode.IDLE else _state.value.mode
        }
        _state.value = _state.value.copy(mode = newMode)
    }

    fun syncFromScreenOcr(ocrStatus: ScreenOcrBus.Status, ocrMode: ScreenOcrMode) {
        val targetMode = when (ocrMode) {
            ScreenOcrMode.REGION -> HubMode.SCREEN_REGION
            ScreenOcrMode.FULLSCREEN -> HubMode.FULLSCREEN
            ScreenOcrMode.COMIC -> HubMode.COMIC
        }
        val (newMode, paused) = when (ocrStatus) {
            ScreenOcrBus.Status.Running, ScreenOcrBus.Status.Starting -> targetMode to false
            ScreenOcrBus.Status.Paused -> targetMode to true
            else -> {
                val m = if (_state.value.mode == targetMode) HubMode.IDLE else _state.value.mode
                m to false
            }
        }
        _state.value = _state.value.copy(mode = newMode, isPaused = paused)
    }

    fun reset() {
        _state.value = HubUiState()
    }
}
