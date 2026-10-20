package com.jarvis.nexus

import android.Manifest
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.content.ConnectivityManager
import android.net.NetworkCapabilities
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.AlarmClock
import android.provider.MediaStore
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.app.ActivityManager
import android.hardware.camera2.CameraManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val Bg = Color(0xFF070A11)
private val Panel = Color(0xFF111925)
private val Red = Color(0xFFFF4C63)
private val Blue = Color(0xFF39A7FF)
private val Muted = Color(0xFF98A6B9)
private data class Line(val who: String, val message: String)

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var brain: LocalBrain
    private var tts: TextToSpeech? = null
    private val messages = mutableStateListOf(Line("JARVIS", "NEXUS inizializzato. Importa un modello per conversare offline."))
    private var modelLoaded by mutableStateOf(false)
    private var onlineEnabled by mutableStateOf(true)
    private var apiKey by mutableStateOf("")
    private var engineStatus by mutableStateOf("PRONTO")
    private var isBusy by mutableStateOf(false)
    private var currentModule by mutableStateOf("CORE")
    private var tactical by mutableStateOf(false)
    private var blueTheme by mutableStateOf(true)
    private var torchEnabled by mutableStateOf(false)
    private val prefs by lazy { getSharedPreferences("nexus_local", MODE_PRIVATE) }
    private val scope by lazy { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + Dispatchers.Main) }

    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) try {
            contentResolver.openOutputStream(uri)?.use { it.write((prefs.getString("notes", "") ?: "").toByteArray()) }
            messages.add(Line("SYSTEM", "Note esportate."))
        } catch (e: Exception) { messages.add(Line("ERROR", "Esportazione: ${e.message}")) }
    }
    private val importText = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(100000) } ?: ""
            prefs.edit().putString("notes", txt).apply()
            messages.add(Line("SYSTEM", "Note importate da file."))
        } catch (e: Exception) { messages.add(Line("ERROR", "Importazione: ${e.message}")) }
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
        apiKey = prefs.getString("gemini_api_key", "") ?: ""
        onlineEnabled = prefs.getBoolean("gemini_enabled", true)
                messages.add(Line("SYSTEM", "Modello importato. L'inferenza dipende dalla compatibilità del file."))
            } catch (e: Exception) {
                messages.add(Line("ERROR", "Importazione fallita: ${e.message}"))
            } finally { isBusy = false }
        }
    }
    private val voicePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) send(spoken)
        }
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) quickAction("Torcia") else messages.add(Line("SYSTEM", "Permesso fotocamera negato."))
    }
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startListening() else messages.add(Line("SYSTEM", "Permesso microfono negato."))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        brain = LocalBrain(this)
        modelLoaded = brain.available()
        tts = TextToSpeech(this, this)
        setContent { NexusScreen() }
    }
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.language = Locale.ITALIAN
    }
    private fun speak(text: String) {
        tts?.speak(text.take(900), TextToSpeech.QUEUE_FLUSH, null, "jarvis")
    }
    private fun startListening() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            voicePicker.launch(intent)
        } catch (e: Exception) { messages.add(Line("ERROR", "Riconoscimento vocale non disponibile.")) }
    }
    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startListening()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
    private fun launchSafe(intent: Intent, success: String) {
        try { startActivity(intent); messages.add(Line("SYSTEM", success)) }
        catch (e: Exception) { messages.add(Line("ERROR", "Azione non disponibile: ${e.message}")) }
    }
    private fun quickAction(action: String) {
        when(action) {
            "Note" -> send("mostra note")
            "Attività" -> send("mostra attività")
            "Esporta note" -> exportFile.launch("jarvis-note.txt")
            "Importa note" -> importText.launch(arrayOf("text/plain", "*/*"))
            "Calendario" -> launchSafe(Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI), "Editor evento aperto.")
            "Promemoria" -> launchSafe(Intent(AlarmClock.ACTION_SET_ALARM), "Editor sveglia aperto.")
            "Email" -> launchSafe(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")), "Client email aperto.")
            "Browser" -> launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Browser aperto.")
            "Mappe" -> launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), "App mappe aperta.")
            "Fotocamera" -> launchSafe(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Fotocamera aperta.")
            "File" -> launchSafe(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type = "*/*"; addCategory(Intent.CATEGORY_OPENABLE) }, "Selettore file aperto.")
            "Wi-Fi" -> launchSafe(Intent(Settings.ACTION_WIFI_SETTINGS), "Impostazioni Wi-Fi aperte.")
            "Bluetooth" -> launchSafe(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Impostazioni Bluetooth aperte.")
            "App" -> launchSafe(Intent(Settings.ACTION_APPLICATION_SETTINGS), "Impostazioni app aperte.")
            "Impostazioni" -> launchSafe(Intent(Settings.ACTION_SETTINGS), "Impostazioni aperte.")
            "Condividi" -> launchSafe(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT, prefs.getString("notes", "") ?: "") }, "Condividi note"), "Condivisione aperta.")
            "Copia note" -> {
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Note JARVIS", prefs.getString("notes", "") ?: ""))
                messages.add(Line("SYSTEM", "Note copiate negli appunti."))
            }
            "Torcia" -> try {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    cameraPermission.launch(Manifest.permission.CAMERA)
                    return
                }
                val camera = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = camera.cameraIdList.firstOrNull { camera.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                if (id == null) messages.add(Line("SYSTEM", "Flash non disponibile.")) else {
                    torchEnabled = !torchEnabled; camera.setTorchMode(id, torchEnabled)
                    messages.add(Line("SYSTEM", if (torchEnabled) "Torcia accesa." else "Torcia spenta."))
                }
            } catch (e: Exception) { messages.add(Line("ERROR", "Torcia: ${e.message}")) }
            "Stato" -> {
                val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
                val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
                val mi = ActivityManager.MemoryInfo(); am.getMemoryInfo(mi)
                val storage = StatFs(Environment.getDataDirectory().path)
                val gb = 1024.0*1024*1024
                messages.add(Line("SYSTEM", "Batteria ${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}% • RAM libera %.1f GB • Archiviazione libera %.1f GB".format(Locale.ITALIAN,mi.availMem/gb,storage.availableBytes/gb)))
            }
            "Tema" -> { blueTheme = !blueTheme; messages.add(Line("SYSTEM", "Tema HUD aggiornato.")) }
            "Voce" -> listen()
            "Cancella chat" -> { messages.clear(); messages.add(Line("JARVIS", "Conversazione cancellata.")) }
        }
    }
    private fun send(raw: String) {
        val q = raw.trim()
        if (q.isBlank() || isBusy) return
        messages.add(Line("TU", q))
        val normalized = q.lowercase(Locale.ITALIAN)
        val answer: String? = when {
            normalized == "ora" || normalized.contains("che ore") -> SimpleDateFormat("HH:mm", Locale.ITALIAN).format(Date())
            normalized.contains("che giorno") || normalized == "data" -> SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN).format(Date())
            normalized.startsWith("ricorda ") -> {
                prefs.edit().putString("notes", (prefs.getString("notes", "") ?: "") + "\n• " + q.substringAfter(" ")).apply()
                "Nota salvata in locale."
            }
            normalized.startsWith("attività ") -> {
                prefs.edit().putString("tasks", (prefs.getString("tasks", "") ?: "") + "\n□ " + q.substringAfter(" ")).apply()
                "Attività aggiunta."
            }
            normalized == "mostra attività" -> prefs.getString("tasks", "")?.ifBlank { "Nessuna attività." } ?: "Nessuna attività."
            normalized == "cancella attività" -> { prefs.edit().remove("tasks").apply(); "Attività eliminate." }
            normalized.startsWith("cerca web ") -> { launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q.substringAfter("cerca web ")))), "Ricerca nel browser aperta."); "Ricerca avviata nel browser." }
            normalized.startsWith("naviga ") -> { launchSafe(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(q.substringAfter("naviga ")))), "Navigazione aperta."); "Mappe aperte." }
            normalized == "mostra note" || normalized == "leggi note" -> prefs.getString("notes", "")?.ifBlank { "Nessuna nota." } ?: "Nessuna nota."
            normalized == "cancella note" -> { prefs.edit().remove("notes").apply(); "Note eliminate." }
            normalized.contains("apri impostazioni") -> {
                startActivity(Intent(Settings.ACTION_SETTINGS)); "Impostazioni aperte."
            }
            normalized.contains("batteria") -> {
                val battery = getSystemService(BATTERY_SERVICE) as BatteryManager
                "Batteria: ${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)}%."
            }
            else -> null
        }
        if (answer != null) {
            messages.add(Line("JARVIS", answer))
            speak(answer)
        } else {
            isBusy = true
            val context = messages.takeLast(8).joinToString("\n") { "${it.who}: ${it.message}" }
            engineStatus = "ELABORAZIONE"
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    if (onlineEnabled && apiKey.isNotBlank() && hasInternet()) {
                        try { Pair("GEMINI", GeminiClient.ask(apiKey, q, context)) }
                        catch (e: Exception) {
                            Pair("OFFLINE", try { brain.respond(q, context) } catch (local: Exception) { "Gemini non disponibile (${e.message}). Modello locale non avviabile: ${local.message}" })
                        }
                    } else {
                        Pair("OFFLINE", try { brain.respond(q, context) } catch (e: Exception) { "Motore locale non disponibile: ${e.message}" })
                    }
                }
                engineStatus = result.first
                val response = result.second
                messages.add(Line("JARVIS ${result.first}", response))
                speak(response)
                isBusy = false
            }
        }
    }
    private fun hasInternet(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    override fun onDestroy() {
        brain.unload()
        tts?.stop()
        tts?.shutdown()
        scope.cancel()
        super.onDestroy()
    }

    @Composable
    private fun NexusScreen() {
        var input by remember { mutableStateOf("") }
        MaterialTheme(colorScheme = darkColorScheme(primary = Red, background = Bg, surface = Panel, onSurface = Color.White)) {
            Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("J.A.R.V.I.S.", color = Color.White, fontWeight = FontWeight.Black, fontSize = 25.sp, letterSpacing = 3.sp)
                        Text("NEXUS  /  ADAPTIVE INTELLIGENCE", color = Blue, fontSize = 10.sp, letterSpacing = 1.sp)
                    }
                    Text(if (onlineEnabled && apiKey.isNotBlank()) "● GEMINI + OFFLINE" else if (modelLoaded) "● SOLO OFFLINE" else "○ CONFIGURA AI", color = if (modelLoaded || apiKey.isNotBlank()) Color(0xFF7EE4B2) else Muted, fontSize = 10.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("NEURAL COMMAND CENTER", color = Muted, fontSize = 11.sp)
                    TextButton(onClick = { tactical = !tactical }) { Text(if (tactical) "TACTICAL" else "NEXUS", color = Red) }
                }
                HudCore(tactical, isBusy)
                Text(if (isBusy) "ELABORAZIONE..." else "MOTORE: $engineStatus", color = Red, fontSize = 12.sp, letterSpacing = 2.sp,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("AI", if (apiKey.isNotBlank()) "GEMINI" else if (modelLoaded) "OFFLINE" else "SETUP", Modifier.weight(1f))
                    Metric("FALLBACK", if (modelLoaded) "READY" else "SETUP", Modifier.weight(1f))
                    Metric("MODULES", "06", Modifier.weight(1f))
                }
                Text("MODULI OPERATIVI", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val modules = listOf("CORE" to "AI offline", "VOICE" to "STT e TTS italiano",
                    "MEMORY" to "Note e attività", "DEVICE" to "Batteria e controlli",
                    "AUTOMATE" to "Sveglie e calendario", "SYSTEM" to "Diagnostica locale",
                    "WORK" to "Email e file", "NETWORK" to "Wi-Fi e Bluetooth",
                    "MEDIA" to "Fotocamera e torcia", "NAV" to "Mappe e ricerca")
                modules.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (name, description) ->
                            Surface(
                                onClick = {
                                    currentModule = name
                                    messages.add(Line("SYSTEM", "Modulo $name selezionato: $description"))
                                },
                                modifier = Modifier.weight(1f), color = if (currentModule == name) Color(0xFF361923) else Panel,
                                shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, if (currentModule == name) Red else Color(0xFF2A3444))
                            ) {
                                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("◇ $name", color = if (currentModule == name) Red else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    Text(description, color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
                Text("AZIONI RAPIDE", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val actions = when(currentModule) {
                    "VOICE" -> listOf("Voce", "Tema")
                    "MEMORY" -> listOf("Note", "Attività", "Esporta note", "Importa note", "Copia note")
                    "DEVICE" -> listOf("Stato", "Wi-Fi", "Bluetooth", "App", "Torcia")
                    "AUTOMATE" -> listOf("Promemoria", "Calendario")
                    "SYSTEM" -> listOf("Stato", "Impostazioni", "Cancella chat")
                    "WORK" -> listOf("Email", "File", "Condividi", "Esporta note")
                    "NETWORK" -> listOf("Wi-Fi", "Bluetooth", "Browser", "Mappe")
                    "MEDIA" -> listOf("Fotocamera", "Torcia", "Condividi")
                    "NAV" -> listOf("Mappe", "Browser")
                    else -> listOf("Voce", "Stato", "Note", "Attività", "Tema")
                }
                actions.chunked(3).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        group.forEach { action ->
                            OutlinedButton(onClick = { quickAction(action) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 4.dp)) {
                                Text(action, fontSize = 10.sp, color = if (blueTheme) Blue else Red)
                            }
                        }
                        repeat(3-group.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Surface(color = Panel, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFF2C3543))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("⌁  COMMAND TERMINAL", color = Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Column(Modifier.heightIn(min = 90.dp, max = 230.dp).verticalScroll(rememberScrollState())) {
                            messages.takeLast(10).forEach {
                                Text("${it.who}  ${it.message}", color = if (it.who == "JARVIS") Color(0xFFFFA4B1) else Color.White,
                                    fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                        OutlinedTextField(
                            value = input, onValueChange = { input = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Inserisci un comando...", color = Muted) },
                            singleLine = true, enabled = !isBusy,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { send(input); input = "" })
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { send(input); input = "" }, enabled = !isBusy) { Text("INVIA") }
                            OutlinedButton(onClick = { listen() }) { Text("🎙 VOCE") }
                        }
                    }
                }
                Surface(color = Panel, shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Blue.copy(alpha = .65f))) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("GEMINI CLOUD + FALLBACK LOCALE", color = Blue, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("La chat usa Gemini quando disponibile. Senza connessione passa al modello locale importato.", color = Muted, fontSize = 11.sp)
                        OutlinedTextField(value = apiKey, onValueChange = {
                            apiKey = it.trim()
                            prefs.edit().putString("gemini_api_key", apiKey).apply()
                        }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            label = { Text("API key Gemini (non inserire nel repository)") })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Preferisci Gemini online", modifier = Modifier.weight(1f), color = Color.White, fontSize = 12.sp)
                            Switch(checked = onlineEnabled, onCheckedChange = {
                                onlineEnabled = it
                                prefs.edit().putBoolean("gemini_enabled", it).apply()
                            })
                        }
                        Text("Nota: la chiave è salvata nelle preferenze private dell'app, non cifrata a livello applicativo. I messaggi inviati a Gemini lasciano il dispositivo. Non usare dati sensibili.", color = Muted, fontSize = 10.sp)
                    }
                }
                OutlinedButton(onClick = { modelPicker.launch(arrayOf("application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth(), enabled = !isBusy) {
                    Text(if (modelLoaded) "SOSTITUISCI MODELLO LOCALE" else "IMPORTA MODELLO LOCALE .BIN")
                }
                Text("Versione 3.0 • UI HUD animata 2D • Modello AI separato • Nessuna API a pagamento",
                    color = Muted, fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
    @Composable private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
        Surface(modifier = modifier, color = Panel, shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFF29313E))) {
            Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(label, color = Muted, fontSize = 9.sp)
            }
        }
    }
    @Composable private fun HudCore(tactical: Boolean, busy: Boolean) {
        val infinite = rememberInfiniteTransition(label = "hud")
        val spin by infinite.animateFloat(0f, 360f, infiniteRepeatable(tween(if (busy) 3800 else 13000, easing = LinearEasing)), label = "spin")
        val pulse by infinite.animateFloat(.84f, 1.04f, infiniteRepeatable(tween(1450), RepeatMode.Reverse), label = "pulse")
        Canvas(Modifier.fillMaxWidth().height(290.dp)) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = minOf(size.width, size.height) * .43f
            drawCircle(brush = Brush.radialGradient(listOf(Red.copy(alpha = .24f), Color.Transparent), center = c, radius = r * 1.4f), radius = r * 1.4f, center = c)
            listOf(1f,.88f,.76f,.63f,.50f).forEachIndexed { i, scale ->
                drawCircle(color = if (i%2==0) Red.copy(alpha = .38f) else Color(0xFF703448),
                    radius = r*scale, center = c, style = Stroke(width = if (i==2) 5f else 1.8f))
            }
            rotate(spin, pivot = c) {
                drawArc(Red, 10f, 83f, false, topLeft = Offset(c.x-r,c.y-r), size = Size(2*r,2*r), style = Stroke(5f))
                drawArc(Blue.copy(alpha=.75f), 145f, 66f, false, topLeft = Offset(c.x-r,c.y-r), size = Size(2*r,2*r), style = Stroke(5f))
                drawArc(Blue.copy(alpha=.8f), 260f, 54f, false, topLeft = Offset(c.x-r,c.y-r), size = Size(2*r,2*r), style = Stroke(5f))
            }
            rotate(-spin*.65f, pivot = c) {
                repeat(36) { i ->
                    val a = Math.toRadians((i*10).toDouble())
                    val start = Offset(c.x + cos(a).toFloat()*r*.83f, c.y+sin(a).toFloat()*r*.83f)
                    val end = Offset(c.x + cos(a).toFloat()*r*.91f, c.y+sin(a).toFloat()*r*.91f)
                    drawLine((if (i%2==0) Blue else Red).copy(alpha=if (i%3==0) .95f else .4f), start, end, strokeWidth=if(i%3==0) 4f else 2f)
                }
            }
            drawCircle(Color(0xFF160E19), radius = r*.46f, center = c)
            drawCircle(Red.copy(alpha=.55f), radius = r*.46f, center = c, style=Stroke(2f))
            repeat(21) { i ->
                val x = c.x + (i-10)*r*.035f
                val h = (9+(i*7%13))*pulse*r*.018f
                drawLine(Red.copy(alpha=.55f+(i%4)*.12f), Offset(x,c.y-h), Offset(x,c.y+h), strokeWidth=3.5f)
            }
            drawCircle(Red, radius = 8f*pulse, center = c)
            drawCircle(Color.White, radius=3f, center=c)
            if (tactical) {
                drawLine(Red.copy(alpha=.6f), Offset(c.x-r*1.25f,c.y), Offset(c.x-r*.95f,c.y))
                drawLine(Red.copy(alpha=.6f), Offset(c.x+r*.95f,c.y), Offset(c.x+r*1.25f,c.y))
            }
        }
    }
}
