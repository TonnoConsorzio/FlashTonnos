# FlashTonnos 🐟

**FlashTonnos** è un'applicazione Android moderna, fluida e intuitiva per lo studio avanzato basato su flashcard e ripetizione spaziata (*spaced repetition*). Consente di trasformare istantaneamente i tuoi appunti, appunti di studio o file Markdown ospitati su un repository GitHub in mazzi di flashcard interattive generate in tempo reale tramite intelligenza artificiale.

Con un design moderno basato su **Material Design 3**, una gestione dei gesti avanzata e il supporto per modelli linguistici avanzati (tramite OpenRouter), **FlashTonnos** è lo strumento ideale per memorizzare informazioni in modo intelligente, rapido e divertente.

---

## ✨ Caratteristiche Principali

- **☁️ Sincronizzazione con GitHub**: Connetti direttamente il tuo repository di note in formato Markdown (.md) per importarle automaticamente nell'applicazione.
- **🧠 Generazione AI delle Flashcard**: Genera automaticamente 5 carte didattiche di altissima qualità per ciascun file Markdown. Supporta risposte a scelta multipla, Vero/Falso, domande a richiamo attivo e pillole di curiosità, analizzando le tue note tramite **OpenRouter API**.
- **🏷️ Filtri per Argomento (Focus di Studio)**: Ogni flashcard generata include automaticamente tra i 3 e i 5 argomenti chiave (topics) ricoperti. Puoi filtrare il tuo mazzo di studio cliccando sui bellissimi chip interattivi nella dashboard per focalizzarti solo su un determinato argomento.
- **🔥 Streak e Record Personali**: Tieni traccia dei tuoi progressi e mantieni alta la motivazione con statistiche dettagliate:
  - **Streak di Studio Giornaliero**: Conta i giorni consecutivi in cui hai studiato. Include la visualizzazione del tuo record storico assoluto!
  - **Risposte Esatte Consecutive**: Conta quante risposte corrette hai dato di fila durante le tue sessioni di ripasso corrente, con tracking del record storico.
- **⏰ Promemoria Giornaliero**: Un sistema di notifiche push locali configurabile direttamente dalle impostazioni per ricordarti ogni sera alle 20:00 di effettuare il ripasso quotidiano e non perdere la tua streak di studio.
- **🎴 Spaced Repetition e Gesti Fluidi**: Sistema di ripasso basato su gesti intuitivi (*swipe gestures*):
  - 🟩 **Gesto Tinder (Vero o Falso)**: Trascina la carta a destra per "Vero" o a sinistra per "Falso". Più trascini la carta, più lo sfondo si colora gradualmente di verde o di rosso, con un feedback visivo immediato ed estremamente appagante.
  - 🟩 **Destra (Swipe Right)**: Segnala risposta corretta / compresa.
  - 🟥 **Sinistra (Swipe Left)**: Segnala risposta errata / da rivedere.
  - 🟨 **Su (Swipe Up)**: Salta la carta corrente.
  - 🟦 **Giù (Swipe Down)**: Posticipa la carta nel mazzo corrente.
- **📚 Tre Modalità di Studio Avanzate**:
  - **Approfondimento (Deep Dive)**: Studia le pillole informative dettagliate estratte direttamente dai tuoi appunti per memorizzare concetti complessi.
  - **Vero o Falso**: Metti alla prova la tua memoria in modo fulmineo con lo swipe stile Tinder.
  - **Risposta Multipla**: Scegli l'opzione corretta tra quattro alternative generate dall'AI.
- **🔥 Streak in Fiamme (Fire Animation)**: Quando entri in una streak di risposte esatte consecutive (a partire da 9 di fila), la card prende letteralmente fuoco! Un'animazione particellare dinamica aumenta l'intensità delle fiamme man mano che la streak cresce, raggiungendo il picco massimo a 30 risposte esatte per poi stabilizzarsi per non compromettere la visibilità del testo.
- **🎨 Layout Spazioso e Moderno (Breathe Layout)**: Interfaccia utente ridisegnata per dare massima leggibilità. Le domande e i tag sono stati riorganizzati, distanziati in griglia Material 3, e la domanda superiore si nasconde automaticamente quando capovolgi la carta per dare pieno respiro e risalto alla spiegazione e alle risposte.
- **🔄 Sincronizzazione Intelligente e Forza Sincronizzazione**:
  - **Sincronizzazione Incrementale**: Scarica solo i file modificati su GitHub controllando le firme SHA degli indici per risparmiare traffico e tempo.
  - **Forza Sincronizzazione**: Un'opzione dedicata per ignorare la cache e forzare il download completo da zero di tutti i file (es. le tue 75 note), garantendo che tutti i dati locali siano perfettamente allineati e aggiornati.
- **🎨 Temi Visivi Personalizzati**: Supporto completo per diversi schemi di colore moderni (es. Slate, Forest, Sunset, Lavender) per adattarsi alle tue preferenze di studio diurne o notturne.
- **📴 Database Locale (Room/SQLite)**: Tutte le tue flashcard, risposte e progressi di studio sono memorizzati offline sul tuo dispositivo tramite Room Database per un ripasso fulmineo senza latenza.
- **🛡️ Zona di Pericolo nelle Impostazioni**: Pieno controllo sul tuo database. Puoi svuotare completamente l'archivio locale con un solo tocco per ricaricare nuove note o inizializzare l'app da zero.

---

## 🛠️ Stack Tecnologico

- **Linguaggio**: Kotlin
- **UI Framework**: Jetpack Compose (con animazioni fluide, ripple effects e gesti nativi)
- **Architettura**: MVVM (Model-View-ViewModel) + Clean Architecture
- **Data Persistence**: Room Database (SQLite) per caricamento immediato, memorizzazione offline e sincronizzazione
- **Rete**: Retrofit 2 + OkHttp 3 per l'interazione con GitHub e OpenRouter
- **Asincronia**: Kotlin Coroutines e StateFlow per aggiornamenti di stato in tempo reale e reattivi
- **CI/CD**: GitHub Actions per la compilazione automatica ad ogni push

---

## 🚀 Come Iniziare

### Prerequisiti

Per abilitare la generazione automatica delle flashcard dalle tue note, avrai bisogno di:
1. Un **GitHub Personal Access Token (PAT)** con permessi di lettura per la tua repository.
2. Un account su **OpenRouter** con una **API Key** (puoi utilizzare modelli gratuiti o avanzati come `openrouter/auto`).

*Nota: Se non hai ancora configurato queste credenziali, l'app ti consentirà comunque di esplorare le funzionalità utilizzando un deck demo integrato di 150 card piene di curiosità.*

### Configurazione nell'Applicazione

1. Avvia l'applicazione sul tuo dispositivo Android.
2. Clicca su **Inizializza Deck** o vai su **Impostazioni**.
3. Inserisci i tuoi dati:
   - **GitHub Personal Access Token**
   - **GitHub Owner** (es. il tuo nome utente GitHub)
   - **Nome Repository** (es. `le-mie-note`)
   - **Branch** (es. `main`)
   - **OpenRouter API Key**
   - **Cartelle Note / Appunti** (puoi inserire più cartelle separate da virgola, es: `Appunti, note, Scrittura`, per raccogliere tutti i file .md presenti in queste directory).
4. Salva e procedi con l'importazione automatica delle note!

---

## 📦 Integrazione Continua (CI/CD)

Il progetto include un workflow automatizzato tramite **GitHub Actions** (`.github/workflows/build-apk.yml`). 
Ad ogni push sul branch principale (`main` o `master`), il workflow:
1. Configura l'ambiente di build con JDK 17 (Zulu).
2. Sincronizza ed esegue il build del progetto Android.
3. Genera un file APK di Debug compatibile con tutte le ultime versioni di Android.
4. Carica l'APK risultante come artefatto scaricabile direttamente dalla scheda **Actions** della tua repository GitHub.

---

## 📁 Struttura del Progetto

Il codice segue i pattern consigliati per lo sviluppo Android moderno:

- **`com.example.data`**: Gestione dell'accesso ai dati. Contiene le API di Retrofit per GitHub e OpenRouter, la persistenza locale (Room), i ricevitori di allarmi (`ReminderReceiver`) e le impostazioni utente (SharedPreferences/AppPreferences).
- **`com.example.domain`**: Logica di business dell'applicazione, inclusi i modelli di dominio e i repository.
- **`com.example.ui`**: Schermate e componenti scritti interamente in Jetpack Compose, suddivisi in screen (StudyScreen, SettingsScreen, StatsScreen, ecc.) e temi visivi personalizzati.
- **`com.example.di`**: Service Locator / Container per la Dependency Injection manuale e leggera.

---

## 📄 Licenza

Questo progetto è rilasciato sotto la licenza **MIT**.
