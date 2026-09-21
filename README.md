# ASK Auszahlung – Android-App

Native Android-App (Kotlin, Jetpack Compose) für Auszahlungsbestätigungen pro
Spieler. Drei umschaltbare Ebenen:

1. **Training** – Trainingsliste aus dem Google Sheet, Spalte A bis AM,
   je nach gewähltem Monat, bis zur Zeile mit "ENDE" in Spalte A.
2. **Kosten** – rohe Ansicht der Tabelle "Kosten Spielbetrieb".
3. **Spieler** – Spieler per Dropdown wählen, Fixum/Abzüge werden angezeigt,
   Bemerkungsfeld (max. 250 Zeichen), Unterschriftenfeld ("Betrag erhalten"),
   Button "Daten übernehmen" speichert alles inkl. Unterschrift in Firebase.

**Kein Google-Login in der App nötig.** Die Sheets werden serverseitig über
eine Firebase Cloud Function ("sheetsProxy") gelesen, die mit einem Service
Account auf die Google Sheets zugreift. Die App meldet sich dafür nur
automatisch anonym bei Firebase an (kein Anmeldebildschirm, kein
Testnutzer-Verwalten, kein 7-Tage-Ablauf). Fertige Bestätigungen werden
ebenfalls in Firebase gespeichert – Unterschrift als PNG in Cloud Storage,
restliche Felder in Firestore.

## ⚠️ Wichtige Annahmen – bitte prüfen

Ich hatte keinen Lesezugriff auf die drei verlinkten Google Sheets und konnte
den Aufbau daher nicht direkt einsehen – die folgenden Punkte basieren auf
deinen Beschreibungen und Screenshots. **Alle Spaltenbuchstaben sind in der
App unter „Einstellungen" ohne Neubau änderbar:**

- Beide Tabellen (Trainingsliste, Kosten Spielbetrieb) haben **pro Monat ein
  eigenes Tabellenblatt** (Tab), z.B. „Juli 2026". Der gemeinsame
  Monats-Dropdown zeigt nur Tab-Namen, die in **beiden** Tabellen existieren
  (Schnittmenge) – rein interne Tabs wie „GESAMT" fallen damit automatisch raus.
- Trainingsliste: letzte Datenzeile ist die Zeile **vor** „Masseur Ersatz" in
  Spalte A; diese Zeile selbst wird nicht angezeigt.
- Kosten Spielbetrieb: Name = Spalte A, Fixkosten = Spalte B, AP = Spalte C,
  Punkte = Spalte D, Abzug Sonstiges = Spalte I, Abzug Masseur = Spalte J,
  Punkte-Multiplikator = Spalte O (Punkte-Betrag in Ebene 3 = Punkte ×
  Punkte-Multiplikator).
- Die Detail-Spielertabelle in „Kosten Spielbetrieb" wird automatisch anhand
  der Kopfzeile erkannt, deren erste Zelle „Name" und zweite Zelle „Fixkosten"
  enthält (mindestens 5 befüllte Spalten, um sie von einem kleineren
  „Name/Fixkosten/Bemerkung"-Block weiter oben im Sheet zu unterscheiden).
  Komplett leere Zeilen werden als optischer Leerraum dargestellt.

Falls eine dieser Annahmen nicht stimmt: **Einstellungen → Spaltenzuordnung**
in der App anpassen, keine Codeänderung nötig – außer bei der
Header-Erkennung in „Kosten Spielbetrieb" (dafür bitte kurz melden, welche
Zeile/Spalte nicht richtig erkannt wird).

## ⚠️ Nicht selbst kompiliert / deployt

In meiner Umgebung steht kein Android-SDK, kein Zugriff auf das
Google-Maven-Repository und kein Firebase-Projekt zur Verfügung – ich konnte
weder die App noch die Cloud Function selbst bauen oder testen (dasselbe
Vorgehen wie beim Kassenrechner-Projekt). Der App-Build läuft über die
mitgelieferte GitHub-Actions-Workflow-Datei, die Cloud Function wird über die
Firebase CLI deployt (siehe unten). Sollten Bibliotheksversionen (AGP/Kotlin/
Compose/Firebase/googleapis) inzwischen veraltet sein, meldet das der erste
Build bzw. Deploy – dann einfach die vorgeschlagene neuere Version eintragen.

## Einrichtung – Schritt für Schritt

### 1. Firebase-Projekt einrichten

Unter https://console.firebase.google.com ein neues Projekt anlegen (oder ein
bestehendes Google-Cloud-Projekt verwenden).

1. **Firestore Database** anlegen (Modus "Produktion", Region z.B. `eur3`).
2. **Storage** aktivieren (Cloud Storage für die Unterschrift-PNGs).
3. **Authentication → Sign-in-Methode → Anonym** aktivieren. Die App meldet
   sich automatisch anonym an – ohne Login-Dialog.
4. **Projekteinstellungen → Meine Apps → Android-App hinzufügen**
   - Paketname: `at.mannersdorf.ask.auszahlung`
   - SHA-1-Fingerabdruck (fester Debug-Keystore, im Repo enthalten unter
     `/keystore/debug.keystore`):
     ```
     14:9A:C8:BC:4A:DD:60:AD:47:2A:98:F1:E8:9E:F1:30:CF:C1:03:96
     ```
   - `google-services.json` herunterladen und als `app/google-services.json`
     im Projekt ablegen (Datei mit committen – sie enthält keine Geheimnisse
     und wird von GitHub Actions zum Bauen gebraucht).

### 2. Firebase CLI installieren und Projekt verbinden

```bash
npm install -g firebase-tools
firebase login
cd AuszahlungApp
firebase use --add          # das gerade angelegte Firebase-Projekt auswählen
```

### 3. Cloud Function deployen

```bash
cd functions
npm install
cd ..
firebase deploy --only functions
```

**Falls der Deploy mit "Cannot determine backend specification. Timeout after
10000" abbricht:** Firebase analysiert den Function-Code vor dem Hochladen
lokal und bricht nach 10 Sekunden ab, falls das zu lange dauert – unter
Windows knapp bemessen. Einmal mit mehr Zeit erneut versuchen:

```bash
$env:FUNCTIONS_DISCOVERY_TIMEOUT=60
firebase deploy --only functions
```

Danach in der Google Cloud Console (https://console.cloud.google.com,
dasselbe Projekt) unter **Cloud Functions → sheetsProxy → Details** (oder
**IAM & Verwaltung → IAM**) die **Dienstkonto-E-Mail-Adresse** der Function
nachsehen – das ist i.d.R. etwas wie
`<projektnummer>-compute@developer.gserviceaccount.com`.

### 4. Google Sheets für die Function freigeben

Beide Tabellen (Trainingsliste, Kosten Spielbetrieb) ganz normal per
**Teilen** freigeben – aber diesmal **einmalig** für die in Schritt 3
ermittelte Dienstkonto-E-Mail-Adresse, Berechtigung **"Betrachter"**. Kein
Testnutzer-Verwalten, kein Ablauf nach 7 Tagen, keine einzelnen
Trainer-Google-Konten mehr nötig.

### 5. Sicherheitsregeln deployen

Liegen bereits fertig im Repo (`firestore.rules`, `storage.rules`) – lassen
nur angemeldete (auch anonyme) App-Nutzer lesen/schreiben, niemanden sonst:

```bash
firebase deploy --only firestore:rules,storage:rules
```

**Hinweis zur Sicherheit:** Die anonyme Anmeldung verhindert offenen Zugriff
durch Dritte, unterscheidet aber nicht, *welche* Person eine Bestätigung
gespeichert hat (ähnlich wie ein gemeinsames Server-Passwort). Falls später
nachvollziehbar sein soll, wer welche Auszahlung bestätigt hat, kann das auf
einen echten Google-Sign-In für Firebase Auth umgestellt werden.

### 6. Repository auf GitHub anlegen (App-Build)

```bash
cd AuszahlungApp
git init
git add .
git commit -m "Erste Version ASK Auszahlung"
git branch -M main
git remote add origin https://github.com/<dein-benutzername>/ASK-Auszahlung.git
git push -u origin main
```

Voraussetzung: `app/google-services.json` aus Schritt 1 liegt bereits im
Projekt, sonst schlägt der Build fehl. Danach baut GitHub Actions automatisch
die Debug-APK. Fertige APK unter
*Actions → letzter Lauf → Artefakte → ask-auszahlung-debug-apk*.

### 7. APK installieren

Debug-APK aus GitHub Actions herunterladen, auf das Gerät übertragen,
Installation aus unbekannten Quellen erlauben, installieren.

## Mögliche Erweiterungen (nicht umgesetzt)

- PDF-Export der einzelnen Bestätigung (z.B. zum Ausdrucken oder Mailen)
- Automatisches Zurückschreiben der Bestätigung in das "Bestätigung"-Sheet
- Offline-Zwischenspeicherung, falls kein Netz beim Abzeichnen verfügbar ist
- Übersicht/Historie bereits bestätigter Auszahlungen direkt in der App
  (aktuell nur über die Firebase-Konsole einsehbar: Firestore → Sammlung
  "auszahlungen")
- Echter Google-Sign-In für Firebase Auth statt anonymer Anmeldung, falls
  nachvollziehbar sein soll, wer welche Bestätigung erstellt hat
