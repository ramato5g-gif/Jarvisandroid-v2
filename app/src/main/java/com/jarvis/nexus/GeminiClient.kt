package com.jarvis.nexus

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Cloud chat; API key stays on device and is never checked into source control. */
object GeminiClient {
    private const val MODEL = "gemini-2.5-flash"
    fun ask(key: String, question: String, context: String): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 45000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            val prompt = "Sei JARVIS, assistente personale Android. Rispondi sempre in italiano. " +
                "Non inventare azioni eseguite sul telefono. Non presumere di avere accesso a dati non forniti.\n" +
                "Contesto recente:\n" + context.takeLast(3500) + "\nUtente: " + question
            val body = JSONObject().put("contents", JSONArray().put(
                JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", prompt)))
            )).put("generationConfig", JSONObject().put("maxOutputTokens", 1024))
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code: " + response.take(180))
            val candidates = JSONObject(response).optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val answer = buildString {
                if (parts != null) for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i)
                    if (part != null && part.has("text")) append(part.optString("text"))
                }
            }.trim()
            if (answer.isEmpty()) throw IllegalStateException("Risposta vuota o bloccata")
            return answer
        } finally { connection.disconnect() }
    }
}
