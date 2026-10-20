package com.jarvis.nexus

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

/** Un'azione da eseguire sul telefono. */
data class Cmd(val type: String, val args: Map<String, String> = emptyMap()) {
    operator fun get(k: String): String = args[k] ?: ""
}

data class Outcome(val ok: Boolean, val msg: String)

/** Riconoscimento comandi vocali in italiano + parsing delle azioni proposte da Gemini. */
object Commands {
    private val IC = RegexOption.IGNORE_CASE
    private val wake = Regex("^\\s*(?:(?:ehi|hey|ok|ciao)\\s+)?jarvis[\\s,!.:]*", IC)

    fun norm(s: String): String = Normalizer.normalize(s.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]"), "")

    fun lev(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]; dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    /** Comandi diretti riconosciuti localmente (istantanei, funzionano anche offline). */
    fun parse(input: String): Cmd? {
        val raw = input.replace(wake, "").trim().trimEnd('.', '!', '?').trim()
        val t = raw.lowercase(Locale.ITALIAN)
        if (t.isEmpty()) return null

        // Torcia
        if (Regex("\\b(torcia|flash)\\b").containsMatchIn(t) && !t.startsWith("apri")) {
            val on = when {
                Regex("\\b(spegni|disattiva|togli)\\b").containsMatchIn(t) -> "false"
                Regex("\\b(accendi|attiva)\\b").containsMatchIn(t) -> "true"
                else -> "toggle"
            }
            return Cmd("torch", mapOf("on" to on))
        }

        // Attiva / disattiva connessioni → pannello impostazioni
        Regex("^(?:attiva|disattiva|accendi|spegni|abilita|disabilita)\\s+(?:il\\s+|la\\s+|lo\\s+|l')?(wi-?fi|bluetooth|gps|posizione|nfc|dati(?: mobili)?|hotspot|modalit[aà] aereo|non disturbare|risparmio energetico)", IC)
            .find(raw)?.let { return Cmd("settings", mapOf("page" to it.groupValues[1])) }

        // Sveglia
        if (Regex("\\bsveglia|svegliami").containsMatchIn(t)) {
            val m = Regex("(\\d{1,2})(?:\\s*[:.]\\s*(\\d{2})|\\s+e\\s+(\\d{1,2})\\b)?").find(t) ?: return Cmd("alarm")
            var h = m.groupValues[1].toInt()
            var min = m.groupValues[2].ifEmpty { m.groupValues[3] }.ifEmpty { "0" }.toInt()
            if (t.contains("e mezza")) min = 30
            if (t.contains("e un quarto")) min = 15
            if (t.contains("meno un quarto")) { h = (h + 23) % 24; min = 45 }
            if ((t.contains("pomeriggio") || t.contains("sera")) && h < 12) h += 12
            if (h > 23 || min > 59) return Cmd("alarm")
            return Cmd("alarm", mapOf("hour" to "$h", "minute" to "$min"))
        }

        // Timer
        if (t.contains("timer") || t.contains("conto alla rovescia")) {
            val tt = t.replace("un'ora", "1 ora").replace("mezz'ora", "30 minuti").replace("mezzora", "30 minuti")
                .replace(Regex("\\bun\\s"), "1 ").replace(Regex("\\buna\\s"), "1 ")
            var sec = 0
            Regex("(\\d+)\\s*(secondi|secondo|minuti|minuto|ore|ora)").findAll(tt).forEach {
                val v = it.groupValues[1].toInt()
                sec += when {
                    it.groupValues[2].startsWith("second") -> v
                    it.groupValues[2].startsWith("minut") -> v * 60
                    else -> v * 3600
                }
            }
            return if (sec > 0) Cmd("timer", mapOf("seconds" to "$sec")) else Cmd("timer")
        }

        // Volume
        if (t.contains("volume") || Regex("\\b(silenzia|muto)\\b").containsMatchIn(t)) {
            Regex("(\\d{1,3})").find(t)?.let { return Cmd("volume", mapOf("level" to it.value)) }
            return when {
                Regex("\\b(muto|silenzia|azzera)\\b").containsMatchIn(t) -> Cmd("volume", mapOf("level" to "0"))
                t.contains("massimo") -> Cmd("volume", mapOf("level" to "100"))
                Regex("\\b(abbassa|diminuisci|riduci)\\b").containsMatchIn(t) -> Cmd("volume", mapOf("dir" to "down"))
                else -> Cmd("volume", mapOf("dir" to "up"))
            }
        }

        // Chiamate
        Regex("^(?:chiama|telefona(?:\\s+a)?|fai una (?:telefonata|chiamata) a)\\s+(.+)$", IC).find(raw)?.let {
            return Cmd("call", mapOf("who" to it.groupValues[1].trim()))
        }

        // Messaggi SMS / WhatsApp
        Regex("^(?:manda(?:mi)?|invia|scrivi)\\s+(?:un[oa]?\\s+)?(?:messaggio\\s+)?(?:(?:su|con|via)\\s+)?(?:whatsapp|sms|messaggio)?\\s*a\\s+(.+)$", IC).find(raw)?.let { m ->
            val rest = m.groupValues[1].replace(Regex("\\s*(?:su|con|via)\\s+whatsapp\\b", IC), "").trim()
            val sep = Regex("\\s*(?:dicendo|che dice|scrivendo|con scritto|:|,)\\s*", IC).find(rest)
            val who: String
            val text: String
            if (sep != null) { who = rest.substring(0, sep.range.first).trim(); text = rest.substring(sep.range.last + 1).trim() }
            else { who = rest.substringBefore(" ").trim(); text = rest.substringAfter(" ", "").trim() }
            if (who.isNotEmpty()) return Cmd(if (t.contains("whatsapp")) "whatsapp" else "sms", mapOf("who" to who, "text" to text))
        }

        // Navigazione
        Regex("^(?:naviga|portami|guidami|navigazione|indicazioni)\\s+(?:verso\\s+|per\\s+|fino a\\s+|a\\s+|al\\s+|alla\\s+|allo\\s+|ai\\s+|alle\\s+|in\\s+)?(.+)$", IC).find(raw)?.let {
            return Cmd("navigate", mapOf("to" to it.groupValues[1].trim()))
        }

        // Foto
        if (Regex("\\b(scatta|fai|fammi)\\s+(una\\s+|un\\s+)?(foto|selfie)").containsMatchIn(t)) return Cmd("camera")

        // Musica
        Regex("^(?:metti|riproduci|suona|fammi sentire|ascolta|play)\\s+(?:la canzone\\s+|il brano\\s+|la musica di\\s+|musica di\\s+|un po' di\\s+)?(.+)$", IC).find(raw)?.let {
            return Cmd("play", mapOf("q" to it.groupValues[1].trim()))
        }

        // Ricerca web
        Regex("^(?:cerca|cercami|googla)\\s+(?:(?:su\\s+)?(?:google|internet|web)\\s+)?(.+)$", IC).find(raw)?.let {
            return Cmd("search", mapOf("q" to it.groupValues[1].trim()))
        }

        // Note e attività
        Regex("^(?:ricorda(?:mi)?|prendi nota|annota)\\s+(?:che\\s+|di\\s+)?(.+)$", IC).find(raw)?.let {
            return Cmd("note", mapOf("text" to it.groupValues[1].trim()))
        }
        Regex("^(?:aggiungi\\s+(?:alla lista\\s+)?(?:un'?\\s*)?(?:attivit[aà]|task|cosa da fare)|attivit[aà]|da fare)\\s*:?\\s+(.+)$", IC).find(raw)?.let {
            return Cmd("task", mapOf("text" to it.groupValues[1].trim()))
        }

        // Apri app / impostazioni
        Regex("^(?:apri|avvia|lancia|vai\\s+(?:su|in|nelle|nella)|mostrami|accedi a)\\s+(?:l'app\\s+|l'applicazione\\s+|app\\s+|applicazione\\s+|l'|la\\s+|il\\s+|lo\\s+|le\\s+|gli\\s+|i\\s+)?(.+)$", IC).find(raw)?.let {
            return Cmd("open", mapOf("app" to it.groupValues[1].trim()))
        }
        return null
    }

    /** Estrae la riga "AZIONE: {json}" dalla risposta di Gemini. */
    fun fromGemini(reply: String): Pair<Cmd?, String> {
        val lines = reply.replace("```json", "").replace("```", "").lines()
        val idx = lines.indexOfFirst { it.trim().startsWith("AZIONE", ignoreCase = true) }
        val text = lines.filterIndexed { i, _ -> i != idx }.joinToString("\n").replace("*", "").trim()
        if (idx < 0) return null to text
        val line = lines[idx]
        val s = line.indexOf('{')
        val e = line.lastIndexOf('}')
        if (s < 0 || e <= s) return null to text
        return try {
            val o = JSONObject(line.substring(s, e + 1))
            val type = o.optString("type")
            val args = mutableMapOf<String, String>()
            o.keys().forEach { k -> if (k != "type") args[k] = o.get(k).toString() }
            (if (type.isBlank()) null else Cmd(type, args)) to text
        } catch (ex: Exception) { null to text }
    }
}
