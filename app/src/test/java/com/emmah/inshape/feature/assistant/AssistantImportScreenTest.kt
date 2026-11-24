package com.emmah.inshape.feature.assistant

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.emmah.inshape.JournalViewModel
import com.emmah.inshape.R
import com.emmah.inshape.RedesignInteractionFixture
import com.emmah.inshape.data.JournalSnapshot
import com.emmah.inshape.ui.theme.InShapeTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class AssistantImportScreenTest : RedesignInteractionFixture() {
    @Test fun failedCropDisablesImportAt320Dp() = assertFailedCropCannotReusePriorImage(width = 320, fontScale = 1f)

    @Test fun failedCropDisablesImportAt200PercentFontScale() = assertFailedCropCannotReusePriorImage(width = 390, fontScale = 2f)

    @Test fun failedCropDisablesImportAt320DpAnd200PercentFontScale() = assertFailedCropCannotReusePriorImage(width = 320, fontScale = 2f)

    private fun assertFailedCropCannotReusePriorImage(width: Int, fontScale: Float) {
        val container = createContainer()
        val source = File.createTempFile("assistant-import-test-", ".png", context.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
            try {
                source.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
            val picker = object : ActivityResultRegistryOwner {
                override val activityResultRegistry = object : ActivityResultRegistry() {
                    override fun <I, O> onLaunch(
                        requestCode: Int,
                        contract: ActivityResultContract<I, O>,
                        input: I,
                        options: ActivityOptionsCompat?
                    ) {
                        dispatchResult(requestCode, listOf(Uri.fromFile(source)))
                    }
                }
            }
            compose.setContent {
                val vm = viewModel<JournalViewModel> { JournalViewModel(container) }
                val window = LocalWindowInfo.current
                val sizedWindow = object : WindowInfo by window {
                    override val containerDpSize = DpSize(width.dp, window.containerDpSize.height)
                }
                CompositionLocalProvider(
                    LocalActivityResultRegistryOwner provides picker,
                    LocalWindowInfo provides sizedWindow,
                    LocalDensity provides Density(LocalDensity.current.density, fontScale)
                ) {
                    InShapeTheme {
                        Box(Modifier.width(width.dp)) {
                            AssistantImportScreen(false, JournalSnapshot(), vm, {}, { _, _ -> })
                        }
                    }
                }
            }
            val review = compose.onNodeWithText(context.getString(R.string.review_extraction))
            val preview = context.getString(R.string.selected_image, 1)
            review.assertIsNotEnabled()
            compose.onNodeWithText(context.getString(R.string.choose_images)).performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithContentDescription(preview).fetchSemanticsNodes().isNotEmpty() }
            review.assertIsEnabled()

            assertTrue(source.delete())
            compose.onNodeWithText(context.getString(R.string.crop_image, 1)).performScrollTo().performClick()
            val error = context.getString(R.string.prepare_image_failed)
            compose.waitUntil(10_000) { compose.onAllNodesWithText(error).fetchSemanticsNodes().isNotEmpty() }

            review.assertIsNotEnabled()
            compose.onNodeWithContentDescription(preview).assertDoesNotExist()
        } finally {
            source.delete()
        }
    }
}
