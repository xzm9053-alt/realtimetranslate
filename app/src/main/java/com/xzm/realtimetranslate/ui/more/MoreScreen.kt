package com.xzm.realtimetranslate.ui.more

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xzm.realtimetranslate.LiveTranslateApp
import com.xzm.realtimetranslate.service.StaticModeTarget
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
import com.xzm.realtimetranslate.ui.video.queryDisplayName

/**
 * 直接呈现对应的小工具页面（文档、视频、图片、文本），
 * 点返回直接返回首页，彻底消除中间二级菜单。
 */
@Composable
fun MoreScreen(
    target: StaticModeTarget,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    initialVideoUri: Uri? = null,
) {
    val app = LocalContext.current.applicationContext as LiveTranslateApp
    val context = LocalContext.current

    when (target) {
        StaticModeTarget.TEXT -> {
            val textVm: TextTranslateViewModel = viewModel(
                factory = TextTranslateViewModelFactory(
                    app,
                    app.settingsRepository,
                    app.apiKeyStore,
                    app.historyRepository,
                ),
            )
            TextTranslateScreen(
                viewModel = textVm,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )
        }
        StaticModeTarget.VIDEO -> {
            val videoVm: VideoTranslateViewModel = viewModel(
                factory = VideoTranslateViewModelFactory(
                    app,
                    app.settingsRepository,
                    app.apiKeyStore,
                    app.modelManager,
                ),
            )
            LaunchedEffect(initialVideoUri) {
                initialVideoUri?.let { uri ->
                    videoVm.setVideo(uri, queryDisplayName(context, uri))
                }
            }
            VideoTranslateScreen(
                viewModel = videoVm,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )
        }
        StaticModeTarget.CAMERA, StaticModeTarget.IMAGE -> {
            val imageVm: ImageTranslateViewModel = viewModel(
                factory = ImageTranslateViewModelFactory(
                    app,
                    app.settingsRepository,
                    app.apiKeyStore,
                    app.historyRepository,
                ),
            )
            LaunchedEffect(target) {
                if (target == StaticModeTarget.CAMERA) {
                    imageVm.setSource(ImageSource.CAMERA)
                } else {
                    imageVm.setSource(ImageSource.GALLERY)
                }
            }
            ImageTranslateScreen(
                viewModel = imageVm,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )
        }
        StaticModeTarget.DOCUMENT -> {
            val documentVm: DocumentTranslateViewModel = viewModel(
                factory = DocumentTranslateViewModelFactory(
                    app,
                    app.settingsRepository,
                    app.apiKeyStore,
                    app.historyRepository,
                ),
            )
            DocumentTranslateScreen(
                viewModel = documentVm,
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                modifier = modifier,
            )
        }
    }
}
