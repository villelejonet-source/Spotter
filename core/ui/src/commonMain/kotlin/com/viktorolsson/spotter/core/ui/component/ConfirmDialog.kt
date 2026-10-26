package com.viktorolsson.spotter.core.ui.component

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.*

/**
 * "Do this?" with one confirming action. A [destructive] action is a text button in
 * the error colour; otherwise the confirmation is the filled button.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
    dismissText: String = stringResource(Res.string.action_cancel),
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            if (destructive) {
                TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
            } else {
                Button(onClick = onConfirm) { Text(confirmText) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissText) } },
    )
}
