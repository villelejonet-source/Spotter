package com.viktorolsson.spotter.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Particle(val angle: Float, val speed: Float, val spin: Float, val colorIndex: Int, val width: Float)

/**
 * A short, decorative confetti burst from the top centre. Restarts whenever [key]
 * changes. Not announced by screen readers (the caller announces the reason).
 */
@Composable
fun Confetti(key: Any, modifier: Modifier = Modifier, durationMillis: Int = 2200) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
    )
    val particles = remember(key) {
        val random = Random(key.hashCode())
        List(70) {
            Particle(
                angle = (-160 + random.nextFloat() * 140).toRadians(),
                speed = 900f + random.nextFloat() * 900f,
                spin = random.nextFloat() * 720f - 360f,
                colorIndex = random.nextInt(colors.size),
                width = 10f + random.nextFloat() * 10f,
            )
        }
    }
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { progress.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }
    Canvas(modifier.fillMaxSize()) {
        val t = progress.value * durationMillis / 1000f
        val origin = Offset(size.width / 2, size.height * 0.08f)
        particles.forEach { p ->
            val x = origin.x + cos(p.angle) * p.speed * t * 0.6f
            val y = origin.y + sin(p.angle) * p.speed * t * 0.6f + 900f * t * t
            val alpha = (1f - progress.value).coerceIn(0f, 1f)
            rotate(p.spin * t, pivot = Offset(x, y)) {
                drawRect(
                    color = colors[p.colorIndex].copy(alpha = alpha),
                    topLeft = Offset(x - p.width / 2, y - p.width / 4),
                    size = Size(p.width, p.width / 2),
                )
            }
        }
    }
}

private fun Float.toRadians() = this * (Math.PI / 180).toFloat()
