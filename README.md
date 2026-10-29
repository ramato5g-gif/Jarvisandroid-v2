# J.A.R.V.I.S. NEXUS — Android v3.1 (report mensili dal PC)

Versione 3.1: tutto quello che c'era nella v3.0 **più** il modulo **REPORT**, che tiene sul telefono
una copia aggiornata dei report mensili Sapio (registro + Excel Allegato D) di JARVIS desktop.

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
