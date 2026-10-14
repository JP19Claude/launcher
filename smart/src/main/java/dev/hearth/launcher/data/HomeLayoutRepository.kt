package dev.hearth.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A folder on the home screen: its name and the apps in it (by key), in order. */
@Immutable
data class HomeFolderData(val id: String, val name: String, val apps: List<String>) {
    val key: String get() = "folder:$id"
}

/** A fixed place for an app on the home screen. */
@Immutable
data class CellPos(val page: Int, val col: Int, val row: Int)

/**
 * Where the user put apps on the home screen. Apps without a place (new installs)
 * fill the free cells in order.
 */
class HomeLayoutRepository(context: Context) {

    private val prefs = context.getSharedPreferences("hearth_layout", Context.MODE_PRIVATE)

    private val _positions = MutableStateFlow(read())
    val positions: StateFlow<Map<String, CellPos>> = _positions.asStateFlow()

    private val folderPrefs = context.getSharedPreferences("hearth_folders", Context.MODE_PRIVATE)
    private val _folders = MutableStateFlow(readFolders())
    val folders: StateFlow<List<HomeFolderData>> = _folders.asStateFlow()

    fun setFolders(folders: List<HomeFolderData>) {
        _folders.value = folders
        val text = folders.joinToString("\n") { f ->
            listOf(f.id, f.name.replace('\t', ' ').replace('\n', ' '), f.apps.joinToString(SEP.toString())).joinToString("\t")
        }
        folderPrefs.edit().putString("folders", text).apply()
    }

    private fun readFolders(): List<HomeFolderData> = folderPrefs.getString("folders", null)
        ?.split('\n')
        ?.mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size < 3) null else HomeFolderData(parts[0], parts[1], parts[2].split(SEP).filter { it.isNotBlank() })
        }
        ?: emptyList()

    fun set(positions: Map<String, CellPos>) {
        _positions.value = positions
        prefs.edit().clear().apply {
            positions.forEach { (key, p) -> putString(key, "${p.page},${p.col},${p.row}") }
        }.apply()
    }

    fun forget(key: String) {
        if (key !in _positions.value) return
        set(_positions.value - key)
    }

    fun reset() = set(emptyMap())

    private companion object {
        const val SEP = '\u001F'
    }

    private fun read(): Map<String, CellPos> = prefs.all.mapNotNull { (key, value) ->
        val n = (value as? String)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return@mapNotNull null
        if (n.size != 3) null else key to CellPos(n[0], n[1], n[2])
    }.toMap()
}
