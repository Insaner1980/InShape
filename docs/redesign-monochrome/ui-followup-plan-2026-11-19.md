# InShape UI follow-up plan (after-screenshots of 2026-11-19)

Audience: the coding agent (Codex) implementing changes in `C:\Dev\InShape`.

Source: a review of the 165 screenshots in `reports/screenshots-2026-11-19-pixel-9-after/` (Pixel 9, font scale 1.15, light and dark theme), taken after `ui-simplification-plan-2026-11-19.md` was implemented. Screenshots are cited in brackets by their file-name prefix, for example [13] means `13-finish-summary.png`. File paths are relative to `app/src/main/java/com/emmah/inshape/`. Line numbers are from the working tree on 2026-11-19 and can drift; search for the quoted code if a line does not match.

The simplification plan landed well. This plan is small on purpose: it fixes five visible defects and polishes a handful of places where the new layout is still unclear. Do not redesign anything that is not listed here. The owner asked for changes only where they clearly improve the app.

## 0. Ground rules

All ground rules in section 0 of `ui-simplification-plan-2026-11-19.md` still apply. The most important ones:

- Keep all artwork and all `ArtworkWash` gradients.
- Monochrome foreground; `error` only for destructive actions and errors.
- Never use `·`, `•` or ` | ` as separators in any UI text, string, content description, export or test.
- No coloured vertical bar on the left edge of a card, row or panel.
- Every colour, size, spacing value and text style comes from `ui/theme/`. Add a token if one is missing.
- Delete code that becomes unused in the same change and update every caller.
- Do not change the Room schema, the backup format or the CSV/PDF column keys.
- Do not commit, push or open a pull request. Do not run `lc` or `sc`. Do not modify anything in `reports/`.
- Check current docs for any Compose API you touch (`Slider`, `SliderDefaults`, `WindowInsets`, `navigationBarsPadding`, `OutlinedTextField` supporting text) against the versions in `gradle/libs.versions.toml`.
- After every section, `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` must pass. Update instrumented tests that reference changed strings.

## Priorities

- **P0** (section 1): visible defects. Fix all of them.
- **P1** (section 2): clarity fixes that noticeably improve everyday use.
- **P2** (section 3): small polish. Do these after P0 and P1, one at a time. Skip an item if it turns out to need more than a local change, and say so in your summary.

---

## 1. P0: visible defects

### 1.1 Music screens draw under the system navigation bar

Evidence: [41, 41f, 43a, 51c, 40b, 42b]. On the pushed Music screens the mini-player sits under the system back/home/recents buttons (the back triangle overlaps "Northline"), and the last list rows scroll under the system bar with no bottom padding.

Cause: `navigation/AppNavigation.kt` around line 340. When `inMusic` is true the `MiniPlayer` is shown, but no `NavigationBar` follows it, so nothing consumes the navigation-bar inset. Before the simplification Music was a tab and the `NavigationBar` handled the inset.

Fix:

1. In the bottom bar `Column`, when `selectedTab == null` (Music, playlist detail), apply `Modifier.navigationBarsPadding()` to the mini-player container so it sits above the system bar.
2. Make the Music lists (playlists, songs, playlist detail, add songs, edit playlist, now playing queue) add the navigation-bar inset plus the mini-player height to their bottom content padding, so the last row can scroll fully into view. Reuse one helper for this; do not repeat the calculation per screen.
3. Check the same on every other pushed screen without a bottom bar and without a fixed bottom button (Settings, Privacy, Export, exercise detail, routine detail, History workout detail).

Acceptance: on Pixel 9 with 3-button navigation and with gesture navigation, no app content or mini-player is covered by the system bar on any Music screen, and the last list row can be scrolled fully above it.

### 1.2 Today title wraps to two lines

Evidence: [50] on the installed app shows "Thursday 19 / November" on two lines at font scale 1.15. Wednesday, Thursday and Saturday with long month names will always wrap at this size.

Cause: `feature/today/TodayScreen.kt:118` uses `today.longDate()` in `ScreenHeader` next to two `IconCircleButton`s.

Fix: add `LocalDate.todayTitle()` to `ui/theme/Dates.kt` producing "Thu 19 November" (abbreviated weekday, full month, year only when not the current year) and use it for the Today header only. Keep `longDate()` for the History detail date line. Set `maxLines = 1` on the header title.

Acceptance: the Today title stays on one line for every weekday and month at font scale 1.15 on a 411 dp wide screen, and at font scale 1.3 on 411 dp it either fits or ellipsizes, never wraps.

### 1.3 Field error text inside the border

Evidence: [22b]. "Enter a workout name" is drawn inside the red outline, squeezed against its bottom edge.

Cause: `ui/components/Fields.kt:88` draws the border with `Modifier.border(...)` on the whole `TextField`, which includes the `supportingText` slot (line 92).

Fix: draw the border only around the input container, not around the supporting text. Either move the border to the container (for example via the text field's container/indicator styling) or render the error `Text` below the bordered field instead of in `supportingText`. Apply the same to the second field variant around line 161.

Acceptance: in [22b], the red border wraps only the input, and the error message sits below it with `Dimens.innerTiny` spacing.

### 1.4 Routine header text wraps

Evidence: [30, 30a, 94a]. "7 exercises, about / 50 min" wraps to two lines in a large semibold style next to the barbell artwork.

Cause: `feature/library/RoutineEditor.kt:315` uses `MaterialTheme.typography.titleLarge` for `routineSummary(...)`.

Fix: use `MaterialTheme.typography.bodyLarge` with `colors.text`, `maxLines = 1`. Keep the artwork and wash. If a description exists it stays below in `bodyMedium`, `colors.textSecondary`.

Acceptance: on [30] the summary is one line and does not touch the artwork.

### 1.5 Lone dot in Progress exercise rows

Evidence: [24]. "Warm-up bike" shows a single black dot at the end of the row. It is the sparkline with one data point.

Cause: `feature/progress/ProgressCharts.kt:245` `ExerciseSparkline` draws even when `points.size < 2`.

Fix: in `ProgressScreen.kt:221`, pass `trailing = null` when the series has fewer than two points (or return early from `ExerciseSparkline` without reserving space). Do not show a placeholder.

Acceptance: rows with zero or one data point show no trailing graphic.

---

## 2. P1: clarity

### 2.1 Text actions must look tappable

Evidence: "Other routine or empty workout" [02], "Create a routine" [05, 50], "Pick from list" [11, 13g], "Vary by set" [30e]. They render as plain body text and read as labels. "+ Add set" works because it has an icon.

Fix: every `TextAction` gets a leading icon and `FontWeight.Medium`:

- "Other routine or empty workout" (`TodayScreen.kt:132`): `Icons.Outlined.SwapHoriz` or `Icons.AutoMirrored.Outlined.List`.
- "Create a routine" (`TodayScreen.kt:130`): `Icons.Outlined.Add`.
- "Pick from list" (`feature/checkin/PainInput.kt:74`): `Icons.AutoMirrored.Outlined.List`.
- "Vary by set" (`RoutineEditor.kt:628`): `Icons.Outlined.Tune`.

If `TextAction` (`ui/components/Buttons.kt`) does not already default to medium weight with an icon slot, change the component once, not each call site. Check other `TextAction` call sites without an icon and give each a fitting icon.

Acceptance: every `TextAction` in the app has a leading icon.

### 2.2 Finish screen rhythm and hierarchy

Evidence: [13, 13a, 12b, 92a].

1. The gap above "How did it feel?" and "Anything hurt?" equals the gap between the question and its control, so questions do not group with their controls. Use `Dimens.sectionGap` (32 dp) above each question and `Dimens.itemGap` (12 dp) between question and control.
2. The questions use `titleLarge` (`FinishScreen.kt:217`, `PainInput.kt:65`) and look larger than "Full body A" on the summary card. Use `MaterialTheme.typography.titleMedium` (16 sp semibold) for both questions, in the finish screen and in the BEFORE/NEXT_DAY check-in screens.
3. Inside the expanded "Energy and notes" section, "Energy" is a `SectionHeader` (`feature/checkin/CheckInScreen.kt:178`), larger than the "Energy and notes" row that contains it. Replace it with a caption: `labelSmall`, `colors.textSecondary`, the same style as "Pain" and "Stiffness" in the area sheet [13f].

Acceptance: on [13] the stat tiles, the feeling block and the pain block read as three separate groups, and no heading inside a collapsible is larger than the collapsible's own row.

### 2.3 History: one "Edit workout" screen

Evidence: [21b, 21c, 21d]. The History ⋮ menu has Repeat workout, Fix time, Edit note and Edit sets. "Fix time" opens a screen that also edits the workout name, date, duration and note, so its title is wrong, and the note can be edited in two places.

Fix:

1. Rename the timing screen title for the correction mode from "Fix time" to "Edit workout" (`navigation/AppNavigation.kt:316`, only for `timing/{id}/correct`). The finish screen's own "Fix time" sheet keeps its name.
2. Rename the History menu item "Fix time" (`HistoryScreen.kt:481`) to "Edit workout".
3. Remove the "Edit note" menu item and its dialog (`HistoryScreen.kt:485` and the dialog around line 527). The note is edited on the Edit workout screen.
4. Final History menu: Repeat workout, Edit workout, Edit sets.

Acceptance: the workout note can be edited in exactly one place from History.

### 2.4 Replace "Completion only"

Evidence: [10i] shows "Completion only" at the end of an Up next row. The same text is a "Measure as" option in the routine targets dialog [30e].

Cause: `feature/workout/SetSummary.kt:23` and `feature/library/RoutineEditor.kt:84` (`COMPLETION_ONLY`, options at lines 545 to 557).

Fix:

1. In `SetSummary.kt`, when there are no sets, return an empty string for planned targets (Up next, routine rows show nothing at the end) and "Done" for completed history rows. If the function cannot tell planned from completed, add a parameter rather than a second function.
2. In the routine targets dialog, rename the label "Measure as" to "Log as" and the options to "Sets", "Time" and "Just done". Keep the stored values (for example `QUICK`) unchanged; only the display labels change.
3. Do not change `transfer/JournalExports.kt` keys (`COMPLETION_ONLY` stays in CSV).

Acceptance: the phrase "Completion only" no longer appears in the UI.

### 2.5 Settings grouping

Evidence: [07, 07c, 96] when Health Connect is unavailable: "Health Connect / Not available on this phone" appears directly under the Reminders section without its own header, so it reads as part of Reminders. On the installed app [50a] the Health Connect header is present. "Weekly goal" has no header at all.

Fix (`feature/settings/SettingsScreen.kt`, `health/HealthControls.kt` around lines 107 to 109 and 309):

1. Always show the "Health Connect" section header, also in the unavailable state. Under it, the single row reads "Not available on this phone" as a secondary-coloured body line (no row title repeating "Health Connect").
2. Put "Weekly goal" and "Workout reminders" under one header "Goal and reminders".

Acceptance: every row in Settings belongs to a visible section header, in both the available and unavailable Health Connect states.

### 2.6 Progress empty state grouping

Evidence: [44a]. "Pain and stiffness" is listed under the "Exercises" header.

Fix (`feature/progress/ProgressScreen.kt:199`): when there are no exercises with data, hide the "Exercises" header, and give "Pain and stiffness" its own section header "Pain and stiffness" with the collapsible below it (or show the collapsible without a header, matching the non-empty layout [23e]). Pick whichever matches the non-empty screen.

### 2.7 History month header and summary card

Evidence: [20, 20a, 44, 90b].

1. "November 2026 workouts" repeats the year that the calendar header already shows. In `HistoryScreen.kt:422` use the month name only: "November workouts" (add `YearMonth.monthName()` to `Dates.kt` if needed).
2. In the month summary card the values "3 h 50 min" and "46 min" nearly touch. Give the three columns weights based on content (for example 1f, 1.4f, 1f) or use `Arrangement.SpaceBetween` with intrinsic widths, and ensure at least `Dimens.inner` between values. Verify at font scale 1.3 on a 320 dp wide screen; if the values do not fit, stack the total time value above its label and shorten to "3 h 50 m" only in this card.

Acceptance: at font scale 1.15 on Pixel 9 there is visible space of at least 16 dp between every value in the card.

---

## 3. P2: polish

Each item is independent. Keep changes local.

1. **Today active card [01, 06, 90a]:** "Started 10:42" sits alone at the end of the top row. Move it to the end of the "4 of 8 exercises done" row (opposite ends of the same row) and remove the now-empty top row (`TodayScreen.kt:267`).
2. **Seek bar [42, 51d]:** the `Slider` in `feature/music/PlayerScreens.kt:260` shows the Material 3 end stop indicator as a second dot. Hide the stop indicator (check the current `Slider`/`SliderDefaults.Track` API for the pinned version) so only the thumb dot remains.
3. **"Add 0 songs" [41b]:** in `feature/music/PlaylistEditors.kt:131`, when the count is 0 show "Add songs" (disabled). From 1 upward keep "Add 1 song", "Add 3 songs".
4. **Routine targets dialog [30e, 30h]:**
   - Put Sets, Reps and kg in one row of three equal fields in the uniform editor.
   - The per-set remove icon (`RoutineEditor.kt:621`) is tinted `colors.error` and looks like a validation error. Use the default `colors.text` tint like every other close icon in the editors.
5. **New routine title [31b]:** after the details dialog, the entered name is not visible anywhere and the toolbar still says "New routine". Show the routine name as the toolbar title once it exists (`RoutineEditor.kt:263`), and remove the ⋮ menu from the creating state if it has no useful actions there.
6. **Library search row [34, 44c]:** the outlined search field sits next to a gray filled square star button. Make the star toggle an icon button without fill (outlined circle when off, filled `colors.ink` star when on), vertically centred on the field.
7. **Swap exercise sheet [10e]:** "All exercises" is a quiet gray header (`WorkoutScreen.kt:809`, `quiet = true`) while "Your alternatives" is a normal header. Use the same style for both.
8. **"Save changes to routine" row [10i]:** it opens a confirmation dialog, not a new screen. Remove the chevron (`WorkoutScreen.kt:302`, `showChevron = false`).
9. **Empty finish [14a]:** the user can add a note in "Energy and notes", but the primary button stays "Back to workout" (`FinishScreen.kt:528`). Verify: if a note is entered, the workout is saveable, so the button must switch to "Save workout". If saving with only a note is not allowed, hide "Energy and notes" in the empty state instead.
10. **History detail exercise rows [21, 21a]:** the equipment line ("Seat height 4, thigh pad 2, handle wide bar") and the exercise note hang below the row, detached from the chevron. Remove the equipment line from History (it lives on the exercise page). Keep the note, inside the same text column as the title and summary so the chevron is centred on the whole block.

---

## 4. Verification

1. Build and unit tests pass (ground rules).
2. Retake the affected screenshots into a new folder `reports/screenshots-<date>-pixel-9-followup/` in light and dark theme: 01, 02, 05, 07, 10e, 10i, 13, 13a, 14a, 20, 21b, 21c, 22b, 24, 30, 30e, 30h, 31b, 34, 41, 41b, 41f, 42, 43a, 44a, 50, 50a, 51c. Do not modify existing report folders.
3. Check by eye:
   - nothing is covered by the system navigation bar on Music screens (both navigation modes),
   - the Today title is one line,
   - error text sits below its field,
   - every text action has an icon,
   - no "Completion only", no lone dots, no `·`, `•` or ` | `,
   - artwork and washes unchanged.
4. Run the existing layout checks at font scale 1.3 on 411 dp and 320 dp widths for Today, active workout, finish, Settings and routine detail.
