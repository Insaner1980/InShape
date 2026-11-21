# InShape final UI fixes (follow-up screenshots of 2026-11-19)

Audience: the coding agent (Codex) implementing changes in `C:\Dev\InShape`.

Source: a review of the 56 screenshots in `reports/screenshots-2026-11-19-pixel-9-followup/light/` and `.../dark/` (Pixel 9, font scale 1.15), taken after `ui-followup-plan-2026-11-19.md` was implemented. Screenshots are cited in brackets by their file-name prefix, for example [30] means `30-library-routine-detail.png`. File paths are relative to `app/src/main/java/com/emmah/inshape/` unless they start with `app/`, `docs/` or `reports/`. Line numbers are from the working tree on 2026-11-19 and can drift; search for the quoted code if a line does not match.

The follow-up plan landed well. This plan has exactly four items. Do not change anything else.

## 0. Ground rules

All ground rules in section 0 of `ui-simplification-plan-2026-11-19.md` still apply. In short:

- Keep all artwork and all `ArtworkWash` gradients unchanged.
- Monochrome foreground; `error` only for destructive actions and errors.
- Never use `·`, `•` or ` | ` as separators in any UI text, string, content description, export or test.
- No coloured vertical bar on the left edge of a card, row or panel.
- Every colour, size, spacing value and text style comes from `ui/theme/`. Add a token if one is missing; do not hardcode `dp`, `sp` or `Color` in feature files.
- Delete code that becomes unused in the same change and update every caller.
- Do not change the Room schema, the backup format or CSV/PDF keys.
- Do not commit, push or open a pull request. Do not run `lc` or `sc`. Do not modify anything in `reports/` except creating the new screenshot folder in section 5.
- Check the current documentation for every Compose API you use here (`FocusRequester`, `Modifier.focusRequester`, `LocalFocusManager`, `LocalSoftwareKeyboardController`, `TextButton` interaction source and indication) against the versions pinned in `gradle/libs.versions.toml`. Do not assume signatures from memory.
- After each section, `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` must pass. Update instrumented tests in `app/src/androidTest/` that reference changed UI. Do not restore test files that are deleted in the current working tree.

Implement the sections in order. Each one is self-contained.

---

## 1. Routine header card: restore padding, use two lines

### Problem

Evidence: [30] in light and dark. The text "7 exercises, about 50 min" starts about 4 dp from the left edge of the card, while every other artwork card in the app (Today "Next up", finish summary, Progress "Workouts per week", exercise detail photo card) has `Dimens.inner` (16 dp) inner padding. The text looks glued to the card edge.

### Cause

`feature/library/RoutineEditor.kt` lines 309 to 325. To fit the summary on one line next to the artwork (a requirement from `ui-followup-plan-2026-11-19.md` section 1.4), the padding was reduced:

```kotlin
Modifier.clip(InShapeShapes.card).background(ArtworkWash.Routine.brush())
    .padding(horizontal = Dimens.innerTiny, vertical = Dimens.inner),
cropEnd = artwork != R.drawable.art_training_mobility_mat,
gap = Dimens.innerTiny
```

The one-line requirement was wrong: at font scale 1.15 the full sentence does not fit next to a 136 dp artwork with normal padding. This plan replaces that requirement.

### Fix

1. Restore the card padding to `Modifier.padding(Dimens.inner)` on all sides, and remove the `gap = Dimens.innerTiny` argument so `ArtworkHeader` uses its default `Dimens.itemGap`. Match exactly how the Today "Next up" card in `feature/today/TodayScreen.kt` builds its `ArtworkHeader` modifier; if the two differ in any other way (clip, background order, padding), use the Today card as the reference.
2. Split the summary into two lines inside the card's text column:
   - line 1: exercise count, for example "7 exercises", style `MaterialTheme.typography.titleMedium`, colour `InShapeTheme.colors.text`, `maxLines = 1`;
   - line 2: duration, for example "About 50 min", style `MaterialTheme.typography.bodyMedium`, colour `InShapeTheme.colors.textSecondary`, `maxLines = 1`. Omit this line when the routine has no estimated minutes.
   - If the routine has a description, show it as a third line in `bodyMedium`, `textSecondary`, `maxLines = 2`, `overflow = TextOverflow.Ellipsis`.
   - Space the lines with `Arrangement.spacedBy(Dimens.innerTiny)`.
3. Do not duplicate the formatting logic. `routineSummary(count, minutes)` in `feature/library/LibraryScreen.kt:181` currently builds "7 exercises, about 50 min" as one string and is also used by the Library routine list (line 205) and Today (`TodayScreen.kt:175` and 241). Refactor it into two small functions in the same file, and rebuild `routineSummary` from them so the other callers keep their one-line text unchanged:
   - `exerciseCountLabel(count: Int): String` returning the plural "7 exercises",
   - `estimatedDurationLabel(minutes: Int?): String?` returning "about 50 min" or `null`,
   - `routineSummary(count, minutes)` = `listOfNotNull(exerciseCountLabel(count), estimatedDurationLabel(minutes)).joinToString(", ")`.
   In the routine header card, capitalise the first letter of the duration line ("About 50 min") with `replaceFirstChar { it.titlecase(Locale.ENGLISH) }` at the call site, not in the shared function.
4. Keep the artwork, its size (`Dimens.routineArtworkWidth`, `Dimens.routineArtworkHeight`), `cropEnd` and the `ArtworkWash.Routine` background unchanged.
5. Keep the `if (draft.items.isNotEmpty())` condition: the card is only shown when the routine has exercises.

### Acceptance

- On [30] (light and dark, font scale 1.15) the left edge of "7 exercises" is 16 dp from the card edge, the same as the "Next up" text on [02].
- "7 exercises" and "About 50 min" are each on one line and do not touch the artwork.
- The Library routine list and Today still show "7 exercises, about 50 min" on one line.
- At font scale 1.3 on a 320 dp wide screen no text in the card is clipped (lines may ellipsize, they must not overlap the artwork).

---

## 2. New routine top bar: no ⋮ menu

### Problem

Evidence: [31b]. While creating a routine, the top bar still shows a ⋮ button. In that state its menu contains a single item, "Edit details", because "Duplicate" and "Remove" are only added when `id != "new"`. A menu with one item is an unnecessary extra tap and hides the only action.

### Cause

`feature/library/RoutineEditor.kt` around lines 264 to 289. The `PushedHeader` always renders the `IconButton(...MoreVert...)` and the `AppMenu`.

### Fix

1. When `creating` is true, do not render the ⋮ `IconButton` or the `AppMenu`. Render instead a single `IconButton` with `Icons.Outlined.Edit` and content description "Edit details", which sets `details = true` (the same action as the current menu item). Use the same icon size and tint as the other top-bar icons.
2. When `creating` is false (an existing routine), keep the ⋮ menu exactly as it is: Edit details, Duplicate, Remove.
3. Make sure no unused state remains: `routineMenu` is only needed in the non-creating branch. Keep it declared where it is if both branches share the composable scope, but do not toggle it from the creating branch.
4. Keep the title behaviour: `draft.title.ifBlank { "New routine" }` while creating.

### Acceptance

- On [31b] the top bar shows the back arrow, the routine name and a pencil icon. No ⋮.
- Tapping the pencil opens the "Routine details" dialog.
- On an existing routine [30] the ⋮ menu still has Edit details, Duplicate and Remove.
- TalkBack reads the pencil as "Edit details".

---

## 3. Dialog confirm button keeps a gray highlight after a failed validation

### Problem

Evidence: [22b] in light and dark. In the "Add past workout" dialog, after tapping "Continue" with an empty name, the error appears under the field correctly, but "Continue" stays drawn on a gray pill. The button keeps focus after the click, and Material 3 draws its focus state layer. Nothing moves focus away because the dialog stays open. It looks like the button is stuck or selected.

### Cause

`feature/history/HistoryScreen.kt` around lines 226 to 238: on an empty name the `DialogAction("Continue", ...)` click only sets `showPastTitleError = true`. Focus remains on the button. `DialogAction` itself (`ui/components/Editors.kt:136`) is a plain `TextButton`.

### Fix

The correct behaviour on a failed validation is to put the user where the problem is: focus the invalid field and show the keyboard. That also removes the focus (and the gray layer) from the button.

1. Add an optional parameter `focusRequester: FocusRequester? = null` to `TextField` in `ui/components/Fields.kt` (line 74). Apply it to the inner `MaterialTextField` modifier (the one that already has `.border(...)` and `.semantics {...}`), not to the outer `Column` `modifier`, so focus lands in the editable field. Apply the same parameter to the second field variant in the same file (around line 111) if it is used by any validated field.
2. In the past-workout dialog in `HistoryScreen.kt`:
   - create `val pastTitleFocus = remember { FocusRequester() }`,
   - pass it to the "Workout name" `TextField`,
   - in the "Continue" click, when the name is blank: set `showPastTitleError = true`, then call `pastTitleFocus.requestFocus()` and show the keyboard with `LocalSoftwareKeyboardController.current?.show()`.
3. Clear the error as soon as the user types a non-blank name (the current `takeIf { showPastTitleError && pastTitle.isBlank() }` already does this; keep it).
4. Apply the same pattern everywhere else a confirm or save action validates a text field and stays on screen. Search for every `error =` argument passed to `TextField` and every `show...Error` state in `feature/` and `ui/components/`. On 2026-11-19 at least these exist; check each:
   - `feature/library/ExerciseEditor.kt` around line 76 to 91 ("Enter an exercise name"),
   - the routine details dialog in `feature/library/RoutineEditor.kt` (routine name),
   - the playlist name in `feature/music/PlaylistEditors.kt` (new playlist and rename),
   - the name dialog for any other create flow you find.
   For each: on a failed validation, focus the invalid field and show the keyboard. If a flow instead disables its confirm button until the input is valid, leave it as it is.
5. Do not remove focus indication from `DialogAction` or `TextAction` globally. Keyboard and switch-access users need it. The fix is to move focus to the right place, not to hide the focus state.

### Acceptance

- In [22b], after tapping "Continue" with an empty name: the error text is shown under the field, the cursor is in "Workout name", the keyboard is open, and "Continue" has no gray background.
- The same holds for every other validated field found in step 4.
- With a hardware keyboard or TalkBack, focus is still visible when moving between the dialog buttons.

---

## 4. Library exercise list: verify the last recorded values

### Problem

Evidence: [34] in light and dark. The exercise rows show no trailing value. In the previous set (`reports/screenshots-2026-11-19-pixel-9-after/34-library-exercises.png`) the same demo showed "3 × 10, 25 kg" with "Wed 11 Nov" at the end of the Chest press and Lat pulldown rows. The rendering code in `feature/library/LibraryScreen.kt` (around line 257, `trailing = last?.let { ... }`) and `lastRecordedValues(...)` (lines 69 to 85) look unchanged, so the cause is probably the data used for this capture, not the UI. This must be verified, not assumed.

### Steps

1. Run `LastRecordedValuesTest` (`app/src/test/java/com/emmah/inshape/feature/library/LastRecordedValuesTest.kt`). It must pass. If it does not cover a workout where an exercise has completed weighted sets after the follow-up changes to `SetSummary.kt`, add one case: a qualifying workout with three completed sets of 10 at 25 kg must give the value "3 × 10, 25 kg" and that workout's date.
2. Check where the trailing value string comes from: `lastRecordedDisplayValue(items)` (line 87 onward). If it now goes through the shared set summary from `feature/workout/SetSummary.kt` and that function returns an empty string for some case (the follow-up plan made planned summaries without sets return ""), make sure an empty string is treated as "no value" (`null` trailing), and that completed weighted sets never produce an empty string.
3. Find how the [34] screenshot was captured (the capture test or script that writes `34-library-exercises.png`) and which demo fixture it uses (`app/src/debug/java/com/emmah/inshape/demo/DemoFixtures.kt`). Confirm whether that fixture contains finished workouts with Chest press and Lat pulldown.
4. Outcome:
   - If the fixture has no history for this capture, change nothing in the app. Make the capture use the same fixture with history as the after-set, so [34] shows the values again.
   - If the fixture has history but the values are missing, it is a real regression: fix the cause found in step 2, and add a unit test that fails without the fix.
5. Report in your summary which of the two outcomes it was and why.

### Acceptance

- [34] shows "3 × 10, 25 kg" and "Wed 11 Nov" (or the fixture's equivalent) at the end of rows that have recorded work, in light and dark.
- `LastRecordedValuesTest` passes and covers completed weighted sets.

---

## 5. Verification

1. `./gradlew :app:assembleDebug` and `./gradlew :app:testDebugUnitTest` pass.
2. Take new screenshots in light and dark theme at font scale 1.15 on Pixel 9 into `reports/screenshots-<date>-pixel-9-final/light/` and `.../dark/`:
   - `30-library-routine-detail.png`,
   - `31b-library-new-routine-editor.png`,
   - `31c-library-new-routine-details-dialog.png` (after tapping the pencil),
   - `22b-history-past-workout-name-required.png` (with the keyboard open),
   - `33-exercise-editor-name-required.png` (empty exercise name after tapping save or create, if that flow validates),
   - `34-library-exercises.png`,
   - `02-today-strength.png` (reference for the card padding comparison).
   Do not modify existing report folders.
3. Re-run the existing layout checks at font scale 1.3 on 411 dp and 320 dp widths, and add the routine detail screen if it is not in them yet.
4. Check by eye:
   - the routine card text has the same left inset as the Today card,
   - there is no ⋮ while creating a routine,
   - no button keeps a gray background after a failed validation,
   - the exercise list shows the last values,
   - artwork and washes are unchanged,
   - no `·`, `•` or ` | ` anywhere.
