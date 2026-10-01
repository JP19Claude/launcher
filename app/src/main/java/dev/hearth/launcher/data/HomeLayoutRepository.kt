package dev.hearth.launcher.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

    private fun read(): Map<String, CellPos> = prefs.all.mapNotNull { (key, value) ->
        val n = (value as? String)?.split(',')?.mapNotNull { it.toIntOrNull() } ?: return@mapNotNull null
        if (n.size != 3) null else key to CellPos(n[0], n[1], n[2])
    }.toMap()
}
