package com.jarvis.nexus.wear

import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.input.RemoteInputIntentHelper
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

private val Cyan = Color(0xFF00E5FF)
private val CyanDim = Color(0xFF00838F)
private val Amber = Color(0xFFFFB300)

private enum class St { IDLE, LISTENING, THINKING, ANSWER }

/** JARVIS da polso: tocca il nucleo per parlare, tieni premuto per scrivere. Il cervello è l'app JARVIS del telefono. */
class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    companion object {
        const val PATH_ASK = "/jarvis/ask"
        const val PATH_REPLY = "/jarvis/reply"
        private const val INPUT_KEY = "q"
    }

    private var state by mutableStateOf(St.IDLE)
    private var heard by mutableStateOf("")
    private var answer by mutableStateOf("")
    private var engine by mutableStateOf("")
    private var voiceOn by mutableStateOf(true)
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val prefs by lazy { getSharedPreferences("jarvis_watch", MODE_PRIVATE) }

    @Volatile private var pending: CompletableDeferred<String>? = null
    private val listener = MessageClient.OnMessageReceivedListener { ev ->
        if (ev.path == PATH_REPLY) pending?.complete(String(ev.data, Charsets.UTF_8))
    }

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (r.resultCode == RESULT_OK && !text.isNullOrBlank()) ask(text) else back()
    }
    private val keyboard = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(INPUT_KEY)?.toString() }
        if (!text.isNullOrBlank()) ask(text) else back()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voiceOn = prefs.getBoolean("voice", true)
        tts = TextToSpeech(this, this)
        setContent { Ui() }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) tts?.language = Locale.ITALIAN
    }

    override fun onResume() { super.onResume(); Wearable.getMessageClient(this).addListener(listener) }
    override fun onPause() { Wearable.getMessageClient(this).removeListener(listener); tts?.stop(); super.onPause() }
    override fun onDestroy() { tts?.shutdown(); super.onDestroy() }

    private fun back() { state = if (answer.isEmpty()) St.IDLE else St.ANSWER }

    private fun listen() {
        tts?.stop()
        state = St.LISTENING
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Dimmi")
        try { speech.launch(i) } catch (e: ActivityNotFoundException) { type() }
    }

    /** Tastiera / dettatura di sistema (funziona anche se manca il riconoscimento vocale Google). */
    private fun type() {
        tts?.stop()
        state = St.LISTENING
        val i = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(i, listOf(RemoteInput.Builder(INPUT_KEY).setLabel("Chiedi a JARVIS").build()))
        try { keyboard.launch(i) } catch (e: ActivityNotFoundException) { show("Nessun metodo di input disponibile.", "ERRORE") }
    }

    private fun ask(q: String) {
        heard = q; answer = ""; engine = ""; state = St.THINKING
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        lifecycleScope.launch {
            val res = try {
                JSONObject(withTimeout(55_000) { sendToPhone(q) })
            } catch (e: TimeoutCancellationException) {
                JSONObject().put("text", "Il telefono non risponde. Controlla di avere l'ultima versione di JARVIS sul telefono.").put("engine", "ERRORE")
            } catch (e: Exception) {
                JSONObject().put("text", e.message ?: "Errore di collegamento.").put("engine", "ERRORE")
            }
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            res.optJSONObject("action")?.let { runAction(it) }
            show(res.optString("text").ifBlank { "Fatto." }, res.optString("engine"))
        }
    }

    private suspend fun sendToPhone(q: String): String {
        val nodes = Wearable.getNodeClient(this).connectedNodes.await()
        val node = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
            ?: throw IllegalStateException("Telefono non collegato.")
        val d = CompletableDeferred<String>()
        pending = d
        Wearable.getMessageClient(this).sendMessage(node.id, PATH_ASK, q.toByteArray(Charsets.UTF_8)).await()
        return d.await()
    }

    private fun show(text: String, eng: String) {
        answer = text; engine = eng; state = St.ANSWER
        try { getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) { }
        if (voiceOn) speak(text)
    }

    private fun speak(text: String) { if (ttsReady) tts?.speak(text.take(600), TextToSpeech.QUEUE_FLUSH, null, "jarvis") }

    /** Timer e sveglie partono direttamente sull'orologio. */
    private fun runAction(a: JSONObject) {
        val label = a.optString("label").ifBlank { "JARVIS" }
        try {
            when (a.optString("type")) {
                "timer" -> {
                    val sec = a.optString("seconds").toIntOrNull() ?: return
                    startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, label).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                }
                "alarm" -> {
                    val h = a.optString("hour").toIntOrNull() ?: return
                    val m = a.optString("minute").toIntOrNull() ?: 0
                    startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
                        .putExtra(AlarmClock.EXTRA_MINUTES, m).putExtra(AlarmClock.EXTRA_MESSAGE, label)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                }
            }
        } catch (_: Exception) { }
    }

    private fun toggleVoice() {
        voiceOn = !voiceOn
        prefs.edit().putBoolean("voice", voiceOn).apply()
        if (!voiceOn) tts?.stop()
    }

    // ───────────── Interfaccia ─────────────
    @Composable
    private fun Ui() {
        MaterialTheme {
            Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                if (state == St.ANSWER) AnswerView() else CoreView()
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun CoreView() {
        val busy = state == St.THINKING
        val t = rememberInfiniteTransition(label = "core")
        val rot by t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 1200 else 6000, easing = LinearEasing)), label = "rot")
        val pulse by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(if (busy) 500 else 1800), RepeatMode.Reverse), label = "pulse")
        val accent = if (state == St.LISTENING) Amber else Cyan

        Box(
            Modifier.fillMaxSize().combinedClickable(enabled = !busy, onClick = { listen() }, onLongClick = { type() }),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize().padding(10.dp)) {
                val r = size.minDimension / 2
                val c = Offset(size.width / 2, size.height / 2)
                drawCircle(CyanDim.copy(alpha = 0.5f), r, c, style = Stroke(1.dp.toPx()))
                listOf(0f, 120f, 240f).forEach { a ->
                    drawArc(accent, rot + a, 70f, false, Offset(c.x - r * 0.92f, c.y - r * 0.92f), Size(r * 1.84f, r * 1.84f),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                listOf(60f, 240f).forEach { a ->
                    drawArc(accent.copy(alpha = 0.6f), -rot * 1.5f + a, 100f, false, Offset(c.x - r * 0.72f, c.y - r * 0.72f), Size(r * 1.44f, r * 1.44f),
                        style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                }
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * pulse), Color.Transparent), c, r * 0.55f), r * 0.55f, c)
                drawCircle(accent.copy(alpha = 0.9f), r * 0.16f, c, style = Stroke(2.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("J.A.R.V.I.S.", color = Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(54.dp))
                Text(
                    when (state) { St.LISTENING -> "IN ASCOLTO"; St.THINKING -> "ELABORAZIONE"; else -> "TOCCA PER PARLARE" },
                    color = accent, fontSize = 10.sp, fontFamily = FontFamily.Monospace
                )
                if (busy && heard.isNotBlank()) {
                    Text("“$heard”", color = Color.LightGray, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2,
                        modifier = Modifier.padding(horizontal = 30.dp, vertical = 4.dp))
                }
            }
        }
    }

    @Composable
    private fun AnswerView() {
        val scroll = rememberScrollState()
        val focus = remember { FocusRequester() }
        val scope = rememberCoroutineScope()
        LaunchedEffect(answer) { scroll.scrollTo(0); focus.requestFocus() }
        Column(
            Modifier.fillMaxSize()
                // Ghiera del Watch4 Classic per scorrere
                .onRotaryScrollEvent { scope.launch { scroll.scrollBy(it.verticalScrollPixels) }; true }
                .focusRequester(focus).focusable()
                .verticalScroll(scroll)
                .padding(start = 22.dp, end = 22.dp, top = 30.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("J.A.R.V.I.S." + if (engine.isNotBlank()) " · $engine" else "", color = Cyan, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(6.dp))
            Text(heard, color = Color.Gray, fontSize = 11.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(answer, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Button({ listen() }, colors = ButtonDefaults.buttonColors(backgroundColor = Cyan, contentColor = Color.Black)) {
                    Text("PARLA", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Button({ toggleVoice() }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263238), contentColor = Cyan)) {
                    Text(if (voiceOn) "VOCE ON" else "VOCE OFF", fontSize = 9.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Button({ type() }, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF263238), contentColor = Cyan)) {
                Text("SCRIVI", fontSize = 10.sp)
            }
        }
    }
}
