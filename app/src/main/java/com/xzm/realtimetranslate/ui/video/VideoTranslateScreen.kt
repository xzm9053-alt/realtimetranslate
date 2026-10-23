package com.xzm.realtimetranslate.ui.video

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.xzm.realtimetranslate.ui.components.LanguageChip
import com.xzm.realtimetranslate.ui.components.LanguagePickerSheet
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.theme.Booth
import com.xzm.realtimetranslate.video.VideoCue
import com.xzm.realtimetranslate.video.VideoTranslationPipeline
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LangField { Source, Target }

/** SenseVoice recognizes these; anything else would be silently mistranscribed. */
private val VIDEO_SOURCE_CODES = listOf("auto", "zh-Hans", "zh-Hant", "en", "ja", "ko")

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

    val sourceOptions = remember {
        val known = SupportedLanguages.sourceOptions.associateBy { it.code }
        VIDEO_SOURCE_CODES.mapNotNull { known[it] }
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
        item {
            PageTitle(
                title = stringResource(R.string.more_video_title),
                subtitle = stringResource(R.string.more_video_summary),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(text = stringResource(R.string.common_back), onClick = onBack)
                if (state.durationMs > 0L) {
                    Text(
                        text = formatDuration(state.durationMs),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        item {
            SectionCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.displayName.ifBlank {
                                stringResource(R.string.video_empty_title)
                            },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                        )
                        if (state.displayName.isBlank()) {
                            Text(
                                text = stringResource(R.string.video_empty_hint),
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                    TextButton(
                        text = stringResource(
                            if (state.hasVideo) R.string.video_change else R.string.video_pick,
                        ),
                        onClick = {
                            if (!state.isRunning) pickVideo.launch(arrayOf("video/*"))
                        },
                        enabled = !state.isRunning,
                    )
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
                            value = stringResource(targetLabel.labelRes),
                            modifier = Modifier.weight(1f),
                            onClick = { if (!state.isRunning) picker = LangField.Target },
                        )
                    }
                    Text(
                        text = stringResource(R.string.video_local_asr_hint),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { if (state.isRunning) viewModel.cancel() else viewModel.start() },
                        enabled = state.isRunning || state.canStart,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = if (state.isRunning) {
                            ButtonDefaults.buttonColors()
                        } else {
                            ButtonDefaults.buttonColorsPrimary()
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (state.isRunning) R.string.video_stop else R.string.video_start,
                            ),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                    TextButton(
                        text = stringResource(R.string.video_export_srt),
                        onClick = viewModel::exportSrt,
                        enabled = state.canExport,
                    )
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

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player },
        )
        if (player != null) {
            SubtitleOverlay(
                player = player,
                cues = cues,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 52.dp),
            )
        }
    }
}

@Composable
private fun SubtitleOverlay(
    player: Player,
    cues: List<VideoCue>,
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

    Text(
        text = cue.translation.ifBlank { cue.source },
        color = Color.White,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
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

private fun queryDisplayName(context: Context, uri: Uri): String {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
}
