package com.emmah.inshape.demo

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.emmah.inshape.AppContainer
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.ActivityTheme
import kotlinx.coroutines.*

class DemoHost(application: Application) : AndroidViewModel(application) {
    val container = AppContainer(application, DemoFixtures.clock, "reference-demo.db", integrationsEnabled = false)
    var loaded by mutableStateOf<String?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    fun load(name: String) {
        if (loaded == name) return
        viewModelScope.launch { try { withContext(Dispatchers.IO) { DemoFixtures.load(container, name) }; loaded = name } catch (failure: Exception) { error = failure.message } }
    }
    override fun onCleared() { container.close(); super.onCleared() }
}
class DemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); enableEdgeToEdge()
        val requested = intent.getStringExtra("state")?.takeIf { it in DemoFixtures.names } ?: "TodayWithActiveWorkout"
        setContent {
            val host: DemoHost = viewModel()
            ActivityTheme(host.container.themeSettings) {
            LaunchedEffect(requested) { host.load(requested) }
            if (host.loaded == requested) {
                Box(Modifier.fillMaxSize()) {
                    AppNavigation(host.container, initialRoute = DemoFixtures.route(requested), initialExpandedRoutineId = if (requested == "LibraryRoutineExpanded") DemoFixtures.routineId else null)
                    if (requested == "FailedSave") Surface(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(16.dp)) {
                        com.emmah.inshape.ui.components.SaveStatus(com.emmah.inshape.SaveState.Failed("Demo: storage is full. Pending changes are retained."), {})
                    }
                }
            } else Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                if (host.error == null) CircularProgressIndicator() else Text(host.error!!)
            }
        } }
    }
}
