package com.xzm.realtimetranslate.ui.subtitle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.data.LanguageOption
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.service.ScreenOcrBus
import com.xzm.realtimetranslate.service.SessionBus
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.StatusPill
import com.xzm.realtimetranslate.ui.components.StatusTone
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
) {
    val running = session.status == SessionBus.Status.Running ||
        session.status == SessionBus.Status.Starting
    val ocrRunning = ocrSession.status == ScreenOcrBus.Status.Running ||
        ocrSession.status == ScreenOcrBus.Status.Starting

    var picker by remember { mutableStateOf<LangPicker?>(null) }
    var selectedMode by remember(ocrRunning, running) {
        mutableIntStateOf(if (ocrRunning && !running) 1 else 0)
    }

    val (statusLabel, tone) = when (session.status) {
        SessionBus.Status.Idle -> stringResource(R.string.status_idle) to StatusTone.Idle
        SessionBus.Status.Starting -> stringResource(R.string.status_starting) to StatusTone.Working
        SessionBus.Status.Running -> stringResource(R.string.status_running) to StatusTone.Ok
        SessionBus.Status.Error -> stringResource(R.string.status_error) to StatusTone.Error
        SessionBus.Status.Stopped -> stringResource(R.string.status_stopped) to StatusTone.Warn
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.subtitle_title),
            subtitle = stringResource(R.string.subtitle_subtitle),
        )

        // 顶部分段胶囊：声音同传 vs 屏幕取词
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val isAudio = selectedMode == 0
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isAudio) MiuixTheme.colorScheme.surface else Color.Transparent)
                    .clickable { selectedMode = 0 }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🎙️ 声音同传",
                    fontWeight = if (isAudio) FontWeight.Bold else FontWeight.Normal,
                    color = if (isAudio) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
            val isOcr = selectedMode == 1
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (isOcr) MiuixTheme.colorScheme.surface else Color.Transparent)
                    .clickable { selectedMode = 1 }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "🔍 屏幕取词",
                    fontWeight = if (isOcr) FontWeight.Bold else FontWeight.Normal,
                    color = if (isOcr) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
        }

        if (selectedMode == 0) {
            // ---- 声音同传模式 ----
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.subtitle_hero_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(
                                    if (running) {
                                        R.string.subtitle_hero_running
                                    } else {
                                        R.string.subtitle_hero_idle
                                    },
                                ),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        StatusPill(label = statusLabel, tone = tone)
                    }

                    if (session.message.isNotBlank()) {
                        Text(
                            text = session.message,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Button(
                        onClick = { if (running) onStop() else onStart() },
                        enabled = !ocrRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = if (running) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColorsPrimary()
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (running) R.string.subtitle_stop else R.string.subtitle_start,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }

                    if (ocrRunning) {
                        Text(
                            text = stringResource(R.string.subtitle_audio_busy),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                        )
                    }

                    if (!canDrawOverlays) {
                        Text(
                            text = stringResource(R.string.subtitle_need_overlay),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        } else {
            // ---- 屏幕取词模式 ----
            SmallTitle(
                text = stringResource(R.string.settings_ocr),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_ocr_desc),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    if (ocrSession.message.isNotBlank()) {
                        Text(
                            text = ocrSession.message,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { if (ocrRunning) onOcrStop() else onOcrStart() },
                        enabled = !running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = if (ocrRunning) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColorsPrimary()
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (ocrRunning) R.string.subtitle_stop_ocr
                                else R.string.subtitle_start_ocr,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                    if (running) {
                        Text(
                            text = stringResource(R.string.subtitle_ocr_busy),
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 12.sp,
                        )
                    }
                    if (ocrSession.inputPreview.isNotBlank() || ocrSession.outputPreview.isNotBlank()) {
                        if (ocrSession.inputPreview.isNotBlank()) {
                            Text(
                                text = ocrSession.inputPreview,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (ocrSession.outputPreview.isNotBlank()) {
                            Text(
                                text = ocrSession.outputPreview,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // 语言设置卡片（双向语言互换按钮）
        SmallTitle(
            text = stringResource(R.string.subtitle_language),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LanguageChip(
                        title = stringResource(R.string.subtitle_source),
                        value = stringResource(SupportedLanguages.labelResOf(settings.sourceLanguageCode)),
                        onClick = { picker = LangPicker.Source },
                        modifier = Modifier.weight(1f),
                    )
                    val canSwap = settings.sourceLanguageCode != "auto"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (canSwap) Booth.Accent.copy(alpha = 0.14f) else MiuixTheme.colorScheme.surfaceVariant)
                            .clickable(enabled = canSwap) {
                                val currentSrc = settings.sourceLanguageCode
                                val currentTgt = settings.targetLanguageCode
                                onSourceLanguage(currentTgt)
                                onTargetLanguage(currentSrc)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "⇄",
                            fontWeight = FontWeight.Bold,
                            color = if (canSwap) Booth.Accent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = 16.sp,
                        )
                    }
                    LanguageChip(
                        title = stringResource(R.string.subtitle_target),
                        value = stringResource(SupportedLanguages.labelResOf(settings.targetLanguageCode)),
                        onClick = { picker = LangPicker.Target },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (selectedMode == 0) {
            SmallTitle(
                text = stringResource(R.string.subtitle_audio_source),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(modifier = Modifier.padding(vertical = 6.dp)) {
                    AudioSourceRow(
                        label = stringResource(R.string.audio_source_media),
                        selected = settings.audioSourceMode == AudioSourceMode.MEDIA,
                        enabled = !running,
                        onClick = { onAudioSource(AudioSourceMode.MEDIA) },
                    )
                    AudioSourceRow(
                        label = stringResource(R.string.audio_source_mic),
                        selected = settings.audioSourceMode == AudioSourceMode.MIC,
                        enabled = !running,
                        onClick = { onAudioSource(AudioSourceMode.MIC) },
                    )
                    AudioSourceRow(
                        label = stringResource(R.string.audio_source_both),
                        selected = settings.audioSourceMode == AudioSourceMode.MEDIA_AND_MIC,
                        enabled = !running,
                        onClick = { onAudioSource(AudioSourceMode.MEDIA_AND_MIC) },
                    )
                }
            }
        }

        if (session.outputPreview.isNotBlank() || session.inputPreview.isNotBlank()) {
            SmallTitle(
                text = stringResource(R.string.subtitle_preview),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (session.inputPreview.isNotBlank()) {
                        Text(
                            text = session.inputPreview,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (session.outputPreview.isNotBlank()) {
                        Text(
                            text = session.outputPreview,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (session.canExport && !running) {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = stringResource(R.string.subtitle_export_txt),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (!exportMessage.isNullOrBlank()) {
                        Text(
                            text = exportMessage,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
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
private fun AudioSourceRow(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                !enabled -> MiuixTheme.colorScheme.disabledOnSurface
                selected -> Booth.Accent
                else -> MiuixTheme.colorScheme.onSurface
            },
        )
        if (selected) {
            Text(text = "✓", color = Booth.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun LanguageChip(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MiuixTheme.colorScheme.onSurface,
        )
    }
}
