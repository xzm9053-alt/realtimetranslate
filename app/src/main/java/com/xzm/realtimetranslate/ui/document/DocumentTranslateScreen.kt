package com.xzm.realtimetranslate.ui.document

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.document.DocumentType
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.components.SettingSwitchRow
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DocumentTranslateScreen(
    viewModel: DocumentTranslateViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = true) { onBack() }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MiuixTheme.colorScheme

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            var name = "document"
            var size = 0L
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIdx != -1) name = cursor.getString(nameIdx) ?: name
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIdx != -1) size = cursor.getLong(sizeIdx)
                    }
                }
            }
            val mime = context.contentResolver.getType(uri)
            viewModel.selectDocument(uri, name, size, mime)
        }
    }

    fun openDocument(uri: Uri?, mimeType: String) {
        if (uri == null) {
            Toast.makeText(context, R.string.document_open_failed, Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            context.startActivity(intent)
        }.onFailure {
            Toast.makeText(context, R.string.document_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    fun shareDocument(uri: Uri?, mimeType: String) {
        if (uri == null) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.document_share_file)))
        }
    }

    val docInfo = state.documentInfo
    val enabled = !state.isWorking && !state.missingKey

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            text = stringResource(R.string.document_title),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.document_subtitle),
                            fontSize = 11.5.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }

                if (docInfo != null && !state.isWorking) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(Booth.Accent.copy(alpha = 0.12f))
                            .clickable { viewModel.clearSelection() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = "🔄 换个文档",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Booth.Accent,
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
                        text = "📄 PDF / Word 原版式",
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
                        text = "⚡ 保留图表与结构",
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
                                text = stringResource(R.string.document_need_key_title),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.document_need_key_body),
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

        // ================= 错误信息提示 =================
        state.error?.let { err ->
            item {
                SectionCard {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(text = "❌", fontSize = 15.sp)
                        Text(
                            text = err,
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = Color(0xFFFF5C5C),
                        )
                    }
                }
            }
        }

        // ================= 核心操作区：未选择文档时的 Hero Card =================
        if (docInfo == null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = enabled) {
                            openDocumentLauncher.launch(
                                arrayOf(
                                    "application/pdf",
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                    "application/msword",
                                    "*/*",
                                ),
                            )
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
                            Text(text = "📑", fontSize = 28.sp)
                        }
                        Text(
                            text = "点击选择待翻译文档",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        Text(
                            text = "支持 PDF (.pdf) 与 Word (.docx / .doc)",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(text = "PDF 逐页覆盖", fontSize = 11.sp, color = scheme.onSurfaceVariantSummary)
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(scheme.surfaceVariant)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(text = "Word 原位段落替换", fontSize = 11.sp, color = scheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }

            // 说明卡片
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "💡 文档翻译支持说明",
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
                                    text = "高保真原格式输出",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                )
                                Text(
                                    text = "翻译后生成同名译文文档，表格与图表排版完全保留",
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
                            Text(text = "🔒", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = stringResource(R.string.document_scope_notice),
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ================= 已选择文档的卡片展示 =================
            item {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Booth.Accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                val icon = if (docInfo.type == DocumentType.PDF) "📄" else "📝"
                                Text(text = icon, fontSize = 22.sp)
                            }
                            Column {
                                Text(
                                    text = docInfo.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = scheme.onSurface,
                                    maxLines = 2,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${docInfo.type.displayName} · ${docInfo.formattedSize}",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        if (!state.isWorking) {
                            TextButton(
                                text = "重选",
                                onClick = {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/msword",
                                            "*/*",
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            // PDF 双语排版切换开关
            if (docInfo.type == DocumentType.PDF) {
                item {
                    SectionCard {
                        SettingSwitchRow(
                            title = stringResource(R.string.document_bilingual_toggle),
                            summary = stringResource(R.string.document_bilingual_summary),
                            checked = state.bilingualAlternating,
                            onCheckedChange = viewModel::setBilingualAlternating,
                            enabled = !state.isWorking,
                        )
                    }
                }
            }

            // 开始翻译大按钮
            if (!state.isWorking && !state.hasResult) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    ) {
                        Button(
                            onClick = viewModel::translate,
                            enabled = !state.missingKey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Text(
                                text = "▶ 开始翻译文档",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        }

        // ================= 正在翻译进度展示 =================
        if (state.isWorking) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = Booth.Accent,
                            strokeWidth = 3.dp,
                        )
                        Text(
                            text = stringResource(R.string.document_translating),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onSurface,
                        )
                        if (state.progressLabel.isNotBlank()) {
                            Text(
                                text = "正在处理：${state.progressLabel}",
                                fontSize = 12.5.sp,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                        if (state.total > 0) {
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
                        if (state.progressDescription.isNotBlank()) {
                            Text(
                                text = state.progressDescription,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariantSummary,
                            )
                        }
                    }
                }
            }
        }

        // ================= 翻译结果完成卡片 =================
        if (state.hasResult) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(text = "🎉", fontSize = 18.sp)
                            Text(
                                text = stringResource(R.string.document_done_title),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF10B981),
                            )
                        }
                        Text(
                            text = state.message ?: "",
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp,
                            color = scheme.onSurface,
                        )

                        SettingDivider()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            val mime = state.documentInfo?.type?.mimeType ?: "*/*"
                            Button(
                                onClick = { openDocument(state.resultUri, mime) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                            ) {
                                Text(
                                    text = "📂 " + stringResource(R.string.document_open_file),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp,
                                )
                            }
                            Button(
                                onClick = { shareDocument(state.resultUri, mime) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp),
                                colors = ButtonDefaults.buttonColors(),
                            ) {
                                Text(
                                    text = "↗️ " + stringResource(R.string.document_share_file),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.5.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
