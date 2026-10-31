package com.xzm.realtimetranslate.ui.video

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.LanguageOption
import com.xzm.realtimetranslate.data.SupportedLanguages
import com.xzm.realtimetranslate.data.VideoAsrEngine
import com.xzm.realtimetranslate.data.VideoSubtitleMode
import com.xzm.realtimetranslate.ui.components.LanguageChip
import com.xzm.realtimetranslate.ui.components.LanguagePickerSheet
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.components.SettingSwitchRow
import com.xzm.realtimetranslate.ui.theme.Booth
import com.xzm.realtimetranslate.video.VideoCue
import com.xzm.realtimetranslate.video.VideoTranslationPipeline
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LangField { Source, Target }

/** SenseVoice 只认这五种；切云端后没有这个限制。 */
private val LOCAL_SOURCE_CODES = VideoAsrEngine.LOCAL_SOURCE_CODES

@Composable
fun VideoTranslateScreen(
    viewModel: VideoTranslateViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme

    var picker by remember { mutableStateOf<LangField?>(null) }

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.setVideo(uri, queryDisplayName(context, uri))
    }

    val sourceOptions = remember(state.asrEngine) {
        val known = SupportedLanguages.sourceOptions.associateBy { it.code }
        // 本地 SenseVoice 只认五种，云端 Whisper 支持 99 种，所以下拉长度跟引擎走。
        val codes = if (state.asrEngine == VideoAsrEngine.LOCAL) {
            LOCAL_SOURCE_CODES
        } else {
            SupportedLanguages.sourceOptions.map { it.code }
        }
        codes.mapNotNull { known[it] }
    }
    val targetOptions: List<LanguageOption> = SupportedLanguages.targetOptions
    val sourceLabel = sourceOptions.firstOrNull { it.code == state.sourceCode }
        ?: sourceOptions.first()
    val targetLabel = targetOptions.firstOrNull { it.code == state.targetCode }
        ?: targetOptions.first()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ================= 顶部一体化导航栏 =================
        item {
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
                            text = stringResource(R.string.more_video_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.more_video_summary),
                            fontSize = 11.5.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }

                if (state.durationMs > 0L) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.surfaceVariant)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "⏱️ ${formatDuration(state.durationMs)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        // ================= 特性徽章胶囊 =================
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Booth.Accent.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "🎬 视频字幕提取与翻译",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Booth.Accent,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        text = "⚡ 端侧 SenseVoice · 导出 SRT",
                        fontSize = 11.5.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // ================= 视频文件选择与展示 =================
        if (!state.hasVideo) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable {
                            if (!state.isRunning && !state.isBurning) {
                                pickVideo.launch(arrayOf("video/*"))
                            }
                        },
                    colors = CardDefaults.defaultColors(
                        color = scheme.surfaceContainer,
                        contentColor = scheme.onSurfaceContainer,
                    ),
                    cornerRadius = 16.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Booth.Accent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "🎬", fontSize = 28.sp)
                        }
                        Text(
                            text = "点击选择本地视频文件",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "支持 MP4 / MKV / MOV 等主流视频格式",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        } else {
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Booth.Accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(text = "🎬", fontSize = 20.sp)
                            }
                            Column {
                                Text(
                                    text = state.displayName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    color = scheme.onSurface,
                                )
                                if (state.durationMs > 0L) {
                                    Text(
                                        text = "时长：${formatDuration(state.durationMs)}",
                                        fontSize = 12.sp,
                                        color = scheme.onSurfaceVariantSummary,
                                    )
                                }
                            }
                        }
                        if (!state.isRunning && !state.isBurning) {
                            TextButton(
                                text = "更换视频",
                                onClick = { pickVideo.launch(arrayOf("video/*")) },
                            )
                        }
                    }
                }
            }
        }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LanguageChip(
                            label = stringResource(R.string.text_source_lang),
                            value = stringResource(sourceLabel.labelRes),
                            modifier = Modifier.weight(1f),
                            onClick = { if (!state.isRunning) picker = LangField.Source },
                        )
                        LanguageChip(
                            label = stringResource(R.string.text_target_lang),
                            value = if (state.recognizeOnly) "（不翻译 · 仅听写）" else stringResource(targetLabel.labelRes),
                            modifier = Modifier.weight(1f),
                            onClick = { if (!state.isRunning && !state.recognizeOnly) picker = LangField.Target },
                        )
                    }
                    SettingDivider()
                    SettingSwitchRow(
                        title = "仅识别（不翻译）",
                        summary = "直接提取视频语音生成字幕，不调用 AI 翻译，完全离线免 API Key",
                        checked = state.recognizeOnly,
                        onCheckedChange = viewModel::setRecognizeOnly,
                        enabled = !state.isRunning,
                    )
                    SettingDivider()
                    Text(
                        text = "语音识别引擎",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ModeChip(
                            label = "📱 本地 SenseVoice",
                            selected = state.asrEngine == VideoAsrEngine.LOCAL,
                            onClick = { if (!state.isRunning) viewModel.setAsrEngine(VideoAsrEngine.LOCAL) },
                        )
                        ModeChip(
                            label = "☁️ 云端 Whisper",
                            selected = state.asrEngine == VideoAsrEngine.CLOUD,
                            onClick = { if (!state.isRunning) viewModel.setAsrEngine(VideoAsrEngine.CLOUD) },
                        )
                    }
                    Text(
                        text = if (state.asrEngine == VideoAsrEngine.LOCAL) {
                            stringResource(R.string.video_local_asr_hint)
                        } else {
                            stringResource(R.string.video_cloud_asr_hint)
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        if (state.hasVideo) {
            item {
                VideoPlayerBox(
                    uri = state.uri!!,
                    cues = state.cues,
                    mode = state.subtitleMode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        if (state.cues.isNotEmpty()) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Text(
                        text = stringResource(R.string.video_subtitle_mode),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        VideoSubtitleMode.entries.forEach { mode ->
                            ModeChip(
                                label = stringResource(videoSubtitleModeLabel(mode)),
                                selected = state.subtitleMode == mode,
                                onClick = { viewModel.setSubtitleMode(mode) },
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.video_subtitle_mode_hint),
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.isBurning) {
                    ProgressBar(progress = state.burnProgress)
                    Text(
                        text = stringResource(
                            R.string.video_burn_progress,
                            (state.burnProgress * 100).toInt().coerceAtLeast(0),
                        ),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }

                if (state.isRunning) {
                    ProgressBar(progress = state.progress)
                    Text(
                        text = when (state.stage) {
                            VideoTranslationPipeline.Stage.RECOGNIZING -> stringResource(
                                R.string.video_stage_recognize,
                                state.segmentsDone,
                            )
                            else -> stringResource(R.string.video_stage_extract)
                        },
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }

                Button(
                    onClick = { if (state.isRunning) viewModel.cancel() else viewModel.start() },
                    enabled = state.isRunning || state.canStart,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    colors = if (state.isRunning) {
                        ButtonDefaults.buttonColors()
                    } else {
                        ButtonDefaults.buttonColorsPrimary()
                    },
                ) {
                    Text(
                        text = if (state.isRunning) {
                            stringResource(R.string.video_stop)
                        } else if (state.recognizeOnly) {
                            "▶ 开始提取字幕（仅识别）"
                        } else {
                            stringResource(R.string.video_start)
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }

        if (state.canExport || state.canBurn) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = "🎉", fontSize = 18.sp)
                            Text(
                                text = "字幕提取与翻译就绪",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(
                                onClick = viewModel::exportSrt,
                                enabled = state.canExport,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(
                                    text = "💾 导出 SRT",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                )
                            }
                            Button(
                                onClick = viewModel::burnSubtitles,
                                enabled = state.canBurn,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text(
                                    text = "🎬 压制视频",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }
        }

        state.error?.let { message ->
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Booth.Danger,
                    )
                    TextButton(
                        text = stringResource(R.string.text_open_settings),
                        onClick = onOpenSettings,
                    )
                }
            }
        }

        state.message?.let { message ->
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = Booth.Accent,
                    )
                    if (state.failedTranslations > 0) {
                        Text(
                            text = stringResource(
                                R.string.video_failed_count,
                                state.failedTranslations,
                            ),
                            fontSize = 12.sp,
                            color = Booth.Danger,
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = stringResource(R.string.video_cues_title),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (state.cues.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.video_cues_empty),
                    fontSize = 13.sp,
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        } else {
            items(state.cues, key = { it.index }) { cue ->
                CueRow(cue = cue)
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
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

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerBox(
    uri: String,
    cues: List<VideoCue>,
    mode: VideoSubtitleMode,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val player = remember(uri) {
        runCatching { Uri.parse(uri) }.getOrNull()?.let { parsed ->
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(parsed))
                prepare()
            }
        }
    }

    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    DisposableEffect(lifecycleOwner, player) {
        if (player == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The frame follows the video's own aspect ratio instead of a fixed 16:9 box:
    // vertical phone footage would otherwise be pillarboxed into a thin strip.
    var videoAspect by remember(uri) { mutableStateOf(16f / 9f) }
    var fullscreen by remember(uri) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(videoAspect)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        // Exactly one PlayerView exists at a time: the player has a single output surface,
        // and a second view would steal it. The box keeps its measured aspect ratio while
        // empty, so entering and leaving fullscreen does not shift the page.
        if (!fullscreen) AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setAspectRatioListener { _, _, _ -> }
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                view.player = player
                val size = player?.videoSize
                if (size != null && size.width > 0 && size.height > 0) {
                    val ratio = size.width * (size.pixelWidthHeightRatio.takeIf { it > 0f } ?: 1f) / size.height
                    if (ratio > 0f && kotlin.math.abs(ratio - videoAspect) > 0.01f) {
                        videoAspect = ratio
                    }
                }
            },
        )
        if (player != null) {
            SubtitleOverlay(
                player = player,
                cues = cues,
                mode = mode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 52.dp),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { fullscreen = true }
                    .padding(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Fullscreen,
                    contentDescription = stringResource(R.string.video_fullscreen),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }

    if (fullscreen && player != null) {
        FullscreenVideoDialog(
            player = player,
            cues = cues,
            mode = mode,
            onDismiss = { fullscreen = false },
        )
    }
}

/**
 * Full-screen playback with pinch zoom, because a landscape clip is a thin strip inside
 * the scrolling page and small text in the picture is unreadable there.
 *
 * The player view runs without its built-in controller: those controllers consume the
 * touches that the zoom/pan gesture needs, and two gesture owners on one surface is a
 * fight neither wins. Play/pause and seeking get their own row instead.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun FullscreenVideoDialog(
    player: Player,
    cues: List<VideoCue>,
    mode: VideoSubtitleMode,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var viewSize by remember { mutableStateOf(IntSize.Zero) }
        var playing by remember { mutableStateOf(player.isPlaying) }
        var positionMs by remember { mutableLongStateOf(0L) }
        var durationMs by remember { mutableLongStateOf(0L) }
        // While the user is dragging the thumb, the poll below must not overwrite the
        // value they are setting — otherwise the slider snaps back under their finger.
        var seeking by remember { mutableStateOf(false) }

        LaunchedEffect(player) {
            while (true) {
                playing = player.isPlaying
                if (!seeking) {
                    positionMs = runCatching { player.currentPosition }.getOrDefault(0L)
                }
                durationMs = runCatching { player.duration }.getOrDefault(0L).coerceAtLeast(0L)
                delay(250L)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .onSizeChanged { viewSize = it }
                .pointerInput(Unit) {
                    detectTransformGestures { _, gesturePan, gestureZoom, _ ->
                        val next = (zoom * gestureZoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        zoom = next
                        // The picture scales about its centre, so at 2x there is half a
                        // screen of overhang in each direction to pan through — no more.
                        val maxX = viewSize.width * (next - 1f) / 2f
                        val maxY = viewSize.height * (next - 1f) / 2f
                        pan = if (next <= 1.01f) {
                            Offset.Zero
                        } else {
                            Offset(
                                (pan.x + gesturePan.x).coerceIn(-maxX, maxX),
                                (pan.y + gesturePan.y).coerceIn(-maxY, maxY),
                            )
                        }
                    }
                },
        ) {
            AndroidView(
                // Inflated from XML so the surface is a TextureView. The default
                // SurfaceView is composited in its own layer and ignores the scale and
                // translation set below — the pinch zoom would do nothing.
                factory = { ctx ->
                    LayoutInflater.from(ctx)
                        .inflate(R.layout.view_fullscreen_player, null) as PlayerView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
                update = { it.player = player },
            )

            SubtitleOverlay(
                player = player,
                cues = cues,
                mode = mode,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    // Clears the control row below it, which is itself inset by the
                    // navigation bar; a fixed offset would collide on gesture-nav phones.
                    .padding(bottom = 64.dp),
            )

            IconButton(
                onClick = onDismiss,
                // Edge-to-edge dialog: without the inset the button lands under the
                // status bar. The video stays full-bleed, only the controls are inset.
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.video_close),
                    tint = Color.White,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Icon(
                        imageVector = if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(
                            if (playing) R.string.video_pause else R.string.video_play,
                        ),
                        tint = Color.White,
                    )
                }
                androidx.compose.material3.Slider(
                    value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = {
                        seeking = true
                        positionMs = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(positionMs)
                        seeking = false
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    enabled = durationMs > 0L,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${formatClock(positionMs)} / ${formatClock(durationMs)}",
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private const val MIN_ZOOM = 1f
private const val MAX_ZOOM = 4f

private fun formatClock(millis: Long): String {
    val total = (millis / 1000L).coerceAtLeast(0L)
    val minutes = total / 60L
    val seconds = total % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun ModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) Booth.Accent.copy(alpha = 0.15f) else scheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Booth.Accent else scheme.onSurface,
        )
    }
}

private fun videoSubtitleModeLabel(mode: VideoSubtitleMode): Int = when (mode) {
    VideoSubtitleMode.TRANSLATION -> R.string.video_subtitle_translation
    VideoSubtitleMode.SOURCE -> R.string.video_subtitle_source
    VideoSubtitleMode.BOTH -> R.string.video_subtitle_both
}

@Composable
private fun SubtitleOverlay(
    player: Player,
    cues: List<VideoCue>,
    mode: VideoSubtitleMode,
    modifier: Modifier = Modifier,
) {
    var positionMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(player) {
        while (true) {
            positionMs = runCatching { player.currentPosition }.getOrDefault(0L)
            delay(100L)
        }
    }

    if (cues.isEmpty()) return
    val cue = cues.firstOrNull { positionMs >= it.startMs && positionMs < it.endMs } ?: return

    val source = cue.source.trim()
    val translation = cue.translation.trim()
    val primary = when (mode) {
        VideoSubtitleMode.SOURCE -> source.ifBlank { translation }
        else -> translation.ifBlank { source }
    }
    // The dim line only earns its place in BOTH mode, and only when it differs from the
    // primary line; otherwise the primary already is the source and both would read the same.
    val showSource = mode == VideoSubtitleMode.BOTH &&
        translation.isNotEmpty() && source != translation

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        if (showSource) {
            Text(
                text = source,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
        if (primary.isNotEmpty()) {
            Text(
                text = primary,
                color = Color.White,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ProgressBar(progress: Float) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(scheme.surfaceVariant),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Booth.Accent),
        )
    }
}

@Composable
private fun CueRow(cue: VideoCue) {
    val scheme = MiuixTheme.colorScheme
    SectionCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "${formatDuration(cue.startMs)} – ${formatDuration(cue.endMs)}",
                fontSize = 11.sp,
                color = scheme.onSurfaceVariantSummary,
            )
            Text(text = cue.translation, fontSize = 14.sp, lineHeight = 20.sp)
            if (cue.source.isNotBlank() && cue.source != cue.translation) {
                Text(
                    text = cue.source,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}

internal fun queryDisplayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
}

