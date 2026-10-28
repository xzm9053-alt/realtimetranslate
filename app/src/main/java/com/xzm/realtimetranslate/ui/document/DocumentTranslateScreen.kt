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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingSwitchRow
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            PageTitle(
                title = stringResource(R.string.document_title),
                subtitle = stringResource(R.string.document_subtitle),
            )
        }

        item {
            Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                TextButton(text = stringResource(R.string.common_back), onClick = onBack)
            }
        }

        if (state.missingKey) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.document_need_key_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.document_need_key_body),
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

        state.error?.let { err ->
            item {
                SectionCard {
                    Text(
                        text = err,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFFF5C5C),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
        }

        // Document picker card
        item {
            SectionCard {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val docInfo = state.documentInfo
                    if (docInfo == null) {
                        TextButton(
                            text = stringResource(R.string.document_select_file),
                            onClick = {
                                if (!state.isWorking) {
                                    openDocumentLauncher.launch(
                                        arrayOf(
                                            "application/pdf",
                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                            "application/msword",
                                            "*/*",
                                        ),
                                    )
                                }
                            },
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = docInfo.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "${docInfo.type.displayName} · ${docInfo.formattedSize}",
                                    fontSize = 12.sp,
                                    color = scheme.onSurfaceVariantSummary,
                                )
                            }
                            if (!state.isWorking) {
                                TextButton(
                                    text = stringResource(R.string.document_change_file),
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
            }
        }

        // PDF options
        if (state.documentInfo?.type == DocumentType.PDF) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(vertical = 4.dp),
                    ) {
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
        }

        // Scope notice
        item {
            SectionCard {
                Text(
                    text = stringResource(R.string.document_scope_notice),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = scheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        // Working progress card
        if (state.isWorking) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.document_translating),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            if (state.progressLabel.isNotBlank()) {
                                Text(
                                    text = state.progressLabel,
                                    fontSize = 13.sp,
                                    color = scheme.primary,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        if (state.total > 0) {
                            ProgressBar(progress = state.done.toFloat() / state.total.toFloat())
                        }
                        Text(
                            text = state.progressDescription,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }

        // Action button to start translation
        if (state.hasDocument && !state.isWorking && !state.hasResult) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Button(
                        onClick = viewModel::translate,
                        enabled = !state.missingKey,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text(
                            text = stringResource(R.string.document_start),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }

        // Result card
        if (state.hasResult) {
            item {
                SectionCard {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.document_done_title),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF10B981),
                        )
                        Text(
                            text = state.message ?: "",
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = scheme.onSurface,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            val mime = state.documentInfo?.type?.mimeType ?: "*/*"
                            TextButton(
                                text = stringResource(R.string.document_open_file),
                                onClick = { openDocument(state.resultUri, mime) },
                            )
                            TextButton(
                                text = stringResource(R.string.document_share_file),
                                onClick = { shareDocument(state.resultUri, mime) },
                            )
                        }
                    }
                }
            }
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
