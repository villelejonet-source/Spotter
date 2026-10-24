package com.viktorolsson.spotter.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.viktorolsson.spotter.core.model.PersonalRecord
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.R
import com.viktorolsson.spotter.core.ui.formatRecordValue
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.localDate
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing

/**
 * Personal records under a trophy heading. [celebrate] paints it in the tertiary
 * container (a just-set record); otherwise it's a regular content card listing recent
 * records with their dates, and [emptyText] when there are none yet.
 */
@Composable
fun RecordsCard(
    title: String,
    records: List<PersonalRecord>,
    unit: WeightUnit,
    modifier: Modifier = Modifier,
    celebrate: Boolean = true,
    emptyText: String? = null,
) {
    val container = if (celebrate) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val onContainer = if (celebrate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
    val iconTint = if (celebrate) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.tertiary
    Card(colors = CardDefaults.cardColors(containerColor = container), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(Dimens.CardPadding), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = iconTint)
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    modifier = Modifier.padding(start = Spacing.sm).semantics { heading() },
                )
            }
            if (records.isEmpty() && emptyText != null) {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            records.forEach { record ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (celebrate) {
                            Text(
                                stringResource(R.string.record_line, record.exerciseName, stringResource(record.type.labelRes)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = onContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(record.exerciseName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.record_line, stringResource(record.type.labelRes), formatShortDate(record.achievedAt.localDate())),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        formatRecordValue(record, unit),
                        style = if (celebrate) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                        color = onContainer,
                        modifier = Modifier.padding(start = Spacing.sm),
                    )
                }
            }
        }
    }
}
