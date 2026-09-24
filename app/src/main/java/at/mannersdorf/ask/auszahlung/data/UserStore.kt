package at.mannersdorf.ask.auszahlung.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.mannersdorf.ask.auszahlung.data.model.AppBenutzer
import at.mannersdorf.ask.auszahlung.data.model.Benutzerrolle
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

private val Context.userDataStore by preferencesDataStore(name = "user_session")

/**
 * Verwaltet Benutzer-Session (wer ist eingeloggt) in DataStore und die
 * Benutzerliste in Firestore. Admin-Konto ist immer vorhanden und wird
 * automatisch angelegt falls noch nicht in Firestore.
 */
class UserStore(private val context: Context) {

    private val firestore: FirebaseFirestore = Firebase.firestore

    companion object {
        val KEY_EINGELOGGT_ALS = stringPreferencesKey("eingeloggt_als")
        const val ADMIN_NAME = "admin"
        const val ADMIN_PIN = "24521919"
        const val SPIELER_STANDARD_PIN = "0815"

        /** Alle Spieler von Sutter bis Altun – werden automatisch als User angelegt. */
        val SPIELER_NAMEN = listOf(
            "Sutter Michael", "Holzmann Stefan", "Grohs David", "Nowak Niki",
            "Vojkovic Vedran", "Brettl Stefan", "Bekirovic Erman", "Alic Edin",
            "Harcevic Edin", "Fuchs Marco", "Roth Patrick", "Srok Toni",
            "Vasiljevic Nenad", "Shaljani Drilon", "Dervishi Gentian",
            "Brettl Thomas", "Gaisruck Manuel", "Zupan Andreas", "Altun Serkan"
        )
    }

    /** Liefert den aktuell angemeldeten Benutzernamen (leer = niemand). */
    suspend fun angemeldeterBenutzer(): String =
        context.userDataStore.data.map { it[KEY_EINGELOGGT_ALS] ?: "" }.first()

    suspend fun setzeAngemeldeterBenutzer(name: String) {
        context.userDataStore.edit { it[KEY_EINGELOGGT_ALS] = name }
    }

    suspend fun abmelden() {
        context.userDataStore.edit { it[KEY_EINGELOGGT_ALS] = "" }
    }

    /** Versucht Login mit Benutzername + PIN. Liefert den Benutzer oder null. */
    suspend fun login(benutzername: String, pin: String): AppBenutzer? {
        if (benutzername.trim().equals(ADMIN_NAME, ignoreCase = true) && pin == ADMIN_PIN) {
            setzeAngemeldeterBenutzer(ADMIN_NAME)
            // Beim Admin-Login Spieler-User anlegen falls noch nicht vorhanden
            erstelleSpielerUserFallsFehlend()
            return AppBenutzer(id = "admin", benutzername = ADMIN_NAME, pin = ADMIN_PIN, rolle = Benutzerrolle.ADMINS)
        }
        return try {
            val ergebnis = firestore.collection("benutzer")
                .whereEqualTo("benutzername", benutzername.trim())
                .whereEqualTo("pin", pin)
                .get().await()
            val dok = ergebnis.documents.firstOrNull() ?: return null
            val benutzer = dok.toBenutzer()
            setzeAngemeldeterBenutzer(benutzer.benutzername)
            benutzer
        } catch (e: Exception) { null }
    }

    /** Legt alle Spieler-User an, die noch nicht in Firestore existieren. */
    private suspend fun erstelleSpielerUserFallsFehlend() {
        try {
            val vorhandene = firestore.collection("benutzer").get().await()
                .documents.mapNotNull { it.getString("benutzername") }.toSet()
            for (name in SPIELER_NAMEN) {
                if (name !in vorhandene) {
                    firestore.collection("benutzer").add(
                        hashMapOf("benutzername" to name, "pin" to SPIELER_STANDARD_PIN, "rolle" to Benutzerrolle.SPIELER.name)
                    ).await()
                }
            }
        } catch (_: Exception) {}
    }

    /** Alle Benutzer laden (nur für Admin). */
    suspend fun alleBenutzer(): List<AppBenutzer> = try {
        val ergebnis = firestore.collection("benutzer").get().await()
        ergebnis.documents.map { it.toBenutzer() }
    } catch (e: Exception) { emptyList() }

    /** Benutzer anlegen oder aktualisieren. */
    suspend fun speichereBenutzer(benutzer: AppBenutzer): Result<Unit> = try {
        val daten = hashMapOf(
            "benutzername" to benutzer.benutzername,
            "pin" to benutzer.pin,
            "rolle" to benutzer.rolle.name
        )
        if (benutzer.id.isBlank()) {
            firestore.collection("benutzer").add(daten).await()
        } else {
            firestore.collection("benutzer").document(benutzer.id).set(daten).await()
        }
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /** Benutzer löschen (Admin kann sich selbst nicht löschen). */
    suspend fun loescheBenutzer(id: String): Result<Unit> = try {
        firestore.collection("benutzer").document(id).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    private fun com.google.firebase.firestore.DocumentSnapshot.toBenutzer() = AppBenutzer(
        id = id,
        benutzername = getString("benutzername") ?: "",
        pin = getString("pin") ?: "",
        rolle = runCatching { Benutzerrolle.valueOf(getString("rolle") ?: "") }
            .getOrDefault(Benutzerrolle.SPIELER)
    )
}
