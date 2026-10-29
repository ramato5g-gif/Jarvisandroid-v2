package com.jarvis.nexus

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.DownloadManager
import android.app.SearchManager
import android.app.StatusBarManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ───────────────────────── Palette ─────────────────────────
private val Bg = Color(0xFF04070D)
private val Panel = Color(0xFF0A111C)
private val Panel2 = Color(0xFF0F1928)
private val Stroke1 = Color(0xFF1C2A3F)
private val NeonRed = Color(0xFFFF3B4E)
private val NeonBlue = Color(0xFF2E9BFF)
private val Cyan = Color(0xFF39E1FF)
private val Green = Color(0xFF3DDC97)
private val Muted = Color(0xFF8593A8)

private data class Line(val who: String, val message: String)
private enum class Screen { HOME, CHAT, MODULES, MODULE, DEVICE, AUTOMATE, MEMORY, SETTINGS, REPORTS }
private data class Module(val id: String, val sub: String, val icon: ImageVector, val actions: List<String> = emptyList()) {
    val title: String get() = id
}
private data class Routine(val id: String, val title: String, val desc: String, val icon: ImageVector)
private data class DevStats(
    val battery: Int = 0, val charging: Boolean = false, val temp: Float = 0f,
    val ramPct: Int = 0, val ramFree: Double = 0.0, val ramTotal: Double = 0.0,
    val storageFree: Double = 0.0, val storageTotal: Double = 0.0,
    val wifi: Boolean = false, val bt: Boolean = false, val location: Boolean = false, val online: Boolean = false
)

private val modules = listOf(
    Module("AI CORE", "Modello locale", Icons.Rounded.Psychology, listOf("Modelli locali", "Gemini", "Cancella chat")),
    Module("VOICE", "STT / TTS", Icons.Rounded.GraphicEq, listOf("Voce", "Test voce")),
    Module("MEMORY", "Note e attività", Icons.Rounded.Storage),
    Module("REPORT", "Report mensili PC", Icons.Rounded.Assessment),
    Module("DEVICE", "Controllo Android", Icons.Rounded.PhoneAndroid),
    Module("AUTOMATE", "Routine e scenari", Icons.Rounded.Autorenew),
    Module("SYSTEM", "Diagnostica", Icons.Rounded.Speed, listOf("Stato", "Batteria", "Archiviazione", "App", "Impostazioni", "Cancella chat")),
    Module("WORK", "Produttività", Icons.Rounded.Work, listOf("Email", "Calendario", "File", "Condividi", "Esporta note", "Copia note")),
    Module("HEALTH", "Promemoria", Icons.Rounded.Favorite, listOf("Farmaco", "Acqua", "Timer", "Promemoria")),
    Module("NETWORK", "Rete e connessioni", Icons.Rounded.Wifi, listOf("Wi-Fi", "Bluetooth", "Dati mobili", "NFC", "Browser", "Mappe")),
)

private data class LocalModel(
    val id: String, val name: String, val desc: String, val url: String,
    val file: String, val size: String, val bytes: Long, val needsToken: Boolean
)
private data class DlState(val status: Int, val done: Long, val total: Long, val reason: Int)

private val localModels = listOf(
    LocalModel("qwen25-1.5b", "Qwen 2.5 1.5B Instruct", "Consigliato • buon italiano • nessun account",
        "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
        "Qwen2.5-1.5B-Instruct_q8_ekv1280.task", "~1,6 GB", 1_700_000_000L, false),
    LocalModel("gemma3-1b", "Gemma 3 1B IT", "Leggero e veloce • serve token HF",
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task",
        "Gemma3-1B-IT_q4_ekv2048.task", "~0,6 GB", 600_000_000L, true),
    LocalModel("gemma3n-e2b", "Gemma 3n E2B", "Il più capace • token HF • sperimentale",
        "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
        "gemma-3n-E2B-it-int4.task", "~3,1 GB", 3_200_000_000L, true),
)

private val routines = listOf(
    Routine("morning", "Briefing", "Ora, data, batteria e attività a voce", Icons.Rounded.WbSunny),
    Routine("work", "Modalità lavoro", "Apre Non disturbare", Icons.Rounded.Work),
    Routine("night", "Buonanotte", "Saluta e apre la sveglia", Icons.Rounded.Bedtime),
    Routine("backup", "Backup note", "Esporta le note in un file", Icons.Rounded.Backup),
)

private fun actionIcon(a: String): ImageVector = when (a) {
    "Voce" -> Icons.Rounded.Mic
    "Test voce" -> Icons.Rounded.RecordVoiceOver
    "Importa modello" -> Icons.Rounded.Download
    "Modelli locali" -> Icons.Rounded.CloudDownload
    "Gemini" -> Icons.Rounded.VpnKey
    "Cancella chat" -> Icons.Rounded.DeleteSweep
    "Stato" -> Icons.Rounded.Speed
    "Batteria" -> Icons.Rounded.BatteryChargingFull
    "Archiviazione" -> Icons.Rounded.SdStorage
    "App" -> Icons.Rounded.Apps
    "Impostazioni" -> Icons.Rounded.Settings
    "Email" -> Icons.Rounded.Email
    "Calendario" -> Icons.Rounded.Event
    "File" -> Icons.Rounded.Folder
    "Condividi" -> Icons.Rounded.Share
    "Esporta note" -> Icons.Rounded.Upload
    "Importa note" -> Icons.Rounded.Download
    "Copia note" -> Icons.Rounded.ContentCopy
    "Farmaco" -> Icons.Rounded.Medication
    "Acqua" -> Icons.Rounded.WaterDrop
    "Timer" -> Icons.Rounded.Timer
    "Promemoria" -> Icons.Rounded.Alarm
    "Wi-Fi" -> Icons.Rounded.Wifi
    "Bluetooth" -> Icons.Rounded.Bluetooth
    "Dati mobili" -> Icons.Rounded.SignalCellularAlt
    "NFC" -> Icons.Rounded.Nfc
    "Browser" -> Icons.Rounded.Language
    "Mappe" -> Icons.Rounded.Map
    "Posizione" -> Icons.Rounded.LocationOn
    "Torcia" -> Icons.Rounded.FlashlightOn
    "Fotocamera" -> Icons.Rounded.CameraAlt
    "Telefono" -> Icons.Rounded.Call
    "WhatsApp" -> Icons.Rounded.Sms
    else -> Icons.Rounded.Bolt
}

open class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    /** true nella finestra rapida (AssistActivity), false nell'app completa. */
    protected open val assistMode: Boolean = false
    private lateinit var brain: LocalBrain
    private var tts: TextToSpeech? = null
    private val prefs by lazy { getSharedPreferences("nexus_local", MODE_PRIVATE) }
    private val scope by lazy { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main) }

    private val messages = mutableStateListOf<Line>()
    private val notes = mutableStateListOf<String>()
    private val tasks = mutableStateListOf<String>()
    private val routineOn = mutableStateMapOf<String, Boolean>()
    private var screen by mutableStateOf(Screen.HOME)
    private var currentModule by mutableStateOf("AI CORE")
    private var modelLoaded by mutableStateOf(false)
    private var onlineEnabled by mutableStateOf(true)
    private var apiKey by mutableStateOf("")
    private var userName by mutableStateOf("Riccardo")
    private var engineStatus by mutableStateOf("PRONTO")
    private var isBusy by mutableStateOf(false)
    private var listening by mutableStateOf(false)
    private var altTheme by mutableStateOf(false)
    private var torchEnabled by mutableStateOf(false)
    private var stats by mutableStateOf(DevStats())
    @Volatile private var launcherApps: List<Pair<String, String>> = emptyList()
    private var pendingAfterPerm: (() -> Unit)? = null
    private val downloads = mutableStateMapOf<String, Float>()
    private var activeModelPath by mutableStateOf("")
    private var hfToken by mutableStateOf("")
    private var modelsVersion by mutableStateOf(0)
    private var recognizer: SpeechRecognizer? = null
    private var liveText by mutableStateOf("")
    private var micLevel by mutableStateOf(0f)
    private var voiceTurn = false
    private var followUp = false
    private var resumeTick by mutableStateOf(0)
    private var reportsVersion by mutableStateOf(0)
    private var reportsBusy by mutableStateOf(false)

    private val accent: Color get() = if (altTheme) NeonBlue else NeonRed
    private val accent2: Color get() = if (altTheme) NeonRed else NeonBlue

    // ───────────── Activity results ─────────────
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { out -> out.write(notes.joinToString("\n") { "• $it" }.toByteArray()) }
            sys("Note esportate.")
        } catch (e: Exception) { err("Esportazione: ${e.message}") }
    }
    private val importText = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(100000) } ?: ""
            notes.clear(); notes.addAll(parseList(txt, "• ")); saveLists()
            sys("Note importate: ${notes.size}.")
        } catch (e: Exception) { err("Importazione: ${e.message}") }
    }
    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            isBusy = true
            try {
                val dest = withContext(Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: "model.task"
                    val ext = name.substringAfterLast('.', "task").lowercase().take(8)
                    brain.modelFile = null
                    val f = File(filesDir, "imported.$ext")
                    contentResolver.openInputStream(uri)!!.use { input -> f.outputStream().use { output -> input.copyTo(output) } }
                    f
                }
                activateModel(dest, "Modello importato")
            } catch (e: Exception) { err("Importazione modello fallita: ${e.message}") }
            finally { isBusy = false }
        }
    }
    private val voicePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) { voiceTurn = true; if (!assistMode) screen = Screen.CHAT; send(spoken) }
        }
    }
    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        val p = pendingAfterPerm; pendingAfterPerm = null
        if (res.values.all { it }) p?.invoke() else reply("Permesso negato: non posso completare l'azione.", "SISTEMA")
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else sys("Permesso microfono negato.")
    }

    // ───────────── Lifecycle ─────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        brain = LocalBrain(this)
        hfToken = prefs.getString("hf_token", "") ?: ""
        activeModelPath = prefs.getString("model_path", "") ?: ""
        if (activeModelPath.isEmpty() && File(filesDir, "model.bin").exists()) activeModelPath = File(filesDir, "model.bin").absolutePath
        if (activeModelPath.isNotEmpty()) brain.modelFile = File(activeModelPath)
        modelLoaded = brain.available()
        apiKey = prefs.getString("gemini_api_key", "") ?: ""
        onlineEnabled = prefs.getBoolean("gemini_enabled", true)
        userName = prefs.getString("user_name", "Riccardo") ?: "Riccardo"
        altTheme = prefs.getBoolean("alt_theme", false)
        notes.addAll(parseList(prefs.getString("notes", "") ?: "", "• "))
        tasks.addAll(parseList(prefs.getString("tasks", "") ?: "", "□ "))
        routines.forEach { routineOn[it.id] = prefs.getBoolean("routine_${it.id}", it.id == "morning") }
        if (!assistMode) messages.add(Line("JARVIS", "Ciao $userName. Sono JARVIS, il tuo assistente. Come posso aiutarti?"))
        tts = TextToSpeech(this, this)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (assistMode && followUp && !isFinishing && !isDestroyed && !listening && !isBusy) { followUp = false; listen() }
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
        })
        scope.launch(Dispatchers.IO) { try { loadApps() } catch (e: Exception) { } }
        localModels.forEach { m ->
            val id = prefs.getLong("dl_${m.id}", -1L)
            if (id >= 0) { downloads[m.id] = -1f; scope.launch { pollDownload(m, id) } }
        }
        if (assistMode) stats = try { readStats() } catch (e: Exception) { DevStats() }
        if (!assistMode && ReportSync.pairing(this) != null) { ReportSyncWorker.schedule(this); syncReports(silent = true) }
        setContent { if (assistMode) AssistOverlay() else NexusApp() }
        if (assistMode) listen()
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.ITALIAN }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (assistMode && !listening) listen()
    }
    override fun onResume() { super.onResume(); resumeTick++ }
    override fun onStop() {
        super.onStop()
        // La finestra rapida si chiude quando un'azione apre un'altra app
        if (assistMode && !isChangingConfigurations) finish()
    }
    override fun onDestroy() {
        recognizer?.destroy(); recognizer = null
        brain.unload()
        val t = tts
        if (assistMode && t?.isSpeaking == true) Handler(mainLooper).postDelayed({ t.shutdown() }, 8000)
        else { t?.stop(); t?.shutdown() }
        scope.cancel()
        super.onDestroy()
    }

    // ───────────── Helpers ─────────────
    private fun sys(m: String) { messages.add(Line("SYSTEM", m)) }
    private fun err(m: String) { messages.add(Line("ERROR", m)) }
    private fun parseList(txt: String, prefix: String) =
        txt.lines().map { it.trim().removePrefix(prefix.trim()).trim() }.filter { it.isNotBlank() }
    private fun saveLists() {
        prefs.edit().putString("notes", notes.joinToString("\n") { "• $it" })
            .putString("tasks", tasks.joinToString("\n") { "□ $it" }).apply()
    }
    private fun speak(text: String) { tts?.speak(text.take(900), TextToSpeech.QUEUE_FLUSH, null, "jarvis") }
    private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..12 -> "Buongiorno"; in 13..17 -> "Buon pomeriggio"; else -> "Buonasera"
    }
    /** Riconoscimento vocale integrato: niente popup, testo live sul nucleo. */
    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { startListeningPopup(); return }
        try {
            tts?.stop()
            val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(this).also { recognizer = it }
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; liveText = "" }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { micLevel = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { micLevel = 0f }
                override fun onError(error: Int) {
                    listening = false; micLevel = 0f
                    val silent = error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    if (assistMode && silent) { Handler(mainLooper).postDelayed({ if (!listening && !isBusy) finish() }, 1200); return }
                    if (!silent) sys("Riconoscimento vocale non riuscito (errore $error).")
                }
                override fun onResults(results: Bundle?) {
                    listening = false; micLevel = 0f
                    val t = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) {
                        liveText = t; voiceTurn = true
                        if (!assistMode) screen = Screen.CHAT
                        send(t)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { if (it.isNotBlank()) liveText = it }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            liveText = ""
            listening = true
            r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
            })
        } catch (e: Exception) { listening = false; startListeningPopup() }
    }
    private fun startListeningPopup() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            }
            listening = true
            voicePicker.launch(intent)
        } catch (e: Exception) { listening = false; err("Riconoscimento vocale non disponibile.") }
    }
    private fun stopListening() { recognizer?.stopListening(); listening = false; micLevel = 0f }
    private fun listen() {
        if (listening) { stopListening(); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun launchSafe(intent: Intent, success: String) {
        try { startActivity(intent); sys(success) } catch (e: Exception) { err("Azione non disponibile: ${e.message}") }
    }
    private fun panelOr(panel: String, fallback: String, label: String) {
        val action = if (Build.VERSION.SDK_INT >= 29) panel else fallback
        launchSafe(Intent(action), label)
    }
    private fun hasInternet(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    private fun readStats(): DevStats {
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val status = bi?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
        val ramPct = if (mi.totalMem > 0) ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt() else 0
        val fs = StatFs(Environment.getDataDirectory().path)
        val gb = 1024.0 * 1024 * 1024
        val wifi = try { (applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).isWifiEnabled } catch (e: Exception) { false }
        val bt = try { (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter?.isEnabled == true } catch (e: Exception) { false }
        val loc = try { (getSystemService(LOCATION_SERVICE) as LocationManager).isLocationEnabled } catch (e: Exception) { false }
        return DevStats(pct, charging, temp, ramPct, mi.availMem / gb, mi.totalMem / gb,
            fs.availableBytes / gb, fs.totalBytes / gb, wifi, bt, loc, hasInternet())
    }

    private fun openModule(id: String) {
        when (id) {
            "MEMORY" -> screen = Screen.MEMORY
            "REPORT" -> screen = Screen.REPORTS
            "DEVICE" -> screen = Screen.DEVICE
            "AUTOMATE" -> screen = Screen.AUTOMATE
            else -> { currentModule = id; screen = Screen.MODULE }
        }
    }
    private fun runRoutine(id: String) {
        when (id) {
            "morning" -> { screen = Screen.CHAT; send("briefing") }
            "work" -> launchSafe(Intent("android.settings.ZEN_MODE_SETTINGS"), "Modalità lavoro: Non disturbare aperto.")
            "night" -> { speak("Buonanotte, $userName."); launchSafe(Intent(AlarmClock.ACTION_SET_ALARM), "Buonanotte. Editor sveglia aperto.") }
            "backup" -> exportFile.launch("jarvis-note.txt")
        }
    }

    private fun quickAction(action: String) {
        when (action) {
            "Voce" -> listen()
            "Test voce" -> speak("Sistemi online. Tutti i moduli sono operativi, $userName.")
            "Importa modello" -> modelPicker.launch(arrayOf("application/octet-stream", "*/*"))
            "Gemini", "Modelli locali" -> screen = Screen.SETTINGS
            "Cancella chat" -> { messages.clear(); messages.add(Line("JARVIS", "Conversazione cancellata.")) }
            "Esporta note" -> exportFile.launch("jarvis-note.txt")
            "Importa note" -> importText.launch(arrayOf("text/plain", "*/*"))
            "Copia note" -> {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Note JARVIS", notes.joinToString("\n") { "• $it" }))
                sys("Note copiate negli appunti.")
            }
            "Condividi" -> launchSafe(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, notes.joinToString("\n") { "• $it" })
            }, "Condividi note"), "Condivisione aperta.")
            "Calendario" -> launchSafe(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI), "Editor evento aperto.")
            "Promemoria" -> launchSafe(Intent(AlarmClock.ACTION_SET_ALARM), "Editor sveglia aperto.")
            "Farmaco" -> launchSafe(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_MESSAGE, "Farmaco"), "Sveglia farmaco pronta da confermare.")
            "Acqua" -> launchSafe(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, 3600)
                .putExtra(AlarmClock.EXTRA_MESSAGE, "Bevi acqua").putExtra(AlarmClock.EXTRA_SKIP_UI, false), "Timer acqua (1 h) pronto.")
            "Timer" -> launchSafe(Intent(AlarmClock.ACTION_SET_TIMER), "Timer aperto.")
            "Email" -> launchSafe(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), "Client email aperto.")
            "Browser" -> launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Browser aperto.")
            "Mappe" -> launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), "Mappe aperte.")
            "Fotocamera" -> launchSafe(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Fotocamera aperta.")
            "Telefono" -> launchSafe(Intent(Intent.ACTION_DIAL), "Tastierino aperto.")
            "WhatsApp" -> {
                val i = packageManager.getLaunchIntentForPackage("com.whatsapp")
                if (i != null) launchSafe(i, "WhatsApp aperto.") else sys("WhatsApp non installato.")
            }
            "File" -> launchSafe(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, "Selettore file aperto.")
            "Wi-Fi" -> panelOr(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS, "Pannello Wi-Fi aperto.")
            "Dati mobili" -> panelOr(Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.ACTION_WIRELESS_SETTINGS, "Pannello connessione aperto.")
            "NFC" -> panelOr(Settings.Panel.ACTION_NFC, Settings.ACTION_NFC_SETTINGS, "Pannello NFC aperto.")
            "Bluetooth" -> launchSafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Impostazioni Bluetooth aperte.")
            "Posizione" -> launchSafe(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS), "Impostazioni posizione aperte.")
            "Batteria" -> launchSafe(Intent(Intent.ACTION_POWER_USAGE_SUMMARY), "Utilizzo batteria aperto.")
            "Archiviazione" -> launchSafe(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS), "Archiviazione aperta.")
            "App" -> launchSafe(Intent(Settings.ACTION_APPLICATION_SETTINGS), "Impostazioni app aperte.")
            "Impostazioni" -> launchSafe(Intent(Settings.ACTION_SETTINGS), "Impostazioni aperte.")
            "Torcia" -> sys(setTorch(null).msg)
            "Stato" -> {
                val s = readStats()
                sys("Batteria ${s.battery}% • RAM usata ${s.ramPct}% • Libero %.1f GB • Temp %.1f °C".format(Locale.ITALIAN, s.storageFree, s.temp))
                screen = Screen.CHAT
            }
        }
    }

    private fun reply(text: String, engine: String) {
        if (voiceTurn) { followUp = assistMode; voiceTurn = false }
        engineStatus = engine
        messages.add(Line("JARVIS", text))
        speak(text)
    }

    /** Risposte immediate su dati del telefono. */
    private fun localAnswer(n: String): String? = when {
        n.contains("briefing") -> {
            val s = readStats()
            val ora = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date())
            val data = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date())
            "${greeting()}, $userName. Sono le $ora di $data. Batteria al ${s.battery}%" +
                (if (s.charging) " in carica" else "") + ". Hai ${tasks.size} attività e ${notes.size} note."
        }
        n == "ora" || n.contains("che ore") -> "Sono le " + SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date()) + "."
        n.contains("che giorno") || n == "data" -> "Oggi è " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date()) + "."
        n == "mostra attività" -> if (tasks.isEmpty()) "Nessuna attività." else tasks.joinToString("\n") { "□ $it" }
        n == "cancella attività" -> { tasks.clear(); saveLists(); "Attività eliminate." }
        n == "mostra note" || n == "leggi note" -> if (notes.isEmpty()) "Nessuna nota." else notes.joinToString("\n") { "• $it" }
        n == "cancella note" -> { notes.clear(); saveLists(); "Note eliminate." }
        n.contains("batteria") && !n.contains("impostazion") && !n.contains("risparmio") && !n.startsWith("apri") -> {
            val s = readStats(); "Batteria al ${s.battery}%" + (if (s.charging) ", in carica." else ".")
        }
        else -> null
    }

    private fun buildTurns(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        messages.filter { it.who == "TU" || it.who == "JARVIS" }.takeLast(14).forEach { m ->
            val role = if (m.who == "TU") "user" else "model"
            if (out.isEmpty() && role == "model") return@forEach
            if (out.isNotEmpty() && out.last().first == role) out[out.lastIndex] = role to (out.last().second + "\n" + m.message)
            else out.add(role to m.message)
        }
        return out
    }

    private fun systemPrompt(): String {
        val now = SimpleDateFormat("EEEE d MMMM yyyy, HH:mm", Locale.ITALIAN).format(Date())
        return """Sei J.A.R.V.I.S., l'assistente personale di $userName su uno smartphone Android (${Build.MANUFACTURER} ${Build.MODEL}).
Rispondi sempre in italiano, in modo conciso (massimo 3 frasi), naturale da ascoltare a voce: niente markdown, elenchi o emoji. Tono cortese e brillante, come il JARVIS di Iron Man.
Data e ora attuali: $now. Batteria: ${stats.battery}%.
Se l'utente chiede di compiere un'azione sul telefono, la PRIMA riga della risposta deve essere esattamente
AZIONE: {json su una sola riga}
seguita da una breve frase di conferma. Azioni disponibili:
{"type":"open","app":"nome app o impostazione"}
{"type":"call","who":"nome contatto o numero"}
{"type":"sms","who":"...","text":"..."}
{"type":"whatsapp","who":"...","text":"..."}
{"type":"email","to":"indirizzo","subject":"...","body":"..."}
{"type":"alarm","hour":7,"minute":30,"label":"..."}
{"type":"timer","seconds":600,"label":"..."}
{"type":"event","title":"...","start":"yyyy-MM-ddTHH:mm","end":"yyyy-MM-ddTHH:mm"}
{"type":"navigate","to":"destinazione"}
{"type":"search","q":"..."}
{"type":"play","q":"brano o artista"}
{"type":"torch","on":true}
{"type":"volume","level":50}  oppure  {"type":"volume","dir":"up"}
{"type":"settings","page":"wifi|bluetooth|posizione|nfc|dati|aereo|non disturbare|batteria|schermo|suoni|app|generali"}
{"type":"note","text":"..."}
{"type":"task","text":"..."}
{"type":"camera"}
Usa solo queste azioni e una sola per risposta. Non dire di aver fatto cose fuori da questo elenco. Per domande normali rispondi senza riga AZIONE."""
    }

    private fun send(raw: String) {
        val q = raw.trim()
        if (q.isBlank() || isBusy) return
        messages.add(Line("TU", q))
        val n = q.lowercase(Locale.ITALIAN)
        localAnswer(n)?.let { reply(it, "LOCALE"); return }
        ReportSync.answer(this, n)?.let { reply(it, "REPORT"); return }
        Commands.parse(q)?.let { cmd -> reply(execute(cmd).msg, "COMANDO"); return }

        isBusy = true
        engineStatus = "ELABORAZIONE"
        val turns = buildTurns()
        val system = systemPrompt()
        val ctx = messages.takeLast(8).joinToString("\n") { "${it.who}: ${it.message}" }
        val useCloud = onlineEnabled && apiKey.isNotBlank()
        val key = apiKey
        scope.launch {
            var engine = "LOCALE"
            var cmd: Cmd? = null
            var cloudError: String? = null
            val text = withContext(Dispatchers.IO) {
                var out: String? = null
                if (useCloud) {
                    try {
                        val parsed = Commands.fromGemini(GeminiClient.chat(key, system, turns))
                        cmd = parsed.first; out = parsed.second; engine = "GEMINI"
                    } catch (e: Exception) { cloudError = e.message }
                }
                out ?: if (brain.available()) {
                    try { brain.respond(q, ctx) } catch (e: Exception) { "Motore locale non disponibile: ${e.message}" }
                } else if (!useCloud) "Configura la API key di Gemini nella sezione Sistema, oppure importa un modello locale."
                else "Gemini non è raggiungibile e non c'è un modello locale di riserva."
            }
            cloudError?.let { sys("Gemini non disponibile: $it") }
            val c = cmd
            if (c != null) {
                val r = execute(c)
                reply(if (!r.ok || text.isBlank()) r.msg else text, "GEMINI")
            } else reply(text.ifBlank { "Fatto." }, engine)
            isBusy = false
        }
    }

    // ───────────── Esecuzione azioni ─────────────
    private fun ok(m: String) = Outcome(true, m)
    private fun fail(m: String) = Outcome(false, m)
    private fun tryStart(i: Intent): Boolean = try { startActivity(i); true } catch (e: Exception) { false }

    private fun execute(c: Cmd): Outcome = try {
        when (c.type) {
            "open" -> openApp(c["app"])
            "call", "sms", "whatsapp" -> contactAction(c)
            "email" -> {
                val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).apply {
                    if (c["to"].contains("@")) putExtra(Intent.EXTRA_EMAIL, arrayOf(c["to"]))
                    putExtra(Intent.EXTRA_SUBJECT, c["subject"]); putExtra(Intent.EXTRA_TEXT, c["body"])
                }
                if (tryStart(i)) ok("Email pronta da inviare.") else fail("Nessuna app email disponibile.")
            }
            "alarm" -> {
                val h = c["hour"].toIntOrNull()
                val m = c["minute"].toIntOrNull() ?: 0
                if (h == null) { if (tryStart(Intent(AlarmClock.ACTION_SET_ALARM))) ok("Apro le sveglie.") else fail("App orologio non disponibile.") }
                else if (tryStart(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, m)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, c["label"].ifBlank { "JARVIS" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true)))
                    ok("Sveglia impostata alle %d:%02d.".format(h, m))
                else fail("Non riesco a impostare la sveglia.")
            }
            "timer" -> {
                val sec = c["seconds"].toIntOrNull()
                if (sec == null || sec <= 0) { if (tryStart(Intent(AlarmClock.ACTION_SET_TIMER))) ok("Apro il timer.") else fail("Timer non disponibile.") }
                else if (tryStart(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, sec)
                        .putExtra(AlarmClock.EXTRA_MESSAGE, c["label"].ifBlank { "JARVIS" }).putExtra(AlarmClock.EXTRA_SKIP_UI, true)))
                    ok("Timer di ${durata(sec)} avviato.")
                else fail("Non riesco ad avviare il timer.")
            }
            "event" -> {
                val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, c["title"])
                parseIso(c["start"])?.let { st ->
                    i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, st)
                    i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, parseIso(c["end"]) ?: (st + 3_600_000L))
                }
                if (tryStart(i)) ok("Evento pronto: confermalo nel calendario.") else fail("Calendario non disponibile.")
            }
            "navigate" -> if (tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(c["to"])))) ||
                tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(c["to"]))))) ok("Navigazione verso ${c["to"]}.")
                else fail("Nessuna app di mappe disponibile.")
            "search" -> if (tryStart(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, c["q"])) ||
                tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(c["q"]))))) ok("Cerco ${c["q"]}.")
                else fail("Nessun browser disponibile.")
            "play" -> if (tryStart(Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(SearchManager.QUERY, c["q"])
                    .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")) ||
                tryStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(c["q"]))))) ok("Riproduco ${c["q"]}.")
                else fail("Nessuna app musicale disponibile.")
            "torch" -> setTorch(when (c["on"]) { "true" -> true; "false" -> false; else -> null })
            "volume" -> setVolume(c["level"].toIntOrNull(), c["dir"])
            "settings" -> {
                val action = settingsAction(Commands.norm("impostazioni " + c["page"])) ?: Settings.ACTION_SETTINGS
                if (tryStart(Intent(action))) ok("Apro le impostazioni ${c["page"]}.") else fail("Impostazioni non disponibili.")
            }
            "note" -> { notes.add(c["text"]); saveLists(); ok("Nota salvata.") }
            "task" -> { tasks.add(c["text"]); saveLists(); ok("Attività aggiunta.") }
            "camera" -> if (tryStart(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))) ok("Fotocamera pronta.") else fail("Fotocamera non disponibile.")
            else -> fail("Azione non supportata: ${c.type}.")
        }
    } catch (e: Exception) { fail("Non ci sono riuscito: ${e.message}") }

    private fun durata(s: Int): String = when {
        s >= 3600 -> "${s / 3600} ore" + (if (s % 3600 >= 60) " e ${(s % 3600) / 60} minuti" else "")
        s >= 60 -> "${s / 60} minuti" + (if (s % 60 > 0) " e ${s % 60} secondi" else "")
        else -> "$s secondi"
    }
    private fun parseIso(v: String): Long? = try {
        if (v.length < 16) null else SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.ITALIAN).parse(v.take(16))?.time
    } catch (e: Exception) { null }

    private fun loadApps() {
        val pm = packageManager
        launcherApps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .filter { it.second != packageName }
    }

    private fun findApp(n: String): Pair<String, String>? {
        if (launcherApps.isEmpty()) loadApps()
        var best: Pair<String, String>? = null
        var bestScore = Int.MAX_VALUE
        for (a in launcherApps) {
            val l = Commands.norm(a.first)
            if (l.isEmpty()) continue
            val sc = when {
                l == n -> 0
                l.startsWith(n) && n.length >= 2 -> 1
                n.startsWith(l) && l.length >= 3 -> 2
                l.contains(n) && n.length >= 3 -> 3
                n.contains(l) && l.length >= 4 -> 4
                else -> { val d = Commands.lev(l, n); if (d <= maxOf(1, n.length / 4)) 5 + d else Int.MAX_VALUE }
            }
            if (sc < bestScore) { bestScore = sc; best = a }
        }
        return best
    }

    private fun settingsAction(n: String): String? {
        val isSet = n.startsWith("impostazion") || n.startsWith("settings") || n.startsWith("modalita")
        fun k(vararg w: String) = w.any { n.contains(it) }
        return when {
            k("wifi") -> Settings.ACTION_WIFI_SETTINGS
            k("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            k("nfc") -> Settings.ACTION_NFC_SETTINGS
            k("gps", "posizion", "localizzazion") -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            k("aereo") -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            k("nondisturbare") -> "android.settings.ZEN_MODE_SETTINGS"
            k("hotspot") -> Settings.ACTION_WIRELESS_SETTINGS
            !isSet -> null
            k("dati", "rete", "connession", "mobile") -> Settings.ACTION_WIRELESS_SETTINGS
            k("batteri", "risparmio") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            k("schermo", "display", "luminosit") -> Settings.ACTION_DISPLAY_SETTINGS
            k("suon", "audio", "volume", "suoneria") -> Settings.ACTION_SOUND_SETTINGS
            k("archiviazion", "memoria", "spazio") -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            k("lingua", "tastiera") -> Settings.ACTION_LOCALE_SETTINGS
            k("dataeora", "orario") -> Settings.ACTION_DATE_SETTINGS
            k("sicurezza") -> Settings.ACTION_SECURITY_SETTINGS
            k("accessibilit") -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            k("app", "applicazion") -> Settings.ACTION_APPLICATION_SETTINGS
            else -> Settings.ACTION_SETTINGS
        }
    }

    private fun categoryIntent(n: String): Intent? {
        fun sel(cat: String) = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, cat)
        return when (n) {
            "fotocamera", "camera", "macchinafotografica" -> Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            "telefono", "tastierino", "dialer" -> Intent(Intent.ACTION_DIAL)
            "messaggi", "sms" -> sel(Intent.CATEGORY_APP_MESSAGING)
            "galleria" -> sel(Intent.CATEGORY_APP_GALLERY)
            "calendario", "agenda" -> sel(Intent.CATEGORY_APP_CALENDAR)
            "browser", "internet" -> sel(Intent.CATEGORY_APP_BROWSER)
            "mappe", "mappa" -> sel(Intent.CATEGORY_APP_MAPS)
            "email", "mail", "posta" -> sel(Intent.CATEGORY_APP_EMAIL)
            "musica" -> sel(Intent.CATEGORY_APP_MUSIC)
            "contatti", "rubrica" -> sel(Intent.CATEGORY_APP_CONTACTS)
            "calcolatrice" -> sel(Intent.CATEGORY_APP_CALCULATOR)
            "sveglia", "sveglie", "orologio" -> Intent(AlarmClock.ACTION_SHOW_ALARMS)
            "file", "documenti", "archivio" -> sel(Intent.CATEGORY_APP_FILES)
            else -> null
        }
    }

    private fun openApp(name: String): Outcome {
        val n = Commands.norm(name)
        if (n.isEmpty()) return fail("Quale app devo aprire?")
        settingsAction(n)?.let { return if (tryStart(Intent(it))) ok("Apro le impostazioni.") else fail("Impostazioni non disponibili.") }
        categoryIntent(n)?.let { if (tryStart(it)) return ok("Apro $name.") }
        val app = findApp(n) ?: return fail("Non trovo l'app $name.")
        val i = packageManager.getLaunchIntentForPackage(app.second) ?: return fail("Non riesco ad aprire ${app.first}.")
        return if (tryStart(i)) ok("Apro ${app.first}.") else fail("Non riesco ad aprire ${app.first}.")
    }

    private fun lookupContact(name: String): String? {
        val target = Commands.norm(name)
        if (target.isEmpty()) return null
        var best: String? = null
        var bestScore = Int.MAX_VALUE
        val proj = arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER)
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, proj, null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val dn = cur.getString(0) ?: continue
                val num = cur.getString(1) ?: continue
                val l = Commands.norm(dn)
                val sc = when {
                    l == target -> 0
                    l.startsWith(target) -> 1
                    l.contains(target) -> 2
                    else -> { val d = Commands.lev(l, target); if (d <= 2) 3 + d else Int.MAX_VALUE }
                }
                if (sc < bestScore) { bestScore = sc; best = num }
            }
        }
        return best?.filter { it.isDigit() || it == '+' }
    }

    private fun waNumber(num: String): String {
        var d = num.filter { it.isDigit() }
        if (num.startsWith("00")) d = d.removePrefix("00")
        else if (!num.startsWith("+") && d.length in 9..10 && (d.startsWith("3") || d.startsWith("0"))) d = "39$d"
        return d
    }

    private fun contactAction(c: Cmd): Outcome {
        val who = c["who"].trim()
        if (who.isEmpty()) return fail("A chi?")
        val digits = who.filter { it.isDigit() || it == '+' }
        val num: String
        if (digits.count { it.isDigit() } >= 5) num = digits
        else {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                pendingAfterPerm = { reply(contactAction(c).msg, "COMANDO") }
                permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                return fail("Mi serve l'accesso alla rubrica: concedilo e procedo.")
            }
            num = lookupContact(who) ?: return fail("Non trovo $who in rubrica.")
        }
        return when (c.type) {
            "call" -> when {
                checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED -> {
                    pendingAfterPerm = { reply(contactAction(c).msg, "COMANDO") }
                    permLauncher.launch(arrayOf(Manifest.permission.CALL_PHONE))
                    fail("Concedi il permesso per le chiamate e chiamo $who.")
                }
                tryStart(Intent(Intent.ACTION_CALL, Uri.parse("tel:$num"))) -> ok("Chiamo $who.")
                else -> fail("Non riesco ad avviare la chiamata.")
            }
            "sms" -> if (tryStart(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$num")).putExtra("sms_body", c["text"])))
                ok("Messaggio per $who pronto, premi invia.") else fail("Nessuna app SMS disponibile.")
            else -> {
                val uri = Uri.parse("https://api.whatsapp.com/send?phone=${waNumber(num)}&text=${Uri.encode(c["text"])}")
                if (tryStart(Intent(Intent.ACTION_VIEW, uri).setPackage("com.whatsapp")) || tryStart(Intent(Intent.ACTION_VIEW, uri)))
                    ok("WhatsApp aperto con $who" + (if (c["text"].isNotBlank()) ", messaggio pronto: premi invia." else "."))
                else fail("WhatsApp non disponibile.")
            }
        }
    }

    private fun setTorch(on: Boolean?): Outcome {
        val camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = camera.cameraIdList.firstOrNull {
            camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        } ?: return fail("Flash non disponibile.")
        val target = on ?: !torchEnabled
        return try {
            camera.setTorchMode(id, target); torchEnabled = target
            ok(if (target) "Torcia accesa." else "Torcia spenta.")
        } catch (e: Exception) { fail("Torcia: ${e.message}") }
    }

    private fun setVolume(level: Int?, dir: String): Outcome {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (level != null) {
            val l = level.coerceIn(0, 100)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, (max * l / 100f).roundToInt(), AudioManager.FLAG_SHOW_UI)
            return ok(if (l == 0) "Audio silenziato." else "Volume al $l%.")
        }
        val down = dir == "down"
        repeat(2) { i ->
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (down) AudioManager.ADJUST_LOWER else AudioManager.ADJUST_RAISE,
                if (i == 0) AudioManager.FLAG_SHOW_UI else 0)
        }
        return ok(if (down) "Volume abbassato." else "Volume alzato.")
    }

    // ───────────── Modelli locali ─────────────
    private fun modelsDir(): File = (getExternalFilesDir("models") ?: File(filesDir, "models")).apply { mkdirs() }

    private fun activateModel(f: File, label: String) {
        brain.modelFile = f
        activeModelPath = f.absolutePath
        prefs.edit().putString("model_path", f.absolutePath).apply()
        modelLoaded = brain.available()
        modelsVersion++
        sys("$label pronto: è il motore offline di riserva.")
    }

    private fun deleteModel(m: LocalModel) {
        val f = File(modelsDir(), m.file)
        if (activeModelPath == f.absolutePath) {
            brain.modelFile = null; activeModelPath = ""
            prefs.edit().remove("model_path").apply()
        }
        f.delete()
        modelLoaded = brain.available()
        modelsVersion++
        sys("${m.name} eliminato.")
    }

    /** Segue i redirect di Hugging Face con il token, così DownloadManager riceve l'URL finale firmato. */
    private fun resolveDownloadUrl(start: String, token: String): String {
        var url = start
        repeat(6) {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "HEAD"
                connectTimeout = 15000; readTimeout = 15000
                if (token.isNotBlank() && u.host.endsWith("huggingface.co")) setRequestProperty("Authorization", "Bearer $token")
            }
            try {
                val code = conn.responseCode
                when {
                    code in 300..399 -> {
                        val loc = conn.getHeaderField("Location") ?: return url
                        url = URL(u, loc).toString()
                    }
                    code == 401 || code == 403 -> throw IllegalStateException(
                        "Accesso negato (HTTP $code): accetta la licenza del modello su huggingface.co e verifica il token.")
                    code == 404 -> throw IllegalStateException("Modello non trovato (HTTP 404).")
                    else -> return url
                }
            } finally { conn.disconnect() }
        }
        return url
    }

    private fun startDownload(m: LocalModel) {
        if (downloads.containsKey(m.id)) return
        val dir = modelsDir()
        if (StatFs(dir.path).availableBytes < m.bytes + 300_000_000L) { err("Spazio insufficiente per ${m.name} (${m.size})."); return }
        if (m.needsToken && hfToken.isBlank()) {
            err("${m.name} richiede un token Hugging Face e l'accettazione della licenza Gemma sul sito.")
            return
        }
        downloads[m.id] = -1f
        scope.launch {
            try {
                val token = if (m.needsToken) hfToken else ""
                val finalUrl = withContext(Dispatchers.IO) { resolveDownloadUrl(m.url, token) }
                File(dir, m.file).delete()
                val req = DownloadManager.Request(Uri.parse(finalUrl))
                    .setTitle("JARVIS • ${m.name}")
                    .setDescription("Modello AI locale (${m.size})")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalFilesDir(this@MainActivity, "models", m.file)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(false)
                if (token.isNotBlank() && Uri.parse(finalUrl).host?.endsWith("huggingface.co") == true)
                    req.addRequestHeader("Authorization", "Bearer $token")
                val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
                val id = dm.enqueue(req)
                prefs.edit().putLong("dl_${m.id}", id).apply()
                sys("Download di ${m.name} avviato (${m.size}). Puoi chiudere l'app: continua in background.")
                pollDownload(m, id)
            } catch (e: Exception) {
                downloads.remove(m.id)
                err(e.message ?: "Download fallito.")
            }
        }
    }

    private fun cancelDownload(m: LocalModel) {
        val id = prefs.getLong("dl_${m.id}", -1L)
        if (id >= 0) (getSystemService(DOWNLOAD_SERVICE) as DownloadManager).remove(id)
        prefs.edit().remove("dl_${m.id}").apply()
        downloads.remove(m.id)
        File(modelsDir(), m.file).delete()
        modelsVersion++
    }

    private suspend fun pollDownload(m: LocalModel, id: Long) {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        while (true) {
            val st = withContext(Dispatchers.IO) {
                try {
                    dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (!c.moveToFirst()) null else DlState(
                            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                            c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                            c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)))
                    }
                } catch (e: Exception) { null }
            }
            if (!downloads.containsKey(m.id)) return
            if (st == null) {
                downloads.remove(m.id); prefs.edit().remove("dl_${m.id}").apply()
                err("Download di ${m.name} interrotto."); return
            }
            when (st.status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    downloads.remove(m.id); prefs.edit().remove("dl_${m.id}").apply()
                    activateModel(File(modelsDir(), m.file), m.name)
                    return
                }
                DownloadManager.STATUS_FAILED -> {
                    downloads.remove(m.id); prefs.edit().remove("dl_${m.id}").apply()
                    File(modelsDir(), m.file).delete(); modelsVersion++
                    err(if (st.reason == 401 || st.reason == 403) "Accesso negato a ${m.name}: accetta la licenza su Hugging Face e controlla il token."
                        else "Download di ${m.name} fallito (codice ${st.reason}).")
                    return
                }
                else -> downloads[m.id] = if (st.total > 0) st.done.toFloat() / st.total else -1f
            }
            delay(1000)
        }
    }

    // ───────────── Report mensili dal PC (sola lettura) ─────────────
    private fun syncReports(silent: Boolean = false) {
        if (reportsBusy) return
        reportsBusy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { ReportSync.sync(applicationContext) }
            reportsBusy = false; reportsVersion++
            if (!silent || r.changed > 0) sys(r.msg)
        }
    }

    private fun pairReports(raw: String) {
        val p = ReportSync.parsePairing(raw)
        if (p == null) { err("QR non valido: sul PC scrivi \"abbina telefono report\" e inquadra quel QR."); return }
        ReportSync.savePairing(this, p)
        ReportSyncWorker.schedule(this)
        sys("Abbinato al PC ${p.host}. Scarico i report…")
        syncReports()
    }

    private fun scanReportQr() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { b -> pairReports(b.rawValue ?: "") }
            .addOnFailureListener { e -> err("Scansione non riuscita: ${e.message}. Puoi incollare il link del QR.") }
    }

    private fun unpairReports() {
        ReportSync.unpair(this); ReportSyncWorker.cancel(this); reportsVersion++
        sys("Telefono scollegato dal PC. Le copie già scaricate restano consultabili.")
    }

    /** Apre la copia locale dell'Excel in sola lettura (all'altra app non viene dato permesso di scrittura). */
    private fun openReportExcel(month: String) {
        val f = ReportSync.excelFile(this, month)
        if (!f.exists()) { err("Excel di ${ReportSync.monthLabel(month)} non ancora scaricato."); return }
        val uri = FileProvider.getUriForFile(this, "$packageName.reports", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(Intent.createChooser(i, "Apri report")) } catch (e: Exception) { err("Nessuna app per aprire file Excel.") }
    }

    // ═════════════════════════ UI ═════════════════════════
    @Composable
    private fun NexusApp() {
        LaunchedEffect(Unit) {
            while (true) {
                stats = withContext(Dispatchers.IO) { try { readStats() } catch (e: Exception) { stats } }
                delay(3000)
            }
        }
        BackHandler(enabled = screen != Screen.HOME) {
            screen = when (screen) { Screen.MODULE, Screen.MEMORY, Screen.AUTOMATE, Screen.REPORTS -> Screen.MODULES; else -> Screen.HOME }
        }
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, secondary = accent2, background = Bg, surface = Panel, onSurface = Color.White)) {
            Column(Modifier.fillMaxSize().background(Bg).systemBarsPadding().imePadding()) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    Crossfade(targetState = screen, label = "screen") { s ->
                        when (s) {
                            Screen.HOME -> HomeScreen()
                            Screen.CHAT -> ChatScreen()
                            Screen.MODULES -> ModulesScreen()
                            Screen.MODULE -> ModuleDetailScreen()
                            Screen.DEVICE -> DeviceScreen()
                            Screen.AUTOMATE -> AutomationsScreen()
                            Screen.MEMORY -> MemoryScreen()
                            Screen.SETTINGS -> SettingsScreen()
                            Screen.REPORTS -> ReportsScreen()
                        }
                    }
                }
                BottomNav()
            }
        }
    }

    // ───────────── HOME ─────────────
    @Composable
    private fun HomeScreen() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                HexLogo(44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("J.A.R.V.I.S.", color = Color.White, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 3.sp)
                    Text("NEXUS 3.0", color = accent, fontSize = 12.sp, letterSpacing = 4.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    val cloud = onlineEnabled && apiKey.isNotBlank() && stats.online
                    Text(if (cloud) "● ONLINE" else "○ OFFLINE", color = if (cloud) Green else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (modelLoaded) "● LOCALE" else "○ LOCALE", color = if (modelLoaded) Green else Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            val haptic = LocalHapticFeedback.current
            val ripple = remember { Animatable(0f) }
            val uiScope = rememberCoroutineScope()
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                HudCore(Modifier.fillMaxSize().padding(horizontal = 30.dp).pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            uiScope.launch { ripple.snapTo(0f); ripple.animateTo(1f, tween(900)) }
                            listen()
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            screen = Screen.CHAT
                        })
                }, isBusy, listening, ripple.value, micLevel)
                val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 9.sp, color = Muted, lineHeight = 13.sp)
                Column(Modifier.align(Alignment.TopStart)) {
                    Text(Build.MODEL, style = mono, color = Color.White)
                    Text("Android ${Build.VERSION.RELEASE}", style = mono)
                    Text("NEXUS Core", style = mono)
                    Text("v3.0.0", style = mono)
                }
                Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
                    Text("RAM  ${stats.ramPct}%", style = mono, color = Color.White)
                    Text("BAT  ${stats.battery}%", style = mono)
                    Text("TEMP %.0f°C".format(Locale.ITALIAN, stats.temp), style = mono)
                    Text(if (stats.online) "NET  OK" else "NET  --", style = mono)
                }
                Text(if (isBusy) "ELABORAZIONE…" else if (listening) (liveText.ifBlank { "ASCOLTO…" }) else "TOCCA IL NUCLEO PER PARLARE",
                    Modifier.align(Alignment.BottomCenter), color = accent, fontSize = 10.sp, letterSpacing = 2.sp)
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${greeting()}, $userName.", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Come posso assisterti oggi?", color = Muted, fontSize = 13.sp)
            }
            modules.take(6).chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m -> ModuleTile(m.icon, m.title, m.sub, Modifier.weight(1f)) { openModule(m.id) } }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Telefono", "WhatsApp", "File", "Fotocamera", "Torcia", "Impostazioni").forEach { a ->
                    CircleAction(actionIcon(a), a, if (a == "Torcia" && torchEnabled) Cyan else accent2) { quickAction(a) }
                }
            }
            val active = routines.filter { routineOn[it.id] == true }
            if (active.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(active) { r -> Chip(r.title, r.icon, accent) { runRoutine(r.id) } }
                }
            }
            CommandBar { t -> screen = Screen.CHAT; send(t) }
            Spacer(Modifier.height(8.dp))
        }
    }

    // ───────────── CHAT ─────────────
    @Composable
    private fun ChatScreen() {
        val listState = rememberLazyListState()
        LaunchedEffect(messages.size, isBusy) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size) }
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(CircleShape).clickable { listen() }) { HexLogo(34.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("J.A.R.V.I.S.", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp, letterSpacing = 2.sp)
                    Text(if (isBusy) "sta elaborando…" else "motore: ${engineStatus.lowercase()}", color = accent2, fontSize = 11.sp)
                }
                IconButton(onClick = { quickAction("Cancella chat") }) { Icon(Icons.Rounded.DeleteSweep, "Cancella", tint = Muted) }
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { m -> Bubble(m) }
                item {
                    if (isBusy) Text("JARVIS sta pensando…", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 40.dp, top = 4.dp))
                    else Spacer(Modifier.height(4.dp))
                }
            }
            LazyRow(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val sugg = listOf("Briefing" to Icons.Rounded.WbSunny, "Che ore sono?" to Icons.Rounded.Schedule,
                    "Batteria" to Icons.Rounded.BatteryChargingFull, "Mostra note" to Icons.Rounded.Description,
                    "Mostra attività" to Icons.Rounded.TaskAlt, "Apri WhatsApp" to Icons.Rounded.Sms,
                    "Timer 5 minuti" to Icons.Rounded.Timer, "Accendi la torcia" to Icons.Rounded.FlashlightOn)
                items(sugg) { (t, ic) -> Chip(t, ic, accent2) { send(t) } }
            }
            if (listening) {
                if (liveText.isNotBlank()) Text("“$liveText”", color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                Waveform(Modifier.fillMaxWidth().height(40.dp).padding(bottom = 6.dp))
            }
            CommandBar { send(it) }
            Spacer(Modifier.height(8.dp))
        }
    }

    @Composable
    private fun Bubble(m: Line) {
        when (m.who) {
            "TU" -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(m.message, color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                        .background(Brush.horizontalGradient(listOf(accent2.copy(alpha = .35f), accent2.copy(alpha = .2f))))
                        .padding(horizontal = 14.dp, vertical = 10.dp))
            }
            "JARVIS" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                HexLogo(28.dp)
                Spacer(Modifier.width(8.dp))
                Text(m.message, color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                        .background(Panel2).border(1.dp, accent.copy(alpha = .45f), RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp))
            }
            else -> Text(m.message, color = if (m.who == "ERROR") NeonRed else Muted, fontSize = 11.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
        }
    }

    // ───────────── MODULI ─────────────
    @Composable
    private fun ModulesScreen() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenHeader("MODULI OPERATIVI", null)
            modules.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m -> ModuleTile(m.icon, m.title, m.sub, Modifier.weight(1f)) { openModule(m.id) } }
                }
            }
            Spacer(Modifier.height(6.dp))
            NeonCard(Modifier.fillMaxWidth(), accent2) {
                Text("COMANDI RAPIDI", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                listOf("apri <app>" to "qualsiasi app o impostazione", "chiama <nome>" to "chiamata dalla rubrica",
                    "scrivi su whatsapp a <nome>: <testo>" to "messaggio pronto", "svegliami alle 7 e mezza" to "imposta sveglia",
                    "timer 10 minuti" to "conto alla rovescia", "naviga verso <luogo>" to "navigatore",
                    "metti <brano>" to "musica", "volume al 50" to "audio", "accendi la torcia" to "flash",
                    "attiva il wifi" to "pannello rapido", "ricorda <testo>" to "salva una nota").forEach { (c, d) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(c, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(170.dp))
                        Text(d, color = Muted, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    @Composable
    private fun ModuleDetailScreen() {
        val m = modules.firstOrNull { it.id == currentModule } ?: modules.first()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenHeader(m.title) { screen = Screen.MODULES }
            NeonCard(Modifier.fillMaxWidth(), accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlowIcon(m.icon, accent2, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(m.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(m.sub, color = Muted, fontSize = 12.sp)
                        if (m.id == "AI CORE") Text(
                            (if (apiKey.isNotBlank()) "Gemini configurato" else "Gemini non configurato") + " • " +
                                (if (modelLoaded) "modello locale pronto" else "nessun modello locale"),
                            color = accent2, fontSize = 11.sp)
                    }
                }
            }
            m.actions.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { a -> ModuleTile(actionIcon(a), a, "", Modifier.weight(1f)) { quickAction(a) } }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            LastLog()
        }
    }

    // ───────────── DISPOSITIVO ─────────────
    @Composable
    private fun DeviceScreen() {
        val s = stats
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("DISPOSITIVO", null)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NeonCard(Modifier.weight(1.1f), Green, horizontal = Alignment.CenterHorizontally) {
                    Box(Modifier.size(130.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val st = 11.dp.toPx()
                            val tl = Offset(st / 2, st / 2); val sz = Size(size.width - st, size.height - st)
                            drawArc(Stroke1, 135f, 270f, false, tl, sz, style = Stroke(st, cap = StrokeCap.Round))
                            drawArc(Brush.sweepGradient(listOf(Cyan, Green, Cyan)), 135f, 270f * s.battery / 100f, false, tl, sz,
                                style = Stroke(st, cap = StrokeCap.Round))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${s.battery}%", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text("Batteria", color = Muted, fontSize = 11.sp)
                            Text(if (s.charging) "In carica" else "Scarica", color = if (s.charging) Green else Muted, fontSize = 10.sp)
                        }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatCard(Icons.Rounded.Thermostat, "%.1f°C".format(Locale.ITALIAN, s.temp), "Temperatura", if (s.temp > 40) NeonRed else accent2)
                    StatCard(Icons.Rounded.Memory, "${s.ramPct}%", "RAM usata", accent2)
                    StatCard(Icons.Rounded.SdStorage, "%.0f GB".format(Locale.ITALIAN, s.storageFree), "Liberi", accent2)
                }
            }
            Text("CONNESSIONI", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            val toggles = listOf(
                Triple("Wi-Fi", s.wifi, if (s.wifi) "Attivo" else "Spento"),
                Triple("Bluetooth", s.bt, if (s.bt) "Attivo" else "Spento"),
                Triple("Dati mobili", s.online, if (s.online) "Online" else "Offline"),
                Triple("Posizione", s.location, if (s.location) "Attiva" else "Spenta"),
                Triple("NFC", false, "Impostazioni"),
                Triple("Torcia", torchEnabled, if (torchEnabled) "Accesa" else "Spenta"),
            )
            toggles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (name, on, label) -> ToggleTile(name, on, label, Modifier.weight(1f)) { quickAction(name) } }
                }
            }
            NeonCard(Modifier.fillMaxWidth(), Stroke1) {
                Text("SISTEMA", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                listOf("Modello" to "${Build.MANUFACTURER} ${Build.MODEL}",
                    "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
                    "RAM" to "%.1f / %.1f GB liberi".format(Locale.ITALIAN, s.ramFree, s.ramTotal),
                    "Memoria" to "%.0f / %.0f GB liberi".format(Locale.ITALIAN, s.storageFree, s.storageTotal)).forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, color = Muted, fontSize = 12.sp, modifier = Modifier.width(80.dp))
                        Text(v, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Batteria", "Archiviazione", "App").forEach { a -> ModuleTile(actionIcon(a), a, "", Modifier.weight(1f)) { quickAction(a) } }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    // ───────────── AUTOMAZIONI ─────────────
    @Composable
    private fun AutomationsScreen() {
        var tab by remember { mutableStateOf(0) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenHeader("AUTOMAZIONI") { screen = Screen.MODULES }
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(4.dp)) {
                listOf("Routine", "Pianificazione").forEachIndexed { i, t ->
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                        .background(if (tab == i) accent2.copy(alpha = .9f) else Color.Transparent)
                        .clickable { tab = i }.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                        Text(t, color = if (tab == i) Color.White else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (tab == 0) {
                routines.forEach { r ->
                    NeonCard(Modifier.fillMaxWidth(), if (routineOn[r.id] == true) accent else Stroke1) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            GlowIcon(r.icon, accent, 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(r.desc, color = Muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { runRoutine(r.id) }) { Icon(Icons.Rounded.PlayArrow, "Esegui", tint = accent2) }
                            Switch(checked = routineOn[r.id] == true, onCheckedChange = {
                                routineOn[r.id] = it; prefs.edit().putBoolean("routine_${r.id}", it).apply()
                            }, colors = SwitchDefaults.colors(checkedTrackColor = accent2, checkedThumbColor = Color.White,
                                uncheckedTrackColor = Panel2, uncheckedBorderColor = Stroke1))
                        }
                    }
                }
                Text("Le routine attive compaiono come scorciatoie nella Home.", color = Muted, fontSize = 11.sp)
            } else {
                listOf("Promemoria", "Timer", "Calendario", "Farmaco", "Acqua").chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { a -> ModuleTile(actionIcon(a), a, "", Modifier.weight(1f)) { quickAction(a) } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            LastLog()
        }
    }

    // ───────────── MEMORY ─────────────
    @Composable
    private fun MemoryScreen() {
        var text by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenHeader("MEMORY") { screen = Screen.MODULES }
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                placeholder = { Text("Scrivi una nota o un'attività…", color = Muted) }, colors = fieldColors())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { if (text.isNotBlank()) { notes.add(text.trim()); saveLists(); text = "" } },
                    Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent2)) { Text("+ NOTA") }
                Button(onClick = { if (text.isNotBlank()) { tasks.add(text.trim()); saveLists(); text = "" } },
                    Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent)) { Text("+ ATTIVITÀ") }
            }
            ListCard("ATTIVITÀ (${tasks.size})", tasks, Icons.Rounded.TaskAlt)
            ListCard("NOTE (${notes.size})", notes, Icons.Rounded.Description)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Esporta note", "Importa note", "Copia note").forEach { a -> ModuleTile(actionIcon(a), a, "", Modifier.weight(1f)) { quickAction(a) } }
            }
            Spacer(Modifier.height(10.dp))
        }
    }

    @Composable
    private fun ListCard(title: String, list: MutableList<String>, icon: ImageVector) {
        NeonCard(Modifier.fillMaxWidth(), Stroke1) {
            Text(title, color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            if (list.isEmpty()) Text("Vuoto", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            list.toList().forEachIndexed { i, item ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(item, color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.Close, "Elimina", tint = Muted, modifier = Modifier.size(18.dp)
                        .clickable { if (i < list.size) { list.removeAt(i); saveLists() } })
                }
            }
        }
    }

    // ───────────── IMPOSTAZIONI ─────────────
    @Composable
    private fun SettingsScreen() {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ScreenHeader("SISTEMA", null)
            NeonCard(Modifier.fillMaxWidth(), Stroke1) {
                Text("PROFILO", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                OutlinedTextField(userName, {
                    userName = it; prefs.edit().putString("user_name", it.trim()).apply()
                }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Come ti chiamo") }, colors = fieldColors())
            }
            NeonCard(Modifier.fillMaxWidth(), accent2) {
                Text("AI CORE — GEMINI + LOCALE", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Text("Ogni richiesta va prima a Gemini; se non risponde passa al modello locale. I comandi diretti (apri, chiama, sveglia…) sono istantanei.", color = Muted, fontSize = 11.sp)
                OutlinedTextField(apiKey, {
                    apiKey = it.trim(); prefs.edit().putString("gemini_api_key", apiKey).apply()
                }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    label = { Text("API key Gemini") }, colors = fieldColors())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Priorità a Gemini online", Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                    Switch(onlineEnabled, { onlineEnabled = it; prefs.edit().putBoolean("gemini_enabled", it).apply() },
                        colors = SwitchDefaults.colors(checkedTrackColor = accent2))
                }
                Text("La chiave resta sul telefono (non cifrata). I messaggi inviati a Gemini lasciano il dispositivo: niente dati sensibili.",
                    color = Muted, fontSize = 10.sp)
            }
            QuickAccessCard()
            ModelsCard()
            NeonCard(Modifier.fillMaxWidth(), Stroke1) {
                Text("INTERFACCIA", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Tema invertito (blu / rosso)", Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                    Switch(altTheme, { altTheme = it; prefs.edit().putBoolean("alt_theme", it).apply() },
                        colors = SwitchDefaults.colors(checkedTrackColor = accent2))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = { quickAction("Test voce") }, Modifier.weight(1f)) { Text("TEST VOCE", color = accent2, fontSize = 12.sp) }
                    OutlinedButton(onClick = { quickAction("Cancella chat") }, Modifier.weight(1f)) { Text("CANCELLA CHAT", color = accent, fontSize = 12.sp) }
                }
            }
            Text("J.A.R.V.I.S. NEXUS 3.0 • Kotlin + Jetpack Compose", color = Muted, fontSize = 10.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
        }
    }

    private fun isAssistant(): Boolean = try {
        Build.VERSION.SDK_INT >= 29 && getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_ASSISTANT)
    } catch (e: Exception) { false }

    private fun openAssistantSettings() {
        if (!tryStart(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)) && !tryStart(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)))
            tryStart(Intent(Settings.ACTION_SETTINGS))
    }

    private fun requestTile() {
        if (Build.VERSION.SDK_INT >= 33) {
            try {
                getSystemService(StatusBarManager::class.java).requestAddTileService(
                    ComponentName(this, JarvisTileService::class.java), "JARVIS",
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_tile), mainExecutor
                ) { _ -> }
            } catch (e: Exception) { err("Aggiungi il riquadro JARVIS dal pannello rapido (matita → trascina).") }
        } else sys("Apri il pannello rapido, tocca la matita e trascina il riquadro JARVIS.")
    }

    @Composable
    private fun QuickAccessCard() {
        val tick = resumeTick
        val held = tick >= 0 && isAssistant()
        NeonCard(Modifier.fillMaxWidth(), if (held) Green else accent) {
            Text("ACCESSO RAPIDO (SENZA APRIRE L'APP)", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Assistente predefinito", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (held) "Attivo ✓" else "Non impostato", color = if (held) Green else Muted, fontSize = 11.sp)
                }
                TextButton(onClick = { openAssistantSettings() }) { Text(if (held) "CAMBIA" else "IMPOSTA", color = accent2, fontSize = 12.sp) }
            }
            Text("1) Assistente digitale → JARVIS.  2) Impostazioni → Funzioni avanzate → Tasto laterale → Tieni premuto → Assistente digitale.",
                color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Riquadro nel pannello rapido", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Scorri giù dalla barra di stato → JARVIS", color = Muted, fontSize = 11.sp)
                }
                TextButton(onClick = { requestTile() }) { Text("AGGIUNGI", color = accent2, fontSize = 12.sp) }
            }
            Text("Anche: tieni premuta l'icona di JARVIS → \"Parla con JARVIS\".", color = Muted, fontSize = 10.sp)
        }
    }

    // ───────────── Finestra rapida (assistente) ─────────────
    @Composable
    private fun AssistOverlay() {
        val lastJarvis = messages.lastOrNull { it.who == "JARVIS" }?.message
        val lastUser = messages.lastOrNull { it.who == "TU" }?.message
        val lastErr = messages.lastOrNull { it.who == "ERROR" || it.who == "SYSTEM" }?.message
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        MaterialTheme(colorScheme = darkColorScheme(primary = accent, secondary = accent2, background = Bg, surface = Panel, onSurface = Color.White)) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { finish() }) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .clip(shape).background(Brush.verticalGradient(listOf(Panel2, Bg)))
                    .border(1.dp, Brush.horizontalGradient(listOf(accent, accent2)), shape)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .navigationBarsPadding().imePadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(Modifier.size(width = 40.dp, height = 4.dp).background(Stroke1, RoundedCornerShape(2.dp)))
                    Box(Modifier.size(160.dp).pointerInput(Unit) { detectTapGestures(onTap = { listen() }) }) {
                        HudCore(Modifier.fillMaxSize(), isBusy, listening, 0f, micLevel)
                    }
                    Text(when { isBusy -> "ELABORAZIONE…"; listening -> "TI ASCOLTO"; else -> "TOCCA IL NUCLEO PER PARLARE" },
                        color = accent, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                    val heard = if (listening) liveText else (lastUser ?: "")
                    if (heard.isNotBlank()) Text("“$heard”", color = if (listening) Color.White else Muted, fontSize = 16.sp, textAlign = TextAlign.Center)
                    if (!listening && !isBusy) {
                        val answer = lastJarvis ?: lastErr ?: "${greeting()}, $userName. Come posso aiutarti?"
                        Text(answer, color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState()))
                    }
                    if (listening) Waveform(Modifier.fillMaxWidth().height(36.dp))
                    CommandBar { voiceTurn = false; send(it) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { startActivity(Intent(this@MainActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish() }) {
                            Text("APRI JARVIS", color = accent2, fontSize = 12.sp)
                        }
                        TextButton(onClick = { finish() }) { Text("CHIUDI", color = Muted, fontSize = 12.sp) }
                    }
                }
            }
        }
    }

    @Composable
    private fun ModelsCard() {
        val version = modelsVersion
        NeonCard(Modifier.fillMaxWidth(), accent) {
            Text("MODELLO LOCALE (OFFLINE)", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
            Text("Entra in gioco quando Gemini non è raggiungibile. Meglio scaricare su Wi-Fi.", color = Muted, fontSize = 11.sp)
            localModels.forEach { m -> ModelRow(m, version) }
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(hfToken, { hfToken = it.trim(); prefs.edit().putString("hf_token", hfToken).apply() },
                Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                label = { Text("Token Hugging Face (solo Gemma)") }, colors = fieldColors())
            Text("Per Gemma: accedi a huggingface.co, apri la pagina del modello e accetta la licenza, poi crea un token di lettura in Settings → Access Tokens.",
                color = Muted, fontSize = 10.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = { quickAction("Importa modello") }, Modifier.fillMaxWidth(), enabled = !isBusy) {
                Text("IMPORTA DA FILE (.task / .bin)", color = accent2, fontSize = 12.sp)
            }
        }
    }

    @Composable
    private fun ModelRow(m: LocalModel, version: Int) {
        val f = File(modelsDir(), m.file)
        val dl = downloads[m.id]
        val downloaded = version >= 0 && dl == null && f.exists() && f.length() > 0
        val active = downloaded && activeModelPath == f.absolutePath
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = if (active) Green else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (active) "IN USO • ${m.size}" else "${m.size} • ${m.desc}", color = if (active) Green else Muted, fontSize = 11.sp)
                }
                when {
                    dl != null -> TextButton(onClick = { cancelDownload(m) }) { Text("ANNULLA", color = accent, fontSize = 12.sp) }
                    active -> TextButton(onClick = { deleteModel(m) }) { Text("ELIMINA", color = Muted, fontSize = 12.sp) }
                    downloaded -> Row {
                        TextButton(onClick = { activateModel(f, m.name) }) { Text("USA", color = accent2, fontSize = 12.sp) }
                        TextButton(onClick = { deleteModel(m) }) { Text("ELIMINA", color = Muted, fontSize = 12.sp) }
                    }
                    else -> Button(onClick = { startDownload(m) }, colors = ButtonDefaults.buttonColors(containerColor = accent2),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)) { Text("SCARICA", fontSize = 12.sp) }
                }
            }
            if (dl != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Stroke1)) {
                    if (dl >= 0f) Box(Modifier.fillMaxWidth(dl.coerceIn(0f, 1f)).fillMaxHeight()
                        .background(Brush.horizontalGradient(listOf(accent2, Cyan))))
                }
                Text(if (dl < 0f) "Connessione…" else "${(dl * 100).toInt()}%", color = Muted, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 2.dp))
            }
        }
    }

    // ═════════════════════════ Components ═════════════════════════
    @Composable
    private fun BottomNav() {
        val items = listOf(Triple(Screen.HOME, Icons.Rounded.Home, "Home"), Triple(Screen.CHAT, Icons.Rounded.Forum, "Chat"),
            Triple(Screen.MODULES, Icons.Rounded.Apps, "Moduli"), Triple(Screen.DEVICE, Icons.Rounded.PhoneAndroid, "Dispositivo"),
            Triple(Screen.SETTINGS, Icons.Rounded.Settings, "Sistema"))
        Column(Modifier.fillMaxWidth().background(Panel)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Brush.horizontalGradient(listOf(Color.Transparent, accent.copy(alpha = .7f), accent2.copy(alpha = .7f), Color.Transparent))))
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                items.forEach { (s, ic, label) ->
                    val sel = screen == s || (s == Screen.MODULES && screen in listOf(Screen.MODULE, Screen.AUTOMATE, Screen.MEMORY))
                    Column(Modifier.weight(1f).clickable { screen = s }.padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(ic, label, tint = if (sel) accent else Muted, modifier = Modifier.size(24.dp))
                        Text(label, fontSize = 10.sp, color = if (sel) Color.White else Muted)
                        Box(Modifier.padding(top = 3.dp).size(width = 18.dp, height = 2.dp)
                            .background(if (sel) accent else Color.Transparent, RoundedCornerShape(1.dp)))
                    }
                }
            }
        }
    }

    // ───────────── REPORT ─────────────
    @Composable
    private fun ReportsScreen() {
        val v = reportsVersion
        val pairing = remember(v) { ReportSync.pairing(this@MainActivity) }
        val months = remember(v) { ReportSync.localMonths(this@MainActivity) }
        var open by remember { mutableStateOf<String?>(null) }
        var link by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ScreenHeader("REPORT MENSILI") { screen = Screen.MODULES }
            NeonCard(Modifier.fillMaxWidth(), if (pairing != null) Green else accent) {
                Text("SINCRONIZZAZIONE CON IL PC", color = accent2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(6.dp))
                if (pairing == null) {
                    Text("Non abbinato. Sul PC scrivi a JARVIS \"abbina telefono report\", poi tocca ABBINA e inquadra il QR.",
                        color = Color.White, fontSize = 13.sp)
                } else {
                    Text("PC: ${pairing.host}:${pairing.port}", color = Color.White, fontSize = 13.sp)
                    Text("Ultimo aggiornamento: ${ReportSync.lastSync(this@MainActivity) ?: "mai"}", color = Muted, fontSize = 12.sp)
                    ReportSync.lastError(this@MainActivity)?.let { Text(it, color = accent, fontSize = 12.sp) }
                }
                Text("Sola lettura: il telefono scarica copie, non modifica nulla sul PC. Si aggiorna da solo sul Wi-Fi di casa.",
                    color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = { scanReportQr() }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = accent2)) {
                        Text(if (pairing == null) "ABBINA" else "RIABBINA", fontSize = 12.sp)
                    }
                    if (pairing != null) Button(onClick = { syncReports() }, Modifier.weight(1f), enabled = !reportsBusy,
                        colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                        Text(if (reportsBusy) "AGGIORNO…" else "AGGIORNA ORA", fontSize = 12.sp)
                    }
                }
                if (pairing == null) {
                    OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth().padding(top = 8.dp), singleLine = true,
                        placeholder = { Text("oppure incolla il link jarvisreport://…", color = Muted) }, colors = fieldColors())
                    if (link.isNotBlank()) TextButton(onClick = { pairReports(link); link = "" }) { Text("USA LINK", color = accent2, fontSize = 12.sp) }
                } else {
                    TextButton(onClick = { unpairReports() }) { Text("SCOLLEGA", color = Muted, fontSize = 12.sp) }
                }
            }
            if (months.isEmpty()) Text("Nessun report sul telefono.", color = Muted, fontSize = 12.sp)
            months.forEach { m ->
                NeonCard(Modifier.fillMaxWidth(), Stroke1, onClick = { open = if (open == m.month) null else m.month }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Assessment, null, tint = accent2, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(ReportSync.monthLabel(m.month), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("${m.n} prestazioni" + (m.totale?.let { " • %.2f €".format(Locale.ITALIAN, it) } ?: ""),
                                color = Muted, fontSize = 12.sp)
                        }
                        if (m.hasExcel) TextButton(onClick = { openReportExcel(m.month) }) { Text("EXCEL", color = accent2, fontSize = 12.sp) }
                    }
                    if (open == m.month) {
                        val rows = remember(v, m.month) { ReportSync.rows(this@MainActivity, m.month) }
                        if (rows.isEmpty()) Text("Dettaglio non disponibile.", color = Muted, fontSize = 12.sp)
                        rows.forEach { r ->
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                                Text(r[0].takeLast(5), color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.width(48.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r[1], color = Color.White, fontSize = 13.sp)
                                    Text(r[2], color = Muted, fontSize = 11.sp)
                                }
                                Text("${r[3]} €", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            LastLog()
            Spacer(Modifier.height(10.dp))
        }
    }

    @Composable
    private fun ScreenHeader(title: String, onBack: (() -> Unit)?) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Indietro", tint = Color.White) }
            else Spacer(Modifier.width(4.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, letterSpacing = 2.sp, modifier = Modifier.weight(1f))
            HexLogo(30.dp)
        }
    }

    @Composable
    private fun NeonCard(modifier: Modifier, border: Color, onClick: (() -> Unit)? = null, pad: Dp = 12.dp,
                         horizontal: Alignment.Horizontal = Alignment.Start, content: @Composable ColumnScope.() -> Unit) {
        val shape = RoundedCornerShape(14.dp)
        Column(modifier.clip(shape)
            .background(Brush.verticalGradient(listOf(Panel2, Panel)))
            .border(1.dp, Brush.verticalGradient(listOf(border.copy(alpha = .9f), border.copy(alpha = .2f))), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(pad), horizontalAlignment = horizontal, content = content)
    }

    @Composable
    private fun GlowIcon(icon: ImageVector, color: Color, dim: Dp) {
        Box(Modifier.size(dim).background(Brush.radialGradient(listOf(color.copy(alpha = .3f), Color.Transparent)), CircleShape),
            contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(dim * 0.55f))
        }
    }

    @Composable
    private fun ModuleTile(icon: ImageVector, title: String, sub: String, modifier: Modifier, onClick: () -> Unit) {
        NeonCard(modifier.height(if (sub.isEmpty()) 92.dp else 104.dp), accent, onClick, 8.dp, Alignment.CenterHorizontally) {
            GlowIcon(icon, accent2, 42.dp)
            Spacer(Modifier.height(4.dp))
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (sub.isNotEmpty()) Text(sub, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }

    @Composable
    private fun StatCard(icon: ImageVector, value: String, label: String, color: Color) {
        NeonCard(Modifier.fillMaxWidth(), Stroke1, pad = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(label, color = Muted, fontSize = 10.sp)
                }
            }
        }
    }

    @Composable
    private fun ToggleTile(name: String, on: Boolean, label: String, modifier: Modifier, onClick: () -> Unit) {
        NeonCard(modifier, if (on) Green else Stroke1, onClick, 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(actionIcon(name), null, tint = if (on) Green else Muted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(label, color = if (on) Green else Muted, fontSize = 10.sp)
                }
                Box(Modifier.size(8.dp).background(if (on) Green else Stroke1, CircleShape))
            }
        }
    }

    @Composable
    private fun CircleAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
        Box(Modifier.size(46.dp).clip(CircleShape).background(Panel2).border(1.dp, tint.copy(alpha = .6f), CircleShape)
            .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp))
        }
    }

    @Composable
    private fun Chip(text: String, icon: ImageVector?, color: Color, onClick: () -> Unit) {
        val shape = RoundedCornerShape(20.dp)
        Row(Modifier.clip(shape).background(Panel2).border(1.dp, color.copy(alpha = .5f), shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
            Text(text, color = Color.White, fontSize = 12.sp)
        }
    }

    @Composable
    private fun CommandBar(onSend: (String) -> Unit) {
        var text by remember { mutableStateOf("") }
        val shape = RoundedCornerShape(28.dp)
        Row(Modifier.fillMaxWidth().clip(shape).background(Panel2)
            .border(1.dp, Brush.horizontalGradient(listOf(accent.copy(alpha = .8f), accent2.copy(alpha = .8f))), shape)
            .padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { listen() }) { Icon(Icons.Rounded.Mic, "Voce", tint = if (listening) accent else accent2) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (text.isEmpty()) Text(if (listening) "Ascolto…" else "Scrivi un comando o premi il microfono…",
                    color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp), cursorBrush = SolidColor(accent2),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (text.isNotBlank()) { onSend(text); text = "" } }))
            }
            IconButton(onClick = { if (text.isNotBlank()) { onSend(text); text = "" } }, enabled = !isBusy) {
                Icon(Icons.Rounded.Send, "Invia", tint = accent2)
            }
        }
    }

    @Composable
    private fun LastLog() {
        val last = messages.lastOrNull { it.who == "SYSTEM" || it.who == "ERROR" } ?: return
        Text(last.message, color = if (last.who == "ERROR") NeonRed else Muted, fontSize = 11.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    }

    @Composable
    private fun fieldColors() = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accent2, unfocusedBorderColor = Stroke1, focusedTextColor = Color.White,
        unfocusedTextColor = Color.White, cursorColor = accent2, focusedLabelColor = accent2, unfocusedLabelColor = Muted)

    @Composable
    private fun HexLogo(dim: Dp) {
        val a = accent; val b = accent2
        Canvas(Modifier.size(dim)) {
            val c = center
            val r = size.minDimension / 2f * .92f
            val hex = Path()
            for (i in 0..5) {
                val ang = Math.toRadians(60.0 * i - 90)
                val x = c.x + cos(ang).toFloat() * r; val y = c.y + sin(ang).toFloat() * r
                if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
            }
            hex.close()
            drawPath(hex, Brush.radialGradient(listOf(b.copy(alpha = .35f), Color.Transparent), center = c, radius = r))
            drawPath(hex, Brush.linearGradient(listOf(b, a)), style = Stroke(size.minDimension * .08f))
            drawCircle(b.copy(alpha = .9f), r * .32f, c, style = Stroke(size.minDimension * .06f))
            drawCircle(Color.White, r * .12f, c)
        }
    }

    @Composable
    private fun Waveform(modifier: Modifier) {
        val inf = rememberInfiniteTransition(label = "wave")
        val ph by inf.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "ph")
        val a = accent; val b = accent2
        Canvas(modifier) {
            val n = 48
            val w = size.width / n
            for (i in 0 until n) {
                val env = sin(Math.PI * i / (n - 1)).toFloat()
                val h = (0.15f + 0.85f * abs(sin(ph + i * 0.45f))) * env * size.height / 2f
                val x = i * w + w / 2
                drawLine(if (i < n / 2) a else b, Offset(x, center.y - h), Offset(x, center.y + h), strokeWidth = w * .5f, cap = StrokeCap.Round)
            }
        }
    }

    @Composable
    private fun HudCore(modifier: Modifier, busy: Boolean, listen: Boolean, ripple: Float = 0f, level: Float = 0f) {
        val red = accent; val blue = accent2
        val inf = rememberInfiniteTransition(label = "hud")
        val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 4000 else 16000, easing = LinearEasing)), label = "spin")
        val spin2 by inf.animateFloat(360f, 0f, infiniteRepeatable(tween(26000, easing = LinearEasing)), label = "spin2")
        val pulse by inf.animateFloat(.88f, 1.06f, infiniteRepeatable(tween(if (listen) 450 else 1600), RepeatMode.Reverse), label = "pulse")
        Canvas(modifier) {
            val c = center
            val r = size.minDimension / 2f * .96f
            fun box(k: Float) = Pair(Offset(c.x - r * k, c.y - r * k), Size(2 * r * k, 2 * r * k))

            drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = .30f), red.copy(alpha = .10f), Color.Transparent), center = c, radius = r * 1.2f), r * 1.2f, c)
            drawCircle(red.copy(alpha = .45f), r, c, style = Stroke(1.5f))

            rotate(spin, c) {
                val (tl, sz) = box(.95f)
                for (i in 0 until 4) drawArc(red, i * 90f + 10f, 55f, false, tl, sz, style = Stroke(r * .04f, cap = StrokeCap.Round))
            }
            rotate(spin2, c) {
                for (i in 0 until 72) {
                    val ang = Math.toRadians(i * 5.0)
                    val long = i % 6 == 0
                    val r1 = r * (if (long) .73f else .78f); val r2 = r * .84f
                    drawLine((if (i % 2 == 0) blue else red).copy(alpha = if (long) .95f else .4f),
                        Offset(c.x + cos(ang).toFloat() * r1, c.y + sin(ang).toFloat() * r1),
                        Offset(c.x + cos(ang).toFloat() * r2, c.y + sin(ang).toFloat() * r2),
                        strokeWidth = if (long) 3f else 1.5f)
                }
            }
            rotate(-spin * 1.5f, c) {
                val (tl, sz) = box(.64f)
                drawArc(blue, 20f, 115f, false, tl, sz, style = Stroke(r * .055f, cap = StrokeCap.Round))
                drawArc(blue.copy(alpha = .55f), 200f, 80f, false, tl, sz, style = Stroke(r * .055f, cap = StrokeCap.Round))
            }
            drawCircle(red.copy(alpha = .35f), r * .56f, c, style = Stroke(2f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFF0E2546), Color(0xFF050912)), center = c, radius = r * .48f), r * .48f, c)
            drawCircle(blue.copy(alpha = .85f), r * .48f, c, style = Stroke(3f))

            val s = r * .28f * (pulse + level * .3f)
            val tri = Path().apply { moveTo(c.x - s, c.y - s * .55f); lineTo(c.x + s, c.y - s * .55f); lineTo(c.x, c.y + s); close() }
            drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = .55f), Color.Transparent), center = c, radius = s * 1.8f), s * 1.8f, c)
            drawPath(tri, Brush.verticalGradient(listOf(Color(0xFFA6ECFF), blue, Color(0xFF0A46C8)), startY = c.y - s, endY = c.y + s))
            val inner = Path().apply { moveTo(c.x - s * .48f, c.y - s * .28f); lineTo(c.x + s * .48f, c.y - s * .28f); lineTo(c.x, c.y + s * .45f); close() }
            drawPath(inner, Color(0xFF061024))
            drawPath(tri, Color.White.copy(alpha = .75f), style = Stroke(2f))
            if (ripple > 0f && ripple < 1f) {
                drawCircle(blue.copy(alpha = (1f - ripple) * .9f), r * (.48f + ripple * .62f), c, style = Stroke(2f + 6f * (1f - ripple)))
                drawCircle(red.copy(alpha = (1f - ripple) * .5f), r * (.48f + ripple * .45f), c, style = Stroke(2f))
            }
        }
    }
}
