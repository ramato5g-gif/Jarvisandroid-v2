package com.jarvis.nexus

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
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
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
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
private enum class Screen { HOME, CHAT, MODULES, MODULE, DEVICE, AUTOMATE, MEMORY, SETTINGS }
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
    Module("AI CORE", "Modello locale", Icons.Rounded.Psychology, listOf("Importa modello", "Gemini", "Cancella chat")),
    Module("VOICE", "STT / TTS", Icons.Rounded.GraphicEq, listOf("Voce", "Test voce")),
    Module("MEMORY", "Note e attività", Icons.Rounded.Storage),
    Module("DEVICE", "Controllo Android", Icons.Rounded.PhoneAndroid),
    Module("AUTOMATE", "Routine e scenari", Icons.Rounded.Autorenew),
    Module("SYSTEM", "Diagnostica", Icons.Rounded.Speed, listOf("Stato", "Batteria", "Archiviazione", "App", "Impostazioni", "Cancella chat")),
    Module("WORK", "Produttività", Icons.Rounded.Work, listOf("Email", "Calendario", "File", "Condividi", "Esporta note", "Copia note")),
    Module("HEALTH", "Promemoria", Icons.Rounded.Favorite, listOf("Farmaco", "Acqua", "Timer", "Promemoria")),
    Module("NETWORK", "Rete e connessioni", Icons.Rounded.Wifi, listOf("Wi-Fi", "Bluetooth", "Dati mobili", "NFC", "Browser", "Mappe")),
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

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
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
                withContext(Dispatchers.IO) {
                    brain.unload()
                    contentResolver.openInputStream(uri)!!.use { input ->
                        File(filesDir, "model.bin").outputStream().use { output -> input.copyTo(output) }
                    }
                }
                modelLoaded = brain.available()
                sys("Modello importato. L'inferenza dipende dalla compatibilità del file.")
            } catch (e: Exception) { err("Importazione modello fallita: ${e.message}") }
            finally { isBusy = false }
        }
    }
    private val voicePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        listening = false
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) { screen = Screen.CHAT; send(spoken) }
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) quickAction("Torcia") else sys("Permesso fotocamera negato.")
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else sys("Permesso microfono negato.")
    }

    // ───────────── Lifecycle ─────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        brain = LocalBrain(this)
        modelLoaded = brain.available()
        apiKey = prefs.getString("gemini_api_key", "") ?: ""
        onlineEnabled = prefs.getBoolean("gemini_enabled", true)
        userName = prefs.getString("user_name", "Riccardo") ?: "Riccardo"
        altTheme = prefs.getBoolean("alt_theme", false)
        notes.addAll(parseList(prefs.getString("notes", "") ?: "", "• "))
        tasks.addAll(parseList(prefs.getString("tasks", "") ?: "", "□ "))
        routines.forEach { routineOn[it.id] = prefs.getBoolean("routine_${it.id}", it.id == "morning") }
        messages.add(Line("JARVIS", "Ciao $userName. Sono JARVIS, il tuo assistente. Come posso aiutarti?"))
        tts = TextToSpeech(this, this)
        setContent { NexusApp() }
    }
    override fun onInit(status: Int) { if (status == TextToSpeech.SUCCESS) tts?.language = Locale.ITALIAN }
    override fun onDestroy() {
        brain.unload(); tts?.stop(); tts?.shutdown(); scope.cancel()
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
    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            listening = true
            voicePicker.launch(intent)
        } catch (e: Exception) { listening = false; err("Riconoscimento vocale non disponibile.") }
    }
    private fun listen() {
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
            "Gemini" -> screen = Screen.SETTINGS
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
            "Torcia" -> try {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    cameraPermission.launch(Manifest.permission.CAMERA); return
                }
                val camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                if (id == null) sys("Flash non disponibile.") else {
                    torchEnabled = !torchEnabled; camera.setTorchMode(id, torchEnabled)
                    sys(if (torchEnabled) "Torcia accesa." else "Torcia spenta.")
                }
            } catch (e: Exception) { err("Torcia: ${e.message}") }
            "Stato" -> {
                val s = readStats()
                sys("Batteria ${s.battery}% • RAM usata ${s.ramPct}% • Libero %.1f GB • Temp %.1f °C".format(Locale.ITALIAN, s.storageFree, s.temp))
                screen = Screen.CHAT
            }
        }
    }

    private fun send(raw: String) {
        val q = raw.trim()
        if (q.isBlank() || isBusy) return
        messages.add(Line("TU", q))
        val n = q.lowercase(Locale.ITALIAN)
        val answer: String? = when {
            n.contains("briefing") -> {
                val s = readStats()
                val ora = SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date())
                val data = SimpleDateFormat("EEEE d MMMM", Locale.ITALIAN).format(Date())
                "${greeting()}, $userName. Sono le $ora di $data. Batteria al ${s.battery}%" +
                    (if (s.charging) " in carica" else "") + ". Hai ${tasks.size} attività e ${notes.size} note."
            }
            n == "ora" || n.contains("che ore") -> "Sono le " + SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date()) + "."
            n.contains("che giorno") || n == "data" -> "Oggi è " + SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date()) + "."
            n.startsWith("ricorda ") -> { notes.add(q.substringAfter(" ")); saveLists(); "Nota salvata in locale." }
            n.startsWith("attività ") -> { tasks.add(q.substringAfter(" ")); saveLists(); "Attività aggiunta." }
            n == "mostra attività" -> if (tasks.isEmpty()) "Nessuna attività." else tasks.joinToString("\n") { "□ $it" }
            n == "cancella attività" -> { tasks.clear(); saveLists(); "Attività eliminate." }
            n == "mostra note" || n == "leggi note" -> if (notes.isEmpty()) "Nessuna nota." else notes.joinToString("\n") { "• $it" }
            n == "cancella note" -> { notes.clear(); saveLists(); "Note eliminate." }
            n.startsWith("cerca web ") -> { launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.substringAfter("cerca web ")))), "Ricerca aperta."); "Ricerca avviata nel browser." }
            n.startsWith("naviga ") -> { launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q.substringAfter("naviga ")))), "Navigazione aperta."); "Mappe aperte." }
            n.contains("apri impostazioni") -> { launchSafe(Intent(Settings.ACTION_SETTINGS), "Impostazioni aperte."); "Fatto." }
            n.contains("torcia") -> { quickAction("Torcia"); "Torcia commutata." }
            n.contains("batteria") -> { val s = readStats(); "Batteria al ${s.battery}%" + (if (s.charging) ", in carica." else ".") }
            else -> null
        }
        if (answer != null) { messages.add(Line("JARVIS", answer)); speak(answer); return }
        isBusy = true
        engineStatus = "ELABORAZIONE"
        val context = messages.takeLast(8).joinToString("\n") { "${it.who}: ${it.message}" }
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (onlineEnabled && apiKey.isNotBlank() && hasInternet()) {
                    try { Pair("GEMINI", GeminiClient.ask(apiKey, q, context)) }
                    catch (e: Exception) {
                        Pair("LOCALE", try { brain.respond(q, context) } catch (l: Exception) { "Gemini non disponibile (${e.message}). Modello locale non avviabile: ${l.message}" })
                    }
                } else Pair("LOCALE", try { brain.respond(q, context) } catch (e: Exception) { "Motore locale non disponibile: ${e.message}" })
            }
            engineStatus = result.first
            messages.add(Line("JARVIS", result.second))
            speak(result.second)
            isBusy = false
        }
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
            screen = when (screen) { Screen.MODULE, Screen.MEMORY, Screen.AUTOMATE -> Screen.MODULES; else -> Screen.HOME }
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
            Box(Modifier.fillMaxWidth().height(300.dp)) {
                HudCore(Modifier.fillMaxSize().padding(horizontal = 30.dp), isBusy, listening)
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
                Text(if (isBusy) "ELABORAZIONE…" else if (listening) "ASCOLTO…" else "MOTORE: $engineStatus",
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
                HexLogo(34.dp)
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
                    "Mostra attività" to Icons.Rounded.TaskAlt)
                items(sugg) { (t, ic) -> Chip(t, ic, accent2) { send(t) } }
            }
            if (listening) Waveform(Modifier.fillMaxWidth().height(40.dp).padding(bottom = 6.dp))
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
                listOf("ricorda <testo>" to "salva una nota", "attività <testo>" to "aggiunge un'attività",
                    "cerca web <testo>" to "ricerca Google", "naviga <luogo>" to "apre le mappe",
                    "briefing" to "riepilogo vocale", "torcia" to "accende/spegne il flash").forEach { (c, d) ->
                    Row(Modifier.padding(vertical = 3.dp)) {
                        Text(c, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(150.dp))
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
                Text("La chat usa Gemini quando c'è rete; senza connessione passa al modello locale.", color = Muted, fontSize = 11.sp)
                OutlinedTextField(apiKey, {
                    apiKey = it.trim(); prefs.edit().putString("gemini_api_key", apiKey).apply()
                }, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    label = { Text("API key Gemini") }, colors = fieldColors())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Preferisci Gemini online", Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                    Switch(onlineEnabled, { onlineEnabled = it; prefs.edit().putBoolean("gemini_enabled", it).apply() },
                        colors = SwitchDefaults.colors(checkedTrackColor = accent2))
                }
                OutlinedButton(onClick = { quickAction("Importa modello") }, Modifier.fillMaxWidth(), enabled = !isBusy) {
                    Text(if (modelLoaded) "SOSTITUISCI MODELLO LOCALE" else "IMPORTA MODELLO LOCALE .BIN", color = accent2)
                }
                Text("La chiave resta sul telefono (non cifrata). I messaggi inviati a Gemini lasciano il dispositivo: niente dati sensibili.",
                    color = Muted, fontSize = 10.sp)
            }
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
    private fun HudCore(modifier: Modifier, busy: Boolean, listen: Boolean) {
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

            val s = r * .28f * pulse
            val tri = Path().apply { moveTo(c.x - s, c.y - s * .55f); lineTo(c.x + s, c.y - s * .55f); lineTo(c.x, c.y + s); close() }
            drawCircle(Brush.radialGradient(listOf(blue.copy(alpha = .55f), Color.Transparent), center = c, radius = s * 1.8f), s * 1.8f, c)
            drawPath(tri, Brush.verticalGradient(listOf(Color(0xFFA6ECFF), blue, Color(0xFF0A46C8)), startY = c.y - s, endY = c.y + s))
            val inner = Path().apply { moveTo(c.x - s * .48f, c.y - s * .28f); lineTo(c.x + s * .48f, c.y - s * .28f); lineTo(c.x, c.y + s * .45f); close() }
            drawPath(inner, Color(0xFF061024))
            drawPath(tri, Color.White.copy(alpha = .75f), style = Stroke(2f))
        }
    }
}
