package com.emmah.inshape

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui.theme.ActivityTheme
import com.emmah.inshape.widget.InShapeWidgetReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

open class MainActivity : ComponentActivity() {
    protected open val allowWorkoutStart = false
    private var entryAction by mutableStateOf<Pair<String, Long>?>(null)
    private var entrySequence = 0L

    // Optional preview publication can fail inside Glance or the launcher without blocking the app.
    @Suppress("TooGenericExceptionCaught")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 35 && savedInstanceState == null) {
            lifecycleScope.launch {
                try {
                    GlanceAppWidgetManager(this@MainActivity).setWidgetPreviews(InShapeWidgetReceiver::class)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("InShape", "Widget preview could not be published", failure)
                }
            }
        }
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
