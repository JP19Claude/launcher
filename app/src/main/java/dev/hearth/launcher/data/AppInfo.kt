package dev.hearth.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppInfo(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
) {
    val packageName: String get() = component.packageName

    /** Stable id, unique across work and personal profiles. */
    val key: String get() = "${component.flattenToShortString()}@${user.hashCode()}"
}
