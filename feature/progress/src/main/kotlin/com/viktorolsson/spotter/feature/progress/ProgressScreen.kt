package com.viktorolsson.spotter.feature.progress

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.viktorolsson.spotter.core.ui.component.EmptyState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProgressScreen() {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) },
    ) { padding ->
        EmptyState(
            icon = Icons.Rounded.Insights,
            title = stringResource(R.string.progress_empty_title),
            body = stringResource(R.string.progress_empty_body),
            modifier = Modifier.padding(padding),
        )
    }
}
