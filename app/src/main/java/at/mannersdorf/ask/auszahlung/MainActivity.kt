package at.mannersdorf.ask.auszahlung

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import at.mannersdorf.ask.auszahlung.ui.MainScreen
import at.mannersdorf.ask.auszahlung.ui.theme.AuszahlungAppTheme
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    private val benachrichtigungsBerechtigungAnfrage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* egal ob ja/nein - Themenabo geht so oder so */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        erstelleBenachrichtigungskanal(this)
        stelleBenachrichtigungsBerechtigungSicher()
        // Alle Geräte abonnieren gemeinsam dasselbe Thema statt einzelne
        // Token zu verwalten - die Cloud Function sendet neue Bestätigungen
        // einfach an "neue_bestaetigungen" (siehe /functions).
        FirebaseMessaging.getInstance().subscribeToTopic(PUSH_THEMA)

        behandleBenachrichtigungsIntent(intent)

        setContent {
            AuszahlungAppTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    // Wird aufgerufen, wenn die App schon läuft und eine Benachrichtigung
    // angetippt wird (onCreate läuft dann NICHT erneut).
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        behandleBenachrichtigungsIntent(intent)
    }

    private fun behandleBenachrichtigungsIntent(intent: Intent?) {
        val bestaetigungId = intent?.getStringExtra(PUSH_EXTRA_BESTAETIGUNG_ID)
        if (bestaetigungId != null) {
            viewModel.oeffneBestaetigungAusPush(bestaetigungId)
        }
    }

    private fun stelleBenachrichtigungsBerechtigungSicher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val bereitsErteilt = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!bereitsErteilt) {
                benachrichtigungsBerechtigungAnfrage.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
