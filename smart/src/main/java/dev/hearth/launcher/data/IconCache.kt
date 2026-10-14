package dev.hearth.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.UserManager
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

/**
 * Keeps the rendered app list on disk. When Android has closed the launcher in the
 * background, the home screen can show all icons right away from here, instead of
 * waiting for every icon to be rendered again; the fresh list replaces it moments later.
 */
class IconCache(context: Context) {

    private val dir = File(context.noBackupFilesDir, "icon-cache")
    private val index = File(dir, "index.tsv")
    private val users = context.getSystemService(UserManager::class.java)

    /** The cached list, if it was made with the same icon settings ([signature]). */
    fun read(signature: String): List<AppInfo>? = runCatching {
        if (!index.exists()) return null
        val lines = index.readLines()
        if (lines.firstOrNull() != signature) return null
        lines.drop(1).mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < FIELDS) return@mapNotNull null
            val component = ComponentName.unflattenFromString(f[0]) ?: return@mapNotNull null
            val user = users?.getUserForSerialNumber(f[1].toLongOrNull() ?: return@mapNotNull null)
                ?: return@mapNotNull null
            val bitmap = BitmapFactory.decodeFile(File(dir, f[6]).path) ?: return@mapNotNull null
            AppInfo(
                label = f[2],
                component = component,
                user = user,
                icon = bitmap.asImageBitmap(),
                iconKind = runCatching { IconKind.valueOf(f[3]) }.getOrDefault(IconKind.Shaped),
                installTime = f[4].toLongOrNull() ?: 0L,
                category = runCatching { LibraryCategory.valueOf(f[5]) }.getOrDefault(LibraryCategory.Other),
            )
        }.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun write(signature: String, apps: List<AppInfo>) {
        runCatching {
            dir.mkdirs()
            val files = HashSet<String>()
            val text = StringBuilder(signature).append('\n')
            apps.forEach { app ->
                val serial = users?.getSerialNumberForUser(app.user) ?: 0L
                val name = "${(app.component.flattenToString() + "@" + serial).hashCode().toUInt()}.png"
                files += name
                File(dir, name).outputStream().use { out ->
                    app.icon.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                text.append(app.component.flattenToString()).append('\t')
                    .append(serial).append('\t')
                    .append(app.label.replace('\t', ' ').replace('\n', ' ')).append('\t')
                    .append(app.iconKind.name).append('\t')
                    .append(app.installTime).append('\t')
                    .append(app.category.name).append('\t')
                    .append(name).append('\n')
            }
            // Write the index last and in one go, so a half-written cache is never read.
            val tmp = File(dir, "index.tmp")
            tmp.writeText(text.toString())
            tmp.renameTo(index)
            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".png") && file.name !in files) file.delete()
            }
        }
    }

    private companion object {
        const val FIELDS = 7
    }
}
