package dev.hearth.launcher.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dev.hearth.launcher.LauncherViewModel
import dev.hearth.launcher.data.AppInfo
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private const val COLUMNS = 4
private const val ROWS = 6
private const val FIRST_PAGE_ROWS = 4

@Composable
fun LauncherScreen(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val dock by vm.dock.collectAsStateWithLifecycle()
    val backdrop by vm.backdrop.collectAsStateWithLifecycle()
    val needsWallpaperAccess by vm.needsWallpaperAccess.collectAsStateWithLifecycle()
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val storagePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { vm.refreshWallpaper() }
    val requestWallpaperAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: "All files access" is the only way a launcher may read the wallpaper.
            val appSpecific = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { context.startActivity(appSpecific) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else {
            storagePermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    val homeApps = remember(apps, dock) {
        val dockKeys = dock.map { it.key }.toSet()
        apps.filterNot { it.key in dockKeys }
    }
    val pages = remember(homeApps) { paginate(homeApps) }
    val pagerState = rememberPagerState(pageCount = { pages.size })

    val actions = remember(vm) {
        AppActions(launch = vm::launch, info = vm::openAppInfo, uninstall = vm::uninstall)
    }

    // Home button: close search and jump back to the first page.
    LaunchedEffect(Unit) {
        vm.homeEvents.collect {
            searchOpen = false
            pagerState.animateScrollToPage(0)
        }
    }

    // Always enabled: on the home screen, back does nothing (like every launcher).
    BackHandler { searchOpen = false }

    CompositionLocalProvider(LocalBackdrop provides backdrop) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.28f),
                    0.35f to Color.Transparent,
                    0.8f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.22f),
                ),
            )
            .openSearchOnVerticalSwipe { searchOpen = true },
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            HorizontalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                    if (page == 0) {
                        HomeHeader(Modifier.padding(start = 12.dp, top = 28.dp, bottom = 20.dp))
                        if (needsWallpaperAccess) {
                            WallpaperAccessHint(
                                onClick = requestWallpaperAccess,
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 16.dp),
                            )
                        }
                    } else {
                        Spacer(Modifier.padding(top = 16.dp))
                    }
                    AppGrid(pages[page], actions)
                }
            }

            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (pages.size > 1) {
                    PageDots(
                        count = pages.size,
                        current = pagerState.currentPage,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
                SearchPill(onClick = { searchOpen = true })
            }

            Dock(
                apps = dock,
                actions = actions,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
            )
        }

        AnimatedVisibility(
            visible = searchOpen,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { -it / 10 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { -it / 10 },
        ) {
            SearchOverlay(
                apps = apps,
                actions = actions,
                onLaunch = {
                    vm.launch(it)
                    searchOpen = false
                },
                onWebSearch = {
                    vm.webSearch(it)
                    searchOpen = false
                },
                onDismiss = { searchOpen = false },
            )
        }
    }
    }
}

/** First page holds the clock, so it has fewer rows of apps. */
private fun paginate(apps: List<AppInfo>): List<List<AppInfo>> {
    val firstPageSize = COLUMNS * FIRST_PAGE_ROWS
    val rest = apps.drop(firstPageSize).chunked(COLUMNS * ROWS)
    return listOf(apps.take(firstPageSize)) + rest
}

/** Swipe up or down anywhere on the home screen opens search. */
private fun Modifier.openSearchOnVerticalSwipe(onOpen: () -> Unit): Modifier =
    pointerInput(Unit) {
        val threshold = 64.dp.toPx()
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (abs(total) > threshold) onOpen() },
            onVerticalDrag = { _, dragAmount -> total += dragAmount },
        )
    }

@Composable
private fun rememberNow(): State<LocalDateTime> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState(LocalDateTime.now(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = LocalDateTime.now()
                value = now
                val msToNextMinute = 60_000L - (now.second * 1_000L + now.nano / 1_000_000L)
                delay(msToNextMinute.coerceAtLeast(500L))
            }
        }
    }
}

@Composable
private fun HomeHeader(modifier: Modifier = Modifier) {
    val now by rememberNow()
    val locale = Locale.getDefault()
    val time = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm", locale)) }
    val date = remember(now) {
        now.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", locale))
            .replaceFirstChar { it.titlecase(locale) }
    }
    val greeting = when (now.hour) {
        in 5..10 -> "Guten Morgen"
        in 11..17 -> "Guten Tag"
        in 18..22 -> "Guten Abend"
        else -> "Gute Nacht"
    }

    Column(modifier) {
        Text(
            text = greeting,
            color = Color.White.copy(alpha = 0.85f),
            fontFamily = FontFamily.Serif,
            fontStyle = FontStyle.Italic,
            fontSize = 20.sp,
            style = OnWallpaperText,
        )
        Text(
            text = time,
            color = Color.White,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Light,
            fontSize = 76.sp,
            lineHeight = 80.sp,
            style = OnWallpaperText,
        )
        Text(
            text = date,
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 16.sp,
            style = OnWallpaperText,
        )
    }
}

@Composable
private fun AppGrid(apps: List<AppInfo>, actions: AppActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        apps.chunked(COLUMNS).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { app ->
                    key(app.key) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            AppIcon(app, actions)
                        }
                    }
                }
                repeat(COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index == current) 0.95f else 0.4f)),
            )
        }
    }
}

/** Glass capsule; swells slightly while pressed, like liquid glass. */
@Composable
private fun SearchPill(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 1.08f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pillPress",
    )
    LiquidGlass(
        cornerRadius = 20.dp,
        refraction = 8.dp,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text("Suchen", color = Color.White, fontSize = 14.sp, style = OnWallpaperText)
        }
    }
}

/** Shown until the launcher may read the wallpaper; without it, glass can only be frosted. */
@Composable
private fun WallpaperAccessHint(onClick: () -> Unit, modifier: Modifier = Modifier) {
    LiquidGlass(
        cornerRadius = 20.dp,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Echtes Glas aktivieren",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                style = OnWallpaperText,
            )
            Text(
                text = "Tippen und Zugriff auf alle Dateien erlauben, damit das Glas dein Hintergrundbild zeigen kann.",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                style = OnWallpaperText,
            )
        }
    }
}
