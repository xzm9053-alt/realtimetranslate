package com.xzm.realtimetranslate.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
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
import com.xzm.realtimetranslate.data.TranslationEngineType
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
            SettingSwitchRow(
                title = stringResource(R.string.settings_bilingual),
                summary = stringResource(
                    if (settings.bilingual) {
                        R.string.settings_bilingual_on
                    } else {
                        R.string.settings_bilingual_off
                    },
                ),
                checked = settings.bilingual,
                onCheckedChange = { c -> viewModel.update { it.copy(bilingual = c) } },
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
            text = stringResource(R.string.settings_voice),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            SettingSwitchRow(
                title = stringResource(R.string.settings_play_voice),
                summary = stringResource(R.string.settings_play_voice_summary),
                checked = settings.playTranslatedAudio,
                onCheckedChange = { c -> viewModel.update { it.copy(playTranslatedAudio = c) } },
            )
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = buildString {
                        append(
                            stringResource(
                                R.string.settings_voice_volume,
                                (settings.translatedVolume * 100).toInt(),
                            ),
                        )
                        if (settings.translatedVolume > 1f) {
                            append(stringResource(R.string.settings_voice_volume_boost))
                        }
                    },
                    fontSize = 13.sp,
                )
                Slider(
                    value = settings.translatedVolume.coerceIn(0f, 2f),
                    onValueChange = { v ->
                        viewModel.update { it.copy(translatedVolume = v.coerceIn(0f, 2f)) }
                    },
                    valueRange = 0f..2f,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.settings_voice_volume_hint),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
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
            val context = LocalContext.current
            val githubUrl = stringResource(R.string.github_url).trim()
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.settings_github),
                    fontWeight = FontWeight.Medium,
                )
                if (githubUrl.isBlank()) {
                    Text(
                        text = stringResource(R.string.settings_github_pending),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                } else {
                    Text(
                        text = githubUrl,
                        fontSize = 13.sp,
                        color = Booth.Accent,
                    )
                    TextButton(
                        text = stringResource(R.string.settings_github_open),
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
