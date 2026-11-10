package dev.hearth.launcher

import android.app.Application
import dev.hearth.launcher.crash.CrashGuard

/**
 * ZENITH's application: it sets up the crash guard (an error is written down and shown, three in two minutes start the
 * safe mode) before anything else runs. The crash screen has a process of its own and doesn't catch its own errors.
 */
class HearthApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashGuard.load(this)
        val inCrashScreen = runCatching { Application.getProcessName().endsWith(":crash") }.getOrDefault(false)
        if (!inCrashScreen) CrashGuard.install(this)
        // Glimmer deeper in the system: if the user chose "keep Glimmer switched on" and Android switched its service off, it goes on again.
        if (!inCrashScreen) runCatching { dev.hearth.launcher.system.GlimmerDeep.heal(this) }
    }
}
