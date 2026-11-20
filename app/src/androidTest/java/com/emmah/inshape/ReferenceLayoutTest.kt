package com.emmah.inshape

import com.emmah.inshape.demo.DemoMusic
import androidx.compose.ui.test.onLast
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import com.emmah.inshape.data.weekStart
import com.emmah.inshape.ui.theme.Dimens
import com.emmah.inshape.widget.WidgetContent
import com.emmah.inshape.widget.WidgetState
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.hasContentDescription
import com.emmah.inshape.feature.checkin.BodyLevel
import com.emmah.inshape.feature.checkin.EffortLevel
import com.emmah.inshape.feature.checkin.EnergyLevel
import android.view.KeyEvent
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.SemanticsNodeInteraction
import com.emmah.inshape.ui.theme.monthTitle
import java.time.YearMonth
import com.emmah.inshape.feature.settings.label
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
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
import com.emmah.inshape.ui.theme.shortDate
import com.emmah.inshape.ui.theme.todayTitle
import java.io.File
import java.time.LocalDate
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
            captureFinalScreenshot("22b-history-past-workout-name-required")

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
        fun scenario(state: String, ready: String, test: () -> Unit) {
            val intent = Intent(context, DemoActivity::class.java).putExtra("state", state)
            ActivityScenario.launch<DemoActivity>(intent).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty() }
                test()
            }
        }
        scenario("TodayStrength", ui(R.string.start_workout)) {
            compose.onNodeWithText(ui(R.string.other_routine)).performClick()
            compose.onNodeWithText(ui(R.string.empty_workout)).assertExists()
            capture("02a-today-start-sheet", dialog = true)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(ui(R.string.music)).performClick()
            compose.onNodeWithText(ui(R.string.playlists)).assertExists()
            capture("40c-music-from-today")
        }
        scenario("FinishSummary", ui(R.string.workout_done)) {
            openFinishPainArea { capture("13e-finish-pain-input") }
            capture("13f-finish-pain-area-sheet", dialog = true)
        }
    }

    private fun openFinishPainArea(beforeArea: () -> Unit = {}) {
        compose.onNodeWithText(ui(R.string.yes)).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.pick_from_list)).performScrollTo()
        beforeArea()
        compose.onNodeWithText(ui(R.string.pick_from_list)).performClick()
        bodyAreaList().performScrollToNode(hasText(ui(R.string.region_left_knee)))
        compose.onNode(hasText(ui(R.string.region_left_knee)) and hasAnyAncestor(isDialog()))
            .performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        compose.onNodeWithText(ui(R.string.region_left_knee)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        compose.onNodeWithContentDescription(ui(R.string.body_area_metric, ui(R.string.stiffness), ui(R.string.region_left_knee))).assertExists()
    }

    private fun bodyAreaList(): SemanticsNodeInteraction {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val title = compose.onNodeWithText(ui(R.string.add_body_area)).fetchSemanticsNode().boundsInWindow
        assertTrue("The body area sheet opens below the status bar", title.top > context.resources.displayMetrics.heightPixels * .15f)
        val scrolling = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        return compose.onNode(scrolling and hasAnyAncestor(isDialog()))
    }

    private fun assertSingleSegmentedRow(labels: List<String>, within: SemanticsMatcher? = null) {
        val baselines = labels.map { label ->
            val matcher = if (within == null) hasText(label) else hasText(label) and within
            compose.onNode(matcher, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.center.y
        }
        assertEquals("Segmented labels share a row", 1, baselines.distinct().size)
    }

    private fun openWorkoutAssistant() {
        compose.onNodeWithContentDescription(ui(R.string.named_options, ui(R.string.exercise_lat_pulldown)))
            .performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.workout_assistant)).performClick()
        compose.onNodeWithContentDescription(ui(R.string.record_speech)).assertExists()
    }

    private fun openHistoryMonthPicker() {
        compose.onNodeWithText(YearMonth.from(LocalDate.now(DemoFixtures.clock)).monthTitle()).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.select_month)).assertExists()
    }

    private fun openProgram() {
        val program = ApplicationProvider.getApplicationContext<InShapeApplication>().container.programs.programs.first()
        compose.onAllNodesWithText(ui(R.string.library)).onLast().performClick()
        compose.onNodeWithText(program.name).performScrollTo().performClick()
    }

    @Test fun finalRoutineAndExerciseLibraryPaths() {
        val arguments = InstrumentationRegistry.getArguments()
        arguments.getString("themeMode")?.let { themeSettings.set(ThemeMode.valueOf(it)) }
        val context = ApplicationProvider.getApplicationContext<Context>()
        fun demo(state: String, ready: String, check: () -> Unit) {
            val intent = Intent(context, DemoActivity::class.java).putExtra("state", state)
            ActivityScenario.launch<DemoActivity>(intent).use {
                compose.waitUntil(30_000) { compose.onAllNodesWithText(ready).fetchSemanticsNodes().isNotEmpty() }
                check()
            }
        }

        demo("LibraryRoutineExpanded", ui(R.string.start_workout)) {
            compose.onNodeWithText(quantity(R.plurals.exercise_count, 7)).assertIsDisplayed()
            compose.onNodeWithText(ui(R.string.approximate_duration, "50 min").replaceFirstChar(Char::titlecase)).assertIsDisplayed()
            captureFinalScreenshot("30-library-routine-detail")
            compose.onNodeWithContentDescription(ui(R.string.routine_options)).performClick()
            listOf(R.string.edit_details, R.string.duplicate, R.string.remove).forEach { compose.onNodeWithText(ui(it)).assertExists() }
        }
        demo("TodayStrength", ui(R.string.start_workout)) {
            compose.onNodeWithText(quantity(R.plurals.exercise_count, 7) + ", " + ui(R.string.approximate_duration, "50 min")).assertExists()
            captureFinalScreenshot("02-today-strength")
            compose.onNodeWithText(ui(R.string.library)).performClick()
            compose.onNodeWithText(quantity(R.plurals.exercise_count, 7) + ", " + ui(R.string.approximate_duration, "50 min")).assertExists()
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
            captureFinalScreenshot("31b-library-new-routine-editor")
            compose.onNodeWithContentDescription(ui(R.string.edit_details)).performClick()
            compose.onNodeWithText(ui(R.string.routine_details)).assertExists()
            captureFinalScreenshot("31c-library-new-routine-details-dialog")
        }
        // The Today artwork demos deliberately omit workouts. Use the regular November history here.
        demo("TodayWithActiveWorkout", ui(R.string.resume_workout)) {
            compose.onNodeWithText(ui(R.string.library)).performClick()
            compose.onNodeWithText(ui(R.string.exercises)).performClick()
            val recordedDate = LocalDate.of(2026, 11, 11).shortDate()
            listOf(R.string.exercise_chest_press, R.string.exercise_lat_pulldown).forEach { exercise ->
                val row = hasText(ui(exercise)) and hasText(ui(R.string.set_summary_with_load, ui(R.string.set_summary_uniform, 3, 10), ui(R.string.weight_value, "25"))) and hasText(recordedDate)
                compose.onNode(row).performScrollTo().assertIsDisplayed()
            }
            captureFinalScreenshot("34-library-exercises")
        }
    }

    private fun captureFinalScreenshot(name: String) {
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: return
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.filesDir, "verification/reference/$label").apply { mkdirs() }
        screenshot(output, name)
    }

    private fun screenshot(output: File, name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        val automation = instrumentation.uiAutomation
        automation.waitForIdle(800, 5_000)
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    @OptIn(ExperimentalGlanceRemoteViewsApi::class)
    @Test fun captureIdleWidget() = runBlocking {
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: return@runBlocking
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        val context = ApplicationProvider.getApplicationContext<Context>()
        val output = File(context.filesDir, "verification/reference/$label").apply { mkdirs() }
        val today = LocalDate.now(DemoFixtures.clock)
        val routineName = DemoFixtures.FULL_BODY
        val week = weekStart(today)
        val state = WidgetState(week, 2, 3, false, null, today = today, completedDays = setOf(week, week.plusDays(2)),
            nextRoutineTitle = routineName)
        val glance = GlanceRemoteViews()
        for (width in listOf(Dimens.widgetNarrow, Dimens.widgetWide)) {
            val views = glance.compose(context, DpSize(width, Dimens.widgetHeight)) {
                WidgetContent(state, actionStartActivity(Intent(context, MainActivity::class.java)), timer = null, time = null,
                    workoutCount = quantity(R.plurals.workout_count, state.count))
            }.remoteViews
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                val density = context.resources.displayMetrics.density
                val widthPx = (width.value * density).roundToInt()
                val heightPx = (Dimens.widgetHeight.value * density).roundToInt()
                val widget = views.apply(context, FrameLayout(context))
                widget.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(heightPx, View.MeasureSpec.EXACTLY))
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
        val arguments = InstrumentationRegistry.getArguments()
        val label = arguments.getString("captureLabel") ?: return
        val dark = arguments.getString("themeMode") == ThemeMode.Dark.name
        themeSettings.set(if (dark) ThemeMode.Dark else ThemeMode.Light)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val requested = arguments.getString("captureGroups")?.split(",")?.toSet()
        val failures = mutableListOf<String>()
        val scroll = SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

        fun scenario(group: String, state: String, ready: Int, action: () -> Unit) {
            if (requested != null && group !in requested) return
            try {
                val intent = Intent(context, DemoActivity::class.java).putExtra("state", state)
                ActivityScenario.launch<DemoActivity>(intent).use {
                    compose.waitUntil(30_000) { compose.onAllNodesWithText(ui(ready)).fetchSemanticsNodes().isNotEmpty() }
                    action()
                }
            } catch (error: AssertionError) {
                failures += "$group: ${error.message}"
            } catch (error: IllegalStateException) {
                failures += "$group: ${error.message}"
            }
        }

        fun SemanticsNodeInteraction.clickThroughScroll() {
            var parent = fetchSemanticsNode().parent
            while (parent != null && !scroll.matches(parent)) parent = parent.parent
            if (parent != null) performScrollTo()
            performClick()
        }
        fun click(id: Int) = compose.onNode(hasText(ui(id)) and hasClickAction()).clickThroughScroll()
        fun icon(id: Int, vararg values: Any) = compose.onNodeWithContentDescription(ui(id, *values)).clickThroughScroll()
        fun tab(id: Int) = compose.onAllNodes(hasText(ui(id)) and hasClickAction()).onLast().performClick()
        fun back() {
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            compose.waitForIdle()
        }
        fun capture(name: String) = captureFinalScreenshot(name)

        scenario("workout", "ActiveWorkoutPartiallyDone", R.string.exercise_done) {
            capture("10-treeni")
            if (!dark) {
                icon(R.string.named_options, ui(R.string.exercise_lat_pulldown))
                capture("10a-liikkeen-valikko")
                click(R.string.workout_assistant)
                compose.onNodeWithContentDescription(ui(R.string.record_speech)).assertExists()
                capture("10b-treeniavustaja")
            }
        }
        scenario("finish", "FinishSummary", R.string.workout_done) {
            capture("12-treenin-lopetus")
            if (!dark) {
                compose.onNode(hasText(ui(R.string.effort_right)) and hasClickAction()).assertIsSelected()
                click(R.string.fix_time)
                capture("12a-korjaa-aika")
                back()
                openFinishPainArea {
                    assertSingleSegmentedRow(listOf(ui(R.string.no), ui(R.string.yes)))
                    assertSingleSegmentedRow(listOf(ui(R.string.body_front), ui(R.string.body_back)))
                    capture("13e-finish-pain-input")
                }
                listOf(R.string.pain, R.string.stiffness).forEach { metric ->
                    val within = hasAnyAncestor(hasContentDescription(ui(R.string.body_area_metric, ui(metric), ui(R.string.region_left_knee))))
                    val labels = BodyLevel.entries.map { ui(it.label) }
                    assertSingleSegmentedRow(labels.take(2), within)
                    assertSingleSegmentedRow(labels.drop(2), within)
                    val first = compose.onNode(hasText(labels.first()) and within, useUnmergedTree = true).fetchSemanticsNode()
                    val third = compose.onNode(hasText(labels[2]) and within, useUnmergedTree = true).fetchSemanticsNode()
                    assertTrue("The moderate option starts a second row", first.boundsInRoot.center.y < third.boundsInRoot.center.y)
                }
                capture("13f-finish-pain-area-sheet")
            }
        }
        scenario("library", "TodayStrength", R.string.start_workout) {
            tab(R.string.library)
            if (dark) {
                icon(R.string.new_routine)
                click(R.string.build_routine)
                capture("36-rutiiniavustaja")
                back()
            }
            if (!dark) {
                capture("32-kirjasto-rutiinit")
                icon(R.string.new_routine)
                capture("32a-kirjaston-lisaysvalikko")
                click(R.string.new_routine)
                capture("35-uusi-rutiini")
                back()
                icon(R.string.back)
                icon(R.string.new_routine)
                click(R.string.build_routine)
                capture("36-rutiiniavustaja")
                compose.onNodeWithText(ui(R.string.place_gym)).assertDoesNotExist()
                compose.onNodeWithText(ui(R.string.place_home)).performScrollTo().assertIsDisplayed()
                capture("36a-rutiinin-poikkeava-sijainti")
                val title = compose.onNode(hasText(ui(R.string.routine_name)) and hasSetTextAction())
                compose.onNode(hasText(ui(R.string.review_routine)) and hasClickAction()).assertIsEnabled().performClick()
                title.assertIsFocused().performTextInput("Testirutiini")
                click(R.string.review_routine)
                compose.onNodeWithText(ui(R.string.save_as_routine)).assertDoesNotExist()
                click(R.string.exercise_chest_press)
                click(R.string.review_routine)
                compose.onNodeWithText(ui(R.string.save_as_routine)).assertIsEnabled()
                back()
                icon(R.string.new_routine)
                click(R.string.import_program)
                capture("37-ohjelman-tuonti")
                back()
            }
            click(R.string.exercises)
            capture("33-kirjasto-liikkeet")
            if (!dark) {
                icon(R.string.new_exercise)
                click(R.string.new_exercise)
                capture("34-uusi-liike")
                back()
                icon(R.string.new_exercise)
                click(R.string.import_equipment)
                capture("38-laitekortin-tuonti")
            }
        }
        scenario("program", "TodayStrength", R.string.start_workout) {
            openProgram()
            capture("39-ohjelma")
            if (!dark) {
                compose.onNodeWithText(ui(R.string.eight_week_plan)).assertDoesNotExist()
                click(R.string.program_more_information)
                compose.onNodeWithText(ui(R.string.eight_week_plan)).assertExists()
                click(R.string.program_more_information)
                click(R.string.use_program)
                compose.onNodeWithText(ui(R.string.place_gym)).assertDoesNotExist()
                capture("39a-ohjelman-laitevalinta")
                val app = ApplicationProvider.getApplicationContext<InShapeApplication>()
                val missing = compose.onAllNodes(hasText(ui(R.string.program_choose)) and hasClickAction())[0]
                val missingName = missing.fetchSemanticsNode().config[SemanticsProperties.Text].first().text
                compose.onNode(hasText(ui(R.string.use_program)) and hasClickAction()).assertIsEnabled().performClick()
                compose.onNode(hasText(missingName) and !hasClickAction() and hasAnyAncestor(isDialog())).assertExists()
                back()
                val slot = app.container.programs.programs.first().sessions.first().exercises.first()
                val firstSlot = compose.onNode(hasText(slot.name) and hasClickAction())
                firstSlot.clickThroughScroll()
                compose.onNode(hasText(ui(R.string.create_from_template)) and hasAnyAncestor(isDialog())).assertExists()
                click(R.string.create_from_template)
                compose.onNode(hasText(slot.name) and hasText(ui(R.string.create_from_template))).assertExists()
                back()
                compose.onNode(hasText(slot.name) and hasClickAction()).clickThroughScroll()
                compose.onNodeWithText(ui(R.string.exercise_guide)).assertExists()
                capture("39b-liikeohje")
            }
        }
        scenario("player", "MusicHome", R.string.playlists) {
            val playlist = DemoMusic.catalog.playlists.first()
            icon(R.string.play_named, playlist.name)
            compose.onNode(hasText(playlist.tracks.first().title) and hasClickAction()).clickThroughScroll()
            compose.onNodeWithText(ui(R.string.now_playing)).assertExists()
            capture("46-nyt-soi")
        }
        if (dark) {
            scenario("50", "Empty", R.string.start_workout) { capture("50-tyhja-paivakirja") }
            scenario("history", "HistoryNovember", R.string.calendar) {
                openHistoryMonthPicker()
                capture("20c-kuukausivalinta")
            }
        }
        if (!dark) {
            scenario("workout-with-music", "WorkoutWithMusic", R.string.exercise_done) {
                compose.onNodeWithContentDescription(ui(R.string.now_playing)).assertExists()
                compose.onNodeWithText(DemoMusic.catalog.playlists.first().tracks.first().title).assertDoesNotExist()
                capture("10-treeni")
                icon(R.string.now_playing)
                compose.onNodeWithContentDescription(ui(R.string.pause)).assertExists()
                back()
                capture("11-treeni-ja-musiikki")
            }
            val directScreens = listOf(
                Triple("TodayWithActiveWorkout", R.string.resume_workout, "01-tanaan-kaynnissa"),
                Triple("TodayStrength", R.string.start_workout, "02-tanaan-voimatreeni"),
                Triple("TodayMobility", R.string.start_workout, "03-tanaan-liikkuvuus"),
                Triple("TodayShort", R.string.start_workout, "04-tanaan-lyhyt"),
                Triple("FailedSave", R.string.save_failed, "05-tallennusvirhe"),
                Triple("ProgressWithHistory", R.string.values, "21-kehitys"),
                Triple("SparseHistory", R.string.progress, "22-kehitys-vahan-tietoja"),
                Triple("ProgressWithoutAttendance", R.string.progress, "23-kehitys-tyhja"),
                Triple("LibraryRoutineExpanded", R.string.start_workout, "30-rutiini"),
                Triple("ExerciseWithSetup", R.string.edit, "31-liikkeen-tiedot"),
                Triple("Empty", R.string.start_workout, "50-tyhja-paivakirja"),
                Triple("ProgramWorkout", R.string.program_workout, "42a-ohjelman-treeni")
            )
            directScreens.forEach { (state, ready, name) ->
                scenario(name.substringBefore('-'), state, ready) {
                    if (state == "ProgressWithHistory") {
                        assertSingleSegmentedRow(listOf(R.string.weeks_4, R.string.weeks_12, R.string.all).map { ui(it) })
                    }
                    capture(name)
                    if (state == "ProgramWorkout") {
                        compose.onNodeWithText(ui(R.string.place_gym)).assertDoesNotExist()
                        compose.onNode(hasText(ui(R.string.standard_workout)) and hasClickAction()).assertIsSelected()
                        click(R.string.program_mode_light)
                        compose.onNode(hasText(ui(R.string.program_mode_light)) and hasClickAction()).assertIsSelected()
                        val program = ApplicationProvider.getApplicationContext<InShapeApplication>().container.programs.programs.first()
                        icon(R.string.workout_options)
                        compose.onNodeWithText(ui(R.string.advance_to_week, 2)).performClick()
                        compose.waitUntil(10_000) {
                            compose.onAllNodesWithText(ui(R.string.program_week, 2, program.durationWeeks)).fetchSemanticsNodes().isNotEmpty()
                        }
                    }
                }
            }
            scenario("start", "TodayStrength", R.string.other_routine) {
                click(R.string.other_routine)
                capture("07-treenin-aloitusvalinnat")
            }
            scenario("settings", "SettingsConfigured", R.string.settings) {
                capture("06-asetukset")
                click(R.string.assistant)
                compose.onNodeWithText(ui(R.string.assistant_unavailable)).assertExists()
                capture("08-avustaja")
                back()
                click(R.string.export)
                assertSingleSegmentedRow(listOf(ui(R.string.export_format_pdf), ui(R.string.export_format_csv)))
                capture("09-vienti")
                click(R.string.period)
                capture("09a-vientijakso")
            }
            if (requested == null || "settings" in requested) {
                ActivityScenario.launch(HealthPrivacyActivity::class.java).use { capture("09b-tietosuoja") }
            }
            scenario("checkin", "CheckInAfter", R.string.skip) {
                assertSingleSegmentedRow(EffortLevel.entries.map { ui(it.label) })
                capture("13-voinnin-kirjaus")
                if (compose.onAllNodesWithText(ui(R.string.energy)).fetchSemanticsNodes().isEmpty()) {
                    click(R.string.energy_and_notes)
                }
                assertSingleSegmentedRow(EnergyLevel.entries.map { ui(it.label) })
                click(R.string.pick_from_list)
                val list = bodyAreaList()
                capture("13a-kehon-alueen-valinta")
                list.performScrollToNode(hasText(ui(R.string.region_right_ankle_foot)))
                val finalArea = compose.onNodeWithText(ui(R.string.region_right_ankle_foot)).assertIsDisplayed()
                assertTrue("The last body area is fully visible", finalArea.getUnclippedBoundsInRoot().bottom <= list.getUnclippedBoundsInRoot().bottom)
                capture("13b-kehon-alueet-listan-lopussa")
            }
            scenario("history", "HistoryNovember", R.string.calendar) {
                capture("20-historia")
                icon(R.string.add_past_workout)
                capture("20a-lisaa-aiempi-treeni")
                back()
                openHistoryMonthPicker()
                val monthLabels = context.resources.getStringArray(R.array.history_months)
                compose.onNodeWithText(monthLabels[11]).assertIsNotEnabled()
                capture("20c-kuukausivalinta")
                back()
                icon(R.string.ask_journal)
                capture("20b-paivakirjan-kysymykset")
                val question = compose.onNode(hasText(ui(R.string.question)) and hasSetTextAction())
                val ask = compose.onNodeWithContentDescription(ui(R.string.ask)).assertIsNotEnabled()
                question.performClick().performTextInput("2026-13-01")
                click(R.string.evidence_summary)
                compose.onNodeWithText(ui(R.string.invalid_date_range)).assertDoesNotExist()
                ask.assertIsEnabled().performClick()
                compose.onNodeWithText(ui(R.string.invalid_date_range)).assertExists()
                question.performClick().performTextReplacement("2026-11-01")
                question.performImeAction()
                compose.onNodeWithText(ui(R.string.invalid_date_range)).assertDoesNotExist()
            }
            scenario("music", "MusicHome", R.string.playlists) {
                capture("40-musiikki")
                click(R.string.songs)
                capture("42-kappaleet")
                click(R.string.playlists)
                icon(R.string.new_playlist)
                capture("42b-musiikin-lisaysvalikko")
                click(R.string.new_playlist)
                capture("43-uusi-soittolista")
                compose.onNode(hasContentDescription(ui(R.string.search_songs)) and hasSetTextAction()).assertIsDisplayed()
                back()
                icon(R.string.new_playlist)
                click(R.string.playlist_with_assistant)
                capture("44-soittolista-avustaja")
            }
            scenario("playlist", "MusicPlaylist", R.string.play) {
                capture("41-soittolista")
                icon(R.string.playlist_options)
                capture("45-soittolistan-valikko")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
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
                "36-rutiiniavustaja" -> setOf(ui(R.string.exercise_chest_press), ui(R.string.exercise_leg_curl), ui(R.string.exercise_hip_flexor_stretch))
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
                                (!centeredLineFits && (0 until layout.lineCount).any {
                                    layout.getLineLeft(it) < -1f || layout.getLineRight(it) > layout.size.width + 1f
                                })
                        }.forEach {
                            issues +=
                                "$state: clipped text: ${it.layoutInput.text.text} (${it.size}, height ${it.multiParagraph.height}, intrinsic width ${it.multiParagraph.intrinsics.maxIntrinsicWidth}, " +
                                    "line bounds ${(0 until it.lineCount).joinToString { line -> "${it.getLineLeft(line)}..${it.getLineRight(line)}" }})"
                        }
                }
            if (exerciseTitles.isNotEmpty()) {
                if (inspectedTitles != exerciseTitles) issues += "$state: missing title measurements for ${exerciseTitles - inspectedTitles}"
                geometry += "$state: ${inspectedTitles.size} ListRow titles measured"
            }
        }
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
                if (state == "Empty") {
                    compose.onNodeWithText(ui(R.string.empty_workout)).assertIsDisplayed()
                    compose.onNode(hasText(ui(R.string.start_workout)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
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
            Triple("FinishSummary", "13f-finish-pain-area-sheet", { openFinishPainArea() }),
            Triple("ActiveWorkoutPartiallyDone", "10b-treeniavustaja", { openWorkoutAssistant() }),
            Triple("TodayStrength", "39a-ohjelman-laitevalinta", {
                openProgram()
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
            Triple("HistoryNovember", "20c-kuukausivalinta", { openHistoryMonthPicker() })
        )
        extraScreens.forEach { (state, name, open) ->
            ActivityScenario.launch<DemoActivity>(Intent(context, DemoActivity::class.java).putExtra("state", state)).use { scenario ->
                compose.waitUntil(30_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
                open()
                compose.waitForIdle()
                inspectText(name)
                screenshot(output, name)
                scenario.onActivity { activity ->
                    val configuration = activity.resources.configuration
                    geometry += "$name: font=${configuration.fontScale}, widthDp=${configuration.screenWidthDp}"
                }
            }
        }
        File(output, "layout-results.txt").writeText(
            geometry.joinToString("\n") + "\n" +
                if (issues.isEmpty()) {
                    "All ${states.size + extraScreens.size} screens inspected. Text fits its bounds and the measured markers align with the first title line."
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
