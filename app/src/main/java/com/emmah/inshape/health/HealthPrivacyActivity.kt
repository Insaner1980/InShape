package com.emmah.inshape.health

import com.emmah.inshape.ui.theme.withFinnishLocale
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.emmah.inshape.InShapeApplication
import com.emmah.inshape.R
import com.emmah.inshape.ui.components.Page
import com.emmah.inshape.ui.components.PushedHeader
import com.emmah.inshape.ui.components.SectionHeader
import com.emmah.inshape.ui.theme.ActivityTheme
import com.emmah.inshape.ui.theme.InShapeTheme

class HealthPrivacyActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withFinnishLocale())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as InShapeApplication).container
            ActivityTheme(container.themeSettings) {
                val colors = InShapeTheme.colors
                Surface(Modifier.fillMaxSize(), color = colors.background, contentColor = colors.text) {
                    Page(
                        Modifier.navigationBarsPadding(),
                        topBar = { PushedHeader(::finish, title = stringResource(R.string.health_privacy_title)) }
                    ) {
                        SectionHeader(stringResource(R.string.health_privacy_local_title))
                        Text(stringResource(R.string.health_privacy_local_body), style = MaterialTheme.typography.bodyLarge)
                        SectionHeader(stringResource(R.string.health_privacy_sharing_title))
                        Text(stringResource(R.string.health_privacy_sharing_body), style = MaterialTheme.typography.bodyLarge)
                        SectionHeader(stringResource(R.string.health_privacy_removal_title))
                        Text(stringResource(R.string.health_privacy_removal_body), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}
