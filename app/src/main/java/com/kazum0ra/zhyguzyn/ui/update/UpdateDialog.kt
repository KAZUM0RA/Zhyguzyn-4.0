package com.kazum0ra.zhyguzyn.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kazum0ra.zhyguzyn.R
import com.kazum0ra.zhyguzyn.ui.components.message
import com.kazum0ra.zhyguzyn.update.ReleaseInfo
import com.kazum0ra.zhyguzyn.update.UpdateManager
import com.kazum0ra.zhyguzyn.update.UpdateState

/** Діалог оновлення: список змін, кнопка «Оновити», прогрес завантаження, встановлення. */
@Composable
fun UpdateDialogHost(manager: UpdateManager) {
    val visible by manager.dialogVisible.collectAsStateWithLifecycle()
    val state by manager.state.collectAsStateWithLifecycle()
    if (!visible) return
    val context = LocalContext.current

    when (val current = state) {
        is UpdateState.Available -> ReleaseDialog(
            release = current.release,
            currentVersion = manager.currentVersion,
            onDismiss = manager::dismissDialog,
            confirmText = stringResource(R.string.update_button),
            onConfirm = { manager.download(current.release) },
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = manager::dismissDialog,
            title = { Text(stringResource(R.string.update_title, current.release.tagName)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val progress = current.progress
                    if (progress == null) {
                        Text(stringResource(R.string.update_downloading))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else {
                        Text(stringResource(R.string.update_downloading_percent, (progress * 100).toInt()))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = manager::dismissDialog) { Text(stringResource(R.string.close)) }
            },
        )
        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = manager::dismissDialog,
            title = { Text(stringResource(R.string.update_title, current.release.tagName)) },
            text = {
                Text(
                    stringResource(
                        if (current.needsPermission) R.string.update_needs_permission else R.string.update_ready,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = { manager.install(context) }) { Text(stringResource(R.string.update_install)) }
            },
            dismissButton = {
                TextButton(onClick = manager::dismissDialog) { Text(stringResource(R.string.update_later)) }
            },
        )
        is UpdateState.Failed -> {
            val release = current.release
            if (release == null) {
                // Помилки перевірки показуються в налаштуваннях, не в діалозі.
                return
            }
            AlertDialog(
                onDismissRequest = manager::dismissDialog,
                title = { Text(stringResource(R.string.update_error_title)) },
                text = { Text(current.error.message()) },
                confirmButton = {
                    TextButton(onClick = { manager.download(release) }) { Text(stringResource(R.string.update_button)) }
                },
                dismissButton = {
                    TextButton(onClick = manager::dismissDialog) { Text(stringResource(R.string.close)) }
                },
            )
        }
        else -> Unit
    }
}

@Composable
private fun ReleaseDialog(
    release: ReleaseInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_title, release.tagName)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.update_current, currentVersion),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.update_whats_new), style = MaterialTheme.typography.titleSmall)
                Text(release.notes.ifBlank { stringResource(R.string.update_no_notes) })
            }
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.update_later)) } },
    )
}
