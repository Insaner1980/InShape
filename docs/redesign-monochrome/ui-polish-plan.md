# InShape UI polish plan (review of 2026-11-17 screenshots)

Audience: the coding agent (Codex) implementing fixes in `C:\Dev\InShape`.

Source: a critical review of the 130 screenshots in `reports/screenshots-2026-11-17/`. Screenshot file names are cited in brackets, for example [01] means `01-today-active.png`.

This plan builds on `implementation-plan.md` in this folder. Its ground rules (section 0) still apply in full:

- App UI stays in English, sentence case.
- Monochrome only. Red is for destructive actions and errors.
- No rest timer. No pause button. Health Connect stays.
- Never use `·`, `•` or ` | ` as separators in any UI text. Never add a coloured vertical bar on the left edge of a card, row or panel.
- Every colour, size, radius and text style comes from `ui/theme/`. No duplicates, delete dead code, update all callers.
- Do not commit, push or open a pull request. Do not run `lc` or `sc`.

Before you use any API mentioned here (Material 3 date and time pickers, `WindowInsets.isImeVisible`, Glance preview layouts, plural resources), check the current official documentation for the versions in `gradle/libs.versions.toml`. Do not assume signatures.

## Verdict

The monochrome direction works. Inter at these weights reads well, the black hero card on Today is a strong focal point, the set table is big and thumb friendly, and the dialogs are clean and consistent. The problems are in execution, and most of them come from a handful of shared components. Fixing section 2 (system level) will clean up roughly two thirds of the visual noise across all screens before any screen-specific work.

Priorities:

- **P0**: visibly broken or contradictory. Fix first.
- **P1**: system-level token and component fixes. One change fixes many screens.
- **P2**: screen-specific improvements.
- **P3**: copy.

## 1. P0: broken or contradictory

### 1.1 Remove the floating "Saved automatically" line

Problem: `SaveStatus` is rendered in the scaffold `bottomBar` (`navigation/AppNavigation.kt` around line 259 to 276), so the text "Saved automatically" floats over scrolling content on almost every screen. It overlaps list rows [105, 107], sits 4 dp left of the content column (16 dp instead of 20 dp), and contradicts the explicit save buttons right above it ("Save workout", "Save", "Save exercise", "Save correction") [18, 30, 52b, 79a, 94a].

Fix:

1. Delete the `SaveState.Saved` and `SaveState.Saving` text output. Successful autosave is silent.
2. Show `SaveState.Failed` as a `Snackbar` in a `SnackbarHost` (monochrome: `ink` container, `onInk` text, "Retry" action when retryable), anchored above the bottom navigation or above the sticky bottom button. It must never cover content permanently.
3. Validation failures (for example an empty routine name [69]) are shown inline at the field (`isError` plus supporting text in `error` colour), not in a generic panel.

Files: `ui/components/Editors.kt` (`SaveStatus`), `navigation/AppNavigation.kt`.

### 1.2 Hyphens render with gaps ("Warm - up", "check - in", "YYYY - MM - DD")

Problem: `Type.kt` line 25 applies `fontFeatureSettings = "tnum"` to every text style. In Inter, `tnum` also switches the hyphen to a tabular width, so every hyphenated word looks broken: "Warm-up bike", "Assisted pull-up machine", "Check-ins", "Before check-in", the NHS URL in the link editor, and date hints [14, 15, 22, 26, 58, 66].

Fix: remove `tnum` from the shared `style()` helper. Apply it only where digits change in place or line up in columns:

- `displayLarge`, `displaySmall` (workout clock, Today clock, big averages)
- `InShapeType.setValue` (kg and reps cells)
- a new `InShapeType.numeric` style (`bodyLarge` plus `tnum`) for right-aligned times and durations in rows ("52 min", "10:42", "17:30")

The previous plan said "apply it to all styles for simplicity". That instruction is withdrawn.

### 1.3 Empty workout claims "Your plan is complete"

Problem: an empty workout and the past-workout editor show "All exercises done" and "Your plan is complete" although there are no exercises [06, 52a]. There is also no visible way to add an exercise; it is hidden in the overflow menu.

Fix in `feature/workout/WorkoutScreen.kt` (around lines 238 to 246):

- No items: title "No exercises yet", body "Add exercises as you go.", and a visible `OutlineButton` "Add exercise" with a plus icon in the body.
- All items done (list not empty): keep "All exercises done", title "Your plan is complete", and also show the visible "Add exercise" button.

### 1.4 "Workout done" when nothing was recorded

Problem: the finish screen with no recorded work still shows a check icon and the headline "Workout done" [07].

Fix: when nothing is recorded, hide the check icon and use the headline "Nothing recorded yet". Keep "Back to workout" as primary and "Discard workout" as the destructive text action.

### 1.5 The "not done" check on a set is nearly invisible

Problem: the unchecked set button uses the `divider` colour for its ring. Measured contrast: 1.28:1 in light theme, 1.37:1 in dark theme [10, 101]. This is the most-tapped control in the app and fails the 3:1 minimum for UI components.

Fix in `feature/workout/SetRow.kt`: unchecked ring 1.5 dp in `textSecondary` (6.5:1 light, 7.8:1 dark), check glyph in `textSecondary`. Checked state stays `ink` fill with `onInk` glyph.

### 1.6 Mixed 12-hour and 24-hour time

Problem: the app prints 24-hour times ("10:42", "17:30", "Started 19:31"), but both time pickers show 12-hour dials with AM and PM [19b, 71] and the ongoing notification says "started 7:31 PM" [93, 115].

Fix: one formatter, one rule. Follow the system setting (`DateFormat.is24HourFormat(context)`) for display, for `TimePicker` (`is24Hour`), for the notification and for the widget. Put the formatter next to `workoutTime()` in `ui/theme/ElapsedTime.kt` and route every caller through it.

### 1.7 Dates: typed ISO fields and an inconsistent picker

Problems:

- Dates are typed as text in "YYYY-MM-DD" format in Export [80, 111] and Add past workout [52], and displayed as ISO strings in Review workout [52b, 55, 55a].
- The Material date picker starts weeks on Sunday and shows "Nov 1, 2026" [51, 55b], while the app calendar starts on Monday and uses "13 Nov".
- In [55b] the picker title "Workout date" has no padding and collides with the dialog corner.
- [51] opens on 1 November instead of today or the shown month.

Fix:

1. Replace every typed date field with a read-only field or row that opens the date picker (a date range picker for Export).
2. Display dates with the app formatter (`Dates.kt`), for example "Fri 13 Nov 2026". No ISO strings in the UI.
3. Configure the picker for Monday-first weeks and the app's date style (check the current Material 3 API for the locale or first-day-of-week parameter).
4. Give the picker title the standard Material title padding.
5. Initial selection: the current value, else today.

### 1.8 Plurals

Problem: "1 links" [25], "Week of 21 Sep: 1 workouts" [40a]. The same pattern exists in `HistoryScreen.kt` line 277, `ProgressCharts.kt` line 255, `TodayScreen.kt` line 198, `ExerciseEditor.kt` line 119 and `widget/InShapeWidget.kt` line 80.

Fix: one shared plural helper (or `pluralStringResource`) used by all of them.

### 1.9 Wrong title on the new exercise screen

Problem: creating an exercise opens a screen titled "Edit exercise" [67].

Fix: "New exercise" when creating, "Edit exercise" when editing (`navigation/AppNavigation.kt` line 220).

### 1.10 "View full photo" is not full screen

Problem: the full photo opens in a small dialog, and the "Close photo" button overlaps the image [29b].

Fix: a full-screen viewer on a black background, image fitted to the screen, a 48 dp close icon at the top left, system back closes it.

### 1.11 Keyboard and inset problems in the workout screen

Problems:

- With the keyboard open, the sticky "Exercise done" button sits above the keyboard and hides the set row being edited [11b].
- Scrolled content (the progress segments) is drawn through the status bar icons [11b].
- [11b] shows a full letter keyboard for the kg cell, although `Fields.kt` requests `KeyboardType.Decimal`. Verify on a real device with Gboard. If it reproduces, find out why the option is lost for the compact cell.

Fix: hide the sticky bottom action while the IME is visible, and make sure the focused cell is scrolled into view above the keyboard. Clip or pad scrolling content below the status bar inset.

### 1.12 Health Connect controls look active when unavailable

Problem: Settings says "Health Connect is unavailable on this device" in a paragraph at the bottom, while the four switches above look enabled [70].

Fix: when unavailable, show the switches in the disabled state and put the status as one line directly under the "Health Connect" section label.

### 1.13 Chart and empty-state glitches

- The current-week bar at zero height renders as a squashed dashed squiggle [44, 45]. Give the dashed placeholder the same minimum height as the zero-week dash.
- An empty month shows "None" as total time and the stat grid changes from three columns to two [46 versus 104]. Show "0 min" and keep the three-column grid, or hide the stats row when there are no workouts.
- Progress with no data shows "0.0 on average" [47]. Show the empty-state text instead of a zero number.

### 1.14 Wrong icon on "Hide energy and notes"

Problem: the collapse action uses a plus icon [31, 33, 35]. Use a chevron that points down when collapsed and up when expanded, on both "Add energy and notes" and "Hide energy and notes".

## 2. P1: system-level fixes (tokens and shared components)

### 2.1 Text actions are indented 12 dp

Problem: `TextAction` wraps Material `TextButton`, which has 12 dp horizontal content padding. Every left-aligned text action starts 12 dp to the right of the content column: "View weekly counts", "All exercises", "Exercise information", "I don't know the times", "Use recorded times", "Edit routine details", "Remove setting", "Remove link", "Discard workout", "Allow share completed workouts", "View data values" [05, 07, 19, 22, 24, 40, 60b, 79a]. In headers, "See all" ends 12 dp before the right edge, so it does not line up with "2 of 3" and "7 exercises" below it [01].

Fix in `ui/components/Buttons.kt`: give `TextAction` zero horizontal content padding by default (keep the 48 dp minimum height), so its text aligns with the column on either side. Then review each use. Many of these are better as a list row with a chevron (for example "Exercise information", "All exercises") or should move into an overflow menu (see section 3).

### 2.2 Buttons of different heights side by side

Problem: `PrimaryButton` has a 56 dp minimum height, `OutlineButton` 48 dp. When they sit next to each other the pair looks broken: "Save PDF" and "Share PDF" [82], "Save CSV" and "Share CSV" [85], "Prepare PDF" and "Prepare CSV" [81].

Fix: add a size parameter to both buttons. Large (56 dp) is only for the full-width bottom call to action. Medium (48 dp) is for every inline button, filled or outlined. Two buttons in one row always use the same size.

### 2.3 Section labels and headings

Problems:

- Small group labels ("Health Connect", "Workouts", "Appearance", "Your data", "Location", "Measurement", "Machine settings" in the editor, "Add body area", "Comparison", "Links", "Notes", "Alternatives") start about 4 dp right of the content column [24, 66, 70, 112].
- Sibling sections use different heading sizes. In exercise details, "History" is clearly larger than "My instructions" and "Alternatives" [23]. "Workouts per week" and "New record in this period" use a heading size in grey `textSecondary` [40, 43].

Fix: two tiers only.

- Section title: `titleMedium`, `text` colour, flush left, 12 dp to its content, 32 dp above it.
- Group label (above a field or a small group): `labelSmall`, `textSecondary`, flush left, 8 dp to its content.

Check `SectionLabel` and `GroupLabel` in `ui/components/Controls.kt` for the stray start padding and remove it.

### 2.4 One header pattern per screen type

Problem: there are at least five header layouts.

- Tab roots: big title with an outlined action on the same row ("History", "Library").
- Settings: back arrow row, then a big title below [70].
- Forms: back arrow with a smaller inline title ("Edit exercise", "Export your journal", "Workout details", "Review workout").
- Object screens: back arrow and actions, big object name below (exercise details, finish).
- Health privacy: no top bar at all, a small title under the status bar and a compact "Close" button at the bottom [74].

The back arrow also sits at two different heights (compare [70] and [20]).

Fix: three patterns, one shared component each.

1. Tab root: `headlineMedium` title left, at most one action right, same top offset on all three tabs.
2. Pushed screen: 64 dp top bar with back (48 dp touch target), inline `titleLarge` title, actions right. Use for Settings, Export, editors, Review workout, Health privacy (remove its bottom "Close" button).
3. Object screen: 64 dp top bar with back and actions but no title, then the object name as `headlineMedium` in the body. Use for exercise details, workout details and the finish summary.

### 2.5 Text contrast

Measured: `textTertiary` #8E8E8E on white is 3.28:1, and #6F6F6F on the dark background is 3.92:1. Both fail the 4.5:1 minimum for text. It is used for unselected bottom navigation labels and other small text [01, 104].

Fix in `ui/theme/InShapeColors.kt`: light `textTertiary` #707070 (4.95:1 on white, 4.5:1 on `surface`), dark `textTertiary` #858585 (5.33:1 on background, 4.72:1 on `surface`). Use `divider` only for hairlines, never for anything the user has to find or tap.

### 2.6 Switch off state

Problem: in dark theme an off switch is almost invisible [110, 111]. In light theme there are two different off looks: an outlined track with a grey thumb [70] and a pale filled track with a white thumb [78].

Fix: one `SwitchDefaults.colors(...)` in a shared component. Off: track `background`, 1.5 dp border and thumb in `textSecondary`. On: track `ink`, thumb `onInk`. Disabled: the same at reduced alpha. Use checkboxes, not switches, for selecting items in a list (Export workout selection [80, 81]).

### 2.7 Sticky bottom buttons

Problem: content scrolls under the bottom primary button with nothing behind it. Cards are cut mid-border [30, 33, 36], and stray dots show between the list and the button [10, 11a].

Fix: one `BottomActionBar` component. `background` colour behind the button, 1 dp `divider` top border that only appears when content scrolls underneath, and bottom content padding on the list equal to the bar height so the last item can scroll fully into view.

### 2.8 Section header rhythm on Today

Problem: "This week" sits 12 dp above its content, "Routines" about 32 dp above its first row, because the 48 dp "See all" button and the row padding add up [01].

Fix: one `SectionHeader` component (title left, optional trailing text action flush right, fixed 12 dp to content). Use it on Today, History, Progress and exercise details.

### 2.9 Row affordances

Problem: tappable rows are inconsistent. Library routine rows have chevrons, Today routine rows do not [01, 106]. In the add-exercise sheet every row has a chevron although tapping adds the exercise instead of navigating [15, 63]. In the exercise library some rows have a chevron and others show "25 kg Wed" instead [66].

Rule: a chevron means "opens another screen". Rows that select or add have no chevron (or a plus icon). Apply it everywhere. In the exercise library, show the last load on the right when known and no chevron.

### 2.10 Small captions next to big numbers

Problem: captions are top-aligned beside big numbers, so they float: "1.2 on average" [40], "27.5 kg was 20 on 25 Sep" [41].

Fix: align the caption to the number's baseline (`Modifier.alignByBaseline()`), or put it on its own line below the number. Add the unit: "was 20 kg on 25 Sep".

### 2.11 Structured values instead of comma chains

Problems:

- Machine settings in the workout are one run-on line: "Seat height 4, Thigh pad 2, Handle Wide bar, Backrest None" [10].
- Observations read "Pain, mild, Stiffness, medium" [30] and "lower back: pain, medium, stiffness, strong" in lower case [58]. `ui/theme/ObservationText.kt` builds these strings.

Fix:

- Machine settings in the workout: small outlined pills, one per setting ("Seat 4", "Thigh pad 2", "Wide bar"). Hide settings whose value is "None".
- Observations: area name in `bodyLarge`, then pills "Pain mild" and "Stiffness medium". Area names capitalised. One formatter used by check-in, workout details and progress.

### 2.12 Key and value rows

Problem: the same label and value row looks different on each screen. In exercise details the label is 13 sp and the value 15 sp [20]. In Settings both are 15 sp. In Fix time the editable values are black and the computed duration grey [19], which is good but not explained anywhere else.

Fix: one `KeyValueRow` component. Label `bodyLarge` `text`, value `bodyLarge` with `tnum` when numeric. Editable values in `text` colour, read-only computed values in `textSecondary`.

## 3. P2: screen by screen

### 3.1 Today [01 to 05, 100]

- Empty-state card says the same thing three times: "Ready when you are", "Start a workout", "Start workout" [03]. Drop the eyebrow. Title "Start a workout", body "Pick a routine below or add exercises as you go.", button "Start".
- Routine rows: add a chevron (they open the routine) so they match Library.
- Dark theme: the in-progress card turns into a near-white slab on black [100]. It is the brightest thing on screen at night. Use `surface` with a 1 dp `outline` border in dark theme and keep the white primary button as the emphasis.

### 3.2 Workout [10 to 17, 101]

- The clock decision stands (big running clock at the top). When the big clock scrolls out of view, show a compact clock in the top bar so it stays visible while the user logs sets.
- The exercise overflow icon is about 8 dp lower than the exercise name [10]. Centre it on the title row.
- Set table headers are left-aligned ("kg", "Reps") while values are centred in their cells [10]. Centre each header over its column.
- The exercise menu has eight items [12], which works against the "fewer buttons" goal. Remove "Move up" and "Move down" (reordering belongs to the plan list), and keep "Exercise details", "Add note", "Swap exercise", "Do later", "Skip today" and "Remove exercise".
- The set menu offers "Edit set 1" although the cells are already editable [11]. Keep only "Remove set".
- Workout menu item "Before check-in" [11a] is unclear. Rename to "Check in before workout".
- "This movement felt uncomfortable" [14b] overlaps with the pain check-in. Keep it only if it is used anywhere downstream. Otherwise remove it.

### 3.3 Finish summary [18, 18a, 102]

- "Share to Health Connect Off" looks like a static value [18]. Make it a switch row bound to the setting, or a chevron row to Settings with the subtitle "Off".
- After 1.1 the primary "Save workout" no longer contradicts anything. Keep it.

### 3.4 Check-in [30 to 37, 109]

- The body map is 72 by 156 dp [30], so each body area is only a few dp tall. Make it the main element: full content width, about 300 dp tall, centred, "Front" and "Back" segmented control full width above it, helper text below. Every area needs a 48 dp hit target, even if the visible shape is smaller.
- Dark theme: the figure is `surfaceStrong` on near black and barely visible [109]. Use a 1.5 dp `textSecondary` outline.
- Three free-text fields ("General feelings (optional)", "Workout note", "Note for this check-in") [35]. Keep one: "Note".
- Comparison line "Before the workout: pain, medium, stiffness, strong" [33] becomes "Before" plus the same pills as 2.11.
- Area picker [34]: rename "Left forearm wrist" to "Left wrist and forearm" (same for right). Consider a bottom sheet grouped by upper body and lower body instead of a 17-item radio dialog.

### 3.5 History and workout details [50 to 59, 104]

- Workout details rows have a third line "Done" under each exercise [54, 57]. Remove it for done items. Show a pill only for "Skipped" or "Incomplete".
- "Fix time or note" row plus "Edit recorded sets" outlined button [54]: move both into the top bar overflow menu ("Fix time", "Edit note", "Edit sets"). "Repeat workout" stays the only primary.
- Review workout [52b, 55]: remove the developer copy ("0 exercises with recorded work", "0 skipped, 0 incomplete", "Only explicitly completed sets and activities count as performed work"). Reuse the Fix time pattern from [19] (Started and Finished rows with pickers, computed duration) instead of four separate date and time rows. Make the duration field full width.
- Add past workout dialog [52]: the two fields touch with no gap. Use 12 dp between fields, and the date field opens the picker (1.7).

### 3.6 Progress [40 to 47, 105]

- Chart encoding is unexplained: one bar is black and the rest grey, the dashed line at the top has no label [40]. Label the goal line directly ("Goal 3" at its right end, remove it from the header) and add a one-line legend, or colour all weeks that met the goal in `ink`.
- Flat sparklines look like underlines or dividers [40b]. Draw a sparkline only when there are at least two different values.
- "+7.5 kg" pill is top-aligned, not centred on the exercise selector row [41]. Centre it.
- "View data values" lists each value as a bold text action with large gaps [42, 43b]. Show them as compact `KeyValueRow`s (date left, value right).
- "New record in this period" heading plus a "New record" pill says it twice [43]. Keep the heading only.

### 3.7 Library: routines [60 to 65, 68, 68b, 69]

- Editing a routine expands it inline in the list, with "Add exercise", "Start routine", "Edit routine details" and "Save routine" in the middle of the list [60b]. The list jumps and the other routines are pushed down. Open a routine as a pushed screen instead: top bar with back, title and an overflow menu (Edit details, Duplicate, Remove), the exercise list with drag handles, "Add exercise" as a text action under the list, and "Start routine" as the bottom primary. If edits autosave, remove "Save routine". If the editor is draft-based, keep it and say so in the code.
- Each exercise row has both a drag handle and "Move up" and "Move down" in its menu [61]. Keep the drag handle; remove the menu items.
- "Duplicate routine" and "Remove routine" live inside the details form dialog [64]. Move them to the overflow menu.
- "Start routine" is enabled for a routine with 0 exercises [68b]. Disable it.

### 3.8 Library: exercises and exercise details [20 to 29c, 66, 107, 108]

- Filters take about 260 dp before the first result [66]: search, star, four chips, a "Location" label and a full-width dropdown. Move location into the chip row as one chip ("All locations" with a down chevron) and remove the label.
- Initials avatars ("AP", "BP", "CP") add noise without information [66]. Show the machine photo thumbnail when one exists, otherwise nothing.
- "Add photo" button touches its caption with no gap [20]. Use 12 dp.
- The photo is narrower than the content column [29]. Make it full content width with the card radius.
- Photo actions are four separate buttons ("Add photo", "View full photo", "Replace photo", "Remove photo") with misaligned thumbnail and button tops [29]. Tap the photo to view it full screen (1.10). Put "Replace" and "Remove" in an overflow on the photo. Show additional photos as a thumbnail row with a plus tile at the end.
- Muscle and type chips ("Back, arms", "Machine") look like filled buttons but are not interactive [20]. Use small outlined pills (28 dp) or plain `textSecondary` text.
- Alternatives are large outlined buttons [22, 23], while the swap sheet shows the same data as rows [13]. Use rows with chevrons.
- "History" title and "See chart" action are not on the same baseline [23]. Use `SectionHeader` (2.8).
- Exercise editor: each machine setting takes two full-width fields plus a "Remove setting" text action [24, 25]. Use one row per setting: label field, value field, and a 48 dp remove icon.
- Alternatives checkboxes are about 8 dp lower than their labels [27]. Use a row with `verticalAlignment = CenterVertically` and a 52 dp minimum height.

### 3.9 Settings [70 to 79a, 110, 112]

- Grouping: "Name" sits under "Workouts" [70]. Order: Profile (Name, Weekly goal), Reminders, Health Connect, Appearance, Your data, Privacy.
- "Name" is an inline text field disguised as a grey value [71a, 71b]. Make it a row that opens a small dialog with a text field.
- Reminder days are seven chips wrapping to two rows [71a, 112]. Use seven equal circular toggles in one row (reuse the week-dot component, 40 dp).
- "Notification settings" and "Activate these reminders" are two stacked outlined buttons of different widths [71a, 73]. Show "Paused after restore" as the switch row subtitle with a "Resume" text action. Show "Notification settings" only when notification permission is denied.
- Three explanatory paragraphs in the Health Connect section and one under Your data [70, 73]. Move them to the Privacy screen and keep at most one subtitle line per row.
- "Backup saved successfully." is a grey line easy to miss [76b]. Use a snackbar.
- Health privacy screen [74] is a wall of text. Use the pushed-screen header (2.4) and split the text into three short headed sections: "What stays on this phone", "What is shared with Health Connect", "Removing data".

### 3.10 Export [80 to 85, 111]

- Typed date fields become a date range picker (1.7).
- Workout selection uses switches. Use checkboxes (2.6).
- Cards nested inside cards (the date card, the options card, the result card [81, 82]) with large inner padding. Use flat sections with section titles instead.
- The flow has two steps per format ("Prepare PDF", then "Save PDF" or "Share PDF"). Simplify to a format choice (PDF or CSV segmented control), then one primary "Save" and one secondary "Share" that both prepare the file on demand.
- "Cancelled. No file was saved or restored." [82] mentions restore on an export screen. Use "Export cancelled." as a snackbar.

### 3.11 Widget and notification [90 to 93, 114, 115]

- The widget picker shows only the app icon as the preview [90]. Provide a real preview layout (check the current Glance documentation for preview support).
- Idle widget content is crammed into the top half with a large empty area below [91]. Centre the content vertically or size the widget to its content. Make "Start" a 48 dp touch target.
- Active widget shows only the clock [92, 114]. Add the routine name and the "4 of 8" progress line, same hierarchy as the Today card. Tapping opens the workout.
- Notification body "Workout, started 7:31 PM" [93] becomes the routine name (or "Workout") and "Started 19:31" in the system time format (1.6). Verify that the expanded notification has the "Finish" action the earlier plan required; the screenshots only show the collapsed state.

## 4. P3: copy changes

- "Your plan is complete" on an empty workout: see 1.3.
- "Workout done" with nothing recorded: "Nothing recorded yet" (1.4).
- New exercise screen title: "New exercise" (1.9).
- "Before check-in" menu item: "Check in before workout".
- "HTTP or HTTPS address" [26]: "Web address".
- "Left forearm wrist" and "Right forearm wrist": "Left wrist and forearm", "Right wrist and forearm".
- "was 20 on 25 Sep": "was 20 kg on 25 Sep".
- "None" as total time: "0 min".
- "Cancelled. No file was saved or restored.": "Export cancelled."
- "Recorded duration in minutes (optional)" plus "Leave duration blank when it is unknown. It can differ from the start-to-end interval." [52b, 55a]: field label "Duration (min)", supporting text "Leave empty if you don't know."
- "Allow share completed workouts" [79a]: "Allow access".

## 5. Verification

1. Build and run the existing unit and UI tests listed in section 12 of `implementation-plan.md`.
2. Retake the full screenshot set in light and dark theme with the same file names into a new folder `reports/screenshots-<date>/` and compare against `reports/screenshots-2026-11-17/`.
3. Check specifically:
   - No "Saved automatically" text anywhere. A forced save error shows a snackbar.
   - "Warm-up bike" and "Check-ins" render with normal hyphens. The clock and set values still do not jitter.
   - Every left-aligned text action, section label and list row starts on the same 20 dp column.
   - Side-by-side buttons share one height.
   - Unchecked set ring, off switches and unselected nav labels pass contrast (3:1 for controls, 4.5:1 for text) in both themes.
   - Time pickers, notification, widget and in-app times all use the same 12 or 24 hour format as the system.
   - No typed date fields remain. The date picker starts on Monday.
   - With the keyboard open in a set cell, the edited row stays visible and nothing draws under the status bar.
   - Plurals read correctly for 0, 1 and 2.
4. Search every string you changed for `·`, `•` and ` | `.
