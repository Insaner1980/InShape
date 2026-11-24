package com.emmah.inshape

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.demo.DemoActivity
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.feature.settings.label
import com.emmah.inshape.health.HealthPrivacyActivity
import com.emmah.inshape.ui.theme.DarkColors
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.ui.theme.LightColors
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.todayTitle
import com.emmah.inshape.widget.WidgetContent
import com.emmah.inshape.widget.WidgetState
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.runBlocking
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
    private val screenCapture = ReferenceScreenCapture(compose, themeSettings)
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
                val choice = compose.onNodeWithText(ui(mode.label()))
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

    @Test fun workoutNameErrorLeavesTheFieldFocusedWhileCorrectingIt() {
        InstrumentationRegistry.getArguments().getString("themeMode")?.let { themeSettings.set(ThemeMode.valueOf(it)) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val history = Intent(context, DemoActivity::class.java).putExtra("state", "HistoryNovember")
        ActivityScenario.launch<DemoActivity>(history).use {
            compose.waitUntil(30_000) {
                compose.onAllNodesWithContentDescription(ui(R.string.add_past_workout)).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithContentDescription(ui(R.string.add_past_workout)).performClick()
            compose.onNodeWithText(ui(R.string.continue_action)).performClick()

            val field = compose.onNode(hasText(ui(R.string.workout_name)) and hasSetTextAction()).assertIsFocused()
            val message = compose.onNodeWithText(ui(R.string.workout_name_required))
            assertTrue(message.fetchSemanticsNode().boundsInRoot.top > field.fetchSemanticsNode().boundsInRoot.bottom)
            screenCapture.captureFinalScreenshot("22b-history-past-workout-name-required")

            val title = ui(R.string.category_mobility)
            field.performTextInput(title.take(1))
            field.assertIsFocused()
            field.performTextInput(title.drop(1))
            compose.onNodeWithText(title).assertExists()
            message.assertDoesNotExist()
        }
    }

    @Test fun captureSimplificationPaths() {
        val arguments = InstrumentationRegistry.getArguments()
        val label = arguments.getString("captureLabel") ?: "simplification"
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        arguments.getString("themeMode")?.let { themeSettings.set(ThemeMode.valueOf(it)) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.filesDir, "verification/reference/$label").apply { mkdirs() }
        fun capture(name: String, dialog: Boolean = false) {
            compose.waitForIdle()
            val target = if (dialog) compose.onNode(isDialog()) else compose.onRoot()
            val bitmap = target.captureToImage().asAndroidBitmap()
            File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        screenCapture.withDemoState("TodayStrength", hasText(ui(R.string.start_workout))) {
            compose.onNodeWithText(ui(R.string.other_routine)).performClick()
            compose.onNodeWithText(ui(R.string.empty_workout)).assertExists()
            capture("02a-today-start-sheet", dialog = true)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(ui(R.string.music)).performClick()
            compose.onNodeWithText(ui(R.string.playlists)).assertExists()
            capture("40c-music-from-today")
        }
        screenCapture.withDemoState("FinishSummary", hasText(ui(R.string.workout_done))) {
            screenCapture.openFinishPainArea { capture("13e-finish-pain-input") }
            capture("13f-finish-pain-area-sheet", dialog = true)
        }
    }

    private fun openWorkoutAssistant() {
        compose.onNodeWithContentDescription(ui(R.string.named_options, ui(R.string.exercise_lat_pulldown)))
            .performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.workout_assistant)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.record_speech)).assertExists()
    }

    @Test fun programAndSettingsCopy() {
        val context = ApplicationProvider.getApplicationContext<InShapeApplication>()
        val programs = context.container.programs.programs
        val capturing = InstrumentationRegistry.getArguments().getString("captureLabel") != null
        themeSettings.set(ThemeMode.Light)

        fun open(state: String, ready: Int, verify: () -> Unit) {
            ActivityScenario.launch<DemoActivity>(Intent(context, DemoActivity::class.java).putExtra("state", state)).use { scenario ->
                compose.waitUntil(30_000) { compose.onAllNodesWithText(ui(ready)).fetchSemanticsNodes().isNotEmpty() }
                if (capturing) scenario.onActivity { assertEquals(1.15f, it.resources.configuration.fontScale, 0.001f) }
                verify()
            }
        }
        fun click(label: String) {
            val node = compose.onNode(hasText(label) and hasClickAction())
            if (!node.isDisplayed()) node.performScrollTo()
            node.performClick()
        }
        programs.forEachIndexed { index, program ->
            open("TodayStrength", R.string.start_workout) {
                compose.onAllNodesWithText(ui(R.string.library)).onLast().performClick()
                click(program.name)
                val slot = program.sessions.first().exercises.first()
                val rest = ui(R.string.program_guide_rest, slot.restSeconds)
                val alternative = ui(R.string.program_alternative_label) + " " + slot.alternatives.first().label
                compose.onNodeWithText(program.description).assertDoesNotExist()
                compose.onAllNodes(
                    hasText(slot.name) and hasText(ui(R.string.program_slot, slot.sets, slot.target.range))
                )[0].assertIsDisplayed()
                compose.onNodeWithText(rest).assertDoesNotExist()
                compose.onNodeWithText(alternative).assertDoesNotExist()
                screenCapture.captureFinalScreenshot("0${index + 1}-${program.id}")
                if (index == 0) {
                    compose.onNodeWithText("1–2 kertaa viikossa, 30–40 min").assertIsDisplayed()
                    compose.onNode(hasText(slot.name) and hasText("2 × 10–15")).assertIsDisplayed()
                    click(slot.name)
                    compose.onNodeWithText(ui(R.string.exercise_guide)).assertIsDisplayed()
                    compose.onNodeWithText(slot.instruction).assertExists()
                    compose.onNodeWithText(rest).assertExists()
                    compose.onNodeWithText(alternative).assertExists()
                    screenCapture.captureFinalScreenshot("04-liikeohje")
                    compose.onNodeWithContentDescription(ui(R.string.back)).performClick()
                    click(ui(R.string.use_program))
                    screenCapture.captureFinalScreenshot("05-ohjelman-kayttoonotto")
                    click(slot.name)
                    compose.onNode(hasText(ui(R.string.create_from_template)) and hasAnyAncestor(isDialog())).assertIsDisplayed()
                    screenCapture.captureFinalScreenshot("06-laitteen-valinta")
                }
            }
        }
        open("ProgramWorkout", R.string.program_workout) {
            val phase = programs.first().phases.first()
            compose.onNodeWithText("Viikko 1/8").assertIsDisplayed()
            compose.onNodeWithText("1–2 sarjaa, 3–4 toistoa varaa.").assertIsDisplayed()
            compose.onNodeWithText(phase.goal).assertDoesNotExist()
            screenCapture.captureFinalScreenshot("07-ohjelman-treeni")
        }
        open("SettingsConfigured", R.string.settings) {
            screenCapture.captureFinalScreenshot("08-asetukset")
            click(ui(R.string.export))
            compose.onNodeWithText(ui(R.string.export_format_pdf)).assertIsDisplayed()
            screenCapture.captureFinalScreenshot("09-vienti")
        }
        open("Empty", R.string.start_workout) {
            compose.onNodeWithContentDescription(ui(R.string.music)).performClick()
            compose.onNodeWithText(ui(R.string.choose_music_folder)).assertIsDisplayed()
            screenCapture.captureFinalScreenshot("10-musiikkikansion-valinta")
        }
        open("ActiveWorkoutPartiallyDone", R.string.exercise_done) {
            click(ui(R.string.discard_workout))
            compose.onNodeWithText(ui(R.string.discard_workout_question)).assertIsDisplayed()
            screenCapture.captureFinalScreenshot("11-hylkaamisen-vahvistus")
        }
    }

    @Test fun finalRoutineAndExerciseLibraryPaths() {
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString("themeMode")?.let { themeSettings.set(ThemeMode.valueOf(it)) }

        screenCapture.withDemoState("LibraryRoutineExpanded", hasText(ui(R.string.start_workout))) {
            compose.onNodeWithText(quantity(R.plurals.exercise_count, 7)).assertIsDisplayed()
            compose.onNodeWithText(ui(R.string.approximate_duration, "50 min").replaceFirstChar(Char::titlecase)).assertIsDisplayed()
            screenCapture.captureFinalScreenshot("30-library-routine-detail")
            compose.onNodeWithContentDescription(ui(R.string.routine_options)).performClick()
            listOf(R.string.edit_details, R.string.duplicate, R.string.remove).forEach { compose.onNodeWithText(ui(it)).assertExists() }
        }
        screenCapture.withDemoState("TodayStrength", hasText(ui(R.string.start_workout))) {
            compose.onNodeWithText(
                quantity(R.plurals.exercise_count, 7) + ", " + ui(R.string.approximate_duration, "50 min")
            ).assertExists()
            screenCapture.captureFinalScreenshot("02-today-strength")
            compose.onNodeWithText(ui(R.string.library)).performClick()
            compose.onNodeWithText(
                quantity(R.plurals.exercise_count, 7) + ", " + ui(R.string.approximate_duration, "50 min")
            ).assertExists()
            compose.onNodeWithContentDescription(ui(R.string.new_routine)).performClick()
            compose.onNodeWithText(ui(R.string.new_routine)).performClick()
            val field = compose.onNode(hasText(ui(R.string.routine_name)) and hasSetTextAction())
            compose.onNodeWithText(ui(R.string.done)).performClick()
            compose.onNodeWithText(ui(R.string.routine_name_required)).assertExists()
            field.assertIsFocused()
            field.performTextInput("Full body B")
            compose.onNodeWithText(ui(R.string.routine_name_required)).assertDoesNotExist()
            compose.onNodeWithText(ui(R.string.done)).performClick()
            compose.onNodeWithText("Full body B").assertExists()
            compose.onNodeWithContentDescription(ui(R.string.routine_options)).assertDoesNotExist()
            screenCapture.captureFinalScreenshot("31b-library-new-routine-editor")
            compose.onNodeWithContentDescription(ui(R.string.edit_details)).performClick()
            compose.onNodeWithText(ui(R.string.routine_details)).assertExists()
            screenCapture.captureFinalScreenshot("31c-library-new-routine-details-dialog")
        }
        // The Today artwork demos deliberately omit workouts. Use the regular November history here.
        screenCapture.withDemoState("TodayWithActiveWorkout", hasText(ui(R.string.resume_workout))) {
            compose.onNodeWithText(ui(R.string.library)).performClick()
            compose.onNodeWithText(ui(R.string.exercises)).performClick()
            val recordedDate = LocalDate.of(2026, 11, 11).shortDate()
            listOf(R.string.exercise_chest_press, R.string.exercise_lat_pulldown).forEach { exercise ->
                val summary = ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 3, 10), ui(R.string.weight_value, "25"))
                val row = hasText(ui(exercise)) and hasText("$summary, $recordedDate")
                compose.onNode(row).performScrollTo().assertIsDisplayed()
            }
            screenCapture.captureFinalScreenshot("34-library-exercises")
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test
    fun captureIdleWidget() = runBlocking {
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: return@runBlocking
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.filesDir, "verification/reference/$label").apply { mkdirs() }
        val today = LocalDate.now(DemoFixtures.clock)
        val routineName = DemoFixtures.FULL_BODY
        val week = weekStart(today)
        val state = WidgetState(
            week,
            2,
            3,
            false,
            null,
            today = today,
            completedDays = setOf(week, week.plusDays(2)),
            nextRoutineTitle = routineName
        )
        val glance = GlanceRemoteViews()
        for (width in listOf(Dimens.widgetNarrow, Dimens.widgetWide)) {
            val views = glance.compose(context, DpSize(width, Dimens.widgetHeight)) {
                WidgetContent(
                    state,
                    actionStartActivity(Intent(context, MainActivity::class.java)),
                    timer = null,
                    time = null,
                    workoutCount = quantity(R.plurals.workout_count, state.count)
                )
            }.remoteViews
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val density = context.resources.displayMetrics.density
                val widthPx = (width.value * density).roundToInt()
                val heightPx = (Dimens.widgetHeight.value * density).roundToInt()
                val widget = views.apply(context, FrameLayout(context))
                widget.measure(
                    View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY)
                )
                widget.layout(0, 0, widthPx, heightPx)

                fun View.textViews(): Sequence<TextView> = when (this) {
                    is TextView -> sequenceOf(this)
                    is ViewGroup -> (0 until childCount).asSequence().flatMap { getChildAt(it).textViews() }
                    else -> emptySequence()
                }
                listOf(routineName, ui(R.string.start)).forEach { text ->
                    val view = widget.textViews().single { it.text.toString() == text }
                    val layout = checkNotNull(view.layout)
                    assertTrue("$text fits the widget", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                    assertTrue("$text fits the widget height", layout.height <= view.height)
                }
                val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                try {
                    widget.draw(Canvas(bitmap))
                    val name = "52-widget-${width.value.roundToInt()}x${Dimens.widgetHeight.value.roundToInt()}.png"
                    File(output, name).outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                } finally {
                    bitmap.recycle()
                }
            }
        }
    }

    @Test fun captureFinnishReviewScreens() {
        screenCapture.captureFinnishReviewScreens()
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
        val todayTitle = LocalDate.now(DemoFixtures.clock).todayTitle()
        fun inspectRowSlot(row: SemanticsMatcher, title: String, slot: SemanticsMatcher, leading: Boolean) {
            compose.onNode(row).performScrollTo()
            val titleNode = compose.onNode(hasText(title), useUnmergedTree = true).fetchSemanticsNode()
            val bounds = titleNode.boundsInRoot
            val parentId = checkNotNull(titleNode.parent).id
            val withinRow = hasAnyAncestor(SemanticsMatcher("the exercise row") { it.id == parentId })
            val icon = compose.onNode(slot and withinRow, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val besideTitle = if (leading) icon.right <= bounds.left else icon.left >= bounds.right
            if (!besideTitle || kotlin.math.abs(icon.top - bounds.top) > 1f) {
                issues += "$title: marker is outside the first title line (title $bounds, marker $icon)"
            }
            geometry += "$title: title $bounds, marker $icon"
        }
        fun inspectText(state: String) {
            val exerciseTitles = when (state) {
                "39a-ohjelman-laitevalinta" -> (context as InShapeApplication).container.programs.programs.first()
                    .sessions.first().exercises.map { it.name }.toSet()

                "36-rutiiniavustaja" -> setOf(
                    ui(R.string.exercise_chest_press),
                    ui(R.string.exercise_leg_curl),
                    ui(R.string.exercise_hip_flexor_stretch)
                )

                else -> emptySet()
            }
            val inspectedTitles = mutableSetOf<String>()
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
                            val text = layout.layoutInput.text.text
                            if (text in exerciseTitles) {
                                inspectedTitles += text
                                val splitsWord = (1 until layout.lineCount).any { line ->
                                    val start = layout.getLineStart(line)
                                    start in 1 until text.length && text[start - 1].isLetter() && text[start].isLetter()
                                }
                                if (splitsWord) issues += "$state: exercise title splits a word: $text"
                            }
                            val isTodayTitle = state.startsWith("Today") && text == todayTitle
                            val expectedEllipsis = isTodayTitle && layout.layoutInput.density.fontScale >= 1.3f &&
                                layout.lineCount == 1 && layout.isLineEllipsized(0)
                            // Android can measure alignment against the available width before Compose sizes the node.
                            // Check both the line advance and its intrinsic width without using that offset.
                            val centeredLineFits = layout.layoutInput.style.textAlign == TextAlign.Center &&
                                layout.lineCount == 1 && !layout.isLineEllipsized(0) &&
                                layout.multiParagraph.intrinsics.maxIntrinsicWidth <= layout.size.width + 1f &&
                                layout.getLineRight(0) - layout.getLineLeft(0) <= layout.size.width + 1f
                            (isTodayTitle && layout.lineCount != 1) ||
                                (layout.multiParagraph.didExceedMaxLines && !expectedEllipsis) ||
                                layout.multiParagraph.height > layout.size.height + 1f ||
                                (
                                    !centeredLineFits && (0 until layout.lineCount).any {
                                        layout.getLineLeft(it) < -1f || layout.getLineRight(it) > layout.size.width + 1f
                                    }
                                    )
                        }.forEach {
                            issues +=
                                "$state: clipped text: ${it.layoutInput.text.text} (${it.size}, height ${it.multiParagraph.height}, " +
                                "intrinsic width ${it.multiParagraph.intrinsics.maxIntrinsicWidth}, " +
                                "line bounds ${(0 until it.lineCount).joinToString { line ->
                                    "${it.getLineLeft(line)}..${it.getLineRight(line)}"
                                }})"
                        }
                }
            if (exerciseTitles.isNotEmpty()) {
                if (inspectedTitles !=
                    exerciseTitles
                ) {
                    issues += "$state: missing title measurements for ${exerciseTitles - inspectedTitles}"
                }
                geometry += "$state: ${inspectedTitles.size} ListRow titles measured"
            }
        }
        val states = InstrumentationRegistry.getArguments().getString("referenceStates")?.split(",")
            ?: DemoFixtures.names.take(9)
        require(states.all { it in DemoFixtures.names })
        states.forEach { state ->
            screenCapture.withDemoState(state, matcher) { scenario ->
                compose.waitForIdle()
                if (state == "Empty") {
                    compose.onNodeWithText(ui(R.string.empty_workout)).assertIsDisplayed()
                    compose.onNode(
                        hasText(ui(R.string.start_workout)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
                    )
                        .assertIsDisplayed().assertIsEnabled()
                }
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
                inspectText(state)
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
                    inspectText(state)
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
        val extraScreens = listOf(
            Triple("FinishSummary", "13f-finish-pain-area-sheet", { screenCapture.openFinishPainArea() }),
            Triple("ActiveWorkoutPartiallyDone", "10b-treeniavustaja", { openWorkoutAssistant() }),
            Triple("TodayStrength", "39a-ohjelman-laitevalinta", {
                screenCapture.openProgram()
                compose.onNodeWithText(ui(R.string.use_program)).performClick()
                compose.onNodeWithText(ui(R.string.choose_equipment)).assertExists()
                val selected = hasContentDescription(ui(R.string.program_equipment_selected))
                val row = compose.onAllNodes(selected)[0].fetchSemanticsNode()
                val title = row.config[SemanticsProperties.Text].first().text
                inspectRowSlot(hasText(title) and selected, title, selected, leading = false)
            }),
            Triple("TodayStrength", "36-rutiiniavustaja", {
                compose.onAllNodesWithText(ui(R.string.library)).onLast().performClick()
                compose.onNodeWithContentDescription(ui(R.string.new_routine)).performClick()
                compose.onNodeWithText(ui(R.string.build_routine)).performClick()
                val title = ui(R.string.exercise_chest_press)
                val checkbox = hasTestTag("routineExerciseCheckbox")
                inspectRowSlot(hasText(title) and hasClickAction(), title, checkbox, leading = true)
            }),
            Triple("HistoryNovember", "20c-kuukausivalinta", { screenCapture.openHistoryMonthPicker() })
        )
        extraScreens.forEach { (state, name, open) ->
            screenCapture.withDemoState(state, matcher) { scenario ->
                open()
                compose.waitForIdle()
                inspectText(name)
                screenCapture.screenshot(output, name)
                scenario.onActivity { activity ->
                    val configuration = activity.resources.configuration
                    geometry += "$name: font=${configuration.fontScale}, widthDp=${configuration.screenWidthDp}"
                }
            }
        }
        File(output, "layout-results.txt").writeText(
            geometry.joinToString("\n") + "\n" +
                if (issues.isEmpty()) {
                    "All ${states.size + extraScreens.size} screens inspected. Text fits its bounds " +
                        "and the measured markers align with the first title line."
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
