package com.xzm.realtimetranslate.ui.subtitle

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.audio.CaptureState
import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.data.LanguageOption
import com.xzm.realtimetranslate.data.OcrScript
import com.xzm.realtimetranslate.data.ScreenOcrMode
import com.xzm.realtimetranslate.data.SubtitleDisplayMode
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.service.ScreenOcrBus
import com.xzm.realtimetranslate.service.SessionBus
import com.xzm.realtimetranslate.service.StaticModeTarget
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.components.SettingSwitchRow
import com.xzm.realtimetranslate.ui.components.StatusPill
import com.xzm.realtimetranslate.ui.components.StatusTone
import com.xzm.realtimetranslate.ui.more.MoreScreen
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private enum class LangPicker { Source, Target }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleScreen(
    modifier: Modifier = Modifier,
    settings: UserSettings,
    session: SessionBus.UiState,
    ocrSession: ScreenOcrBus.UiState,
    exportMessage: String?,
    onSourceLanguage: (String) -> Unit,
    onTargetLanguage: (String) -> Unit,
    onAudioSource: (AudioSourceMode) -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOcrStart: () -> Unit,
    onOcrStop: () -> Unit,
    onExport: () -> Unit,
    canDrawOverlays: Boolean,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit = {},
    onResetAppearance: () -> Unit = {},
    onResetOcrAppearance: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onToggleHub: (Boolean) -> Unit = {},
    staticLaunch: StaticModeTarget? = null,
    pendingVideoUri: Uri? = null,
    onStaticLaunchConsumed: () -> Unit = {},
) {
    var activeTool by rememberSaveable { mutableStateOf<StaticModeTarget?>(null) }

    LaunchedEffect(staticLaunch, pendingVideoUri) {
        if (staticLaunch != null) {
            activeTool = staticLaunch
        } else if (pendingVideoUri != null) {
            activeTool = StaticModeTarget.VIDEO
        }
    }

    BackHandler(enabled = activeTool != null) {
        activeTool = null
        onStaticLaunchConsumed()
    }

    val running = session.status == SessionBus.Status.Running ||
        session.status == SessionBus.Status.Starting
    val ocrRunning = ocrSession.status.isActive

    var picker by remember { mutableStateOf<LangPicker?>(null) }
    var selectedMode by remember(ocrRunning, running) {
        mutableIntStateOf(if (ocrRunning && !running) 1 else 0)
    }
    var appearanceExpanded by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current

    val (statusLabel, tone) = when (session.status) {
        SessionBus.Status.Idle -> stringResource(R.string.status_idle) to StatusTone.Idle
        SessionBus.Status.Starting -> stringResource(R.string.status_starting) to StatusTone.Working
        SessionBus.Status.Running -> stringResource(R.string.status_running) to StatusTone.Ok
        SessionBus.Status.Error -> stringResource(R.string.status_error) to StatusTone.Error
        SessionBus.Status.Stopped -> stringResource(R.string.status_stopped) to StatusTone.Warn
    }

    AnimatedContent(
        targetState = activeTool,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(260)) { it } + fadeIn(tween(260))).togetherWith(
                    slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(200)),
                )
            } else {
                (slideInHorizontally(tween(260)) { -it / 3 } + fadeIn(tween(260))).togetherWith(
                    slideOutHorizontally(tween(260)) { it } + fadeOut(tween(200)),
                )
            }
        },
        modifier = modifier.fillMaxSize(),
        label = "ToolTransition",
    ) { currentTool ->
        if (currentTool != null) {
            MoreScreen(
                target = currentTool,
                onBack = {
                    activeTool = null
                    onStaticLaunchConsumed()
                },
                onOpenSettings = {
                    activeTool = null
                    onStaticLaunchConsumed()
                    onOpenSettings()
                },
                initialVideoUri = pendingVideoUri,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
        // 顶部 Header + 悬浮球快捷状态胶囊
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.subtitle_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = "极简双核同传中枢",
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            // 悬浮球开关状态芯片
            val hubBgColor by animateColorAsState(
                targetValue = if (settings.hubEnabled) Booth.Accent.copy(alpha = 0.12f) else MiuixTheme.colorScheme.surfaceVariant,
                animationSpec = tween(220),
                label = "hubBgColor",
            )
            val hubDotColor by animateColorAsState(
                targetValue = if (settings.hubEnabled) Color(0xFF10B981) else Color(0xFF94A3B8),
                animationSpec = tween(220),
                label = "hubDotColor",
            )
            val hubTextColor by animateColorAsState(
                targetValue = if (settings.hubEnabled) Booth.Accent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                animationSpec = tween(220),
                label = "hubTextColor",
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(hubBgColor)
                    .clickable { onToggleHub(!settings.hubEnabled) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(hubDotColor),
                )
                Text(
                    text = if (settings.hubEnabled) "悬浮球已开" else "悬浮球已关",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = hubTextColor,
                )
            }
        }

        // 顶部分段大胶囊：声音同传 vs 屏幕取词
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isAudio = selectedMode == 0
            val audioBgColor by animateColorAsState(
                targetValue = if (isAudio) MiuixTheme.colorScheme.surface else Color.Transparent,
                animationSpec = tween(200),
                label = "audioBgColor",
            )
            val audioTextColor by animateColorAsState(
                targetValue = if (isAudio) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                animationSpec = tween(200),
                label = "audioTextColor",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(audioBgColor)
                    .clickable { selectedMode = 0 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🎙️ 声音同传",
                    fontWeight = if (isAudio) FontWeight.Bold else FontWeight.Medium,
                    color = audioTextColor,
                    fontSize = 14.5.sp,
                )
            }
            val isScreen = selectedMode == 1
            val screenBgColor by animateColorAsState(
                targetValue = if (isScreen) MiuixTheme.colorScheme.surface else Color.Transparent,
                animationSpec = tween(200),
                label = "screenBgColor",
            )
            val screenTextColor by animateColorAsState(
                targetValue = if (isScreen) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                animationSpec = tween(200),
                label = "screenTextColor",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(screenBgColor)
                    .clickable { selectedMode = 1 }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🔍 屏幕取词",
                    fontWeight = if (isScreen) FontWeight.Bold else FontWeight.Medium,
                    color = screenTextColor,
                    fontSize = 14.5.sp,
                )
            }
        }

        // 统一语言设置卡片（作为中央核心枢纽）
        val isVoice = selectedMode == 0
        val isVoiceRecognizeOnly = isVoice && settings.voiceRecognizeOnly
        SectionCard {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { picker = LangPicker.Source }
                            .padding(4.dp),
                    ) {
                        Text(
                            text = "源语言 (SOURCE)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = stringResource(SupportedLanguages.labelResOf(settings.sourceLanguageCode)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                    }
                    val canSwap = settings.sourceLanguageCode != "auto" && !isVoiceRecognizeOnly
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (canSwap) Booth.Accent.copy(alpha = 0.12f) else MiuixTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = canSwap) {
                                val currentSrc = settings.sourceLanguageCode
                                val currentTgt = settings.targetLanguageCode
                                onSourceLanguage(currentTgt)
                                onTargetLanguage(currentSrc)
                            }
                            .padding(10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (isVoiceRecognizeOnly) "→" else "⇄",
                            fontWeight = FontWeight.Bold,
                            color = if (canSwap) Booth.Accent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 16.sp,
                        )
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = !isVoiceRecognizeOnly) { picker = LangPicker.Target }
                            .padding(4.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "目标语言 (TARGET)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = if (isVoiceRecognizeOnly) "（不翻译 · 仅听写）" else stringResource(SupportedLanguages.labelResOf(settings.targetLanguageCode)),
                            fontSize = if (isVoiceRecognizeOnly) 13.5.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isVoiceRecognizeOnly) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                        )
                    }
                }
                if (isVoice) {
                    SettingDivider()
                    SettingSwitchRow(
                        title = "仅识别（不翻译）",
                        summary = "直接打出原始语音识别字幕，端侧离线运行，免 API Key、不消耗 Token",
                        checked = settings.voiceRecognizeOnly,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(voiceRecognizeOnly = checked) }
                        },
                    )
                }
            }
        }

        AnimatedContent(
            targetState = selectedMode,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally(tween(240)) { it / 3 } + fadeIn(tween(240))).togetherWith(
                        slideOutHorizontally(tween(200)) { -it / 3 } + fadeOut(tween(180)),
                    )
                } else {
                    (slideInHorizontally(tween(240)) { -it / 3 } + fadeIn(tween(240))).togetherWith(
                        slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(180)),
                    )
                }
            },
            label = "ModeSwitchAnimation",
        ) { mode ->
            if (mode == 0) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ================= 声音同传面板 =================
                    SectionCard {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "声音来源",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                StatusPill(label = statusLabel, tone = tone)
                            }

                            // 声音来源横向胶囊切换器
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                val isLegacyAndroid = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                                val mediaLabel = if (isLegacyAndroid) "媒体音(需安卓10+)" else "📻 媒体音"
                                SourceSegmentChip(
                                    label = mediaLabel,
                                    selected = settings.audioSourceMode == AudioSourceMode.MEDIA,
                                    onClick = {
                                        if (isLegacyAndroid) {
                                            Toast.makeText(
                                                context,
                                                R.string.audio_media_unsupported_on_legacy_android,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            onAudioSource(AudioSourceMode.MIC)
                                        } else {
                                            onAudioSource(AudioSourceMode.MEDIA)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                SourceSegmentChip(
                                    label = "🎙️ 麦克风",
                                    selected = settings.audioSourceMode == AudioSourceMode.MIC,
                                    onClick = { onAudioSource(AudioSourceMode.MIC) },
                                    modifier = Modifier.weight(1f),
                                )
                                val bothLabel = if (isLegacyAndroid) "混音(需10+)" else "混音"
                                SourceSegmentChip(
                                    label = bothLabel,
                                    selected = settings.audioSourceMode == AudioSourceMode.MEDIA_AND_MIC,
                                    onClick = {
                                        if (isLegacyAndroid) {
                                            Toast.makeText(
                                                context,
                                                R.string.audio_media_unsupported_on_legacy_android,
                                                Toast.LENGTH_LONG,
                                            ).show()
                                            onAudioSource(AudioSourceMode.MIC)
                                        } else {
                                            onAudioSource(AudioSourceMode.MEDIA_AND_MIC)
                                        }
                                    },
                                    modifier = Modifier.weight(0.9f),
                                )
                            }

                            if (running) {
                                AudioLevelRow(level = session.audioLevel, state = session.captureState)
                            }

                            if (session.message.isNotBlank()) {
                                Text(
                                    text = session.message,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 大发射按钮（声音同传）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = { if (running) onStop() else onStart() },
                            enabled = !ocrRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = if (running) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = if (running) "⏹ 停止声音同传" else if (settings.voiceRecognizeOnly) "▶ 开始实时听写（仅识别）" else "▶ 开始同传翻译",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                            )
                        }

                        if (ocrRunning) {
                            Text(
                                text = stringResource(R.string.subtitle_audio_busy),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }

                        if (!canDrawOverlays) {
                            Text(
                                text = stringResource(R.string.subtitle_need_overlay),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // ================= 屏幕取词面板 =================
                    SectionCard {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "识别语种与选区",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                StatusPill(
                                    label = if (ocrRunning) "取词中" else "未启动",
                                    tone = if (ocrRunning) StatusTone.Ok else StatusTone.Idle,
                                )
                            }

                            OcrScriptRow(
                                selected = settings.ocrScript,
                                onSelect = { script -> onUpdateSettings { it.copy(ocrScript = script) } },
                            )

                            SettingSwitchRow(
                                title = stringResource(R.string.ocr_outline_enabled),
                                summary = stringResource(R.string.ocr_outline_enabled_summary),
                                checked = settings.ocrRegionOutlineEnabled,
                                onCheckedChange = { v ->
                                    onUpdateSettings { it.copy(ocrRegionOutlineEnabled = v) }
                                },
                            )

                            if (ocrSession.message.isNotBlank()) {
                                Text(
                                    text = ocrSession.message,
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceSecondary,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 大发射按钮（屏幕取词）
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Button(
                            onClick = {
                                if (ocrRunning) {
                                    onOcrStop()
                                } else {
                                    onOcrStart()
                                }
                            },
                            enabled = !running,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            colors = if (ocrRunning) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = if (ocrRunning) "⏹ 停止屏幕取词" else "▶ 开始画框取词",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.5.sp,
                            )
                        }

                        if (running) {
                            Text(
                                text = stringResource(R.string.subtitle_ocr_busy),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            )
                        }
                    }
                }
            }
        }

        // 实时预览卡片（当存在识别或翻译文本时显现）
        val hasPreview = (selectedMode == 0 && (session.outputPreview.isNotBlank() || session.inputPreview.isNotBlank())) ||
            (selectedMode == 1 && (ocrSession.outputPreview.isNotBlank() || ocrSession.inputPreview.isNotBlank()))
        AnimatedVisibility(
            visible = hasPreview,
            enter = expandVertically(tween(220)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val inText = if (selectedMode == 0) session.inputPreview else ocrSession.inputPreview
                    val outText = if (selectedMode == 0) session.outputPreview else ocrSession.outputPreview
                    if (inText.isNotBlank()) {
                        Text(
                            text = inText,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (outText.isNotBlank()) {
                        Text(
                            text = outText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        // 导出按钮
        if (session.canExport && !running && selectedMode == 0) {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(text = stringResource(R.string.subtitle_export_txt), onClick = onExport)
            }
        }

        // 离线与静态小工具 4 宫格快捷托盘
        QuickToolsTray(
            onOpenDocument = { activeTool = StaticModeTarget.DOCUMENT },
            onOpenImage = { activeTool = StaticModeTarget.IMAGE },
            onOpenVideo = { activeTool = StaticModeTarget.VIDEO },
            onOpenText = { activeTool = StaticModeTarget.TEXT },
        )

        // 外观个性化设置（折叠收纳抽屉，根据顶部所选模式自适应）
        val isAudio = selectedMode == 0
        SectionCard {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { appearanceExpanded = !appearanceExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(text = "🎨", fontSize = 16.sp)
                        Column {
                            Text(
                                text = if (isAudio) "字幕外观微调" else "取词框与气泡微调",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (appearanceExpanded) {
                                    "点击折叠设置面板"
                                } else if (isAudio) {
                                    "字号、双语排版、文字颜色、背景透明度"
                                } else {
                                    "选区框颜色、明显程度、气泡字号与颜色"
                                },
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    val appearanceArrowRotation by animateFloatAsState(
                        targetValue = if (appearanceExpanded) 180f else 0f,
                        animationSpec = tween(220),
                        label = "appearanceArrowRotation",
                    )
                    Text(
                        text = "▼",
                        modifier = Modifier.rotate(appearanceArrowRotation),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                AnimatedVisibility(
                    visible = appearanceExpanded,
                    enter = expandVertically(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    ) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "实时效果预览",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (isAudio) {
                        // ================= 声音同传字幕外观 =================
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Color(
                                        red = 0.08f,
                                        green = 0.08f,
                                        blue = 0.10f,
                                        alpha = settings.backgroundAlpha.coerceIn(0.1f, 1f),
                                    ),
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(36.dp)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.35f)),
                                )
                                when (settings.displayMode) {
                                    SubtitleDisplayMode.BOTH -> {
                                        Text(
                                            text = "Live transcription demo text",
                                            fontSize = settings.fontSizeSp.sp,
                                            color = Color(settings.sourceTextColor.toInt()),
                                            lineHeight = (settings.fontSizeSp * 1.35f).sp,
                                        )
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(1.dp)
                                                .background(Color.White.copy(alpha = 0.18f)),
                                        )
                                        Text(
                                            text = "实时字幕预览示例",
                                            fontSize = settings.fontSizeSp.sp,
                                            color = Color(settings.translationTextColor.toInt()),
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = (settings.fontSizeSp * 1.35f).sp,
                                        )
                                    }
                                    SubtitleDisplayMode.SOURCE -> {
                                        Text(
                                            text = "Live transcription demo text",
                                            fontSize = settings.fontSizeSp.sp,
                                            color = Color(settings.sourceTextColor.toInt()),
                                            lineHeight = (settings.fontSizeSp * 1.35f).sp,
                                        )
                                    }
                                    SubtitleDisplayMode.TRANSLATION -> {
                                        Text(
                                            text = "实时字幕预览示例",
                                            fontSize = settings.fontSizeSp.sp,
                                            color = Color(settings.translationTextColor.toInt()),
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = (settings.fontSizeSp * 1.35f).sp,
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.settings_font_size, settings.fontSizeSp.roundToInt()),
                            fontSize = 13.sp,
                        )
                        Slider(
                            value = settings.fontSizeSp,
                            onValueChange = { s -> onUpdateSettings { it.copy(fontSizeSp = s.roundToInt().toFloat()) } },
                            valueRange = 12f..32f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_bg_alpha,
                                (settings.backgroundAlpha * 100).toInt(),
                            ),
                            fontSize = 13.sp,
                        )
                        Slider(
                            value = settings.backgroundAlpha,
                            onValueChange = { a -> onUpdateSettings { it.copy(backgroundAlpha = a) } },
                            valueRange = 0.1f..0.95f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "译文分段",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = when (settings.sentencesPerParagraph) {
                                    0 -> "不分段（连续输出）"
                                    1 -> "每句一行"
                                    else -> "每 ${settings.sentencesPerParagraph} 句一行"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Slider(
                            value = settings.sentencesPerParagraph.toFloat().coerceIn(
                                0f,
                                UserSettings.Defaults.SENTENCES_PER_PARAGRAPH_MAX.toFloat(),
                            ),
                            onValueChange = { v ->
                                onUpdateSettings {
                                    it.copy(
                                        sentencesPerParagraph = v.roundToInt().coerceIn(
                                            0,
                                            UserSettings.Defaults.SENTENCES_PER_PARAGRAPH_MAX,
                                        ),
                                    )
                                }
                            },
                            valueRange = 0f..UserSettings.Defaults.SENTENCES_PER_PARAGRAPH_MAX.toFloat(),
                            steps = UserSettings.Defaults.SENTENCES_PER_PARAGRAPH_MAX - 1,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_bilingual),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        OptionRow(
                            label = stringResource(R.string.display_mode_both),
                            summary = stringResource(R.string.display_mode_both_summary),
                            selected = settings.displayMode == SubtitleDisplayMode.BOTH,
                            onClick = { onUpdateSettings { it.copy(displayMode = SubtitleDisplayMode.BOTH) } },
                            horizontalPadding = 0.dp,
                        )
                        OptionRow(
                            label = stringResource(R.string.display_mode_source),
                            summary = stringResource(R.string.display_mode_source_summary),
                            selected = settings.displayMode == SubtitleDisplayMode.SOURCE,
                            onClick = { onUpdateSettings { it.copy(displayMode = SubtitleDisplayMode.SOURCE) } },
                            horizontalPadding = 0.dp,
                        )
                        OptionRow(
                            label = stringResource(R.string.display_mode_translation),
                            summary = stringResource(R.string.display_mode_translation_summary),
                            selected = settings.displayMode == SubtitleDisplayMode.TRANSLATION,
                            onClick = {
                                onUpdateSettings { it.copy(displayMode = SubtitleDisplayMode.TRANSLATION) }
                            },
                            horizontalPadding = 0.dp,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_source_color),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        ColorSwatchRow(
                            selected = settings.sourceTextColor,
                            onSelect = { c -> onUpdateSettings { it.copy(sourceTextColor = c) } },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.settings_translation_color),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        ColorSwatchRow(
                            selected = settings.translationTextColor,
                            onSelect = { c -> onUpdateSettings { it.copy(translationTextColor = c) } },
                        )
                        TextButton(
                            text = stringResource(R.string.settings_reset_appearance),
                            onClick = onResetAppearance,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    } else {
                        // ================= 屏幕取词框与气泡外观 =================
                        val outlineColor = Color(settings.ocrRegionOutlineColor.toInt())
                        val outlineAlpha = if (settings.ocrRegionOutlineEnabled) {
                            settings.ocrRegionOutlineAlpha.coerceIn(0.1f, 1f)
                        } else {
                            0.15f
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF14151B))
                                .padding(14.dp),
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // 模拟屏幕取词区域框（按用户配置的边框颜色与透明度）
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(68.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            outlineColor.copy(
                                                alpha = if (settings.ocrRegionOutlineEnabled) {
                                                    (outlineAlpha * 0.12f).coerceIn(0.02f, 0.3f)
                                                } else 0.02f,
                                            ),
                                        )
                                        .border(
                                            width = if (settings.ocrRegionOutlineEnabled) 2.dp else 1.dp,
                                            color = outlineColor.copy(alpha = outlineAlpha),
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        if (settings.ocrRegionOutlineEnabled) outlineColor else Color.Gray,
                                                    ),
                                            )
                                            Text(
                                                text = if (settings.ocrRegionOutlineEnabled) {
                                                    "正在取词区域（取词框）"
                                                } else {
                                                    "取词选区框已隐藏"
                                                },
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (settings.ocrRegionOutlineEnabled) {
                                                    outlineColor
                                                } else {
                                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                                },
                                            )
                                        }
                                        Text(
                                            text = "拖动四角可调",
                                            fontSize = 10.sp,
                                            color = Color.White.copy(alpha = 0.4f),
                                        )
                                    }
                                }

                                // 模拟取词译文气泡（模拟悬浮球旁弹出的译文框）
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xEC14141C))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "屏幕取词实时译文预览效果",
                                                fontSize = settings.fontSizeSp.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(settings.translationTextColor.toInt()),
                                                lineHeight = (settings.fontSizeSp * 1.35f).sp,
                                            )
                                        }
                                        Text(
                                            text = "✕",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(start = 6.dp),
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        SettingSwitchRow(
                            title = stringResource(R.string.ocr_outline_enabled),
                            summary = stringResource(R.string.ocr_outline_enabled_summary),
                            checked = settings.ocrRegionOutlineEnabled,
                            onCheckedChange = { v ->
                                onUpdateSettings { it.copy(ocrRegionOutlineEnabled = v) }
                            },
                        )

                        if (settings.ocrRegionOutlineEnabled) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.ocr_outline_color),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            ColorSwatchRow(
                                selected = settings.ocrRegionOutlineColor,
                                onSelect = { c -> onUpdateSettings { it.copy(ocrRegionOutlineColor = c) } },
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "选区框明显程度",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = "${(settings.ocrRegionOutlineAlpha * 100).toInt()}%",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                            Slider(
                                value = settings.ocrRegionOutlineAlpha,
                                onValueChange = { v ->
                                    onUpdateSettings { it.copy(ocrRegionOutlineAlpha = v) }
                                },
                                valueRange = 0.1f..1f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "气泡译文字号",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = "${settings.fontSizeSp.roundToInt()} sp",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Slider(
                            value = settings.fontSizeSp,
                            onValueChange = { s -> onUpdateSettings { it.copy(fontSizeSp = s.roundToInt().toFloat()) } },
                            valueRange = 12f..32f,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "气泡译文字体颜色",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        ColorSwatchRow(
                            selected = settings.translationTextColor,
                            onSelect = { c -> onUpdateSettings { it.copy(translationTextColor = c) } },
                        )

                        TextButton(
                            text = "重置为默认取词外观",
                            onClick = onResetOcrAppearance,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }

        Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

    val currentPicker = picker
    if (currentPicker != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val options: List<LanguageOption> = when (currentPicker) {
            LangPicker.Source -> SupportedLanguages.sourceOptions
            LangPicker.Target -> SupportedLanguages.targetOptions
        }
        val selected = when (currentPicker) {
            LangPicker.Source -> settings.sourceLanguageCode
            LangPicker.Target -> settings.targetLanguageCode
        }
        ModalBottomSheet(
            onDismissRequest = { picker = null },
            sheetState = sheetState,
            containerColor = MiuixTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = stringResource(
                        if (currentPicker == LangPicker.Source) {
                            R.string.subtitle_pick_source
                        } else {
                            R.string.subtitle_pick_target
                        },
                    ),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    itemsIndexed(options) { _, option ->
                        val selectedRow = option.code == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (currentPicker) {
                                        LangPicker.Source -> onSourceLanguage(option.code)
                                        LangPicker.Target -> onTargetLanguage(option.code)
                                    }
                                    picker = null
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(option.labelRes),
                                modifier = Modifier.weight(1f),
                                fontWeight = if (selectedRow) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selectedRow) {
                                    Booth.Accent
                                } else {
                                    MiuixTheme.colorScheme.onSurface
                                },
                            )
                            if (selectedRow) {
                                Text(
                                    text = stringResource(R.string.subtitle_selected),
                                    color = Booth.Accent,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickToolsTray(
    onOpenDocument: () -> Unit,
    onOpenImage: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenText: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                Text(
                    text = "实用翻译工具",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurfaceVariantSummary,
                )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                QuickToolItem(
                    emoji = "📄",
                    title = "文档翻译",
                    subtitle = "PDF/Word",
                    onClick = onOpenDocument,
                    modifier = Modifier.weight(1f),
                )
                QuickToolItem(
                    emoji = "🖼️",
                    title = "图片翻译",
                    subtitle = "拍照/相册",
                    onClick = onOpenImage,
                    modifier = Modifier.weight(1f),
                )
                QuickToolItem(
                    emoji = "🎬",
                    title = "视频翻译",
                    subtitle = "离线字幕",
                    onClick = onOpenVideo,
                    modifier = Modifier.weight(1f),
                )
                QuickToolItem(
                    emoji = "📝",
                    title = "文本翻译",
                    subtitle = "分段长文",
                    onClick = onOpenText,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun QuickToolItem(
    emoji: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(scheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 20.sp)
        }
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurface,
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = scheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun SourceSegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    val animatedBg by animateColorAsState(
        targetValue = if (selected) Booth.Accent.copy(alpha = 0.15f) else scheme.surfaceVariant,
        animationSpec = tween(200),
        label = "chipBg",
    )
    val animatedText by animateColorAsState(
        targetValue = if (selected) Booth.Accent else scheme.onSurface,
        animationSpec = tween(200),
        label = "chipText",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(animatedBg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = animatedText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AudioLevelRow(level: Float, state: CaptureState) {
    val scheme = MiuixTheme.colorScheme
    val (labelRes, dotColor) = when (state) {
        CaptureState.STARTING -> R.string.audio_level_starting to scheme.onSurfaceVariantSummary
        CaptureState.FLOWING -> R.string.audio_level_flowing to Color(0xFF34C759)
        CaptureState.QUIET -> R.string.audio_level_quiet to scheme.onSurfaceVariantSummary
        CaptureState.DIGITAL_SILENCE -> R.string.audio_level_digital_silence to Color(0xFFFF5C5C)
        CaptureState.STALLED -> R.string.audio_level_stalled to Color(0xFFFF5C5C)
    }
    val animatedLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(120),
        label = "audioLevel",
    )
    val animatedDotColor by animateColorAsState(
        targetValue = dotColor,
        animationSpec = tween(200),
        label = "dotColor",
    )
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(scheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedLevel.coerceIn(0.01f, 1f))
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (state == CaptureState.FLOWING) Color(0xFF34C759) else scheme.onSurfaceVariantSummary),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(animatedDotColor),
            )
            Text(
                text = stringResource(labelRes),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    summary: String? = null,
    selected: Boolean,
    onClick: () -> Unit,
    horizontalPadding: Dp = 16.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column {
                Text(
                    text = label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                )
                if (summary != null) {
                    Text(
                        text = summary,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        if (selected) {
            Text(text = "✓", color = Booth.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

/** One compact row instead of five radio rows: the OCR script only rarely changes. */
@Composable
private fun OcrScriptRow(
    selected: OcrScript,
    onSelect: (OcrScript) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = OcrScript.entries
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(horizontal = 0.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "识别语种",
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(selected.labelRes()),
                    color = Booth.Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = " ▾",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = stringResource(option.labelRes()),
                            fontSize = 14.sp,
                            fontWeight = if (option == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == selected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun OcrScript.labelRes(): Int = when (this) {
    OcrScript.AUTO -> R.string.ocr_script_auto
    OcrScript.LATIN -> R.string.ocr_script_latin
    OcrScript.CHINESE_MIX -> R.string.ocr_script_chinese
    OcrScript.JAPANESE -> R.string.ocr_script_japanese
    OcrScript.KOREAN -> R.string.ocr_script_korean
}

private val SUBTITLE_COLOR_PRESETS: List<Long> = listOf(
    0xFFFFFFFF, 0xFFD6D6D6, 0xFFB0BEC5, 0xFF000000, 0xFFFFD600,
    0xFFFFC400, 0xFFFF9800, 0xFFFF5252, 0xFFE91E8C, 0xFFCE93D8,
    0xFF448AFF, 0xFF00E5FF, 0xFF69F0AE, 0xFF00C853,
)

@Composable
private fun ColorSwatchRow(
    selected: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SUBTITLE_COLOR_PRESETS.chunked(7).forEach { rowColors ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowColors.forEach { c ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(CircleShape)
                            .background(Color(c.toInt()))
                            .then(
                                if (c == selected) {
                                    Modifier.border(3.dp, Booth.Accent, CircleShape)
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelect(c) },
                    )
                }
            }
        }
    }
}
