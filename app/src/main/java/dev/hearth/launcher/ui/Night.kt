package dev.hearth.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.nightActive
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Is dark mode on right now? Looks at the clock once a minute (and at Android's theme). */
@Composable
fun rememberNight(settings: LauncherSettings): Boolean {
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - LocalTime.now().second * 1000L)
            hour = LocalTime.now().hour
        }
    }
    return settings.nightActive(hour, androidx.compose.foundation.isSystemInDarkTheme())
}

/**
 * Night mode for things floating over other apps (Glimmer): everything drawn gets darker,
 * while the transparent rest of the window stays untouched.
 */
fun Modifier.nightDim(on: Boolean, amount: Float = 0.4f): Modifier = if (!on) this else this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(Color.Black.copy(alpha = amount), blendMode = BlendMode.SrcAtop)
    }
