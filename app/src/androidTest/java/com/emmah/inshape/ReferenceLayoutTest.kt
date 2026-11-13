package com.emmah.inshape

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.demo.DemoActivity
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt

/** Captures real natural scroll content once, retaining fixed chrome only once. */
@OptIn(ExperimentalTestApi::class)
class ReferenceLayoutTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val themeSettings = ThemeSettings(ApplicationProvider.getApplicationContext())
    private val originalTheme = themeSettings.mode.value
    @After fun restoreTheme() { themeSettings.set(originalTheme) }

    @Test fun allEightReferenceScreensScrollWithoutTextClipping() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: "390-100"
        InstrumentationRegistry.getArguments().getString("themeMode")?.let { themeSettings.set(ThemeMode.valueOf(it)) }
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        val output = File(context.filesDir, "verification/reference/$label").apply { mkdirs() }
        val issues = linkedSetOf<String>()
        val geometry = mutableListOf<String>()
        val matcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        DemoFixtures.names.take(8).forEach { state ->
            ActivityScenario.launch<DemoActivity>(Intent(context, DemoActivity::class.java).putExtra("state", state)).use { scenario ->
                compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                val pageNode = compose.onAllNodes(matcher).fetchSemanticsNodes().maxBy { it.boundsInRoot.width * it.boundsInRoot.height }
                val page = compose.onNode(SemanticsMatcher("main scroll page") { it.id == pageNode.id })
                val pieces = mutableListOf<Bitmap>()
                fun inspectText() {
                    compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes().forEach { node ->
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                        // Android text metrics can exceed integer raster bounds by a fractional pixel.
                        layouts.filter { layout -> layout.multiParagraph.didExceedMaxLines || layout.multiParagraph.height > layout.size.height + 1f ||
                            (0 until layout.lineCount).any { layout.getLineLeft(it) < -1f || layout.getLineRight(it) > layout.size.width + 1f }
                        }.forEach { issues += "$state: clipped text: ${it.layoutInput.text.text} (${it.size}, height ${it.multiParagraph.height})" }
                    }
                }
                val first = compose.onRoot().captureToImage().asAndroidBitmap()
                val top = pageNode.boundsInRoot.top.roundToInt().coerceIn(0, first.height)
                val bottom = pageNode.boundsInRoot.bottom.roundToInt().coerceIn(top + 1, first.height)
                val viewport = bottom - top
                scenario.onActivity { activity ->
                    val insets = activity.window.decorView.rootWindowInsets
                    geometry += "$state: root=${first.width}x${first.height}, scroll=$top..$bottom, decor=${activity.window.decorView.width}x${activity.window.decorView.height}, systemInsets=${insets?.systemWindowInsetTop}/${insets?.systemWindowInsetBottom}, font=${activity.resources.configuration.fontScale}, widthDp=${activity.resources.configuration.screenWidthDp}"
                }
                if (top > 0) pieces += Bitmap.createBitmap(first, 0, 0, first.width, top)
                pieces += Bitmap.createBitmap(first, 0, top, first.width, viewport)
                val footer = if (bottom < first.height) Bitmap.createBitmap(first, 0, bottom, first.width, first.height - bottom) else null
                inspectText()
                var previous = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                var steps = 0
                while (true) {
                    val range = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                    if (range.value() >= range.maxValue() - 1f) break
                    check(++steps <= 80) { "Scroll did not terminate for $state" }
                    page.performSemanticsAction(SemanticsActions.ScrollBy) { action -> action(0f, (viewport - 80).toFloat()) }
                    compose.waitForIdle()
                    val current = page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
                    val added = (current - previous).roundToInt().coerceAtMost(viewport)
                    check(added > 0) { "Scroll did not move for $state" }
                    val frame = compose.onRoot().captureToImage().asAndroidBitmap()
                    pieces += Bitmap.createBitmap(frame, 0, bottom - added, frame.width, added)
                    inspectText(); previous = current
                }
                footer?.let(pieces::add)
                val result = Bitmap.createBitmap(first.width, pieces.sumOf { it.height }, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result); var y = 0f
                pieces.forEach { piece -> canvas.drawBitmap(piece, 0f, y, null); y += piece.height; piece.recycle() }
                File(output, "$state.png").outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
                result.recycle()
            }
        }
        File(output, "layout-results.txt").writeText(geometry.joinToString("\n") + "\n" + if (issues.isEmpty()) "All eight screens traversed. No text extends beyond raster bounds by more than one pixel." else issues.joinToString("\n"))
        assertTrue(issues.joinToString("\n"), issues.isEmpty())
    }
}
