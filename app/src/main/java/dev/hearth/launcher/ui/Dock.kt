package dev.hearth.launcher.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.hearth.launcher.data.AppInfo

@Composable
fun Dock(apps: List<AppInfo>, actions: AppActions, modifier: Modifier = Modifier) {
    if (apps.isEmpty()) return
    LiquidGlass(cornerRadius = 34.dp, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            apps.forEach { app ->
                key(app.key) {
                    AppIcon(app, actions, showLabel = false)
                }
            }
        }
    }
}
