# InShape UI simplification plan (screenshots of 2026-11-19)

Audience: the coding agent (Codex) implementing changes in `C:\Dev\InShape`.

Source: a full review of the 162 screenshots in `reports/screenshots-2026-11-19-pixel-9/` (Pixel 9, light and dark theme) and of the theme tokens in `app/src/main/java/com/emmah/inshape/ui/theme/`. Screenshots are cited in brackets by their file-name prefix, for example [10] means `10-workout-active.png` and [21a] means `21a-history-workout-check-ins.png`. File paths below are relative to `app/src/main/java/com/emmah/inshape/` unless they start with `app/`, `docs/` or `reports/`. Line numbers are from the working tree on 2026-11-19 and can drift. If a line does not match, search for the quoted string.

## Why this plan exists

The owner (the only user of this app, a gym beginner with a few sore spots) finds the app cluttered and hard to use. The review found no single big defect. The clutter comes from accumulation:

- The same thing can be reached in several places (starting a workout, routines, music).
- Almost every screen carries its own explanatory sentence.
- Menus contain near-synonyms (Do later, Skip today, Remove exercise).
- The same data is shown in different formats on different screens (at least nine date formats and three set-summary formats).
- About eight button styles and four nearly identical grays make it hard to see what is tappable and what is an input.

The goal of this plan is fewer screens, fewer controls, fewer words and one format per concept. It does not add features.

This plan builds on the earlier documents in this folder. Where this plan conflicts with an earlier one, this plan wins.

- `implementation-plan.md` (monochrome redesign, ground rules in its section 0)
- `ui-polish-plan.md` (2026-11-17)
- `artwork-implementation-plan.md` (approved artwork and gradient washes, 2026-11-18)
- `ui-review-plan-2026-11-18.md` (critical review of the 2026-11-18 screenshots)

---

## 0. Ground rules (apply to every change)

- **Keep all artwork and all gradient washes.** The owner explicitly confirmed on 2026-11-19 that the photographic artwork (barbell, mat, kettlebell, towel, stacked plates, notebook, machine images, playlist covers) and the `ArtworkWash` gradients stay. Do not remove, recolour or resize them unless a section below says so. When a section below shortens text on an artwork card, keep the artwork in place.
- App UI stays in English, sentence case.
- Foreground UI is monochrome: black, white and grays from `ui/theme/InShapeColors.kt`. The `error` token is only for destructive actions and errors.
- No rest timer. No pause button. Health Connect stays (reading context data and writing finished workouts).
- Never use `·`, `•` or ` | ` as separators in any UI text, string resource, content description, PDF/CSV export or test. Separate values with layout (opposite ends of a row, stacked lines, different weight or colour, separate pills) or, in running text, with a comma or parentheses.
- Never add a coloured vertical bar on the left edge of a card, row or panel. Express state with a pill, text colour, icon, or the background or border of the whole element.
- Every colour, size, radius, spacing value and text style comes from `ui/theme/` (`InShapeColors`, `Dimens`, `InShapeShapes`, `Type.kt`). If a value you need does not exist, add a token there. Do not hardcode a `dp`, `sp` or `Color` in a feature file.
- One source of truth per concept. Before writing new code, search for existing logic. Delete code, strings, drawables and tests that become unused in the same change. When you move or rename something, update every caller.
- Do not change the Room schema or the backup format in this plan. Where a UI field is removed, keep the database column and keep backup import and export tolerant of it. Section 9.4 is the one place where existing data is rewritten, and it only moves text between two existing columns.
- Do not commit, push or open a pull request. Do not run the `lc` or `sc` scripts. Do not modify, move or delete anything in `reports/`.
- Before using any API named here (`ModalBottomSheet`, `SegmentedButton`, `Modifier.semantics { customActions = ... }`, `CustomAccessibilityAction`, `Slider`, `SliderDefaults`), check the current official documentation for the versions pinned in `gradle/libs.versions.toml`. Do not assume signatures from memory.
- After every section: `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` must pass. Update instrumented tests in `app/src/androidTest/` that reference changed strings or removed screens. Do not restore test files that are deleted in the current working tree.

## Priorities

- **P1**: structure and the in-gym workflow (sections 1 to 3). These decide whether the app feels simple.
- **P2**: system-level tokens and components (sections 4 to 6). One change fixes many screens.
- **P3**: screen-specific simplification (sections 7 to 11).
- **P4**: copy (section 12).

Implement in section order. Each section has acceptance criteria.

---

## 1. P1: navigation and structure

### 1.1 Three bottom tabs: Today, History, Library

Evidence: the bottom bar has four tabs [01, 20, 31, 40]. History, Library and Music each have their own sub-tabs, so the app has about nine top-level views. Music takes a quarter of the bottom bar although it is a side feature.

Change:

1. In `navigation/AppNavigation.kt`, change `mainTabs` (line 108) to `listOf(Destination.Today, Destination.History, Destination.Library)`.
2. Music stays a destination but is no longer a tab. It becomes a pushed screen with a back arrow, reached from:
   - a new music icon button in the Today header, to the left of the settings gear (same `IconCircleButton` style as the gear [01]),
   - the existing music icon in the workout top bar [10],
   - tapping the mini-player anywhere it is shown.
3. Inside the Music screen keep the Playlists and Songs sub-tabs and all existing music features. Only the entry point changes.
4. The mini-player stays visible above the bottom bar on all three tabs while something is playing or paused, as it does now on the Music tab [51].
5. Update `selectedTab` logic (lines 135 to 139) so music routes no longer select a tab. Update the `MainActivity.OPEN_MUSIC` handling (line 311) to navigate to the pushed Music screen.
6. Update the widget, notification and any deep link that opens Music.

Acceptance:

- The bottom bar shows exactly three items in light and dark theme.
- Music can be opened from Today, from the workout screen and from the mini-player, and the back arrow returns to where it was opened from.
- Playback continues across navigation as before.

### 1.2 Routines live only in Library

Evidence: Today shows a Routines section with "See all" while a workout is active or when no next routine exists [01, 01b, 05], and Library shows the same routines [31].

Change: remove the Routines section from `feature/today/TodayScreen.kt` in every state. Choosing a different routine is handled by the start sheet in section 7.2.

Acceptance: Today never shows a routine list. Library is the only list of routines.

### 1.3 Header create actions become icon buttons

Evidence: every tab root has a large outlined pill next to the title: "+ Past workout" [20], "+ New routine" [31], "+ New exercise" [34], "+ New playlist" [40]. They compete with the page title and make the header about 140 dp tall before the sub-tabs start.

Change:

1. Replace each of these `OutlineButton`s with an `IconCircleButton` (`ui/components/Buttons.kt:124`) showing `Icons.Outlined.Add`, aligned to the end of the title row and vertically centred on the title.
2. Content descriptions: "Add past workout", "New routine", "New exercise", "New playlist". In Library the icon acts on the selected sub-tab, as the pill does now.
3. Where a list has an empty state, keep the existing in-content create button in the empty state [44b, 51].
4. The Music screen header currently has both the pill and a ⋮ menu [51a]. Keep the ⋮ menu (Choose folder, Refresh) and replace the pill with the icon button.

Acceptance: no tab root or Music header contains an outlined text pill. The title row is no taller than `Dimens.touch` plus the title line height.

---

## 2. P1: the active workout screen

The core of this screen is good and must stay as it is: the set table with large kg and reps fields, completed rows on a gray fill and the active row outlined, the "Last time" card and the equipment chips [10]. The changes below are around it.

### 2.1 Top bar: visible clock, counter, one Finish action, no overflow menu

Evidence: [10] shows back arrow, a small gray "32:15", music icon, an outlined "Finish" pill and a ⋮ menu. The 2026-11-14 decision was a clearly visible running clock. The empty workout [14] shows the clock differently, as a large 00:00 card.

Change (in `feature/workout/WorkoutScreen.kt`):

1. Top bar layout from start to end: back arrow, clock, exercise counter, flexible space, music icon, "Finish" text action.
2. Clock: `MaterialTheme.typography.titleLarge` with tabular numbers (`fontFeatureSettings = "tnum"`), colour `colors.text` (not secondary). Add `InShapeType.workoutClock` in `Type.kt` for this.
3. Exercise counter: "4 / 8" (done exercises over planned exercises) in `bodyMedium`, `colors.textSecondary`, directly after the clock with `Dimens.innerSmall` gap. Hide it when the workout has no exercises.
4. "Finish" becomes a `TextAction` (not an outlined pill). It opens the finish screen exactly as the current button does.
5. Remove the top-bar ⋮ menu. Its items move to section 2.4.
6. Remove the large 00:00 clock card from the empty workout state [14]. The empty state uses the same top bar as every workout, and the body shows only "No exercises yet" and the primary "Add exercise" button.

Acceptance: the clock is the same style in every workout state, is readable at arm's length (text colour, 20 sp, semibold) and there is exactly one ⋮ on the workout screen (the exercise menu, section 2.3).

### 2.2 One primary action that changes with context

Evidence: three "done" concepts: the Done column [10], the "Exercise done" bottom button and "Finish" in the top bar. The final action has less weight than the per-exercise one.

Change: the bottom `PrimaryButton` label (currently set around `WorkoutScreen.kt:367`) is:

- "Exercise done" while the current exercise exists and other exercises remain in Up next,
- "Finish workout" when the current exercise is the last undone one, or when nothing remains in Up next; tapping it marks the current exercise done (if any) and opens the finish screen,
- "Add exercise" when the workout is empty,
- "Save workout" for past entries (unchanged).

Rename the set table column header "Done" to an empty header (the check circles are self-explanatory) and give the header cell a content description "Set done" for TalkBack.

Acceptance: on the last exercise the bottom button reads "Finish workout". The word "Done" appears at most once on the screen (the "Done" section header of finished exercises, section 2.5).

### 2.3 Exercise menu: four items

Evidence: the exercise ⋮ menu has six items [10c]: Exercise details, Add note, Swap exercise, Do later, Skip today, Remove exercise. Three of them mean roughly "not now".

Change (around `WorkoutScreen.kt:687`):

1. Merge "Do later" and "Skip today" into one item "Skip for now". Behaviour: the exercise moves to the end of Up next. If it is still undone when the workout is finished, it is recorded as skipped exactly as "Skip today" records it today. Check `data/JournalRepository.kt` for how both actions are stored and keep one code path. Delete the other.
2. Remove "Exercise details" from the menu. Tapping the exercise name opens the details instead (make the title row clickable with a ripple and the content description "Exercise details").
3. Final menu, in this order: Add note, Swap exercise, Skip for now, Remove exercise (destructive, `colors.error`, divider above it as today).

Acceptance: the menu has four items. Tapping the exercise name opens the exercise detail screen.

### 2.4 Workout actions move to the end of the plan list

The items from the removed top-bar menu [10h] move into the scroll content, after the "Done" section, as plain list rows separated from the list above by `Dimens.sectionGap`:

- "+ Add exercise" (`TextAction` with the add icon, same style as "+ Add set"),
- "Add check-in" (`ListRow` with chevron; opens the BEFORE check-in as today),
- "Save changes to routine" (`ListRow`; only when the workout was started from a routine and the plan differs),
- "Discard workout" (`TextAction` in `colors.error` with the delete icon, same as "Delete workout" in History [21f]).

Keep the confirmation dialogs [10i, 10j] but shorten them (section 12).

Acceptance: every action from the old top-bar menu is reachable by scrolling to the end of the workout screen.

### 2.5 Plan list and bottom area

1. Keep "Up next" above "Done" [10f]. Make the Up next rows show the planned target in the single set-summary format from section 5.3, for example "3 × 10, 25 kg" instead of "3 sets".
2. The mini-player sits above the bottom button [43]. Remove the divider line between the scroll content and the mini-player, and use `Dimens.innerSmall` between the mini-player and the button, so the bottom area reads as one block.

Acceptance: Up next rows and History rows use the same set-summary text.

---

## 3. P1: finishing a workout and check-ins

### 3.1 One finish screen

Evidence: the current flow is Finish → "Workout done" summary [13] → "Save workout" → workout detail with the AFTER check-in pushed on top [12]. `navigation/AppNavigation.kt:611` (`showFinishedWorkout`) navigates to `detail/$id` and then `checkin/$id/AFTER`. The summary also has "Share to Health Connect: Off" [13], which is a setting, not a per-workout decision.

Change:

1. `feature/workout/FinishScreen.kt` becomes the only finish screen. Order of content:
   1. check icon and "Workout done" headline (keep),
   2. the artwork summary card with routine name, date, duration and time range (keep, including the artwork and wash),
   3. the two stat tiles (exercises, sets) (keep),
   4. **"How did it feel?"** segmented control: Easy, Just right, Hard (moved from the check-in screen),
   5. **"Anything hurt?"** segmented control: No, Yes. Yes expands the compact pain input from section 3.2 inline,
   6. "Energy and notes" collapsible (collapsed by default) with Energy (Low, OK, Good) and the workout note field. This replaces both the check-in note and the separate "Add note" row,
   7. "Fix time" `ListRow` with chevron (keep, opens the existing sheet [13b]).
2. Remove the "Share to Health Connect" row from the finish screen. Sharing follows the setting in Settings. If sharing is on, show a one-line caption under the stat tiles: "Shared to Health Connect after saving". If it is off or unavailable, show nothing.
3. "Save workout" saves the workout and the AFTER check-in in one action (the check-in only when at least one of feeling, pain, energy or note was set), then navigates to `detail/$id`. Remove the `navigate("checkin/$id/AFTER")` call from `showFinishedWorkout`.
4. Reuse the existing check-in composables from `feature/checkin/` instead of copying them. Move shared pieces into a file both screens can import if needed.
5. The empty finish state [14a] stays as it is, with copy shortened in section 12.

Acceptance:

- After tapping "Save workout", the user lands on the workout detail and no check-in screen opens.
- The AFTER check-in saved from the finish screen appears in History exactly as one saved from the old check-in screen [21a].
- The finish screen fits the summary card, stat tiles, feeling and "Anything hurt?" on one Pixel 9 screen without scrolling when "Anything hurt?" is No.

### 3.2 Compact pain input

Evidence: one check-in currently shows Easy/Just right/Hard, No/Yes, Front/Back, the body map, a "Choose area" dropdown that duplicates the body map, a permanent explanation ("Tap the body to add an area. A filled area means pain. An outline means stiffness only." in `feature/checkin/BodyMap.kt:142`), and for every area a large card with two rows of four unboxed text options [11, 12, 12a, 12b].

Change (in `feature/checkin/BodyMap.kt` and `CheckInScreen.kt`, used by both the finish screen and the BEFORE/NEXT_DAY check-in screen):

1. Remove the explanation text at line 142.
2. Replace the "Add body area" `SelectField` (line 147) with a small `TextAction` "Pick from list" placed under the body map. It opens the existing area picker dialog [11b]. This keeps a non-map path for small areas and for TalkBack.
3. Front/Back: use the same segmented control component as Easy/Just right/Hard (section 6.3), full width, placed above the body map, not a right-aligned pair of small buttons.
4. Tapping a body area adds it with Pain = Mild and Stiffness = None and shows it as a compact row under the map, not a large card:
   - left: area name (`bodyLarge`),
   - below it: the summary in `bodyMedium`, `colors.textSecondary`, for example "Mild pain" or "Mild pain, medium stiffness" (format in section 5.4),
   - end: a close icon button to remove the area.
5. Tapping the row opens a `ModalBottomSheet` with the area name as title and two segmented controls: Pain (None, Mild, Medium, Strong) and Stiffness (None, Mild, Medium, Strong), then a "Done" primary button. Use the shared segmented control so the options look tappable [12a shows them as plain text today].
6. Keep the body map rendering rule (filled = pain, outline = stiffness only). Do not explain it in text.
7. The "Remove 2 areas?" confirmation [12d] is only needed when the user switches "Anything hurt?" from Yes to No with areas added. Shorten its text (section 12).
8. The data model, the 0 to 3 levels and `CheckInLevels.kt` stay unchanged.

Acceptance: with two areas added, the pain section is no taller than the body map plus two rows. No dropdown and no explanation text is visible.

### 3.3 BEFORE and NEXT_DAY check-ins

These remain optional and are opened only from "Add check-in" (section 2.4) and from the History detail [21h]. Their screen uses the same compact pain input. The NEXT_DAY screen title "How do you feel today?" stays. The BEFORE screen title "How do you feel?" stays.

### 3.4 Check-ins in History

Evidence: [21a] shows "Observed 11 Nov 2026, 10:00" and chips "Pain medium", "Stiffness strong", "Energy OK".

Change (`feature/history/HistoryScreen.kt:635` to 643):

- Row title: "Before workout", "After workout" or "Next day" (keep).
- Row subtitle: only the time, for example "10:00", or "Thu 12 Nov, 09:30" when the check-in date differs from the workout date (use `dateTime()` from section 5.1). Remove the word "Observed" and the year.
- After workout row: feeling ("Just right") as a chip instead of a separate text line.
- Chips use the formats from section 5.4: "Medium pain", "Strong stiffness", "OK energy".
- Apply the same wording in `transfer/PdfExporter.kt:88`, which also prints "Observed …" with a raw instant and zone id.

---

## 4. P2: tokens (Type.kt, InShapeColors.kt, Dimens.kt)

### 4.1 Type scale

Evidence (`ui/theme/Type.kt`): nine sizes (56, 40, 28, 20, 16, 15, 14, 13, 12) and duplicate styles (`headlineLarge` = `headlineMedium`, `displayMedium` = `displaySmall` apart from `tnum`). Body is 15 sp and titles are 16 sp, so list rows look almost as large as section headers [01: "Routines" versus "Full body A"].

Target scale (seven sizes, three weights):

- **Hero number** (finish duration, progress value): `displayLarge` 56/60 and `displaySmall` 40/44, Medium, `tnum`.
- **Page title**: `headlineLarge` 28/34, SemiBold.
- **Section header, toolbar title, workout clock**: `titleLarge` 20/26, SemiBold.
- **List title, body, button**: `bodyLarge` 16/22 Regular; `titleMedium` and `labelLarge` 16/22 SemiBold.
- **Secondary text, captions, small labels**: `bodyMedium`, `bodySmall`, `labelMedium`, `labelSmall`, all 13/18, Regular or Medium.
- **Bottom navigation**: `InShapeType.nav` and `navSelected` 12/16, Regular and SemiBold.

Steps:

1. Change `bodyLarge` from 15/22 to 16/22. Change `labelLarge` from 15/20 to 16/22. Change `labelMedium` from 14/20 to 13/18 Medium.
2. Make the duplicate slots reference one definition instead of repeating the call: `headlineMedium = headlineLarge`, `headlineSmall = titleLarge`, `titleSmall = titleMedium`, `displayMedium = displaySmall`. Then search all usages of `headlineMedium`, `headlineSmall`, `titleSmall`, `displayMedium` and `labelMedium` and replace them with the canonical slot so each role uses one slot.
3. Section headers (`SectionHeader` in `ui/components/`) must use `titleLarge`. List row titles must use `bodyLarge`. Verify on [01] that "Routines"-style headers now read clearly larger than rows.
4. Small table headers and field captions (Set, kg, Reps, Measure, Machine settings, Links) use `labelSmall` 13 sp in `colors.textSecondary`, not `textTertiary`, so they stay readable.
5. Check every screen at font scale 1.3 (the `largeText` code paths) for clipping after the 15 → 16 sp change.

Acceptance: `Type.kt` defines each size once. A grep for `fontSize =` outside `ui/theme/` returns nothing.

### 4.2 Grays: separate inputs from information

Evidence: fields, set inputs, the "Last time" card, chips, search fields, segmented tracks and stat tiles are all the same gray rounded rectangle [10, 30e, 33, 33c]. There are four near-identical grays: `surface` F4F4F4, `surfaceStrong` EBEBEB, `divider` E3E3E3 and `inactiveMark` E0E0E0.

Change:

1. Add tokens to `InShapeColors`:
   - `fieldBorder`: light `0xFFC8C8C8`, dark `0xFF3D3D3D` (1 dp border of text inputs, at least 1.6:1 against the background is enough because the label also identifies the field),
   - `markRing`: light `0xFF949494`, dark `0xFF6B6B6B` (rings of not-done week days, at least 3:1 against the background).
2. Text input fields (`ui/components/Fields.kt`: `TextField`, `SelectField`, search fields) use `colors.background` as fill, a `Dimens.line` border in `fieldBorder`, and a `Dimens.ring` border in `colors.ink` when focused. Error state keeps the `error` border.
3. **Exception:** the set table fields in the workout screen and in History "Edit sets" [10, 21e] keep their current look (gray fill for completed rows, ink outline for the active row). That contrast is the clearest state signal in the app.
4. Information containers ("Last time" card, stat tiles, area rows, chips, segmented-control track) keep `colors.surface`.
5. Remove `inactiveMark`. Audit `surfaceStrong`: where it is only used as a slightly different gray container, replace it with `surface`. Keep it only where it marks a pressed or selected state. Delete the token if nothing remains.

Acceptance: on [33] and [30e] the editable fields have a white (dark theme: black) fill with a thin border, and read clearly differently from the "Last time" card on [10].

### 4.3 Dimens

1. Remove values that become unused after this plan (for example the 00:00 card sizes from section 2.1). Search each removed screen element's `Dimens` usages before deleting.
2. Add `Dimens.headerAction` if the icon header buttons in section 1.3 need a size different from `Dimens.touch`. Otherwise reuse `touch`.

---

## 5. P2: one format per concept

Create the helpers once and replace every call site. Delete the old free-form helpers afterwards.

### 5.1 Dates and times

Evidence: at least nine formats: "Friday 13 November" [01], "Fri 13 Nov 2026" [22], "Wed 11 Nov" [10], "Wednesday 11 November 2026" [21], "11 Nov 2026, 10:00" [21a], "Wed 11 Nov 2026, 10:00" [21c], "2026-11-19" [50], "24 August" [23], "Week of 24 Aug" [23a]. Call sites pass raw patterns to `displayDate(pattern)` and `journalDateLabel(pattern)` in `ui/theme/Dates.kt` from about 30 places (Today, History, Progress, Library, Workout, Finish, TimingEditor, Export, TimePickers, Widget).

Change `ui/theme/Dates.kt` to expose exactly these functions and use nothing else in feature code:

- `LocalDate.longDate()`: "Friday 13 November". Used only for the Today title and the History detail date line. Append the year when it is not the current year.
- `LocalDate.shortDate()`: "Fri 13 Nov". Default everywhere a date labels a workout, check-in or record. Append " 2025" style year only when not the current year.
- `LocalDate.axisDate()`: "13 Nov". Chart axes and chart value lists only.
- `YearMonth.monthTitle()`: "November 2026". Calendar header and month pickers.
- `dateTime(instant, zone, uses24Hours)`: "Fri 13 Nov, 10:00". Same year rule.
- Time only: keep the existing `time24` / `time12` helpers in `ElapsedTime.kt`.

Then:

1. Replace every `displayDate("…")`, `journalDateLabel("…")` and `DateTimeFormatter.ofPattern` in feature code with these functions. The list from `grep -rn "displayDate(\|journalDateLabel(\|ofPattern("` on 2026-11-19 is: `feature/history/HistoryScreen.kt` (214, 264, 313, 379, 417, 428, 498), `feature/library/ExerciseScreen.kt` (407, 421), `feature/library/LibraryScreen.kt` (308), `feature/progress/ProgressCharts.kt` (152, 158, 165, 176, 260, 273), `feature/progress/ProgressScreen.kt` (352, 405, 418, 480), `feature/today/TodayScreen.kt` (111, 146, 260, 314), `feature/workout/FinishDraft.kt` (62), `feature/workout/FinishScreen.kt` (247, 357), `feature/workout/WorkoutScreen.kt` (473, 570), `feature/workout/WorkoutTimingEditor.kt` (182, 397), `transfer/ExportScreen.kt` (130, 174), `ui/components/TimePickers.kt` (143), `widget/WidgetState.kt` (32). `data/RecordedTiming.kt:19` compares strings, not display text; leave it unless it becomes simpler with the helpers.
2. Remove the `pattern` parameters from `displayDate` and `journalDateLabel`, or delete those functions if nothing uses them any more.
3. Remove the ISO date from the Health context text (`health/AndroidHealthGateway.kt:148`, "…, local midnight to …"). See section 7.4.
4. "Week of 24 Aug" in weekly counts [23a] becomes "Week of 24 Aug" via `axisDate()` (already correct wording, keep it consistent). The chart start label "24 August" becomes "24 Aug".

Acceptance: `grep -rn "ofPattern(" app/src/main/java` only matches `ui/theme/`. A spot check of [01, 10, 20a, 21, 21a, 21c, 22, 23, 50] shows only the formats above.

### 5.2 Durations

Keep the current duration format ("52 min", "1 h 3 min", "3 h 50 min"). Make sure one helper (in `ui/theme/ElapsedTime.kt`) produces it everywhere, including the History month summary [20] and export.

Empty month summary [44]: show "0 workouts", "0 min" and, for the average, a dash "–" instead of "Not recorded" (which wraps to two lines today).

### 5.3 Set and target summaries

Evidence: "3 sets of 10, 25 kg" (routine [30]), "3 sets, 10 reps, 25 kg" (history and workout [10f, 21]), "3 sets" (Up next [10]), "Done, 5 min" [10f], "Done, no weight recorded" [24], "5 min planned" [30].

Add one formatter (for example `feature/workout/SetSummary.kt` or next to `ObservationText.kt` in `ui/theme/` if it is purely presentational) used by Workout, History, Routine, Library, Progress, Export (CSV/PDF) and the widget:

- Weight sets, all equal: "3 × 10, 25 kg".
- Weight sets, unequal: "3 sets, best 27.5 kg × 10".
- Bodyweight: "3 × 12".
- Assisted: "3 × 8, 20 kg help".
- Machine level: "3 × 10, level 4".
- Time: "5 min".
- Planned (routine, Up next): the same formats, no "planned" suffix. The context (Routine, Up next) already says it is a plan.
- Done without values: "Done".

Acceptance: the same exercise shows the same summary text in the routine, in Up next, in the Done list and in History.

### 5.4 Observation text

`ui/theme/ObservationText.kt` produces "Pain mild", "Pain, mild" and "Mild" in different places [12a, 21a, 23f]. Use one form everywhere, adjective first, lowercase second word:

- "Mild pain", "Medium pain", "Strong pain",
- "Mild stiffness", "Medium stiffness", "Strong stiffness",
- "Low energy", "OK energy", "Good energy",
- combined in one line: "Mild pain, medium stiffness".

---

## 6. P2: components

### 6.1 Four button roles

Evidence: about eight button looks: black full width, outlined header pill, outlined full width, small outlined toolbar pill (Finish, Edit, Save), gray segmented, text with plus, red text with trash, round check.

Keep exactly these roles in `ui/components/Buttons.kt` and remove any other variant that becomes unused:

1. `PrimaryButton`: black full width (white in dark theme). At most one per screen. Fixed at the bottom where it is today.
2. `OutlineButton`: secondary full-width or content-width action inside content (for example "Shuffle" [41], "Create exercise" [10k], empty-state create buttons).
3. `TextAction`: inline actions ("+ Add set", "+ Add exercise", "See chart", "Pick from list", toolbar "Finish", "Fix time" links). Destructive variant uses `colors.error` and the delete icon.
4. `IconCircleButton`: header icon actions (settings, music, add, favourite).

Specific changes:

- Toolbar "Edit" on exercise detail [32] and "Save" on the past-workout editor [22c] become `TextAction`s.
- "Start empty workout" on Today is removed (section 7.2).
- "Save routine" in the new routine editor is removed (section 9.1).
- "Repeat workout" on History detail is no longer a primary bottom button (section 8.2).

### 6.2 One save model

Evidence: the routine detail autosaves ("All changes saved" [30f], `RoutineEditor.kt:362`), while the new routine [31b], exercise editor [33], playlist editor [41c] and add songs [41b] require a save button.

Rule: **editing an existing item autosaves** (routine, routine targets, exercise, playlist order, playlist name). **Creating a new item** has one primary "Create …" button at the bottom ("Create routine", "Create exercise", "Create playlist") that is disabled until the name is valid, and after creating, the screen switches to the autosaving edit state of the new item.

- Remove the "All changes saved" text; autosave is now the norm everywhere and needs no label. Show a snackbar only on failure (the existing error snackbar style [06]).
- Remove "Save exercise" from the edit state of `ExerciseEditor.kt` and "Save" from `PlaylistEditors.kt` edit and reorder screens. Back navigation keeps the changes.
- "Add songs" [41b] keeps an explicit "Add 3 songs" primary button (it is a selection action, not an edit), with the count in the label and disabled at zero.

Acceptance: no edit screen for an existing item has a save button, and no create screen has both a save and a start button.

### 6.3 One segmented control

Use one segmented control component (in `ui/components/SelectionControls.kt`) for: Easy/Just right/Hard, No/Yes, Front/Back, Pain and Stiffness levels, Energy, 4 weeks/12 weeks/All, Before/After/Next day, PDF/CSV. Today Front/Back [11] and the pain levels [12a] use different looks. Keep the existing stacked layout for font scale 1.3 (line 153).

### 6.4 Week day marks

Evidence: not-done days are filled `#E0E0E0` circles (about 1.3:1 against white) that look like disabled buttons [02].

Change in Today and the widget: not-done day = empty circle with a `Dimens.line` ring in `markRing`; today = `Dimens.ring` ring in `colors.ink` (keep); done = filled `colors.ink` with check (keep). Dark theme mirrors this with the dark tokens.

### 6.5 Detail screen header pattern

Evidence: routine detail puts the name in the toolbar [30], workout detail and exercise detail put a 28 sp title under an empty toolbar [21, 32], playlist detail shows "Playlist" in the toolbar and the real name on the cover [41].

Rule:

- **Content detail screens** (workout detail, exercise detail, routine detail, playlist detail): toolbar has only back and actions, no title. The first content element is the item name in `headlineLarge`, except for playlists where the name stays on the cover artwork and the toolbar title is empty.
- **Form and system screens** (Settings, Export, Edit exercise, New exercise, New routine, New playlist, Add songs, Edit playlist, Now playing, Privacy): toolbar title, no large title.

Routine detail [30]: move "Full body A" from the toolbar to a large title. Under it, keep the artwork card but shorten its text to one line: "7 exercises, about 50 min".

---

## 7. P3: Today

### 7.1 Header

Evidence: "Hi Emma" (40 sp) and the date take about 20 % of the screen and carry no information [01]. The empty state uses "Today" instead [05], so the header is inconsistent.

Change (`feature/today/TodayScreen.kt:108` to 111):

1. Title: the date via `longDate()`, for example "Friday 13 November", in `headlineLarge`. Remove the greeting and the separate date line.
2. End of the title row: music icon button (section 1.1), then settings gear.
3. The Name setting is only used for the greeting (`SettingsScreen.kt:89` and 116, `TodayScreen.kt:108`). Remove the Name row from Settings and the name dialog [07a]. Keep the stored preference field and backup compatibility (ground rules), but delete UI code that reads or writes it.

### 7.2 Start card and start sheet

Evidence: "Next up" card plus a full-width "Start empty workout" button [02], four stacked text lines on the card.

Change:

1. Keep the "Next up" card with its artwork and wash. Text inside: "Next up" label, routine name, then one line "7 exercises, about 50 min". Primary button "Start workout" (keep).
2. Replace the "Start empty workout" button with a `TextAction` under the card: "Other routine or empty workout". It opens a `ModalBottomSheet` titled "Start a workout" listing every routine (name and the one-line summary) and, last, "Empty workout". Tapping an item starts it.
3. Empty state (no routines) [05]: keep the "Start a workout" card with artwork, remove the sentence "Pick a routine below or add exercises as you go.", and keep "Start". Under it, a `TextAction` "Create a routine" that opens the new routine screen.
4. Active workout card [01]: keep the dark card with artwork, the clock and the segmented progress bar. Remove the "In progress" label (the running clock says it). Keep "Started 10:42" at the end of the top row. Keep "4 of 8 exercises done". Keep "Resume workout".

### 7.3 Below the card

1. "This week" with the week marks from section 6.4 and "2 of 3" at the end. When the weekly goal is off, show "2 workouts" (keep current behaviour [05]).
2. "Last workout" row (currently only in [01b]): show it in every state where a previous workout exists, as one `ListRow`: title = routine name, subtitle = `shortDate()`, end = duration. Tapping opens the workout detail.
3. Remove the Routines section (section 1.2).

### 7.4 Health context

Evidence: [50] shows "Health context", "72 steps", "Steps today", "2026-11-19, local midnight to 15:13", "Source:" (empty).

Change (`health/HealthControls.kt:285` and the producing code in `health/AndroidHealthGateway.kt:148`):

1. Remove the section header "Health context".
2. Show the available values as at most two compact `KeyValueRow`s inside one surface container at the end of Today: "Steps today" with "72", "Sleep last night" with "7 h 10 min", "Heart rate" with "64 bpm, 14:20". Only rows with data appear. If nothing is available or Health Connect is off, show nothing.
3. Remove the period text and the "Source:" line from the UI. If the source name is needed for privacy reasons, show it only on the Privacy screen.

---

## 8. P3: History

### 8.1 Calendar tab

1. Header: title "History" with the add icon (section 1.3).
2. Month summary card: keep the wash. Values per section 5.2.
3. Workout list rows [20a]: title = routine name, subtitle = `shortDate()` only (drop ", 7 exercises"), end = duration.

### 8.2 Workout detail

Evidence: [21, 21a, 21b, 21f]. "Repeat workout" is the large sticky primary button on a screen that is mostly read.

Change (`feature/history/HistoryScreen.kt`):

1. Remove the sticky "Repeat workout" bottom button. Add "Repeat workout" as the first item in the ⋮ menu, before Fix time, Edit note and Edit sets [21b].
2. The date line under the title uses `longDate()` (year only if not current).
3. Exercise rows use the set-summary format (section 5.3).
4. The exercise equipment line "Seat height 4, Thigh pad 2, Handle Wide bar, Backrest None" [21a]: omit settings whose value is empty, "None" or "0", and format as "Seat height 4, thigh pad 2, handle wide bar".
5. Check-ins per section 3.4.
6. "Next day: Add" row [21f]: title "Next day check-in", no subtitle, chevron.
7. Keep "Delete workout" as the destructive text action at the end.

### 8.3 Add past workout: one flow

Evidence: dialog with date and name [22] → editor with a banner "Demo workout / Started Fri 13 Nov / Review the timing before saving." [22c] → "Save" → a separate "Review workout" form with timing, duration, note and "Check-ins are optional…" [22d] → "Save workout".

Change:

1. Keep the dialog (date, name) [22]. Rename its confirm button from "Add exercises" to "Continue".
2. In the editor, remove the banner. Show the workout name as the large title and the date as subtitle.
3. Add a "Duration" row in the editor (a `KeyValueRow` that opens a minutes input; empty means unknown) and a "Note" field after the exercise list.
4. The bottom primary button "Save workout" saves everything directly. Remove the step that opens `timing/{id}/…` for new past workouts.
5. The toolbar "Save" pill is removed (one primary action).
6. The timing editor stays only for correcting an existing workout (from History ⋮ "Fix time") and for "Fix time" on the finish screen. Rename its toolbar title from "Review workout" (`navigation/AppNavigation.kt:329`) to "Fix time".

Acceptance: adding a past workout takes the dialog plus one screen.

### 8.4 Progress tab

Evidence: [23 to 24]. The same record is shown four times ("+7.5 kg" badge, "27.5 kg / was 20 kg on 25 Sep", "13 Nov: 27.5 kg", "New record in this period / 13 Nov, Heaviest weight / Previous best: 25 kg on 30 Oct"). The Measure dropdown has five analytic modes, two of which need an extra "Compare at (kg)" input ("Reps at a set weight", "Weight for a rep target") [23c, 23h]. The pain chart is a dot plot of categorical levels with a legend [23f].

Change (`feature/progress/ProgressScreen.kt`, `ProgressCharts.kt`, `ProgressCalculator.kt`):

1. **Workouts per week:** keep the card with the stacked-plates artwork and the average, keep the bar chart and the goal line. Change the axis labels to `axisDate()` and "This week". Keep "Weekly counts" collapsible.
2. **Exercise progress:** the metric follows the exercise's measurement basis automatically. Remove the Measure dropdown.
   - weight → heaviest weight,
   - bodyweight → most reps,
   - assisted → least help (lower is better),
   - machine level → highest level,
   - time → longest time.
   Remove `REPS_AT_LOAD` ("Reps at a set weight"), the rep-target mode, "Number of sets" and the "Compare at (kg)" input. Delete the calculator code, enum entries and strings that become unused.
3. Exercise header: exercise picker row (name with chevron, keep) and the change badge "+7.5 kg" at the end (keep).
4. Below the header show the current value in `displaySmall` ("27.5 kg") and one secondary line: "Best so far, up from 20 kg on 25 Sep". Remove the "13 Nov: 27.5 kg" caption under the chart and the whole "New record in this period" block.
5. Chart: keep. "Data values" collapsible: rename to "Values" and keep.
6. "All exercises" row: keep.
7. **Pain and stiffness:** replace the dot chart [23f] with a list for the selected area. Keep the area picker [23g]. Remove the Before/After/Next day segmented control. Each row: title `shortDate()`, subtitle phase ("After workout"), end chip with the observation text from section 5.4. Newest first, at most 12 rows, then "Show all" `TextAction`.
8. Empty progress [44a]: keep the notebook artwork card. Text: "No workouts in this period."

---

## 9. P3: Library

### 9.1 Routines

1. List rows [31]: title = name, subtitle = "7 exercises, about 50 min". If the routine has a description, show it as a second subtitle line (keep, as "No equipment" and "For tired days" are useful).
2. New routine [31a, 31b]: the details dialog opens first (keep). The editor then has "+ Add exercise" and, as the only bottom button, "Create routine" (disabled until at least one exercise is added). Remove "Save routine" and the disabled "Start routine". After creating, navigate to the routine detail.
3. Routine detail [30]: header pattern from section 6.5. Bottom primary "Start routine" (keep).
4. Exercise rows: keep the drag handle. Remove "Move up" and "Move down" from the row menu [30d] and expose them as TalkBack custom actions on the row (`Modifier.semantics { customActions = listOf(...) }`). The row menu keeps "Edit targets and note" and "Remove exercise".
5. Edit targets sheet [30e]: default to a uniform editor with three fields in one row: Sets, Reps, kg (or the basis-specific value). Add a `TextAction` "Vary by set" that switches to the existing per-set editor. In the per-set editor replace each red "Remove planned set" text with a close icon button at the end of the set row. Rename the "Record" dropdown label to "Measure as".
6. Remove routine dialog [30c]: keep, copy in section 12.

### 9.2 Exercises list and filters

Evidence: [34] search field, favourites star, "All locations" dropdown and horizontally scrolling type chips that are cut off ("Bodyw…"). Four filter mechanisms for about ten exercises.

Change (`feature/library/LibraryScreen.kt`):

1. Keep the search field and the favourites star toggle.
2. Remove the location dropdown and the type chip row. Make search also match the type ("machine", "mobility") and location ("gym", "home") so the same filtering is still possible by typing.
3. Row subtitle: type only ("Machine"). Do not append the machine name ("Machine, Pulldown machine" [34b]).
4. Keep the thumbnail artwork and the last value with weekday at the end of the row.
5. Empty search result [44c]: "No matching exercises" only, remove "Change your filters or add an exercise."

### 9.3 Exercise detail

Evidence: [32] the photo placeholder card shows a stock illustration next to "Your photo of the machine" and "Add photo", so it is unclear whether the illustration is the user's photo. History repeats the best value [32b, 32c].

Change (`feature/library/ExerciseScreen.kt`):

1. Header pattern from section 6.5. Toolbar: back, favourite star, "Edit" as `TextAction`.
2. Type and muscle chips (keep).
3. Placeholder card without a photo: keep the card, its wash and the machine artwork. Replace the text "Your photo of the machine" with "No photo yet" in `colors.textSecondary` and keep "Add photo" as an `OutlineButton`. Keep the camera icon.
4. Machine settings: omit rows whose value is empty or "None" ("Backrest None" [32]).
5. "My instructions" section: title "Notes" (see 9.4).
6. Links, Alternatives: keep.
7. History: remove the "Best load for 10+ reps" card. Under the History header (with "See chart") list the last five sessions: date `shortDate()` as subtitle above the set summary (keep current layout [32c]), using the formats from section 5.

### 9.4 Exercise editor

Evidence: [33 to 33e, 34c]. Fields: name, machine settings, measurement with the sentence "Use a separate exercise for each machine or way of measuring." (shown twice), "More details" sub-page with Type, Muscles or labels, Location, "Machine name (as shown on the machine)", "My instructions", "Personal notes", Links, Alternatives, and "Archive exercise".

Change (`feature/library/ExerciseEditor.kt`):

1. Remove the "More details" sub-page. Show all fields on one scrolling screen in this order: Name, Type, Measurement, Machine settings, Notes, Muscles or labels, Location, Links, Alternatives, Archive exercise.
2. Remove the "Machine name (as shown on the machine)" field (line 189). Keep the column. Stop showing the machine name anywhere in the UI (Library subtitle, exercise detail, PDF).
3. Merge "My instructions" and "Personal notes" (line 199) into one field labelled "Notes", bound to the instructions column. When an exercise is opened in the editor and its personal notes column is not blank, append it to the instructions text separated by a blank line, and clear the notes column on the next autosave. This is the one data rewrite allowed by the ground rules. Update backup import so a backup with both fields still imports both columns unchanged (the merge happens only in the editor).
4. Remove the sentence "Use a separate exercise for each machine or way of measuring." in both places.
5. New exercise [34c]: remove "Save this exercise, then add photos on its details page." and the empty "Photos" heading. Bottom button "Create exercise" (section 6.2).
6. Machine settings rows: keep the two-field setting/value pairs with a close icon. Remove the "Setting" and "Value" column captions [33]; the placeholders say the same.
7. Measurement and Type pickers [33a, 33d]: keep.
8. Archive confirmation [33f]: copy in section 12.

---

## 10. P3: Music (pushed screen, section 1.1)

1. Header: title "Music" with the add icon (new playlist) and ⋮ (Choose folder, Refresh) [51a]. Back arrow on the left because it is no longer a tab.
2. Playlist detail [41]: toolbar without title (section 6.5), the name stays on the cover artwork. Keep Play and Shuffle.
3. Playlist ⋮ menu [41a]: keep Add songs, Edit playlist, Rename, Delete playlist.
4. Now playing [42]: remove the separate Stop button (pause already stops playback; check `MusicPlayback` for any behaviour only Stop provides and keep it reachable through pause plus queue end). Keep shuffle and repeat at the two ends of that row.
5. Progress bar [42, 51d]: the thumb is a tall black line that looks like a text cursor. Use a round thumb of `Dimens.chartDot * 2` diameter in `colors.ink`, and keep the inactive track in `colors.surface`. Check `Slider` / `SliderDefaults` docs for the pinned Compose version.
6. Mini-player while paused [51]: the playing indicator shows five small dots, which reads as "loading". When paused, show the static bars icon at reduced alpha (`colors.textTertiary`) instead.
7. Empty state "Add your music" [44d, 44e]: keep.
8. Delete playlist dialog [41e]: copy in section 12.

---

## 11. P3: Settings, export, backup, privacy

### 11.1 Settings screen

Evidence: [07, 07c, 50a, 50b].

Order and content after the change:

1. **Weekly goal**: stepper row (keep). When the value is 0, the stepper shows "0" and the subtitle reads "No weekly goal" instead of showing "Off" inside the stepper [50a]. Otherwise the subtitle is "Workouts per week".
2. **Reminders**: one switch "Workout reminders". When on, the day circles and the time row appear (keep [07]). When reminders were paused by a backup restore, the switch is off and its subtitle reads "Paused after restore". Remove the separate "Turn on again" action [07] (the switch does it).
3. **Health Connect**: keep the switches [50b]. When Health Connect is not available, show a single row "Health Connect" with subtitle "Not available on this phone" instead of a section header plus a sentence [07c].
4. **Appearance**: keep the three preview tiles.
5. **Your data**: "Create backup" (subtitle "Last backup 13 Nov, 11:15" via `dateTime()`, or "No backup yet"), "Restore backup", "Export". Remove "Home screen widget / Not placed yet" (`SettingsScreen.kt:175`); the widget is added from the launcher as usual.
6. **Privacy**: "Privacy and permissions" row (keep, see 11.4).
7. Remove the Profile section (section 7.1).

### 11.2 Export

Evidence: [08 to 08d]: period selector, Select all, Clear selection, a checkbox per workout, two option switches, a format control, Save and Share, and the caption "Several CSV files, packed in one ZIP file."

Change (`transfer/ExportScreen.kt`):

1. Toolbar title "Export".
2. Content: "Period" row (opens the existing range dialog [08a], default "All workouts"), then "Include private notes" and "Include check-ins" switches, then the PDF/CSV segmented control.
3. Remove the per-workout checkboxes, "Select all" and "Clear selection". The period defines what is exported.
4. Remove the CSV caption.
5. Bottom: "Share" as `PrimaryButton` and "Save to files" as `OutlineButton` above it, or both in one row as today. Keep exactly one primary.
6. Keep `transfer/JournalExports.kt` selection logic working by passing every workout in the period.

### 11.3 Backup restore dialog

[07i] Seven lines of text. New copy in section 12. Keep the counts in a shorter form.

### 11.4 Privacy screen

`health/HealthPrivacyActivity.kt` must stay: Health Connect requires a privacy rationale activity. Shorten the content [07d] to three short sections, each one or two sentences (copy in section 12). Keep the strings in `res/values/strings.xml`.

---

## 12. P4: copy changes

Apply these exact replacements. "Remove" means delete the text and any layout reserved for it. Search for each old string in `app/src/main/` (Kotlin and `res/values/strings.xml`) and update affected tests.

### Remove

- "Pick a routine below or add exercises as you go." (Today empty state)
- "Tap the body to add an area. A filled area means pain. An outline means stiffness only." (`BodyMap.kt:142`)
- "Use a separate exercise for each machine or way of measuring." (exercise editor, new exercise)
- "Save this exercise, then add photos on its details page." (new exercise)
- "Planned values stay separate from your recorded work." (`LibraryScreen.kt:229`, keep the first sentence "Add the exercises you want to remember." shortened to "Add the exercises you do.")
- "Change when you started or finished." (Fix time sheet [13b])
- "Workouts without times aren't shared to Health Connect." (Fix time sheet [13d])
- "Leave empty if you don't know." (duration field [21c, 22d])
- "Check-ins are optional. You can add one after saving." [22d]
- "Demo workout / Started Fri 13 Nov / Review the timing before saving." banner [22c]
- "Several CSV files, packed in one ZIP file." [08d]
- "Change your filters or add an exercise." [44c]
- "All changes saved" (`RoutineEditor.kt:362`)
- "In progress" label on the Today active card [01]
- "Health context" header and the period and source lines [50]
- "Health Connect is not available on this phone." as a standalone sentence (replaced by the row in 11.1)
- "Not placed yet" / "Placed" widget row
- "Observed" prefix in check-in rows and PDF

### Replace

- **Empty finish [14a]**
  - Old: "Complete at least one set, mark an exercise done or add a note."
  - New: "Log a set or add a note to save this workout."
- **Remove areas dialog [12d]**
  - Old: "The areas will be removed when you save this check-in."
  - New: "The areas will be removed."
- **Update routine dialog [10i]**
  - Old: Title "Update the saved routine?" and a list of every exercise with "N planned sets" (`WorkoutScreen.kt:322`)
  - New: Title "Update Full body A?" (routine name), body "Save today's exercise order and targets to the routine." No per-exercise list.
- **Discard dialog [10j]**
  - Old: "The session and all work recorded in it will be deleted."
  - New: "Everything logged in this workout will be deleted."
- **Delete workout dialog [21g]**
  - Old: "This workout is removed from your journal. If it was shared to Health Connect, it is removed there the next time the app has access."
  - New: "This workout will be deleted, also from Health Connect."
- **Remove routine dialog [30c]**
  - Old: "Saved workouts and any active session are kept."
  - New: "Your workout history is kept."
- **Archive exercise dialog [33f]**
  - Old: "Historical records and photos are kept. This exercise will no longer appear in new selections."
  - New: "History and photos are kept. The exercise is hidden from lists."
- **Remove photo dialog [32h]**
  - Old: "This photo will be removed from your exercise library."
  - New: "The photo will be deleted."
- **Delete playlist dialog [41e]**
  - Old: "Delete Night Drive and its song list?"
  - New: "Your music files are not deleted." (title stays "Delete playlist?")
- **Restore dialog [07i]**
  - Old: Seven-line paragraph
  - New: "6 workouts, 10 exercises and 3 routines will replace your current journal. Reminders and Health Connect sharing will be off until you turn them on."
- **Fix time sheet**
  - Old: "I don't know the times"
  - New: "Times unknown"
- **Fix time sheet**
  - Old: "Use recorded times"
  - New: "Use recorded times" (keep)
- **Timing editor toolbar**
  - Old: "Review workout"
  - New: "Fix time"
- **Past workout dialog**
  - Old: "Add exercises"
  - New: "Continue"
- **Finish screen**
  - Old: "Share to Health Connect / Off" row
  - New: Removed; optional caption "Shared to Health Connect after saving" (section 3.1)
- **Exercise menu**
  - Old: "Do later", "Skip today"
  - New: "Skip for now" (one item)
- **Exercise detail**
  - Old: "Your photo of the machine"
  - New: "No photo yet"
- **Exercise detail**
  - Old: "Best load for 10+ reps" card
  - New: Removed
- **Exercise editor**
  - Old: "My instructions", "Personal notes"
  - New: "Notes"
- **Routine targets**
  - Old: "Record"
  - New: "Measure as"
- **Progress**
  - Old: "Data values"
  - New: "Values"
- **Progress**
  - Old: "New record in this period" block
  - New: Removed
- **Progress**
  - Old: "was 20 kg on 25 Sep"
  - New: "Best so far, up from 20 kg on 25 Sep"
- **Progress empty**
  - Old: "No workouts recorded in this period."
  - New: "No workouts in this period."
- **History month summary**
  - Old: "Not recorded" (average)
  - New: "–"
- **History next-day row**
  - Old: "Next day / Add"
  - New: "Next day check-in"
- **Settings reminders**
  - Old: "Paused after restoring a backup" + "Turn on again"
  - New: Switch subtitle "Paused after restore"
- **Settings weekly goal**
  - Old: Stepper value "Off"
  - New: Stepper "0", subtitle "No weekly goal"
- **Settings data**
  - Old: "Create full backup"
  - New: "Create backup"
- **Settings data**
  - Old: "Export CSV or PDF"
  - New: "Export"
- **Export toolbar**
  - Old: "Export your journal"
  - New: "Export"
- **Routines empty state title**
  - Old: "Make it your routine"
  - New: "No routines yet"
- **Music playlists empty**
  - Old: "Make a playlist for your workouts."
  - New: keep

### Privacy screen copy (section 11.4)

- **On this phone**: "Your journal is stored only on this phone. Backups contain everything, so keep them private."
- **Health Connect**: "Finished workouts can be shared with their start, end and type. Steps, sleep and heart rate are shown in the app and never saved in your journal."
- **Removing data**: "Turn sharing off or remove data in Health Connect settings. Deleting a workout here also deletes it there."

---

## 13. Verification

1. Build and unit tests pass after every section (ground rules).
2. Update the screenshot capture so that it produces the same screen set as `reports/screenshots-2026-11-19-pixel-9/`, minus removed screens (top-bar workout menu, Name dialog, More details page, Measure picker, check-in area cards, Review workout for new past workouts) and plus new ones (start sheet [Today], finish screen with "Anything hurt?" = Yes, pain area sheet, Music opened from Today). Write the new set to `reports/screenshots-<date>-pixel-9/` in light and dark theme. Do not touch existing report folders.
3. Check by eye on the new screenshots:
   - bottom bar has three tabs,
   - the workout clock is dark, 20 sp and next to "4 / 8",
   - there is one ⋮ on the workout screen,
   - finishing a workout is one screen,
   - all artwork and washes are still present,
   - no date outside the formats in section 5.1,
   - no `·`, `•` or ` | ` anywhere,
   - text inputs have a thin border and white (dark: black) fill, except the set table.
4. TalkBack spot check: the workout set check circles, the exercise title (opens details), the routine row custom actions (Move up, Move down), the body map "Pick from list" action and the header add buttons all have meaningful labels.
5. Font scale 1.3: Today, workout, finish, Settings and Library show no clipped text.
