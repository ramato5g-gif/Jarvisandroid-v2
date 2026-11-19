package com.jarvis.nexus

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ponte JARVIS ↔ Galaxy Watch (Wearable Data Layer).
 * L'orologio manda la domanda su /jarvis/ask, il telefono usa lo stesso cervello (Claude Haiku, chiave locale)
 * e risponde su /jarvis/reply con JSON {text, engine, action?}. Le azioni timer/sveglia le esegue l'orologio.
 */
class WearBridgeService : WearableListenerService() {

    companion object {
        const val PATH_ASK = "/jarvis/ask"
        const val PATH_REPLY = "/jarvis/reply"
        private val WATCH_ACTIONS = setOf("timer", "alarm")
        private val PHONE_ACTIONS = setOf("event", "note", "task", "torch", "volume")

        // Breve memoria della conversazione dal polso (si azzera dopo 10 minuti di silenzio)
        private val history = mutableListOf<Pair<String, String>>()
        private var lastAt = 0L
    }

    private val prefs by lazy { getSharedPreferences("nexus_local", Context.MODE_PRIVATE) }

    // Chiamato su un thread di background: il lavoro bloccante qui è consentito
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_ASK) return
        val q = String(event.data, Charsets.UTF_8).trim()
        val out = try { answer(q) } catch (e: Exception) { reply("Errore sul telefono: ${e.message}", "ERRORE") }
        try {
            Tasks.await(Wearable.getMessageClient(this).sendMessage(event.sourceNodeId, PATH_REPLY, out.toString().toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) { }
    }

    private fun reply(text: String, engine: String, action: Cmd? = null): JSONObject {
        val o = JSONObject().put("text", text).put("engine", engine)
        if (action != null) {
            val a = JSONObject().put("type", action.type)
            action.args.forEach { (k, v) -> a.put(k, v) }
            o.put("action", a)
        }
        return o
    }

    private fun answer(q: String): JSONObject {
        if (q.isBlank()) return reply("Non ho sentito nulla.", "LOCALE")
        if (q == "__ping__") return reply("Collegato al telefono.", "PING")
        val n = q.lowercase(Locale.ITALIAN)
        ReportSync.answer(this, n)?.let { return reply(it, "REPORT") }
        SomnoApts.answer(this, n)?.let { return reply(it, "SOMNO") }
        Commands.parse(q)?.let { cmd ->
            if (cmd.type in WATCH_ACTIONS) return reply("Fatto.", "COMANDO", cmd)
            if (cmd.type in PHONE_ACTIONS) return reply(execute(cmd), "COMANDO")
        }

        val key = prefs.getString("claude_api_key", "") ?: ""
        if (!prefs.getBoolean("claude_enabled", true) || key.isBlank())
            return reply("Configura la chiave di Claude nell'app JARVIS sul telefono.", "ERRORE")

        val now = System.currentTimeMillis()
        val turns: List<Pair<String, String>>
        synchronized(history) {
            if (now - lastAt > 10 * 60_000L) history.clear()
            lastAt = now
            history.add("user" to q)
            while (history.size > 8) history.removeAt(0)
            turns = history.toList()
        }
        val (cmd, text) = Commands.fromClaude(ClaudeClient.chat(key, systemPrompt(), turns))
        synchronized(history) { history.add("assistant" to text.ifBlank { "Fatto." }) }

        return when {
            cmd == null -> reply(text.ifBlank { "Fatto." }, "HAIKU")
            cmd.type in WATCH_ACTIONS -> reply(text.ifBlank { "Fatto." }, "HAIKU", cmd)
            cmd.type in PHONE_ACTIONS -> reply(execute(cmd).let { r -> if (text.isBlank()) r else text }, "HAIKU")
            else -> reply(text.ifBlank { "Questo devo farlo dal telefono." }, "HAIKU")
        }
    }

    private fun systemPrompt(): String {
        val name = prefs.getString("user_name", "Riccardo") ?: "Riccardo"
        val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN).format(Date())
        val events = OsData.upcoming(this, 48).take(8)
        val ctx = if (events.isEmpty()) "" else {
            val f = SimpleDateFormat("EEE d MMM HH:mm", Locale.ITALIAN)
            "Prossimi eventi del calendario: " + events.joinToString("; ") { "${f.format(Date(it.begin))} ${it.title}" } + ".\n"
        }
        return """Sei J.A.R.V.I.S., l'assistente personale di $name. Ti sta parlando dal suo smartwatch (Galaxy Watch, schermo rotondo piccolo).
Rispondi sempre in italiano, brevissimo: massimo 2 frasi, naturali da ascoltare. Niente markdown, elenchi o emoji. Tono cortese e brillante, come il JARVIS di Iron Man.
Data e ora attuali: $now.
${ctx}Se l'utente chiede un'azione, la PRIMA riga della risposta deve essere esattamente
AZIONE: {json su una sola riga}
seguita da una breve frase di conferma. Azioni disponibili dal polso:
{"type":"timer","seconds":600,"label":"..."}
{"type":"alarm","hour":7,"minute":30,"label":"..."}
{"type":"event","title":"...","start":"yyyy-MM-ddTHH:mm","end":"yyyy-MM-ddTHH:mm"}
{"type":"note","text":"..."}
{"type":"task","text":"..."}
{"type":"torch","on":true}
{"type":"volume","level":50}
Usa solo queste azioni, una per risposta. Per chiamate, messaggi, navigazione o app spiega che deve usare il telefono. Per domande normali rispondi senza riga AZIONE."""
    }

    // ───── Azioni eseguibili dal telefono senza aprire schermate ─────
    private fun execute(c: Cmd): String = try {
        when (c.type) {
            "note" -> { appendList("notes", "• ", c["text"]); "Nota salvata." }
            "task" -> { appendList("tasks", "□ ", c["text"]); "Attività aggiunta." }
            "event" -> {
                val st = parseIso(c["start"])
                val en = parseIso(c["end"]) ?: st?.plus(3_600_000L)
                if (st != null && en != null && OsData.insertEvent(this, c["title"], st, en) != null)
                    "Evento \"${c["title"]}\" aggiunto al calendario."
                else "Non riesco a scrivere nel calendario: apri JARVIS sul telefono e concedi il permesso."
            }
            "torch" -> {
                val cm = getSystemService(CameraManager::class.java)
                val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                if (id == null) "Il telefono non ha la torcia." else {
                    val on = c["on"] != "false"
                    cm.setTorchMode(id, on)
                    if (on) "Torcia accesa." else "Torcia spenta."
                }
            }
            "volume" -> {
                val am = getSystemService(AudioManager::class.java)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val lvl = c["level"].toIntOrNull()
                if (lvl != null) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, (lvl.coerceIn(0, 100) * max) / 100, 0)
                    "Volume del telefono al ${lvl.coerceIn(0, 100)}%."
                } else {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                        if (c["dir"] == "down") AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE, 0)
                    if (c["dir"] == "down") "Volume abbassato." else "Volume alzato."
                }
            }
            else -> "Azione non disponibile dal polso."
        }
    } catch (e: Exception) { "Azione non riuscita: ${e.message}" }

    private fun appendList(key: String, bullet: String, text: String) {
        if (text.isBlank()) return
        val cur = prefs.getString(key, "") ?: ""
        prefs.edit().putString(key, if (cur.isBlank()) "$bullet$text" else "$cur\n$bullet$text").apply()
    }

    private fun parseIso(v: String): Long? = try {
        if (v.length < 16) null else SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ITALIAN).parse(v.take(16))?.time
    } catch (e: Exception) { null }
}
