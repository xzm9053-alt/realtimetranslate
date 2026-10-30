package com.xzm.realtimetranslate.ui.text

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.LanguageOption
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.ui.components.LanguageChip
import com.xzm.realtimetranslate.ui.components.LanguagePickerSheet
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.theme.Booth
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LangField { Source, Target }

@Composable
fun TextTranslateScreen(
    viewModel: TextTranslateViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme

    var picker by remember { mutableStateOf<LangField?>(null) }
    var showCopied by remember { mutableStateOf(false) }

    LaunchedEffect(showCopied) {
        if (showCopied) {
            delay(1500)
            showCopied = false
        }
    }

    val sourceOptions = SupportedLanguages.sourceOptions
    val targetOptions = SupportedLanguages.targetOptions
    val sourceLabel = sourceOptions.firstOrNull { it.code == state.sourceCode }
        ?: sourceOptions.first()
    val targetLabel = targetOptions.firstOrNull { it.code == state.targetCode }
        ?: targetOptions.first()
    val canSwap = state.sourceCode != "auto"

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // ================= 顶部一体化导航栏 =================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(scheme.surfaceVariant)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "‹",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.text_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.text_subtitle),
                        fontSize = 11.5.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceVariant)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    text = "共 ${state.input.length} 字",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }

        // ================= 语言切换核心枢纽卡片 =================
        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                LanguageChip(
                    label = stringResource(R.string.text_source_lang),
                    value = stringResource(sourceLabel.labelRes),
                    modifier = Modifier.weight(1f),
                    onClick = { picker = LangField.Source },
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clip(CircleShape)
                        .background(if (canSwap) Booth.Accent.copy(alpha = 0.12f) else scheme.surfaceVariant)
                        .clickable(enabled = canSwap) {
                            val currentSrc = state.sourceCode
                            val currentTgt = state.targetCode
                            viewModel.setSourceLanguage(currentTgt)
                            viewModel.setTargetLanguage(currentSrc)
                        }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "⇄",
                        fontWeight = FontWeight.Bold,
                        color = if (canSwap) Booth.Accent else scheme.onSurfaceVariantSummary,
                        fontSize = 16.sp,
                    )
                }

                LanguageChip(
                    label = stringResource(R.string.text_target_lang),
                    value = stringResource(targetLabel.labelRes),
                    modifier = Modifier.weight(1f),
                    onClick = { picker = LangField.Target },
                )
            }
        }

        // ================= 输入卡片（带快捷粘贴与清空工具栏） =================
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "输入文本内容",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurfaceVariantSummary,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Booth.Accent.copy(alpha = 0.10f))
                                .clickable {
                                    val clipText = clipboard.getText()?.text.orEmpty()
                                    if (clipText.isNotBlank()) {
                                        viewModel.setInput(state.input + clipText)
                                        Toast.makeText(context, "已从剪贴板粘贴", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "📋 粘贴",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Booth.Accent,
                            )
                        }
                        if (state.input.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.surfaceVariant)
                                    .clickable { viewModel.clearInput() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = "✕ 清空",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::setInput,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.text_input_hint),
                            fontSize = 14.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    },
                    minLines = 6,
                    maxLines = 14,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = scheme.onSurface,
                        unfocusedTextColor = scheme.onSurface,
                        focusedBorderColor = Booth.Accent,
                        unfocusedBorderColor = scheme.outline.copy(alpha = 0.35f),
                        cursorColor = Booth.Accent,
                        focusedContainerColor = scheme.surface,
                        unfocusedContainerColor = scheme.surface,
                    ),
                )
            }
        }

        // ================= 大发射翻译按钮 =================
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
        ) {
            Button(
                onClick = { if (state.isTranslating) viewModel.cancel() else viewModel.translate() },
                enabled = state.isTranslating || state.input.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                colors = if (state.isTranslating) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            ) {
                Text(
                    text = if (state.isTranslating) {
                        stringResource(R.string.text_stop)
                    } else {
                        "▶ " + stringResource(R.string.text_translate)
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }

        if (state.isTranslating && state.chunkCount > 1) {
            Text(
                text = stringResource(
                    R.string.text_chunk_progress,
                    state.chunkIndex.coerceAtLeast(1),
                    state.chunkCount,
                ),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (state.missingKey) {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.text_need_key),
                        fontSize = 13.sp,
                        color = Booth.Danger,
                    )
                    Button(
                        onClick = onOpenSettings,
                    ) {
                        Text(text = stringResource(R.string.text_open_settings))
                    }
                }
            }
        }

        state.error?.let { message ->
            SectionCard {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "❌", fontSize = 15.sp)
                    Text(
                        text = stringResource(R.string.text_translate_failed, message),
                        fontSize = 13.sp,
                        color = Booth.Danger,
                    )
                }
            }
        }

        // ================= 译文结果卡片 =================
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "✨ 翻译结果",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = scheme.onSurface,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.output.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Booth.Accent.copy(alpha = 0.12f))
                                    .clickable {
                                        clipboard.setText(AnnotatedString(state.output))
                                        showCopied = true
                                        Toast.makeText(context, "已复制译文到剪贴板", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = if (showCopied) "✓ 已复制" else "📋 复制译文",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Booth.Accent,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.surfaceVariant)
                                    .clickable(enabled = !state.savedToHistory) {
                                        viewModel.saveToHistory()
                                        Toast.makeText(context, "已存入历史记录", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = if (state.savedToHistory) "已存入历史" else "📁 存入历史",
                                    fontSize = 12.sp,
                                    color = if (state.savedToHistory) Booth.Accent else scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }

                SettingDivider()

                if (state.output.isBlank()) {
                    Text(
                        text = stringResource(R.string.text_output_empty),
                        fontSize = 13.5.sp,
                        color = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = state.output,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 23.sp,
                            color = scheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    when (picker) {
        LangField.Source -> LanguagePickerSheet(
            title = stringResource(R.string.text_source_lang),
            options = sourceOptions,
            selectedCode = state.sourceCode,
            onSelect = { code ->
                viewModel.setSourceLanguage(code)
                picker = null
            },
            onDismiss = { picker = null },
        )
        LangField.Target -> LanguagePickerSheet(
            title = stringResource(R.string.text_target_lang),
            options = targetOptions,
            selectedCode = state.targetCode,
            onSelect = { code ->
                viewModel.setTargetLanguage(code)
                picker = null
            },
            onDismiss = { picker = null },
        )
        null -> Unit
    }
}
