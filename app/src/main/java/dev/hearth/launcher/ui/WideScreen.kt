package dev.hearth.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** From this width (dp) on the screen is a computer's: an Android laptop, a big tablet held wide, a window pulled wide. */
const val WIDE_SCREEN_DP = 840

/** True on a computer's wide screen: the launcher uses its room (more columns, content in the middle) instead of stretching a phone's layout. */
@Composable
fun isWideScreen(): Boolean = LocalConfiguration.current.screenWidthDp >= WIDE_SCREEN_DP

/**
 * The side gutter that keeps a column of content at most [maxContent] wide and in the middle of a wide screen; the
 * [base] gutter of a phone otherwise.
 */
@Composable
fun wideGutter(base: Dp, maxContent: Dp = 760.dp): Dp {
    val screen = LocalConfiguration.current.screenWidthDp
    return if (screen > maxContent.value + 2f * base.value) ((screen - maxContent.value) / 2f).dp else base
}
