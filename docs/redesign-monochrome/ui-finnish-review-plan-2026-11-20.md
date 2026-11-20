# InShape UI review after the Finnish switch (screenshots of 2026-11-20)

Audience: the coding agent (Codex) implementing changes in `C:\Dev\InShape`.

Source: a review of the screenshots in `reports/screenshots-2026-11-20-pixel-9-fi/` (Pixel 9, Finnish UI, demo data) and the earlier English set `reports/screenshots-2026-11-20-pixel-9/`. Screenshots are cited in brackets by their file-name prefix, for example [39a] means `39a-ohjelman-laitevalinta.png` in the Finnish folder, and [L:FinishSummary] means `layout/finnish-default/FinishSummary.png`. File paths are relative to `app/src/main/java/com/emmah/inshape/` unless they start with `app/`, `docs/` or `reports/`. Line numbers are from the working tree on 2026-11-20 and can drift; search for the quoted code if a line does not match.

The Finnish switch landed well: no mixed languages, Finnish dates, decimal commas and ma–su weekday labels everywhere in the app's own components. This plan fixes two defects the switch exposed and the structural problems from the earlier review that are still open: too much explanatory copy, AI buttons inside the content, ragged button stacks and the program screens.

Implement the sections in order. Each one is self-contained. Do not change anything that is not listed here.

## 0. Ground rules

All ground rules in section 0 of `ui-simplification-plan-2026-11-19.md` still apply. In short:

- Keep all artwork and all `ArtworkWash` gradients unchanged, including the sage and petrol tones and the theme preview tiles in Settings.
- Monochrome foreground; `error` only for destructive actions and errors.
- Never use `·`, `•` or ` | ` as separators in any UI text, string, content description, export, widget or test. Separate values with layout (opposite ends of a row, value and label on separate lines, different weight or colour).
- No coloured vertical bar on the left edge of a card, row or panel.
- Every colour, size, spacing value and text style comes from `ui/theme/`. Add a token if one is missing; do not hardcode `dp`, `sp` or `Color` in feature files.
- All user-visible text lives in `app/src/main/res/values/strings.xml` in Finnish. Delete string resources that become unused.
- **New rule: do not add any helper, explanation or disclosure text.** No sentences that describe what a screen does, what data is sent where, or what happens after an action. If a string in this plan is removed, do not replace it with a shorter explanation unless the plan gives the exact new text.
- Delete code that becomes unused in the same change and update every caller.
- Do not change the Room schema, the backup format or CSV/PDF keys.
- Do not commit, push or open a pull request. Do not run `lc` or `sc`. Do not modify anything in `reports/` except creating the new screenshot folder in section 8.
- Check the current documentation for every Compose API you touch here (`DropdownMenu` offset, `SliderDefaults.Track` parameters, `FlowRow`, `TopAppBar` actions, Glance text) against the versions pinned in `gradle/libs.versions.toml`. Do not assume signatures from memory.
- After each section, `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` must pass. Update instrumented tests in `app/src/androidTest/` and `app/src/sharedTest/` that reference changed UI. Tests use string resources, not hardcoded text.

---

## 1. Defects

### 1.1 Pain and stiffness levels break a word in half

Evidence: [L:13f-finish-pain-area-sheet]. In the body-area sheet ("Vasen polvi") both rows "Kipu" and "Jäykkyys" show four options `Ei / Lievä / Kohtalainen / Voimakas`. "Kohtalainen" wraps as "Kohtalaine" + "n".

Cause: `ui/components/SelectionControls.kt` `SegmentedControl` (line 145) puts all options on one row (`maxItemsInEachRow = options.size`) unless font scale is at least 1.3, and the label `Text` may wrap anywhere.

Fix:

1. In `SegmentedControl`, the label `Text` must never wrap inside a word: set `maxLines = 1` and `softWrap = false`.
2. Choose the number of items per row from the available width, not only from font scale. Measure the widest label with `rememberTextMeasurer()` in the label style. If `options.size` labels at that width plus padding do not fit in one row, use 2 items per row; if two do not fit, use 1. Keep the current font-scale rule as an additional condition. Use `BoxWithConstraints` for the width.
3. Do not shorten or rename the level labels.
4. Verify on [13f] that the sheet shows `Ei` `Lievä` on the first row and `Kohtalainen` `Voimakas` on the second, and that all other segmented controls in the app (finish effort, check-in Ei/Kyllä, Edestä/Takaa, Vireys, progress range, export PDF/CSV) still fit on one row at font scale 1.15.

### 1.2 Assistant settings page is a dead end without credentials

Evidence: [08]. The page shows only "OpenAI" and "Kokeile avustajaa". Nothing can be tapped.

Cause: `feature/assistant/AssistantSettings.kt` line 75 to 78. When `credentials == null` the page shows `R.string.test_assistant` ("Kokeile avustajaa") and returns. The text promises an action that does not exist.

Fix: replace the string with `assistant_unavailable` = "Avainta ei voi tallentaa tässä asennuksessa." in `textSecondary`, delete `test_assistant`, and keep the early return. Nothing else on the page changes.

### 1.3 Header actions too close to the right edge

Evidence: [10] "Lopeta" ends about 12 dp from the right edge, [13] "Ohita" about 4 dp. Every other right-aligned element sits on the 20 dp screen padding, for example "Muokkaa" in [31].

Cause: `ui/components/Controls.kt` `PushedHeader` adds `Dimens.headerActionEnd` only when `endInset = true`. Only `feature/library/ExerciseScreen.kt:147` passes it. `feature/workout/WorkoutScreen.kt:508` uses `Modifier.padding(end = Dimens.innerSmall)` instead, and `feature/checkin/CheckInScreen.kt:133` has neither.

Fix:

1. In `PushedHeader`, add the end inset automatically when the last action is a text action. Simplest: add a parameter `trailingText: Boolean = false` and rename nothing else; or make `endInset` default to `true` and pass `false` only where the last action is an `IconButton` (its own 12 dp padding already aligns the glyph). Pick one approach and apply it to every `PushedHeader` call site.
2. Remove the ad-hoc `Modifier.padding(end = Dimens.innerSmall)` from the "Lopeta" action in `WorkoutScreen.kt:508`.
3. Popup menus from `ui/components/Menus.kt` `AppMenu` open about 4 dp from the right edge [45], [10a]. Give `AppMenu` an `offset` so the menu's right edge lines up with `Dimens.screenPadding`. Check how `DropdownMenu` computes its horizontal position in the pinned Material3 version before choosing the value.

### 1.4 Now playing seek bar thumb floats apart from the track

Evidence: [46]. At position 0:00 the dot sits left of the track with a visible gap.

Cause: `feature/music/PlayerScreens.kt` around line 267. `SliderDefaults.Track` draws a gap around the thumb by default (thumb track gap), and the custom thumb is a small dot, so the gap shows as empty space.

Fix: pass the track parameter that sets the thumb gap to zero (in the pinned Material3 version it is `thumbTrackGapSize`; verify the name) and keep `drawStopIndicator = null`. The dot must sit on the track at every position.

### 1.5 Workout clock is clipped at 320 dp and 200 % font

Evidence: `reports/screenshots-2026-11-20-pixel-9-fi/layout/finnish-320-large/layout-results.txt`: "ActiveWorkoutPartiallyDone: clipped text: 32:15".

Fix in `WorkoutHeader` (`feature/workout/WorkoutScreen.kt` around line 471): when the clock and the `4 / 8` counter do not fit next to the actions, the counter wraps to its own line (the `FlowRow` already allows this) and the clock keeps one line without clipping. If the top app bar height is the cause, use the larger header height (`Dimens.topBarLarge`) whenever the title content wraps, not only at font scale 1.3. Re-run the layout test for all four layout folders; all must report OK.

---

## 2. Copy cuts

Remove the following texts. Delete the string resources and every call site. Do not replace them with anything unless a replacement is given.

### 2.1 OpenAI disclosure paragraphs

Remove the heading `share_openai_heading` ("Jaetaan OpenAI:lle") and the paragraph under it in all places:

- `feature/assistant/AssistantImportScreen.kt:198-199` (`image_import_disclosure`) [37], [38]. This also fixes [38], where the text talks about pasted text on a screen that has no text field.
- `feature/assistant/AssistantReview.kt:164-165` (`assistant_review_disclosure`) [10b].
- `feature/assistant/RoutineProposal.kt:152-153` (`routine_openai_disclosure`).
- `feature/music/PlaylistAssistantScreen.kt:80-81` (`playlist_assistant_disclosure`) [44].
- Also remove `transcription_disclosure` and `narration_disclosure` wherever they are shown.

### 2.2 Other explanations and status lines

- `feature/assistant/JournalEvidence.kt:218` `journal_answers` ("Vastaukset perustuvat tämän laitteen päiväkirjaan.") [20b].
- `choose_routine_exercises` ("Valitse treenin liikkeet suoritusjärjestyksessä.") in the routine builder [36].
- Privacy page [09b]: keep the page and its three section titles, but replace the three bodies with one line each:
  - `health_privacy_local_body` = "Tiedot ovat vain tässä puhelimessa."
  - `health_privacy_sharing_body` = "Valmiit treenit jaetaan Health Connectiin."
  - `health_privacy_removal_body` = "Treenin poisto poistaa sen myös Health Connectista."
- `reminders_paused` ("Tauolla palautuksen jälkeen") [06]: show no subtitle when reminders are off; delete the string.
- `save_failed` [05] = "Tallennus epäonnistui." (drop the second sentence).
- Assistant sheet context line [10b]: remove the line built at `feature/assistant/AssistantReview.kt:128` (`listOf(item.row.name, item.row.equipmentName, item.row.basis)…`). It shows "Ylätalja, Ylätalja, kg".
- PDF [51]: remove the lines `duration_not_recorded` ("Kestoa ei kirjattu") and `export_no_interval` ("Alku- ja loppuaikaa ei kirjattu") from the PDF and CSV; show nothing when the value is missing.

### 2.3 Duplicate titles

When a pushed screen has a top-bar title, do not repeat the same or almost the same title as a large heading below it. Keep the top-bar title and delete the large heading:

- Tuonti + "Tuo ohjelma" / "Tuo laitekortti" [37], [38]: top-bar title becomes "Tuo ohjelma" or "Tuo laitekortti"; delete the heading in `AssistantImportScreen.kt:144`.
- Päiväkirja + "Päiväkirjan kysymykset" [20b]: top-bar title "Kysy päiväkirjasta"; delete the heading.
- Soittolista-avustaja twice [44]: delete the heading in `PlaylistAssistantScreen.kt:75`. When editing an existing playlist, the top-bar title is "Muokkaa soittolistaa".
- Rutiini + "Koosta rutiini" / "Mukauta treeniä": top-bar title is the action name; delete the heading in `RoutineProposal.kt:112`.
- Ohjelma + program name [39]: keep the program name as the large heading, and make the top bar title-less (back arrow only), like the routine detail [30].
- Valitse laite + program name + "A  Koko vartalo laitteilla" [39a]: keep the top-bar title "Valitse laite", show the day as one line "A: Koko vartalo laitteilla" in `titleMedium`, and drop the program-name heading.
- Month picker in History [20c]: dialog title "Valitse kuukausi", confirm button "Valmis".

### 2.4 Today empty state and widget

- [50] The empty Today card shows the title "Aloita treeni" and the button "Aloita". Remove the title; keep the button with the text "Aloita treeni". The week header shows "0 treeniä" while the normal state shows "0/3"; when there is no weekly goal show nothing on the right.
- Widget [52]: replace `no_active_workout` ("Ei käynnissä olevaa treeniä") with the next routine name ("Koko vartalo A") when one exists, and with "Tyhjä treeni" otherwise. Add the ma–su labels under the week circles if they fit at the widget's minimum size; otherwise leave them out.

---

## 3. AI actions out of the content flow

The AI features stay. Their entry points move out of the main content so the core screens start with their own content. Use the existing components only (`IconButton` in `PushedHeader` or the screen header, `AppMenu` + `MenuAction`).

- Active workout [10]: remove the outlined "Treeniavustaja" button (`feature/workout/WorkoutScreen.kt:292`). Add "Treeniavustaja" as the first item in the exercise's ⋮ menu [10a], with a microphone icon.
- History [L:HistoryNovember], [L:ProgressWithHistory]: remove the outlined "Kysy päiväkirjasta" button from both tabs (`feature/history/HistoryScreen.kt:189`). Add an icon button left of the existing "+" in the History header that opens the journal questions screen. Content description "Kysy päiväkirjasta".
- Past workout detail: move "Kysy tästä treenistä" into the existing ⋮ menu.
- Library, Rutiinit tab [32]: remove the outlined "Koosta rutiini" and "Tuo ohjelma" buttons (`feature/library/LibraryScreen.kt:133-134`). The header "+" opens a menu with "Uusi rutiini", "Koosta rutiini" and "Tuo ohjelma".
- Library, Liikkeet tab [33]: remove the outlined "Tuo laitekortti" button (`LibraryScreen.kt:139`). The header "+" opens a menu with "Uusi liike" and "Tuo laitekortti".
- Music, Soittolistat tab [40]: remove the outlined "Soittolista-avustaja" button (`feature/music/MusicScreens.kt:174`). The header "+" opens a menu with "Uusi soittolista" and "Soittolista avustajalla". This also fixes the button touching the tab indicator.
- Playlist detail [41]: remove the outlined "Muokkaa avustajalla" button (`MusicScreens.kt:321`) and add it to the ⋮ menu [45] after "Muokkaa soittolistaa". "Toista" and "Sekoita" become the first controls under the header card.

---

## 4. Buttons

### 4.1 One button style per role

- A screen has at most one `PrimaryButton`. When the screen has a main action, it sits in the `Page` `bottomBar` (sticky), like "Liike tehty" [10] and "Tallenna treeni" [12].
- Apply this to: "Ota ohjelma käyttöön" on the program page [39] (currently an `OutlineButton` in the content at `feature/program/ProgramScreen.kt:58`), "Ota ohjelma käyttöön" and "Aloita treeni" in program setup [39a] (`feature/program/ProgramSetupScreen.kt:86` and `:93`), "Tuo tarkistettavaksi" [37], [38], "Esikatsele soittolistaa" [44] and the review button in the routine builder [36].
- Secondary actions that are not navigation use `OutlineButton` with `Modifier.fillMaxWidth()` when stacked vertically. Never stack hug-content buttons of different widths. Two short side-by-side buttons (for example "Valitse kuvat" and "Ota kuva" [37]) each take `Modifier.weight(1f)` in the row.

### 4.2 Workout assistant sheet

Evidence: [10b]. Buttons "Äänitä", "Lähetä tarkistettavaksi" and "Takaisin kirjaukseen" are three different widths. In the review state the sheet has four buttons in three rows.

- Input state: the microphone becomes a trailing icon inside the text field ("Äänitä" as content description). "Lähetä tarkistettavaksi" is the only button, full width, primary. Remove "Takaisin kirjaukseen"; closing the sheet returns to manual entry.
- Review state: one full-width primary "Tallenna" and one full-width outline "Muokkaa pyyntöä". Remove "Hylkää luonnos" and "Takaisin kirjaukseen"; dismissing the sheet discards the draft.

---

## 5. Active workout screen

### 5.1 Machine setting pills

Evidence: [10]. Pills read "Kahva Leveä tanko" and "Selkänoja Ei mitään": label and value run together, and a setting with value "Ei mitään" is shown at all. With four pills they take three rows.

Fix in `WorkoutSetup` (`feature/workout/WorkoutScreen.kt` around line 584) and `workoutSetupLabels`:

1. Skip settings whose value is empty or means none ("Ei mitään", "Ei", "-").
2. Inside each pill show the label in `textSecondary` and the value in `text` with `FontWeight.SemiBold`, separated by a space; do not join them into one plain string.
3. Keep the pills on as few rows as possible; no other layout change.

### 5.2 Section names and bottom actions

- The done section header uses `R.string.done` ("Valmis") at `WorkoutScreen.kt:786`, the same word as dialog confirm buttons. Add `workout_done_section` = "Tehdyt" and use it there.
- The rows at `WorkoutScreen.kt:334-342` use four different styles (chevron row, plain row, plain row, red text action). Make them one list: every item is a `ListRow` with a leading icon, no chevrons except for rows that navigate to another screen ("Kirjaa vointi", "Mukauta treeniä"), and "Hylkää treeni" is the last row with the `error` colour on icon and text, left-aligned like the others.

---

## 6. Program screens

### 6.1 Program page [39]

- Each exercise row keeps its title and subtitle ("2 × 10–15 toistoa, palautus 120 s").
- The instruction (`slot.instruction`, `ProgramScreen.kt:70`) moves inside the same row as a third line in `bodyMedium`, `textSecondary`, `maxLines = 3`. It is no longer a separate list item between dividers.
- Alternatives (`ProgramScreen.kt:71-75`) are shown inside the main row as one line "Vaihtoehto: Tuettu kyykky penkille" in `bodyMedium`, `textSecondary`, tappable to open its guide. They are no longer separate rows that look like exercises.
- "Vuorottelu: A" (`program_rotation`): remove when the program has only one day; otherwise keep.
- The long guidance block at the end (`ProgramGuidance`, warm-up, cool-down, eight-week plan, safety note and sources) moves behind one collapsible row "Lisätietoa" that is closed by default. Do not shorten or edit its content.

### 6.2 Equipment choice [39a]

Evidence: [39a]. The exercise name is a heading and under it an outlined "Valitse laite" button, six times in a row. After choosing, the button repeats the name ("Jalkaprässi, Sali").

Fix `ProgramEquipmentChoice` (`feature/program/ProgramSetupScreen.kt` around line 118):

- Each exercise is one `ListRow`: title = exercise name, trailing value = chosen equipment name (without repeating the exercise name) or "Valitse" in `textSecondary` when nothing is chosen, chevron. Tapping opens the existing choice list in a bottom sheet instead of expanding inline.
- When the library has exactly one eligible exercise for a slot, preselect it.
- "Ota ohjelma käyttöön" / "Aloita treeni" sticky at the bottom (see 4.1).

### 6.3 Program workout

Evidence: the English set `reports/screenshots-2026-11-20-pixel-9/42-program-next-*.png` (the Finnish set has no screenshot of this screen; add one in section 8).


- The week line "Viikko 1/8" stays. Show the phase text as two lines: goal, then sets and effort together in one sentence. Replace the visible abbreviation "RIR 3–4" with "3–4 toistoa varaa" everywhere in the UI (keep the data unchanged).
- "Siirry viikkoon 2" moves into the screen's ⋮ menu.

---

## 7. Small fixes

- Check-in rows [L:CheckInAfter]: "Kipu: lievä, Jäykkyys: kohtalainen" uses inconsistent capitals. Show "Kipu lievä" and "jäykkyys kohtalainen" on one line with the labels in `textSecondary` and the values in `text`, without colons.
- Progress [L:ProgressWithHistory]: "Paras tähän asti, nousua arvosta 20 kg (25.9.)" becomes "Ennätys" when the current value is the best, and the comparison line becomes "+7,5 kg 25.9. lähtien". Keep the existing "+7,5 kg" pill; do not show the same delta twice.
- PDF header and footer [51] (`transfer/PdfExporter.kt:162-170`): `export_page` and `export_footer` join two values with spaces. Draw the workout count on the left and "Sivu 1" right-aligned on the same line; draw the footer as two lines or left and right ends of the line. Do not use separators.
- Today running card [01]: "Liikkeitä tehty 4/8" and "Alkoi 10:42" use different sizes. Use the same text style for both.

---

## 8. Verification

1. Build and unit tests pass after every section.
2. Extend the layout test so it also opens the body-area sheet [13f], the workout assistant sheet [10b], the program setup [39a] and the History month picker [20c], in all four layout configurations (default, 320 dp, 200 %, 320 dp + 200 %). All must report OK.
3. Take a new screenshot set into `reports/screenshots-2026-11-20-pixel-9-fi-after/` with the same file names as the Finnish set, plus the four sheets above and the program workout screen (section 6.3). Include dark mode for [10], [12], [33], [39] and [46].
4. In the report, list any item of this plan you could not implement and why.
