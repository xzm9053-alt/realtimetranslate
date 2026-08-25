package com.xzm.realtimetranslate.ui.settings

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.BuildConfig
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.OcrScript
import com.xzm.realtimetranslate.data.SubtitleDisplayMode
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingSwitchRow
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    onOpenOverlayPermission: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val deepSeekKey by viewModel.deepSeekKey.collectAsStateWithLifecycle()
    val deepSeekModel by viewModel.deepSeekModel.collectAsStateWithLifecycle()
    val deepSeekBaseUrl by viewModel.deepSeekBaseUrl.collectAsStateWithLifecycle()
    val mirrorUrl by viewModel.mirrorUrl.collectAsStateWithLifecycle()
    val hfToken by viewModel.hfToken.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()

    var revealKey by remember { mutableStateOf(false) }
    val isDeepSeek = settings.translationEngine == TranslationEngineType.DEEPSEEK
    val context = LocalContext.current

    // Dark-mode safe field colors (explicit text / cursor colors)
    val scheme = MiuixTheme.colorScheme
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = scheme.onSurface,
        unfocusedTextColor = scheme.onSurface,
        disabledTextColor = scheme.disabledOnSurface,
        focusedBorderColor = Booth.Accent,
        unfocusedBorderColor = scheme.outline.copy(alpha = 0.45f),
        focusedLabelColor = Booth.Accent,
        unfocusedLabelColor = scheme.onSurfaceVariantSummary,
        cursorColor = Booth.Accent,
        focusedContainerColor = scheme.surface,
        unfocusedContainerColor = scheme.surface,
        focusedPlaceholderColor = scheme.onSurfaceVariantSummary,
        unfocusedPlaceholderColor = scheme.onSurfaceVariantSummary,
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.settings_title),
            subtitle = stringResource(R.string.settings_subtitle),
        )

        SmallTitle(
            text = stringResource(R.string.settings_engine),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_engine_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                OptionRow(
                    label = stringResource(R.string.engine_deepseek),
                    summary = stringResource(R.string.engine_deepseek_summary),
                    selected = isDeepSeek,
                    onClick = { viewModel.setEngine(TranslationEngineType.DEEPSEEK) },
                )
                OptionRow(
                    label = stringResource(R.string.engine_microsoft),
                    summary = stringResource(R.string.engine_microsoft_summary),
                    selected = !isDeepSeek,
                    onClick = { viewModel.setEngine(TranslationEngineType.MICROSOFT) },
                )
            }
        }

        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isDeepSeek) {
                    OutlinedTextField(
                        value = deepSeekKey,
                        onValueChange = viewModel::setDeepSeekKey,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            androidx.compose.material3.Text(stringResource(R.string.settings_deepseek_key))
                        },
                        singleLine = true,
                        visualTransformation = if (revealKey) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                    )
                    TextButton(
                        text = stringResource(
                            if (revealKey) R.string.settings_hide else R.string.settings_show,
                        ),
                        onClick = { revealKey = !revealKey },
                    )
                    OutlinedTextField(
                        value = deepSeekModel,
                        onValueChange = viewModel::updateDeepSeekModel,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            androidx.compose.material3.Text(stringResource(R.string.settings_deepseek_model))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                    )
                    OutlinedTextField(
                        value = deepSeekBaseUrl,
                        onValueChange = viewModel::updateDeepSeekBaseUrl,
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            androidx.compose.material3.Text(stringResource(R.string.settings_deepseek_base_url))
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = fieldColors,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.settings_microsoft_desc),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                Button(
                    onClick = {
                        viewModel.saveDeepSeekKey()
                        viewModel.testConnection()
                    },
                    enabled = !testing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = stringResource(
                            if (testing) R.string.settings_testing else R.string.settings_test_engine,
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (isDeepSeek) {
                    TextButton(
                        text = stringResource(R.string.settings_save_key_only),
                        onClick = viewModel::saveDeepSeekKey,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (!testResult.isNullOrBlank()) {
                    val resultText = testResult.orEmpty()
                    val hasOk = resultText.contains("✅")
                    val hasFail = resultText.contains("❌")
                    Text(
                        text = resultText,
                        fontSize = 13.sp,
                        color = when {
                            hasOk && !hasFail -> Booth.Success
                            hasFail && !hasOk -> Booth.Danger
                            else -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                        },
                    )
                }
            }
        }

        SmallTitle(
            text = stringResource(R.string.settings_model),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_model_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = modelStatus.ifBlank { stringResource(R.string.settings_model_unknown) },
                    fontSize = 13.sp,
                )
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = mirrorUrl,
                    onValueChange = viewModel::updateMirrorUrl,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        androidx.compose.material3.Text(stringResource(R.string.settings_model_mirror))
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                )
                OutlinedTextField(
                    value = hfToken,
                    onValueChange = viewModel::updateHfToken,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        androidx.compose.material3.Text(stringResource(R.string.settings_model_hf_token))
                    },
                    singleLine = true,
                    visualTransformation = if (revealKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors,
                )
                Button(
                    onClick = {
                        if (downloading) viewModel.refreshModelStatus()
                        else viewModel.downloadModels()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (downloading) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColorsPrimary()
                    },
                ) {
                    Text(
                        text = stringResource(
                            if (downloading) {
                                R.string.settings_model_downloading
                            } else {
                                R.string.settings_model_download
                            },
                        ),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        SmallTitle(
            text = stringResource(R.string.settings_vad),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_vad_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.settings_vad_pause_value, settings.vadMinSilenceSec),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Slider(
                    value = settings.vadMinSilenceSec.coerceIn(0.1f, 1.0f),
                    onValueChange = { v ->
                        viewModel.update { it.copy(vadMinSilenceSec = (v * 10).roundToInt() / 10f) }
                    },
                    valueRange = 0.1f..1.0f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_vad_max_value, settings.vadMaxSpeechSec),
                    fontSize = 13.sp,
                )
                Slider(
                    value = settings.vadMaxSpeechSec.coerceIn(1f, 10f),
                    onValueChange = { v ->
                        viewModel.update { it.copy(vadMaxSpeechSec = (v * 2).roundToInt() / 2f) }
                    },
                    valueRange = 1f..10f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SmallTitle(
            text = stringResource(R.string.settings_ocr),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_ocr_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                OptionRow(
                    label = stringResource(R.string.ocr_script_auto),
                    summary = stringResource(R.string.ocr_script_auto_summary),
                    selected = settings.ocrScript == OcrScript.AUTO,
                    onClick = { viewModel.setOcrScript(OcrScript.AUTO) },
                )
                OptionRow(
                    label = stringResource(R.string.ocr_script_latin),
                    summary = stringResource(R.string.ocr_script_latin_summary),
                    selected = settings.ocrScript == OcrScript.LATIN,
                    onClick = { viewModel.setOcrScript(OcrScript.LATIN) },
                )
                OptionRow(
                    label = stringResource(R.string.ocr_script_chinese),
                    summary = stringResource(R.string.ocr_script_chinese_summary),
                    selected = settings.ocrScript == OcrScript.CHINESE_MIX,
                    onClick = { viewModel.setOcrScript(OcrScript.CHINESE_MIX) },
                )
                OptionRow(
                    label = stringResource(R.string.ocr_script_japanese),
                    summary = stringResource(R.string.ocr_script_japanese_summary),
                    selected = settings.ocrScript == OcrScript.JAPANESE,
                    onClick = { viewModel.setOcrScript(OcrScript.JAPANESE) },
                )
                OptionRow(
                    label = stringResource(R.string.ocr_script_korean),
                    summary = stringResource(R.string.ocr_script_korean_summary),
                    selected = settings.ocrScript == OcrScript.KOREAN,
                    onClick = { viewModel.setOcrScript(OcrScript.KOREAN) },
                )
                SettingSwitchRow(
                    title = stringResource(R.string.ocr_outline_enabled),
                    summary = stringResource(R.string.ocr_outline_enabled_summary),
                    checked = settings.ocrRegionOutlineEnabled,
                    onCheckedChange = { v ->
                        viewModel.update { it.copy(ocrRegionOutlineEnabled = v) }
                    },
                )
                if (settings.ocrRegionOutlineEnabled) {
                    Text(
                        text = stringResource(R.string.ocr_outline_color),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    ColorSwatchRow(
                        selected = settings.ocrRegionOutlineColor,
                        onSelect = { c ->
                            viewModel.update { it.copy(ocrRegionOutlineColor = c) }
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.ocr_outline_alpha,
                                (settings.ocrRegionOutlineAlpha * 100).toInt(),
                            ),
                            fontSize = 13.sp,
                        )
                        Slider(
                            value = settings.ocrRegionOutlineAlpha,
                            onValueChange = { v ->
                                viewModel.update { it.copy(ocrRegionOutlineAlpha = v) }
                            },
                            valueRange = 0.1f..1f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        SmallTitle(
            text = stringResource(R.string.settings_appearance),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_font_size, settings.fontSizeSp.toInt()),
                    fontSize = 13.sp,
                )
                Slider(
                    value = settings.fontSizeSp,
                    onValueChange = { size -> viewModel.update { it.copy(fontSizeSp = size) } },
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
                    onValueChange = { a -> viewModel.update { it.copy(backgroundAlpha = a) } },
                    valueRange = 0.1f..0.95f,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                text = stringResource(R.string.settings_bilingual),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            OptionRow(
                label = stringResource(R.string.display_mode_both),
                summary = stringResource(R.string.display_mode_both_summary),
                selected = settings.displayMode == SubtitleDisplayMode.BOTH,
                onClick = { viewModel.update { it.copy(displayMode = SubtitleDisplayMode.BOTH) } },
            )
            OptionRow(
                label = stringResource(R.string.display_mode_source),
                summary = stringResource(R.string.display_mode_source_summary),
                selected = settings.displayMode == SubtitleDisplayMode.SOURCE,
                onClick = { viewModel.update { it.copy(displayMode = SubtitleDisplayMode.SOURCE) } },
            )
            OptionRow(
                label = stringResource(R.string.display_mode_translation),
                summary = stringResource(R.string.display_mode_translation_summary),
                selected = settings.displayMode == SubtitleDisplayMode.TRANSLATION,
                onClick = {
                    viewModel.update { it.copy(displayMode = SubtitleDisplayMode.TRANSLATION) }
                },
            )
            Text(
                text = stringResource(R.string.settings_source_color),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ColorSwatchRow(
                selected = settings.sourceTextColor,
                onSelect = { c -> viewModel.update { it.copy(sourceTextColor = c) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_translation_color),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            ColorSwatchRow(
                selected = settings.translationTextColor,
                onSelect = { c -> viewModel.update { it.copy(translationTextColor = c) } },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            TextButton(
                text = stringResource(R.string.settings_reset_appearance),
                onClick = viewModel::resetSubtitleAppearance,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        SmallTitle(
            text = stringResource(R.string.settings_history),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(vertical = 6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_history_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                OptionRow(
                    label = stringResource(R.string.history_mode_auto_clear),
                    summary = stringResource(R.string.history_mode_auto_clear_summary),
                    selected = settings.historyMode == HistoryMode.AUTO_CLEAR,
                    onClick = { viewModel.setHistoryMode(HistoryMode.AUTO_CLEAR) },
                )
                OptionRow(
                    label = stringResource(R.string.history_mode_save_all),
                    summary = stringResource(R.string.history_mode_save_all_summary),
                    selected = settings.historyMode == HistoryMode.SAVE_ALL,
                    onClick = { viewModel.setHistoryMode(HistoryMode.SAVE_ALL) },
                )
                if (settings.historyMode == HistoryMode.AUTO_CLEAR) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.history_max_entries,
                                settings.historyLimit,
                            ),
                            fontSize = 13.sp,
                        )
                        Slider(
                            value = settings.historyLimit.toFloat(),
                            onValueChange = { v -> viewModel.setHistoryLimit(v.toInt()) },
                            valueRange = UserSettings.Defaults.HISTORY_LIMIT_MIN.toFloat()..
                                UserSettings.Defaults.HISTORY_LIMIT_MAX.toFloat(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        SmallTitle(
            text = stringResource(R.string.settings_permissions),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.settings_open_overlay),
                    onClick = onOpenOverlayPermission,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_permissions_hint),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        SmallTitle(
            text = stringResource(R.string.settings_about),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.settings_bilibili,
                        stringResource(R.string.settings_bilibili_author),
                    ),
                    fontSize = 13.sp,
                    color = Booth.Accent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://space.bilibili.com/1366321010"),
                                    )
                                )
                            }
                        },
                )
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

@Composable
private fun OptionRow(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Column {
                Text(
                    text = label,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        if (selected) {
            Text(text = "✓", color = Booth.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

/** Preset subtitle text colors (ARGB as Long) — readable on the dark overlay. */
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
