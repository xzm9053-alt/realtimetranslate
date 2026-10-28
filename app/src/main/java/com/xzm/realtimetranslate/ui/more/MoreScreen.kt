package com.xzm.realtimetranslate.ui.more

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.service.StaticModeTarget
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.document.DocumentTranslateScreen
import com.xzm.realtimetranslate.ui.document.DocumentTranslateViewModel
import com.xzm.realtimetranslate.ui.document.DocumentTranslateViewModelFactory
import com.xzm.realtimetranslate.ui.image.ImageSource
import com.xzm.realtimetranslate.ui.image.ImageTranslateScreen
import com.xzm.realtimetranslate.ui.image.ImageTranslateViewModel
import com.xzm.realtimetranslate.ui.image.ImageTranslateViewModelFactory
import com.xzm.realtimetranslate.ui.text.TextTranslateScreen
import com.xzm.realtimetranslate.ui.text.TextTranslateViewModel
import com.xzm.realtimetranslate.ui.text.TextTranslateViewModelFactory
import com.xzm.realtimetranslate.ui.video.VideoTranslateScreen
import com.xzm.realtimetranslate.ui.video.VideoTranslateViewModel
import com.xzm.realtimetranslate.ui.video.VideoTranslateViewModelFactory
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private const val PAGE_HUB = 0
private const val PAGE_TEXT = 1
private const val PAGE_VIDEO = 2
private const val PAGE_IMAGE = 3
private const val PAGE_DOCUMENT = 4

/**
 * "其他翻译"入口下的二级页面：一个 Hub，加文本 / 视频 / 图片三个子页。
 *
 * 沿用项目现有的手写导航（rememberSaveable + BackHandler），不引入 Navigation-Compose。
 *
 * [initialStaticTarget] 来自悬浮球菜单（相机 / 图片）：一进来就落在对应子页，
 * 并带着那个来源预选（拍照 or 相册）。消费后回调 [onInitialTargetConsumed]，
 * 否则每次重组都会被拉回同一页。
 */
@Composable
fun MoreScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialStaticTarget: StaticModeTarget? = null,
    onInitialTargetConsumed: () -> Unit = {},
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_HUB) }
    val app = LocalContext.current.applicationContext as LiveTranslateApp

    val textVm: TextTranslateViewModel = viewModel(
        factory = TextTranslateViewModelFactory(
            app,
            app.settingsRepository,
            app.apiKeyStore,
            app.historyRepository,
        ),
    )
    val videoVm: VideoTranslateViewModel = viewModel(
        factory = VideoTranslateViewModelFactory(
            app,
            app.settingsRepository,
            app.apiKeyStore,
            app.modelManager,
        ),
    )
    val imageVm: ImageTranslateViewModel = viewModel(
        factory = ImageTranslateViewModelFactory(
            app,
            app.settingsRepository,
            app.apiKeyStore,
            app.historyRepository,
        ),
    )
    val documentVm: DocumentTranslateViewModel = viewModel(
        factory = DocumentTranslateViewModelFactory(
            app,
            app.settingsRepository,
            app.apiKeyStore,
            app.historyRepository,
        ),
    )

    // Must come after imageVm: the ball's request carries which picker to preselect.
    LaunchedEffect(initialStaticTarget) {
        when (initialStaticTarget) {
            StaticModeTarget.CAMERA -> {
                imageVm.setSource(ImageSource.CAMERA)
                page = PAGE_IMAGE
            }
            StaticModeTarget.IMAGE -> {
                imageVm.setSource(ImageSource.GALLERY)
                page = PAGE_IMAGE
            }
            StaticModeTarget.DOCUMENT -> {
                page = PAGE_DOCUMENT
            }
            StaticModeTarget.VIDEO -> {
                page = PAGE_VIDEO
            }
            StaticModeTarget.TEXT -> {
                page = PAGE_TEXT
            }
            null -> Unit
        }
        if (initialStaticTarget != null) onInitialTargetConsumed()
    }

    when (page) {
        PAGE_TEXT -> TextTranslateScreen(
            viewModel = textVm,
            onBack = { page = PAGE_HUB },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        PAGE_VIDEO -> VideoTranslateScreen(
            viewModel = videoVm,
            onBack = { page = PAGE_HUB },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        PAGE_IMAGE -> ImageTranslateScreen(
            viewModel = imageVm,
            onBack = { page = PAGE_HUB },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        PAGE_DOCUMENT -> DocumentTranslateScreen(
            viewModel = documentVm,
            onBack = { page = PAGE_HUB },
            onOpenSettings = onOpenSettings,
            modifier = modifier,
        )
        else -> MoreHub(
            onBack = onBack,
            onOpenDocument = { page = PAGE_DOCUMENT },
            onOpenText = { page = PAGE_TEXT },
            onOpenVideo = { page = PAGE_VIDEO },
            onOpenCamera = {
                imageVm.setSource(ImageSource.CAMERA)
                page = PAGE_IMAGE
            },
            onOpenImage = {
                imageVm.setSource(ImageSource.GALLERY)
                page = PAGE_IMAGE
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun MoreHub(
    onBack: () -> Unit,
    onOpenDocument: () -> Unit,
    onOpenText: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.more_title),
            subtitle = stringResource(R.string.more_subtitle),
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            TextButton(text = stringResource(R.string.common_back), onClick = onBack)
        }

        HubEntry(
            emoji = "📄",
            title = stringResource(R.string.more_document_title),
            summary = stringResource(R.string.more_document_summary),
            onClick = onOpenDocument,
        )

        HubEntry(
            emoji = "📷",
            title = stringResource(R.string.more_camera_title),
            summary = stringResource(R.string.more_camera_summary),
            onClick = onOpenCamera,
        )
        HubEntry(
            emoji = "🖼️",
            title = stringResource(R.string.more_image_title),
            summary = stringResource(R.string.more_image_summary),
            onClick = onOpenImage,
        )
        HubEntry(
            emoji = "🎬",
            title = stringResource(R.string.more_video_title),
            summary = stringResource(R.string.more_video_summary),
            onClick = onOpenVideo,
        )
        HubEntry(
            emoji = "📝",
            title = stringResource(R.string.more_text_title),
            summary = stringResource(R.string.more_text_summary),
            onClick = onOpenText,
        )
    }
}

@Composable
private fun HubEntry(
    emoji: String,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 26.sp)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(text = title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
            Text(text = "›", fontSize = 26.sp, color = scheme.onSurfaceVariantSummary)
            Spacer(modifier = Modifier.width(2.dp))
        }
    }
}
