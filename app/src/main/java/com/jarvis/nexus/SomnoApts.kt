package com.jarvis.nexus

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.util.Random
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Appuntamenti SomnoDesk (Firebase RTDB via REST, /sd/apts.json), stesso formato del JARVIS desktop.
 * Le funzioni di rete sono BLOCCANTI: vanno chiamate fuori dal main thread (il progetto non usa coroutines).
 * Privacy: nessun Log, errori solo con codice HTTP, cache nella cartella privata dell'app.
 */
object SomnoApts {
    /** Tipi di prestazione, identici al desktop (somno-intake.js). */
    val TIPI = listOf(
        "Polisonnografia", "Titolazione APAP", "Saturimetria notturna", "Emogasanalisi arteriosa",
        "Visita di controllo", "Follow-up CPAP", "Consegna/ritiro dispositivo", "Altro",
    )

    /** [raw] e' il JSON originale: i campi sconosciuti non si perdono nelle modifiche (S3). */
    data class Apt(
        val id: String, val name: String, val type: String, val date: String, val time: String,
        val addr: String, val km: Double, val tel: String, val priority: String, val device: String,
        val maschera: String, val taglia: String, val note: String, val consCode: String,
        val done: Boolean, val updatedAt: Long, val raw: JSONObject,
    )

    data class Config(val dbUrl: String, val apiKey: String, val email: String, val password: String)
    data class Cache(val at: Long, val raw: List<JSONObject>) // raw include i _deleted
    data class Result(val ok: Boolean, val msg: String, val apts: List<Apt> = emptyList())

    private class HttpErr(val code: Int) : Exception("HTTP $code")

    private const val PREFS = "somno"
    private const val MAX_BODY = 4L * 1024 * 1024
    private const val CONNECT_MS = 8000
    private const val READ_MS = 20000
    private val DATE_RE = Regex("^\\d{4}-\\d{2}-\\d{2}$")
    private val TIME_RE = Regex("^\\d{2}:\\d{2}$")
    private val rnd = Random()

    // ---------- S1: modello, parse, validazione ----------

    fun fromJson(o: JSONObject) = Apt(
        id = o.optString("id"), name = o.optString("name"), type = o.optString("type"),
        date = o.optString("date"), time = o.optString("time"), addr = o.optString("addr"),
        km = o.optDouble("km", 0.0).let { if (it.isNaN()) 0.0 else it },
        tel = o.optString("tel"), priority = o.optString("priority", "media"), device = o.optString("device"),
        maschera = o.optString("maschera"), taglia = o.optString("taglia"), note = o.optString("note"),
        consCode = o.optString("cons_code"), done = o.optBoolean("done", false),
        updatedAt = o.optLong("updatedAt", 0L), raw = o,
    )

    /**
     * Array grezzo (inclusi i _deleted) da una risposta {d:"<array JSON>"} oppure array.
     * Lancia IllegalStateException("cifrato") se d inizia con "v2:".
     */
    fun parseRaw(body: String): List<JSONObject> {
        val t = body.trim()
        if (t == "null" || t.isEmpty()) return emptyList()
        var d: Any? = if (t.startsWith("[")) JSONArray(t) else JSONObject(t).opt("d")
        if (d is String) {
            if (d.startsWith("v2:")) throw IllegalStateException("cifrato")
            d = JSONArray(d)
        }
        val arr = d as? JSONArray ?: return emptyList()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    /** Appuntamenti non cancellati. */
    fun parse(body: String): List<Apt> = visible(parseRaw(body))

    fun visible(raw: List<JSONObject>): List<Apt> = raw.filter { !it.optBoolean("_deleted", false) }.map { fromJson(it) }

    /** Id come il desktop: base36(now) + 3 caratteri base36 casuali + indice. */
    fun newId(now: Long, index: Int): String {
        val sb = StringBuilder()
        repeat(3) { sb.append(Character.forDigit(rnd.nextInt(36), 36)) }
        return java.lang.Long.toString(now, 36) + sb + index
    }

    /** Null se valido, altrimenti il motivo. Obbligatori: name, date, time, addr. */
    fun validate(name: String, date: String, time: String, addr: String): String? = when {
        name.isBlank() -> "Manca il nome"
        !DATE_RE.matches(date) -> "Data non valida (AAAA-MM-GG)"
        !TIME_RE.matches(time) -> "Ora non valida (HH:mm)"
        addr.isBlank() -> "Manca l'indirizzo"
        else -> null
    }

    // ---------- S1: cache atomica ----------

    private fun cacheFile(ctx: Context) = File(File(ctx.filesDir, "somno").apply { mkdirs() }, "apts.json")

    @Synchronized
    fun saveCache(ctx: Context, raw: List<JSONObject>, at: Long = System.currentTimeMillis()) {
        val arr = JSONArray(); raw.forEach { arr.put(it) }
        val f = cacheFile(ctx)
        val tmp = File(f.parentFile, f.name + ".tmp")
        // cifrata con la chiave del Keystore (vedi seal): i dati dei pazienti non restano in chiaro sul telefono
        tmp.writeText(seal(JSONObject().put("at", at).put("apts", arr).toString()), Charsets.UTF_8)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) { tmp.delete(); throw IllegalStateException("scrittura locale fallita") } }
    }

    fun loadCache(ctx: Context): Cache? = try {
        val f = cacheFile(ctx)
        if (!f.exists()) null else {
            // una copia in chiaro di una versione precedente si legge ancora e viene cifrata al salvataggio successivo
            val j = JSONObject(unseal(f.readText(Charsets.UTF_8)))
            val arr = j.getJSONArray("apts")
            Cache(j.optLong("at", 0L), (0 until arr.length()).mapNotNull { arr.optJSONObject(it) })
        }
    } catch (e: Exception) { null }

    /** Appuntamenti dalla copia locale (vuoto se non c'e'). */
    fun cached(ctx: Context): List<Apt> = loadCache(ctx)?.let { visible(it.raw) } ?: emptyList()

    /** Momento (ms) dell'ultima lettura riuscita, 0 se mai. */
    fun lastRead(ctx: Context): Long = loadCache(ctx)?.at ?: 0L

    // ---------- S2: configurazione e auth ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Password e refresh token cifrati con una chiave AES-GCM del Keystore Android (non esportabile).
    private const val KEY_ALIAS = "jarvis_somno"
    private const val SEALED = "k1:"

    // lock dedicato: con @Synchronized sull'object il main thread resterebbe bloccato mentre token() fa rete
    private val keyLock = Any()

    private fun secretKey(): SecretKey = synchronized(keyLock) {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val old = ks.getKey(KEY_ALIAS, null) as? SecretKey
        if (old != null) old else {
            val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            g.init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
            g.generateKey()
        }
    }

    private fun seal(s: String): String {
        if (s.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secretKey())
        return SEALED + Base64.encodeToString(c.iv + c.doFinal(s.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    /** Valore in chiaro di una versione precedente restituito com'è; "" se non decifrabile (chiave persa). */
    private fun unseal(v: String?): String {
        if (v.isNullOrEmpty()) return ""
        if (!v.startsWith(SEALED)) return v
        return try {
            val b = Base64.decode(v.substring(SEALED.length), Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, b, 0, 12))
            String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { "" }
    }

    /** Legge un segreto; se era salvato in chiaro lo ricifra subito. */
    private fun secret(ctx: Context, key: String): String {
        val p = prefs(ctx)
        val v = p.getString(key, null)
        val plain = unseal(v)
        if (!v.isNullOrEmpty() && !v.startsWith(SEALED)) {
            try { p.edit().putString(key, seal(plain)).apply() } catch (e: Exception) { }
        }
        return plain
    }

    fun config(ctx: Context): Config {
        val p = prefs(ctx)
        return Config(
            p.getString("dbUrl", "")?.trim()?.trimEnd('/') ?: "", p.getString("apiKey", "")?.trim() ?: "",
            p.getString("email", "")?.trim() ?: "", secret(ctx, "password"),
        )
    }

    /** Cambiando credenziali i token vecchi vengono scartati. */
    fun saveConfig(ctx: Context, c: Config) {
        prefs(ctx).edit().putString("dbUrl", c.dbUrl.trim().trimEnd('/')).putString("apiKey", c.apiKey.trim())
            .putString("email", c.email.trim()).putString("password", seal(c.password))
            .remove("idToken").remove("refreshToken").remove("expiresAt").apply()
    }

    // senza decifrare la password: viene chiamata dalla UI (main thread)
    fun isConfigured(ctx: Context): Boolean = prefs(ctx).let { p ->
        (p.getString("dbUrl", "") ?: "").trim().startsWith("https://") && (p.getString("apiKey", "") ?: "").trim().isNotEmpty()
    }

    private fun http(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = CONNECT_MS; c.readTimeout = READ_MS
            c.useCaches = false; c.instanceFollowRedirects = false
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json")
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            if (code !in 200..299) throw HttpErr(code)
            val out = ByteArrayOutputStream()
            c.inputStream.use { inp ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = inp.read(buf); if (r < 0) break
                    out.write(buf, 0, r)
                    if (out.size() > MAX_BODY) throw IllegalStateException("risposta troppo grande")
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    private fun saveTokens(ctx: Context, idToken: String, refreshToken: String, expiresIn: Long) {
        if (idToken.isEmpty()) throw IllegalStateException("token mancante")
        val ed = prefs(ctx).edit().putString("idToken", idToken)
            .putLong("expiresAt", System.currentTimeMillis() + (expiresIn - 60) * 1000)
        if (refreshToken.isNotEmpty()) ed.putString("refreshToken", seal(refreshToken))
        ed.apply()
    }

    private fun login(ctx: Context, c: Config) {
        // email senza password = password non più decifrabile (chiave persa): niente ripiego silenzioso sull'anonimo
        if (c.email.isNotEmpty() && c.password.isEmpty()) throw IllegalStateException("Password SomnoDesk da reinserire nelle Impostazioni")
        val anon = c.email.isEmpty()
        val body = JSONObject().put("returnSecureToken", true)
        if (!anon) body.put("email", c.email).put("password", c.password)
        val ep = if (anon) "signUp" else "signInWithPassword"
        val j = JSONObject(String(http("POST", "https://identitytoolkit.googleapis.com/v1/accounts:$ep?key=${enc(c.apiKey)}",
            body.toString().toByteArray(Charsets.UTF_8), "application/json"), Charsets.UTF_8))
        saveTokens(ctx, j.optString("idToken"), j.optString("refreshToken"), j.optString("expiresIn").toLongOrNull() ?: 3600L)
    }

    private fun refresh(ctx: Context, c: Config, rt: String): Boolean = try {
        val form = "grant_type=refresh_token&refresh_token=${enc(rt)}"
        val j = JSONObject(String(http("POST", "https://securetoken.googleapis.com/v1/token?key=${enc(c.apiKey)}",
            form.toByteArray(Charsets.UTF_8), "application/x-www-form-urlencoded"), Charsets.UTF_8))
        saveTokens(ctx, j.optString("id_token"), j.optString("refresh_token"), j.optString("expires_in").toLongOrNull() ?: 3600L)
        true
    } catch (e: Exception) { false }

    /** Token valido; con [force] ignora la scadenza e rinnova (refresh, poi login completo). */
    @Synchronized
    private fun token(ctx: Context, force: Boolean): String {
        val c = config(ctx)
        if (!isConfigured(ctx)) throw IllegalStateException("Somno non configurato")
        val p = prefs(ctx)
        val cur = p.getString("idToken", null)
        if (!force && cur != null && System.currentTimeMillis() < p.getLong("expiresAt", 0L)) return cur
        val rt = secret(ctx, "refreshToken")
        if (rt.isEmpty() || !refresh(ctx, c, rt)) login(ctx, c)
        return p.getString("idToken", null) ?: throw IllegalStateException("token mancante")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    /** GET autenticato con un solo rinnovo del token su 401. */
    private fun authGet(ctx: Context, path: String): ByteArray {
        val db = config(ctx).dbUrl
        return try {
            http("GET", "$db$path?auth=${enc(token(ctx, false))}")
        } catch (e: HttpErr) {
            if (e.code != 401) throw e
            http("GET", "$db$path?auth=${enc(token(ctx, true))}")
        }
    }

    /** Legge tutti gli appuntamenti dal DB e aggiorna la cache. BLOCCANTE. Su errore la cache resta intatta. */
    fun readAll(ctx: Context): Result {
        return try {
            val raw = parseRaw(String(authGet(ctx, "/sd/apts.json"), Charsets.UTF_8))
            if (raw.isNotEmpty() || loadCache(ctx)?.raw.isNullOrEmpty()) try { saveCache(ctx, raw) } catch (e: Exception) { } // lettura riuscita comunque
            Result(true, "ok", visible(raw))
        } catch (e: HttpErr) {
            Result(false, "Errore HTTP ${e.code}", cached(ctx))
        } catch (e: java.io.IOException) {
            Result(false, "Rete non raggiungibile", cached(ctx))
        } catch (e: IllegalStateException) {
            Result(false, if (e.message == "cifrato") "Dati cifrati (v2): non supportati" else (e.message ?: "errore"), cached(ctx))
        } catch (e: Exception) {
            Result(false, "Risposta non valida", cached(ctx))
        }
    }

    // ---------- S4: streaming (Firebase SSE, come watch() del desktop) ----------

    enum class Live { OFFLINE, ONLINE, ERROR }

    /** Stato osservabile da Compose; si aggiorna sempre sul main thread. */
    var liveApts by mutableStateOf<List<Apt>>(emptyList()); private set
    var status by mutableStateOf(Live.OFFLINE); private set
    var updatedAt by mutableStateOf(0L); private set

    private const val IDLE_MS = 90_000
    private const val RETRY_MS = 5_000L
    private const val MAX_RETRY_MS = 300_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var liveThread: Thread? = null
    @Volatile private var liveConn: HttpURLConnection? = null

    private fun publish(apts: List<Apt>, st: Live, at: Long = System.currentTimeMillis()) {
        main.post { liveApts = apts; status = st; updatedAt = at }
    }

    private fun setStatus(st: Live) { main.post { status = st } }

    /** Applica un evento put/patch di Firebase al valore locale {d,t}. */
    private fun applyEvent(root: JSONObject?, ev: String, path: String, data: Any?): JSONObject? {
        val keys = path.split('/').filter { it.isNotEmpty() }
        val patch = ev == "patch"
        fun merge(old: Any?, d: Any?): Any? {
            if (!patch || d !is JSONObject) return d
            val n = if (old is JSONObject) copy(old) else JSONObject()
            d.keys().forEach { k -> n.put(k, d.get(k)) }
            return n
        }
        if (keys.isEmpty()) return merge(root, data) as? JSONObject
        val out = if (root != null) copy(root) else JSONObject()
        var node = out
        for (k in keys.dropLast(1)) {
            val next = node.optJSONObject(k)?.let { copy(it) } ?: JSONObject()
            node.put(k, next); node = next
        }
        val last = keys.last()
        node.put(last, merge(node.opt(last), data) ?: JSONObject.NULL)
        return out
    }

    /** Avvia lo streaming (thread daemon). Subito mostra la cache; idempotente. */
    fun startLive(ctx: Context) {
        if (liveThread != null) return
        val app = ctx.applicationContext
        val t = Thread { liveLoop(app) }
        t.isDaemon = true
        liveThread = t
        t.start()
    }

    fun stopLive() {
        val t = liveThread ?: return
        liveThread = null
        t.interrupt()
        try { liveConn?.disconnect() } catch (e: Exception) { }
        setStatus(Live.OFFLINE)
    }

    private fun liveLoop(ctx: Context) {
        val me = Thread.currentThread()
        fun stopped() = liveThread !== me
        loadCache(ctx)?.let { publish(visible(it.raw), Live.OFFLINE, it.at) }
        var delay = RETRY_MS
        var force = false
        var root: JSONObject? = null
        while (!stopped()) {
            var revoked = false
            try {
                val db = config(ctx).dbUrl
                val c = URL("$db/sd/apts.json?auth=${enc(token(ctx, force))}").openConnection() as HttpURLConnection
                force = false
                liveConn = c
                if (stopped()) { c.disconnect(); return }
                try {
                    c.requestMethod = "GET"
                    c.connectTimeout = CONNECT_MS; c.readTimeout = IDLE_MS // watchdog: i keep-alive arrivano ogni ~30 s
                    c.useCaches = false; c.instanceFollowRedirects = false
                    c.setRequestProperty("Accept", "text/event-stream")
                    val code = c.responseCode
                    if (code == 401) force = true
                    if (code !in 200..299) throw HttpErr(code)
                    var ev = "message"; val data = StringBuilder()
                    val r = c.inputStream.bufferedReader(Charsets.UTF_8)
                    try {
                        while (!stopped()) {
                            val line = r.readLine() ?: break
                            if (line.isEmpty()) {
                                val d = data.toString(); val e = ev
                                ev = "message"; data.setLength(0)
                                if (e == "auth_revoked") { revoked = true; break }
                                if (e == "cancel") break
                                if (e != "put" && e != "patch") continue
                                try {
                                    val m = JSONObject(d)
                                    root = applyEvent(root, e, m.optString("path", "/"), m.opt("data"))
                                    val raw = parseRaw((root ?: JSONObject()).toString())
                                    // DB vuoto: non svuoto la cache, così la guardia sulle scritture resta attiva
                                    if (raw.isNotEmpty() || loadCache(ctx)?.raw.isNullOrEmpty()) try { saveCache(ctx, raw) } catch (x2: Exception) { }
                                    delay = RETRY_MS
                                    publish(visible(raw), Live.ONLINE)
                                } catch (x: IllegalStateException) { setStatus(Live.ERROR)
                                } catch (x: org.json.JSONException) { /* evento non valido: ignoro */ }
                            } else if (line.startsWith("event:")) ev = line.substring(6).trim()
                            else if (line.startsWith("data:")) {
                                data.append(line.substring(5).trim())
                                if (data.length > MAX_BODY) throw java.io.IOException("evento troppo grande")
                            }
                        }
                    } finally { r.close() }
                } finally { if (liveConn === c) liveConn = null; c.disconnect() }
            } catch (e: InterruptedException) {
                return
            } catch (e: java.io.IOException) {
                if (!stopped()) setStatus(Live.OFFLINE)
            } catch (e: Exception) {
                if (!stopped()) setStatus(Live.ERROR)
            }
            if (stopped()) return
            if (revoked) { force = true; root = null; continue }
            root = null
            try { Thread.sleep(delay) } catch (e: InterruptedException) { return }
            delay = minOf(delay * 2, MAX_RETRY_MS)
        }
    }

    // ---------- S8: comando chat locale in sola lettura ----------

    private val GIORNI = listOf("domenica", "lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato")

    /** Risposta locale a "appuntamenti di oggi/domani/..." ; null se la frase non e' pertinente. [n] minuscolo. */
    fun answer(ctx: Context, n: String): String? {
        if (n.length > 80 || !(n.contains("appuntamenti") || n.contains("agenda somnodesk"))) return null
        if (Regex("\\b(aggiungi|inserisci|elimina|cancella|togli|sposta)\\b").containsMatchIn(n))
            return "Aggiungere, modificare o eliminare appuntamenti si fa dalla schermata SOMNO."
        if (!isConfigured(ctx)) return "SomnoDesk non configurato"
        val cal = java.util.Calendar.getInstance()
        var label = "oggi"
        val dm = Regex("\\b(\\d{1,2})/(\\d{1,2})\\b").find(n)
        val wd = GIORNI.indexOfFirst { n.contains(it) || n.contains(it.dropLast(1) + "i") }
        when {
            dm != null -> {
                val d = dm.groupValues[1].toInt(); val m = dm.groupValues[2].toInt()
                if (d !in 1..31 || m !in 1..12) return "Data non valida"
                cal.set(cal.get(java.util.Calendar.YEAR), m - 1, d)
                label = "%02d/%02d".format(d, m)
            }
            n.contains("dopodomani") -> { cal.add(java.util.Calendar.DAY_OF_MONTH, 2); label = "dopodomani" }
            n.contains("domani") -> { cal.add(java.util.Calendar.DAY_OF_MONTH, 1); label = "domani" }
            wd >= 0 -> {
                var add = (wd - (cal.get(java.util.Calendar.DAY_OF_WEEK) - 1) + 7) % 7
                if (add == 0) add = 7
                cal.add(java.util.Calendar.DAY_OF_MONTH, add); label = GIORNI[wd]
            }
        }
        val day = "%04d-%02d-%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH))
        val cache = loadCache(ctx)
        val src = if (liveApts.isNotEmpty()) liveApts else (cache?.let { visible(it.raw) } ?: emptyList())
        val list = src.filter { it.date == day }.sortedBy { it.time }
        if (list.isEmpty()) return "Nessun appuntamento ($label)"
        val sb = StringBuilder("Appuntamenti $label:")
        list.forEach { sb.append("\n").append(it.time).append(" ").append(it.name).append(" — ").append(it.type).append(", ").append(it.addr) }
        if (status != Live.ONLINE) {
            val at = if (updatedAt > 0) updatedAt else (cache?.at ?: 0L)
            if (at > 0) sb.append("\n(dati di ").append(java.text.SimpleDateFormat("HH:mm", java.util.Locale.ITALIAN).format(java.util.Date(at))).append(")")
        }
        return sb.toString()
    }

    // ---------- S3: scrittura (ETag + if-match, come addApts/deleteApts del desktop) ----------

    /** [done]/[duplicates] sono i record toccati / rifiutati; [apts] la lista visibile dopo la scrittura. */
    data class WriteResult(
        val ok: Boolean, val msg: String, val done: List<Apt> = emptyList(),
        val duplicates: List<Apt> = emptyList(), val apts: List<Apt> = emptyList(),
    )

    private class Plan(val all: List<JSONObject>?, val done: List<Apt>, val dup: List<Apt>)

    private class Raw(val code: Int, val etag: String?, val body: ByteArray)

    /** Come [http] ma restituisce anche i codici non 2xx (serve il 412) e l'ETag; headers extra opzionali. */
    private fun httpRaw(method: String, url: String, headers: Map<String, String>, body: ByteArray? = null): Raw {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = CONNECT_MS; c.readTimeout = READ_MS
            c.useCaches = false; c.instanceFollowRedirects = false
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val etag = c.getHeaderField("ETag")
            if (code !in 200..299) return Raw(code, etag, ByteArray(0))
            val out = ByteArrayOutputStream()
            c.inputStream.use { inp ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = inp.read(buf); if (r < 0) break
                    out.write(buf, 0, r)
                    if (out.size() > MAX_BODY) throw IllegalStateException("risposta troppo grande")
                }
            }
            return Raw(code, etag, out.toByteArray())
        } finally { c.disconnect() }
    }

    /** Richiesta autenticata con un solo rinnovo del token su 401. */
    private fun authRaw(ctx: Context, method: String, path: String, headers: Map<String, String>, body: ByteArray? = null): Raw {
        val db = config(ctx).dbUrl
        val r = httpRaw(method, "$db$path?auth=${enc(token(ctx, false))}", headers, body)
        if (r.code != 401) return r
        return httpRaw(method, "$db$path?auth=${enc(token(ctx, true))}", headers, body)
    }

    private fun copy(o: JSONObject) = JSONObject(o.toString())

    private fun sameApt(x: JSONObject, name: String, date: String, time: String) =
        !x.optBoolean("_deleted", false) && x.optString("name").trim().equals(name.trim(), ignoreCase = true) &&
            x.optString("date") == date && x.optString("time") == time

    /** Ciclo lettura(ETag) -> [plan] -> PUT if-match; su 412 rilegge, max 3 tentativi. BLOCCANTE. */
    private fun write(ctx: Context, plan: (List<JSONObject>) -> Plan): WriteResult {
        return try {
            repeat(3) {
                val g = authRaw(ctx, "GET", "/sd/apts.json", mapOf("X-Firebase-ETag" to "true"))
                if (g.code !in 200..299) throw HttpErr(g.code)
                val etag = g.etag?.takeIf { it.isNotEmpty() } ?: return WriteResult(false, "ETag mancante, scrittura annullata")
                val list = parseRaw(String(g.body, Charsets.UTF_8))
                val p = plan(list)
                val all = p.all ?: return WriteResult(true, "nessuna modifica", p.done, p.dup, visible(list))
                val stamp = System.currentTimeMillis()
                val arr = JSONArray(); all.forEach { arr.put(it) }
                val body = JSONObject().put("d", arr.toString()).put("t", stamp).toString().toByteArray(Charsets.UTF_8)
                val w = authRaw(ctx, "PUT", "/sd/apts.json", mapOf("if-match" to etag), body)
                if (w.code == 412) return@repeat // modificato altrove: rileggo
                if (w.code !in 200..299) throw HttpErr(w.code)
                try { saveCache(ctx, all) } catch (e: Exception) { } // il PUT è riuscito: la cache si riallinea dallo stream
                publish(visible(all), Live.ONLINE.takeIf { liveThread != null } ?: status)
                return WriteResult(true, "ok", p.done, p.dup, visible(all))
            }
            WriteResult(false, "Archivio modificato da un altro dispositivo, riprova", apts = cached(ctx))
        } catch (e: HttpErr) {
            WriteResult(false, "Errore HTTP ${e.code}", apts = cached(ctx))
        } catch (e: java.io.IOException) {
            WriteResult(false, "Rete non raggiungibile", apts = cached(ctx))
        } catch (e: IllegalStateException) {
            WriteResult(false, if (e.message == "cifrato") "Dati cifrati (v2): scrittura annullata" else (e.message ?: "errore"), apts = cached(ctx))
        } catch (e: Exception) {
            WriteResult(false, "Risposta non valida", apts = cached(ctx))
        }
    }

    /** Aggiunge appuntamenti nuovi (usa name, type, date, time, addr, km, tel, note); id e resto generati. */
    fun add(ctx: Context, apts: List<Apt>): WriteResult {
        if (apts.isEmpty()) return WriteResult(false, "Nessun appuntamento")
        apts.forEach { validate(it.name, it.date, it.time, it.addr)?.let { m -> return WriteResult(false, m) } }
        val hadCache = cached(ctx).isNotEmpty()
        return write(ctx) { list ->
            if (list.isEmpty() && hadCache) throw IllegalStateException("Archivio remoto vuoto, scrittura annullata")
            val dups = apts.filter { a -> list.any { sameApt(it, a.name, a.date, a.time) } }
            val fresh = apts.filter { it !in dups }
            if (fresh.isEmpty()) return@write Plan(null, emptyList(), dups)
            val stamp = System.currentTimeMillis()
            val recs = fresh.mapIndexed { k, a ->
                JSONObject().put("id", newId(stamp, k)).put("name", a.name.trim())
                    .put("type", a.type.ifEmpty { "Altro" }).put("date", a.date).put("time", a.time)
                    .put("addr", a.addr.trim()).put("km", if (a.km > 0) a.km else 0)
                    .put("tel", a.tel).put("priority", "media").put("device", "").put("maschera", "")
                    .put("taglia", "").put("note", a.note).put("cons_code", "").put("done", false)
                    .put("updatedAt", stamp)
            }
            Plan(list.map { copy(it) } + recs, recs.map { fromJson(it) }, dups)
        }
    }

    /** Modifica un appuntamento per id, facendo merge sul JSON grezzo (campi sconosciuti conservati). */
    fun update(ctx: Context, apt: Apt): WriteResult {
        validate(apt.name, apt.date, apt.time, apt.addr)?.let { return WriteResult(false, it) }
        return write(ctx) { list ->
            if (list.isEmpty()) throw IllegalStateException("Archivio remoto vuoto, modifica annullata")
            val stamp = System.currentTimeMillis()
            var hit: JSONObject? = null
            val all = list.map { x ->
                if (hit == null && !x.optBoolean("_deleted", false) && x.optString("id") == apt.id) {
                    val n = copy(x)
                    n.put("name", apt.name.trim()).put("type", apt.type).put("date", apt.date).put("time", apt.time)
                        .put("addr", apt.addr.trim()).put("km", apt.km).put("tel", apt.tel)
                        .put("note", apt.note).put("updatedAt", stamp) // solo i campi del form: gli altri restano quelli remoti
                    hit = n; n
                } else copy(x)
            }
            val h = hit ?: throw IllegalStateException("Appuntamento non trovato")
            Plan(all, listOf(fromJson(h)), emptyList())
        }
    }

    /** Cancellazione soft (_deleted:true, updatedAt=ora) per id. */
    fun delete(ctx: Context, ids: List<String>): WriteResult {
        val want = ids.filter { it.isNotEmpty() }.toSet()
        if (want.isEmpty()) return WriteResult(false, "Nessun appuntamento da cancellare")
        return write(ctx) { list ->
            if (list.isEmpty()) throw IllegalStateException("Archivio remoto vuoto, cancellazione annullata")
            val stamp = System.currentTimeMillis()
            val done = ArrayList<Apt>()
            val all = list.map { x ->
                val n = copy(x)
                if (!x.optBoolean("_deleted", false) && x.optString("id") in want) {
                    done.add(fromJson(x)); n.put("_deleted", true).put("updatedAt", stamp)
                }
                n
            }
            if (done.isEmpty()) Plan(null, emptyList(), emptyList()) else Plan(all, done, emptyList())
        }
    }
}
