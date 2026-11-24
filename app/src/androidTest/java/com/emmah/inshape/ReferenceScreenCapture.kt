package com.emmah.inshape

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.KeyEvent
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.emmah.inshape.demo.DemoActivity
import com.emmah.inshape.demo.DemoFixtures
import com.emmah.inshape.demo.DemoMusic
import com.emmah.inshape.feature.checkin.BodyLevel
import com.emmah.inshape.feature.checkin.EffortLevel
import com.emmah.inshape.feature.checkin.EnergyLevel
import com.emmah.inshape.feature.settings.label
import com.emmah.inshape.health.HealthPrivacyActivity
import com.emmah.inshape.ui.theme.ThemeMode
import com.emmah.inshape.ui.theme.ThemeSettings
import com.emmah.inshape.ui.theme.monthTitle
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

@OptIn(ExperimentalTestApi::class)
internal class ReferenceScreenCapture(private val compose: ComposeTestRule, private val themeSettings: ThemeSettings) {
    fun withDemoState(state: String, ready: SemanticsMatcher, test: (ActivityScenario<DemoActivity>) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = Intent(context, DemoActivity::class.java).putExtra("state", state)
        ActivityScenario.launch<DemoActivity>(intent).use { scenario ->
            compose.waitUntil(30_000) { compose.onAllNodes(ready).fetchSemanticsNodes().isNotEmpty() }
            test(scenario)
        }
    }

    fun openFinishPainArea(beforeArea: () -> Unit = {}) {
        compose.onNodeWithText(ui(R.string.yes)).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.pick_from_list)).performScrollTo()
        beforeArea()
        compose.onNodeWithText(ui(R.string.pick_from_list)).performClick()
        bodyAreaList().performScrollToNode(hasText(ui(R.string.region_left_knee)))
        compose.onNode(hasText(ui(R.string.region_left_knee)) and hasAnyAncestor(isDialog()))
            .performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        compose.onNodeWithText(ui(R.string.region_left_knee)).performScrollTo()
            .performSemanticsAction(SemanticsActions.OnClick) { check(it()) }
        compose.onNodeWithContentDescription(
            ui(R.string.body_area_metric, ui(R.string.stiffness), ui(R.string.region_left_knee))
        ).assertExists()
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

    fun openHistoryMonthPicker() {
        compose.onNodeWithText(YearMonth.from(LocalDate.now(DemoFixtures.clock)).monthTitle()).performScrollTo().performClick()
        compose.onNodeWithText(ui(R.string.select_month)).assertExists()
    }

    fun openProgram() {
        val program = ApplicationProvider.getApplicationContext<InShapeApplication>().container.programs.programs.first()
        compose.onAllNodesWithText(ui(R.string.library)).onLast().performClick()
        compose.onNodeWithText(program.name).performScrollTo().performClick()
    }

    fun captureFinalScreenshot(name: String, settleAfterTap: Boolean = false) {
        val label = InstrumentationRegistry.getArguments().getString("captureLabel") ?: return
        require(label.matches(Regex("[a-zA-Z0-9-]+")))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val output = File(instrumentation.targetContext.filesDir, "verification/reference/$label").apply { mkdirs() }
        screenshot(output, name, settleAfterTap)
    }

    fun screenshot(output: File, name: String, settleAfterTap: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitForIdle()
        val automation = instrumentation.uiAutomation
        if (settleAfterTap) android.os.SystemClock.sleep(1_000) else automation.waitForIdle(800, 5_000)
        val bitmap = checkNotNull(automation.takeScreenshot())
        try {
            File(output, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            bitmap.recycle()
        }
    }

    fun captureFinnishReviewScreens() {
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
                withDemoState(state, hasText(ui(ready))) { action() }
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
                    val within =
                        hasAnyAncestor(hasContentDescription(ui(R.string.body_area_metric, ui(metric), ui(R.string.region_left_knee))))
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
            val exercises = compose.onNode(hasText(ui(R.string.exercises)) and hasClickAction())
            exercises.performTouchInput { click() }.assertIsSelected()
            captureFinalScreenshot("33-kirjasto-liikkeet", settleAfterTap = true)
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
                            compose.onAllNodesWithText(
                                ui(R.string.program_week, 2, program.durationWeeks)
                            ).fetchSemanticsNodes().isNotEmpty()
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
                assertTrue(
                    "The last body area is fully visible",
                    finalArea.getUnclippedBoundsInRoot().bottom <= list.getUnclippedBoundsInRoot().bottom
                )
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
}
