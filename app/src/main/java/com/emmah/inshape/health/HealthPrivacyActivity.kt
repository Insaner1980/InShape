package com.emmah.inshape.health

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PrimaryButton
import com.emmah.inshape.ui.components.SectionLabel
import com.emmah.inshape.ui.theme.ActivityTheme

class HealthPrivacyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ActivityTheme((application as InShapeApplication).container.themeSettings) {
                Surface {
                    Page {
                        SectionLabel("InShape and your health data")
                        Text(
                            "Your journal is stored locally and works offline. Health Connect sharing, backups and exports are optional."
                        )
                        Text(
                            "Each capability is optional. Steps, sleep and recent heart rate are read only to show " +
                                "context while you use the app. They are not saved in your journal or sent to a server."
                        )
                        Text(
                            "Workout sharing writes a summary with its actual start, end and activity type. Sets, " +
                                "photos, equipment settings, check-ins and private notes stay in your local journal."
                        )
                        Text(
                            "Disabling sharing stops future uploads. Existing shared records remain until you " +
                                "explicitly remove them. Local deletions retain a pending removal if Health Connect access " +
                                "is unavailable."
                        )
                        Text(
                            "Exercise-read access is requested only when you explicitly choose to reconcile one " +
                                "restored workout. InShape searches its own previously shared summaries for that workout's " +
                                "stable identity. It does not read other apps' workouts."
                        )
                        Text(
                            "You can manage or revoke permissions in Android Health Connect settings. You can export " +
                                "your local data or delete individual records from InShape."
                        )
                        PrimaryButton("Close", ::finish)
                    }
                }
            }
        }
    }
}
