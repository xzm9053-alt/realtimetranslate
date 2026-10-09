package com.xzm.realtimetranslate.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xzm.realtimetranslate.R
import com.xzm.realtimetranslate.ui.components.PageTitle
import com.xzm.realtimetranslate.util.AppLog
import com.xzm.realtimetranslate.util.LogLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** A whole session plus context fits well inside this, and it bounds the read. */
private const val MAX_LINES = 800

private sealed interface LogState {
    data object Loading : LogState

    data class Loaded(val lines: List<String>) : LogState

    data object Failed : LogState
}

/**
 * Read-only tail of the log file.
 *
 * Deliberately manual-refresh: a session emits a line every few seconds, so auto-scroll
 * would fight the user for position and make reading a stack trace impossible.
 *
 * The "errors only" filter keys off the level letter rather than the tag, which is what
 * makes one switch useful across every failure mode — crashes, HTTP failures and capture
 * failures are all `W`/`E`, while the `TRANSPROF` latency lines and heartbeats that drown
 * a report are `I`. Continuation lines follow their header, so stack frames survive.
 */
@Composable
fun LogViewerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var errorsOnly by rememberSaveable { mutableStateOf(false) }
    var reloads by remember { mutableIntStateOf(0) }
    val state by produceState<LogState>(LogState.Loading, reloads) {
        // File IO never runs during composition.
        value = withContext(Dispatchers.IO) {
            runCatching { AppLog.tailMain(MAX_LINES) }.fold(
                onSuccess = { LogState.Loaded(it) },
                onFailure = { LogState.Failed },
            )
        }
    }

    BackHandler(enabled = true) { onBack() }

    val allLines = (state as? LogState.Loaded)?.lines.orEmpty()
    val shown = remember(allLines, errorsOnly) {
        if (errorsOnly) LogLine.filterErrors(allLines) else allLines
    }

    Column(modifier = modifier.fillMaxSize()) {
        PageTitle(title = stringResource(R.string.settings_logs_title))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(
                text = stringResource(R.string.settings_logs_back),
                onClick = onBack,
            )
            TextButton(
                text = stringResource(R.string.settings_logs_refresh),
                onClick = { reloads++ },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.settings_logs_errors_only),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Switch(
                    checked = errorsOnly,
                    onCheckedChange = { errorsOnly = it },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        when {
            state is LogState.Failed -> CenteredNotice(stringResource(R.string.settings_logs_read_failed))
            shown.isEmpty() -> CenteredNotice(stringResource(R.string.settings_logs_empty))
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MiuixTheme.colorScheme.surface),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // No keys: lines are not unique and the list never reorders.
                items(shown) { line ->
                    Text(
                        text = line,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredNotice(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
