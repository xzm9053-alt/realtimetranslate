package com.xzm.realtimetranslate.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.BuildConfig
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyProviders
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.OcrScript
import com.xzm.realtimetranslate.data.SubtitleDisplayMode
import com.xzm.realtimetranslate.data.ThinkingOffStyle
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.translate.ModelAvailability
import com.xzm.realtimetranslate.translate.ModelListFilter
import com.xzm.realtimetranslate.translate.ModelProbe
import com.xzm.realtimetranslate.translate.ModelPresets
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import android.widget.Toast
import androidx.compose.runtime.rememberCoroutineScope
import com.xzm.realtimetranslate.update.RemoteUpdateInfo
import com.xzm.realtimetranslate.update.UpdateChecker
import com.xzm.realtimetranslate.update.UpdateCheckResult
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    onOpenOverlayPermission: () -> Unit,
    onPromptUpdate: (RemoteUpdateInfo) -> Unit = {},
) {
    // The log viewer replaces this screen rather than becoming a tab: MainActivity's
    // `when(page)` falls through to the history screen for anything unrecognised, so
    // adding a value there is a silent mis-navigation waiting to happen.
    var showLogs by rememberSaveable { mutableStateOf(false) }
    if (showLogs) {
        LogViewerScreen(onBack = { showLogs = false }, modifier = modifier)
        return
    }

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val deepSeekKey by viewModel.deepSeekKey.collectAsStateWithLifecycle()
    val deepSeekModel by viewModel.deepSeekModel.collectAsStateWithLifecycle()
    val deepSeekBaseUrl by viewModel.deepSeekBaseUrl.collectAsStateWithLifecycle()
    val zhipuKey by viewModel.zhipuKey.collectAsStateWithLifecycle()
    val zhipuModel by viewModel.zhipuModel.collectAsStateWithLifecycle()
    val zhipuBaseUrl by viewModel.zhipuBaseUrl.collectAsStateWithLifecycle()
    val geminiKey by viewModel.geminiKey.collectAsStateWithLifecycle()
    val geminiModel by viewModel.geminiModel.collectAsStateWithLifecycle()
    val geminiBaseUrl by viewModel.geminiBaseUrl.collectAsStateWithLifecycle()
    val genericKey by viewModel.genericKey.collectAsStateWithLifecycle()
    val genericModel by viewModel.genericModel.collectAsStateWithLifecycle()
    val genericBaseUrl by viewModel.genericBaseUrl.collectAsStateWithLifecycle()
    val mirrorUrl by viewModel.mirrorUrl.collectAsStateWithLifecycle()
    val hfToken by viewModel.hfToken.collectAsStateWithLifecycle()
    val modelStatus by viewModel.modelStatus.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val modelProbes by viewModel.modelProbes.collectAsStateWithLifecycle()
    val discoveredModels by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val modelsCheckedAt by viewModel.modelsCheckedAt.collectAsStateWithLifecycle()
    val checkingModels by viewModel.checkingModels.collectAsStateWithLifecycle()

    var revealKey by remember { mutableStateOf(false) }
    val engineType = settings.translationEngine
    val context = LocalContext.current

    val diagMessage by viewModel.diagMessage.collectAsStateWithLifecycle()
    val logSizeBytes by viewModel.logSizeBytes.collectAsStateWithLifecycle()
    val lastRunCrashed by viewModel.lastRunCrashed.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    var isCheckingUpdate by remember { mutableStateOf(false) }
    // Version is the natural place for a hidden gesture: nobody taps a version number by
    // accident five times, and it costs no screen space.
    var versionTaps by rememberSaveable { mutableIntStateOf(0) }
    var showApiKeyGuide by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshDiagnostics() }

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

    var activeModelPickerEngine by rememberSaveable { mutableStateOf<TranslationEngineType?>(null) }
    val pickingEngine = activeModelPickerEngine
    if (pickingEngine != null) {
        val (currentModelVal, updateModelFn) = when (pickingEngine) {
            TranslationEngineType.DEEPSEEK -> deepSeekModel to viewModel::updateDeepSeekModel
            TranslationEngineType.ZHIPU -> zhipuModel to viewModel::updateZhipuModel
            TranslationEngineType.GEMINI -> geminiModel to viewModel::updateGeminiModel
            TranslationEngineType.OPENAI_COMPAT -> genericModel to viewModel::updateGenericModel
            else -> TextFieldValue("") to { _: TextFieldValue -> }
        }
        ModelPickerSubScreen(
            engineType = pickingEngine,
            model = currentModelVal,
            onModelChange = updateModelFn,
            probes = modelProbes,
            discovered = discoveredModels,
            checkedAt = modelsCheckedAt,
            checking = checkingModels,
            colors = fieldColors,
            onCheck = viewModel::checkModels,
            onBack = { activeModelPickerEngine = null },
            modifier = modifier,
        )
        return
    }

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
                    selected = engineType == TranslationEngineType.DEEPSEEK,
                    onClick = { viewModel.setEngine(TranslationEngineType.DEEPSEEK) },
                )
                OptionRow(
                    label = stringResource(R.string.engine_zhipu),
                    summary = stringResource(R.string.engine_zhipu_summary),
                    selected = engineType == TranslationEngineType.ZHIPU,
                    onClick = { viewModel.setEngine(TranslationEngineType.ZHIPU) },
                )
                OptionRow(
                    label = stringResource(R.string.engine_gemini),
                    summary = stringResource(R.string.engine_gemini_summary),
                    selected = engineType == TranslationEngineType.GEMINI,
                    onClick = { viewModel.setEngine(TranslationEngineType.GEMINI) },
                )
                OptionRow(
                    label = stringResource(R.string.engine_microsoft),
                    summary = stringResource(R.string.engine_microsoft_summary),
                    selected = engineType == TranslationEngineType.MICROSOFT,
                    onClick = { viewModel.setEngine(TranslationEngineType.MICROSOFT) },
                )
                OptionRow(
                    label = stringResource(R.string.engine_google_free),
                    summary = stringResource(R.string.engine_google_free_summary),
                    selected = engineType == TranslationEngineType.GOOGLE_FREE,
                    onClick = { viewModel.setEngine(TranslationEngineType.GOOGLE_FREE) },
                )
                OptionRow(
                    label = stringResource(R.string.engine_generic),
                    summary = stringResource(R.string.engine_generic_summary),
                    selected = engineType == TranslationEngineType.OPENAI_COMPAT,
                    onClick = { viewModel.setEngine(TranslationEngineType.OPENAI_COMPAT) },
                )
            }
        }

        SectionCard {
            Column {
                // ---- 当前引擎的专属配置 ----
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (engineType) {
                        TranslationEngineType.DEEPSEEK -> {
                            EngineKeyField(
                                value = deepSeekKey,
                                onValueChange = viewModel::setDeepSeekKey,
                                labelRes = R.string.settings_deepseek_key,
                                reveal = revealKey,
                                colors = fieldColors,
                            )
                            RevealKeyButton(revealKey) { revealKey = !revealKey }
                            ModelPickerEntryRow(
                                model = deepSeekModel.text,
                                onClick = { activeModelPickerEngine = engineType },
                            )
                            EngineTextField(
                                value = deepSeekBaseUrl,
                                onValueChange = viewModel::updateDeepSeekBaseUrl,
                                labelRes = R.string.settings_deepseek_base_url,
                                colors = fieldColors,
                            )
                        }
                        TranslationEngineType.ZHIPU -> {
                            EngineKeyField(
                                value = zhipuKey,
                                onValueChange = viewModel::setZhipuKey,
                                labelRes = R.string.settings_zhipu_key,
                                reveal = revealKey,
                                colors = fieldColors,
                            )
                            RevealKeyButton(revealKey) { revealKey = !revealKey }
                            ModelPickerEntryRow(
                                model = zhipuModel.text,
                                onClick = { activeModelPickerEngine = engineType },
                            )
                            EngineTextField(
                                value = zhipuBaseUrl,
                                onValueChange = viewModel::updateZhipuBaseUrl,
                                labelRes = R.string.settings_zhipu_base_url,
                                colors = fieldColors,
                            )
                            EngineHint(R.string.settings_zhipu_model_hint)
                        }
                        TranslationEngineType.GEMINI -> {
                            EngineKeyField(
                                value = geminiKey,
                                onValueChange = viewModel::setGeminiKey,
                                labelRes = R.string.settings_gemini_key,
                                reveal = revealKey,
                                colors = fieldColors,
                            )
                            RevealKeyButton(revealKey) { revealKey = !revealKey }
                            ModelPickerEntryRow(
                                model = geminiModel.text,
                                onClick = { activeModelPickerEngine = engineType },
                            )
                            EngineTextField(
                                value = geminiBaseUrl,
                                onValueChange = viewModel::updateGeminiBaseUrl,
                                labelRes = R.string.settings_gemini_base_url,
                                colors = fieldColors,
                            )
                            EngineHint(R.string.settings_gemini_model_hint)
                        }
                        TranslationEngineType.OPENAI_COMPAT -> {
                            EngineKeyField(
                                value = genericKey,
                                onValueChange = viewModel::setGenericKey,
                                labelRes = R.string.settings_generic_key,
                                reveal = revealKey,
                                colors = fieldColors,
                            )
                            RevealKeyButton(revealKey) { revealKey = !revealKey }
                            EngineTextField(
                                value = genericBaseUrl,
                                onValueChange = viewModel::updateGenericBaseUrl,
                                labelRes = R.string.settings_generic_base_url,
                                colors = fieldColors,
                            )
                            EngineHint(R.string.settings_generic_hint)
                            ModelPickerEntryRow(
                                model = genericModel.text,
                                onClick = { activeModelPickerEngine = engineType },
                            )
                            // 三选一而非下拉框：项目里没有下拉组件，OptionRow 与引擎选择
                            // 是同一套视觉，且下面每个选项都需要一行说明的分量。
                            Text(
                                text = stringResource(R.string.settings_generic_thinking_style),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                            ThinkingOffStyle.entries.forEach { style ->
                                OptionRow(
                                    label = stringResource(style.labelRes),
                                    selected = settings.genericThinkingOffStyle == style,
                                    horizontalPadding = 0.dp,
                                    onClick = { viewModel.setGenericThinkingOffStyle(style) },
                                )
                            }
                            EngineHint(R.string.settings_generic_thinking_hint)
                        }
                        TranslationEngineType.MICROSOFT -> EngineHint(R.string.settings_microsoft_desc)
                        TranslationEngineType.GOOGLE_FREE -> EngineHint(R.string.settings_google_free_desc)
                    }
                }

                // ---- 思考开关：对所有 AI 类引擎生效，纯翻译 API 常驻置灰 ----
                SettingSwitchRow(
                    title = stringResource(R.string.settings_ai_thinking),
                    summary = stringResource(
                        if (engineType.isLlm) {
                            R.string.settings_ai_thinking_summary
                        } else {
                            R.string.settings_ai_thinking_summary_na
                        },
                    ),
                    checked = settings.aiDeepThinking,
                    onCheckedChange = viewModel::setAiDeepThinking,
                    enabled = engineType.isLlm,
                )

                // ---- 连接测试 ----
                Column(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = {
                            viewModel.saveApiKey()
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
                    // showsApiKeyField, not requiresApiKey: the generic engine's key is
                    // optional but still has a field to save.
                    if (engineType.showsApiKeyField) {
                        TextButton(
                            text = stringResource(R.string.settings_save_key_only),
                            onClick = viewModel::saveApiKey,
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
        }

        // 「怎么申请 API Key」：平时收成一个小长方块，点开才铺开四家申请入口。
        //
        // 摆在引擎区**之后**是刻意的：先让用户挑引擎，再告诉他去哪拿 Key，顺序才对得上。
        // 反过来（摆在引擎列表之前）会让人以为这个 App 只有那几家免费服务商，而实际上
        // 免费档全都跑在下面这些引擎上——缺的从来只是「去哪拿 Key」这一句话。
        SectionCard {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showApiKeyGuide = !showApiKeyGuide }
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_apikey_guide),
                        color = Booth.Accent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (showApiKeyGuide) {
                            Icons.Outlined.ExpandLess
                        } else {
                            Icons.Outlined.ExpandMore
                        },
                        contentDescription = null,
                        tint = Booth.Accent,
                    )
                }
                if (showApiKeyGuide) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_apikey_desc),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        ApiKeyProviders.all.forEach { provider ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(provider.nameRes),
                                        color = MiuixTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = stringResource(provider.costRes),
                                        fontSize = 13.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                                TextButton(
                                    text = stringResource(R.string.settings_apikey_open),
                                    onClick = { openUrl(context, provider.consoleUrl) },
                                )
                            }
                        }
                        EngineHint(
                            textRes = R.string.settings_apikey_nokey,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                        EngineHint(
                            textRes = R.string.settings_apikey_privacy,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
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
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 14.dp),
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
            // 「边说边出」跟切句参数是一回事的两面：切得越长，草稿字幕能提前显示的
            // 就越多，所以放在同一张卡里——用户一眼能看出两个滑杆是在权衡同一件事。
            SettingSwitchRow(
                title = stringResource(R.string.settings_partial_title),
                summary = stringResource(R.string.settings_partial_summary),
                checked = settings.partialTranscripts,
                onCheckedChange = { v ->
                    viewModel.update { it.copy(partialTranscripts = v) }
                },
            )
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
                // 所见即所得实时字幕预览卡片
                Text(
                    text = "实时效果预览",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Color(
                                red = 0.08f,
                                green = 0.08f,
                                blue = 0.10f,
                                alpha = settings.backgroundAlpha.coerceIn(0.1f, 1f)
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 抓手条示意
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.45f)),
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        if (settings.displayMode != SubtitleDisplayMode.TRANSLATION) {
                            Text(
                                text = "Good morning, everyone. Welcome to the conference.",
                                fontSize = settings.fontSizeSp.sp,
                                color = Color(settings.sourceTextColor),
                                lineHeight = (settings.fontSizeSp * 1.3f).sp,
                                fontWeight = FontWeight.Normal,
                            )
                        }

                        if (settings.displayMode == SubtitleDisplayMode.BOTH) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.15f))
                            )
                        }

                        if (settings.displayMode != SubtitleDisplayMode.SOURCE) {
                            Text(
                                text = "大家早上好，欢迎参加本次大会。",
                                fontSize = settings.fontSizeSp.sp,
                                color = Color(settings.translationTextColor),
                                lineHeight = (settings.fontSizeSp * 1.3f).sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

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
            text = stringResource(R.string.settings_logs),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (lastRunCrashed) {
                    Text(
                        text = stringResource(R.string.settings_crash_detected),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFD32F2F),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_logs_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                SettingSwitchRow(
                    title = stringResource(R.string.settings_diagnostic_content),
                    summary = stringResource(R.string.settings_diagnostic_content_summary),
                    checked = settings.diagnosticLogContent,
                    onCheckedChange = { v ->
                        viewModel.update { it.copy(diagnosticLogContent = v) }
                    },
                )
                TextButton(
                    text = stringResource(R.string.settings_share_diagnostics),
                    onClick = viewModel::shareDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    text = stringResource(R.string.settings_save_diagnostics),
                    onClick = viewModel::saveDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.settings_view_logs),
                        onClick = { showLogs = true },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.settings_clear_logs),
                        onClick = viewModel::clearLogs,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    text = stringResource(R.string.settings_logs_size, formatBytes(logSizeBytes)),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (!diagMessage.isNullOrBlank()) {
                    Text(
                        text = diagMessage.orEmpty(),
                        fontSize = 12.sp,
                        color = Booth.Accent,
                    )
                }
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
                    modifier = Modifier.clickable { versionTaps++ },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCheckingUpdate) {
                            isCheckingUpdate = true
                            Toast.makeText(
                                context,
                                context.getString(R.string.update_checking),
                                Toast.LENGTH_SHORT,
                            ).show()
                            coroutineScope.launch {
                                val result = UpdateChecker().checkForUpdate(
                                    UpdateChecker.DEFAULT_UPDATE_URL,
                                    BuildConfig.VERSION_CODE,
                                )
                                isCheckingUpdate = false
                                when (result) {
                                    is UpdateCheckResult.HasUpdate -> onPromptUpdate(result.info)
                                    is UpdateCheckResult.AlreadyLatest -> {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.update_already_latest),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    is UpdateCheckResult.Failed -> {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.update_check_failed),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.update_check_title),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    if (isCheckingUpdate) {
                        Text(
                            text = stringResource(R.string.update_checking),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                if (versionTaps >= CRASH_TEST_TAPS) {
                    // The only way to verify crash capture: `adb shell am crash` and a
                    // plain kill both bypass the default uncaught-exception handler.
                    TextButton(
                        text = stringResource(R.string.settings_crash_test),
                        onClick = {
                            Thread { throw RuntimeException("diagnostic test crash") }.start()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

/**
 * 单行模型入口：主设置页展示当前选中的模型，点击后跳转至模型选择二级页面。
 */
@Composable
private fun ModelPickerEntryRow(
    model: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_model_title),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = model.ifBlank { stringResource(R.string.settings_model_select_action) },
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (model.isNotBlank()) MiuixTheme.colorScheme.onSurface else Booth.Accent,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
        )
    }
}

/**
 * 模型选择二级页面：包含搜索过滤、推荐预设、服务商返回列表（可折叠）、自定义模型以及连通性/可用性检测。
 *
 * 解决接入包含海量模型的服务商（如阿里云百炼）时设置主页被大量模型铺满、难以翻阅的问题。
 */
@Composable
private fun ModelPickerSubScreen(
    engineType: TranslationEngineType,
    model: TextFieldValue,
    onModelChange: (TextFieldValue) -> Unit,
    probes: Map<String, ModelProbe>,
    discovered: List<String>,
    checkedAt: Long,
    checking: Boolean,
    colors: TextFieldColors,
    onCheck: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var discoveredExpanded by rememberSaveable { mutableStateOf(false) }
    var customMode by rememberSaveable(engineType) { mutableStateOf(false) }

    val presets = remember(engineType) {
        if (engineType == TranslationEngineType.OPENAI_COMPAT) {
            ModelPresets.RECOMMENDED_COMPAT_MODELS
        } else {
            ModelPresets.forEngine(engineType)
        }
    }

    val current = model.text.trim()
    val query = searchQuery.trim()

    // 过滤推荐预设模型
    val filteredPresets = remember(presets, query) {
        if (query.isEmpty()) presets
        else presets.filter { it.contains(query, ignoreCase = true) }
    }

    // 过滤服务商拉取的模型（排除已在预设中的模型，并过滤掉非 chat 类的模型）
    val allDiscovered = remember(engineType, discovered, presets) {
        discovered
            .filter { it.isNotBlank() && it !in presets && ModelListFilter.isChatModel(it) }
            .distinct()
            .sorted()
    }
    val filteredDiscovered = remember(allDiscovered, query) {
        if (query.isEmpty()) allDiscovered
        else allDiscovered.filter { it.contains(query, ignoreCase = true) }
    }

    val showCustomField = customMode || (current.isNotBlank() && current !in presets && current !in allDiscovered)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.settings_model_picker_title),
            subtitle = stringResource(
                when (engineType) {
                    TranslationEngineType.DEEPSEEK -> R.string.engine_deepseek
                    TranslationEngineType.ZHIPU -> R.string.engine_zhipu
                    TranslationEngineType.GEMINI -> R.string.engine_gemini
                    TranslationEngineType.OPENAI_COMPAT -> R.string.engine_generic
                    else -> R.string.settings_engine
                }
            ),
        )

        // 顶部导航与检测操作栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.settings_logs_back),
                onClick = onBack,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (checkedAt > 0L) {
                    Text(
                        text = stringResource(R.string.settings_model_checked_at, formatCheckedAt(checkedAt)),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                TextButton(
                    text = stringResource(
                        if (checking) R.string.settings_model_checking else R.string.settings_model_check,
                    ),
                    onClick = { if (!checking) onCheck() },
                    enabled = !checking,
                )
            }
        }

        // 当前模型若有报错详情，置顶醒目提醒
        val probe = probes[current]
        if (probe != null && probe.availability != ModelAvailability.AVAILABLE && probe.detail.isNotBlank()) {
            SectionCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_model_detail,
                            probe.detail.take(MAX_DETAIL_CHARS),
                        ),
                        fontSize = 12.sp,
                        color = availabilityStyle(probe.availability).second,
                    )
                }
            }
        }

        // 搜索框（支持模糊搜索匹配，如 qwen、realtime 等）
        SectionCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        androidx.compose.material3.Text(
                            text = stringResource(R.string.settings_model_search_placeholder),
                            fontSize = 13.sp,
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = colors,
                )
            }
        }

        // 1. 推荐预设模型组
        if (filteredPresets.isNotEmpty()) {
            SmallTitle(
                text = stringResource(R.string.settings_model_presets_group),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    filteredPresets.forEach { id ->
                        ModelRow(
                            model = id,
                            selected = !customMode && id == current,
                            availability = probes[id]?.availability,
                            onClick = {
                                customMode = false
                                onModelChange(TextFieldValue(id))
                            },
                        )
                    }
                }
            }
        }

        // 2. 服务商返回模型列表（支持可折叠，当超过 6 个且未搜索时默认折叠）
        if (filteredDiscovered.isNotEmpty()) {
            SmallTitle(
                text = stringResource(
                    R.string.settings_model_discovered_group,
                    filteredDiscovered.size,
                ),
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    val isCollapsible = query.isEmpty() && filteredDiscovered.size > 6
                    val displayList = if (isCollapsible && !discoveredExpanded) {
                        filteredDiscovered.take(6)
                    } else {
                        filteredDiscovered
                    }

                    displayList.forEach { id ->
                        ModelRow(
                            model = id,
                            selected = !customMode && id == current,
                            availability = probes[id]?.availability,
                            onClick = {
                                customMode = false
                                onModelChange(TextFieldValue(id))
                            },
                        )
                    }

                    if (isCollapsible) {
                        TextButton(
                            text = if (discoveredExpanded) {
                                stringResource(R.string.settings_model_collapse)
                            } else {
                                stringResource(R.string.settings_model_expand, filteredDiscovered.size)
                            },
                            onClick = { discoveredExpanded = !discoveredExpanded },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // 3. 搜索无结果时的空状态提示
        if (query.isNotEmpty() && filteredPresets.isEmpty() && filteredDiscovered.isEmpty()) {
            SectionCard {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.settings_model_search_empty),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // 4. 自定义模型输入组
        SmallTitle(
            text = stringResource(R.string.settings_model_custom),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                OptionRow(
                    label = stringResource(R.string.settings_model_custom),
                    summary = stringResource(R.string.settings_model_custom_summary),
                    selected = showCustomField,
                    horizontalPadding = 0.dp,
                    onClick = { customMode = true },
                )
                if (showCustomField) {
                    EngineTextField(
                        value = model,
                        onValueChange = onModelChange,
                        labelRes = R.string.settings_model_custom_label,
                        colors = colors,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                EngineHint(
                    if (engineType.hasPresetModels) {
                        R.string.settings_model_presets_hint
                    } else {
                        R.string.settings_generic_model_hint
                    },
                    Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

/** Keeps a provider error body from turning the settings page into a log dump. */
private const val MAX_DETAIL_CHARS = 160

/** A model the user can pick. A retired one is shown but not selectable. */
@Composable
private fun ModelRow(
    model: String,
    selected: Boolean,
    availability: ModelAvailability?,
    onClick: () -> Unit,
) {
    val retired = availability == ModelAvailability.RETIRED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !retired, onClick = onClick)
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = model,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                retired -> MiuixTheme.colorScheme.onSurfaceVariantSummary
                selected -> Booth.Accent
                else -> MiuixTheme.colorScheme.onSurface
            },
        )
        if (selected && !retired) {
            Text(text = "✓", color = Booth.Accent, fontWeight = FontWeight.Bold)
        }
        availability?.let { AvailabilityLabel(it) }
    }
}

@Composable
private fun AvailabilityLabel(availability: ModelAvailability) {
    val (textRes, color) = availabilityStyle(availability)
    Text(
        text = stringResource(textRes),
        fontSize = 12.sp,
        color = color,
        modifier = Modifier.padding(start = 8.dp),
    )
}

/**
 * Label and colour for one verdict. Shared so a status's colour cannot say one thing in
 * the row and another in the detail line below it.
 *
 * Red is reserved for a verdict the user must act on (a dead or unreachable model, a
 * rejected key, an empty account); a transient limit or an inconclusive answer stays
 * grey, because it is not something to go fix.
 */
@Composable
private fun availabilityStyle(availability: ModelAvailability): Pair<Int, Color> {
    val scheme = MiuixTheme.colorScheme
    return when (availability) {
        ModelAvailability.AVAILABLE -> R.string.model_status_available to Booth.Accent
        ModelAvailability.RETIRED -> R.string.model_status_retired to scheme.error
        ModelAvailability.BAD_KEY -> R.string.model_status_bad_key to scheme.error
        ModelAvailability.NO_BALANCE -> R.string.model_status_no_balance to scheme.error
        ModelAvailability.RATE_LIMITED ->
            R.string.model_status_rate_limited to scheme.onSurfaceVariantSummary
        ModelAvailability.UNKNOWN -> R.string.model_status_unknown to scheme.onSurfaceVariantSummary
        ModelAvailability.UNREACHABLE ->
            R.string.model_status_unreachable to scheme.onSurfaceVariantSummary
    }
}

/** Absolute rather than relative: a stale check reads better as a date. */
private fun formatCheckedAt(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))

@Composable
private fun EngineTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    @StringRes labelRes: Int,
    colors: TextFieldColors,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { androidx.compose.material3.Text(stringResource(labelRes)) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = colors,
    )
}

/** API-key field: masked unless [reveal], and restricted to ASCII. */
@Composable
private fun EngineKeyField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    @StringRes labelRes: Int,
    reveal: Boolean,
    colors: TextFieldColors,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { androidx.compose.material3.Text(stringResource(labelRes)) },
        singleLine = true,
        visualTransformation = if (reveal) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        shape = RoundedCornerShape(16.dp),
        colors = colors,
    )
}

@Composable
private fun RevealKeyButton(reveal: Boolean, onToggle: () -> Unit) {
    TextButton(
        text = stringResource(if (reveal) R.string.settings_hide else R.string.settings_show),
        onClick = onToggle,
    )
}

/**
 * Opens an external page (a provider's sign-up page). Best-effort: a device with no
 * browser, or a locked-down one that refuses the intent, simply does nothing rather than
 * crashing the settings screen.
 */
private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}

/** Small grey explanatory line under an engine's fields. */
@Composable
private fun EngineHint(@StringRes textRes: Int, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(textRes),
        fontSize = 13.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = modifier,
    )
}

/**
 * @param summary second line; null omits it (used by lists where the label says enough)
 * @param horizontalPadding overridable because this row is also used inside a column
 *   that already carries the screen's 16dp inset — the default would double it
 */
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

/** Taps on the version number that reveal the test-crash button. */
private const val CRASH_TEST_TAPS = 5

/**
 * Log size for the settings row. Binary units, because that is what the 512 KB rotation
 * cap in [com.xzm.realtimetranslate.util.AppLog] is expressed in.
 */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024 -> "%.1f KB".format(Locale.US, bytes / 1024.0)
    else -> "%.1f MB".format(Locale.US, bytes / (1024.0 * 1024.0))
}
