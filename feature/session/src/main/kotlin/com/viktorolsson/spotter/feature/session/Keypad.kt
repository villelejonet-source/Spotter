package com.viktorolsson.spotter.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.KeyboardHide
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * In-app numeric keypad for set logging: digits, ± steppers (2.5 kg / 5 lb for
 * weight, 1 for reps), Plates, and Next to move through the sets in order.
 */
@Composable
internal fun Keypad(
    title: String,
    field: SetField,
    stepLabel: String,
    showPlates: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onStep: (Int) -> Unit,
    onPlates: () -> Unit,
    onNext: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(
            Modifier.navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f).padding(start = 8.dp))
                TextButton(onClick = onDone) {
                    Icon(Icons.Rounded.KeyboardHide, contentDescription = null)
                    Text(stringResource(R.string.keypad_done), modifier = Modifier.padding(start = 6.dp))
                }
            }
            KeyRow {
                Digit('1', onDigit); Digit('2', onDigit); Digit('3', onDigit)
                StepKey("−$stepLabel", stringResource(R.string.keypad_decrease, stepLabel)) { onStep(-1) }
            }
            KeyRow {
                Digit('4', onDigit); Digit('5', onDigit); Digit('6', onDigit)
                StepKey("+$stepLabel", stringResource(R.string.keypad_increase, stepLabel)) { onStep(1) }
            }
            KeyRow {
                Digit('7', onDigit); Digit('8', onDigit); Digit('9', onDigit)
                if (showPlates) {
                    OutlinedButton(onClick = onPlates, modifier = Modifier.weight(1f).height(KEY_HEIGHT)) {
                        Text(stringResource(R.string.keypad_plates))
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
            KeyRow {
                if (field == SetField.WEIGHT) Digit('.', onDigit) else Spacer(Modifier.weight(1f))
                Digit('0', onDigit)
                val backspace = stringResource(R.string.keypad_backspace)
                FilledTonalButton(
                    onClick = onBackspace,
                    modifier = Modifier.weight(1f).height(KEY_HEIGHT).semantics { contentDescription = backspace },
                ) { Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = null) }
                androidx.compose.material3.Button(onClick = onNext, modifier = Modifier.weight(1f).height(KEY_HEIGHT)) {
                    Text(stringResource(R.string.keypad_next))
                }
            }
        }
    }
}

private val KEY_HEIGHT = 52.dp

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun RowScope.Digit(char: Char, onDigit: (Char) -> Unit) {
    FilledTonalButton(onClick = { onDigit(char) }, modifier = Modifier.weight(1f).height(KEY_HEIGHT)) {
        Text(char.toString(), style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun RowScope.StepKey(label: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(KEY_HEIGHT).semantics { contentDescription = description },
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}
