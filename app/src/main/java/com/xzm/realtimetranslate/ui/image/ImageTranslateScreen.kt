package com.xzm.realtimetranslate.ui.image

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * 相机 / 图片翻译：选一张图，就地盖上译文，可存进相册或分享。
 *
 * 两种来源共用一个页面，靠顶部两个胶囊切换；真正的差别只有启动哪个
 * `ActivityResultContract`。
 */
@Composable
fun ImageTranslateScreen(
    viewModel: ImageTranslateViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme

    var showOriginal by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    // TakePicture writes into a file we hand it; the FileProvider entry lives under
    // cache/shared so nothing else in the app's storage becomes shareable.
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "camera-${System.currentTimeMillis()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.translate(it) }
    }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.translate(uri)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageTitle(
                title = stringResource(R.string.image_title),
                subtitle = stringResource(R.string.image_subtitle),
            )
        }

        item {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(text = stringResource(R.string.common_back), onClick = onBack)
            }
        }

        item {
            SourceChips(
                selected = state.source,
                onSelect = viewModel::setSource,
            )
        }

        if (state.missingKey) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.image_need_key_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.image_need_key_body),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                        TextButton(
                            text = stringResource(R.string.image_open_settings),
                            onClick = onOpenSettings,
                        )
                    }
                }
            }
        }

        state.error?.let { error ->
            item {
                SectionCard {
                    Text(
                        text = error,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFFF5C5C),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val enabled = !state.isWorking && !state.missingKey
                    TextButton(
                        text = stringResource(R.string.image_take_photo),
                        onClick = {
                            if (enabled) {
                                val uri = newCameraUri()
                                cameraUri = uri
                                takePicture.launch(uri)
                            }
                        },
                    )
                    TextButton(
                        text = stringResource(R.string.image_pick_gallery),
                        onClick = {
                            if (enabled) {
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
        }

        if (state.isWorking) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.image_working),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (state.progressLabel.isNotEmpty()) {
                            Text(
                                text = state.progressLabel,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        val painted = viewModel.translatedBitmap()
        val source = viewModel.originalBitmap()
        if (!state.isWorking && painted != null && source != null) {
            item {
                PreviewCard(
                    bitmap = if (showOriginal) source else painted,
                    caption = if (showOriginal) {
                        stringResource(R.string.image_showing_original)
                    } else {
                        stringResource(R.string.image_showing_translated)
                    },
                    onToggle = { showOriginal = !showOriginal },
                )
            }

            if (state.translatedText.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            text = stringResource(R.string.image_save),
                            onClick = viewModel::saveToGallery,
                        )
                        TextButton(
                            text = stringResource(R.string.image_share),
                            onClick = { shareBitmap(context, painted) },
                        )
                    }
                }
            }

            if (state.sourceText.isNotBlank() || state.translatedText.isNotBlank()) {
                item {
                    SectionCard {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            TextPair(
                                label = stringResource(R.string.image_source_label),
                                body = state.sourceText,
                            )
                            TextPair(
                                label = stringResource(R.string.image_translation_label),
                                body = state.translatedText,
                            )
                        }
                    }
                }
            }
        }

        state.message?.let { message ->
            item {
                SectionCard {
                    Text(
                        text = message,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceChips(selected: ImageSource, onSelect: (ImageSource) -> Unit) {
    val scheme = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SourceChip(
            label = stringResource(R.string.image_source_camera),
            selected = selected == ImageSource.CAMERA,
            onClick = { onSelect(ImageSource.CAMERA) },
            modifier = Modifier.weight(1f),
        )
        SourceChip(
            label = stringResource(R.string.image_source_gallery),
            selected = selected == ImageSource.GALLERY,
            onClick = { onSelect(ImageSource.GALLERY) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SourceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) scheme.surface else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun PreviewCard(bitmap: Bitmap, caption: String, onToggle: () -> Unit) {
    val scheme = MiuixTheme.colorScheme
    SectionCard {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = caption,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggle),
            )
            Text(
                text = caption,
                fontSize = 12.sp,
                color = scheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun TextPair(label: String, body: String) {
    val scheme = MiuixTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = label, fontSize = 12.sp, color = scheme.onSurfaceVariantSummary)
        Text(text = body.ifBlank { "—" }, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Writes the painted picture to cache and hands it to the share sheet. */
private fun shareBitmap(context: Context, bitmap: Bitmap) {
    runCatching {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "share-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.image_share))
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
