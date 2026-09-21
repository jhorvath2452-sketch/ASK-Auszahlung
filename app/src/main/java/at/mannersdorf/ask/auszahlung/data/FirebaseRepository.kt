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

    /** Ebene 4 ("Bestätigungen"): lädt die zuletzt gespeicherten Bestätigungen. */
    suspend fun leseBestaetigungen(limit: Long = 300): Result<List<GespeicherteBestaetigung>> =
        withContext(Dispatchers.IO) {
            try {
                stelleSicherAngemeldet()
                val ergebnis = firestore.collection("auszahlungen")
                    .orderBy("serverZeitstempel", Query.Direction.DESCENDING)
                    .limit(limit)
                    .get()
                    .await()

                val liste = ergebnis.documents.map { dokument ->
                    GespeicherteBestaetigung(
                        id = dokument.id,
                        monat = dokument.getString("monat") ?: "",
                        spielerName = dokument.getString("spielerName") ?: "",
                        fixum = dokument.getString("fixum") ?: "",
                        punkte = dokument.getString("punkte") ?: "",
                        abzugSonstiges = dokument.getString("abzugSonstiges") ?: "",
                        abzugMasseur = dokument.getString("abzugMasseur") ?: "",
                        korrektur = dokument.getString("korrektur") ?: "0",
                        bemerkung = dokument.getString("bemerkung") ?: "",
                        betragErhalten = dokument.getString("betragErhalten") ?: "",
                        unterschriftUrl = dokument.getString("unterschriftUrl") ?: "",
                        erstelltAm = dokument.getString("erstelltAm") ?: ""
                    )
                }
                Result.success(liste)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Lädt die Unterschrift-PNG-Bytes über die Firebase-Storage-Download-URL. */
    suspend fun leseUnterschriftBytes(unterschriftUrl: String): Result<ByteArray> =
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
