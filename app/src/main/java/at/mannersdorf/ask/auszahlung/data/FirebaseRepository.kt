package at.mannersdorf.ask.auszahlung.data

import android.util.Base64
import at.mannersdorf.ask.auszahlung.data.model.Auszahlungsbestaetigung
import at.mannersdorf.ask.auszahlung.data.model.GespeicherteBestaetigung
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
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
     * Person innerhalb einer Saison, egal ob Spieler/Masseur/Tormanntrainer/
     * Betreuung - für die Monats- bzw. Saisonsumme.
     */
    suspend fun leseBestaetigungenFuerStatistik(spielerName: String, saison: String): Result<List<GespeicherteBestaetigung>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("auszahlungen")
                    .whereEqualTo("spielerName", spielerName)
                    .whereEqualTo("saison", saison)
                    .get()
                    .await()

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
                Result.success(alle.filter { it.geloeschtAm == null })
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
}
