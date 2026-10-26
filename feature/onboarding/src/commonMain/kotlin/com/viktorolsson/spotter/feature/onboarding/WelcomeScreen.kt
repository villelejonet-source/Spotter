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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.ui.component.ConfirmDialog
import com.viktorolsson.spotter.core.ui.component.LoadingState
import com.viktorolsson.spotter.core.ui.component.PrimaryButton
import com.viktorolsson.spotter.core.ui.component.SecondaryButton
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import com.viktorolsson.spotter.feature.onboarding.resources.Res
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_account_note
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_checking_backup
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_create
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_sign_in
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_tagline
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_title
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_without
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_without_body
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_without_confirm
import com.viktorolsson.spotter.feature.onboarding.resources.welcome_without_title

/** First launch: create an account (backed up), sign in (restore), or stay on this phone only. */
@Composable
internal fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit, onContinueWithout: () -> Unit) {
    var confirmWithout by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(Dimens.FocusedScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(LOGO_SIZE)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.FitnessCenter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(LOGO_ICON_SIZE),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xl))
            Text(
                stringResource(Res.string.welcome_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                stringResource(Res.string.welcome_tagline),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                PrimaryButton(stringResource(Res.string.welcome_create), onCreateAccount)
                SecondaryButton(stringResource(Res.string.welcome_sign_in), onSignIn)
                TextButton(onClick = { confirmWithout = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.welcome_without))
                }
                Text(
                    stringResource(Res.string.welcome_account_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (confirmWithout) {
        // The confirming action is the one the title asks about; creating an account is the way out.
        ConfirmDialog(
            title = stringResource(Res.string.welcome_without_title),
            text = stringResource(Res.string.welcome_without_body),
            confirmText = stringResource(Res.string.welcome_without_confirm),
            onConfirm = { confirmWithout = false; onContinueWithout() },
            onDismiss = { confirmWithout = false },
            dismissText = stringResource(Res.string.welcome_create),
        )
    }
}

/** Right after signing in: the first sync decides between restoring a backup and setting up. */
@Composable
internal fun CheckingBackup() {
    Surface(Modifier.fillMaxSize()) {
        LoadingState(Modifier.fillMaxSize(), text = stringResource(Res.string.welcome_checking_backup))
    }
}

private val LOGO_SIZE = 96.dp
private val LOGO_ICON_SIZE = 48.dp
