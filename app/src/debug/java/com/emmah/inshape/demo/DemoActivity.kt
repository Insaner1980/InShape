package com.emmah.inshape.demo

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.ActivityTheme
import com.emmah.inshape.ui.theme.withFinnishLocale
import kotlinx.coroutines.launch

class DemoHost(application: Application) : AndroidViewModel(application) {
    val container =
        AppContainer(
            application,
            DemoFixtures.clock,
            "reference-demo.db",
            integrationsEnabled = false
        )
    var loaded by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    // The demo reports loading errors on screen but must propagate lifecycle cancellation.
    @Suppress("TooGenericExceptionCaught")
    fun load(name: String) {
        if (loaded == name) return
        viewModelScope.launch {
            try {
                DemoFixtures.load(container, name)
                loaded = name
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = container.context.getString(R.string.demo_load_failed)
            }
        }
    }
    override fun onCleared() {
        container.close()
    }
}
class DemoActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFinnishLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requested =
            intent.getStringExtra("state")?.takeIf { it in DemoFixtures.names }
                ?: "TodayWithActiveWorkout"
        setContent {
            val host: DemoHost = viewModel()
            ActivityTheme(host.container.themeSettings) {
                DemoScreen(host, requested)
            }
        }
    }
}

@Composable
private fun DemoScreen(host: DemoHost, requested: String) {
    LaunchedEffect(requested) { host.load(requested) }
    if (host.loaded == requested) {
        LoadedDemoScreen(host, requested)
    } else {
        val error = host.error
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error == null) CircularProgressIndicator() else Text(error)
        }
    }
}

@Composable
private fun LoadedDemoScreen(host: DemoHost, requested: String, vm: JournalViewModel = viewModel { JournalViewModel(host.container) }) {
    LaunchedEffect(requested) {
        if (requested == "FailedSave") {
            vm.write { throw java.io.IOException() }
        }
    }
    AppNavigation(
        host.container,
        initialRoute = DemoFixtures.route(requested),
        musicFixture = DemoMusic.catalog.takeIf { requested in DemoMusic.states },
        initialMusic = if (requested == "WorkoutWithMusic") DemoMusic.catalog.playlists.first().tracks else emptyList(),
        vm = vm
    )
}
