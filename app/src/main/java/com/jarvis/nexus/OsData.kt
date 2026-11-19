package com.jarvis.nexus

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.CalendarContract
import android.provider.Settings
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.TimeZone

/**
 * JARVIS OS — dati per la modalità launcher (tablet): app, file, calendario, meteo, nodi di rete.
 * Tutto locale; l'unica chiamata esterna è il meteo (Open-Meteo, senza chiave).
 */
object OsData {

    // ───────────── Modalità launcher ─────────────
    fun launcherComponent(ctx: Context) = ComponentName(ctx, LauncherActivity::class.java)

    fun isLauncherEnabled(ctx: Context): Boolean =
        ctx.packageManager.getComponentEnabledSetting(launcherComponent(ctx)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    /** Attiva/disattiva la home JARVIS OS. Sul telefono resta spenta: nessun cambio di launcher. */
    fun setLauncherEnabled(ctx: Context, on: Boolean) {
        ctx.packageManager.setComponentEnabledSetting(
            launcherComponent(ctx),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    fun isTablet(ctx: Context): Boolean = ctx.resources.configuration.smallestScreenWidthDp >= 600

    // ───────────── App ─────────────
    data class App(val label: String, val pkg: String, val icon: Bitmap?)

    fun loadApps(ctx: Context): List<App> {
        val pm = ctx.packageManager
        return pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { ri ->
                App(ri.loadLabel(pm).toString(), ri.activityInfo.packageName,
                    try { ri.loadIcon(pm).toBitmap(128, 128) } catch (e: Exception) { null })
            }
            .sortedBy { it.label.lowercase(Locale.ITALIAN) }
    }

    fun launch(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    }

    fun appInfo(ctx: Context, pkg: String) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    // ───────────── File ─────────────
    val root: File get() = Environment.getExternalStorageDirectory()

    fun hasFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun filesAccessIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))

    fun list(dir: File): List<File> =
        (dir.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
            .sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ITALIAN) }))

    /** Cerca per nome nella memoria condivisa (salta Android/ e cartelle nascoste). */
    fun search(query: String, limit: Int = 30): List<File> {
        val q = query.trim().lowercase(Locale.ITALIAN)
        if (q.isEmpty() || !hasFilesAccess()) return emptyList()
        val skip = File(root, "Android").absolutePath
        return root.walkTopDown().maxDepth(8)
            .onEnter { !it.name.startsWith(".") && it.absolutePath != skip }
            .filter { it.isFile && it.name.lowercase(Locale.ITALIAN).contains(q) }
            .take(limit).toList()
    }

    fun resolve(path: String): File = File(path).let { if (it.isAbsolute) it else File(root, path) }

    fun open(ctx: Context, f: File): Boolean = try {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.reports", f)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "*/*"
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) { false }

    fun size(f: File): String {
        val b = f.length().toDouble()
        return when {
            b >= 1e9 -> "%.1f GB".format(Locale.ITALIAN, b / 1e9)
            b >= 1e6 -> "%.1f MB".format(Locale.ITALIAN, b / 1e6)
            b >= 1e3 -> "%.0f KB".format(Locale.ITALIAN, b / 1e3)
            else -> "${f.length()} B"
        }
    }

    // ───────────── Calendario ─────────────
    data class Event(val title: String, val begin: Long, val end: Long, val allDay: Boolean, val location: String)

    fun hasCalendar(ctx: Context) =
        ctx.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun upcoming(ctx: Context, hours: Int = 48): List<Event> {
        if (!hasCalendar(ctx)) return emptyList()
        val now = System.currentTimeMillis()
        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, now)
        ContentUris.appendId(b, now + hours * 3_600_000L)
        val proj = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION)
        val out = mutableListOf<Event>()
        try {
            ctx.contentResolver.query(b.build(), proj, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext() && out.size < 20) {
                    out.add(Event(c.getString(0) ?: "(senza titolo)", c.getLong(1), c.getLong(2), c.getInt(3) == 1, c.getString(4) ?: ""))
                }
            }
        } catch (e: Exception) { }
        return out
    }

    /** Inserisce direttamente l'evento nel primo calendario scrivibile; null se non ce n'è uno (o manca il permesso). */
    fun insertEvent(ctx: Context, title: String, begin: Long, end: Long): Long? {
        if (ctx.checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val calId = try {
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), null)?.use { c ->
                var first: Long? = null
                var primary: Long? = null
                while (c.moveToNext()) {
                    if (first == null) first = c.getLong(0)
                    if (primary == null && c.getInt(1) == 1) primary = c.getLong(0)
                }
                primary ?: first
            }
        } catch (e: Exception) { null } ?: return null
        val v = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, begin)
            put(CalendarContract.Events.DTEND, end)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        return try { ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, v)?.lastPathSegment?.toLongOrNull() }
        catch (e: Exception) { null }
    }

    // ───────────── Meteo (Open-Meteo) ─────────────
    data class Day(val max: Double, val min: Double, val code: Int)
    data class Weather(val temp: Double, val code: Int, val wind: Double, val humidity: Int, val days: List<Day>, val at: Long)

    fun weather(lat: Double, lon: Double): Weather {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code,wind_speed_10m,relative_humidity_2m" +
            "&daily=temperature_2m_max,temperature_2m_min,weather_code&timezone=auto&forecast_days=3")
        val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8000; readTimeout = 10000 }
        try {
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val cur = o.getJSONObject("current")
            val d = o.getJSONObject("daily")
            val mx = d.getJSONArray("temperature_2m_max"); val mn = d.getJSONArray("temperature_2m_min"); val wc = d.getJSONArray("weather_code")
            val days = (0 until mx.length()).map { Day(mx.getDouble(it), mn.getDouble(it), wc.getInt(it)) }
            return Weather(cur.getDouble("temperature_2m"), cur.getInt("weather_code"), cur.getDouble("wind_speed_10m"),
                cur.optInt("relative_humidity_2m"), days, System.currentTimeMillis())
        } finally { c.disconnect() }
    }

    fun describe(code: Int): String = when (code) {
        0 -> "Sereno"
        1, 2 -> "Poco nuvoloso"
        3 -> "Coperto"
        45, 48 -> "Nebbia"
        51, 53, 55, 56, 57 -> "Pioviggine"
        61, 63, 65, 66, 67 -> "Pioggia"
        71, 73, 75, 77 -> "Neve"
        80, 81, 82 -> "Rovesci"
        85, 86 -> "Rovesci di neve"
        95, 96, 99 -> "Temporale"
        else -> "—"
    }

    // ───────────── Nodi (PC, Pi, servizi di casa) ─────────────
    data class Node(val name: String, val url: String, val online: Boolean?, val ms: Long = 0)

    /** Righe "Nome|http://indirizzo:porta/percorso". */
    fun parseNodes(txt: String): List<Node> = txt.lines().mapNotNull { l ->
        val p = l.split("|")
        if (p.size >= 2 && p[1].trim().startsWith("http")) Node(p[0].trim(), p[1].trim(), null) else null
    }

    fun ping(n: Node): Node {
        val t0 = System.currentTimeMillis()
        return try {
            val c = (URL(n.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500; readTimeout = 2500; requestMethod = "GET"; instanceFollowRedirects = false
            }
            val code = try { c.responseCode } finally { c.disconnect() }
            n.copy(online = code in 1..499, ms = System.currentTimeMillis() - t0)
        } catch (e: Exception) { n.copy(online = false) }
    }

    // ───────────── Luminosità ─────────────
    fun canWriteSettings(ctx: Context) = Settings.System.canWrite(ctx)

    fun setBrightness(ctx: Context, pct: Int): Boolean {
        if (!canWriteSettings(ctx)) return false
        val v = (pct.coerceIn(1, 100) * 255 / 100)
        Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        return Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, v)
    }

    fun writeSettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
}
