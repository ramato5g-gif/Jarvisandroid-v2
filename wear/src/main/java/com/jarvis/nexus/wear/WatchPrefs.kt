package com.jarvis.nexus.wear

import android.content.Context
import androidx.wear.tiles.TileService

/** Stato condiviso tra app, tile e quadrante: ultima risposta e stato del collegamento col telefono. */
object WatchPrefs {
    private fun p(c: Context) = c.getSharedPreferences("jarvis_watch", Context.MODE_PRIVATE)

    fun last(c: Context): String = p(c).getString("last", "") ?: ""

    /** null = mai verificato, true = telefono risponde, false = nessuna risposta. */
    fun link(c: Context): Boolean? = p(c).let { if (it.contains("link")) it.getBoolean("link", false) else null }

    fun saveLast(c: Context, text: String) {
        p(c).edit().putString("last", text.replace(Regex("\\s+"), " ").trim().take(200)).apply()
        try { TileService.getUpdater(c).requestUpdate(JarvisTileService::class.java) } catch (_: Exception) { }
    }

    fun saveLink(c: Context, ok: Boolean) { p(c).edit().putBoolean("link", ok).apply() }
}
