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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import at.mannersdorf.ask.auszahlung.data.UserStore
import at.mannersdorf.ask.auszahlung.data.model.AppBenutzer
import at.mannersdorf.ask.auszahlung.data.model.Benutzerrolle
import at.mannersdorf.ask.auszahlung.ui.LoginScreen
import at.mannersdorf.ask.auszahlung.ui.MainScreen
import at.mannersdorf.ask.auszahlung.ui.theme.AuszahlungAppTheme
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }
    private lateinit var userStore: UserStore

    private val benachrichtigungsBerechtigungAnfrage =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        userStore = UserStore(applicationContext)
        erstelleBenachrichtigungskanal(this)
        stelleBenachrichtigungsBerechtigungSicher()
        FirebaseMessaging.getInstance().subscribeToTopic(PUSH_THEMA)

        val bestaetigungId = intent?.getStringExtra(PUSH_EXTRA_BESTAETIGUNG_ID)
        if (bestaetigungId != null) {
            viewModel.oeffneBestaetigungAusPush(bestaetigungId)
        }

        setContent {
            AuszahlungAppTheme {
                var angemeldeterBenutzer by remember { mutableStateOf<AppBenutzer?>(null) }
                var loginGeprueft by remember { mutableStateOf(false) }

                // Beim Start: vorherige Session wiederherstellen
                LaunchedEffect(Unit) {
                    val gespeicherterName = userStore.angemeldeterBenutzer()
                    if (gespeicherterName.isNotBlank()) {
                        // Admin immer lokal prüfen
                        if (gespeicherterName.equals(UserStore.ADMIN_NAME, ignoreCase = true)) {
                            angemeldeterBenutzer = AppBenutzer(
                                id = "admin",
                                benutzername = UserStore.ADMIN_NAME,
                                pin = UserStore.ADMIN_PIN,
                                rolle = Benutzerrolle.ADMINS
                            )
                        } else {
                            // Anderen Benutzer in Firestore nachschlagen
                            val benutzer = userStore.alleBenutzer()
                                .firstOrNull { it.benutzername == gespeicherterName }
                            angemeldeterBenutzer = benutzer
                        }
                    }
                    loginGeprueft = true
                }

                if (!loginGeprueft) return@AuszahlungAppTheme // kurzes Flackern vermeiden

                if (angemeldeterBenutzer == null) {
                    LoginScreen(
                        userStore = userStore,
                        onAngemeldet = { angemeldeterBenutzer = it }
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        angemeldeterBenutzer = angemeldeterBenutzer!!,
                        userStore = userStore,
                        onAbmelden = {
                            angemeldeterBenutzer = null
                            kotlinx.coroutines.MainScope().launch {
                                userStore.abmelden()
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(PUSH_EXTRA_BESTAETIGUNG_ID)?.let {
            viewModel.oeffneBestaetigungAusPush(it)
        }
    }

    private fun stelleBenachrichtigungsBerechtigungSicher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                benachrichtigungsBerechtigungAnfrage.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
