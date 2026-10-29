package com.xzm.realtimetranslate.ui.history

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.components.SettingDivider
import com.xzm.realtimetranslate.ui.components.SettingRowIcon
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var expandedId by remember { mutableStateOf<Long?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_title),
                    fontWeight = FontWeight.SemiBold,
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.history_clear_confirm_message),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            confirmButton = {
                TextButton(
                    text = stringResource(R.string.dialog_confirm_clear),
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAll()
                    },
                )
            },
            dismissButton = {
                TextButton(
                    text = stringResource(R.string.dialog_cancel),
                    onClick = { showClearConfirmDialog = false },
                )
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.history_title),
            subtitle = stringResource(R.string.history_subtitle),
        )

        // 顶部统计与清空概览卡片
        HistorySummaryCard(
            entriesCount = entries.size,
            onClearAll = { showClearConfirmDialog = true },
        )

        val msg = message
        if (!msg.isNullOrBlank()) {
            Text(
                text = msg,
                fontSize = 13.sp,
                color = Booth.Accent,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        if (entries.isEmpty()) {
            HistoryEmptyState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.stoppedAtEpochMs }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        expanded = expandedId == entry.stoppedAtEpochMs,
                        onToggle = {
                            expandedId = if (expandedId == entry.stoppedAtEpochMs) {
                                null
                            } else {
                                entry.stoppedAtEpochMs
                            }
                        },
                        onCopy = { text ->
                            if (text.isNotBlank()) {
                                clipboard.setText(AnnotatedString(text))
                                Toast.makeText(context, R.string.history_copied, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onSave = { viewModel.saveEntryAsText(entry) },
                        onDelete = { viewModel.deleteEntry(entry.stoppedAtEpochMs) },
                    )
                }
            }
        }
    }
}

/**
 * 顶部会话概览与清空操作卡片。
 */
@Composable
private fun HistorySummaryCard(
    entriesCount: Int,
    onClearAll: () -> Unit,
) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    emoji = "📜",
                    bg = Booth.Accent.copy(alpha = 0.12f),
                    size = 38.dp,
                    fontSize = 18.sp,
                )
                Column {
                    Text(
                        text = stringResource(R.string.history_count, entriesCount),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (entriesCount == 0) {
                            stringResource(R.string.history_summary_empty)
                        } else {
                            stringResource(R.string.history_summary_active)
                        },
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (entriesCount > 0) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Booth.Danger.copy(alpha = 0.10f))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.history_clear_all),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Booth.Danger,
                    )
                }
            }
        }
    }
}

/**
 * 沉浸式空状态卡片。
 */
@Composable
private fun HistoryEmptyState() {
    val scheme = MiuixTheme.colorScheme
    SectionCard(modifier = Modifier.padding(top = 8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 48.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(scheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "📜", fontSize = 28.sp)
            }
            Text(
                text = stringResource(R.string.history_empty),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
            )
            Text(
                text = stringResource(R.string.history_empty_hint),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = scheme.onSurfaceVariantSummary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HistoryEntryCard(
    entry: HistoryEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    SectionCard {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingRowIcon(
                        emoji = if (entry.outputFull.isNotBlank()) "🎙️" else "🔍",
                        bg = if (entry.outputFull.isNotBlank()) {
                            Booth.Accent.copy(alpha = 0.10f)
                        } else {
                            Color(0xFF10B981).copy(alpha = 0.10f)
                        },
                        size = 36.dp,
                        fontSize = 17.sp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = formatTime(entry.stoppedAtEpochMs),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MiuixTheme.colorScheme.onSurface,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.history_entry_meta,
                                        entry.inputFull.length,
                                        entry.outputFull.length,
                                    ),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                        if (!expanded) {
                            val preview = entry.outputFull.ifBlank { entry.inputFull }.trim()
                            if (preview.isNotBlank()) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = preview,
                                    fontSize = 12.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                val rotationAngle by animateFloatAsState(
                    targetValue = if (expanded) 180f else 0f,
                    label = "arrowRotation",
                )
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.6f),
                    modifier = Modifier.rotate(rotationAngle),
                )
            }

            if (expanded) {
                SettingDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CopyableTextBlock(
                        badge = stringResource(R.string.history_original),
                        badgeColor = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        text = entry.inputFull,
                        onCopy = onCopy,
                    )
                    CopyableTextBlock(
                        badge = stringResource(R.string.history_translation),
                        badgeColor = Booth.Accent,
                        text = entry.outputFull,
                        onCopy = onCopy,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Booth.Danger.copy(alpha = 0.08f))
                                .clickable(onClick = onDelete)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.history_delete),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Booth.Danger,
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Booth.Accent.copy(alpha = 0.12f))
                                .clickable(onClick = onSave)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "💾 ",
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text = stringResource(R.string.history_save_txt),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Booth.Accent,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CopyableTextBlock(
    badge: String,
    badgeColor: Color,
    text: String,
    onCopy: (String) -> Unit,
) {
    val scheme = MiuixTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badge,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor,
                )
            }
            if (text.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onCopy(text) }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "📋", fontSize = 11.sp)
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "复制",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariantSummary,
                    )
                }
            }
        }
        Text(
            text = text.ifBlank { "（无内容）" },
            fontSize = 13.5.sp,
            lineHeight = 19.sp,
            color = scheme.onSurface,
        )
    }
}

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
