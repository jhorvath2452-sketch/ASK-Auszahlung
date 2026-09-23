/**
 * ASK Auszahlung – Cloud Function "sheetsProxy"
 * ------------------------------------------------
 * Liest die beiden Google Sheets (Trainingsliste, Kosten Spielbetrieb)
 * serverseitig aus, mit dem Service Account dieser Cloud Function. Die
 * Android-App ruft diese Funktion nur noch als angemeldeter (auch anonymer)
 * Firebase-Nutzer auf – kein eigener Google-Login/OAuth-Consent mehr nötig.
 *
 * Voraussetzung: Die Google Sheets müssen mit der E-Mail-Adresse des
 * Service Accounts dieser Function als "Betrachter" freigegeben sein
 * (siehe README, Abschnitt "Sheets freigeben").
 *
 * Aufruf von der App aus (Beispiel):
 *   { aktion: "listeTabs", spreadsheetId: "..." }
 *     -> { tabs: ["Juli", "August", ...] }
 *   { aktion: "leseRange", spreadsheetId: "...", range: "'Juli'!A1:AM1000" }
 *     -> { werte: [["Spalte A", "Spalte B", ...], [...], ...] }
 */

const { onCall, HttpsError } = require('firebase-functions/v2/https');
const { onDocumentCreated } = require('firebase-functions/v2/firestore');
// Bewusst das schlanke @googleapis/sheets-Paket statt des riesigen
// "googleapis"-Gesamtpakets: Letzteres deckt praktisch alle Google-APIs ab
// und braucht beim Laden (require) oft mehrere Sekunden - unter Windows
// genug, um Firebases 10-Sekunden-Zeitlimit beim Deploy zu reißen
// ("Cannot determine backend specification. Timeout after 10000").
const { sheets } = require('@googleapis/sheets');
const { GoogleAuth } = require('google-auth-library');
const admin = require('firebase-admin');

admin.initializeApp();

const ERLAUBTE_AKTIONEN = new Set(['listeTabs', 'leseRange']);

let sheetsClientPromise = null;

function holeSheetsClient() {
  if (!sheetsClientPromise) {
    sheetsClientPromise = (async () => {
      const auth = new GoogleAuth({
        scopes: ['https://www.googleapis.com/auth/spreadsheets.readonly'],
      });
      const authClient = await auth.getClient();
      return sheets({ version: 'v4', auth: authClient });
    })();
  }
  return sheetsClientPromise;
}

exports.sheetsProxy = onCall({ region: 'europe-west1' }, async (request) => {
  // Nur angemeldete App-Nutzer (auch anonym) dürfen die Function aufrufen.
  if (!request.auth) {
    throw new HttpsError('unauthenticated', 'Bitte zuerst anmelden.');
  }

  const { aktion, spreadsheetId, range } = request.data || {};

  if (!ERLAUBTE_AKTIONEN.has(aktion) || !spreadsheetId) {
    throw new HttpsError('invalid-argument', 'Ungültige Anfrage: aktion/spreadsheetId fehlt.');
  }

  const sheetsClient = await holeSheetsClient();

  try {
    if (aktion === 'listeTabs') {
      const antwort = await sheetsClient.spreadsheets.get({
        spreadsheetId,
        fields: 'sheets.properties.title',
      });
      const tabs = (antwort.data.sheets || []).map((s) => s.properties.title);
      return { tabs };
    }

    if (aktion === 'leseRange') {
      if (!range) {
        throw new HttpsError('invalid-argument', 'range fehlt.');
      }
      const antwort = await sheetsClient.spreadsheets.values.get({
        spreadsheetId,
        range,
      });
      return { werte: antwort.data.values || [] };
    }
  } catch (e) {
    console.error('Sheets-Zugriff fehlgeschlagen', e);
    throw new HttpsError('internal', 'Sheets-Zugriff fehlgeschlagen: ' + e.message);
  }

  // Unerreichbar, da ERLAUBTE_AKTIONEN nur die beiden obigen Fälle enthält.
  throw new HttpsError('invalid-argument', 'Unbekannte Aktion.');
});

/**
 * Löst automatisch eine Push-Benachrichtigung an alle Geräte aus, die das
 * Thema "neue_bestaetigungen" abonniert haben (macht die App beim Start,
 * siehe MainActivity.kt), sobald ein NEUES Dokument in der Firestore-
 * Sammlung "auszahlungen" angelegt wird. Reagiert bewusst nur auf "create"
 * (onDocumentCreated), nicht auf "update" - das weiche Löschen (Papierkorb,
 * setzt nur "geloeschtAm") löst also KEINE Benachrichtigung aus.
 */
exports.benachrichtigeNeueBestaetigung = onDocumentCreated(
  { document: 'auszahlungen/{dokumentId}', region: 'europe-west1' },
  async (event) => {
    const daten = event.data?.data();
    if (!daten) return;

    const spieler = daten.spielerName || 'Unbekannt';
    const monat = daten.monat || '';

    try {
      // Bewusst eine REINE Datennachricht (kein "notification"-Block): Bei
      // einer "notification"-Nachricht zeigt Android die Benachrichtigung
      // selbst an, sobald die App im Hintergrund/geschlossen ist - dabei wird
      // der eigene Öffnen-Code der App (mit der Dokument-ID) übersprungen und
      // stattdessen der App-Standard-Start verwendet. Eine Datennachricht
      // landet dagegen IMMER in onMessageReceived, egal in welchem Zustand
      // die App gerade ist.
      await admin.messaging().send({
        topic: 'neue_bestaetigungen',
        data: {
          titel: 'Neue Auszahlungsbestätigung',
          text: `${spieler} – ${monat}`.trim(),
          dokumentId: event.params.dokumentId,
        },
      });
    } catch (e) {
      console.error('Push-Benachrichtigung fehlgeschlagen', e);
    }
  }
);
