# J.A.R.V.I.S. NEXUS — Android v3.1 (report mensili dal PC)

Versione 3.1: tutto quello che c'era nella v3.0 **più** il modulo **REPORT**, che tiene sul telefono
una copia aggiornata dei report mensili Sapio (registro + Excel Allegato D) di JARVIS desktop.

## JARVIS OS — modalità launcher per Galaxy Tab S6 Lite
La stessa app diventa la **schermata Home** del tablet. Sul telefono non cambia nulla: il launcher è un componente
separato (`LauncherActivity`), **spento di default**, che si accende solo da *Sistema → JARVIS OS*.

**Cosa fa**
- **Home tablet** (orizzontale): a sinistra orologio, nucleo HUD (tocca = parla, tieni premuto = chat), meteo a 3 giorni,
  stato sistema, agenda delle prossime 48 ore, nodi di rete, app fissate; a destra la chat con Claude Haiku sempre aperta.
  In verticale le due metà sono una sopra l'altra.
- **App**: griglia con ricerca (Invio apre la prima); tieni premuto per fissare/sganciare un'app in Home.
- **File**: esplora la memoria interna, cerca per nome, apre con l'app giusta. Il tasto Indietro risale le cartelle.
- **Claude agisce sul tablet** con nuove azioni oltre a quelle già esistenti: `findfile` (cerca file), `openfile` (apre un file),
  `brightness` (luminosità), e `event` ora **scrive direttamente** nel calendario (prima apriva solo l'editor).
  Nel prompt di Claude entrano anche meteo e prossimi eventi, quindi "cosa ho domani?" o "serve l'ombrello?" funzionano senza azioni.
- **Nodi di rete**: elenco `Nome|http://ip:porta` (PC JARVIS, Pi 5, Pi-hole…) con stato online/offline e latenza, aggiornato ogni minuto.
- Tasto Home dentro JARVIS OS → torna alla Home; Indietro sulla Home non chiude nulla.

**Attivazione sul tablet**
1. Installa l'APK della Release (stessa firma del telefono).
2. *Sistema → JARVIS OS* → **Modalità launcher** → scegli **JARVIS OS** come Home predefinita ("Sempre").
3. Nella stessa scheda: **ACCESSO FILE** e **LUMINOSITÀ** (permessi speciali), poi città/coordinate per il meteo e i nodi.
4. Consenti il calendario quando richiesto (scheda Agenda).
5. Opzionale: *Sistema → Accesso rapido* → JARVIS come assistente predefinito (pressione lunga Home = ascolto).

Meteo: Open-Meteo, senza chiave. Il calendario legge/scrive i calendari presenti sul tablet: senza account Google (LineageOS senza GApps)
serve un calendario locale o DAVx5 + Etar, altrimenti `event` ripiega sull'editor.

## Installare LineageOS sul Tab S6 Lite (opzionale)
> Verifica sempre i passaggi sulla pagina ufficiale prima di iniziare: https://wiki.lineageos.org/devices/gta4xlwifi/install/

- **Solo SM-P610** (Wi-Fi 2020, Exynos 9611, codename `gta4xlwifi`). Il modello LTE `SM-P615` ha la sua guida (`gta4xl`);
  la versione **2024** è un altro dispositivo (`gta4xlswifi`, Exynos 1280). Controlla in Impostazioni → Info tablet.
- Versione mantenuta: **LineageOS 23.2 (Android 16)**. Prima serve il **firmware Samsung Android 13 più recente**.
- Sbloccare il bootloader **cancella tutto** e fa scattare **Knox** in modo permanente (Samsung Pass, Cartella sicura, alcune app bancarie).

**Passaggi (in sintesi)**
1. Aggiorna il tablet all'ultimo firmware Android 13. Backup di tutto.
2. Opzioni sviluppatore → **Sblocco OEM** attivo, **Debug USB** attivo.
3. Spegni, tieni **Vol+ e Vol−** e collega il cavo USB → modalità Download → pressione lunga **Vol+** per sbloccare il bootloader (reset).
4. Riavvia, completa la configurazione, collegati a Internet e ricontrolla che *Sblocco OEM* sia attivo (grigio).
5. Dalla pagina download scarica **recovery** e **vbmeta** di LineageOS e flashali dalla modalità Download
   (Heimdall o Odin, con i comandi esatti indicati nel wiki), poi avvia direttamente la recovery senza far partire Android.
6. In Lineage Recovery: **Factory reset → Format data**, poi **Apply update → Apply from ADB** e dal PC
   `adb sideload lineage-23.2-…-gta4xlwifi-signed.zip`.
7. **Prima del primo avvio** sideload di **MindTheGapps** per Android 16 arm64 (Advanced → Reboot to recovery, poi di nuovo Apply from ADB).
   Senza GApps JARVIS funziona, ma mancano il riconoscimento vocale Google, la voce TTS Google e lo scanner QR dei report.
8. Riavvia, accedi a Google, installa JARVIS e segui "Attivazione sul tablet".

---

## Report mensili: come funziona
- **Sola lettura.** Il telefono fa solo richieste `GET` al PC e salva copie nella cartella privata dell'app.
  Non invia dati, non modifica e non cancella nulla sul PC. Anche lato PC il servizio accetta solo `GET`
  e non scrive mai registro o Excel.
- **Solo Wi-Fi di casa.** Niente cloud: i dati dei pazienti viaggiano solo nella rete locale, cifrati (HTTPS).
  Il PC accetta solo indirizzi di rete locale; il telefono accetta solo il certificato del PC con l'impronta letta dal QR.
- **Automatico.** Si aggiorna all'apertura dell'app e ogni ora quando il telefono è su Wi-Fi (WorkManager).
  Scarica solo i mesi cambiati. Una copia viene sostituita solo dopo un download completo e valido;
  i mesi che spariscono dal PC restano sul telefono.
- **In chat:** "report di ottobre", "report del mese" → riepilogo dalla copia locale.

### Abbinamento (una volta sola)
1. Aggiorna JARVIS desktop (serve `electron/services/report-share.js`, versione del 2026-10-29) e riavvialo.
2. Sul PC scrivi o di' a JARVIS: **"abbina telefono report"** → si apre un QR.
3. Sul telefono: MODULI → **REPORT** → **ABBINA** e inquadra il QR (scanner Google, nessun permesso fotocamera).
   In alternativa incolla il link `jarvisreport://…`.
4. Per revocare l'accesso: sul PC **"nuovo codice report"** (il vecchio telefono non accede più), sul telefono **SCOLLEGA**.

Sul PC: `REPORT_SHARE=off` nel `.env` disattiva il servizio; `REPORT_PORT` (predefinita 8767) cambia la porta.
La prima volta Windows può chiedere di consentire JARVIS sulle reti private: va consentito, solo "Private".

### Limiti
- Funziona solo quando PC e telefono sono sulla stessa rete e JARVIS desktop è acceso; altrimenti restano le ultime copie.
- Se il PC rigenera il certificato (ogni ~2 anni) bisogna riabbinare.
- Le copie sul telefono non sono cifrate a livello applicativo (sono nella memoria privata dell'app); tieni il telefono con blocco schermo.
- Progetto non compilato in locale (manca l'SDK Android su questo PC): la build avviene su GitHub Actions.

## Appuntamenti SomnoDesk (lettura e scrittura, in tempo reale)
Modulo **SOMNO** → Appuntamenti: elenco per giorno, inserimento (+), modifica e rimozione (tocca un appuntamento),
allineati con JARVIS desktop e SomnoDesk tramite lo stesso Firebase (`/sd/apts`).
- **Configurazione (una volta):** Impostazioni → SOMNODESK → URL database e API key (gli stessi di `SOMNO_DB_URL` /
  `SOMNO_API_KEY` del desktop, vedi `electron/services/config.js` o `.env`), email/password solo se usate sul PC → SALVA → PROVA CONNESSIONE.
  I valori non sono nel codice di proposito: il repository è su GitHub e i dati sono sanitari.
- **Sincronia:** con l'app aperta resta collegata in streaming; le modifiche fatte altrove compaiono da sole.
  Ogni scrittura rilegge l'archivio e usa `if-match` (ETag): se un altro dispositivo ha scritto nel frattempo, riprova (max 3).
  Le rimozioni sono come in SomnoDesk (`_deleted` + `updatedAt`), quindi si propagano a tutti.
- **Offline:** si vede l'ultima copia (badge OFFLINE con l'ora), ma inserimenti/modifiche/rimozioni sono bloccati.
- **In chat:** "appuntamenti di domani", "appuntamenti di lunedì", "appuntamenti del 12/11" (sola lettura).
- Nessun dato dei pazienti nei log; backup Android disattivato. Password e refresh token sono cifrati (AES-GCM) con una chiave
  del Keystore Android che non lascia il telefono, e con la stessa chiave è cifrata anche la copia offline degli appuntamenti.
  Valori salvati in chiaro da una versione precedente vengono cifrati al primo uso.
  Se la chiave andasse persa (es. ripristino del telefono) basta reinserire la password nelle Impostazioni; la copia offline si riscarica da sola.

## Pubblicare su GitHub (repository nuovo)
1. Crea un repository vuoto (es. `Jarvisandroid-v3`) e carica il contenuto di questa cartella.
2. **Firma:** per installare la 3.1 *sopra* la versione già sul telefono senza perdere note e modelli,
   copia nel nuovo repository gli stessi secret del vecchio (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
   Senza gli stessi secret l'APK ha una firma diversa: Android rifiuta l'aggiornamento e bisogna disinstallare la vecchia app.
3. Actions → *Build & Release JARVIS NEXUS APK* → scarica `JARVIS-NEXUS-v3.1.N.apk` dalla Release.
   Il `versionCode` parte da 1001, quindi è sempre più alto di quello della v3.0.

---


Progetto Kotlin + Jetpack Compose con HUD 2D animato (anelli rotanti, pulsazione, radar tattico), moduli operativi e terminale.
Il codice è una **base sperimentale**: non è stato compilato o testato su dispositivo in questo ambiente.

## Funzionalità implementate
- UI NEXUS rosso/scuro, modalità TACTICAL, HUD animato con Canvas Compose.
- Terminale testuale; data/ora, stato batteria, impostazioni, note locali.
- Input vocale Android con preferenza offline (non garantisce riconoscimento offline).
- Sintesi vocale Android in italiano (dipende dai pacchetti voce installati).
- MediaPipe LLM Inference per un modello `.bin` compatibile importato dall'utente.
- Build APK debug via GitHub Actions.

## Caricamento su GitHub da Samsung / Codespaces
1. Apri Codespaces del repository e usa **Explorer → ... → Upload** per caricare questo ZIP.
2. Nel terminale del repository: `unzip -o JARVIS-NEXUS-Android.zip`.
3. Esegui `git add . && git commit -m "Add JARVIS NEXUS Android" && git push origin main`.
4. GitHub → Actions → Build JARVIS NEXUS APK. Scarica l'artefatto se la build è riuscita.

## AI offline
L'APK non include il modello. Importa un `.bin` supportato dalla versione MediaPipe usata.
I file GGUF non sono direttamente compatibili. Prestazioni e supporto hardware variano.
L'inferenza del modello è locale, ma il riconoscimento vocale Android potrebbe usare rete.
Non sono presenti wake-word continua, agente con accesso illimitato al telefono, né 3D reale.

## Privacy e limiti
Le note sono conservate in SharedPreferences private dell'app ma non cifrate a livello applicativo.
Non memorizzare password o dati clinici. Nessuna chiave API richiesta.
L'APK prodotto è debug; per distribuzione occorre una firma release.
Se la build fallisce, consulta i log GitHub Actions: la compatibilità delle dipendenze
e dei dispositivi richiede una verifica effettiva.

## Claude Haiku primario + AI locale di riserva
- Nella sezione **AI CORE — CLAUDE HAIKU + LOCALE** inserisci la tua API key creata su console.anthropic.com. La chiave non è nel codice o nel repository.
- Il client usa `claude-haiku-5-5` tramite HTTPS e la Messages API (`/v1/messages`); connessione necessaria, limiti e costi dipendono dal tuo account Anthropic.
- Con Claude disabilitato, rete non disponibile o richiesta cloud fallita, prova MediaPipe locale.
- Per Galaxy S25 Ultra (12 GB RAM) partire da un modello piccolo, circa **1B–2B parametri** ottimizzato per Android. **Non basta un qualsiasi GGUF:** il codice MediaPipe richiede un modello `.bin` compatibile con la versione dell'SDK; verificare disponibilità e supporto prima del download. Una scelta da valutare è una variante MediaPipe di Gemma 2 2B, se compatibile con l'SDK installato.
- Il modello non è incluso nell'APK e va importato dall'interfaccia. Il fallback funziona solo dopo importazione e test.
- **Privacy:** Claude invia i messaggi ad Anthropic. La API key è nelle SharedPreferences private, non cifrata a livello applicativo; per produzione si consiglia backend con autenticazione e limiti di utilizzo. Non includere chiavi in GitHub, screenshot o ZIP.
- Progetto non compilato/testato su dispositivo.

## JARVIS da polso — Galaxy Watch (modulo `wear`)
App Wear OS che usa **lo stesso cervello del telefono**: l'orologio manda la domanda via Wearable Data Layer,
`WearBridgeService` sul telefono risponde con Claude Haiku (chiave locale) e la risposta torna al polso, con voce e vibrazione.

- **Tocca** il nucleo = parla · **Tieni premuto** = tastiera/dettatura · **Ghiera** = scorre la risposta.
- Comandi locali come sul telefono: report Sapio, appuntamenti SomnoDesk, "timer 10 minuti", "sveglia alle 7".
- Azioni dal polso: timer, sveglie, "apri <app dell'orologio>" e "chiama <contatto>" (sull'orologio: il telefono cerca il numero
  in rubrica e l'orologio apre il tastierino; se manca, JARVIS spiega che la chiamata va fatta dal telefono);
  eventi in calendario, note, attività, torcia e volume (sul telefono). Note e attività compaiono subito nell'app del telefono.
- Memoria breve della conversazione (8 messaggi, si azzera dopo 10 minuti).
- **Stato collegamento**: all'avvio l'orologio manda `__ping__`; puntino verde sotto "J.A.R.V.I.S." = telefono pronto,
  rosso = nessuna risposta entro 5 s (telefono scollegato o app JARVIS non aggiornata, con messaggio esplicativo).
- **Tile "JARVIS"**: anello HUD ciano con l'ultima risposta; il tocco apre JARVIS già in ascolto.
  Aggiungila tenendo premuto il quadrante → scorri i riquadri → **+** → JARVIS.
- **Complication "JARVIS"**: icona che apre l'app, da mettere su qualsiasi quadrante (Modifica quadrante → Complicazioni → JARVIS).
- **Quadrante "JARVIS HUD"** (design Visore): ghiera con scala e numeri orientati, scia radar e punto luminoso dei secondi,
  ora in Roboto Thin sfumata, data tra filetti, puntino di collegamento col telefono e due indicatori curvi
  alimentati da complication (default batteria e passi, modificabili dall'editor). Tocco sull'ora = JARVIS in ascolto.
  In ambient restano solo ora, data e tacche delle ore. Selezionalo tenendo premuto il quadrante attuale → scorri → JARVIS HUD.

**Installazione** (la Release contiene anche `JARVIS-WATCH-*.apk`, firmato con la stessa chiave del telefono — obbligatorio):
1. Aggiorna JARVIS sul telefono con l'APK della stessa Release.
2. Orologio: Impostazioni → Opzioni sviluppatore → Debug ADB + Debug wireless → Associa nuovo dispositivo.
3. Dal PC: `adb pair IP:PORTA CODICE`, poi `adb connect IP:PORTA` e `adb install JARVIS-WATCH-*.apk`.
4. Opzionale: Impostazioni → Funzioni avanzate → Personalizza tasti → doppia pressione tasto Home = JARVIS.
