package com.xzm.realtimetranslate.ui.glossary

import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xzm.realtimetranslate.data.GlossaryTerm
import com.xzm.realtimetranslate.ui.components.SectionCard
import com.xzm.realtimetranslate.ui.theme.Booth
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GlossaryScreen(
    onBack: () -> Unit,
    viewModel: GlossaryViewModel,
    modifier: Modifier = Modifier,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var query by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingTerm by remember { mutableStateOf<GlossaryTerm?>(null) }
    var showImportExportDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val visibleEntries = remember(entries, query) {
        val q = query.trim()
        if (q.isEmpty()) entries
        else entries.filter {
            it.source.contains(q, ignoreCase = true) ||
                it.target.contains(q, ignoreCase = true) ||
                it.note.contains(q, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = MiuixTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = "专业术语库",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                text = "导入/导出",
                onClick = { showImportExportDialog = true },
            )
            IconButton(
                onClick = { showAddDialog = true },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "添加术语",
                    tint = Booth.Accent,
                )
            }
        }

        // Search bar
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("搜索原文、译文或备注…", fontSize = 14.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(14.dp),
        )

        // Summary Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val enabledCount = entries.count { it.enabled }
            Text(
                text = "共 ${entries.size} 条术语 · 已生效 $enabledCount 条",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            if (entries.isNotEmpty()) {
                Text(
                    text = "清空全部",
                    fontSize = 12.sp,
                    color = Booth.Danger,
                    modifier = Modifier.clickable { showClearDialog = true },
                )
            }
        }

        if (visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (query.isEmpty()) "暂无专业术语，点击右上角 + 添加" else "未找到匹配术语",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(visibleEntries, key = { it.id }) { term ->
                    GlossaryTermCard(
                        term = term,
                        onToggle = { enabled -> viewModel.toggle(term.id, enabled) },
                        onEdit = { editingTerm = term },
                        onDelete = { viewModel.delete(term.id) },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    // Add Dialog
    if (showAddDialog) {
        TermEditDialog(
            title = "添加专业术语",
            initialSource = "",
            initialTarget = "",
            initialNote = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { src, dst, note ->
                viewModel.add(src, dst, note)
                showAddDialog = false
            },
        )
    }

    // Edit Dialog
    editingTerm?.let { term ->
        TermEditDialog(
            title = "编辑专业术语",
            initialSource = term.source,
            initialTarget = term.target,
            initialNote = term.note,
            onDismiss = { editingTerm = null },
            onConfirm = { src, dst, note ->
                viewModel.update(term.copy(source = src, target = dst, note = note))
                editingTerm = null
            },
        )
    }

    // Import/Export Dialog
    if (showImportExportDialog) {
        ImportExportDialog(
            onDismiss = { showImportExportDialog = false },
            onExport = {
                val tsv = viewModel.exportTsv()
                clipboard.setText(AnnotatedString(tsv))
                Toast.makeText(context, "已复制 ${entries.size} 条术语到剪贴板 (TSV格式)", Toast.LENGTH_SHORT).show()
                showImportExportDialog = false
            },
            onImport = { text ->
                viewModel.importText(text) {
                    showImportExportDialog = false
                }
            },
        )
    }

    // Clear All Dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空所有术语", fontWeight = FontWeight.Bold) },
            text = { Text("确定要清空全部 ${entries.size} 条专业术语吗？此操作无法撤销。") },
            confirmButton = {
                TextButton(
                    text = "清空",
                    onClick = {
                        viewModel.clearAll()
                        showClearDialog = false
                    },
                )
            },
            dismissButton = {
                TextButton(
                    text = "取消",
                    onClick = { showClearDialog = false },
                )
            },
        )
    }
}

@Composable
private fun GlossaryTermCard(
    term: GlossaryTerm,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = term.source,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (term.enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "➔",
                        fontSize = 13.sp,
                        color = Booth.Accent,
                    )
                    Text(
                        text = term.target,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (term.enabled) Booth.Accent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (term.note.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = term.note,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action icons and toggle
            IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = "编辑",
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = Booth.Danger.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Switch(
                checked = term.enabled,
                onCheckedChange = onToggle,
            )
        }
    }
}

@Composable
private fun TermEditDialog(
    title: String,
    initialSource: String,
    initialTarget: String,
    initialNote: String,
    onDismiss: () -> Unit,
    onConfirm: (source: String, target: String, note: String) -> Unit,
) {
    var source by remember { mutableStateOf(initialSource) }
    var target by remember { mutableStateOf(initialTarget) }
    var note by remember { mutableStateOf(initialNote) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = source,
                    onValueChange = { source = it },
                    label = { Text("原文术语 (如 LiveTranslate / HP)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text("目标译文 (如 实时翻译 / 生命值)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注说明 (可选，如 游戏专有名词)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = source.isNotBlank() && target.isNotBlank(),
                onClick = { onConfirm(source.trim(), target.trim(), note.trim()) },
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(
                text = "取消",
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun ImportExportDialog(
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: (String) -> Unit,
) {
    var importText by remember { mutableStateOf("") }
    var isImportMode by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isImportMode) "批量导入术语" else "专业术语导入与导出", fontWeight = FontWeight.Bold) },
        text = {
            if (!isImportMode) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "支持以 TSV 格式（每行: 原文 [制表符] 译文 [制表符] 备注）批量备份与快速恢复词库。",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Button(
                        onClick = onExport,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("一键复制全部术语到剪贴板 (导出)")
                    }
                    Button(
                        onClick = { isImportMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Text("从文本 / 剪贴板批量导入")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "支持每行格式：\n• 原文\\t译文\\t备注\n• 原文 -> 译文\n• 原文, 译文",
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        placeholder = { Text("在此粘贴批量词条文本…") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (isImportMode) {
                Button(
                    enabled = importText.isNotBlank(),
                    onClick = { onImport(importText) },
                ) {
                    Text("开始导入")
                }
            }
        },
        dismissButton = {
            TextButton(
                text = if (isImportMode) "返回" else "关闭",
                onClick = {
                    if (isImportMode) isImportMode = false else onDismiss()
                },
            )
        },
    )
}
