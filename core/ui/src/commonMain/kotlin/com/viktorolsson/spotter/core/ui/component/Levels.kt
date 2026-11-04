package com.viktorolsson.spotter.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Scale
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.viktorolsson.spotter.core.model.Badge
import com.viktorolsson.spotter.core.model.BadgeMetric
import com.viktorolsson.spotter.core.model.BadgeProgress
import com.viktorolsson.spotter.core.model.Level
import com.viktorolsson.spotter.core.model.LiftMastery
import com.viktorolsson.spotter.core.model.Quest
import com.viktorolsson.spotter.core.model.QuestKind
import com.viktorolsson.spotter.core.model.WeekQuests
import com.viktorolsson.spotter.core.model.WeightUnit
import com.viktorolsson.spotter.core.ui.formatCount
import com.viktorolsson.spotter.core.ui.formatShortDate
import com.viktorolsson.spotter.core.ui.formatWeight
import com.viktorolsson.spotter.core.ui.howRes
import com.viktorolsson.spotter.core.ui.labelRes
import com.viktorolsson.spotter.core.ui.resources.Res
import com.viktorolsson.spotter.core.ui.resources.badge_locked_description
import com.viktorolsson.spotter.core.ui.resources.badge_progress
import com.viktorolsson.spotter.core.ui.resources.badge_unlocked_on
import com.viktorolsson.spotter.core.ui.resources.level_bar_description
import com.viktorolsson.spotter.core.ui.resources.level_heading
import com.viktorolsson.spotter.core.ui.resources.level_to_next
import com.viktorolsson.spotter.core.ui.resources.mastery_best_next
import com.viktorolsson.spotter.core.ui.resources.mastery_best_top
import com.viktorolsson.spotter.core.ui.resources.mastery_no_tier
import com.viktorolsson.spotter.core.ui.resources.mastery_not_done
import com.viktorolsson.spotter.core.ui.resources.quest_check_ins
import com.viktorolsson.spotter.core.ui.resources.quest_done
import com.viktorolsson.spotter.core.ui.resources.quest_new_exercise
import com.viktorolsson.spotter.core.ui.resources.quest_progress
import com.viktorolsson.spotter.core.ui.resources.quest_records
import com.viktorolsson.spotter.core.ui.resources.quest_sets
import com.viktorolsson.spotter.core.ui.resources.quest_workouts
import com.viktorolsson.spotter.core.ui.theme.Dimens
import com.viktorolsson.spotter.core.ui.theme.Spacing
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

// Levels and badges (core/engine Gamification.kt): the level bar, its heading and a badge tile.

/** "Level 8 · Plate Collector". */
@Composable
fun levelHeading(level: Level): String =
    stringResource(Res.string.level_heading, level.number, stringResource(level.title.labelRes))

/**
 * How far into [level] the XP is, as a bar. It fills from [from] (0–1, e.g. where it stood
 * before this workout) to where it is now; only the bar's scale animates.
 */
@Composable
fun LevelBar(level: Level, modifier: Modifier = Modifier, from: Float? = null) {
    val target = if (level.xpForNext > 0) (level.xpIntoLevel.toFloat() / level.xpForNext).coerceIn(0f, 1f) else 0f
    val fill = remember(level) { Animatable(from?.coerceIn(0f, target) ?: target) }
    LaunchedEffect(target) { fill.animateTo(target, tween(BAR_MILLIS)) }
    val description = stringResource(
        Res.string.level_bar_description, formatCount(level.xpIntoLevel), formatCount(level.xpForNext), level.number + 1,
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fill.value
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** The level heading, its bar and the XP still to go. */
@Composable
fun LevelProgress(level: Level, modifier: Modifier = Modifier, from: Float? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(levelHeading(level), style = MaterialTheme.typography.titleMedium)
        LevelBar(level, from = from)
        Text(
            stringResource(Res.string.level_to_next, formatCount(level.xpForNext - level.xpIntoLevel), level.number + 1),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

val Badge.icon: ImageVector
    get() = when (metric) {
        BadgeMetric.WORKOUTS -> Icons.Rounded.FitnessCenter
        BadgeMetric.RECORDS -> Icons.Rounded.EmojiEvents
        BadgeMetric.TRIPLE_THREATS -> Icons.Rounded.AutoAwesome
        BadgeMetric.WEEK_STREAK -> Icons.Rounded.LocalFireDepartment
        BadgeMetric.VOLUME_KG -> Icons.Rounded.Scale
        BadgeMetric.LISTENED -> Icons.Rounded.SelfImprovement
        BadgeMetric.COMEBACKS -> Icons.Rounded.Replay
        BadgeMetric.EARLY_STARTS -> Icons.Rounded.WbSunny
        BadgeMetric.LATE_STARTS -> Icons.Rounded.Bedtime
        BadgeMetric.EXERCISES -> Icons.Rounded.Explore
    }

/**
 * A badge's icon, name and how to earn it, with the unlock date or how far along it is
 * ([showStatus] false leaves that line out, for a badge just earned).
 */
@Composable
fun BadgeTile(progress: BadgeProgress, modifier: Modifier = Modifier, showStatus: Boolean = true) {
    val unlocked = progress.unlocked
    val container = if (unlocked) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val content = if (unlocked) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val locked = stringResource(Res.string.badge_locked_description)
    Card(colors = CardDefaults.cardColors(containerColor = container, contentColor = content), modifier = modifier) {
        Column(
            Modifier.fillMaxWidth().padding(Dimens.TilePadding).semantics(mergeDescendants = true) {
                if (!unlocked) stateDescription = locked
            },
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Icon(
                    if (unlocked) progress.badge.icon else Icons.Rounded.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(Dimens.IconMedium),
                )
                Text(
                    stringResource(progress.badge.labelRes),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(stringResource(progress.badge.howRes), style = MaterialTheme.typography.bodySmall)
            val on = progress.unlockedOn
            if (showStatus) {
                Text(
                    if (on != null) stringResource(Res.string.badge_unlocked_on, formatShortDate(on))
                    else stringResource(Res.string.badge_progress, (progress.current / progress.badge.target * 100).roundToInt()),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

/** A quest as it reads: "Train 3 times". */
@Composable
fun questLabel(quest: Quest): String = when (quest.kind) {
    QuestKind.WORKOUTS -> pluralStringResource(Res.plurals.quest_workouts, quest.target, quest.target)
    QuestKind.NEW_EXERCISE -> stringResource(Res.string.quest_new_exercise)
    QuestKind.CHECK_INS -> stringResource(Res.string.quest_check_ins, quest.target)
    QuestKind.RECORDS -> stringResource(Res.string.quest_records)
    QuestKind.SETS -> stringResource(Res.string.quest_sets, quest.target)
}

/**
 * This week's quests, each with how far along it is; a finished one is struck through with
 * the [questXp] it paid. [compact] leaves the bars out (Today's card).
 */
@Composable
fun QuestList(week: WeekQuests, questXp: Int, modifier: Modifier = Modifier, compact: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        week.quests.forEach { progress ->
            Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        questLabel(progress.quest),
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (progress.done) TextDecoration.LineThrough else null,
                        color = if (progress.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (progress.done) stringResource(Res.string.quest_done, questXp)
                        else stringResource(Res.string.quest_progress, progress.current, progress.quest.target),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!compact) QuestBar(progress.current.toFloat() / progress.quest.target)
            }
        }
    }
}

@Composable
private fun QuestBar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(QUEST_BAR_HEIGHT)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = fraction.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin(0f, 0.5f)
                }
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Each mastery lift with its tier, its best estimated 1RM and what the next tier needs. */
@Composable
fun MasteryList(lifts: List<LiftMastery>, unit: WeightUnit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        lifts.forEach { lift ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(lift.lift.labelRes), style = MaterialTheme.typography.titleSmall)
                    val best = lift.bestKg
                    val next = lift.nextTier
                    val nextKg = lift.nextKg
                    Text(
                        when {
                            best == null -> stringResource(Res.string.mastery_not_done)
                            next == null || nextKg == null -> stringResource(Res.string.mastery_best_top, formatWeight(best, unit))
                            else -> stringResource(Res.string.mastery_best_next, formatWeight(best, unit), stringResource(next.labelRes), formatWeight(nextKg, unit))
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val tier = lift.tier
                Text(
                    stringResource(tier?.labelRes ?: Res.string.mastery_no_tier),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (tier != null) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(if (tier != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
                )
            }
        }
    }
}

private const val BAR_MILLIS = 900
private val QUEST_BAR_HEIGHT = 6.dp
private val BAR_HEIGHT = 8.dp
