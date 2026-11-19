# InShape UI review plan (screenshots of 2026-11-18)

Audience: the coding agent (Codex) implementing fixes in `C:\Dev\InShape`.

Source: a critical review of the 150 screenshots in `reports/screenshots-2026-11-18-pixel-9/` (Pixel 9, dark and light theme). Screenshot file names are cited in brackets by their numeric prefix, for example [10] means `10-workout-active-dark.png` and [216] means `216-workout-mini-player-light.png`. Several findings were checked against the source code. The file and line references below are from the working tree on 2026-11-18. Line numbers can drift, so search for the quoted code if a line does not match.

This plan builds on three earlier documents in this folder:

- `implementation-plan.md` (monochrome redesign, ground rules in its section 0)
- `ui-polish-plan.md` (review of the 2026-11-17 screenshots)
- `artwork-implementation-plan.md` (approved sage, charcoal and petrol artwork and gradient washes, handed off on 2026-11-18)

## 0. Ground rules (apply to every change)

- App UI stays in English, sentence case.
- Foreground UI is monochrome: black, white and grays from `ui/theme/InShapeColors.kt`. The `error` token is only for destructive actions and errors.
- The artwork and the `ArtworkWash` gradients are an approved design decision. Do not remove or recolour them. This plan only changes how text is laid out next to them.
- No rest timer. No pause button. Health Connect stays.
- Never use `·`, `•` or ` | ` as separators in any UI text, string resource, content description or test. Separate values with layout (opposite ends of a row, stacked lines, different weight or colour) or, in running text, with a comma or parentheses.
- Never add a coloured vertical bar on the left edge of a card, row or panel. Express state with a pill, text colour, icon, or the background or border of the whole element.
- Every colour, size, radius, spacing value and text style comes from `ui/theme/` (`InShapeColors`, `Dimens`, `InShapeShapes`, `Type.kt`). If a value you need does not exist, add a token there and use it. Do not hardcode a `dp`, `sp` or `Color` in a feature file.
- One source of truth per concept. Before writing new code, search for existing logic. Delete code that becomes unused in the same change. When you move or rename something, update every caller.
- Do not commit, push or open a pull request. Do not run the `lc` or `sc` scripts. Do not modify, move or delete anything in `reports/screenshots-2026-11-18-pixel-9/`.
- Before you use an API mentioned here (`BringIntoViewRequester`, `PrimaryTabRow`, `SecondaryTabRow`, `TabRowDefaults`, `ButtonDefaults.TextButtonContentPadding`, `Slider`, `ModalBottomSheet`), check the current official documentation for the versions pinned in `gradle/libs.versions.toml`. Do not assume signatures.

## Priorities

- **P0**: visibly broken, unreadable or leaking internal values. Fix first.
- **P1**: the in-gym workflow. These decide whether the app is quick to use during a workout.
- **P2**: system-level component and token fixes. One change fixes many screens.
- **P3**: screen-specific improvements.
- **P4**: copy.

Implement in this order. Each numbered item is self-contained and has its own acceptance criteria.

---

## 1. P0: broken, unreadable or leaking internal values

### 1.1 Privacy screen body text is black on black

Evidence: [75]. The headings are readable but every paragraph is near-invisible in dark theme.

Cause: `health/HealthPrivacyActivity.kt` calls `Text(...)` without a colour, inside `ActivityTheme { Page { ... } }`. That activity has no `Scaffold` or `Surface`, so `LocalContentColor` falls back to black. `MainActivity` does not have the problem because its `Scaffold` in `navigation/AppNavigation.kt` provides the content colour.

Fix:

1. In `HealthPrivacyActivity`, wrap the `Page` in `Surface(Modifier.fillMaxSize(), color = InShapeTheme.colors.background, contentColor = InShapeTheme.colors.text)`.
2. Move the four paragraph strings and the three section titles into `res/values/strings.xml` and read them with `stringResource`. They are currently hardcoded.
3. Style each paragraph with `MaterialTheme.typography.bodyLarge` and give the column the normal `Dimens.itemGap` spacing that `Page` already provides.

Acceptance: in dark and light theme every paragraph on the Privacy screen has at least 4.5:1 contrast against the background.

### 1.2 Measurement basis keys are shown raw

Evidence: the Measurement dialog lists `level`, `bodyweight`, `assistance_kg`, `duration` [34]. The dropdown shows `kg` [33, 69c]. Raw keys can also reach "Best load for 10+ reps" in Exercise detail, Progress units and Library list values.

Cause: the display text for a basis is computed in at least five places, each slightly differently:

- `feature/library/LibraryScreen.kt:59` declares `measurementBases` and line 82 to 84 formats `"level 3"` and `"25 kg assistance"`.
- `feature/workout/SetRow.kt:48` `loadLabel(basis)` returns `"Assistance kg"`, `"Level"`, `"Load"`.
- `feature/workout/WorkoutScreen.kt:122` formats `"kg assistance"` inline.
- `feature/progress/ProgressScreen.kt:221` to 233 computes `unit` inline.
- `feature/library/ExerciseScreen.kt:343` prints `${row.basis}` directly.
- `feature/library/ExerciseEditor.kt:109` passes `measurementBases` to `SelectField` without `optionLabel`.

Fix:

1. Create one file, for example `data/MeasurementBasis.kt`, with an enum or sealed type that has the stored key (`"kg"`, `"level"`, `"bodyweight"`, `"assistance_kg"`, `"duration"`) plus:
   - `optionLabel`: "Weight (kg)", "Machine level", "Bodyweight", "Assisted (kg of help)", "Time".
   - `columnLabel` for the set table header: "kg", "Level", "Reps only" (or no load column, keep current behaviour), "Assist kg", "Min".
   - `formatValue(value: String): String`: "25 kg", "Level 3", "25 kg assist", "5 min".
   - `unitLabel` for charts: "kg", "level", "kg assist", "min".
   - A `fromKey(key: String)` that falls back safely for unknown stored keys.
2. Replace every call site listed above with this type. Delete `measurementBases`, `loadLabel` and every inline `when (basis)` that becomes unused.
3. In `ExerciseEditor`, pass `optionLabel = { MeasurementBasis.fromKey(it).optionLabel }` to `SelectField`.
4. Add a unit test in `app/src/test/.../data/` that every key round-trips and that no label equals its raw key except "kg".

Acceptance: `grep -rn "assistance_kg" app/src/main/java` only matches the new file and data or serialization code. No screen shows a lowercase key or an underscore.

### 1.3 Today shows an empty "Routines" section

Evidence: [02], [03], [04], [203], [204], [205]. The header "Routines" with "See all" is followed by empty space.

Cause: `feature/today/TodayScreen.kt:274` checks `data.routines.isEmpty()` before line 278 filters out the routine already shown as "Next up". When the only populated routine is the suggested one, the `ListCard` renders with no items.

Fix:

1. Compute `val otherRoutines = data.routines.filter { active != null || it.id != routine?.id }.take(3)` before the `SectionHeader`.
2. If `data.routines.isEmpty()`, keep the existing "Create a routine" row.
3. Else if `otherRoutines.isEmpty()`, do not render the "Routines" section at all (no header, no card).
4. Otherwise render the header and the list from `otherRoutines`.

Acceptance: with one routine and no active workout, Today shows no "Routines" header. With three routines, it shows two rows. With an active workout it shows all three.

### 1.4 "Edit" button touches the right screen edge

Evidence: [30], [30a], [30b], [31], [32], [211]. The outlined "Edit" button is about 4 dp from the screen edge. Every other screen keeps a 20 dp margin.

Cause: `feature/library/ExerciseScreen.kt:158` places an `OutlineButton` as the last item of `PushedHeader` actions. `TopAppBar` only adds 4 dp at the end. Icon buttons look fine because their 48 dp touch box has internal space, but a bordered button does not.

Fix:

1. Add `val headerActionEnd = 16.dp` to `Dimens` (20 dp screen padding minus the 4 dp the app bar already adds). Document that in a short comment.
2. In `PushedHeader` (`ui/components/Controls.kt:152`), add a parameter `endInset: Boolean = false`. When true, append `Spacer(Modifier.width(Dimens.headerActionEnd))` after `actions`.
3. Pass `endInset = true` from every `PushedHeader` whose last action is a bordered or filled button. Search for `PushedHeader(` and check each. At minimum `ExerciseScreen`. The workout screen ends with an overflow icon and does not need it.

Acceptance: on Exercise detail the right edge of the "Edit" border is 20 dp from the screen edge, matching the left edge of the title.

### 1.5 Exercise links look like an empty section heading

Evidence: [31]. "Exercise information" sits between "My instructions" and "Alternatives" in the same bold white style as the headings and seems to have no content. It is actually a saved link rendered with `TextAction` (`feature/library/ExerciseScreen.kt:287`).

Fix: render each link as a `ListRow`:

- title: the link label, or the URL host if the label is blank
- subtitle: the URL host (for example `nhs.uk`)
- trailing: `Icons.AutoMirrored.Outlined.OpenInNew` at `Dimens.iconSmall`, tint `textSecondary`
- `onClick`: the existing intent and error handling

Place the links in their own section with `SectionHeader("Links")` below "My instructions". Do not render the section when there are no links.

### 1.6 Raw enum text in History check-ins

Evidence: [58]. "Energy: ok" and "Energy: good" are lowercase, while the check-in screen offers "Low", "OK", "Good" [44].

Cause: `feature/history/HistoryScreen.kt:519` uses `energy.lowercase()`. The labels are separately hardcoded in `feature/checkin/CheckInScreen.kt:248` to 249.

Fix: create an `EnergyLevel` enum in `feature/checkin/CheckInLevels.kt`, next to `EffortLevel` and `BodyLevel`, with `key` (`"LOW"`, `"OK"`, `"GOOD"`) and `label` ("Low", "OK", "Good"). Use it in both places. In History, show the energy as its own line "Energy OK" (label in `textSecondary`, value in `text`), or as a filled pill (see 3.3). No colon.

### 1.7 Destructive dialogs in Music are not marked as destructive

Evidence: "Delete playlist?" confirms with a white "Delete playlist" [99]. Every other destructive dialog uses the `error` colour [09, 18b, 30d, 39, 59, 67].

Cause: `feature/music/PlaylistEditors.kt` builds its own `AlertDialog` in `PlaylistPrompt` and never sets a colour. `ConfirmDialog` in `ui/components/Editors.kt:109` guesses "destructive" from the first word of the label.

Fix:

1. Give `ConfirmDialog` an explicit `destructive: Boolean` parameter and remove the string-prefix guess. Update every caller.
2. Use `ConfirmDialog(..., destructive = true)` for "Delete playlist?". If `PlaylistPrompt` must stay for the rename case, add the same `destructive` parameter to it.
3. In `RoutineEditor`, the per-exercise menu item "Remove exercise" [63] must use `MenuAction(..., destructive = true)` with `Icons.Outlined.Delete`, matching the workout menu [13].

---

## 2. P1: the in-gym workflow

The aim of this section: open the app, start, log every set and finish without hunting for controls, with one hand, between sets.

### 2.1 Keep the current set on screen

Evidence: with music playing, the mini-player and "Exercise done" occupy about 20 % of the screen and the active set 3 is hidden behind them [90, 216]. The 120 dp clock card also pushes the set table down [10].

Fix:

1. In `feature/workout/WorkoutScreen.kt`, attach a `BringIntoViewRequester` to the current exercise title row (the `Row` at line 347). When `current?.row?.id` changes (initial composition with at least one completed item, and after "Exercise done"), scroll so that this row is at the top of the viewport. The large clock card then scrolls away and the existing compact clock in the top bar (`showTopClock`) takes over. Do not change the clock itself.
2. Attach a second `BringIntoViewRequester` to the highlighted `SetRow` (the one with `highlighted = set.id == firstIncomplete`). When `firstIncomplete` changes, bring that row into view. The visible area must exclude the bottom action bar, so the row ends up fully above the mini-player.
3. Skip both automatic scrolls while the keyboard is visible (`keyboardVisible`) and while the user is dragging the list, so they never fight the user.
4. Do not animate longer than the default `animateScrollTo` spec.

Acceptance: on a Pixel 9 with music playing, after completing set 2 of 3, set 3 is fully visible above the mini-player without manual scrolling. After "Exercise done", the next exercise title is at the top and the top bar shows the compact clock.

### 2.2 Empty workout: make "Add exercise" the primary action

Evidence: [07]. The filled bottom button says "Finish workout" while there are no exercises, and "Finish" also appears in the top bar.

Fix in `WorkoutScreen.kt` around lines 179 to 214 and 320 to 329:

1. When `workout.items.isEmpty()`, the bottom `PrimaryButton` is "Add exercise" with `Icons.Outlined.Add` and opens the picker (`picker = true`). The `EmptyState` keeps its title and message but no button.
2. When all items are done (`current == null` and items exist), keep the bottom "Finish workout" primary button and the `EmptyState` "Your plan is complete" with its outlined "Add exercise".
3. The top bar "Finish" stays in both cases.

### 2.3 Start music in one tap from the workout

Evidence: music is only reachable through the overflow menu, then a chooser [18, 105]. Most people start music when they start the workout.

Fix:

1. In the workout top bar, add an `IconButton` with `Icons.Outlined.MusicNote` (content description "Music") before "Finish", shown when `musicPlayback != null`. It sets `chooser = true`. Remove the "Music" entry from the overflow menu so it exists once.
2. When a song is already playing, the mini-player already opens the chooser. Keep that.

### 2.4 Rename the mid-workout check-in menu item

Evidence: [18]. The overflow shows "Check in before workout" 32 minutes into the workout.

Fix: label it "Pre-workout check-in" while no exercise is done, and "Add check-in" afterwards. It still opens the same "before" check-in. Keep the icon.

### 2.5 Swap exercise needs search

Evidence: [14]. The swap sheet lists only the two alternatives saved on the exercise. If both machines are taken, the user cannot pick anything else from here.

Fix in the alternatives sheet (`WorkoutScreen.kt` around line 568):

1. Keep "Your alternatives" at the top.
2. Below it, add `SectionHeader("All exercises", quiet = true)` and the same `SearchField` plus filtered exercise list used by the "Add exercise" picker (`LibraryScreen.kt:283`). Reuse that composable instead of copying it. If it is private, extract it to a shared `ExercisePickerList` in `feature/library/` and use it in both places.
3. Choosing any exercise performs the same swap as choosing an alternative.
4. Add `Modifier.navigationBarsPadding()` (or the sheet's own inset handling) to the sheet's list so the last row does not sit under the system navigation bar [14, 18c, 66].

### 2.6 Now playing controls are too small for gym use

Evidence: [101, 218]. Transport icons are 24 dp glyphs in default `IconButton`s. "Shuffle off", "Repeat off" and "Stop" read as plain text, not controls.

Fix in `feature/music/PlayerScreens.kt` `NowPlaying` (lines 93 to 158):

1. Add tokens `Dimens.transport = 64.dp`, `Dimens.transportIcon = 36.dp`, `Dimens.playButton = 80.dp`, `Dimens.playIcon = 40.dp`.
2. Previous and next: `IconButton` sized `Dimens.transport`, icon `Dimens.transportIcon`.
3. Play or pause: a filled circle (`InShapeShapes.pill`, `colors.ink` background, `colors.onInk` icon) sized `Dimens.playButton`.
4. Space the three with `Arrangement.spacedBy(Dimens.sectionGap)` and centre them.
5. Replace the three `TextAction`s with one row of three icon controls sized `Dimens.touch`, laid out with `Arrangement.SpaceBetween`: shuffle at the start, stop in the middle, repeat at the end. Shuffle and repeat are toggles (`IconToggleButton` or equivalent) tinted `colors.text` when on and `colors.textTertiary` when off. Repeat shows `Repeat` for off and all, and `RepeatOne` for one. Content descriptions: "Shuffle, on", "Shuffle, off", "Repeat, all", "Repeat, one", "Repeat, off", "Stop".
6. Show the track's cover with `MusicArtwork(..., size = 240.dp token)` above the title when one is available, otherwise keep `PlaybackRibbon`.
7. Fix the `"Repeat ${state.repeat.name.lowercase()}"` string. Do not build UI text from enum names.

### 2.7 Past workout editor uses "Finish"

Evidence: [52b]. Adding a past workout opens the live workout screen with "Finish" and "Finish workout".

Fix: past entries are created with `kind = "PAST"` (`data/JournalRepository.kt:571`, already checked in `navigation/AppNavigation.kt:173`). In `WorkoutScreen`, when `workout.row.kind == "PAST"`, label the top bar button "Save" and the bottom button "Save workout". The navigation target stays the same. Put the `"PAST"` check in one helper (for example `Workout.isPastEntry`) if it is needed in more than one file.

### 2.8 Lighter end-of-workout path

Evidence: Finish takes four screens: "Finish" [10], "Workout done" with "Save workout" [20], then the after-workout check-in with effort, pain toggle, body map, body area picker and notes [40, 43].

Fix (keep all features, change defaults):

1. In the after-workout check-in, show only the effort segmented control ("Easy", "Just right", "Hard") and the "Any pain or stiffness?" No or Yes control by default. The body map, area picker and area cards appear only after "Yes" (this already happens for the body, check that nothing else is visible by default).
2. "Save" with only an effort choice must be enough. Do not require any body area.
3. Keep "Skip" in the top bar.

---

## 3. P2: system-level components and tokens

These fixes change shared components. Update every caller and check every screen that uses the component.

### 3.1 Separate actions from headings

Problem: `TextAction` (`ui/components/Buttons.kt:101`) uses `labelLarge` (15 sp SemiBold) in `colors.text` with zero horizontal padding. `SectionHeader` uses `titleMedium` (16 sp SemiBold) in `colors.text`. The two are indistinguishable. Affected: "View weekly counts", "Hide weekly counts" [54a, 210], "View data values", "Hide data values" [54c, 54e], "View values" [54g], "Remove link" [37], "I don't know the times" [22, 57b], "Use recorded times" [24], "Resume" [70], "Start empty workout" [02], "Add energy and notes" [44], "Exercise information" [31].

Rules:

- **Heading**: `SectionHeader` only. Never tappable except its trailing action.
- **Disclosure** (shows or hides content below it): new `DisclosureRow`.
- **Inline action** (does something): `TextAction`, always with a leading icon when it appears in page content. Without an icon it is allowed only inside dialogs, top bars and as the `SectionHeader` trailing action.
- **Navigation to another screen or a link**: `ListRow` with chevron or the open-in-new icon.

Implementation:

1. Add `DisclosureRow(label: String, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier)` to `ui/components/Lists.kt`. Layout: full-width row, `heightIn(min = Dimens.rowMin)`, text `bodyLarge` regular in `colors.text`, trailing `Icons.Outlined.KeyboardArrowDown` rotated 180 degrees when expanded, tint `textSecondary`. Semantics: `role = Role.Button`, `stateDescription` "Expanded" or "Collapsed". The label does not change between states (use "Weekly counts", "Data values", "Values", "Energy and notes").
2. Replace these with `DisclosureRow`: `ProgressCharts.kt:166`, `ProgressCharts.kt:263`, `ProgressScreen.kt:449`, `CheckInScreen.kt:243`.
3. Change `TextAction` text style to `labelLarge.copy(fontWeight = FontWeight.Medium)` so it is visibly lighter than headings. Keep its colour.
4. Give these an icon: "Start empty workout" (`Icons.Outlined.Add`), "I don't know the times" and "Use recorded times" (`Icons.Outlined.Schedule`), "Add set", "Add setting", "Add link", "Add exercise" (they already have `Add`, keep), "Delete workout" and "Archive exercise" and "Discard workout" (`Icons.Outlined.Delete`, error colour), "Show whole month" (`Icons.Outlined.CalendarMonth`), "Reopen" (`Icons.Outlined.Undo`).
5. "Remove link": see 4.8.
6. "Resume" in Settings: see 4.10.

Acceptance: on every screen, any bold white text without an icon or chevron is a heading and is not tappable.

### 3.2 Dialog buttons run together

Problem: dialog buttons read as one phrase: "Cancel Update routine" [18a], "Cancel Discard" [09], "Cancel New playlist" [104], "Cancel Delete playlist" [99]. `TextAction` sets horizontal content padding to zero, so `AlertDialog`'s 8 dp button gap is the only space.

Fix:

1. Add `DialogAction(text, onClick, destructive = false, enabled = true)` to `ui/components/Editors.kt`. It is a `TextButton` with `ButtonDefaults.TextButtonContentPadding` (verify the name), `labelLarge` SemiBold, colour `error` when destructive and `text` otherwise.
2. Use `DialogAction` for every `confirmButton` and `dismissButton` in: `ui/components/Editors.kt` (`SelectField`, `ConfirmDialog`), `ui/components/TimePickers.kt`, `feature/history/HistoryScreen.kt`, `feature/library/RoutineEditor.kt`, `feature/music/PlaylistEditors.kt`, `feature/settings/SettingsScreen.kt`, `feature/workout/SetRow.kt`, `feature/workout/WorkoutTimingEditor.kt`. Search for `AlertDialog(` and `PickerDialog(` to find all of them.
3. Single-choice dialogs (`SelectField` options, "Comparison", "Type", "Measurement", "Add body area") apply the choice on tap and close. Their only button becomes `DialogAction("Cancel")` instead of "Close".

### 3.3 One meaning for an outline

Problem: a 1 dp outline currently means a secondary button, a data tag ("Back, arms", "Machine" [30]), a machine setting ("Seat 4" [10]), an observation ("Pain mild" [58]), the current set input [10], the selected theme [71], an expanded body-area card [43] and a search field in Music [92]. The user cannot tell what is tappable.

Rule: an outline means **button** or **current or selected state**. Data is never outlined.

Fix:

1. `Pill` (`ui/components/SelectionControls.kt:49`): remove the `outlined` parameter. Neutral pills are `colors.surface` fill, `colors.text` content, `labelMedium`, height `Dimens.setNumber`, horizontal padding `Dimens.innerSmall` plus 2 dp (add a token if needed). Update the three callers: `CheckInLevels.kt:30` and 31, `ExerciseScreen.kt:166`, `WorkoutScreen.kt:410`.
2. Workout machine settings ("Seat 4", "Thigh pad 2", "Wide bar"): use the filled pill with `labelLarge` text so they remain easy to read at arm's length. They are currently larger than the "Last time" text, which is fine for gym use, but make sure the pill row is visually lighter than the exercise title.
3. Check-in body area card (`CheckInScreen.kt` around line 190 to 215): the expanded card uses `InShapeCard(inset = true)` (filled, no border). The collapsed summary card also uses the filled style. Expanded versus collapsed is shown by the close icon and the visible controls, not by a border.
4. Keep the outline on: `OutlineButton`, the highlighted `SetRow` fields, the selected theme preview card.

### 3.4 Navigation tabs versus choice controls

Problem: the same `SegmentedControl` switches whole views (Calendar and Progress [50], Routines and Exercises [68], Playlists and Songs [91]) and also answers questions (Easy, Just right, Hard; No, Yes; Front, Back [40]). Progress adds a third pattern, filter pills for "4 weeks", "12 weeks", "All" [54], stacked under the tab control.

Rule: switching views uses tabs. Choosing a value uses the segmented control.

Fix:

1. Add `ScreenTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit)` to `ui/components/SelectionControls.kt`, built on Material 3 `SecondaryTabRow` or `PrimaryTabRow` (verify which suits a text-only underline). Style: container `colors.background`, text `labelLarge`, selected `colors.text`, unselected `colors.textSecondary`, indicator 2 dp `colors.ink` (add `Dimens.tabIndicator = 2.dp`), divider 1 dp `colors.divider`. Minimum height `Dimens.touch`. No ripple colour other than the theme default.
2. Replace `SegmentedControl` with `ScreenTabs` in `HistoryScreen.kt:151`, `LibraryScreen.kt:121`, `MusicScreens.kt:150`.
3. Progress period ("4 weeks", "12 weeks", "All", `ProgressScreen.kt:118`): replace the `FilterPill`s with `SegmentedControl(compact = true)`. After this, `FilterPill` is used only for Library filters.
4. Keep `SegmentedControl` in check-in, Progress "Before, After, Next day" and Export "PDF, CSV".

### 3.5 Two section header styles on one screen

Problem: Progress has "Workouts per week" as a 16 sp SemiBold white header and "Exercises" as a 12 sp gray label [54]. History has "November" as a gray label above a list [50]. Settings uses gray labels everywhere [70]. Exercise editor mixes both [33].

Rule: page sections use `SectionHeader` (non-quiet). `quiet = true` and `GroupLabel` are only for labels of a form field group directly above that group (for example "Machine settings" above the label and value rows in the editor) and for a list's date group inside an already titled section.

Fix:

1. Progress: "Exercises" becomes `SectionHeader("Exercises")`.
2. History calendar: "November" becomes `SectionHeader` with the month name ("November workouts").
3. Settings: "Profile", "Reminders", "Health Connect", "Appearance", "Your data", "Privacy" become non-quiet `SectionHeader`s. The "Theme" label under "Appearance" is removed (the section header is enough).
4. Audit every `SectionHeader(..., quiet = true)` and `GroupLabel(` usage against the rule and list in your final report any you left as is with a one-line reason.

### 3.6 Disabled outlined buttons

Problem: a disabled `OutlineButton` keeps a full-strength border while its text is gray ("Share" [80, 219]).

Fix: in `OutlineButton` (`Buttons.kt:80`), use `BorderStroke(Dimens.line, if (enabled) colors.outline else colors.divider)` and `disabledContentColor = colors.textTertiary`.

### 3.7 Destructive styling rule

Apply everywhere: destructive menu items use `MenuAction(destructive = true)` with the delete icon. Destructive page actions use `TextAction` with the delete icon and `color = colors.error`. Destructive dialog confirmations use `DialogAction(destructive = true)`. Search for "Delete", "Remove", "Discard", "Archive" and "Replace journal" labels and check each one. Known gaps: 1.7, and the routine exercise menu [63].

### 3.8 One reorder pattern

Problem: routines use drag handles plus a menu with "Move down" [60, 63]. Playlists use up and down arrow buttons on every row [97].

Fix: use the routine pattern in the playlist editor (`PlaylistEditors.kt` `ReorderPlaylist`): drag handle at the start of each row, plus "Move up" and "Move down" as custom accessibility actions, plus a per-row overflow menu with "Move up", "Move down" and "Remove from playlist". Reuse the routine editor's reorder code by extracting a shared reorderable row in `ui/components/` if the logic is the same. Delete the arrow buttons. Remove the checkbox column from the reorder screen (its meaning is unclear [97]) unless it drives removal, in which case removal moves into the row menu.

### 3.9 Menus

Problem: some menus have icons on every item [13, 18, 30c, 57a], the routine exercise menu has none and uses bold text [63].

Fix: every `AppMenu` uses `MenuAction` with an icon. Convert the `DropdownMenuItem`s in `RoutineEditor.kt` (around lines 398 to 410) to `MenuAction`: "Move up" (`ArrowUpward`), "Move down" (`ArrowDownward`), "Edit targets and note" (`Edit`), "Remove exercise" (`Delete`, destructive). Menu item text uses the same style as `MenuAction` elsewhere (not bold).

### 3.10 Light theme low-contrast fills

Problem: in light theme the empty week-day circles on Today [202, 203] and the inactive attendance bars on Progress [210] are `colors.surface` (`#F4F4F4`) on white and almost disappear.

Fix: use `colors.surfaceStrong` for those two non-text marks in light theme (dark theme is fine). Search `TodayScreen.kt:240` and the attendance bar drawing in `ProgressCharts.kt`. Target at least 1.3:1 against the background for these decorative fills and keep the current-week or today mark at 3:1.

---

## 4. P3: screen-specific improvements

### 4.1 Today

Evidence: [01] to [06], [202] to [205].

1. "Start empty workout": change to `OutlineButton("Start empty workout", icon = Icons.Outlined.Add, Modifier.fillMaxWidth())` directly under the hero card, with `Dimens.itemGap` above it. It currently floats as bold centred text with large gaps and reads as a heading.
2. Section spacing: "This week" and "Routines" both use `SectionHeader` spacing. Remove the custom `Modifier.padding(top = Dimens.inner)` column wrapper around "This week" (`TodayScreen.kt` around line 206) so both sections have identical top spacing.

### 4.2 Workout screen details

1. Long-press on a set number opens "Remove set 1" [11]. This is hidden, but do not add a permanent button per row. Add a one-time hint instead: below the set table of the first exercise in a session, show "Long-press a set to remove it" in `bodySmall` `textSecondary` until the user has removed a set once (store the flag in the existing preferences, no schema change). If storing the flag needs a migration, skip this item and say so in the report.
2. Exercise note field [15]: give the `TextField` the label "Note" and the placeholder "For this exercise". The field must not be a bare placeholder.

### 4.3 Workout done (finish screen)

Evidence: [20], [207].

1. The card text "Full body A, Friday 13 November" wraps into an orphan "November" next to the artwork. Split it into two lines: workout title in `titleMedium` and the date in `bodyMedium` `textSecondary`. Each line uses `maxLines = 1` with ellipsis. Do not shrink the approved artwork.
2. Stat tiles ("5 exercises", "11 sets"): keep.
3. "Share to Health Connect" row with "Off" subtitle: keep the row. When Health Connect is unavailable, show the subtitle "Not available on this device" and disable the row.

### 4.4 Fix time sheet

Evidence: [22], [24].

1. In the unknown-times state, the "Duration in minutes (optional)" `NumberField` (`FinishScreen.kt:357`) is half width. Make it `Modifier.fillMaxWidth()`.
2. "Use recorded times" currently shows a pressed-looking partial background [24]. With the `TextAction` icon change from 3.1 it becomes a normal inline action. Check that no ripple or focus background remains visible after the toggle.

### 4.5 History calendar

Evidence: [50], [50a], [209].

1. Summary strip ("5 workouts", "3 h 50 min total time", "46 min average"): the content padding is `Dimens.innerSmall` (`HistoryScreen.kt` around line 295), so "5" sits 8 dp from the rounded edge. Use `PaddingValues(horizontal = Dimens.inner, vertical = Dimens.itemGap)`. Keep the three equal-weight columns. In the empty state ("0 min average") make sure the paper ornament never overlaps a value: apply the existing `ornament` condition to the empty case as well.
2. The calendar takes two thirds of the screen and the workout list starts below the fold. Keep the calendar, but make sure the first list row is at least partly visible on a 6.3 inch screen by reducing the vertical gap between calendar weeks only if the cells keep `Dimens.calendarDay` (48 dp) touch height. Do not shrink touch targets.

### 4.6 History workout detail and review

Evidence: [57], [57b], [57e], [58].

1. "Review workout" shows the duration three ways: started and finished times, "Elapsed time 52 min" and a "Duration (min)" field. Show "Elapsed time" only when it differs from the duration field value, or remove the field when both times are known. Choose one and describe the choice in your report.
2. "I don't know the times" sits between the "Recorded timing" heading and the values. Move it below the "Finished" row, with the icon from 3.1.
3. Edit sets mode [57e]: add the `SetTableHeader` above each exercise's set rows so "25" and "10" have column labels.
4. "Delete workout?" message [59] is technical. Replace with: "This workout is removed from your journal. If it was shared to Health Connect, it is removed there the next time the app has access."

### 4.7 Progress

Evidence: [54] to [55a], [210].

1. Duplicate controls: the Weight, Reps, Sets segmented control (`ProgressScreen.kt:285`) and the "Comparison" select (`ProgressScreen.kt:297`) choose the same `metric`. Keep only the "Comparison" select and rename it "Measure". Option labels, in the order of `ExerciseMetric` (`ProgressCalculator.kt:13`): `WEIGHT` "Heaviest weight", `REPS` "Most reps", `REPS_AT_LOAD` "Reps at a set weight", `LOAD_AT_REPS` "Weight for a rep target", `SET_COUNT` "Number of sets". Put the labels on the enum (a `label` property) instead of the parallel `options` list at `ProgressScreen.kt:99`, and delete that list. Delete the segmented control.
2. "Minimum repetitions" field plus the "10+ reps" line under it [54f] say the same thing twice. Keep the field, delete the line.
3. "New record in this period" block: the label is a `SectionHeader` and the value is `headlineSmall`. Keep the value as the most prominent element. Change the header to quiet label style above the value, so the hierarchy is: small label, big value, small gray details.
4. "Best load for 10+ reps" card in Exercise detail [32] has the same inversion: label in `titleMedium`, value in plain body text. Make the value `titleLarge` and the label `labelSmall` `textSecondary`.
5. Pain and stiffness chart [54g]: the input uses None, Mild, Medium, Strong [43], the chart says "Scale 0 to 10" and draws flat lines. Label the y axis with the four `BodyLevel` labels at their stored values and delete the "Scale 0 to 10" text (`ProgressScreen.kt:421`). If all points are equal, still draw them as dots with the level label next to the latest point.
6. "Weekly counts" disclosure (3.1): when expanded, show the list as `KeyValueRow("Week of 24 Aug", "0 workouts")` rows instead of "Week of 24 Aug: 0 workouts" text lines. Same for "Data values" (the expanded data values list already uses date and value at opposite ends [54e], keep that).
7. Exercise list subtitle "Completion recorded" [55] is unclear. Use "Done, no weight recorded".

### 4.8 Exercise editor

Evidence: [33] to [38], [69c].

1. Machine settings rows show a "Label" and "Value" floating label in every field [33]. Show a single header row above the list with two `GroupLabel`s, "Setting" and "Value", aligned to the two field columns. Fields then use placeholders only ("Seat height", "4"). Keep accessible labels through `semantics { contentDescription = "Setting 1 name" }` and "Setting 1 value".
2. Measurement select: labels from 1.2.
3. "Remove link" [37] is a bold text line under the web address. Replace it with a close icon button at the end of the link label field row, matching the remove icon on machine setting rows (content description "Remove link").
4. Alternatives [38] list every exercise in the library as checkboxes. Replace with: the selected alternatives as rows with a remove icon, and an "Add alternative" `TextAction` with the add icon that opens the shared exercise picker from 2.5, excluding the exercise itself and already selected ones.
5. "Recognizable machine name" and "Nickname (optional)" both name the exercise. Rename "Recognizable machine name" to "Machine name (as shown on the machine)". Keep both fields but put them under "More details".
6. "Save this exercise, then add photos on its details page." [69c]: keep the text but make it `bodyMedium` `textSecondary` and place it directly under the photo-related area instead of at the bottom.

### 4.9 Library

Evidence: [68] to [69d].

1. Routine row subtitle joins exercise count and description: "6 exercises, No equipment" (capital N after a comma) [68]. Show the count as the subtitle and the description as a second subtitle line, or lowercase-first only if the description starts with a capital followed by lowercase letters. Prefer two lines: subtitle "6 exercises, about 15 min", description below in `bodySmall` `textSecondary` with `maxLines = 1`.
2. Exercise filter row: "All locations" dropdown and the type pills share one horizontally scrolling row and the last pill is cut off without any hint ("Free", "Bodyw" [69]). Put "All locations" on its own line above the type pills, or keep one row but add a trailing fade (gradient from transparent to `colors.background`, 24 dp, add a token) so it is clear the row scrolls.
3. The location filter disappears when a search returns nothing [69d], so the layout jumps. Keep the filter row visible in the empty state.
4. Empty routines state [68c] has no action inside it. Add the "New routine" outlined button to the `EmptyState` (the header button stays).

### 4.10 Settings

Evidence: [70], [72], [200], [213].

1. Reminders: the switch is on, the subtitle says "Paused after restore" and a bold "Resume" sits below like a heading. When reminders are held, show the switch off, the subtitle "Paused after restoring a backup" and a `TextAction("Turn on again", icon = Icons.Outlined.NotificationsActive)` directly in the row. Turning the switch on also resumes. Remove the separate "Resume" line.
2. Health Connect unavailable: when the provider is unavailable (`HealthControls.kt:102`), hide the four switches and show only the explanation line "Health Connect is not available on this phone." Switches that cannot do anything are confusing.
3. Section headers: see 3.5.

### 4.11 Routine detail and editor

Evidence: [60] to [68b], [212].

1. Row subtitles show the exercise type ("Machine") instead of the plan. Change the subtitle in `RoutineEditor.kt:348` to describe the targets from `RoutineItem.sets` (`SetTarget(load, reps)`):
   - all sets equal: "3 sets of 10, 25 kg" (use the basis formatter from 1.2)
   - reps differ: "3 sets, 8 to 12 reps"
   - no targets: "No targets yet"
   - duration mode: "5 min planned" (current)
   - quick mode: "Completion only" (current)
2. Header card "7 exercises, about 50 / min" wraps next to the barbell [60]. Render two lines: "7 exercises" in `titleLarge` and "About 50 min" in `bodyMedium` `textSecondary`, each `maxLines = 1`. Same treatment wherever this summary appears next to artwork.
3. "Save routine" (outlined) above "Start routine" (filled) [65]: the user cannot tell whether edits are already saved. Show "Save routine" only when the draft differs from the saved routine, and show the quiet text "All changes saved" in `textSecondary` otherwise. Keep "Start routine" as the primary action.
4. "Routine details" dialog has only "Done" [62]. Add "Cancel" that discards the dialog's edits.
5. New routine with zero exercises [68b]: the header card says "0 exercises" on a gradient wash with nothing else. Hide the card until the routine has at least one exercise.

### 4.12 Check-in

Evidence: [40] to [46].

1. Three identical segmented controls are stacked (effort, pain yes or no, front or back). Front and back is a view switch for the body map, not a question. Render it as two small `FilterPill`s ("Front", "Back") placed at the top end of the body map, not as a full-width segmented control.
2. The body area cards with the two four-step segmented controls are fine. Make the area name `titleMedium` and keep the close icon.
3. The explanation "Tap the body to add an area. A filled area means pain. An outline means stiffness only." stays. Move it directly under the body map (it is, check spacing is `Dimens.innerSmall`).

### 4.13 Music: match the rest of the app

Evidence: [91] to [105], [214] to [218]. Music looks like a different app: rectangular outlined fields, plain-text empty states, denser rows.

1. Fields: replace every `OutlinedTextField` in `MusicScreens.kt` and `PlaylistEditors.kt` with the app's `SearchField` (search) and `TextField` (names) from `ui/components/Fields.kt`. This includes the rename dialog [98], which currently shows a Material outlined field with a floating label.
2. Empty states: replace `Text("No playlists yet")`, `Text("No songs found")`, `Text("No matching songs")`, `Text("No songs in this playlist")`, `Text("No songs to add")` with `EmptyState(title, message, action)`:
   - No folder chosen: "Add your music", "Choose a folder on this phone. InShape plays the files from there.", action "Choose music folder".
   - Folder chosen, no playlists: "No playlists yet", "Make a playlist for your workouts.", action "New playlist".
   - Search with no result: "No matching songs", "Try another word."
   Remove the standalone "Choose music folder" button above lists when a folder is already set. Move "Choose folder" and "Refresh" into an overflow menu in the Music header.
3. Row density: `SongRow`, `TrackCheckbox` rows and queue rows use `ListRow` (or match its `Dimens.rowMinTwoLine` height and `bodyLarge` title plus `bodyMedium` subtitle) so they match Library and History rows.
4. Queue [102]: mark the current song. Title `titleMedium`, trailing `PlaybackBars(state.playing)` at icon size. The semantics already say "Current song", the visual must match.
5. "Add to playlist" dialog [104]: options are centred `TextAction`s and one is gray without explanation. Render options as left-aligned rows (`heightIn(min = Dimens.touch)`, `bodyLarge`). A playlist that already has the song shows a trailing check icon and the subtitle "Already added" and is disabled.
6. Header in the workout chooser [105] puts "New playlist" below the title. Use the same header layout as the Music tab: title at start, button at end. Only stack them at font scale 1.3 or higher.
7. New playlist and Add songs screens [93, 96]: the disabled "Save" sits on a slightly different bottom offset than other screens. Use `Page(bottomBar = ...)` or the same `BottomActionBar` paddings so the button sits exactly where "Save exercise" sits on the exercise editor.

### 4.14 Export

Evidence: [80] to [87], [219].

1. "Select all" and "Clear selection" outlined buttons: keep. Disabled state from 3.6.
2. "Select period" date range picker [81] confirm button is "Select period" and is gray when nothing is chosen. Rename to "Apply".
3. Format "PDF, CSV" stays a `SegmentedControl`. "Linked CSV files in a ZIP." becomes "Several CSV files, packed in one ZIP file."

### 4.15 Library exercise list icons

Evidence: [69]. The leading exercise illustrations are small gray line art next to photoreal artwork elsewhere. This follows the approved artwork plan. Do not change the assets. Only check that each leading image is centred in a fixed `Dimens.photoThumbnail` box so row text aligns in one column (Assisted pull-up and Band pulldown currently start at a different x than Chest press [69]).

---

## 5. P4: copy

Change these strings (move them to `strings.xml` if they are hardcoded):

- "Check in before workout" in the workout menu: see 2.4.
- "Completion recorded" (Progress exercise list): "Done, no weight recorded".
- "Marked done, 5 min" (History and workout Done list): "Done, 5 min".
- "Scale 0 to 10": delete (4.7).
- "Use a separate exercise for a different machine or measurement basis.": "Use a separate exercise for each machine or way of measuring."
- "Delete this local record. If a shared copy exists, its deletion will stay pending until Health Connect access is available.": see 4.6.
- "Replace the current journal?" dialog body [77a]: shorten to three sentences: what the backup contains, that the current journal is replaced, and that reminders and Health Connect sharing are turned off until you turn them on again.
- "Backup checked. Review the replacement below." [77a]: delete. The dialog already explains it.
- "Linked CSV files in a ZIP.": see 4.14.
- "Repeat off", "Repeat all", "Repeat one": content descriptions only (2.6).
- "Pixel Demo" style names: no change, this is user input.

---

## 6. Do not change

- The workout clock, the compact top-bar clock behaviour and the "Started 10:42" line.
- The artwork assets, their selection rules and the `ArtworkWash` colours.
- Health Connect integration and its data flow.
- The set table layout, field sizes (`Dimens.setField`, `Dimens.check`) and the highlighted current row.
- The "Last time" card on the workout screen.
- Data model, Room schema and backup format. Nothing in this plan needs a migration. If you find that a change does, stop and describe it in your report instead.

---

## 7. Tests and verification

1. Update or add tests:
   - Unit: `MeasurementBasis` labels and formatting (1.2), `EnergyLevel` labels (1.6), routine target subtitle formatting (4.11).
   - Compose UI (androidTest): Today with one routine shows no "Routines" header (1.3). Empty workout shows "Add exercise" as the bottom button (2.2). After completing a set with music playing, the next set row is displayed (2.1). Measurement dialog shows "Assisted (kg of help)" and not "assistance_kg" (1.2). Privacy screen text uses the theme text colour (1.1). `DisclosureRow` toggles state description (3.1).
   - Update existing tests that look for old labels or roles: check `RedesignInteractionTest`, `ReferenceLayoutTest`, `ProgressHistoryCoverageTest`, `MusicUiTest`, `NavigationTest`, `WorkoutTimingEditorTest`, `EndToEndJournalTest` and the unit tests under `app/src/test/`. Search the test sources for every label this plan renames.
2. Run from the project root in PowerShell:
   - `.\gradlew.bat :app:assembleDebug --console=plain`
   - `.\gradlew.bat :app:testDebugUnitTest --console=plain`
   - `.\gradlew.bat :app:lintDebug --console=plain`
   - `.\gradlew.bat :app:assembleDebugAndroidTest --console=plain`
   - the connected instrumentation run, if a device is attached
3. Recapture the same screenshot set with the debug demo launcher into a **new** folder, `reports/screenshots-<date>-pixel-9-after/`, using the same file names. Never write into `reports/screenshots-2026-11-18-pixel-9/`.
4. Update `PROJECT.md` (section "UI implementation and design references") with the new shared components (`DisclosureRow`, `DialogAction`, `ScreenTabs`, `MeasurementBasis`, `EnergyLevel`, the shared exercise picker) and the rules from 3.1, 3.3 and 3.4.
5. Final report: list each numbered item with done, changed approach (with reason) or skipped (with reason), and every file you touched.
