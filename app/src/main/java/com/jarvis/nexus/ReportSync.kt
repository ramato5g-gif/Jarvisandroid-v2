package com.jarvis.nexus

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

/**
 * Report mensili Sapio dal JARVIS desktop, in SOLA LETTURA.
 * Il telefono fa solo richieste GET al PC (Wi-Fi di casa) e salva copie nella cartella privata dell'app:
 * non invia dati, non modifica né cancella nulla sul PC. Una copia locale viene sostituita solo
 * dopo un download completo e valido; i mesi spariti dal PC restano sul telefono.
 * Sicurezza: HTTPS con impronta del certificato verificata (letta dal QR) + token.
 */
object ReportSync {
    data class Pairing(val host: String, val port: Int, val token: String, val fp: String)
    data class MonthInfo(val month: String, val n: Int, val totale: Double?, val hasExcel: Boolean)
    data class Result(val ok: Boolean, val msg: String, val changed: Int = 0)

    private const val PREFS = "report_sync"
    private const val MAX_JSON = 4L * 1024 * 1024
    private const val MAX_XLSX = 30L * 1024 * 1024
    private val MONTH_RE = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    private val MESI = listOf("Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno", "Luglio",
        "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun dir(ctx: Context): File = File(ctx.filesDir, "reports").apply { mkdirs() }
    fun excelFile(ctx: Context, month: String) = File(dir(ctx), "$month.xlsx")
    private fun monthFile(ctx: Context, month: String) = File(dir(ctx), "$month.json")

    fun monthLabel(month: String): String {
        val p = month.split("-")
        return "${MESI.getOrElse((p.getOrNull(1)?.toIntOrNull() ?: 1) - 1) { "?" }} ${p[0]}"
    }

    /** Legge il link del QR mostrato dal desktop ("abbina telefono report"). */
    fun parsePairing(raw: String): Pairing? {
        val u = try { Uri.parse(raw.trim()) } catch (e: Exception) { return null }
        if (u.scheme != "jarvisreport" || u.host != "pair") return null
        val host = u.getQueryParameter("h") ?: return null
        val port = u.getQueryParameter("p")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val token = u.getQueryParameter("t")?.takeIf { Regex("^[0-9a-f]{32,128}$").matches(it) } ?: return null
        val fp = u.getQueryParameter("fp")?.lowercase()?.takeIf { Regex("^[0-9a-f]{64}$").matches(it) } ?: return null
        if (!Regex("^[0-9A-Za-z.:-]{1,64}$").matches(host)) return null
        return Pairing(host, port, token, fp)
    }

    fun savePairing(ctx: Context, p: Pairing) {
        prefs(ctx).edit().putString("host", p.host).putInt("port", p.port).putString("token", p.token).putString("fp", p.fp).apply()
    }

    fun pairing(ctx: Context): Pairing? {
        val sp = prefs(ctx)
        val host = sp.getString("host", null) ?: return null
        val token = sp.getString("token", null) ?: return null
        val fp = sp.getString("fp", null) ?: return null
        return Pairing(host, sp.getInt("port", 8767), token, fp)
    }

    /** Toglie solo l'abbinamento; le copie già scaricate restano. */
    fun unpair(ctx: Context) { prefs(ctx).edit().remove("host").remove("port").remove("token").remove("fp").apply() }

    fun lastSync(ctx: Context): String? = prefs(ctx).getLong("last_ok", 0L).takeIf { it > 0 }
        ?.let { SimpleDateFormat("d MMM HH:mm", Locale.ITALIAN).format(Date(it)) }
    fun lastError(ctx: Context): String? = prefs(ctx).getString("last_err", null)

    /** Accetta solo il certificato con l'impronta SHA-256 del QR (il PC usa un certificato self-signed). */
    private fun pinned(p: Pairing, path: String): HttpsURLConnection {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) { throw CertificateException("solo server") }
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                val leaf = chain.firstOrNull() ?: throw CertificateException("nessun certificato")
                val fp = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(fp.toByteArray(), p.fp.toByteArray())) throw CertificateException("certificato del PC cambiato")
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf(tm), null) }
        val host = if (p.host.contains(':')) "[${p.host}]" else p.host
        return (URL("https://$host:${p.port}$path").openConnection() as HttpsURLConnection).apply {
            sslSocketFactory = ssl.socketFactory
            hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true } // identità garantita dall'impronta
            requestMethod = "GET"
            connectTimeout = 6000; readTimeout = 20000
            useCaches = false
            setRequestProperty("X-Jarvis-Token", p.token)
        }
    }

    private fun get(p: Pairing, path: String, max: Long): ByteArray {
        val c = pinned(p, path)
        try {
            val code = c.responseCode
            if (code == 403) throw IllegalStateException("Codice non più valido: riabbina il telefono dal QR del PC.")
            if (code != 200) throw IllegalStateException("Il PC ha risposto $code su $path")
            val out = ByteArrayOutputStream()
            c.inputStream.use { inp ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val r = inp.read(buf); if (r < 0) break
                    out.write(buf, 0, r)
                    if (out.size() > max) throw IllegalStateException("File troppo grande")
                }
            }
            return out.toByteArray()
        } finally { c.disconnect() }
    }

    /** Scrive su file temporaneo e poi rinomina: una copia buona non viene mai rovinata a metà. */
    private fun writeAtomic(f: File, data: ByteArray) {
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeBytes(data)
        if (!tmp.renameTo(f)) { f.delete(); if (!tmp.renameTo(f)) { tmp.delete(); throw IllegalStateException("scrittura locale fallita") } }
    }

    /** Scarica dal PC solo ciò che è cambiato. Sicuro da chiamare spesso. */
    @Synchronized
    fun sync(ctx: Context): Result {
        val p = pairing(ctx) ?: return Result(false, "Telefono non abbinato: sul PC scrivi \"abbina telefono report\" e scansiona il QR.")
        return try {
            val idx = JSONObject(String(get(p, "/api/v1/index", MAX_JSON), Charsets.UTF_8))
            val months = idx.optJSONArray("months") ?: JSONArray()
            val known = prefs(ctx)
            var changed = 0
            for (i in 0 until months.length()) {
                val m = months.getJSONObject(i)
                val month = m.optString("month")
                if (!MONTH_RE.matches(month)) continue
                val reg = m.optJSONObject("registro")
                val regStamp = reg?.let { "${it.optLong("mtime")}/${it.optLong("size")}" }
                if (regStamp != null && (regStamp != known.getString("reg_$month", null) || !monthFile(ctx, month).exists())) {
                    val body = get(p, "/api/v1/month/$month", MAX_JSON)
                    val j = JSONObject(String(body, Charsets.UTF_8)) // valida prima di sostituire
                    if (j.optString("month") != month || j.optJSONArray("rows") == null) throw IllegalStateException("Dati di $month non validi")
                    writeAtomic(monthFile(ctx, month), body)
                    known.edit().putString("reg_$month", regStamp).apply(); changed++
                }
                val xl = m.optJSONObject("excel")
                val xlStamp = xl?.let { "${it.optLong("mtime")}/${it.optLong("size")}" }
                if (xlStamp != null && (xlStamp != known.getString("xl_$month", null) || !excelFile(ctx, month).exists())) {
                    val body = get(p, "/api/v1/excel/$month", MAX_XLSX)
                    // un .xlsx è uno ZIP: deve iniziare con "PK"
                    if (body.size < 4 || body[0] != 'P'.code.toByte() || body[1] != 'K'.code.toByte()) throw IllegalStateException("Excel di $month non valido")
                    writeAtomic(excelFile(ctx, month), body)
                    known.edit().putString("xl_$month", xlStamp).apply(); changed++
                }
            }
            writeAtomic(File(dir(ctx), "index.json"), idx.toString().toByteArray())
            known.edit().putLong("last_ok", System.currentTimeMillis()).remove("last_err").apply()
            Result(true, if (changed == 0) "Report già aggiornati." else "Report aggiornati: $changed file scaricati.", changed)
        } catch (e: Exception) {
            val msg = when (e) {
                is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.NoRouteToHostException ->
                    "PC non raggiungibile: serve la stessa rete Wi-Fi e JARVIS desktop acceso."
                is javax.net.ssl.SSLException -> "Connessione rifiutata: il certificato del PC non corrisponde. Riabbina dal QR."
                else -> e.message ?: "errore sconosciuto"
            }
            prefs(ctx).edit().putString("last_err", msg).apply()
            Result(false, msg)
        }
    }

    /** Mesi disponibili sul telefono (dalle copie locali). */
    fun localMonths(ctx: Context): List<MonthInfo> {
        val idx = try { JSONObject(File(dir(ctx), "index.json").readText()).optJSONArray("months") } catch (e: Exception) { null }
        val fromIdx = mutableMapOf<String, MonthInfo>()
        if (idx != null) for (i in 0 until idx.length()) {
            val m = idx.getJSONObject(i); val month = m.optString("month")
            if (MONTH_RE.matches(month)) fromIdx[month] = MonthInfo(month, m.optInt("n"),
                if (m.isNull("totale")) null else m.optDouble("totale"), excelFile(ctx, month).exists())
        }
        dir(ctx).listFiles()?.forEach { f ->
            val month = f.name.substringBeforeLast('.')
            if (MONTH_RE.matches(month) && month !in fromIdx) fromIdx[month] = MonthInfo(month, 0, null, excelFile(ctx, month).exists())
        }
        return fromIdx.values.sortedByDescending { it.month }
    }

    /** Righe del mese: lista di (data, paziente, attività, totale). */
    fun rows(ctx: Context, month: String): List<List<String>> = try {
        val arr = JSONObject(monthFile(ctx, month).readText()).optJSONArray("rows") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            listOf(r.optString("data"), r.optString("paziente"), r.optString("attivita"), r.optString("totale"))
        }
    } catch (e: Exception) { emptyList() }

    /** Risposta locale in chat ("report di ottobre", "report del mese"). Null se non pertinente. */
    fun answer(ctx: Context, n: String): String? {
        if (n.length > 60 || n.contains(':') || !Regex("\\b(report|allegato d|sapio)\\b").containsMatchIn(n)) return null
        val months = localMonths(ctx)
        if (months.isEmpty()) return "Non ho ancora report sul telefono. Apri REPORT e abbina il PC."
        val wanted = MESI.indexOfFirst { n.contains(it.lowercase()) }
        val m = if (wanted >= 0) months.firstOrNull { it.month.endsWith("-%02d".format(wanted + 1)) } else months.first()
        if (m == null) return "Non ho il report di ${MESI[wanted]} sul telefono."
        val tot = m.totale?.let { " per un totale di %.2f euro".format(Locale.ITALIAN, it) } ?: ""
        val upd = lastSync(ctx)?.let { " Ultimo aggiornamento: $it." } ?: ""
        return "${monthLabel(m.month)}: ${m.n} prestazioni$tot.$upd"
    }
}
