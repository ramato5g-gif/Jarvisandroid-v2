package com.jarvis.nexus

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

/** Modello locale offline (MediaPipe LLM Inference, file .task / .bin). */
class LocalBrain(private val context: Context) {
    private var llm: LlmInference? = null

    @Volatile var modelFile: File? = null
        set(value) {
            if (field?.absolutePath != value?.absolutePath) { unload(); field = value }
        }

    fun available(): Boolean = modelFile?.let { it.exists() && it.length() > 0L } == true

    @Synchronized
    fun respond(question: String, memory: String): String {
        val f = modelFile
        if (f == null || !available()) return "Nessun modello locale: scaricane uno da Sistema → Modello locale."
        if (llm == null) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(f.absolutePath)
                .setMaxTokens(1280)
                .build()
            llm = LlmInference.createFromOptions(context, options)
        }
        return llm!!.generateResponse(
            "Sei JARVIS, assistente Android privato. Rispondi in italiano, in modo breve. " +
            "Non dichiarare di aver eseguito azioni se non è vero.\n" +
            memory.takeLast(1500) + "\nUtente: " + question + "\nJARVIS:"
        ).trim()
    }

    @Synchronized fun unload() { llm?.close(); llm = null }
}
