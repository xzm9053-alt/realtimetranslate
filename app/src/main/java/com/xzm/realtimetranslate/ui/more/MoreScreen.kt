package com.xzm.realtimetranslate.ui.more

import android.net.Uri
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.service.StaticModeTarget
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.components.SettingRowIcon
import com.xzm.realtimetranslate.ui.theme.Booth
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
import com.xzm.realtimetranslate.ui.video.queryDisplayName
import com.xzm.realtimetranslate.ui.video.VideoTranslateViewModel
import com.xzm.realtimetranslate.ui.video.VideoTranslateViewModelFactory
import top.yukonga.miuix.kmp.basic.SmallTitle
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
    initialVideoUri: Uri? = null,
    onInitialTargetConsumed: () -> Unit = {},
) {
    var page by rememberSaveable { mutableIntStateOf(PAGE_HUB) }
    val app = LocalContext.current.applicationContext as LiveTranslateApp
    val context = LocalContext.current

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
                // 别的 App 递过来的视频：页面切过去的同时把文件导入，
                // 否则用户落在一个空页上，还得自己去相册里找刚才那个。
                // 不调 start()：识别要跑好几分钟，且可能因未配 Key / 模型未就绪失败，
                // 那些失败应当发生在用户刚刚主动点过的地方。
                initialVideoUri?.let { uri ->
                    videoVm.setVideo(uri, queryDisplayName(context, uri))
                }
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
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 顶部导航栏：返回按钮 + 大标题
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = stringResource(R.string.more_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.more_subtitle),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // ================= Group 1: 文档与音视频翻译 =================
        SmallTitle(
            text = stringResource(R.string.more_group_files),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column {
                HubRowItem(
                    emoji = "📄",
                    iconBg = Color(0xFFF59E0B).copy(alpha = 0.14f),
                    title = stringResource(R.string.more_document_title),
                    summary = stringResource(R.string.more_document_summary),
                    badge = stringResource(R.string.more_badge_doc),
                    onClick = onOpenDocument,
                )
                SettingDivider()
                HubRowItem(
                    emoji = "🎬",
                    iconBg = Color(0xFF8B5CF6).copy(alpha = 0.14f),
                    title = stringResource(R.string.more_video_title),
                    summary = stringResource(R.string.more_video_summary),
                    badge = stringResource(R.string.more_badge_video),
                    onClick = onOpenVideo,
                )
            }
        }

        // ================= Group 2: 图文与日常快译 =================
        SmallTitle(
            text = stringResource(R.string.more_group_quick),
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SectionCard {
            Column {
                HubRowItem(
                    emoji = "📷",
                    iconBg = Color(0xFF10B981).copy(alpha = 0.14f),
                    title = stringResource(R.string.more_camera_title),
                    summary = stringResource(R.string.more_camera_summary),
                    onClick = onOpenCamera,
                )
                SettingDivider()
                HubRowItem(
                    emoji = "🖼️",
                    iconBg = Color(0xFF06B6D4).copy(alpha = 0.14f),
                    title = stringResource(R.string.more_image_title),
                    summary = stringResource(R.string.more_image_summary),
                    onClick = onOpenImage,
                )
                SettingDivider()
                HubRowItem(
                    emoji = "📝",
                    iconBg = Booth.Accent.copy(alpha = 0.14f),
                    title = stringResource(R.string.more_text_title),
                    summary = stringResource(R.string.more_text_summary),
                    onClick = onOpenText,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HubRowItem(
    emoji: String,
    iconBg: Color,
    title: String,
    summary: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SettingRowIcon(
                emoji = emoji,
                bg = iconBg,
                size = 36.dp,
                fontSize = 18.sp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    )
                    if (!badge.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Booth.Accent.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Booth.Accent,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = scheme.onSurfaceVariantSummary,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = scheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
            modifier = Modifier.rotate(-90f),
        )
    }
}
