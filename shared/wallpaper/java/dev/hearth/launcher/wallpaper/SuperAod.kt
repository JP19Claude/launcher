package dev.hearth.launcher.wallpaper

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextClock
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/*
 * The always-on screen. Android does not let an app draw on the phone's real always-on display: the system's AOD is shown while the
 * screen is "dozing", and a live wallpaper is only told about it (and only shown) if it holds a permission that the system hands out to
 * the maker's own apps and nobody else (android.permission.AMBIENT_WALLPAPER – a wallpaper that asks for it without having it is
 * refused by Android). So this is the closest an app can come: a black screen that stays on, very dim, with the body turning slowly on it
 * (dimmed, moving a few pixels now and then so that nothing burns in) and the time under it. It runs as an activity ("AOD screen", started
 * from the settings, a tap leaves it) and as a screen saver (Android starts it by itself while the phone charges or sits in a dock, once it
 * was chosen in the system's settings).
 */
object SuperAod {

    /** The brightness of the display while it runs – low; the picture is dimmed further by the setting. */
    const val SCREEN_BRIGHTNESS = 0.10f

    /** The activity ends by itself after this long (the battery's and the screen's sake). */
    const val MAX_MS = 6L * 60L * 60L * 1000L

    /** How often the time moves a little (against burn-in), and how far (dp). */
    private const val SHIFT_EVERY_MS = 25_000L
    private const val SHIFT_DP = 18f

    /**
     * The screen's picture for [p]: black; the body (if [SuperParams.aod] is on) turning in the middle; the time (if [SuperParams.aodClock] is
     * on, or when there is no body); a tap calls [onTap].
     */
    fun build(context: Context, p: SuperParams, photoFile: File?, onTap: (() -> Unit)?): View {
        val density = context.resources.displayMetrics.density
        val root = FrameLayout(context).apply { setBackgroundColor(Color.BLACK) }
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        if (p.aod) {
            val view = SuperLiveView(context, photoFile, ambient = true)
            view.setParams(p)
            root.addView(view, FrameLayout.LayoutParams(match, match))
        }
        val showClock = p.aodClock || !p.aod
        if (showClock) {
            val alpha = (p.aodDim.coerceIn(0.1f, 0.8f) * 1.3f).coerceIn(0.15f, 1f)
            val clock = TextClock(context).apply {
                format24Hour = "HH:mm"
                format12Hour = "h:mm"
                setTextColor(Color.argb((alpha * 255f).toInt(), 235, 240, 255))
                typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, if (p.aod) 56f else 84f)
                includeFontPadding = false
            }
            val gravity = if (p.aod) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER
            root.addView(
                clock,
                FrameLayout.LayoutParams(wrap, wrap, gravity).apply { if (p.aod) bottomMargin = (110f * density).toInt() },
            )
            // The time wanders a little every now and then, in a slow circle.
            val handler = Handler(Looper.getMainLooper())
            var step = 0
            val mover = object : Runnable {
                override fun run() {
                    step++
                    val a = step * 1.9f
                    clock.animate()
                        .translationX(SHIFT_DP * density * sin(a))
                        .translationY(SHIFT_DP * density * cos(a * 0.7f))
                        .setDuration(2500L)
                        .start()
                    handler.postDelayed(this, SHIFT_EVERY_MS)
                }
            }
            root.addOnAttachStateChangeListener(
                object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) {
                        handler.postDelayed(mover, SHIFT_EVERY_MS)
                    }

                    override fun onViewDetachedFromWindow(v: View) {
                        handler.removeCallbacks(mover)
                    }
                },
            )
        }
        if (onTap != null) root.setOnClickListener { onTap() }
        return root
    }
}

/**
 * The always-on screen as an activity: the screen stays on, dim, fills the whole display, shows over the lock screen; a tap leaves it. The
 * app that includes it names the settings and the picture file (see ZenithAodActivity and SceneAodActivity).
 */
open class SuperAodActivity : Activity() {

    protected open fun params(): SuperParams = SuperParams()

    protected open fun photoFile(): File? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setShowWhenLocked(true)
        val attributes = window.attributes
        attributes.screenBrightness = SuperAod.SCREEN_BRIGHTNESS
        attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        window.attributes = attributes
        hideBars()
        setContentView(SuperAod.build(this, params(), photoFile()) { finish() })
        handler.postDelayed({ finish() }, SuperAod.MAX_MS)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }
}

/**
 * The always-on screen as a screen saver ("Bildschirmschoner"): Android starts it by itself when the phone charges or sits in a dock
 * (once it is chosen in the system's settings), keeps the display on at a low brightness, and ends it on a touch.
 */
open class SuperDream : DreamService() {

    protected open fun params(): SuperParams = SuperParams()

    protected open fun photoFile(): File? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = false
        setContentView(SuperAod.build(this, params(), photoFile(), null))
    }
}
