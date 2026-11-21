# InShape UI after2 fixes plan (2026-11-20)

Source: review of `reports/screenshots-2026-11-20-pixel-9-fi-after2` (37 PNGs, dark set, layout captures at 320 dp and 200 %) against `ui-after-fixes-plan-2026-11-20.md`. Almost everything in that plan landed and is visible in the screenshots. This brief covers new defects, two items that `coverage.txt` reports as done but the screenshots contradict, and the remaining polish. Screenshot numbers in brackets refer to that folder.

## 0. Ground rules (unchanged)

- UI language is Finnish only. All strings live in `res/values/strings.xml`.
- Monochrome foreground. The photographic artwork and the sage/petrol ArtworkWash gradients stay.
- No helper, explanation, disclosure or status text unless this brief asks for it.
- Never use `·`, `•` or ` | ` as separators in UI strings, PDF output or widget text.
- No coloured vertical bar on the left edge of cards, rows or panels.
- Theme tokens for every colour, size, spacing and shape. Delete code that becomes unused in the same change.
- Do not commit or push. Do not open a PR.
- In `coverage.txt`, only write "Tehty" for an item after checking it in the new screenshot. If the screenshot shows otherwise, say so.

## 1. Defects

### 1.1 Widget "Aloita" button is clipped [52]

The button now has rounded top corners, but its bottom edge is cut flat by the widget bounds. The artwork is cut at the bottom too. The taller button no longer fits in 130 dp.

- `widget/InShapeWidget.kt` (button around line 158, artwork around line 174). Reduce vertical padding or the title-to-button gap so the whole button, including its bottom corners, fits at 160 x 130 dp and at 250 x 130 dp.
- The artwork stays fully visible or is scaled down to fit. Do not crop it at the widget edge.
- Add the 160 x 130 and 250 x 130 widget renders to the screenshot set and check the bottom 8 dp by eye.

### 1.2 Playlist search field lost its search icon [43]

In New playlist, "Soittolistan nimi" and "Hae kappaleita" are now two identical text fields, so the search does not look like search.

- `feature/music/PlaylistEditors.kt` around line 204 uses `TextField` for search. Use the shared `SearchField` (with the magnifier icon), the same one used in `MusicScreens.kt` line 177 and in Kirjasto.
- `strings.xml` has `search_songs` and `find_songs` with the same text "Hae kappaleita". Keep one key, update all callers, delete the other.
- Check every other search input in the app uses `SearchField`.

### 1.3 ListRow breaks apart at large font sizes [layout 320-large: 36, 39a]

`ui/components/Lists.kt` `ListRow`: when `fontScale >= 1.3`, `leading` moves above the title and `trailing` moves below it. Result: the checkbox in Koosta rutiini sits on its own line above the exercise name, and the check icon in Valitse laite drops under the title at the left edge.

- Leading content (checkbox, thumbnail, icon) always stays to the left of the text, top-aligned with the first title line.
- Trailing content that is an icon (check, chevron, ⋮, equalizer) always stays on the right, top-aligned with the first title line.
- Only trailing text values may move under the title at large font sizes, and then they align with the title's start edge.
- Extend the layout check so it also asserts that leading and trailing elements stay on the same row as the title (compare bounds), not only that text fits.

### 1.4 Reported as done, but not visible

- **Koosta rutiini [36]: "Sali" still on every row.** `feature/assistant/RoutineProposal.kt` around line 179 shows the location on every row when `mixedLocations` is true (line 166). One exercise with another location makes all rows show "Sali". Show the location only on rows whose location differs from the most common one. Rows in the common location show no subtitle.
- **Liikkeet [33, dark 33]: the favourites star is still much shorter than the search field.** The touch target may match, but the visible circle does not. Make the visible button the same height as the search field (a rounded square or a pill with the same corner radius as the field), around `feature/library/LibraryScreen.kt` line 180.

## 2. Logic and usability

### 2.1 Empty Today [50, 50a]

The artwork floats without a container, slightly right of centre. At large font sizes [50a] the whole state turns into a plain ListRow "Aloita treeni", so the main action looks as minor as "Luo rutiini".

- Use the same card as "Seuraavaksi" [07] without an outline: ArtworkWash background, artwork on the right, title "Tyhjä treeni", and the "Aloita treeni" primary button inside the card. The empty and filled states become the same structure.
- At large font sizes the card keeps the button. The artwork may hide when it does not fit, but the button stays a button.
- "Luo rutiini" stays as the secondary text action under the card.

### 2.2 Kysy päiväkirjasta [20b]

Two identical outline buttons under the question field. It is unclear which one uses the typed question.

- Put a send icon button (`Icons.AutoMirrored.Rounded.Send`, content description "Kysy") inside the question field on the right. It is enabled when the field is not blank. Keyboard IME action "Send" does the same.
- "Treenin yhteenveto" becomes one ListRow with a chevron under the field. It does not use the question text.
- Remove the "Hae päiväkirjasta" button.

### 2.3 Koosta rutiini [36]

- "Tarkista rutiini" is greyed out with no reason. Keep it enabled. On tap with missing input, focus the first missing field or scroll to the exercise list (same pattern as Ota ohjelma käyttöön). No error text.
- The minutes field is two thirds wide at normal font size and full width at large size. Make it full width always.

### 2.4 Body area sheet [13a]

- Limit the sheet height so it never reaches the status bar (`sheetMaxHeight` or partial expansion at about 80 % of screen height).
- Add the same row dividers as the start sheet [07].
- The list scrolls inside the sheet. Add bottom padding equal to the navigation bar inset so the last item is fully visible when scrolled to the end.

### 2.5 Music playing state in the workout header [10, 11]

The only signal that music is playing is a black instead of grey note. That is too subtle at the gym.

- While music plays, replace the note with the equalizer glyph already used in the queue (Now playing [46]). Same size, same position. Tapping it opens Now playing.

## 3. Visual polish

- **Now playing [46]:** the gap between cover and track title is about 10 dp while the gap above the cover is about 40 dp. Use at least `Dimens.sectionGap` (or the nearest token of 24 dp) between cover and title.
- **Exercise guide [39b]:** the guide image has square corners. Use the same corner radius as other images (`InShapeShapes` card or image shape).
- **Program workout [42a]:** "A: Koko vartalo laitteilla" and "Viikko 1/8" are two bold lines of the same style. Keep the workout name as the title. Show "Viikko 1/8" as `textSecondary` body text.
- **Library add menu [32a]:** "Tuo ohjelma" uses an upload arrow, which reads as export. Use a download or file-open icon (`Icons.Outlined.FileDownload` or `Icons.Outlined.FileOpen`).
- **PDF [51]:**
  - Remove "(Europe/Helsinki)". Show the time zone only when the workout's zone differs from the device's current zone.
  - Remove the footer lines "Muistiinpanot mukana" and "Vointi mukana" (`PdfExporter.kt` around lines 159 to 164) and their strings `export_footer_notes`, `export_footer_checkins`, `export_included`, `export_excluded` if nothing else uses them. Keep the page number.
  - "Treenin muistiinpano" is a heading. Give it the same style as other section headings in the PDF and a little space above it.

## 4. Verification

- Layout check extension from 1.3 runs in all four configurations (411 dp and 320 dp, 115 % and 200 %).
- New screenshot folder `reports/screenshots-2026-11-20-pixel-9-fi-after3` with at least:
  - 10 and 11 (music playing), 13a, 20b, 33, 36, 39a, 39b, 42a, 43, 46, 50, 50a, 51
  - the widget at 160 x 130 and 250 x 130
  - 36, 39a and 50 at 320 dp with 200 % font
  - dark versions of 33, 36 and 50
- `coverage.txt` lists every item of this brief as done, skipped or changed, each with a one-line reason when not done.
