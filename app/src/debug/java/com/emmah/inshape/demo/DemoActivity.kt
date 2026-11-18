package com.emmah.inshape.demo

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.inshape.AppContainer
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.ActivityTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                withContext(Dispatchers.IO) { DemoFixtures.load(container, name) }
                loaded =
                    name
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message
            }
        }
    }
    override fun onCleared() {
        container.close()
    }
}
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val requested =
            intent.getStringExtra("state")?.takeIf { it in DemoFixtures.names }
                ?: "TodayWithActiveWorkout"
        setContent {
            val host: DemoHost = viewModel()
            ActivityTheme(host.container.themeSettings) {
                LaunchedEffect(requested) { host.load(requested) }
                if (host.loaded == requested) {
                    val vm: com.emmah.inshape.JournalViewModel = viewModel {
                        com.emmah.inshape.JournalViewModel(host.container)
                    }
                    LaunchedEffect(requested) {
                        if (requested == "FailedSave") {
                            vm.write { throw java.io.IOException("Demo: storage is full. Pending changes are retained.") }
                        }
                    }
                    AppNavigation(
                        host.container,
                        initialRoute = DemoFixtures.route(requested),
                        vm = vm
                    )
                } else {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        if (host.error == null) CircularProgressIndicator() else Text(host.error!!)
                    }
                }
            }
        }
    }
}
