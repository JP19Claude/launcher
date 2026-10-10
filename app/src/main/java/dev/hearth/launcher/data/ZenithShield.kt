package dev.hearth.launcher.data

import android.Manifest
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * ZENITH 19, the Schutzschild: how well the phone is protected, at a glance – the screen lock,
 * the security patch, developer options and USB debugging, encryption, ZENITH's own vault, app
 * lock and backup – and which apps may use the camera, microphone, location and more.
 *
 * It only looks: ZENITH changes nothing by itself here. Each point leads to the place in
 * Android's settings where you change it yourself.
 */
object ZenithShield {

    enum class Level { Good, Warn, Bad }

    /** One point checked: what, what was found, how good, and where to change it. */
    class Check(
        val title: String,
        val detail: String,
        val level: Level,
        val action: String? = null,
        val intent: Intent? = null,
    )

    /** What an app may use, by the permissions Android has granted it. */
    enum class Access(val label: String, val permissions: List<String>) {
        Camera("Kamera", listOf(Manifest.permission.CAMERA)),
        Microphone("Mikrofon", listOf(Manifest.permission.RECORD_AUDIO)),
        Location("Standort", listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)),
        AlwaysLocation("Standort (immer)", listOf("android.permission.ACCESS_BACKGROUND_LOCATION")),
        Contacts("Kontakte", listOf(Manifest.permission.READ_CONTACTS)),
        Sms("SMS", listOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)),
        Calls("Anrufliste", listOf(Manifest.permission.READ_CALL_LOG)),
        Calendar("Kalender", listOf(Manifest.permission.READ_CALENDAR)),
        Body("Körpersensoren", listOf(Manifest.permission.BODY_SENSORS)),
        Nearby("Geräte in der Nähe", listOf("android.permission.BLUETOOTH_SCAN", "android.permission.NEARBY_WIFI_DEVICES")),
    }

    class AppAccess(val packageName: String, val label: String, val access: Set<Access>)

    private fun settings(action: String): Intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The app's page in Android's settings (to take permissions back). */
    fun appSettings(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun checks(context: Context): List<Check> {
        val list = mutableListOf<Check>()
        val resolver = context.contentResolver

        val keyguard = context.getSystemService(KeyguardManager::class.java)
        list += if (keyguard?.isDeviceSecure == true) {
            Check("Bildschirmsperre", "PIN, Muster oder Passwort ist eingerichtet.", Level.Good)
        } else {
            Check(
                "Bildschirmsperre",
                "Keine Sperre: Wer das Handy in der Hand hat, kommt an alles.",
                Level.Bad,
                "Einrichten",
                settings(Settings.ACTION_SECURITY_SETTINGS),
            )
        }

        val patch = runCatching { LocalDate.parse(Build.VERSION.SECURITY_PATCH) }.getOrNull()
        if (patch != null) {
            val days = ChronoUnit.DAYS.between(patch, LocalDate.now())
            val months = (days / 30).coerceAtLeast(0)
            val level = when {
                days <= 100 -> Level.Good
                days <= 200 -> Level.Warn
                else -> Level.Bad
            }
            list += Check(
                "Sicherheitsupdate",
                "Stand ${Build.VERSION.SECURITY_PATCH}" + when (level) {
                    Level.Good -> " – aktuell."
                    Level.Warn -> " – $months Monate alt. Schau, ob es ein Update gibt."
                    Level.Bad -> " – über $months Monate alt."
                },
                level,
                if (level == Level.Good) null else "Nach Updates suchen",
                if (level == Level.Good) null else settings("android.settings.SYSTEM_UPDATE_SETTINGS"),
            )
        }

        val developer = runCatching { Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }.getOrDefault(false)
        val adb = runCatching { Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1 }.getOrDefault(false)
        val shizuku = runCatching { ShizukuBridge.isReady(context) }.getOrDefault(false)
        list += when {
            adb -> Check(
                "USB-Debugging",
                if (shizuku) "An – Shizuku braucht es. Schalte es aus, wenn du Shizuku gerade nicht nutzt." else "An: Über ein Kabel kann ein Computer tief ins Handy. Aus, wenn du es nicht brauchst.",
                if (shizuku) Level.Warn else Level.Bad,
                "Entwickleroptionen",
                settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            )
            developer -> Check(
                "Entwickleroptionen",
                "Eingeschaltet. Harmlos, solange du dort nichts Unbekanntes änderst.",
                Level.Warn,
                "Öffnen",
                settings(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            )
            else -> Check("Entwickleroptionen", "Aus – so soll es sein.", Level.Good)
        }

        val encryption = runCatching { context.getSystemService(DevicePolicyManager::class.java)?.storageEncryptionStatus }.getOrNull()
        list += if (encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE ||
            encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_PER_USER ||
            encryption == DevicePolicyManager.ENCRYPTION_STATUS_ACTIVE_DEFAULT_KEY
        ) {
            Check("Speicher verschlüsselt", "Android verschlüsselt alles auf dem Handy.", Level.Good)
        } else {
            Check("Speicher verschlüsselt", "Nicht festzustellen.", Level.Warn, "Sicherheit", settings(Settings.ACTION_SECURITY_SETTINGS))
        }

        list += if (ZenithVault.exists(context)) {
            Check("ZENITH-Tresor", "Eingerichtet – deine privaten Fotos und Dateien sind verschlüsselt.", Level.Good)
        } else {
            Check(
                "ZENITH-Tresor",
                "Noch nicht eingerichtet: Private Fotos und Dateien liegen offen in der Galerie.",
                Level.Warn,
                "Einrichten",
                Intent(context, dev.hearth.launcher.VaultActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }

        val locked = runCatching { SettingsRepository(context).settings.value.lockedApps.size }.getOrDefault(0)
        list += if (locked > 0) {
            Check("App-Sperre", "$locked ${if (locked == 1) "App ist" else "Apps sind"} gesperrt.", Level.Good)
        } else {
            Check("App-Sperre", "Keine App gesperrt. Banking, Chats, Galerie? Einstellungen → App-Sperre.", Level.Warn)
        }

        val last = context.getSharedPreferences("omega_cloud", Context.MODE_PRIVATE).getLong("last", 0L)
        val days = if (last > 0L) (System.currentTimeMillis() - last) / 86_400_000L else -1L
        list += when {
            days in 0..30 -> Check("Sicherung", "Vor ${if (days == 0L) "weniger als einem Tag" else "$days Tagen"} gesichert.", Level.Good)
            days > 30 -> Check("Sicherung", "Zuletzt vor $days Tagen. Einstellungen → ZENITH Cloud.", Level.Warn)
            else -> Check("Sicherung", "Noch nie über ZENITH Cloud gesichert. Einstellungen → ZENITH Cloud.", Level.Warn)
        }
        return list
    }

    /** 0..100: how well protected, from the [checks]. */
    fun score(checks: List<Check>): Int =
        (100 - checks.sumOf { if (it.level == Level.Bad) 25 else if (it.level == Level.Warn) 8 else 0 }).coerceIn(0, 100)

    /** Every app with an icon and what Android lets it use (only what's actually granted). */
    fun apps(context: Context): List<AppAccess> {
        val pm = context.packageManager
        val launchable = runCatching {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        return launchable
            .filter { it != context.packageName }
            .mapNotNull { pkg ->
                val info: PackageInfo = runCatching {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, PackageManager.GET_PERMISSIONS)
                }.getOrNull() ?: return@mapNotNull null
                val requested = info.requestedPermissions ?: return@mapNotNull null
                val flags = info.requestedPermissionsFlags ?: return@mapNotNull null
                val granted = requested.indices
                    .filter { i -> i < flags.size && flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 }
                    .map { requested[it] }
                    .toSet()
                val access = Access.entries.filter { a -> a.permissions.any { it in granted } }.toSet()
                if (access.isEmpty()) return@mapNotNull null
                val label = runCatching { info.applicationInfo?.loadLabel(pm)?.toString() }.getOrNull() ?: pkg
                AppAccess(pkg, label, access)
            }
            .sortedWith(compareByDescending<AppAccess> { it.access.size }.thenBy { it.label.lowercase() })
    }
}
