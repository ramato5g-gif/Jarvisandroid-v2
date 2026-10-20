package com.jarvis.nexus

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

class LocalBrain(private val context: Context) {
    private var llm: LlmInference? = null
    private val model: File get() = File(context.filesDir, "model.bin")
    fun available() = model.exists() && model.length() > 0L

    @Synchronized
    fun respond(question: String, memory: String): String {
        if (!available()) return "Modello non configurato. Tocca IMPORTA MODELLO e seleziona un file .bin compatibile."
        if (llm == null) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(model.absolutePath)
                .setMaxTokens(512)
                .setTopK(40)
                .setTemperature(0.65f)
                .build()
            llm = LlmInference.createFromOptions(context, options)
        }
        return llm!!.generateResponse(
            "Sei JARVIS, assistente Android privato. Rispondi in italiano. " +
            "Non dichiarare di aver eseguito azioni se non è vero.\n" +
            memory.takeLast(2400) + "\nUtente: " + question + "\nJARVIS:"
        ).trim()
    }
    @Synchronized fun unload() { llm?.close(); llm = null }
}
