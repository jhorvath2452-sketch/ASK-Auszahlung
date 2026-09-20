package at.mannersdorf.ask.auszahlung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import at.mannersdorf.ask.auszahlung.ui.MainScreen
import at.mannersdorf.ask.auszahlung.ui.theme.AuszahlungAppTheme
import at.mannersdorf.ask.auszahlung.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AuszahlungAppTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }
}
