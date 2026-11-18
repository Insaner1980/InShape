package com.emmah.inshape.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.theme.ActivityTheme

class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as InShapeApplication).container
            ActivityTheme(container.themeSettings) {
                Page(topBar = { PushedHeader(::finish, title = "Privacy") }) {
                    SectionHeader("What stays on this phone")
                    Text(
                        "Your journal works offline. Sets, equipment settings, check-ins, notes and photos are stored on this phone. " +
                            "A full backup also includes routines and drafts. Keep backups somewhere private."
                    )
                    SectionHeader("What is shared with Health Connect")
                    Text(
                        "Completed workout summaries can be shared with their start, end and activity type. " +
                            "Google Health and other connected apps can read those summaries."
                    )
                    Text(
                        "Steps, sleep and recent heart rate are optional context. " +
                            "They are shown in the app and kept out of your journal. " +
                            "InShape reads its own workout summaries only when you ask it to reconcile a restored workout."
                    )
                    SectionHeader("Removing data")
                    Text(
                        "Manage or revoke permissions in Android Health Connect settings. Turning sharing off stops future sharing. " +
                            "Use the workout's Health Connect controls to remove a shared summary. " +
                            "A pending removal is retained if access is unavailable."
                    )
                }
            }
        }
    }
}
