package dev.hearth.launcher.data

import android.content.ComponentName
import android.os.UserHandle
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

/** How an icon bitmap was rendered, so the UI knows how to frame it. */
enum class IconKind {
    /** Full icon with background, square: the UI clips it to its shape. */
    Shaped,

    /** Icon from an icon pack with its own outline: drawn as it is. */
    Free,

    /** Colored symbol on a transparent background, for glass tiles. */
    Glyph,

    /** Single-color symbol (Android 13 themed icon layer), for glass tiles. */
    MonoGlyph,
}

@Immutable
data class AppInfo(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: ImageBitmap,
    val iconKind: IconKind = IconKind.Shaped,
    /** When the app was first installed, for "Neu hinzugefügt". */
    val installTime: Long = 0L,
    /** Folder in the App Library. */
    val category: LibraryCategory = LibraryCategory.Other,
) {
    val packageName: String get() = component.packageName

    /** Stable id, unique across work and personal profiles. */
    val key: String get() = "${component.flattenToShortString()}@${user.hashCode()}"
}
