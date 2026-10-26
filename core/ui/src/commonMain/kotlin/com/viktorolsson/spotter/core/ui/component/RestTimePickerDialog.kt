package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.Role
import com.viktorolsson.spotter.core.ui.RestOptionsSeconds
import com.viktorolsson.spotter.core.ui.formatClock
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.*

@Composable
fun RestTimePickerDialog(
    title: String,
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(RestOptionsSeconds) { seconds ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = seconds == selectedSeconds,
                                onClick = { onSelect(seconds) },
                                role = Role.RadioButton,
                            )
                            .padding(vertical = Spacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = seconds == selectedSeconds, onClick = null)
                        Text(formatClock(seconds.toLong()), modifier = Modifier.padding(start = Spacing.lg))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
