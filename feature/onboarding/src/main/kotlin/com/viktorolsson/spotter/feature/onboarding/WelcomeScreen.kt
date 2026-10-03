package com.viktorolsson.spotter.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** First launch: create an account (backed up), sign in (restore), or stay on this phone only. */
@Composable
internal fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, onContinueWithout: () -> Unit) {
    var confirmWithout by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(96.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(48.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCreateAccount, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.welcome_create), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(onClick = onSignIn, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.welcome_sign_in), style = MaterialTheme.typography.titleMedium)
                }
                TextButton(onClick = { confirmWithout = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.welcome_without))
                }
                Text(
                    stringResource(R.string.welcome_account_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmWithout) {
        AlertDialog(
            onDismissRequest = { confirmWithout = false },
            title = { Text(stringResource(R.string.welcome_without_title)) },
            text = { Text(stringResource(R.string.welcome_without_body)) },
            confirmButton = {
                TextButton(onClick = { confirmWithout = false; onCreateAccount() }) {
                    Text(stringResource(R.string.welcome_create))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmWithout = false; onContinueWithout() }) {
                    Text(stringResource(R.string.welcome_without_confirm))
                }
            },
        )
    }
}

/** Right after signing in: the first sync decides between restoring a backup and setting up. */
@Composable
internal fun CheckingBackup() {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.welcome_checking_backup), style = MaterialTheme.typography.bodyLarge)
        }
    }
}
