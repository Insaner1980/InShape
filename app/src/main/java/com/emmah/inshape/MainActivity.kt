package com.emmah.inshape

import android.os.Bundle
import android.content.Intent
import androidx.compose.runtime.*
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.ActivityTheme

class MainActivity : ComponentActivity() {
    private var entryAction by mutableStateOf<Pair<String, Long>?>(null)
    private var entrySequence = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptIntent(intent)
        enableEdgeToEdge()
        val container = (application as InShapeApplication).container
        setContent { ActivityTheme(container.themeSettings) { AppNavigation(container, entryAction, consumeEntry = { entryAction = null }) } }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); acceptIntent(intent) }
    private fun acceptIntent(intent: Intent) { if (intent.action in setOf(START_OR_RESUME, OPEN_TODAY)) entryAction = intent.action!! to ++entrySequence }
    companion object {
        const val START_OR_RESUME = "com.emmah.inshape.START_OR_RESUME"
        const val OPEN_TODAY = "com.emmah.inshape.OPEN_TODAY"
    }
}
