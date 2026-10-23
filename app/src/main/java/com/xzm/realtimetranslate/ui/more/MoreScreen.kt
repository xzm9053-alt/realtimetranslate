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
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
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

/**
 * "其他翻译"入口下的二级页面：一个 Hub，加文本翻译与视频翻译两个子页。
 *
 * 沿用项目现有的手写导航（rememberSaveable + BackHandler），不引入 Navigation-Compose。
 */
@Composable
fun MoreScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
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
        else -> MoreHub(
            onBack = onBack,
            onOpenText = { page = PAGE_TEXT },
            onOpenVideo = { page = PAGE_VIDEO },
            modifier = modifier,
        )
    }
}

@Composable
private fun MoreHub(
    onBack: () -> Unit,
    onOpenText: () -> Unit,
    onOpenVideo: () -> Unit,
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
