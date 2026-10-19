# J.A.R.V.I.S. Android — Samsung Galaxy S25 Ultra

Progetto Android **Kotlin**, con inferenza LLM locale tramite **MediaPipe LLM Inference**.
Nessuna chiave API e nessun servizio AI remoto. L'APK non contiene il modello.

## Installazione su GitHub (senza Android Studio)
1. Apri il repository `ramato5g-gif/Jarvisandroid-v2`.
2. Seleziona **Add file → Upload files**.
3. **Estrai questo ZIP** e carica i file e le cartelle contenuti **alla radice**, non lo ZIP.
   In particolare carica anche `.github/workflows/build-apk.yml` (cartella nascosta).
   Su Android il browser potrebbe non permettere l'upload di intere cartelle: usa un PC,
   oppure GitHub Codespaces/terminale e `git add . && git commit && git push`.
4. Vai su **Actions → Build Android APK → Run workflow**.
5. Se la compilazione riesce, scarica l'artifact `jarvis-debug-apk` e installa `app-debug.apk`.

## Modello
All'interno dell'app premi **MODELLO** e importa un file `.bin` *compatibile*
con la versione di MediaPipe LLM Inference inclusa nel progetto. **GGUF non è
compatibile direttamente**. I modelli possono richiedere una conversione
specifica e licenze/accettazione dei termini del distributore.
Documentazione: https://ai.google.dev/edge/mediapipe/solutions/genai/llm_inference/android

## Funzioni disponibili
- Chat AI interamente sul dispositivo, dopo importazione di un modello supportato
- Input testuale e riconoscimento vocale Android (offline *richiesto*, ma non garantito)
- Sintesi vocale italiana Android (offline dipende dalla voce installata)
- Cronologia locale persistente, cancellabile dall'app
- Note locali: «ricorda ...», «mostra note», «cancella note»
- Data, ora, fotocamera, impostazioni e ricerca tramite intent Android
- Build automatica APK debug su GitHub Actions

## Limiti e privacy
- **La build non è stata eseguita né verificata in questo ambiente**: controlla i log di Actions.
- Non è presente ascolto continuo, wake word, automazione invasiva o controllo arbitrario delle app.
- La cronologia e le note sono salvate in SharedPreferences private, ma **non cifrate
  a livello applicativo**. Non salvare password o dati sanitari sensibili.
- Le funzioni AI sono offline; riconoscimento vocale e ricerca possono usare servizi di terze parti.
- L'APK è di debug, non è firmato per distribuzione Play Store.
- Il motore usa MediaPipe per semplicità; la migrazione a LiteRT-LM richiede
  un'integrazione dedicata e un modello nel formato supportato.
