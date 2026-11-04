package com.xzm.realtimetranslate.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xzm.realtimetranslate.data.EngineCategory
import com.xzm.realtimetranslate.data.LocalAsrModelCatalog
import com.xzm.realtimetranslate.data.LocalAsrModelPreset
import com.xzm.realtimetranslate.data.LocalAsrProbeResult
import com.xzm.realtimetranslate.data.TranslationEngineCatalog
import com.xzm.realtimetranslate.data.TranslationEngineDetail
import com.xzm.realtimetranslate.data.AsrEngineDetail
import com.xzm.realtimetranslate.data.OpenSourceTranslationPreset
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzm.realtimetranslate.BuildConfig
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.ApiKeyProviders
import com.xzm.realtimetranslate.data.AsrEngineType
import com.xzm.realtimetranslate.data.HistoryMode
import com.xzm.realtimetranslate.data.OcrScript
import com.xzm.realtimetranslate.data.SubtitleDisplayMode
import com.xzm.realtimetranslate.data.ThinkingOffStyle
import com.xzm.realtimetranslate.data.TranslationEngineType
import com.xzm.realtimetranslate.data.UserSettings
import com.xzm.realtimetranslate.translate.AsrModelCatalog
import com.xzm.realtimetranslate.translate.ModelAvailability
import com.xzm.realtimetranslate.translate.ModelListFilter
import com.xzm.realtimetranslate.translate.ModelProbe
import com.xzm.realtimetranslate.translate.ModelPresets
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.components.SettingRowIcon
import com.xzm.realtimetranslate.ui.components.SettingSwitchRow
import com.xzm.realtimetranslate.ui.glossary.GlossaryScreen
import com.xzm.realtimetranslate.ui.glossary.GlossaryViewModel
import com.xzm.realtimetranslate.ui.glossary.GlossaryViewModelFactory
import com.xzm.realtimetranslate.ui.theme.Booth
import com.xzm.realtimetranslate.util.PermissionUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
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

/** Where Bailian hands out DashScope API keys. The old dashscope.console host is dead. */
private const val ALIYUN_API_KEY_URL = "https://bailian.console.aliyun.com/model/settings/api-key"

private sealed interface SettingsNavTarget {
    data object Main : SettingsNavTarget
    data object EnginePicker : SettingsNavTarget
    data object AsrPicker : SettingsNavTarget
    data class ModelPicker(val engine: TranslationEngineType) : SettingsNavTarget
    data object Logs : SettingsNavTarget
    data object Glossary : SettingsNavTarget
}

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel,
    onOpenOverlayPermission: () -> Unit,
    onPromptUpdate: (RemoteUpdateInfo) -> Unit = {},
) {
    var showLogs by rememberSaveable { mutableStateOf(false) }
    var showGlossary by rememberSaveable { mutableStateOf(false) }

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
    val localAsrProbeResult by viewModel.localAsrProbeResult.collectAsStateWithLifecycle()
    val downloading by viewModel.downloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val modelProbes by viewModel.modelProbes.collectAsStateWithLifecycle()
    val discoveredModels by viewModel.discoveredModels.collectAsStateWithLifecycle()
    val modelsCheckedAt by viewModel.modelsCheckedAt.collectAsStateWithLifecycle()
    val checkingModels by viewModel.checkingModels.collectAsStateWithLifecycle()

    val asrEngine = settings.asrEngine
    val aliyunAsrKey by viewModel.aliyunAsrKey.collectAsStateWithLifecycle()
    val aliyunAsrModel by viewModel.aliyunAsrModel.collectAsStateWithLifecycle()
    val aliyunWorkspaceId by viewModel.aliyunWorkspaceId.collectAsStateWithLifecycle()
    val genericAsrBaseUrl by viewModel.genericAsrBaseUrl.collectAsStateWithLifecycle()
    val genericAsrApiKey by viewModel.genericAsrApiKey.collectAsStateWithLifecycle()
    val genericAsrModel by viewModel.genericAsrModel.collectAsStateWithLifecycle()
    val asrTestResult by viewModel.asrTestResult.collectAsStateWithLifecycle()
    val asrTesting by viewModel.asrTesting.collectAsStateWithLifecycle()
    val modelImportUiState by viewModel.modelImportUiState.collectAsStateWithLifecycle()

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

    var showEnginePicker by rememberSaveable { mutableStateOf(false) }
    var activeConfigEngine by rememberSaveable { mutableStateOf<TranslationEngineType?>(null) }
    var activeModelPickerEngine by rememberSaveable { mutableStateOf<TranslationEngineType?>(null) }

    var showAsrEnginePicker by rememberSaveable { mutableStateOf(false) }
    var activeConfigAsrEngine by rememberSaveable { mutableStateOf<AsrEngineType?>(null) }
    var showRewardDialog by remember { mutableStateOf(false) }
    var showUserGuideDialog by remember { mutableStateOf(false) }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var logsExpanded by rememberSaveable { mutableStateOf(false) }

    val currentNavTarget: SettingsNavTarget = when {
        showLogs -> SettingsNavTarget.Logs
        showGlossary -> SettingsNavTarget.Glossary
        activeModelPickerEngine != null -> SettingsNavTarget.ModelPicker(activeModelPickerEngine!!)
        showEnginePicker -> SettingsNavTarget.EnginePicker
        showAsrEnginePicker -> SettingsNavTarget.AsrPicker
        else -> SettingsNavTarget.Main
    }

    BackHandler(enabled = currentNavTarget != SettingsNavTarget.Main) {
        when (currentNavTarget) {
            SettingsNavTarget.Logs -> showLogs = false
            SettingsNavTarget.Glossary -> showGlossary = false
            is SettingsNavTarget.ModelPicker -> activeModelPickerEngine = null
            SettingsNavTarget.EnginePicker -> showEnginePicker = false
            SettingsNavTarget.AsrPicker -> showAsrEnginePicker = false
            SettingsNavTarget.Main -> Unit
        }
    }

    AnimatedContent(
        targetState = currentNavTarget,
        transitionSpec = {
            if (targetState != SettingsNavTarget.Main) {
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
        label = "SettingsNavTransition",
    ) { navTarget ->
        when (navTarget) {
            SettingsNavTarget.Main -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PageTitle(
                        title = stringResource(R.string.settings_title),
                    )

        // ================= Group 1: 核心引擎 =================
        SmallTitle(
            text = "核心引擎",
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        val configured = isEngineConfigured(
            engine = engineType,
            deepSeekKey = deepSeekKey,
            zhipuKey = zhipuKey,
            geminiKey = geminiKey,
            genericKey = genericKey,
            genericBaseUrl = genericBaseUrl.text,
        )
        SectionCard {
            Column {
                // 1. 翻译大模型
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEnginePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "🌐",
                            bg = Booth.Accent.copy(alpha = 0.12f),
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "翻译大模型",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Booth.Accent.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(engineType.titleRes),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Booth.Accent,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val currentModelName = when (engineType) {
                                TranslationEngineType.DEEPSEEK -> deepSeekModel.text
                                TranslationEngineType.ZHIPU -> zhipuModel.text
                                TranslationEngineType.GEMINI -> geminiModel.text
                                TranslationEngineType.OPENAI_COMPAT -> genericModel.text
                                else -> ""
                            }
                            Text(
                                text = if (!configured) {
                                    "尚未配置，点击选择翻译服务"
                                } else if (currentModelName.isNotBlank()) {
                                    "$currentModelName · 点击进入配置"
                                } else {
                                    "${stringResource(engineType.summaryRes)} · 点击配置"
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 2. 切换其他翻译引擎
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showEnginePicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.padding(start = 46.dp).weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_engine_other),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "微软免费 / 谷歌 / 智谱 / Gemini / 自定义API",
                            fontSize = 11.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 3. 语音识别 (ASR)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAsrEnginePicker = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "🎙️",
                            bg = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "语音识别 (ASR)",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF8B5CF6).copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(asrEngine.titleRes),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF8B5CF6),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val asrSubtitle = when (asrEngine) {
                                AsrEngineType.LOCAL_SENSEVOICE -> "本地离线模型 · 零流量 · 隐私安全"
                                AsrEngineType.ALIYUN_FUN_ASR -> {
                                    val m = aliyunAsrModel.text.ifBlank { "fun-asr-realtime" }
                                    "$m · 阿里百炼流式"
                                }
                                AsrEngineType.GENERIC_OPENAI -> {
                                    val m = genericAsrModel.text.ifBlank { "SenseVoiceSmall" }
                                    "$m · OpenAI兼容接口"
                                }
                            }
                            Text(
                                text = "$asrSubtitle · 点击配置",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 4. 切换其他识别引擎
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAsrEnginePicker = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.padding(start = 46.dp).weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_asr_engine_other),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "本地离线 SenseVoice / 阿里百炼 / 通用转录",
                            fontSize = 11.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 4. 专业术语库
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showGlossary = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "📖",
                            bg = Color(0xFF10B981).copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = "专业术语库",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "自定义专有名词精准对齐，防止大模型误翻",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.5f),
                        modifier = Modifier.rotate(-90f),
                    )
                }
            }
        }

        // ================= Group 2: 系统与体验 =================
        SmallTitle(
            text = "系统与体验",
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column {
                // 1. 悬浮窗显示权限
                val canOverlay = PermissionUtils.canDrawOverlays(context)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenOverlayPermission() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "🪟",
                            bg = Color(0xFFF59E0B).copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_permissions),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.settings_permissions_hint),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (canOverlay) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = if (canOverlay) "已授权" else "去开启",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (canOverlay) Color(0xFF10B981) else Color(0xFFF59E0B),
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                            modifier = Modifier.rotate(-90f),
                        )
                    }
                }

                SettingDivider()

                // 2. 历史记录管理
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { historyExpanded = !historyExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "🕒",
                            bg = Color(0xFF64748B).copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_history),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val historySummary = when (settings.historyMode) {
                                HistoryMode.AUTO_CLEAR -> "自动清理 · 最多保留 ${settings.historyLimit} 条"
                                HistoryMode.SAVE_ALL -> "全部保存 · 永不自动删除"
                            }
                            Text(
                                text = historySummary,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    val historyArrowRotation by animateFloatAsState(
                        targetValue = if (historyExpanded) 180f else 0f,
                        animationSpec = tween(220),
                        label = "historyArrow",
                    )
                    Text(
                        text = "▼",
                        modifier = Modifier.rotate(historyArrowRotation),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                AnimatedVisibility(
                    visible = historyExpanded,
                    enter = expandVertically(tween(220)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(180)),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        OptionRow(
                            label = stringResource(R.string.history_mode_auto_clear),
                            summary = stringResource(R.string.history_mode_auto_clear_summary),
                            selected = settings.historyMode == HistoryMode.AUTO_CLEAR,
                            onClick = { viewModel.setHistoryMode(HistoryMode.AUTO_CLEAR) },
                            horizontalPadding = 0.dp,
                        )
                        OptionRow(
                            label = stringResource(R.string.history_mode_save_all),
                            summary = stringResource(R.string.history_mode_save_all_summary),
                            selected = settings.historyMode == HistoryMode.SAVE_ALL,
                            onClick = { viewModel.setHistoryMode(HistoryMode.SAVE_ALL) },
                            horizontalPadding = 0.dp,
                        )
                        if (settings.historyMode == HistoryMode.AUTO_CLEAR) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.history_max_entries, settings.historyLimit),
                                fontSize = 13.sp,
                            )
                            Slider(
                                value = settings.historyLimit.toFloat(),
                                onValueChange = { v -> viewModel.setHistoryLimit(v.toInt()) },
                                valueRange = UserSettings.Defaults.HISTORY_LIMIT_MIN.toFloat()..UserSettings.Defaults.HISTORY_LIMIT_MAX.toFloat(),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }

                SettingDivider()

                // 3. 数字静音自动告警开关
                SettingSwitchRow(
                    title = stringResource(R.string.settings_silence_detection_title),
                    summary = stringResource(R.string.settings_silence_detection_summary),
                    checked = settings.silenceDetectionEnabled,
                    onCheckedChange = { v ->
                        viewModel.update { it.copy(silenceDetectionEnabled = v) }
                    },
                )
            }
        }

        // ================= Group 3: 诊断与支持 =================
        SmallTitle(
            text = "诊断与支持",
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column {
                // 1. 系统日志与诊断报告 (可折叠展示导出与清除操作)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { logsExpanded = !logsExpanded }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "📊",
                            bg = Color(0xFF64748B).copy(alpha = 0.12f),
                        )
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = stringResource(R.string.settings_logs),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MiuixTheme.colorScheme.onSurface,
                                )
                                if (lastRunCrashed) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "● 异常",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFF5C5C),
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "日志大小 ${formatBytes(logSizeBytes)} · 一键导出诊断",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    val logsArrowRotation by animateFloatAsState(
                        targetValue = if (logsExpanded) 180f else 0f,
                        animationSpec = tween(220),
                        label = "logsArrow",
                    )
                    Text(
                        text = "▼",
                        modifier = Modifier.rotate(logsArrowRotation),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }

                AnimatedVisibility(
                    visible = logsExpanded,
                    enter = expandVertically(tween(220)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(180)),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (lastRunCrashed) {
                            Text(
                                text = stringResource(R.string.settings_crash_detected),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFF5C5C),
                            )
                        }
                        SettingSwitchRow(
                            title = stringResource(R.string.settings_diagnostic_content),
                            summary = stringResource(R.string.settings_diagnostic_content_summary),
                            checked = settings.diagnosticLogContent,
                            onCheckedChange = { v ->
                                viewModel.update { it.copy(diagnosticLogContent = v) }
                            },
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                text = stringResource(R.string.settings_share_diagnostics),
                                onClick = viewModel::shareDiagnostics,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(
                                text = stringResource(R.string.settings_save_diagnostics),
                                onClick = viewModel::saveDiagnostics,
                                modifier = Modifier.weight(1f),
                            )
                        }
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
                        if (!diagMessage.isNullOrBlank()) {
                            Text(
                                text = diagMessage.orEmpty(),
                                fontSize = 12.sp,
                                color = Booth.Accent,
                            )
                        }
                    }
                }

                SettingDivider()

                // 2. 使用说明 · 快速上手
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showUserGuideDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "📖",
                            bg = Booth.Accent.copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_user_guide),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "图文并茂快速掌握全功能与手势",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 3. 检查软件更新
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !isCheckingUpdate) {
                            isCheckingUpdate = true
                            Toast.makeText(context, R.string.update_checking, Toast.LENGTH_SHORT).show()
                            coroutineScope.launch {
                                val result = UpdateChecker().checkForUpdate(
                                    UpdateChecker.DEFAULT_UPDATE_URL,
                                    BuildConfig.VERSION_CODE,
                                )
                                isCheckingUpdate = false
                                when (result) {
                                    is UpdateCheckResult.HasUpdate -> onPromptUpdate(result.info)
                                    is UpdateCheckResult.AlreadyLatest -> {
                                        Toast.makeText(context, R.string.update_already_latest, Toast.LENGTH_SHORT).show()
                                    }
                                    is UpdateCheckResult.Failed -> {
                                        Toast.makeText(context, R.string.update_check_failed, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "🔄",
                            bg = Color(0xFF06B6D4).copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.update_check_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isCheckingUpdate) "正在检查更新…" else "当前版本 v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 4. Bilibili 主页
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            openUrl(context, "https://space.bilibili.com/1366321010")
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "🍉",
                            bg = Color(0xFFFB7299).copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_bilibili, stringResource(R.string.settings_bilibili_author)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "视频教程与最新动态分享",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                SettingDivider()

                // 5. 打赏作者
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRewardDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        SettingRowIcon(
                            emoji = "☕",
                            bg = Color(0xFF07C160).copy(alpha = 0.12f),
                        )
                        Column {
                            Text(
                                text = stringResource(R.string.settings_reward_author),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF07C160),
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "微信赞赏码 · 感谢你的鼓励与支持",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                        modifier = Modifier.rotate(-90f),
                    )
                }

                // 6. 版本彩蛋与测试崩溃
                SettingDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { versionTaps++ }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "实时翻译 v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (versionTaps >= CRASH_TEST_TAPS) {
                    TextButton(
                        text = stringResource(R.string.settings_crash_test),
                        onClick = {
                            Thread { throw RuntimeException("diagnostic test crash") }.start()
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
                }
            }
            is SettingsNavTarget.ModelPicker -> {
                val (currentModelVal, updateModelFn) = when (navTarget.engine) {
                    TranslationEngineType.DEEPSEEK -> deepSeekModel to viewModel::updateDeepSeekModel
                    TranslationEngineType.ZHIPU -> zhipuModel to viewModel::updateZhipuModel
                    TranslationEngineType.GEMINI -> geminiModel to viewModel::updateGeminiModel
                    TranslationEngineType.OPENAI_COMPAT -> genericModel to viewModel::updateGenericModel
                    else -> TextFieldValue("") to { _: TextFieldValue -> }
                }
                ModelPickerSubScreen(
                    engineType = navTarget.engine,
                    model = currentModelVal,
                    onModelChange = updateModelFn,
                    probes = modelProbes,
                    discovered = discoveredModels,
                    checkedAt = modelsCheckedAt,
                    checking = checkingModels,
                    colors = fieldColors,
                    onCheck = viewModel::checkModels,
                    onBack = { activeModelPickerEngine = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SettingsNavTarget.EnginePicker -> {
                EnginePickerSubScreen(
                    currentEngine = engineType,
                    onSelectEngine = { selected ->
                        viewModel.setEngine(selected)
                    },
                    onSelectOpenSourcePreset = { preset ->
                        viewModel.setEngine(preset.engineType)
                        if (preset.defaultModelName.isNotBlank()) {
                            viewModel.updateGenericModel(TextFieldValue(preset.defaultModelName))
                        }
                    },
                    settings = settings,
                    deepSeekKey = deepSeekKey,
                    onDeepSeekKeyChange = viewModel::setDeepSeekKey,
                    deepSeekModel = deepSeekModel,
                    deepSeekBaseUrl = deepSeekBaseUrl,
                    onUpdateDeepSeekBaseUrl = viewModel::updateDeepSeekBaseUrl,
                    zhipuKey = zhipuKey,
                    onZhipuKeyChange = viewModel::setZhipuKey,
                    zhipuModel = zhipuModel,
                    zhipuBaseUrl = zhipuBaseUrl,
                    onUpdateZhipuBaseUrl = viewModel::updateZhipuBaseUrl,
                    geminiKey = geminiKey,
                    onGeminiKeyChange = viewModel::setGeminiKey,
                    geminiModel = geminiModel,
                    geminiBaseUrl = geminiBaseUrl,
                    onUpdateGeminiBaseUrl = viewModel::updateGeminiBaseUrl,
                    genericKey = genericKey,
                    onGenericKeyChange = viewModel::setGenericKey,
                    genericModel = genericModel,
                    onUpdateGenericModel = viewModel::updateGenericModel,
                    genericBaseUrl = genericBaseUrl,
                    onUpdateGenericBaseUrl = viewModel::updateGenericBaseUrl,
                    onUpdateGenericThinkingOffStyle = viewModel::setGenericThinkingOffStyle,
                    onUpdateAiDeepThinking = viewModel::setAiDeepThinking,
                    testing = testing,
                    testResult = testResult,
                    onSaveAndTest = {
                        viewModel.saveApiKey()
                        viewModel.testConnection()
                    },
                    onSaveKeyOnly = viewModel::saveApiKey,
                    fieldColors = fieldColors,
                    onOpenModelPicker = { activeModelPickerEngine = it },
                    onBack = { showEnginePicker = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SettingsNavTarget.AsrPicker -> {
                AsrEnginePickerSubScreen(
                    currentEngine = asrEngine,
                    currentLocalModel = settings.localAsrModel,
                    localAsrProbeResult = localAsrProbeResult,
                    onSelectEngine = { selected ->
                        viewModel.setAsrEngine(selected)
                    },
                    onSelectLocalAsrModel = { modelId ->
                        viewModel.setAsrEngine(AsrEngineType.LOCAL_SENSEVOICE)
                        viewModel.setLocalAsrModel(modelId)
                    },
                    onRefreshLocalAsrProbe = viewModel::refreshLocalAsrProbe,
                    modelStatus = modelStatus,
                    downloading = downloading,
                    downloadProgress = downloadProgress,
                    onDownloadModels = viewModel::downloadModels,
                    onRefreshModelStatus = viewModel::refreshModelStatus,
                    mirrorUrl = mirrorUrl,
                    onUpdateMirrorUrl = viewModel::updateMirrorUrl,
                    hfToken = hfToken,
                    onUpdateHfToken = viewModel::updateHfToken,
                    aliyunAsrKey = aliyunAsrKey,
                    onUpdateAliyunAsrKey = viewModel::updateAliyunAsrKey,
                    aliyunAsrModel = aliyunAsrModel,
                    onUpdateAliyunAsrModel = viewModel::updateAliyunAsrModel,
                    aliyunWorkspaceId = aliyunWorkspaceId,
                    onUpdateAliyunWorkspaceId = viewModel::updateAliyunWorkspaceId,
                    genericAsrBaseUrl = genericAsrBaseUrl,
                    onUpdateGenericAsrBaseUrl = viewModel::updateGenericAsrBaseUrl,
                    genericAsrApiKey = genericAsrApiKey,
                    onUpdateGenericAsrApiKey = viewModel::updateGenericAsrApiKey,
                    genericAsrModel = genericAsrModel,
                    onUpdateGenericAsrModel = viewModel::updateGenericAsrModel,
                    fieldColors = fieldColors,
                    asrTesting = asrTesting,
                    asrTestResult = asrTestResult,
                    onSaveAndTest = { viewModel.testAsrConnection(asrEngine) },
                    onSaveOnly = { viewModel.saveAsrConfig(asrEngine) },
                    settings = settings,
                    onUpdateSettings = { transform -> viewModel.update(transform) },
                    modelImportUiState = modelImportUiState,
                    onImportModelArchive = viewModel::importModelArchive,
                    onClearModelImportState = viewModel::clearModelImportState,
                    onBack = { showAsrEnginePicker = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SettingsNavTarget.Logs -> {
                LogViewerScreen(
                    onBack = { showLogs = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            SettingsNavTarget.Glossary -> {
                val app = context.applicationContext as LiveTranslateApp
                val glossaryVm: GlossaryViewModel = viewModel(
                    factory = GlossaryViewModelFactory(app, app.glossaryRepository),
                )
                GlossaryScreen(
                    onBack = { showGlossary = false },
                    viewModel = glossaryVm,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    if (showRewardDialog) {
        RewardDialog(onDismiss = { showRewardDialog = false })
    }
    if (showUserGuideDialog) {
        UserGuideDialog(onDismiss = { showUserGuideDialog = false })
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
 * 二级配置与选择页面通用顶部导航栏：左侧带轻微触控圆底的高灵敏度返回胶囊（‹），
 * 标题与副标题统一规整排布，右侧支持快捷操作（如设为当前引擎、探测模型等）。
 */
@Composable
internal fun SubScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f, fill = false),
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
                    text = title,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    maxLines = 1,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = scheme.onSurfaceVariantSummary,
                        maxLines = 1,
                    )
                }
            }
        }

        if (trailingContent != null) {
            trailingContent()
        }
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
        val engineLabel = stringResource(
            when (engineType) {
                TranslationEngineType.DEEPSEEK -> R.string.engine_deepseek
                TranslationEngineType.ZHIPU -> R.string.engine_zhipu
                TranslationEngineType.GEMINI -> R.string.engine_gemini
                TranslationEngineType.OPENAI_COMPAT -> R.string.engine_generic
                else -> R.string.settings_engine
            }
        )
        SubScreenHeader(
            title = stringResource(R.string.settings_model_picker_title),
            subtitle = "为 $engineLabel 挑选或自定义模型",
            onBack = onBack,
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (checkedAt > 0L) {
                        Text(
                            text = stringResource(R.string.settings_model_checked_at, formatCheckedAt(checkedAt)),
                            fontSize = 11.5.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(end = 6.dp),
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
            },
        )

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
private fun SpeechSlicingAndSilenceCard(
    settings: UserSettings,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    currentAsrEngine: AsrEngineType,
    modifier: Modifier = Modifier,
) {
    SmallTitle(
        text = "语音切片与静音断句",
        modifier = modifier.padding(horizontal = 24.dp),
    )
    SectionCard {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (currentAsrEngine) {
                AsrEngineType.ALIYUN_FUN_ASR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "静音断句门槛",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "${settings.aliyunAsrSilenceMs} ms (${settings.aliyunAsrSilenceMs / 1000f} 秒)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.aliyunAsrSilenceMs.toFloat().coerceIn(200f, 2000f),
                        onValueChange = { v ->
                            val stepped = ((v / 50).roundToInt() * 50).coerceIn(200, 2000)
                            onUpdateSettings { it.copy(aliyunAsrSilenceMs = stepped) }
                        },
                        valueRange = 200f..2000f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AsrEngineType.LOCAL_SENSEVOICE -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "停顿切段门槛",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "%.1f 秒".format(settings.vadMinSilenceSec),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.vadMinSilenceSec.coerceIn(0.1f, 1.0f),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(vadMinSilenceSec = (v * 10).roundToInt() / 10f) }
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "单句最长时间",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "%.1f 秒".format(settings.vadMaxSpeechSec),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.vadMaxSpeechSec.coerceIn(1f, 10f),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(vadMaxSpeechSec = (v * 2).roundToInt() / 2f) }
                        },
                        valueRange = 1f..10f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                AsrEngineType.GENERIC_OPENAI -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "分段停顿门槛",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "%.1f 秒".format(settings.vadMinSilenceSec),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.vadMinSilenceSec.coerceIn(0.1f, 1.0f),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(vadMinSilenceSec = (v * 10).roundToInt() / 10f) }
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SettingSwitchRow(
                title = stringResource(R.string.settings_partial_title),
                summary = stringResource(R.string.settings_partial_summary),
                checked = settings.partialTranscripts,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(partialTranscripts = v) }
                },
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_draft_translation_preview_title),
                summary = stringResource(R.string.settings_draft_translation_preview_summary),
                checked = settings.draftTranslationPreview,
                onCheckedChange = { v ->
                    onUpdateSettings { it.copy(draftTranslationPreview = v) }
                },
            )

        }
    }
}

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

/**
 * 检查指定引擎是否已配置好（免费引擎无需 Key 视为已就绪；LLM 引擎需填好 Key 或 BaseURL）。
 */
private fun isEngineConfigured(
    engine: TranslationEngineType,
    deepSeekKey: TextFieldValue,
    zhipuKey: TextFieldValue,
    geminiKey: TextFieldValue,
    genericKey: TextFieldValue,
    genericBaseUrl: String,
): Boolean {
    return when (engine) {
        TranslationEngineType.MICROSOFT,
        TranslationEngineType.GOOGLE_FREE,
        TranslationEngineType.NONE -> true
        TranslationEngineType.DEEPSEEK -> deepSeekKey.text.isNotBlank()
        TranslationEngineType.ZHIPU -> zhipuKey.text.isNotBlank()
        TranslationEngineType.GEMINI -> geminiKey.text.isNotBlank()
        TranslationEngineType.OPENAI_COMPAT -> genericBaseUrl.isNotBlank() || genericKey.text.isNotBlank()
    }
}

/**
 * 翻译引擎与模型一体化页面：开源模型与商业 API 付费两大生态体系，方格网格点选即配，配置与详细说明就地展开。
 */
@Composable
private fun EnginePickerSubScreen(
    currentEngine: TranslationEngineType,
    onSelectEngine: (TranslationEngineType) -> Unit,
    onSelectOpenSourcePreset: (OpenSourceTranslationPreset) -> Unit,
    settings: UserSettings,
    deepSeekKey: TextFieldValue,
    onDeepSeekKeyChange: (TextFieldValue) -> Unit,
    deepSeekModel: TextFieldValue,
    deepSeekBaseUrl: TextFieldValue,
    onUpdateDeepSeekBaseUrl: (TextFieldValue) -> Unit,
    zhipuKey: TextFieldValue,
    onZhipuKeyChange: (TextFieldValue) -> Unit,
    zhipuModel: TextFieldValue,
    zhipuBaseUrl: TextFieldValue,
    onUpdateZhipuBaseUrl: (TextFieldValue) -> Unit,
    geminiKey: TextFieldValue,
    onGeminiKeyChange: (TextFieldValue) -> Unit,
    geminiModel: TextFieldValue,
    geminiBaseUrl: TextFieldValue,
    onUpdateGeminiBaseUrl: (TextFieldValue) -> Unit,
    genericKey: TextFieldValue,
    onGenericKeyChange: (TextFieldValue) -> Unit,
    genericModel: TextFieldValue,
    onUpdateGenericModel: (TextFieldValue) -> Unit,
    genericBaseUrl: TextFieldValue,
    onUpdateGenericBaseUrl: (TextFieldValue) -> Unit,
    onUpdateGenericThinkingOffStyle: (ThinkingOffStyle) -> Unit,
    onUpdateAiDeepThinking: (Boolean) -> Unit,
    testing: Boolean,
    testResult: String?,
    onSaveAndTest: () -> Unit,
    onSaveKeyOnly: () -> Unit,
    fieldColors: TextFieldColors,
    onOpenModelPicker: (TranslationEngineType) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }
    val initialCat = TranslationEngineCatalog.getDetail(currentEngine).category
    var selectedCategory by rememberSaveable { mutableStateOf(initialCat) }
    var selectedOpenPresetId by rememberSaveable { mutableStateOf("sakura-14b") }
    var showApiKeyGuide by rememberSaveable { mutableStateOf(false) }
    var revealKey by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubScreenHeader(
            title = stringResource(R.string.settings_engine_select_prompt),
            subtitle = "开源模型与商业API两大体系 · 点选即用即配",
            onBack = onBack,
        )

        // 顶部分段大类选择胶囊 (开源模型 vs API付费)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(EngineCategory.OPEN_SOURCE, EngineCategory.API_PAID).forEach { cat ->
                val isSelected = selectedCategory == cat
                val animatedBg by animateColorAsState(
                    targetValue = if (isSelected) Booth.Accent else Color.Transparent,
                    animationSpec = tween(200),
                    label = "engineCatBg",
                )
                val animatedTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface,
                    animationSpec = tween(200),
                    label = "engineCatText",
                )
                val count = if (cat == EngineCategory.OPEN_SOURCE) {
                    TranslationEngineCatalog.OPEN_SOURCE_TRANSLATION_PRESETS.size
                } else {
                    TranslationEngineCatalog.getTranslationEnginesByCategory(EngineCategory.API_PAID).size
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(animatedBg)
                        .clickable { selectedCategory = cat }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${cat.icon} ${cat.label} ($count)",
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = animatedTextColor,
                    )
                }
            }
        }

        SmallTitle(
            text = "${selectedCategory.icon} ${selectedCategory.label}列表（点击直接生效与配置）",
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        if (selectedCategory == EngineCategory.OPEN_SOURCE) {
            // 开源模型一栏：直接把所有的开源模型全部列出来！
            val openPresets = TranslationEngineCatalog.OPEN_SOURCE_TRANSLATION_PRESETS
            val rows = remember { openPresets.chunked(2) }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowPresets.forEach { preset ->
                            val isSelected = (preset.engineType == currentEngine && preset.defaultModelName.isBlank()) ||
                                (preset.engineType == currentEngine && genericModel.text == preset.defaultModelName) ||
                                (preset.id == selectedOpenPresetId)
                            val isCurrentlyInUse = (preset.engineType == currentEngine && preset.defaultModelName.isBlank()) ||
                                (preset.engineType == currentEngine && genericModel.text == preset.defaultModelName)
                            val brandColor = Color(preset.brandColorLong)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) brandColor.copy(alpha = 0.15f)
                                        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.8.dp else 0.8.dp,
                                        color = if (isSelected) brandColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable {
                                        selectedOpenPresetId = preset.id
                                        onSelectOpenSourcePreset(preset)
                                    }
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(brandColor.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(text = preset.iconEmoji, fontSize = 18.sp)
                                        }
                                        if (isCurrentlyInUse) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "✓ 在用",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = preset.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = preset.categoryBadge,
                                        fontSize = 11.sp,
                                        color = brandColor,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (rowPresets.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 当前选中的开源模型配置与说明
            val activePreset = openPresets.firstOrNull { it.id == selectedOpenPresetId } ?: openPresets.first()
            val activeColor = Color(activePreset.brandColorLong)

            SmallTitle(
                text = "当前模型配置与详细说明",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(activeColor.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = activePreset.iconEmoji, fontSize = 24.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activePreset.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "路线归属：🤖 开源模型 · ${activePreset.developer}",
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    Text(
                        text = activePreset.highlight,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = activePreset.detailText,
                        fontSize = 12.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 18.sp,
                    )

                    SettingDivider()

                    Text(
                        text = "🎯 适用场景：${activePreset.suitableScenarios}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = "💰 资费成本：${activePreset.costDesc}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    )

                    if (activePreset.modelScopeUrl.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "🔗 魔搭社区极速下载 ↗",
                                fontSize = 12.sp,
                                color = Booth.Accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activePreset.modelScopeUrl)))
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            )
                            Text(
                                text = "📋 复制下载直链",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("download_url", activePreset.modelScopeUrl))
                                        Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }
                    }

                    // 具体的模型配置项
                    if (activePreset.engineType == TranslationEngineType.OPENAI_COMPAT) {
                        SettingDivider()
                        Text(
                            text = "本地 / 局域网服务配置",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )

                        // 电脑运行与手机局域网连接说明横幅
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Booth.Accent.copy(alpha = 0.10f))
                                .padding(10.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "💡 电脑私有化运行 · 手机局域网调用指南",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Booth.Accent,
                                )
                                Text(
                                    text = "1. 开源翻译大模型需在电脑上运行（推荐安装 Ollama 并设置 OLLAMA_HOST=0.0.0.0:11434 允许局域网连接）；\n" +
                                        "2. 确保手机与电脑连入同一路由器 Wi-Fi 局域网；\n" +
                                        "3. 电脑终端输入 ipconfig 查看 IPv4 地址，在下方 Base URL 填入如 http://192.168.1.xxx:11434/v1 即可免 Key 调用电脑算力！",
                                    fontSize = 11.5.sp,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp,
                                )
                            }
                        }

                        OutlinedTextField(
                            value = genericBaseUrl,
                            onValueChange = onUpdateGenericBaseUrl,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text("API 地址 (Base URL)") },
                            placeholder = { androidx.compose.material3.Text("http://192.168.1.100:11434/v1") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = genericModel,
                            onValueChange = onUpdateGenericModel,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text("模型名称标识") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = onSaveAndTest,
                                enabled = !testing,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(
                                    text = if (testing) "正在测试连接…" else "保存并测试连接",
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            TextButton(
                                text = "仅保存",
                                onClick = onSaveKeyOnly,
                            )
                        }
                        if (testing) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        testResult?.let { resultText ->
                            val hasOk = resultText.contains("✅")
                            val hasFail = resultText.contains("❌")
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            hasOk && !hasFail -> Booth.Accent.copy(alpha = 0.10f)
                                            hasFail -> Color(0xFFD32F2F).copy(alpha = 0.10f)
                                            else -> MiuixTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = resultText,
                                    fontSize = 13.sp,
                                    color = when {
                                        hasOk && !hasFail -> Booth.Accent
                                        hasFail -> Color(0xFFD32F2F)
                                        else -> MiuixTheme.colorScheme.onSurface
                                    },
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    } else {
                        SettingDivider()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF10B981).copy(alpha = 0.10f))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "✅ 免密通道已激活，即开即用，无需配置 API Key 与地址！",
                                fontSize = 13.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }
        } else {
            // API 付费一栏：列出 DeepSeek、智谱、Gemini、自定义 API
            val paidEngines = remember {
                TranslationEngineCatalog.getTranslationEnginesByCategory(EngineCategory.API_PAID)
            }
            val rows = remember(paidEngines) { paidEngines.chunked(2) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEach { rowEngines ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowEngines.forEach { engine ->
                            val isSelected = engine == currentEngine
                            val detail = TranslationEngineCatalog.getDetail(engine)
                            val brandColor = Color(detail.brandColorLong)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) brandColor.copy(alpha = 0.15f)
                                        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.8.dp else 0.8.dp,
                                        color = if (isSelected) brandColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable { onSelectEngine(engine) }
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(brandColor.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(text = detail.iconEmoji, fontSize = 18.sp)
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "✓ 在用",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(engine.titleRes),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = detail.categoryBadge,
                                        fontSize = 11.sp,
                                        color = brandColor,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (rowEngines.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 当前选中的商业 API 引擎配置与说明
            val activeDetail = TranslationEngineCatalog.getDetail(currentEngine)
            val activeBrandColor = Color(activeDetail.brandColorLong)

            SmallTitle(
                text = "当前引擎配置与详细说明",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(activeBrandColor.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = activeDetail.iconEmoji, fontSize = 24.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(currentEngine.titleRes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "研发机构/协议：" + activeDetail.developer,
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    Text(
                        text = activeDetail.highlight,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = activeDetail.detailText,
                        fontSize = 12.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 18.sp,
                    )

                    SettingDivider()

                    Text(
                        text = "🎯 适用场景：${activeDetail.suitableScenarios}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = "💰 资费成本：${activeDetail.costDesc}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    )

                    SettingDivider()

                    // 配置项
                    when (currentEngine) {
                        TranslationEngineType.DEEPSEEK -> {
                            OutlinedTextField(
                                value = deepSeekKey,
                                onValueChange = onDeepSeekKeyChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("DeepSeek API Key") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = if (revealKey) "隐藏 API Key" else "显示 API Key",
                                    onClick = { revealKey = !revealKey },
                                )
                                TextButton(
                                    text = "去 DeepSeek 官网获取 Key ↗",
                                    onClick = { openUrl(context, "https://platform.deepseek.com/api_keys") },
                                )
                            }
                            ModelPickerEntryRow(
                                model = deepSeekModel.text.ifBlank { UserSettings.Defaults.DEEPSEEK_MODEL },
                                onClick = { onOpenModelPicker(currentEngine) },
                            )
                            SettingSwitchRow(
                                title = "AI 深度思考",
                                summary = "开启后模型进行深度思考，延迟增加约 1~3 秒",
                                checked = settings.aiDeepThinking,
                                onCheckedChange = onUpdateAiDeepThinking,
                                enabled = true,
                            )
                            EngineTextField(
                                value = deepSeekBaseUrl,
                                onValueChange = onUpdateDeepSeekBaseUrl,
                                labelRes = R.string.settings_deepseek_base_url,
                                colors = fieldColors,
                            )
                        }
                        TranslationEngineType.ZHIPU -> {
                            OutlinedTextField(
                                value = zhipuKey,
                                onValueChange = onZhipuKeyChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("智谱 API Key") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = if (revealKey) "隐藏 API Key" else "显示 API Key",
                                    onClick = { revealKey = !revealKey },
                                )
                                TextButton(
                                    text = "去智谱开放平台获取 Key ↗",
                                    onClick = { openUrl(context, "https://open.bigmodel.cn/usercenter/apikeys") },
                                )
                            }
                            ModelPickerEntryRow(
                                model = zhipuModel.text.ifBlank { UserSettings.Defaults.ZHIPU_MODEL },
                                onClick = { onOpenModelPicker(currentEngine) },
                            )
                            SettingSwitchRow(
                                title = "AI 深度思考",
                                summary = "开启后模型进行深度思考，延迟增加约 1~3 秒",
                                checked = settings.aiDeepThinking,
                                onCheckedChange = onUpdateAiDeepThinking,
                                enabled = true,
                            )
                            EngineTextField(
                                value = zhipuBaseUrl,
                                onValueChange = onUpdateZhipuBaseUrl,
                                labelRes = R.string.settings_zhipu_base_url,
                                colors = fieldColors,
                            )
                        }
                        TranslationEngineType.GEMINI -> {
                            OutlinedTextField(
                                value = geminiKey,
                                onValueChange = onGeminiKeyChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("Gemini API Key") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = if (revealKey) "隐藏 API Key" else "显示 API Key",
                                    onClick = { revealKey = !revealKey },
                                )
                                TextButton(
                                    text = "去 Google AI Studio 获取 Key ↗",
                                    onClick = { openUrl(context, "https://aistudio.google.com/apikey") },
                                )
                            }
                            ModelPickerEntryRow(
                                model = geminiModel.text.ifBlank { UserSettings.Defaults.GEMINI_MODEL },
                                onClick = { onOpenModelPicker(currentEngine) },
                            )
                            EngineTextField(
                                value = geminiBaseUrl,
                                onValueChange = onUpdateGeminiBaseUrl,
                                labelRes = R.string.settings_gemini_base_url,
                                colors = fieldColors,
                            )
                        }
                        TranslationEngineType.OPENAI_COMPAT -> {
                            OutlinedTextField(
                                value = genericBaseUrl,
                                onValueChange = onUpdateGenericBaseUrl,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("API 地址 (Base URL)") },
                                placeholder = { androidx.compose.material3.Text("https://api.openai.com/v1") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            OutlinedTextField(
                                value = genericKey,
                                onValueChange = onGenericKeyChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("API Key (可选)") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            OutlinedTextField(
                                value = genericModel,
                                onValueChange = onUpdateGenericModel,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("模型名称标识") },
                                placeholder = { androidx.compose.material3.Text("如 gpt-4o-mini 或 deepseek-chat") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                        }
                        else -> Unit
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onSaveAndTest,
                            enabled = !testing,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = if (testing) "正在测试连接…" else "保存并测试连接",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(
                            text = "仅保存",
                            onClick = onSaveKeyOnly,
                        )
                    }

                    if (testing) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    testResult?.let { resultText ->
                        val hasOk = resultText.contains("✅")
                        val hasFail = resultText.contains("❌")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        hasOk && !hasFail -> Booth.Accent.copy(alpha = 0.10f)
                                        hasFail -> Color(0xFFD32F2F).copy(alpha = 0.10f)
                                        else -> MiuixTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = resultText,
                                fontSize = 13.sp,
                                color = when {
                                    hasOk && !hasFail -> Booth.Accent
                                    hasFail -> Color(0xFFD32F2F)
                                    else -> MiuixTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // 申请指南
            SmallTitle(
                text = "申请指南",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showApiKeyGuide = !showApiKeyGuide }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_apikey_guide),
                            color = Booth.Accent,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = if (showApiKeyGuide) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
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
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

/**
 * Two-level model picker: the collapsed row shows the current model; opening it lists
 * model families, and each family opens a submenu of its models.
 */
@Composable
private fun AliyunModelDropdown(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val current = AsrModelCatalog.aliyunPreset(selectedId.trim())
        ?: AsrModelCatalog.aliyunPreset(UserSettings.Defaults.ALIYUN_ASR_MODEL)
    var expanded by remember { mutableStateOf(false) }
    var openGroup by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .clickable { expanded = true; openGroup = null }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = current?.name ?: selectedId.ifBlank { UserSettings.Defaults.ALIYUN_ASR_MODEL },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = current?.id ?: selectedId,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false; openGroup = null },
            modifier = Modifier.fillMaxWidth(0.9f),
        ) {
            AsrModelCatalog.ALIYUN_GROUPS.forEach { (group, presets) ->
                val groupSelected = presets.any { it.id == current?.id }
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = group.ifBlank { "其他" },
                                fontSize = 14.sp,
                                fontWeight = if (groupSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (groupSelected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "›",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    },
                    onClick = { openGroup = if (openGroup == group) null else group },
                )
                if (openGroup == group) {
                    presets.forEach { preset ->
                        val isSelected = preset.id == current?.id
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(start = 16.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                                        )
                                        if (preset.recommended) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Booth.Accent)
                                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                            ) {
                                                Text(
                                                    text = "推荐",
                                                    fontSize = 9.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${preset.id} · ${preset.description}",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    )
                                }
                            },
                            onClick = {
                                onSelect(preset.id)
                                expanded = false
                                openGroup = null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * 语音识别引擎配置二级页面：包含本地 SenseVoice 模型管理、阿里百炼 Key 配置、通用 OpenAI 兼容配置。
 */
@Composable
private fun AsrEngineConfigSubScreen(
    engineType: AsrEngineType,
    isCurrentEngine: Boolean,
    onSetAsCurrent: () -> Unit,
    modelStatus: String,
    downloading: Boolean,
    downloadProgress: Float,
    onDownloadModels: () -> Unit,
    onRefreshModelStatus: () -> Unit,
    mirrorUrl: TextFieldValue,
    onUpdateMirrorUrl: (TextFieldValue) -> Unit,
    hfToken: TextFieldValue,
    onUpdateHfToken: (TextFieldValue) -> Unit,
    aliyunAsrKey: TextFieldValue,
    onUpdateAliyunAsrKey: (TextFieldValue) -> Unit,
    aliyunAsrModel: TextFieldValue,
    onUpdateAliyunAsrModel: (TextFieldValue) -> Unit,
    aliyunWorkspaceId: TextFieldValue,
    onUpdateAliyunWorkspaceId: (TextFieldValue) -> Unit,
    genericAsrBaseUrl: TextFieldValue,
    onUpdateGenericAsrBaseUrl: (TextFieldValue) -> Unit,
    genericAsrApiKey: TextFieldValue,
    onUpdateGenericAsrApiKey: (TextFieldValue) -> Unit,
    genericAsrModel: TextFieldValue,
    onUpdateGenericAsrModel: (TextFieldValue) -> Unit,
    fieldColors: TextFieldColors,
    asrTesting: Boolean,
    asrTestResult: String?,
    onSaveAndTest: () -> Unit,
    onSaveOnly: () -> Unit,
    settings: UserSettings,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    localAsrProbeResult: LocalAsrProbeResult?,
    onSelectLocalAsrModel: (String) -> Unit,
    onRefreshLocalAsrProbe: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }
    val context = LocalContext.current
    var revealKey by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SubScreenHeader(
            title = stringResource(engineType.titleRes) + " 配置",
            subtitle = stringResource(engineType.summaryRes),
            onBack = onBack,
            trailingContent = {
                if (!isCurrentEngine) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Booth.Accent.copy(alpha = 0.12f))
                            .clickable(onClick = onSetAsCurrent)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "设为当前引擎",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "✓ 正在使用",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981),
                        )
                    }
                }
            },
        )

        when (engineType) {
            AsrEngineType.LOCAL_SENSEVOICE -> {
                val currentPreset = LocalAsrModelCatalog.getPresetById(settings.localAsrModel)
                var showImportGuide by rememberSaveable { mutableStateOf(false) }

                SmallTitle(
                    text = "开源语音识别 (ASR) 模型生态",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "选择要使用的本地离线模型（纯端侧 · 0 流量）：",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        LocalAsrModelCatalog.PRESETS.forEach { preset ->
                            val isSelected = preset.id == settings.localAsrModel
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isSelected) Booth.Accent.copy(alpha = 0.10f) else Color.Transparent
                                    )
                                    .clickable { onSelectLocalAsrModel(preset.id) }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "架构：${preset.architecture.name.lowercase()} · 约 ${preset.sizeMb} MB · 语种：${preset.supportedLangs.joinToString("/")}",
                                        fontSize = 11.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                                if (isSelected) {
                                    Text(
                                        text = "✓",
                                        color = Booth.Accent,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(start = 8.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                SmallTitle(
                    text = "当前模型状态与下载直链",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = currentPreset.description,
                            fontSize = 12.5.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                            lineHeight = 18.sp,
                        )

                        // 状态指示徽章
                        val isReady = localAsrProbeResult?.isReady == true ||
                            (currentPreset.id == LocalAsrModelCatalog.DEFAULT_MODEL_ID && modelStatus.startsWith("✅"))

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isReady) Color(0xFF10B981).copy(alpha = 0.12f)
                                    else Color(0xFFF59E0B).copy(alpha = 0.12f)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = if (isReady) {
                                    "🟢 模型已就绪（本地离线完整可用 · 0 流量）"
                                } else if (currentPreset.id == LocalAsrModelCatalog.DEFAULT_MODEL_ID) {
                                    modelStatus.ifBlank { "⚪ 未就绪，可在下方下载" }
                                } else {
                                    "⚪ 未检测到权重文件（同传时自动平滑回退至内置 SenseVoice）"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isReady) Color(0xFF10B981) else Color(0xFFF59E0B),
                            )
                        }

                        // 快捷操作栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "🔗 魔搭社区极速下载 ↗",
                                fontSize = 12.sp,
                                color = Booth.Accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentPreset.domesticDownloadUrl)))
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            )
                            Text(
                                text = "🔗 HF-Mirror 镜像 ↗",
                                fontSize = 12.sp,
                                color = Booth.Accent,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentPreset.mirrorUrl)))
                                        }
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "📋 复制下载链接",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .clickable {
                                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cm?.setPrimaryClip(ClipData.newPlainText("download_url", currentPreset.domesticDownloadUrl))
                                        Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                            )
                            Text(
                                text = "🔄 刷新检测状态",
                                fontSize = 12.sp,
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clickable {
                                        onRefreshLocalAsrProbe()
                                        onRefreshModelStatus()
                                        Toast.makeText(context, "已刷新模型状态", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                            )
                        }

                        // 折叠使用说明卡片
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.surface)
                                .clickable { showImportGuide = !showImportGuide }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "📂 手机导入外部模型说明",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = if (showImportGuide) "收起 ▲" else "展开 ▼",
                                    fontSize = 11.sp,
                                    color = Booth.Accent,
                                )
                            }
                        }

                        AnimatedVisibility(visible = showImportGuide) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = "1. 从 ModelScope 或 HF-Mirror 下载对应模型的压缩包并解压；",
                                    fontSize = 11.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    text = "2. 手机通过 USB 连接电脑或使用手机文件管理器，将解压后的文件夹放入手机内部存储：\nAndroid/data/com.xzm.realtimetranslate/files/models/asr/${currentPreset.id}/",
                                    fontSize = 11.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    text = "3. 放入后点击上方的「🔄 刷新检测状态」，状态变为 🟢 已就绪 即可直接生效！",
                                    fontSize = 11.5.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    lineHeight = 16.sp,
                                )
                                Text(
                                    text = "💡 自动防崩溃保护：若模型未下载，启动同传时将自动平滑回退至内置 SenseVoice，绝不报错或闪退。",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF10B981),
                                    lineHeight = 16.sp,
                                )
                            }
                        }

                        if (currentPreset.id == LocalAsrModelCatalog.DEFAULT_MODEL_ID) {
                            Spacer(modifier = Modifier.height(4.dp))
                            if (downloading) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            Button(
                                onClick = {
                                    if (downloading) onRefreshModelStatus()
                                    else onDownloadModels()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = if (downloading) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(
                                    text = stringResource(if (downloading) R.string.settings_model_downloading else R.string.settings_model_download),
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                            OutlinedTextField(
                                value = mirrorUrl,
                                onValueChange = onUpdateMirrorUrl,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text(stringResource(R.string.settings_model_mirror)) },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            OutlinedTextField(
                                value = hfToken,
                                onValueChange = onUpdateHfToken,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text(stringResource(R.string.settings_model_hf_token)) },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                        }
                    }
                }
            }

            AsrEngineType.ALIYUN_FUN_ASR -> {
                SmallTitle(
                    text = "阿里百炼云端流式配置",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = aliyunAsrKey,
                            onValueChange = onUpdateAliyunAsrKey,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text("DashScope API Key") },
                            singleLine = true,
                            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = if (revealKey) "隐藏 API Key" else "显示 API Key",
                                onClick = { revealKey = !revealKey },
                            )
                            TextButton(
                                text = "去百炼控制台获取 Key ↗",
                                onClick = { openUrl(context, ALIYUN_API_KEY_URL) },
                            )
                        }

                        OutlinedTextField(
                            value = aliyunWorkspaceId,
                            onValueChange = onUpdateAliyunWorkspaceId,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text("业务空间 ID（可选）") },
                            placeholder = { androidx.compose.material3.Text("例如 llm-xxxxxxxx") },
                            supportingText = {
                                androidx.compose.material3.Text(
                                    "留空则走旧域名；填写后改用业务空间专属域名，Qwen 系列新模型需要它才能连上",
                                    fontSize = 11.sp,
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "选择百炼实时语音识别模型",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        AliyunModelDropdown(
                            selectedId = aliyunAsrModel.text,
                            onSelect = { onUpdateAliyunAsrModel(TextFieldValue(it)) },
                        )
                        Text(
                            text = "模型清单随百炼官方按月更新 · 最近核对 " +
                                AsrModelCatalog.CATALOG_LAST_VERIFIED,
                            fontSize = 11.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            lineHeight = 16.sp,
                        )

                        OutlinedTextField(
                            value = aliyunAsrModel,
                            onValueChange = onUpdateAliyunAsrModel,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text("当前模型 ID（支持直接修改或填入自定义模型）") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                    }
                }
            }

            AsrEngineType.GENERIC_OPENAI -> {
                SmallTitle(
                    text = "OpenAI 兼容语音转录配置",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedTextField(
                            value = genericAsrBaseUrl,
                            onValueChange = onUpdateGenericAsrBaseUrl,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text(stringResource(R.string.settings_asr_generic_base_url)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                        OutlinedTextField(
                            value = genericAsrApiKey,
                            onValueChange = onUpdateGenericAsrApiKey,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text(stringResource(R.string.settings_asr_generic_key)) },
                            singleLine = true,
                            visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(
                                text = if (revealKey) "隐藏 API Key" else "显示 API Key",
                                onClick = { revealKey = !revealKey },
                            )
                            TextButton(
                                text = "去硅基流动免费申请 Key ↗",
                                onClick = { openUrl(context, "https://cloud.siliconflow.cn/account/ak") },
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "常用优质转录模型预设（点击快速选择）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AsrModelCatalog.GENERIC_OPENAI_MODELS.forEach { preset ->
                                val isSelected = genericAsrModel.text == preset.id ||
                                    (genericAsrModel.text.isBlank() && preset.id == "FunAudioLLM/SenseVoiceSmall")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isSelected) Booth.Accent.copy(alpha = 0.12f)
                                            else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                        )
                                        .clickable {
                                            onUpdateGenericAsrModel(TextFieldValue(preset.id))
                                            if (genericAsrBaseUrl.text.isBlank()) {
                                                onUpdateGenericAsrBaseUrl(TextFieldValue("https://api.siliconflow.cn/v1"))
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Text(
                                                text = preset.name,
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                                            )
                                            if (preset.recommended) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(Booth.Accent)
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                ) {
                                                    Text(
                                                        text = "推荐",
                                                        fontSize = 10.sp,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = "${preset.id} · ${preset.description}",
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            lineHeight = 16.sp,
                                        )
                                    }
                                    if (isSelected) {
                                        Text(text = "✓", color = Booth.Accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        OutlinedTextField(
                            value = genericAsrModel,
                            onValueChange = onUpdateGenericAsrModel,
                            modifier = Modifier.fillMaxWidth(),
                            label = { androidx.compose.material3.Text("当前模型 ID（支持直接修改或填入自定义模型）") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = fieldColors,
                        )
                    }
                }
            }
        }

        SpeechSlicingAndSilenceCard(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            currentAsrEngine = engineType,
        )

        // 保存并测试连接卡片
        SmallTitle(
            text = "连接测试与生效",
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onSaveAndTest,
                        enabled = !asrTesting,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = if (asrTesting) "正在检测连接…" else "保存并测试连接",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    TextButton(
                        text = "仅保存配置",
                        onClick = onSaveOnly,
                    )
                }

                if (asrTesting) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                asrTestResult?.let { resultText ->
                    val hasOk = resultText.contains("✅")
                    val hasFail = resultText.contains("❌")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    hasOk && !hasFail -> Booth.Accent.copy(alpha = 0.10f)
                                    hasFail -> Color(0xFFD32F2F).copy(alpha = 0.10f)
                                    else -> MiuixTheme.colorScheme.surfaceVariant
                                }
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = resultText,
                            fontSize = 13.sp,
                            color = when {
                                hasOk && !hasFail -> Booth.Accent
                                hasFail -> Color(0xFFD32F2F)
                                else -> MiuixTheme.colorScheme.onSurface
                            },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

// 语音识别弹窗已彻底扁平化，合并至下方的 AsrEnginePickerSubScreen 中直接呈现

/**
 * 语音识别引擎与模型一体化页面：开源模型与商业 API 付费两大生态体系，方格网格点选即配，配置与详细说明就地展开。
 */
@Composable
private fun AsrEnginePickerSubScreen(
    currentEngine: AsrEngineType,
    currentLocalModel: String,
    localAsrProbeResult: LocalAsrProbeResult?,
    onSelectEngine: (AsrEngineType) -> Unit,
    onSelectLocalAsrModel: (String) -> Unit,
    onRefreshLocalAsrProbe: () -> Unit,
    modelStatus: String,
    downloading: Boolean,
    downloadProgress: Float,
    onDownloadModels: () -> Unit,
    onRefreshModelStatus: () -> Unit,
    mirrorUrl: TextFieldValue,
    onUpdateMirrorUrl: (TextFieldValue) -> Unit,
    hfToken: TextFieldValue,
    onUpdateHfToken: (TextFieldValue) -> Unit,
    aliyunAsrKey: TextFieldValue,
    onUpdateAliyunAsrKey: (TextFieldValue) -> Unit,
    aliyunAsrModel: TextFieldValue,
    onUpdateAliyunAsrModel: (TextFieldValue) -> Unit,
    aliyunWorkspaceId: TextFieldValue,
    onUpdateAliyunWorkspaceId: (TextFieldValue) -> Unit,
    genericAsrBaseUrl: TextFieldValue,
    onUpdateGenericAsrBaseUrl: (TextFieldValue) -> Unit,
    genericAsrApiKey: TextFieldValue,
    onUpdateGenericAsrApiKey: (TextFieldValue) -> Unit,
    genericAsrModel: TextFieldValue,
    onUpdateGenericAsrModel: (TextFieldValue) -> Unit,
    fieldColors: TextFieldColors,
    asrTesting: Boolean,
    asrTestResult: String?,
    onSaveAndTest: () -> Unit,
    onSaveOnly: () -> Unit,
    settings: UserSettings,
    onUpdateSettings: ((UserSettings) -> UserSettings) -> Unit,
    modelImportUiState: ModelImportUiState = ModelImportUiState.Idle,
    onImportModelArchive: (Uri, String?) -> Unit = { _, _ -> },
    onClearModelImportState: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }
    val initialCat = TranslationEngineCatalog.getAsrDetail(currentEngine).category
    var selectedCategory by rememberSaveable { mutableStateOf(initialCat) }
    var selectedLocalModelId by rememberSaveable { mutableStateOf(currentLocalModel) }
    var revealKey by remember { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val archivePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            onImportModelArchive(uri, selectedLocalModelId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SubScreenHeader(
            title = stringResource(R.string.settings_asr_engine),
            subtitle = "开源模型与商业API两大体系 · 点选即用即配",
            onBack = onBack,
        )

        // 顶层大类分段选择胶囊 (开源模型 vs API付费)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(EngineCategory.OPEN_SOURCE, EngineCategory.API_PAID).forEach { cat ->
                val isSelected = selectedCategory == cat
                val animatedBg by animateColorAsState(
                    targetValue = if (isSelected) Booth.Accent else Color.Transparent,
                    animationSpec = tween(200),
                    label = "asrCatBg",
                )
                val animatedTextColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface,
                    animationSpec = tween(200),
                    label = "asrCatText",
                )
                val count = if (cat == EngineCategory.OPEN_SOURCE) {
                    LocalAsrModelCatalog.PRESETS.size
                } else {
                    TranslationEngineCatalog.getAsrEnginesByCategory(EngineCategory.API_PAID).size
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(animatedBg)
                        .clickable { selectedCategory = cat }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${cat.icon} ${cat.label} ($count)",
                        fontSize = 13.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = animatedTextColor,
                    )
                }
            }
        }

        SmallTitle(
            text = "${selectedCategory.icon} ${selectedCategory.label}列表（点击直接生效与配置）",
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        if (selectedCategory == EngineCategory.OPEN_SOURCE) {
            // 开源语音识别模型网格：直列全部 8 大模型
            val localPresets = LocalAsrModelCatalog.PRESETS
            val rows = remember { localPresets.chunked(2) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEach { rowPresets ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowPresets.forEach { preset ->
                            val isCurrentlyInUse = currentEngine == AsrEngineType.LOCAL_SENSEVOICE && currentLocalModel == preset.id
                            val isSelected = (selectedLocalModelId == preset.id) || isCurrentlyInUse
                            val brandColor = Color(0xFF10B981) // 开源端侧薄荷绿

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) brandColor.copy(alpha = 0.15f)
                                        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.8.dp else 0.8.dp,
                                        color = if (isSelected) brandColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable {
                                        selectedLocalModelId = preset.id
                                        onSelectLocalAsrModel(preset.id)
                                    }
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(brandColor.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(text = "📱", fontSize = 18.sp)
                                        }
                                        if (isCurrentlyInUse) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "✓ 在用",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = preset.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${preset.badge} · 约 ${preset.sizeMb}MB",
                                        fontSize = 11.sp,
                                        color = brandColor,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (rowPresets.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 当前选中的开源模型配置与说明展开
            val activePreset = LocalAsrModelCatalog.getPresetById(selectedLocalModelId)
            val isReady = (localAsrProbeResult?.isReady == true && currentLocalModel == activePreset.id) ||
                (activePreset.id == LocalAsrModelCatalog.DEFAULT_MODEL_ID && modelStatus.contains("已就绪"))

            SmallTitle(
                text = "当前模型配置与详细说明",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981).copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "📱", fontSize = 24.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activePreset.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "${activePreset.badge} · 架构：${activePreset.architecture.name} · 约 ${activePreset.sizeMb}MB",
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    // 就绪状态徽章
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isReady) Color(0xFF10B981).copy(alpha = 0.12f)
                                else Color(0xFFF59E0B).copy(alpha = 0.12f)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            text = if (isReady) "🟢 模型已就绪（端侧纯离线推理，零流量，零费用）"
                            else "⚪ 尚未在手机存储检测到完整权重（系统自动平滑降级至 SenseVoice）",
                            fontSize = 12.5.sp,
                            color = if (isReady) Color(0xFF10B981) else Color(0xFFF59E0B),
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Text(
                        text = activePreset.description,
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 18.sp,
                    )

                    SettingDivider()

                    Text(
                        text = "🌐 识别语种：${activePreset.supportedLangs.joinToString(" · ")}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (activePreset.supportsDraft) "⚡ 低延迟打字机：支持 350~400ms 高频流式草稿"
                        else "🛡️ 手机过热防护：自回归 Speech-LLM 架构，已抑制高频草稿防 CPU 积压发热",
                        fontSize = 12.sp,
                        color = if (activePreset.supportsDraft) Color(0xFF10B981) else Color(0xFF3B82F6),
                    )

                    SettingDivider()

                    // 下载直链与操作
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🔗 魔搭社区极速下载 ↗",
                            fontSize = 12.sp,
                            color = Booth.Accent,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clickable {
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(activePreset.domesticDownloadUrl)))
                                    }
                                }
                                .padding(vertical = 4.dp),
                        )
                        Text(
                            text = "📋 复制下载链接",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier
                                .clickable {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("download_url", activePreset.domesticDownloadUrl))
                                    Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 4.dp),
                        )
                        Text(
                            text = "🔄 刷新检测",
                            fontSize = 12.sp,
                            color = Booth.Accent,
                            modifier = Modifier
                                .clickable { onRefreshLocalAsrProbe() }
                                .padding(vertical = 4.dp),
                        )
                    }

                    // 一键选择已下载压缩包并自动解压配置
                    val isImporting = modelImportUiState is ModelImportUiState.Importing
                    Button(
                        onClick = {
                            archivePickerLauncher.launch(arrayOf("*/*"))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isImporting,
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = if (isImporting) "正在解包并校验模型..." else "📦 一键导入模型压缩包 (.zip / .tar.bz2 / .tar.gz)",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }

                    // 导入状态反馈
                    when (val state = modelImportUiState) {
                        is ModelImportUiState.Importing -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Booth.Accent.copy(alpha = 0.10f))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = state.progressMessage,
                                    fontSize = 12.sp,
                                    color = Booth.Accent,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        is ModelImportUiState.Success -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🎉 模型导入成功！",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF10B981),
                                        )
                                        Text(
                                            text = "已自动部署 ${state.modelName} (${state.fileCount} 个文件，共 %.1f MB)，已直接设为在用！".format(state.totalBytes / (1024f * 1024f)),
                                            fontSize = 11.5.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                        )
                                    }
                                    Text(
                                        text = "✕",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .clickable { onClearModelImportState() }
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }
                        is ModelImportUiState.Error -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.15f))
                                    .padding(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "❌ 导入失败：${state.errorMessage}",
                                        fontSize = 12.sp,
                                        color = Color(0xFFEF4444),
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "✕",
                                        fontSize = 14.sp,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .clickable { onClearModelImportState() }
                                            .padding(4.dp),
                                    )
                                }
                            }
                        }
                        ModelImportUiState.Idle -> { /* do nothing */ }
                    }

                    // 导入说明卡片（可折叠）
                    var showAsrGuide by rememberSaveable { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                            .clickable { showAsrGuide = !showAsrGuide }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "💡 外部模型导入使用说明（一键导入 vs 手动拷贝）",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = if (showAsrGuide) "收起 ▲" else "展开 ▼",
                                fontSize = 11.sp,
                                color = Booth.Accent,
                            )
                        }
                    }

                    AnimatedVisibility(visible = showAsrGuide) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = "方式 1（推荐 · 极简免电脑）：\n" +
                                    "在手机浏览器、魔搭或微信中下载好模型压缩包（.zip 或 .tar.bz2），回到这里直接点击上方的「📦 一键导入模型压缩包」，选中该压缩包即可。App 会在后台全自动解包、校验架构权重并存入应用安全私有目录，完全不受安卓系统权限限制！",
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                lineHeight = 16.sp,
                            )
                            Text(
                                text = "方式 2（传统 USB 电脑拷入）：\n" +
                                    "在电脑上下载并解压模型，通过 USB 数据线连接手机，将解压后的文件夹复制到手机内部存储：\nAndroid/data/com.xzm.realtimetranslate/files/models/asr/${activePreset.id}/\n放好后点击「🔄 刷新检测」即可激活。",
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                lineHeight = 16.sp,
                            )
                            Text(
                                text = "🛡️ 自动防崩溃保护：若所选模型未就绪，启动同传时将自动平滑回退至内置 SenseVoice，绝不报错或闪退。",
                                fontSize = 11.5.sp,
                                color = Color(0xFF10B981),
                                lineHeight = 16.sp,
                            )
                        }
                    }

                    // 内置 SenseVoice 专属修复/下载通道
                    if (activePreset.id == LocalAsrModelCatalog.DEFAULT_MODEL_ID) {
                        SettingDivider()
                        Text(
                            text = "内置基础模型管理",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "状态：$modelStatus",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        if (downloading) {
                            LinearProgressIndicator(
                                progress = { downloadProgress.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Button(
                            onClick = {
                                if (downloading) onRefreshModelStatus()
                                else onDownloadModels()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = if (downloading) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = if (downloading) "正在下载模型 ($downloadProgress%)..." else "下载 / 修复 SenseVoice 模型",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    SettingDivider()

                    // VAD 停顿切句参数微调
                    Text(
                        text = "VAD 语音活动检测参数",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "分段停顿门槛（静音切句时长）",
                            fontSize = 12.5.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "%.1f 秒".format(settings.vadMinSilenceSec),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.vadMinSilenceSec.coerceIn(0.1f, 1.0f),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(vadMinSilenceSec = (v * 10).roundToInt() / 10f) }
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "单句最长语音切片",
                            fontSize = 12.5.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "%.1f 秒".format(settings.vadMaxSpeechSec),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.vadMaxSpeechSec.coerceIn(1f, 10f),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(vadMaxSpeechSec = (v * 2).roundToInt() / 2f) }
                        },
                        valueRange = 1f..10f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            // API 付费一栏：列出 阿里百炼 FunASR 与 自定义 API 转录
            val paidEngines = remember {
                TranslationEngineCatalog.getAsrEnginesByCategory(EngineCategory.API_PAID)
            }
            val rows = remember(paidEngines) { paidEngines.chunked(2) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                rows.forEach { rowEngines ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowEngines.forEach { engine ->
                            val isSelected = engine == currentEngine
                            val detail = TranslationEngineCatalog.getAsrDetail(engine)
                            val brandColor = Color(detail.brandColorLong)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        if (isSelected) brandColor.copy(alpha = 0.15f)
                                        else MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
                                    )
                                    .border(
                                        width = if (isSelected) 1.8.dp else 0.8.dp,
                                        color = if (isSelected) brandColor else Color.White.copy(alpha = 0.08f),
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable { onSelectEngine(engine) }
                                    .padding(12.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(brandColor.copy(alpha = 0.20f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(text = detail.iconEmoji, fontSize = 18.sp)
                                        }
                                        if (isSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                            ) {
                                                Text(
                                                    text = "✓ 在用",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF10B981),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(engine.titleRes),
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = detail.categoryBadge,
                                        fontSize = 11.sp,
                                        color = brandColor,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        if (rowEngines.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // 当前商业 API 引擎配置与说明
            val activeDetail = TranslationEngineCatalog.getAsrDetail(currentEngine)
            val activeBrandColor = Color(activeDetail.brandColorLong)

            SmallTitle(
                text = "当前引擎配置与详细说明",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(activeBrandColor.copy(alpha = 0.20f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = activeDetail.iconEmoji, fontSize = 24.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(currentEngine.titleRes),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = activeDetail.highlight,
                                fontSize = 11.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                    }

                    Text(
                        text = activeDetail.detailText,
                        fontSize = 12.5.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        lineHeight = 18.sp,
                    )

                    SettingDivider()

                    Text(
                        text = "🎯 适用场景：${activeDetail.suitableScenarios}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = "💰 资费成本：${activeDetail.costDesc}",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 16.sp,
                    )

                    SettingDivider()

                    // 配置项
                    when (currentEngine) {
                        AsrEngineType.ALIYUN_FUN_ASR -> {
                            OutlinedTextField(
                                value = aliyunAsrKey,
                                onValueChange = onUpdateAliyunAsrKey,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("阿里云 DashScope API Key") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(
                                    text = if (revealKey) "隐藏 API Key" else "显示 API Key",
                                    onClick = { revealKey = !revealKey },
                                )
                                TextButton(
                                    text = "去阿里云 DashScope 控制台 ↗",
                                    onClick = { openUrl(context, ALIYUN_API_KEY_URL) },
                                )
                            }
                            AliyunModelDropdown(
                                selectedId = aliyunAsrModel.text,
                                onSelect = { onUpdateAliyunAsrModel(TextFieldValue(it)) },
                            )
                            OutlinedTextField(
                                value = aliyunWorkspaceId,
                                onValueChange = onUpdateAliyunWorkspaceId,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("业务空间 ID (可选)") },
                                placeholder = { androidx.compose.material3.Text("非主账号空间时填写") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                        }
                        AsrEngineType.GENERIC_OPENAI -> {
                            OutlinedTextField(
                                value = genericAsrBaseUrl,
                                onValueChange = onUpdateGenericAsrBaseUrl,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("转录 Base URL") },
                                placeholder = { androidx.compose.material3.Text("https://api.openai.com/v1") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            OutlinedTextField(
                                value = genericAsrApiKey,
                                onValueChange = onUpdateGenericAsrApiKey,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("API Key (可选)") },
                                singleLine = true,
                                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                            OutlinedTextField(
                                value = genericAsrModel,
                                onValueChange = onUpdateGenericAsrModel,
                                modifier = Modifier.fillMaxWidth(),
                                label = { androidx.compose.material3.Text("转录模型名") },
                                placeholder = { androidx.compose.material3.Text("whisper-1") },
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = fieldColors,
                            )
                        }
                        else -> Unit
                    }

                    // VAD 静音切句滑块
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "分段停顿门槛（静音切句时长）",
                            fontSize = 12.5.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "%.1f 秒".format(settings.vadMinSilenceSec),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                    Slider(
                        value = settings.vadMinSilenceSec.coerceIn(0.1f, 1.0f),
                        onValueChange = { v ->
                            onUpdateSettings { it.copy(vadMinSilenceSec = (v * 10).roundToInt() / 10f) }
                        },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onSaveAndTest,
                            enabled = !asrTesting,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = if (asrTesting) "正在测试连接…" else "保存并测试连接",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        TextButton(
                            text = "仅保存",
                            onClick = onSaveOnly,
                        )
                    }

                    if (asrTesting) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }

                    asrTestResult?.let { resultText ->
                        val hasOk = resultText.contains("✅")
                        val hasFail = resultText.contains("❌")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        hasOk && !hasFail -> Booth.Accent.copy(alpha = 0.10f)
                                        hasFail -> Color(0xFFD32F2F).copy(alpha = 0.10f)
                                        else -> MiuixTheme.colorScheme.surfaceVariant
                                    }
                                )
                                .padding(10.dp)
                        ) {
                            Text(
                                text = resultText,
                                fontSize = 13.sp,
                                color = when {
                                    hasOk && !hasFail -> Booth.Accent
                                    hasFail -> Color(0xFFD32F2F)
                                    else -> MiuixTheme.colorScheme.onSurface
                                },
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // 申请指南
            SmallTitle(
                text = "申请指南",
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SectionCard {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showGuide = !showGuide }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_asr_guide_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "阿里百炼云端流式全双工 · 每月免费10小时",
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Icon(
                            imageVector = if (showGuide) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }

                    if (showGuide) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(0.5.dp)
                                .background(MiuixTheme.colorScheme.outline.copy(alpha = 0.2f))
                        )
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.settings_asr_guide_aliyun_title),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = Booth.Accent,
                                )
                                Text(
                                    text = stringResource(R.string.settings_asr_guide_aliyun_desc),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                TextButton(
                                    text = "去阿里云 DashScope 控制台 ↗",
                                    onClick = { openUrl(context, ALIYUN_API_KEY_URL) },
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }
}

/**
 * 打赏弹窗：四档固定金额的微信收款码，外加一档自定义金额。
 * 微信收款码不支持 URL 带金额，所以每一档都是一张独立生成的码。
 */
@Composable
private fun RewardDialog(onDismiss: () -> Unit) {
    data class Tier(val label: String, val res: Int)

    val tiers = listOf(
        Tier("0.5 元", R.drawable.img_reward_05),
        Tier("1 元", R.drawable.img_reward_10),
        Tier("6.6 元", R.drawable.img_reward_66),
        Tier("9.9 元", R.drawable.img_reward_99),
        Tier("自定义", R.drawable.img_reward_qrcode),
    )
    var selected by remember { mutableIntStateOf(0) }
    val isCustom = selected == tiers.lastIndex

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
            cornerRadius = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_reward_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_reward_dialog_desc),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    tiers.forEachIndexed { index, tier ->
                        val isSel = selected == index
                        val animatedBg by animateColorAsState(
                            targetValue = if (isSel) Booth.Accent.copy(alpha = 0.15f) else MiuixTheme.colorScheme.surfaceVariant,
                            animationSpec = tween(200),
                            label = "rewardTierBg",
                        )
                        val animatedBorder by animateColorAsState(
                            targetValue = if (isSel) Booth.Accent.copy(alpha = 0.45f) else Color.Transparent,
                            animationSpec = tween(200),
                            label = "rewardTierBorder",
                        )
                        val animatedTextColor by animateColorAsState(
                            targetValue = if (isSel) Booth.Accent else MiuixTheme.colorScheme.onSurface,
                            animationSpec = tween(200),
                            label = "rewardTierText",
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(animatedBg)
                                .border(0.8.dp, animatedBorder, RoundedCornerShape(10.dp))
                                .clickable { selected = index }
                                .padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = tier.label,
                                fontSize = 13.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = animatedTextColor,
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Crossfade(
                        targetState = tiers[selected].res,
                        animationSpec = tween(200),
                        label = "RewardQrcodeCrossfade",
                    ) { resId ->
                        Image(
                            painter = painterResource(resId),
                            contentDescription = "WeChat Reward QR Code ${tiers[selected].label}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(3f / 4.2f),
                        )
                    }
                }

                Text(
                    text = if (isCustom) "扫码后自己输入金额" else "扫码支付 ${tiers[selected].label}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Booth.Accent,
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = "关闭",
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * 软件使用说明与快速上手弹窗。
 */
@Composable
private fun UserGuideDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = CardDefaults.defaultColors(
                color = MiuixTheme.colorScheme.surfaceContainer,
                contentColor = MiuixTheme.colorScheme.onSurfaceContainer,
            ),
            cornerRadius = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_user_guide_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    GuideItem(
                        icon = "🎧",
                        title = "声音同传与离线听写",
                        desc = "• 声音来源：媒体音（内录其他 App 声音，戴耳机或静音仍有效）/ 麦克风 / 混合。\n• 离线听写：开启「仅识别（不翻译）」可纯本地生成字幕，零网络、零 Token、零延迟。\n• 字幕外观：首页折叠抽屉可实时调字号、透明度与 14 种双语颜色。"
                    )
                    GuideItem(
                        icon = "🪟",
                        title = "悬浮窗高级手势与穿透锁定",
                        desc = "• 拖动与缩放：按住顶部横条移动位置，拖右下角小把手自由拉伸。\n• 贴边半球：拖到屏幕左右边缘自动折叠为半球，拉出带有 60fps 平滑圆球展开动效，支持沿屏幕边缘上下滑。\n• 快捷操作栏：点击顶部把手展开快捷栏，支持一键复制译文、切换双语/单语模式、开启防误触穿透锁定（长按边缘微型胶囊解锁）。"
                    )
                    GuideItem(
                        icon = "🔍",
                        title = "屏幕取词（智能避让与手势）",
                        desc = "• 选区微调：直接拖选区右下角圆点为缩放，长按 500ms 拖动为平移整框。\n• 悬浮球控制：点球暂停/继续；长按 500ms 弹出菜单（可一键隐藏/恢复红色取词框、重选区域或停止）。\n• 智能避让：悬浮球与译文气泡会自动躲避取词选区，防止遮挡内容引发重复识别。"
                    )
                    GuideItem(
                        icon = "🧰",
                        title = "离线多功能工具箱（首页托盘）",
                        desc = "• 📄 文档翻译：支持 PDF（双语交替/仅译文）、Word、Excel、PPT、TXT、Markdown 六大格式，排版原位保留并导出。\n• 🎬 视频翻译：提取本地视频音轨生成时间戳字幕，支持画质自适应预览、导出 SRT 与原画画质压制烧录 MP4。\n• 📷 相机/相册图文：现场拍照或导入图片覆盖翻译，文本支持长按自由划选复制。\n• 📝 快速文本：双向互译、一键粘贴与字数统计。"
                    )
                    GuideItem(
                        icon = "🌐",
                        title = "翻译领域风格（让大模型更懂情境）",
                        desc = "在大模型翻译引擎下，首页大按钮下方提供横向胶囊切换：通用日常 / 动漫游戏 / 商务职场 / 学术科技 / 影视综艺。针对不同场景自动适配地道口语、游戏黑话或专业术语，下一句即时生效。"
                    )
                    GuideItem(
                        icon = "⚡",
                        title = "语音识别引擎解析",
                        desc = "• 本地 SenseVoice：内置端侧运行，支持中英日韩粤，零流量不耗电，支持「边说边出」草稿字幕。\n• 阿里百炼 FunASR：云端极速流式 WebSocket 识别，高并发低延迟，官方 8 大实时模型预设。\n• 通用 OpenAI ASR：支持接入硅基流动、Groq 等任意云端 Whisper 服务。"
                    )
                    GuideItem(
                        icon = "💡",
                        title = "翻译大模型与省钱技巧",
                        desc = "• 免费引擎：微软与谷歌翻译免配置 Key，国内直连开箱即用；智谱 GLM 免费额度充足。\n• 推荐大模型：DeepSeek（deepseek-v4-flash 模型极速且实惠，每日 00:30 ~ 08:30 享受闲时 5 折优惠）。\n• 通用 OpenAI 兼容：支持接入中转站、Ollama 或自建模型。"
                    )
                    GuideItem(
                        icon = "📜",
                        title = "历史记录与导出管理",
                        desc = "每次声音同传或屏幕取词结束后整场聚合归档；支持页内即时关键词模糊搜索、展开双语对照、单条删除与一键导出保存为 .txt 文本文件。"
                    )
                    GuideItem(
                        icon = "⚙️",
                        title = "权限与后台保活建议",
                        desc = "• 必备权限：悬浮窗权限（显示字幕/气泡）+ 录屏权限（音频内录与屏幕截帧）。\n• DRM 版权保护限制：部分 App（如部分银行、受保护流媒体）系统禁止内录画面或声音，此情况请将声音来源切至「麦克风」。\n• 小米/华为等系统：建议在系统设置中为本应用开启「后台无限制」与「允许自启动」，防止锁屏或切后台时被系统杀进程。"
                    )
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(
                        text = stringResource(R.string.settings_user_guide_close),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideItem(
    icon: String,
    title: String,
    desc: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = icon, fontSize = 16.sp)
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = desc,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun OpenSourceModelHubCard(
    onFillPreset: (baseUrl: String, model: String) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(false) }

    SectionCard {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingRowIcon(
                        emoji = "🚀",
                        bg = Color(0xFF10B981).copy(alpha = 0.12f),
                    )
                    Column {
                        Text(
                            text = "开源模型国内下载与部署助手",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "支持 Ollama / LM Studio · 提供魔搭国内免翻墙镜像",
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.rotate(if (expanded) 180f else 0f),
                )
            }

            if (expanded) {
                SettingDivider()

                Text(
                    text = "一键预填常见服务端点：",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onFillPreset("http://192.168.1.100:11434/v1", "qwen2.5:7b") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("Ollama", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onFillPreset("http://192.168.1.100:1234/v1", "qwen2.5-7b-instruct") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("LM Studio", fontSize = 12.sp)
                    }
                    Button(
                        onClick = { onFillPreset("https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct") },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("硅基流动", fontSize = 12.sp)
                    }
                }

                SettingDivider()

                Text(
                    text = "推荐翻译开源模型与国内高速镜像下载：",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )

                // Model 1: Qwen2.5-7B
                OpenSourceModelRow(
                    name = "Qwen2.5-7B-Instruct (通义千问)",
                    desc = "公认中文与综合翻译最强开源模型，8G显存推荐",
                    ollamaCmd = "ollama run qwen2.5:7b",
                    modelScopeUrl = "https://www.modelscope.cn/models/qwen/Qwen2.5-7B-Instruct/summary",
                    hfMirrorUrl = "https://hf-mirror.com/Qwen/Qwen2.5-7B-Instruct-GGUF",
                    onCopy = { cmd ->
                        clipboard.setText(AnnotatedString(cmd))
                        Toast.makeText(context, "已复制拉取命令: $cmd", Toast.LENGTH_SHORT).show()
                    },
                    onOpenUrl = { url -> openUrl(context, url) },
                )

                // Model 2: SakuraLLM (樱花日中)
                OpenSourceModelRow(
                    name = "🌸 SakuraLLM 14B/7B (日漫汉化霸主)",
                    desc = "ACG轻小说/动漫/游戏生肉绝对首选，精准还原人称代词与口吻",
                    ollamaCmd = "ollama run sakura:14b",
                    modelScopeUrl = "https://www.modelscope.cn/models/sakura-editor/Sakura-14B-Qwen2beta-v0.9.2-GGUF",
                    hfMirrorUrl = "https://hf-mirror.com/sakura-editor/Sakura-14B-Qwen2beta-v0.9.2-GGUF",
                    onCopy = { cmd ->
                        clipboard.setText(AnnotatedString(cmd))
                        Toast.makeText(context, "已复制拉取命令: $cmd", Toast.LENGTH_SHORT).show()
                    },
                    onOpenUrl = { url -> openUrl(context, url) },
                )

                // Model 3: Seed-X-7B (字节跳动)
                OpenSourceModelRow(
                    name = "🔥 Seed-X-7B (字节跳动精翻)",
                    desc = "成语/诗歌/俚语网络热词神级对齐，7B参数超越百亿通用大模型",
                    ollamaCmd = "ollama run seed-x:7b",
                    modelScopeUrl = "https://www.modelscope.cn/models/ByteDance/Seed-X-7B",
                    hfMirrorUrl = "https://hf-mirror.com/ByteDance/Seed-X-7B",
                    onCopy = { cmd ->
                        clipboard.setText(AnnotatedString(cmd))
                        Toast.makeText(context, "已复制拉取命令: $cmd", Toast.LENGTH_SHORT).show()
                    },
                    onOpenUrl = { url -> openUrl(context, url) },
                )

                // Model 4: DeepSeek-R1-Distill-Qwen-7B
                OpenSourceModelRow(
                    name = "DeepSeek-R1-Distill-Qwen-7B",
                    desc = "深度求索蒸馏版，推理与句式理解极佳",
                    ollamaCmd = "ollama run deepseek-r1:7b",
                    modelScopeUrl = "https://www.modelscope.cn/models/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B/summary",
                    hfMirrorUrl = "https://hf-mirror.com/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B",
                    onCopy = { cmd ->
                        clipboard.setText(AnnotatedString(cmd))
                        Toast.makeText(context, "已复制拉取命令: $cmd", Toast.LENGTH_SHORT).show()
                    },
                    onOpenUrl = { url -> openUrl(context, url) },
                )

                SettingDivider()

                // Phone connection guide
                Text(
                    text = "💡 手机连接电脑本地模型指南：\n" +
                        "1. 电脑端配置：设置环境变量 OLLAMA_HOST=0.0.0.0:11434 并重启 Ollama（允许局域网设备连接）。\n" +
                        "2. 电脑查看局域网 IP：终端输入 ipconfig 查看 IPv4 地址（如 192.168.1.100）。\n" +
                        "3. 手机端连接：确保手机与电脑处于同一 Wi-Fi，Base URL 填写 http://<电脑IP>:11434/v1 即可免 Key 直接畅享本地模型！",
                    fontSize = 11.5.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

@Composable
private fun OpenSourceModelRow(
    name: String,
    desc: String,
    ollamaCmd: String,
    modelScopeUrl: String,
    hfMirrorUrl: String,
    onCopy: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = name,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
        )
        Text(
            text = desc,
            fontSize = 11.5.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Button(
                onClick = { onCopy(ollamaCmd) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("复制命令", fontSize = 11.sp)
            }
            Button(
                onClick = { onOpenUrl(modelScopeUrl) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("魔搭国内下载", fontSize = 11.sp)
            }
            Button(
                onClick = { onOpenUrl(hfMirrorUrl) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Text("HF镜像", fontSize = 11.sp)
            }
        }
    }
}
