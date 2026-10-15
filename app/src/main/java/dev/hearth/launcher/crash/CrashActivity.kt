package dev.hearth.launcher.crash

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/**
 * What ZENITH shows after it crashed – in a process of its own, built from plain views, so nothing that may have gone
 * wrong in ZENITH can go wrong here. The report to copy or share, and the ways back: the normal start, the safe mode, and –
 * pressed twice – ZENITH's settings deleted.
 */
class CrashActivity : Activity() {

    private var sure = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashGuard.report(this) ?: "Es gibt keinen Bericht."
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF101216.toInt())
            setPadding(dp(18), dp(44), dp(18), dp(18))
        }
        root.addView(
            TextView(this).apply {
                text = "ZENITH ist abgestürzt"
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                setTypeface(typeface, Typeface.BOLD)
            },
        )
        root.addView(
            TextView(this).apply {
                text = "Unten steht, was kaputt gegangen ist. Kopier den Bericht (oder teil ihn) und schick ihn mit – dann lässt es sich beheben. " +
                    "Mit „Abgesichert starten“ läuft ZENITH ohne seine optionalen Looks, Zeremonien und ohne Super-Wallpaper."
                setTextColor(0xFFB9C1CC.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, dp(8), 0, dp(12))
            },
        )
        val trace = TextView(this).apply {
            text = report
            setTextColor(0xFFC9D1D9.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            setBackgroundColor(0xFF1A1E25.toInt())
        }
        root.addView(
            ScrollView(this).apply { addView(trace) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        fun button(label: String, onClick: (Button) -> Unit): Button = Button(this).apply {
            text = label
            isAllCaps = false
            setOnClickListener { onClick(this) }
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(
            button("Bericht kopieren") {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("ZENITH-Absturzbericht", report))
                Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        row1.addView(
            button("Teilen") {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, report)
                runCatching { startActivity(Intent.createChooser(send, "Absturzbericht teilen")) }
            },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
        )
        root.addView(row1)
        root.addView(button("Abgesichert starten") { CrashGuard.setSafe(this, true); relaunch() })
        root.addView(button("Normal starten") { CrashGuard.setSafe(this, false); relaunch() })
        root.addView(
            button("Einstellungen von ZENITH zurücksetzen") { me ->
                if (!sure) {
                    sure = true
                    me.text = "Wirklich? Alle Einstellungen gehen verloren – nochmal tippen"
                } else {
                    CrashGuard.resetSettings(this)
                    relaunch()
                }
            },
        )
        setContentView(root, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT))
        root.visibility = View.VISIBLE
    }

    /** Back into ZENITH. */
    private fun relaunch() {
        val intent = Intent().setClassName(packageName, "dev.hearth.launcher.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        runCatching { startActivity(intent) }
        finish()
    }
}
