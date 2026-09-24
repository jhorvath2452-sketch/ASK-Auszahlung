package at.mannersdorf.ask.auszahlung.data

import android.util.Base64
import at.mannersdorf.ask.auszahlung.data.model.Auszahlungsbestaetigung
import at.mannersdorf.ask.auszahlung.data.model.GespeicherteBestaetigung
import at.mannersdorf.ask.auszahlung.data.model.VertragsDatei
import at.mannersdorf.ask.auszahlung.data.model.VertragsFormular
import at.mannersdorf.ask.auszahlung.data.model.VertragsTyp
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Speichert fertig unterschriebene Auszahlungsbestätigungen in Firebase statt
 * auf einem selbst betriebenen Server:
 *  - Unterschrift (PNG) -> Firebase Cloud Storage
 *  - restliche Felder + Download-Link zur Unterschrift -> Firestore
 *
 * Zugriffsschutz: Die App meldet sich anonym bei Firebase Auth an (kein
 * zusätzlicher Login-Dialog nötig). Die Firestore-/Storage-Regeln lassen nur
 * angemeldete (auch anonyme) Nutzer lesen/schreiben – siehe README für den
 * genauen Regeltext. Das verhindert offenen Zugriff durch Dritte, macht aber
 * NICHT einzelne Personen unterscheidbar. Falls eine echte Zuordnung "wer hat
 * das eingetragen" gebraucht wird, kann das später auf einen richtigen
 * Google-Sign-In für Firebase Auth umgestellt werden.
 */
class FirebaseRepository {

    companion object {
        private const val AUFBEWAHRUNG_PAPIERKORB_MS = 40L * 24 * 60 * 60 * 1000
        const val MAX_VERTRAEGE_PRO_SPIELER = 5
    }

    private val auth by lazy { Firebase.auth }
    private val firestore by lazy { Firebase.firestore }
    private val storage by lazy { Firebase.storage }

    /** Meldet die App bei Bedarf anonym bei Firebase an (idempotent, gefahrlos mehrfach aufrufbar). */
    suspend fun stelleSicherAngemeldet() {
        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }
    }

    suspend fun speichereBestaetigung(bestaetigung: Auszahlungsbestaetigung): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()

                val zeitstempel = System.currentTimeMillis()
                val dateiSicherName = "${bestaetigung.spielerName}_$zeitstempel"
                    .replace(Regex("[^A-Za-z0-9äöüÄÖÜß_-]"), "_")

                // 1. Unterschrift als PNG in Cloud Storage hochladen
                val unterschriftBytes = Base64.decode(bestaetigung.unterschriftPngBase64, Base64.NO_WRAP)
                val storageRef = storage.reference
                    .child("unterschriften/${bestaetigung.monat}/$dateiSicherName.png")
                storageRef.putBytes(unterschriftBytes).await()
                val unterschriftUrl = storageRef.downloadUrl.await().toString()

                // 2. Restliche Daten + Link zur Unterschrift in Firestore ablegen
                val dokument = hashMapOf(
                    "monat" to bestaetigung.monat,
                    "saison" to bestaetigung.saison,
                    "spielerName" to bestaetigung.spielerName,
                    "fixum" to bestaetigung.fixum,
                    "punkte" to bestaetigung.punkte,
                    "abzugSonstiges" to bestaetigung.abzugSonstiges,
                    "abzugMasseur" to bestaetigung.abzugMasseur,
                    "korrektur" to bestaetigung.korrektur,
                    "bemerkung" to bestaetigung.bemerkung,
                    "betragErhalten" to bestaetigung.betragErhalten,
                    "unterschriftUrl" to unterschriftUrl,
                    "erstelltAm" to bestaetigung.erstelltAmIso,
                    "serverZeitstempel" to zeitstempel
                )

                firestore.collection("auszahlungen")
                    .document("${bestaetigung.monat}_$dateiSicherName")
                    .set(dokument)
                    .await()

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Ebene "Bestätigungen": lädt die zuletzt gespeicherten, NICHT gelöschten
     * Bestätigungen. Bestätigungen, die vor mehr als 40 Tagen gelöscht wurden,
     * werden dabei endgültig entfernt (Firestore-Dokument + Unterschrift in
     * Storage) - bis dahin liegen sie wie in einem Papierkorb im Hintergrund.
     */
    suspend fun leseBestaetigungen(limit: Long = 300): Result<List<GespeicherteBestaetigung>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("auszahlungen")
                    .orderBy("serverZeitstempel", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()

                val jetzt = System.currentTimeMillis()
                val alle = ergebnis.documents.map { dokument ->
                    GespeicherteBestaetigung(
                        id = dokument.id,
                        monat = dokument.getString("monat") ?: "",
                        saison = dokument.getString("saison") ?: "2026-27",
                        spielerName = dokument.getString("spielerName") ?: "",
                        fixum = dokument.getString("fixum") ?: "",
                        punkte = dokument.getString("punkte") ?: "",
                        abzugSonstiges = dokument.getString("abzugSonstiges") ?: "",
                        abzugMasseur = dokument.getString("abzugMasseur") ?: "",
                        korrektur = dokument.getString("korrektur") ?: "0",
                        bemerkung = dokument.getString("bemerkung") ?: "",
                        betragErhalten = dokument.getString("betragErhalten") ?: "",
                        unterschriftUrl = dokument.getString("unterschriftUrl") ?: "",
                        erstelltAm = dokument.getString("erstelltAm") ?: "",
                        geloeschtAm = dokument.getLong("geloeschtAm")
                    )
                }

                // Endgültig löschen, was schon länger als 40 Tage im Papierkorb liegt.
                for (b in alle) {
                    if (b.geloeschtAm != null && jetzt - b.geloeschtAm > AUFBEWAHRUNG_PAPIERKORB_MS) {
                        loescheEndgueltig(b)
                    }
                }

                val sichtbar = alle.filter { it.geloeschtAm == null }
                Result.success(sichtbar)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Verschiebt eine Bestätigung in den Papierkorb (bleibt 40 Tage erhalten, dann endgültig gelöscht). */
    suspend fun loescheBestaetigung(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                firestore.collection("auszahlungen").document(id)
                    .update("geloeschtAm", System.currentTimeMillis())
                    .await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private suspend fun loescheEndgueltig(bestaetigung: GespeicherteBestaetigung) {
        try {
            if (bestaetigung.unterschriftUrl.isNotBlank()) {
                storage.getReferenceFromUrl(bestaetigung.unterschriftUrl).delete().await()
            }
        } catch (_: Exception) {
            // Unterschrift evtl. schon weg - Firestore-Dokument trotzdem aufräumen.
        }
        try {
            firestore.collection("auszahlungen").document(bestaetigung.id).delete().await()
        } catch (_: Exception) {
            // Wird beim nächsten Laden erneut versucht.
        }
    }

    /** Lädt eine einzelne Bestätigung per ID (z.B. zum Öffnen aus einer Push-Benachrichtigung). */
    suspend fun leseBestaetigung(id: String): Result<GespeicherteBestaetigung> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val dokument = firestore.collection("auszahlungen").document(id).get().await()
                if (!dokument.exists()) {
                    return@withContext Result.failure(NoSuchElementException("Bestätigung nicht gefunden."))
                }
                Result.success(
                    GespeicherteBestaetigung(
                        id = dokument.id,
                        monat = dokument.getString("monat") ?: "",
                        saison = dokument.getString("saison") ?: "2026-27",
                        spielerName = dokument.getString("spielerName") ?: "",
                        fixum = dokument.getString("fixum") ?: "",
                        punkte = dokument.getString("punkte") ?: "",
                        abzugSonstiges = dokument.getString("abzugSonstiges") ?: "",
                        abzugMasseur = dokument.getString("abzugMasseur") ?: "",
                        korrektur = dokument.getString("korrektur") ?: "0",
                        bemerkung = dokument.getString("bemerkung") ?: "",
                        betragErhalten = dokument.getString("betragErhalten") ?: "",
                        unterschriftUrl = dokument.getString("unterschriftUrl") ?: "",
                        erstelltAm = dokument.getString("erstelltAm") ?: "",
                        geloeschtAm = dokument.getLong("geloeschtAm")
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Ebene "Statistik": lädt alle (nicht gelöschten) Bestätigungen einer
     * Person innerhalb einer Saison. Filtert auch Bestätigungen ohne saison-Feld
     * (ältere Einträge) anhand des Monatsnamens.
     */
    suspend fun leseBestaetigungenFuerStatistik(spielerName: String, saison: String): Result<List<GespeicherteBestaetigung>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                // Alle Bestätigungen des Spielers laden (ohne Saison-Filter, da
                // ältere Einträge kein saison-Feld haben) und clientseitig filtern.
                val ergebnis = firestore.collection("auszahlungen")
                    .whereEqualTo("spielerName", spielerName)
                    .get()
                    .await()

                val saisonMonate = when (saison) {
                    "TEST" -> listOf(
                        "Jänner2026", "Februar2026", "März2026", "April2026", "Mai2026",
                        "Juni2026", "Juli2026", "August2026", "September2026", "Oktober2026", "November2026"
                    )
                    else -> listOf(
                        "Jänner2026", "Februar2026", "März2026", "April2026", "Mai2026",
                        "Juni2026", "Juli2026", "August2026", "September2026", "Oktober2026", "November2026"
                    )
                }

                val alle = ergebnis.documents.map { dokument ->
                    GespeicherteBestaetigung(
                        id = dokument.id,
                        monat = dokument.getString("monat") ?: "",
                        saison = dokument.getString("saison") ?: "",
                        spielerName = dokument.getString("spielerName") ?: "",
                        fixum = dokument.getString("fixum") ?: "",
                        punkte = dokument.getString("punkte") ?: "",
                        abzugSonstiges = dokument.getString("abzugSonstiges") ?: "",
                        abzugMasseur = dokument.getString("abzugMasseur") ?: "",
                        korrektur = dokument.getString("korrektur") ?: "0",
                        bemerkung = dokument.getString("bemerkung") ?: "",
                        betragErhalten = dokument.getString("betragErhalten") ?: "",
                        unterschriftUrl = dokument.getString("unterschriftUrl") ?: "",
                        erstelltAm = dokument.getString("erstelltAm") ?: "",
                        geloeschtAm = dokument.getLong("geloeschtAm")
                    )
                }
                // Papierkorb-Einträge rausfiltern; Saison-Zugehörigkeit über
                // gespeichertes saison-Feld ODER Monatsname im Saison-Monatssatz.
                val gefiltert = alle.filter { b ->
                    b.geloeschtAm == null &&
                    (b.saison == saison || b.saison.isBlank() && b.monat in saisonMonate)
                }
                Result.success(gefiltert)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Liefert die Namen aller Personen, die schon einmal eine Bestätigung
     * bekommen haben (Spieler/Masseur/Tormanntrainer/Betreuung gemeinsam,
     * ohne Unterscheidung), für das Namens-Dropdown in der Statistik.
     */
    suspend fun leseAlleBekanntenNamen(): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("auszahlungen").get().await()
                val namen = ergebnis.documents
                    .mapNotNull { it.getString("spielerName") }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .sorted()
                Result.success(namen)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Lädt ein Base64-PNG (z.B. eine Vertrags-Unterschrift) unter dem angegebenen Storage-Pfad hoch, liefert die Download-URL. */
    suspend fun ladeBildHoch(pfad: String, base64Png: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val bytes = Base64.decode(base64Png, Base64.NO_WRAP)
                val referenz = storage.reference.child(pfad)
                referenz.putBytes(bytes).await()
                Result.success(referenz.downloadUrl.await().toString())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Lädt die Unterschrift-PNG-Bytes über die Firebase-Storage-Download-URL. */    suspend fun leseUnterschriftBytes(unterschriftUrl: String): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            try {
                if (unterschriftUrl.isBlank()) return@withContext Result.failure(IllegalArgumentException("Keine Unterschrift-URL"))
                val bytes = storage.getReferenceFromUrl(unterschriftUrl).getBytes(5L * 1024 * 1024).await()
                Result.success(bytes)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Lädt beliebige Datei-Bytes (z.B. ein fertig generiertes Vertrags-PDF) unter dem angegebenen Storage-Pfad hoch, liefert die Download-URL. */
    suspend fun ladeDateiHoch(pfad: String, bytes: ByteArray): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val referenz = storage.reference.child(pfad)
                referenz.putBytes(bytes).await()
                Result.success(referenz.downloadUrl.await().toString())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ---------- Verträge ----------

    /** Lädt alle mit einem Spieler verknüpften Vertragsdateien (max. 5). */
    suspend fun leseVertraegeFuerSpieler(spielerName: String): Result<List<VertragsDatei>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("vertraege")
                    .whereEqualTo("spielerName", spielerName)
                    .get()
                    .await()
                val liste = ergebnis.documents.map { dokument ->
                    VertragsDatei(
                        id = dokument.id,
                        spielerName = dokument.getString("spielerName") ?: "",
                        dateiName = dokument.getString("dateiName") ?: "",
                        downloadUrl = dokument.getString("downloadUrl") ?: "",
                        hochgeladenAm = dokument.getString("hochgeladenAm") ?: ""
                    )
                }.sortedByDescending { it.hochgeladenAm }
                Result.success(liste)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Lädt eine PDF-Datei hoch und verknüpft sie mit einem Spieler. Lehnt ab,
     * wenn der Spieler schon MAX_VERTRAEGE_PRO_SPIELER Dateien hat.
     */
    suspend fun ladeVertragHoch(spielerName: String, dateiName: String, bytes: ByteArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val bisherige = leseVertraegeFuerSpieler(spielerName).getOrDefault(emptyList())
                if (bisherige.size >= MAX_VERTRAEGE_PRO_SPIELER) {
                    return@withContext Result.failure(
                        IllegalStateException("Für $spielerName sind bereits $MAX_VERTRAEGE_PRO_SPIELER Verträge hinterlegt - erst einen entfernen.")
                    )
                }

                val zeitstempel = System.currentTimeMillis()
                val dateiSicherName = dateiName.replace(Regex("[^A-Za-z0-9äöüÄÖÜß_.-]"), "_")
                val speicherName = "${zeitstempel}_$dateiSicherName"
                val storageRef = storage.reference.child("vertraege/$spielerName/$speicherName")
                storageRef.putBytes(bytes).await()
                val downloadUrl = storageRef.downloadUrl.await().toString()

                val dokument = hashMapOf(
                    "spielerName" to spielerName,
                    "dateiName" to dateiName,
                    "downloadUrl" to downloadUrl,
                    "hochgeladenAm" to zeitstempel.toString()
                )
                firestore.collection("vertraege").add(dokument).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Ermittelt die nächste freie Vereinbarungs-Nummer für das aktuelle Jahr
     * (z.B. "2026#003"), gezählt an bereits gestempelten Vereinbarungen -
     * beginnt jedes Jahr wieder bei #001.
     */
    suspend fun ermittleNaechsteVereinbarungsNummer(jahr: Int): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("vereinbarungen")
                    .whereEqualTo("jahr", jahr)
                    .get()
                    .await()
                val naechste = ergebnis.size() + 1
                Result.success("$jahr#" + naechste.toString().padStart(3, '0'))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Speichert die Metadaten einer gestempelten (abgeschlossenen) Vereinbarung, damit die Nummer nie doppelt vergeben wird. */
    suspend fun speichereVereinbarungsNummer(nummer: String, jahr: Int, spielerName: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val dokument = hashMapOf(
                    "nummer" to nummer,
                    "jahr" to jahr,
                    "spielerName" to spielerName,
                    "erstelltAm" to System.currentTimeMillis()
                )
                firestore.collection("vereinbarungen").document(nummer).set(dokument).await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Legt ein Vertragsformular (Entwurf oder gestempelt) an oder aktualisiert es -
     * anhand von [formular.id]: leer -> neues Dokument, sonst Update des
     * bestehenden. Liefert die (neue oder bestehende) Dokument-ID zurück.
     */
    suspend fun speichereVertragsFormular(formular: VertragsFormular): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val daten = hashMapOf(
                    "typ" to formular.typ.name,
                    "nummer" to formular.nummer,
                    "spielerName" to formular.spielerName,
                    "name" to formular.name,
                    "adresse" to formular.adresse,
                    "mail" to formular.mail,
                    "fixum" to formular.fixum,
                    "bonus" to formular.bonus,
                    "siegProPunkt" to formular.siegProPunkt,
                    "unentschieden" to formular.unentschieden,
                    "anmerkungen" to formular.anmerkungen,
                    "datum" to formular.datum,
                    "unterschriftObmannUrl" to formular.unterschriftObmannUrl,
                    "unterschriftSpielerUrl" to formular.unterschriftSpielerUrl,
                    "unterschriftKassierUrl" to formular.unterschriftKassierUrl,
                    "unterschriftSportlicherLeiterUrl" to formular.unterschriftSportlicherLeiterUrl,
                    "gestempelt" to formular.gestempelt,
                    "gestempeltAm" to formular.gestempeltAm,
                    "erstelltAm" to formular.erstelltAm,
                    "fertigesPdfUrl" to formular.fertigesPdfUrl
                )
                val referenz = if (formular.id.isBlank()) {
                    firestore.collection("vertragsformulare").document()
                } else {
                    firestore.collection("vertragsformulare").document(formular.id)
                }
                referenz.set(daten).await()
                Result.success(referenz.id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Lädt alle Vertragsformulare (Entwürfe + gestempelte) eines Spielers, neueste zuerst. */
    suspend fun leseVertragsformulareFuerSpieler(spielerName: String): Result<List<VertragsFormular>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("vertragsformulare")
                    .whereEqualTo("spielerName", spielerName)
                    .get()
                    .await()
                val liste = ergebnis.documents.map { it.toVertragsFormular() }
                    .sortedByDescending { it.erstelltAm }
                Result.success(liste)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loescheVertrag(vertragId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                firestore.collection("vertraege").document(vertragId).delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun loescheVertragsformular(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                firestore.collection("vertragsformulare").document(id).delete().await()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Lädt ein einzelnes Vertragsformular per ID (zum Fortsetzen eines Entwurfs). */
    suspend fun leseVertragsformular(id: String): Result<VertragsFormular> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val dokument = firestore.collection("vertragsformulare").document(id).get().await()
                if (!dokument.exists()) {
                    return@withContext Result.failure(NoSuchElementException("Formular nicht gefunden."))
                }
                Result.success(dokument.toVertragsFormular())
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun DocumentSnapshot.toVertragsFormular(): VertragsFormular =
        VertragsFormular(
            id = id,
            typ = runCatching { VertragsTyp.valueOf(getString("typ") ?: "VEREINBARUNG") }
                .getOrDefault(VertragsTyp.VEREINBARUNG),
            nummer = getString("nummer") ?: "",
            spielerName = getString("spielerName") ?: "",
            name = getString("name") ?: "",
            adresse = getString("adresse") ?: "",
            mail = getString("mail") ?: "",
            fixum = getString("fixum") ?: "",
            bonus = getString("bonus") ?: "",
            siegProPunkt = getString("siegProPunkt") ?: "",
            unentschieden = getString("unentschieden") ?: "",
            anmerkungen = getString("anmerkungen") ?: "",
            datum = getString("datum") ?: "",
            unterschriftObmannUrl = getString("unterschriftObmannUrl") ?: "",
            unterschriftSpielerUrl = getString("unterschriftSpielerUrl") ?: "",
            unterschriftKassierUrl = getString("unterschriftKassierUrl") ?: "",
            unterschriftSportlicherLeiterUrl = getString("unterschriftSportlicherLeiterUrl") ?: "",
            gestempelt = getBoolean("gestempelt") ?: false,
            gestempeltAm = getString("gestempeltAm") ?: "",
            erstelltAm = getString("erstelltAm") ?: "",
            fertigesPdfUrl = getString("fertigesPdfUrl") ?: ""
        )
}
