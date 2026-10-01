package dev.hearth.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.hearth.launcher.data.LauncherSettings
import dev.hearth.launcher.data.LockLayout
import dev.hearth.launcher.data.NotificationHub
import dev.hearth.launcher.data.ShadeNotice
import dev.hearth.launcher.ui.theme.HearthTheme
import kotlinx.coroutines.delay

/**
 * Notifications on the lock screen like on the iPhone (drawn over it by the accessibility
 * service): stacked at the bottom, or as a count, or as a list. Tap the stack to fan it out,
 * tap a notification to open it (after unlocking), swipe it left to clear it.
 */
@Composable
fun LockScreenNotifications(settings: LauncherSettings) {
    val light = rememberGlassLight(false)
    HearthTheme(dark = true) {
        CompositionLocalProvider(
            LocalSettings provides settings,
            LocalGlassStyle provides controlCenterGlass(GlassStyle.from(settings), settings),
            LocalGlassLight provides light,
            LocalBackdrop provides null,
        ) {
            LockNotificationList(settings)
        }
    }
}

@Composable
private fun LockNotificationList(settings: LauncherSettings) {
    val context = LocalContext.current
    val all by NotificationHub.all.collectAsStateWithLifecycle()
    val notices = remember(all, settings.lockShowContent) {
        all.mapNotNull { it.forLockScreen(settings.lockShowContent) }
    }
    var expanded by remember { mutableStateOf(settings.lockNotifications == LockLayout.List) }
    var confirmClear by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000)
            now = System.currentTimeMillis()
        }
    }
    LaunchedEffect(confirmClear) {
        if (confirmClear) {
            delay(3000)
            confirmClear = false
        }
    }
    LaunchedEffect(notices.size) {
        if (notices.size <= 1 && settings.lockNotifications != LockLayout.List) expanded = false
    }
    if (notices.isEmpty()) return

    val open: (ShadeNotice) -> Unit = { notice -> NotificationHub.open(context, notice) }
    val options: (ShadeNotice) -> Unit = { notice ->
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, notice.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.5f).dp

    AnimatedContent(
        targetState = expanded || settings.lockNotifications == LockLayout.List,
        transitionSpec = {
            (fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.8f, stiffness = 500f), initialScale = 0.96f))
                .togetherWith(fadeOut(tween(120)))
        },
        label = "lockNotifications",
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    ) { listOpen ->
        if (listOpen) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)) {
                    Text(
                        "Mitteilungen",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        style = OnWallpaperText,
                        modifier = Modifier.weight(1f),
                    )
                    if (settings.lockNotifications != LockLayout.List) {
                        IosPill("Weniger") { expanded = false }
                        Spacer(Modifier.width(8.dp))
                    }
                    if (notices.any { it.clearable }) {
                        IosClearButton(label = if (confirmClear) "Alle löschen" else null) {
                            if (confirmClear) {
                                NotificationHub.dismissAll()
                                confirmClear = false
                            } else {
                                confirmClear = true
                            }
                        }
                    }
                }
                Column(
                    Modifier
                        .heightIn(max = maxHeight)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    notices.forEach { notice ->
                        androidx.compose.runtime.key(notice.key) {
                            NotificationCard(
                                notice = notice,
                                now = now,
                                onOpen = { open(notice) },
                                onAction = { action -> NotificationHub.send(context, action.intent) },
                                onOptions = { options(notice) },
                                onDismiss = { NotificationHub.dismiss(notice.key) },
                            )
                        }
                    }
                }
            }
        } else if (settings.lockNotifications == LockLayout.Count && notices.size > 1) {
            // "3 Mitteilungen" as a small glass pill; tap shows them.
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { expanded = true }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(
                        "${notices.size} Mitteilungen",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        } else {
            // The newest one, the others peeking out below; tap fans them out.
            val top = notices.first()
            val more = notices.size - 1
            Box(Modifier.padding(bottom = if (more > 1) 16.dp else if (more == 1) 8.dp else 0.dp)) {
                if (more > 1) StackLayer(Modifier.matchParentSize().padding(horizontal = 26.dp).offset(y = 16.dp))
                if (more > 0) StackLayer(Modifier.matchParentSize().padding(horizontal = 13.dp).offset(y = 8.dp))
                NotificationCard(
                    notice = top,
                    now = now,
                    more = more,
                    onOpen = { if (more > 0) expanded = true else open(top) },
                    onAction = { action -> NotificationHub.send(context, action.intent) },
                    onOptions = { options(top) },
                    onDismiss = { NotificationHub.dismiss(top.key) },
                )
            }
        }
    }
}
