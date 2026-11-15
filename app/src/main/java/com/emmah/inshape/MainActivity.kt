package com.emmah.inshape

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.ActivityTheme

open class MainActivity : ComponentActivity() {
    protected open val allowWorkoutStart = false
    private var entryAction by mutableStateOf<Pair<String, Long>?>(null)
    private var entrySequence = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) acceptIntent(intent)
        enableEdgeToEdge()
        val container = (application as InShapeApplication).container
        setContent {
            ActivityTheme(container.themeSettings) {
                AppNavigation(container, entryAction = entryAction, consumeEntry = {
                    entryAction =
                        null
                })
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptIntent(intent)
    }
    private fun acceptIntent(intent: Intent) {
        if (intent.action in listOf(OPEN_TODAY, OPEN_WORKOUT, OPEN_FINISH) || (allowWorkoutStart && intent.action == START_OR_RESUME)) {
            entryAction = intent.action!! to ++entrySequence
        }
    }
    companion object {
        const val START_OR_RESUME = "com.emmah.inshape.START_OR_RESUME"
        const val OPEN_WORKOUT = "com.emmah.inshape.OPEN_WORKOUT"
        const val OPEN_FINISH = "com.emmah.inshape.OPEN_FINISH"
        const val OPEN_TODAY = "com.emmah.inshape.OPEN_TODAY"
    }
}
