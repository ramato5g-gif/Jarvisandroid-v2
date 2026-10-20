package com.jarvis.nexus

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Client Gemini: conversazione multi-turno con istruzioni di sistema. La chiave resta sul dispositivo. */
object GeminiClient {
    private const val MODEL = "gemini-2.5-flash"

    fun chat(key: String, system: String, turns: List<Pair<String, String>>): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10000
            readTimeout = 40000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            val contents = JSONArray()
            turns.forEach { (role, text) ->
                contents.put(JSONObject().put("role", role).put("parts", JSONArray().put(JSONObject().put("text", text))))
            }
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", contents)
                .put("generationConfig", JSONObject()
                    .put("maxOutputTokens", 800)
                    .put("temperature", 0.7)
                    .put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code: " + response.take(160))
            val parts = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")
            val answer = buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    val p = parts.optJSONObject(i)
                    if (p != null && p.has("text") && !p.optBoolean("thought")) append(p.optString("text"))
                }
            }.trim()
            if (answer.isEmpty()) throw IllegalStateException("Risposta vuota o bloccata")
            return answer
        } finally { connection.disconnect() }
    }
}
