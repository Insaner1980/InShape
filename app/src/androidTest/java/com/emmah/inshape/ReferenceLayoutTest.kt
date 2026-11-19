package com.emmah.inshape

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.demo.DemoActivity
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.health.HealthPrivacyActivity
import com.emmah.inshape.ui.theme.DarkColors
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import java.io.File
import kotlin.math.roundToInt
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Captures real natural scroll content once, retaining fixed chrome only once. */
@OptIn(ExperimentalTestApi::class)
class ReferenceLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val themeSettings = ThemeSettings(ApplicationProvider.getApplicationContext())
    private val originalTheme = themeSettings.mode.value

    @After fun restoreTheme() {
        themeSettings.set(originalTheme)
    }

    @Test fun themePreviewsSwitchTheAppThemeAndExposeSelection() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        ActivityScenario.launch<DemoActivity>(Intent(context, DemoActivity::class.java).putExtra("state", "SettingsConfigured")).use {
            compose.waitUntil(30_000) {
                compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            for (mode in listOf(ThemeMode.Dark, ThemeMode.Light, ThemeMode.System)) {
                val choice = compose.onNodeWithText(mode.name)
                choice.performScrollTo().performClick().assertIsSelected()
                assertEquals(mode, ThemeSettings(context).mode.value)
                if (mode != ThemeMode.System) {
                    val pixels = compose.onRoot().captureToImage().toPixelMap()
                    val y = choice.fetchSemanticsNode().boundsInRoot.center.y.toInt().coerceIn(0, pixels.height - 1)
                    val expected = if (mode == ThemeMode.Dark) DarkColors.background else LightColors.background
                    assertEquals(expected.toArgb(), pixels[pixels.width / 100, y].toArgb())
                }
            }
        }
    }

    @Test fun privacyParagraphsUseTheThemeTextAndBackgroundColors() {
        val context = ApplicationProvider.getApplicationContext<InShapeApplication>()
        val settings = context.container.themeSettings
        val savedAppearance = settings.mode.value
        val paragraphs = listOf(
            R.string.health_privacy_local_body,
            R.string.health_privacy_sharing_body,
            R.string.health_privacy_context_body,
            R.string.health_privacy_removal_body
        )
        try {
            for ((mode, colors) in listOf(ThemeMode.Dark to DarkColors, ThemeMode.Light to LightColors)) {
                settings.set(mode)
                ActivityScenario.launch(HealthPrivacyActivity::class.java).use {
                    val contrast = (maxOf(colors.text.luminance(), colors.background.luminance()) + .05f) /
                        (minOf(colors.text.luminance(), colors.background.luminance()) + .05f)
                    assertTrue("Privacy contrast ($mode): $contrast", contrast >= 4.5f)
                    paragraphs.forEach { resource ->
                        val paragraph = compose.onNodeWithText(context.getString(resource)).performScrollTo()
                        val pixels = paragraph.captureToImage().toPixelMap()
                        fun contains(argb: Int): Boolean = (0 until pixels.height).any { y ->
                            (0 until pixels.width).any { x -> pixels[x, y].toArgb() == argb }
                        }
                        assertTrue("Privacy paragraph foreground ($mode, $resource)", contains(colors.text.toArgb()))
                        assertTrue("Privacy paragraph background ($mode, $resource)", contains(colors.background.toArgb()))
                    }
                }
            }
        } finally {
            settings.set(savedAppearance)
        }
    }

    @Test fun allReferenceScreensScrollWithoutTextClipping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "390-100"
        InstrumentationRegistry.getArguments().getString("themeMode")?.let {
            themeSettings.set(ThemeMode.valueOf(it))
        }
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        val output = File(context.filesDir, "verification/reference/$label").apply { mkdirs() }
        val issues = linkedSetOf<String>()
        val geometry = mutableListOf<String>()
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        val states = InstrumentationRegistry.getArguments().getString("referenceStates")?.split(",")
            ?: DemoFixtures.names.take(9)
        require(states.all { it in DemoFixtures.names })
        states.forEach { state ->
            ActivityScenario.launch<DemoActivity>(
                Intent(context, DemoActivity::class.java).putExtra("state", state)
            ).use { scenario ->
                compose.waitUntil(30_000) {
                    compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
                }
                compose.waitForIdle()
                val pageNode = compose.onAllNodes(matcher).fetchSemanticsNodes().maxBy {
                    it.boundsInRoot.width *
                        it.boundsInRoot.height
                }
                val page = compose.onNode(
                    SemanticsMatcher("main scroll page") {
                        it.id ==
                            pageNode.id
                    }
                )
                val pieces = mutableListOf<Bitmap>()

                fun inspectText() {
                    compose
                        .onAllNodes(
                            SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult),
                            useUnmergedTree = true
                        ).fetchSemanticsNodes()
                        .forEach { node ->
                            val layouts = mutableListOf<TextLayoutResult>()
                            node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(
                                layouts
                            )
                            // Android text metrics can exceed integer raster bounds by a fractional pixel.
                            layouts
                                .filter { layout ->
                                    layout.multiParagraph.didExceedMaxLines ||
                                        layout.multiParagraph.height > layout.size.height + 1f ||
                                        (0 until layout.lineCount).any {
                                            layout.getLineLeft(it) < -1f ||
                                                layout.getLineRight(it) > layout.size.width + 1f
                                        }
                                }.forEach {
                                    issues +=
                                        "$state: clipped text: ${it.layoutInput.text.text} (${it.size}, height ${it.multiParagraph.height})"
                                }
                        }
                }
                val first = compose.onRoot().captureToImage().asAndroidBitmap()
                File(output, "$state-viewport.png").outputStream().use {
                    first.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                val top =
                    pageNode.boundsInRoot.top
                        .roundToInt()
                        .coerceIn(0, first.height)
                val bottom =
                    pageNode.boundsInRoot.bottom
                        .roundToInt()
                        .coerceIn(top + 1, first.height)
                val viewport = bottom - top
                scenario.onActivity { activity ->
                    val insets = activity.window.decorView.rootWindowInsets
                    geometry +=
                        "$state: root=${first.width}x${first.height}" +
                        ", scroll=$top..$bottom, decor=${activity.window.decorView.width}x${activity.window.decorView.height}" +
                        ", systemInsets=${insets?.systemWindowInsetTop}/${insets?.systemWindowInsetBottom}" +
                        ", font=${activity.resources.configuration.fontScale}" +
                        ", widthDp=${activity.resources.configuration.screenWidthDp}"
                }
                if (top > 0) pieces += Bitmap.createBitmap(first, 0, 0, first.width, top)
                pieces += Bitmap.createBitmap(first, 0, top, first.width, viewport)
                val footer = if (bottom <
                    first.height
                ) {
                    Bitmap.createBitmap(
                        first,
                        0,
                        bottom,
                        first.width,
                        first.height - bottom
                    )
                } else {
                    null
                }
                inspectText()
                var previous = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                var steps = 0
                while (true) {
                    val range = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                    if (range.value() >= range.maxValue() - 1f) break
                    check(++steps <= 80) { "Scroll did not terminate for $state" }
                    page.performSemanticsAction(SemanticsActions.ScrollBy) { action ->
                        action(
                            0f,
                            (
                                viewport -
                                    80
                                ).toFloat()
                        )
                    }
                    compose.waitForIdle()
                    val current = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    val added = (current - previous).roundToInt().coerceAtMost(viewport)
                    check(added > 0) { "Scroll did not move for $state" }
                    val frame = compose.onRoot().captureToImage().asAndroidBitmap()
                    pieces += Bitmap.createBitmap(frame, 0, bottom - added, frame.width, added)
                    inspectText()
                    previous = current
                }
                footer?.let(pieces::add)
                val result = Bitmap.createBitmap(
                    first.width,
                    pieces.sumOf {
                        it.height
                    },
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(result)
                var y = 0f
                pieces.forEach { piece ->
                    canvas.drawBitmap(piece, 0f, y, null)
                    y += piece.height
                    piece.recycle()
                }
                File(output, "$state.png").outputStream().use {
                    result.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                result.recycle()
            }
        }
        File(output, "layout-results.txt").writeText(
            geometry.joinToString("\n") + "\n" +
                if (issues.isEmpty()) {
                    "All ${states.size} screens traversed. No text extends beyond raster bounds by more than one pixel."
                } else {
                    issues
                        .joinToString(
                            "\n"
                        )
                }
        )
        assertTrue(issues.joinToString("\n"), issues.isEmpty())
    }
}
