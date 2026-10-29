package com.jarvis.nexus

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Client Claude Haiku (Anthropic Messages API): conversazione multi-turno con istruzioni di sistema. La chiave resta sul dispositivo. */
object ClaudeClient {
    private const val MODEL = "claude-haiku-5-5"

    fun chat(key: String, system: String, turns: List<Pair<String, String>>): String {
        val url = URL("https://api.anthropic.com/v1/messages")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 40000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", key)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            val messages = JSONArray()
            turns.forEach { (role, text) -> messages.put(JSONObject().put("role", role).put("content", text)) }
            val body = JSONObject()
                .put("model", MODEL)
                .put("max_tokens", 800)
                .put("temperature", 0.7)
                .put("system", system)
                .put("messages", messages)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code: " + response.take(160))
            val blocks = JSONObject(response).optJSONArray("content")
            val answer = buildString {
                if (blocks != null) for (i in 0 until blocks.length()) {
                    val b = blocks.optJSONObject(i)
                    if (b != null && b.optString("type") == "text") append(b.optString("text"))
                }
            }.trim()
            if (answer.isEmpty()) throw IllegalStateException("Risposta vuota o bloccata")
            return answer
        } finally { connection.disconnect() }
    }
}
