package com.viktorolsson.spotter.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.viktorolsson.spotter.core.ui.theme.SpotterTheme

@Composable
internal fun TodayRoute(
    onOpenSession: (Long) -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreen(
        uiState = uiState,
        onResume = { uiState.activeSessionId?.let(onOpenSession) },
        onEmptyWorkout = { viewModel.startEmptyWorkout(onOpenSession) },
    )
}

// "Start workout" (the planned day) arrives with the plan generator in milestone 3.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodayScreen(
    uiState: TodayUiState,
    onResume: () -> Unit,
    onEmptyWorkout: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.today_title)) }) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.today_no_plan_title), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.today_no_plan_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.activeSessionId != null) {
                Button(
                    onClick = onResume,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(
                        stringResource(R.string.today_resume_workout),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Text(
                        stringResource(R.string.today_start_workout),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                OutlinedButton(
                    onClick = onEmptyWorkout,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Text(
                        stringResource(R.string.today_empty_workout),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
            Text(
                stringResource(R.string.today_library_count, uiState.exerciseCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun TodayScreenPreview() {
    SpotterTheme { TodayScreen(TodayUiState(exerciseCount = 218), {}, {}) }
}
