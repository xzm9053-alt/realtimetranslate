package com.xzm.realtimetranslate.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.update.RemoteUpdateInfo
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun UpdateDialog(
    info: RemoteUpdateInfo,
    onDismiss: () -> Unit,
    onGoToVideo: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!info.forceUpdate) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = info.title.ifBlank { stringResource(R.string.update_check_title) },
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = MiuixTheme.colorScheme.onSurface,
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (info.versionName.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.update_new_version, info.versionName),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (info.message.isNotBlank()) {
                    Text(
                        text = info.message,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                text = stringResource(R.string.update_action_watch_video),
                onClick = { onGoToVideo(info.videoUrl) },
            )
        },
        dismissButton = if (!info.forceUpdate) {
            {
                TextButton(
                    text = stringResource(R.string.update_action_later),
                    onClick = onDismiss,
                )
            }
        } else null,
    )
}
