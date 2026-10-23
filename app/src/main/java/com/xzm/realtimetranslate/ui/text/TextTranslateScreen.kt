package com.xzm.realtimetranslate.ui.text

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.platform.LocalClipboardManager
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
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.text_title),
            subtitle = stringResource(R.string.text_subtitle),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(text = stringResource(R.string.common_back), onClick = onBack)
            Text(
                text = stringResource(R.string.text_char_count, state.input.length),
                fontSize = 12.sp,
                color = scheme.onSurfaceVariantSummary,
            )
        }

        SectionCard {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageChip(
                    label = stringResource(R.string.text_source_lang),
                    value = stringResource(sourceLabel.labelRes),
                    modifier = Modifier.weight(1f),
                    onClick = { picker = LangField.Source },
                )
                LanguageChip(
                    label = stringResource(R.string.text_target_lang),
                    value = stringResource(targetLabel.labelRes),
                    modifier = Modifier.weight(1f),
                    onClick = { picker = LangField.Target },
                )
            }
        }

        SectionCard {
            OutlinedTextField(
                value = state.input,
                onValueChange = viewModel::setInput,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.text_input_hint),
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                },
                minLines = 8,
                maxLines = 16,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = scheme.onSurface,
                    unfocusedTextColor = scheme.onSurface,
                    focusedBorderColor = Booth.Accent,
                    unfocusedBorderColor = scheme.outline.copy(alpha = 0.45f),
                    cursorColor = Booth.Accent,
                    focusedContainerColor = scheme.surface,
                    unfocusedContainerColor = scheme.surface,
                ),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = { if (state.isTranslating) viewModel.cancel() else viewModel.translate() },
                enabled = state.isTranslating || state.input.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                colors = if (state.isTranslating) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            ) {
                Text(
                    text = stringResource(
                        if (state.isTranslating) R.string.text_stop else R.string.text_translate,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                )
            }
            TextButton(
                text = stringResource(R.string.text_clear),
                onClick = viewModel::clearInput,
                enabled = state.input.isNotEmpty() || state.output.isNotEmpty(),
            )
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
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(R.string.text_need_key),
                        fontSize = 13.sp,
                        color = Booth.Danger,
                    )
                    TextButton(
                        text = stringResource(R.string.text_open_settings),
                        onClick = onOpenSettings,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }

        state.error?.let { message ->
            Text(
                text = stringResource(R.string.text_translate_failed, message),
                fontSize = 13.sp,
                color = Booth.Danger,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        SectionCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.text_output_title),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(
                        text = stringResource(
                            if (showCopied) R.string.text_copied else R.string.text_copy,
                        ),
                        onClick = {
                            if (state.output.isNotBlank()) {
                                clipboard.setText(AnnotatedString(state.output))
                                showCopied = true
                            }
                        },
                        enabled = state.output.isNotBlank(),
                    )
                    TextButton(
                        text = stringResource(
                            if (state.savedToHistory) R.string.text_saved_history
                            else R.string.text_save_history,
                        ),
                        onClick = viewModel::saveToHistory,
                        enabled = state.output.isNotBlank() && !state.savedToHistory,
                    )
                }
                if (state.output.isBlank()) {
                    Text(
                        text = stringResource(R.string.text_output_empty),
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = state.output,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
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
