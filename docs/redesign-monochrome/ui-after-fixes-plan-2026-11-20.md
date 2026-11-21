# InShape UI after-fixes plan (2026-11-20)

Source: review of `reports/screenshots-2026-11-20-pixel-9-fi-after` (83 PNGs, dark set, layout captures at 320 dp and 200 % font) against `ui-finnish-review-plan-2026-11-20.md`. Most of that plan landed. This brief covers what is still wrong, what regressed and what is inconsistent. Screenshot numbers in brackets refer to that folder.

## 0. Ground rules (unchanged, repeat them to yourself before every change)

- UI language is Finnish only. All strings live in `res/values/strings.xml`.
- Monochrome foreground. The photographic artwork and the sage/petrol ArtworkWash gradients stay. Do not remove them.
- No helper, explanation, disclosure or status text unless this brief asks for it. If a state needs explaining, fix the state instead.
- Never use `·`, `•` or ` | ` as separators in UI strings, PDF output or widget text.
- No coloured vertical bar on the left edge of cards, rows or panels.
- Use theme tokens (`ui/theme/`) for every colour, size, spacing and shape. No inline values that have a token.
- Delete code that becomes unused in the same change.
- Do not commit or push. Do not open a PR.

## 1. Defects (fix first)

### 1.1 Equipment choice shows "Sali" instead of the chosen device [39a, 42a]

`feature/program/ProgramSetupScreen.kt`, `ProgramEquipmentChoice` (around line 174). When `equipmentName` is blank or equals the slot name, the trailing text falls back to `location.locationLabel()`, so every row reads "Sali" and the user cannot see what is chosen.

- Trailing value order: nickname, then `equipmentName` (if it differs from the slot name), then a short settings summary (first setting, "Istuin 4" style, label then value, no separator characters).
- If nothing distinguishes the chosen exercise from the slot name, show a check icon (`Icons.Rounded.Check`, `textSecondary`) instead of text.
- Never show the location alone as the value.
- Program workout screen [42a] uses the same value, so it follows automatically. Verify it.

### 1.2 Long Finnish words break mid-word in list rows [39a at 320 dp + 200 %]

"Rintapunnerruslaite" wraps as "Rintapunnerruslait / e".

- In `ListRow` titles, try `TextStyle(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)`. Check on the device whether Finnish hyphenation actually works. If it does not, keep words whole and let the title shrink one typography step at very large font scales (do not go below `bodyMedium`).
- Extend the layout test (`ReferenceLayoutTest` / layout-results checks) so it also covers `ListRow` titles on 39a and 36, not only segmented controls and headers. The current test passed with this break.

### 1.3 Disabled "Ota ohjelma käyttöön" with no reason [39a]

- Keep the button enabled. On tap with missing choices, scroll to the first unchosen row and open its bottom sheet (same pattern as "focus the invalid field" in `ui-final-fixes-plan-2026-11-19.md`). No error text.
- When the button label wraps at large font scales it must stay centred (`textAlign = TextAlign.Center` in `PrimaryButton`). Check all `PrimaryButton`/`OutlineButton` labels.

### 1.4 Mini player reads as the next exercise [11]

`feature/workout/WorkoutScreen.kt`. In the bottom action column (around line 476) the `MiniPlayer` sits directly under the "Seuraavaksi" heading, so the song looks like the next exercise. The header also shows the music note at the same time, so music has two entry points.

- Remove the mini player from the workout bottom area and from the inline position (around line 283). During a workout, music stays in the header: the note icon becomes a small "now playing" state (same icon, filled or with the equalizer glyph) when playback is active, and tapping it opens Now playing.
- Result: the sticky bottom area on the workout screen is only the primary button.
- Remove the `inlinePlayer` parameter and any code that becomes unused.

### 1.5 Duration and clock times disagree [12, 12a]

The summary shows "32 min" next to "10:42–11:15" (33 min). The minutes are floored from seconds while the clock times are rounded to the minute.

- Show the duration as the difference of the displayed start and end minutes, so the two always agree. Do this in one shared helper (next to `finishDuration` in `feature/workout/FinishDraft.kt` or in `ui/theme/ElapsedTime.kt`) and use it on Finish, Korjaa aika, History detail, Today "Viimeisin treeni", the widget and the PDF.
- Add a unit test: 10:42:50 to 11:15:10 shows 33 min.

### 1.6 "Reisituki 2" pill fully bold [10, 11, dark 10]

`workoutSetupLabels` (WorkoutScreen.kt around line 594) only splits label and value for "Handle", "Seat height" and "Backrest" or for `label: value`. "Reisituki 2" has neither, so the whole string becomes the bold value.

- Build the pills from the exercise's structured settings (`settings` with `label` and `value`) instead of parsing the setup string. Use the string parsing only as a fallback for old data. In that fallback, treat the last token as the value when it is a number or a short single word.
- Every pill: label `textSecondary`, value SemiBold, one space between them.

### 1.7 "Valitse kuukausi" is still a day picker [20c]

`feature/history/HistoryScreen.kt` around line 215 uses `RecordedDatePicker`, which shows a full day grid, a "pe 13.11." headline and one-letter weekdays.

- Replace with a month picker in a bottom sheet: year row with previous and next arrows, then a 3 x 4 grid of month names ("tammi" … "joulu" or full names if they fit), current month outlined, selected month filled. Months after the current one are disabled.
- No headline date, no mode toggle.

### 1.8 Now playing lost its cover [46, dark 46]

The cover area shows a thin line-wave graphic in a large empty space.

- If the track has embedded art, show it. If it does not, show the same artwork placeholder that the playlist rows use, at the full cover size and with the same corner radius, not the wave graphic.
- Put shuffle and repeat on the same row as previous, play/pause and next (shuffle far left, repeat far right), so there is no separate row with two lonely icons.

### 1.9 Rest timer is still in the code

`feature/workout/RestTimer.kt` is called from WorkoutScreen.kt (around line 799) when the plan has `restSeconds`. The user rejected a rest timer on 2026-11-14.

- Remove `RestTimer` and its `workout_rest` shared preferences. Keep the rest time only as plan text ("palautus 90 s") in the program and routine rows.
- Delete the strings and tests that only belonged to it.

## 2. Consistency

### 2.1 Sticky bottom bar edge

Screens 30, 39 and 12 show a hairline above the sticky button. Screens 43 and dark 10 do not, and half a text line is visible just above the button ("Ikkunoiden valot", "Lonkan koukistajan venytys").

- Every `Page` with a `bottomBar` gets the same treatment: the hairline divider, and the bottom bar background covers the content fully. Remove the `bottomDivider = false` overrides (for example WorkoutScreen.kt around line 278) unless there is a real reason.

### 2.2 Icon buttons in headers

"+" sits in a grey circle while ⋮ next to it has none (Musiikki [42], Historia). On Today both icons have circles.

- One rule: header icon buttons have no background. Exception: the single primary add action of a top-level tab (Kirjasto, Historia) may keep the circle. Apply the same rule everywhere and put it in one header icon component.
- History's new chat-bubble icon: replace with a clearer one (`Icons.Outlined.QuestionAnswer` or similar) and give it a content description "Kysy päiväkirjasta". No visible label.

### 2.3 Artwork card border

The "Seuraavaksi" card behind the start sheet [07] has a thick dark outline. The active card [01] has none. The empty Today card [50] is an outlined box that only contains the button.

- Artwork wash cards never have an outline. Remove it.
- Empty Today [50]: no card. Show the artwork image and the "Aloita treeni" button without a container, or one ListRow "Aloita treeni" style entry. Pick whichever looks less empty and show both in the screenshots.

### 2.4 Dialogs and sheets

The body area picker [13a] is a Material `AlertDialog` (`feature/checkin/PainInput.kt` around line 121, `SelectionDialog`). The start choice [07], Korjaa aika [12a] and the pain level [13f] are bottom sheets.

- Choices go in bottom sheets. Convert `SelectionDialog` to a bottom sheet list (one tap selects and closes, no radio buttons, no "Peru" button, swipe or scrim tap dismisses).
- Check the other `AlertDialog` uses (HistoryScreen.kt around 223, RoutineEditor.kt around 543 and 587, PlaylistEditors.kt around 272, 411 and 464, SetRow.kt). Keep dialogs only for destructive confirmations. Move choice lists and forms into sheets.

### 2.5 Multi-select style

New playlist [43] uses checkboxes. The routine builder [36] uses chevron rows for the same kind of task.

- The routine builder exercise picker uses checkbox rows like [43]. Remove the repeated "Sali" subtitle when every row has the same location.

### 2.6 Finish defaults [12]

"Ei" is preselected for pain but nothing is selected for effort.

- Preselect "Sopiva" for effort, so a normal workout saves with one tap.

### 2.7 "Ajat eivät ole tiedossa" [12a]

It reads as a status, not an action. Rename the action to "Ei tarkkaa aikaa".

## 3. Smaller visual fixes

- **Kysy päiväkirjasta [20b]:** "Treenin yhteenveto" and "Hae päiväkirjasta" become full-width stacked buttons (plan 2026-11-20 section 4.1, not applied).
- **Program workout [42a]:**
  - "Treeni: Tavallinen" is a selector, not a button. Use the segmented control (Tavallinen, Lyhyt, Kevyt).
  - Change "1-2 per liike, tarpeen mukaan, 3–4 toistoa varaa." to "1–2 sarjaa, 3–4 toistoa varaa." (en dash).
- **Exercise guide [39b]:** remove the "Suurenna ohje" button. Tapping the image already enlarges it.
- **Exercises list [33, dark 33]:**
  - Lower the row height so it fits the thumbnail with normal ListRow padding.
  - Thumbnail at least 56 dp.
  - The favourites star button gets the same height as the search field.
- **New playlist [43]:**
  - Name field and search field get the same height.
  - Use `Dimens.innerSmall` or larger between them.
  - Checkbox to text gap matches the ListRow leading gap.
- **Progress:** centre "+7,5 kg 25.9. lähtien" vertically with the dropdown next to it.
- **Today [01]:** align the "2/3" counter to the baseline of "Tällä viikolla".
- **Finish card [12, 12a]:** the bottom edge of the wash card looks like a soft blurred band instead of a clean rounded corner. Check the clip and shadow order so the corners stay clean in light and dark mode.
- **Korjaa aika [12a]:** content scrolled under the top bar shows a clipped glyph. The top bar needs an opaque background.
- **Widget [52]:**
  - The "Aloita" button gets the same corner radius as in the app.
  - If one-letter weekday labels fit under the dots at 160 x 130 dp, add them. If not, leave them out.
- **PDF [51]:**
  - Remove the double space in the title.
  - Headings use the monochrome text colour, not green.

## 4. Verification

- Unit tests for 1.5 (duration) and 1.6 (pill parsing fallback).
- Layout checks extended to ListRow titles (1.2) and run in all four configurations.
- New screenshot folder `reports/screenshots-2026-11-20-pixel-9-fi-after2` with at least:
  - 07, 10, 11 (music playing during a workout), 12, 12a, 13a, 20b, 20c, 33, 36, 39a, 39b, 42a, 43, 46, 50, 51, 52
  - the Kirjasto and Musiikki "+" menus, which were not captured last time
  - dark versions of 10, 12, 33, 39, 46 and the new month picker
- In `coverage.txt`, list every item of this brief with done, skipped or changed, and a one-line reason for anything not done.
