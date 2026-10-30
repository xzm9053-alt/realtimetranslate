package com.xzm.realtimetranslate.ui.image

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

/**
 * 相机 / 图片翻译：选一张图，就地覆盖译文，可对比原图、存进相册或分享。
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
    val clipboard = LocalClipboardManager.current

    var showOriginal by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

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

    val painted = viewModel.translatedBitmap()
    val source = viewModel.originalBitmap()
    val hasResult = !state.isWorking && painted != null && source != null
    val enabled = !state.isWorking && !state.missingKey

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ================= 顶部一体化导航标题栏 =================
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
                            text = stringResource(R.string.image_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.image_subtitle),
                            fontSize = 11.5.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }

                if (hasResult) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Booth.Accent.copy(alpha = 0.12f))
                            .clickable { viewModel.reset() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "🔄 换一张图",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
                        )
                    }
                }
            }
        }

        // ================= 特性与标签胶囊 =================
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
                        text = "⚡ 端侧离线 OCR",
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
                        text = "🎨 原图原位涂抹覆盖",
                        fontSize = 11.5.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }

        // ================= 凭据提醒卡片 =================
        if (state.missingKey) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = "⚠️", fontSize = 16.sp)
                            Text(
                                text = stringResource(R.string.image_need_key_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.image_need_key_body),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                        Button(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = stringResource(R.string.image_open_settings),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }

        // ================= 错误信息展示 =================
        state.error?.let { error ->
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "❌", fontSize = 15.sp)
                        Text(
                            text = error,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFFF5C5C),
                        )
                    }
                }
            }
        }

        // ================= 核心操作区（大双核交互卡片） =================
        if (!hasResult && !state.isWorking) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ActionHeroCard(
                        emoji = "📷",
                        title = "现场拍照",
                        subtitle = "对准文字直接拍",
                        enabled = enabled,
                        onClick = {
                            if (enabled) {
                                val uri = newCameraUri()
                                cameraUri = uri
                                takePicture.launch(uri)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    ActionHeroCard(
                        emoji = "🖼️",
                        title = "相册选图",
                        subtitle = "导入已有截图/图片",
                        enabled = enabled,
                        onClick = {
                            if (enabled) {
                                pickImage.launch(
                                    PickVisualMediaRequest(
                                        ActivityResultContracts.PickVisualMedia.ImageOnly,
                                    ),
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // 空白指引与特性卡片
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "💡 图片翻译特色说明",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurfaceVariantSummary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(text = "✨", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "智能原位涂抹替换",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = "自动提取原图背景底色并填充，译文直接平滑覆盖原文",
                                    fontSize = 11.5.sp,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        SettingDivider()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(text = "🌐", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "多语种端侧离线 OCR",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = "支持中、英、日、韩混合排版排布，毫秒级快速分块提取",
                                    fontSize = 11.5.sp,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ================= 正在处理/翻译中状态卡片 =================
        if (state.isWorking) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = Booth.Accent,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = stringResource(R.string.image_working),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        if (state.total > 0) {
                            Text(
                                text = "正在处理文本块：${state.done} / ${state.total}",
                                fontSize = 12.5.sp,
                                color = scheme.onSurfaceVariantSummary,
                            )
                            LinearProgressIndicator(
                                progress = { (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Booth.Accent,
                                trackColor = scheme.surfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Booth.Accent,
                                trackColor = scheme.surfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ================= 翻译结果展示区域 =================
        if (hasResult && painted != null && source != null) {
            // 1. 结果图片卡片（带悬浮对比标签与点击切换）
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showOriginal = !showOriginal },
                        ) {
                            val currentBitmap = if (showOriginal) source else painted
                            Image(
                                bitmap = currentBitmap.asImageBitmap(),
                                contentDescription = if (showOriginal) "原图" else "译文",
                                contentScale = ContentScale.FillWidth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(currentBitmap.width.toFloat() / currentBitmap.height),
                            )

                            // 悬浮状态胶囊
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = if (showOriginal) "📷 原图版" else "✨ 译文覆盖版",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                    )
                                    Text(
                                        text = "· 点击对比",
                                        fontSize = 10.5.sp,
                                        color = Color.White.copy(alpha = 0.7f),
                                    )
                                }
                            }
                        }
                        Text(
                            text = if (showOriginal) {
                                stringResource(R.string.image_showing_original)
                            } else {
                                stringResource(R.string.image_showing_translated)
                            },
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                }
            }

            // 2. 保存与分享快捷操作大按钮
            if (state.translatedText.isNotBlank()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Button(
                            onClick = viewModel::saveToGallery,
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = "💾 保存到相册",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                            )
                        }
                        Button(
                            onClick = { shareBitmap(context, painted) },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            colors = ButtonDefaults.buttonColors(),
                        ) {
                            Text(
                                text = "↗️ 分享图片",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp,
                            )
                        }
                    }
                }
            }

            // 3. 提取文字与译文对照卡片
            if (state.sourceText.isNotBlank() || state.translatedText.isNotBlank()) {
                item {
                    SectionCard {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "📝 提取文字与译文对照",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = "复制译文",
                                    fontSize = 12.sp,
                                    color = Booth.Accent,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            clipboard.setText(AnnotatedString(state.translatedText))
                                            Toast.makeText(context, "已复制译文到剪贴板", Toast.LENGTH_SHORT).show()
                                        }
                                        .padding(4.dp),
                                )
                            }

                            SettingDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = stringResource(R.string.image_source_label),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                                Text(
                                    text = state.sourceText.ifBlank { "—" },
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    color = scheme.onSurface.copy(alpha = 0.8f),
                                )
                            }

                            SettingDivider()

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = stringResource(R.string.image_translation_label),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Booth.Accent,
                                )
                                Text(
                                    text = state.translatedText.ifBlank { "—" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 20.sp,
                                    color = scheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            // 底部换图备选按钮
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(
                        text = "📷 重新拍照",
                        onClick = {
                            val uri = newCameraUri()
                            cameraUri = uri
                            takePicture.launch(uri)
                        },
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    TextButton(
                        text = "🖼️ 从相册换图",
                        onClick = {
                            pickImage.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    )
                }
            }
        }

        // ================= 提示吐司信息 =================
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
private fun ActionHeroCard(
    emoji: String,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MiuixTheme.colorScheme
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
        colors = CardDefaults.defaultColors(
            color = scheme.surfaceContainer,
            contentColor = scheme.onSurfaceContainer,
        ),
        cornerRadius = 16.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Booth.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, fontSize = 26.sp)
            }
            Text(
                text = title,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) scheme.onSurface else scheme.disabledOnSurface,
            )
            Text(
                text = subtitle,
                fontSize = 11.5.sp,
                color = scheme.onSurfaceVariantSummary,
            )
        }
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
        context.startActivity(Intent.createChooser(send, context.getString(R.string.image_share)))
    }
}
