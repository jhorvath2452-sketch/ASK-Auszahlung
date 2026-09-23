package at.mannersdorf.ask.auszahlung.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import at.mannersdorf.ask.auszahlung.data.model.SpaltenZuordnung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ask_auszahlung_settings")

/**
 * Speichert alles, was in den Einstellungen konfigurierbar ist: die drei Sheet-IDs
 * und die Spaltenzuordnung der Kosten-Spielbetrieb-Tabelle. So muss nichts hart
 * codiert werden, falls sich das Tabellen-Layout ändert. Die Speicherung der
 * Bestätigungen selbst läuft über Firebase (Konfiguration kommt aus
 * app/google-services.json, nicht aus diesen Einstellungen).
 */
class SettingsStore(private val context: Context) {

    private object Keys {
        val TRAININGSLISTE_SHEET_ID = stringPreferencesKey("trainingsliste_sheet_id")
        val KOSTEN_SPIELBETRIEB_SHEET_ID = stringPreferencesKey("kosten_spielbetrieb_sheet_id")
        val BESTAETIGUNG_SHEET_ID = stringPreferencesKey("bestaetigung_sheet_id")
        val SPALTE_NAME = stringPreferencesKey("spalte_name")
        val SPALTE_FIXUM = stringPreferencesKey("spalte_fixum")
        val SPALTE_AP = stringPreferencesKey("spalte_ap")
        val SPALTE_PUNKTE = stringPreferencesKey("spalte_punkte")
        val SPALTE_PUNKTE_MULTIPLIKATOR = stringPreferencesKey("spalte_punkte_multiplikator")
        val SPALTE_ABZUG_SONSTIGES = stringPreferencesKey("spalte_abzug_sonstiges")
        val SPALTE_ABZUG_MASSEUR = stringPreferencesKey("spalte_abzug_masseur")
        val SPALTE_EINSAETZE = stringPreferencesKey("spalte_einsaetze")
        val SPALTE_TRAININGSGELD_FAKTOR = stringPreferencesKey("spalte_trainingsgeld_faktor")
        val SPALTE_MASSEUR_FAKTOR = stringPreferencesKey("spalte_masseur_faktor")
        val SPALTE_MASSEUR_EINSAETZE = stringPreferencesKey("spalte_masseur_einsaetze")
    }

    // Standardwerte = die drei vom Verein bereits genutzten Google Sheets ("2026-27").
    companion object {
        const val STANDARD_TRAININGSLISTE_ID = "1Nl5F1yldZqP4ynr95D9v_6V_NGT6fZOxF9JSxt0jDdM"
        const val STANDARD_KOSTEN_SPIELBETRIEB_ID = "1dXEs3xbxmoxiPMXPJImKOXe95A46MyiFEjzlMZsUAD0"
        const val STANDARD_BESTAETIGUNG_ID = "10oE94grUj815c9CZfBzf8bvb27hENpk3JKOm8REUlyg"

        // TEST-Tabellen, zwischen denen in der App umgeschaltet werden kann.
        const val TEST_TRAININGSLISTE_ID = "1NusC2oNH_-iEAmWvOGi_a0R10qRY6FU6BrYDSWUltW8"
        const val TEST_KOSTEN_SPIELBETRIEB_ID = "1vMxCVmezteoAYMAjz-hFNpMbW3SiYC3FMZ39E3oLSiE"
    }

    val trainingslisteSheetId: Flow<String> = context.dataStore.data
        .map { it[Keys.TRAININGSLISTE_SHEET_ID] ?: STANDARD_TRAININGSLISTE_ID }

    val kostenSpielbetriebSheetId: Flow<String> = context.dataStore.data
        .map { it[Keys.KOSTEN_SPIELBETRIEB_SHEET_ID] ?: STANDARD_KOSTEN_SPIELBETRIEB_ID }

    val bestaetigungSheetId: Flow<String> = context.dataStore.data
        .map { it[Keys.BESTAETIGUNG_SHEET_ID] ?: STANDARD_BESTAETIGUNG_ID }

    val spaltenZuordnung: Flow<SpaltenZuordnung> = context.dataStore.data.map {
        SpaltenZuordnung(
            nameSpalte = it[Keys.SPALTE_NAME] ?: "A",
            fixumSpalte = it[Keys.SPALTE_FIXUM] ?: "B",
            apSpalte = it[Keys.SPALTE_AP] ?: "C",
            punkteSpalte = it[Keys.SPALTE_PUNKTE] ?: "D",
            punkteMultiplikatorSpalte = it[Keys.SPALTE_PUNKTE_MULTIPLIKATOR] ?: "O",
            abzugSonstigesSpalte = it[Keys.SPALTE_ABZUG_SONSTIGES] ?: "I",
            abzugMasseurSpalte = it[Keys.SPALTE_ABZUG_MASSEUR] ?: "J",
            einsaetzeSpalte = it[Keys.SPALTE_EINSAETZE] ?: "N",
            trainingsgeldFaktorSpalte = it[Keys.SPALTE_TRAININGSGELD_FAKTOR] ?: "L",
            masseurFaktorSpalte = it[Keys.SPALTE_MASSEUR_FAKTOR] ?: "F",
            masseurEinsaetzeSpalte = it[Keys.SPALTE_MASSEUR_EINSAETZE] ?: "S"
        )
    }

    suspend fun leseAlleEinstellungenEinmalig() = context.dataStore.data.first()

    suspend fun speichereSheetIds(trainingsliste: String, kostenSpielbetrieb: String, bestaetigung: String) {
        context.dataStore.edit {
            it[Keys.TRAININGSLISTE_SHEET_ID] = trainingsliste
            it[Keys.KOSTEN_SPIELBETRIEB_SHEET_ID] = kostenSpielbetrieb
            it[Keys.BESTAETIGUNG_SHEET_ID] = bestaetigung
        }
    }

    suspend fun speichereSpaltenZuordnung(zuordnung: SpaltenZuordnung) {
        context.dataStore.edit {
            it[Keys.SPALTE_NAME] = zuordnung.nameSpalte
            it[Keys.SPALTE_FIXUM] = zuordnung.fixumSpalte
            it[Keys.SPALTE_AP] = zuordnung.apSpalte
            it[Keys.SPALTE_PUNKTE] = zuordnung.punkteSpalte
            it[Keys.SPALTE_PUNKTE_MULTIPLIKATOR] = zuordnung.punkteMultiplikatorSpalte
            it[Keys.SPALTE_ABZUG_SONSTIGES] = zuordnung.abzugSonstigesSpalte
            it[Keys.SPALTE_ABZUG_MASSEUR] = zuordnung.abzugMasseurSpalte
            it[Keys.SPALTE_EINSAETZE] = zuordnung.einsaetzeSpalte
            it[Keys.SPALTE_TRAININGSGELD_FAKTOR] = zuordnung.trainingsgeldFaktorSpalte
            it[Keys.SPALTE_MASSEUR_FAKTOR] = zuordnung.masseurFaktorSpalte
            it[Keys.SPALTE_MASSEUR_EINSAETZE] = zuordnung.masseurEinsaetzeSpalte
        }
    }
}
