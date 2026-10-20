# J.A.R.V.I.S. NEXUS — Android (Samsung Galaxy S25 Ultra)

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

## Gemini primario + AI locale di riserva
- Nel pannello **GEMINI CLOUD + FALLBACK LOCALE** inserisci la tua API key creata su Google AI Studio. La chiave non è nel codice o nel repository.
- Il client usa `gemini-2.5-flash` tramite HTTPS e `generateContent`; connessione necessaria, limiti e costi dipendono dal tuo account Google.
- Con Gemini disabilitato, rete non disponibile o richiesta cloud fallita, prova MediaPipe locale.
- Per Galaxy S25 Ultra (12 GB RAM) partire da un modello piccolo, circa **1B–2B parametri** ottimizzato per Android. **Non basta un qualsiasi GGUF:** il codice MediaPipe richiede un modello `.bin` compatibile con la versione dell'SDK; verificare disponibilità e supporto prima del download. Una scelta da valutare è una variante MediaPipe di Gemma 2 2B, se compatibile con l'SDK installato.
- Il modello non è incluso nell'APK e va importato dall'interfaccia. Il fallback funziona solo dopo importazione e test.
- **Privacy:** Gemini invia i messaggi a Google. La API key è nelle SharedPreferences private, non cifrata a livello applicativo; per produzione si consiglia backend con autenticazione e limiti di utilizzo. Non includere chiavi in GitHub, screenshot o ZIP.
- Progetto non compilato/testato su dispositivo.
