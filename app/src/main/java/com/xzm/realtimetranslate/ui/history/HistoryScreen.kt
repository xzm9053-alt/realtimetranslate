package com.xzm.realtimetranslate.ui.history

import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.data.HistoryEntry
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.ui.components.SectionCard
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 12.dp),
    ) {
        PageTitle(
            title = stringResource(R.string.history_title),
            subtitle = stringResource(R.string.history_subtitle),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.history_count, entries.size),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            TextButton(
                text = stringResource(R.string.history_clear_all),
                onClick = viewModel::clearAll,
            )
        }

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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.history_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                Toast.makeText(context, R.string.history_copied, Toast.LENGTH_SHORT)
                                    .show()
                            }
                        },
                        onSave = { viewModel.saveEntryAsText(entry) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryEntryCard(
    entry: HistoryEntry,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCopy: (String) -> Unit,
    onSave: () -> Unit,
) {
    SectionCard {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatTime(entry.stoppedAtEpochMs),
                        fontWeight = FontWeight.SemiBold,
                    )
                    Box(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(
                            R.string.history_entry_meta,
                            entry.inputFull.length,
                            entry.outputFull.length,
                        ),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                Text(
                    text = stringResource(
                        if (expanded) R.string.history_collapse else R.string.history_expand,
                    ),
                    fontSize = 13.sp,
                    color = Booth.Accent,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CopyableTextBlock(
                        title = stringResource(R.string.history_original),
                        text = entry.inputFull,
                        onCopy = onCopy,
                    )
                    CopyableTextBlock(
                        title = stringResource(R.string.history_translation),
                        text = entry.outputFull,
                        onCopy = onCopy,
                    )
                    TextButton(
                        text = stringResource(R.string.history_save_txt),
                        onClick = onSave,
                        modifier = Modifier.align(Alignment.End),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CopyableTextBlock(
    title: String,
    text: String,
    onCopy: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MiuixTheme.colorScheme.surface)
            .combinedClickable(
                onClick = {},
                onLongClick = { onCopy(text) },
                indication = null,
                interactionSource = interaction,
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Text(
            text = text.ifBlank { "（无）" },
            fontSize = 13.sp,
        )
    }
}

private fun formatTime(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
