package com.emmah.inshape.transfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import com.emmah.inshape.AppContainer
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.clickText
import com.emmah.inshape.data.newId
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.navigation.AppNavigation
import com.emmah.inshape.ui
import com.emmah.inshape.ui.theme.InShapeTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ExportFlowJvmTest {
    @get:Rule val compose = createComposeRule()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "export-${newId().take(8)}.db"
    private val app = AppContainer(context, DemoFixtures.clock, name, integrationsEnabled = false)

    @After fun close() {
        app.close()
        context.deleteDatabase(name)
        app.storageRoot.deleteRecursively()
    }

    @Test fun savesCsvWithNotes() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        runBlocking { DemoFixtures.load(app, "HistoryNovember") }
        val output = ByteArrayOutputStream()
        val destination = Uri.parse("content://test-export/archive")
        shadowOf(context.contentResolver).registerOutputStream(destination, output)
        val owner = object : ActivityResultRegistryOwner {
            var lastRequest: Intent? = null
            var pickerCode: Int? = null
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(
                    requestCode: Int,
                    contract: ActivityResultContract<I, O>,
                    input: I,
                    options: ActivityOptionsCompat?
                ) {
                    lastRequest = contract.createIntent(context, input)
                    pickerCode = requestCode
                }
            }
        }
        lateinit var model: JournalViewModel
        compose.setContent {
            val vm = viewModel { JournalViewModel(app) }
            model = vm
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides owner) {
                InShapeTheme { AppNavigation(app, initialRoute = "Export", vm = vm) }
            }
        }
        val format = ui(R.string.export_format_csv)
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(format)).fetchSemanticsNodes().isNotEmpty() }
        compose.clickText(format)
        compose.onNodeWithContentDescription(ui(R.string.include_notes)).performScrollTo().performClick()
        compose.clickText(ui(R.string.save_to_files))
        compose.waitUntil(10_000) {
            compose.runOnIdle {
                val transfer = model.transfers.state.value
                check(!transfer.isError) { transfer.message }
                owner.pickerCode != null
            }
        }
        val request = compose.runOnIdle {
            owner.activityResultRegistry.dispatchResult(requireNotNull(owner.pickerCode), Activity.RESULT_OK, Intent().setData(destination))
            requireNotNull(owner.lastRequest)
        }
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, request.action)
        assertEquals("application/zip", request.type)
        assertEquals("InShape-CSV.zip", request.getStringExtra(Intent.EXTRA_TITLE))
        val saved = ui(R.string.export_saved, "CSV")
        compose.waitUntil(10_000) {
            val transfer = model.transfers.state.value
            check(!transfer.isError) { transfer.message }
            compose.onAllNodes(hasText(saved)).fetchSemanticsNodes().isNotEmpty()
        }
        val tables = ZipInputStream(ByteArrayInputStream(output.toByteArray())).use { zip ->
            buildMap {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    put(entry.name, zip.readBytes().decodeToString())
                }
            }
        }
        assertTrue(DemoFixtures.FULL_BODY in tables.getValue("workouts.csv"))
        assertTrue("Tuntui hyvältä." in tables.getValue("exercises.csv"))
    }
}
