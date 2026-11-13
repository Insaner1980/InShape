# InShape visual redesign: implementation plan

Audience: the coding agent implementing this change in `C:\Dev\InShape` (Kotlin, Jetpack Compose, Material 3).

Read this whole file before editing. It is self-contained: every colour, size and behaviour you need is written here. The reference files are for visual confirmation.

## 1. Goal

1. The app has only a light colour scheme. On a phone that runs in dark mode, Android force-darkens it, which produces the broken look in `current/forced-dark-*.png`. Add a real dark theme and stop relying on forced dark.
2. Replace the visual language (colour, type, spacing, elevation) in both themes.
3. Restructure the six main screens so that each has one clear primary action and a readable hierarchy.

Do not change the data layer, Room schema, backup format, export format, reminders logic or Health Connect logic. This is a presentation-layer change, with two small additions: a theme preference (section 5.4) and one pure calculator function (section 9.4).

## 2. Reference files

All paths are relative to `docs/redesign/`.

### 2.1 Current state (what is wrong)

Screenshots from a Pixel 9, 1080 x 2424 px.

| File | Shows | Main defects |
|---|---|---|
| `current/light-01-today.png` | Today, light | Primary action floats between two cards, cards separated only by a 1 px border, large empty area |
| `current/light-02-history-top.png` | History calendar | Text glyph arrows, calendar rows 148 px tall, legend as a sentence |
| `current/light-03-history-bottom.png` | History stats | Three stat cards of uneven height, explanatory sentence, dash placeholders |
| `current/light-04-progress-top.png` | Progress | Empty chart area 500 px tall, ISO dates in copy |
| `current/light-05-progress-bottom.png` | Body observations | Selectors look like full-width buttons, same topic split over three cards |
| `current/light-06-library-routines.png` | Library routines | Two create buttons for the same action |
| `current/light-07-library-exercises.png` | Library exercises | Filters look like buttons, "Add exercise" twice |
| `current/light-08-settings-top.png` | Settings | Every heading same weight, switch with no label next to it |
| `current/light-09-settings-middle.png` | Health Connect | Row titles as heavy as section title, outlined buttons touching |
| `current/light-10-settings-bottom.png` | Your data | Stack of identical outlined buttons |
| `current/forced-dark-01-today.png` to `forced-dark-12-settings-bottom.png` | Same screens force-darkened by Android | Cards pure black on a lighter background (inverted depth), primary button turned pale, switches invisible |
| `current/forced-dark-08-edit-exercise-top.png`, `forced-dark-09-edit-exercise-bottom.png` | Exercise editor | Text fields and selector buttons alternate with no grouping |

### 2.2 Target design (what to build)

`mockups/*.html` are the source files of the design mockups. They do not render in a plain browser (they need a canvas runtime). Open them as text: every element carries its exact values in inline `style` attributes, and the colour tokens are the CSS variables on the root `<div>`.

Unit conversion: CSS `px` = Compose `dp` for sizes and spacing, CSS `px` = `sp` for font sizes and line heights. The mockup frame is 390 px wide.

| File | Screen | Section in this plan |
|---|---|---|
| `mockups/palette.html` | Colour tokens, both themes | 4 |
| `mockups/today-dark.html`, `mockups/today-light.html` | Today | 8.1 |
| `mockups/workout-dark.html`, `mockups/workout-light.html` | Logging a workout | 8.2 |
| `mockups/history-dark.html` | History | 8.3 |
| `mockups/progress-dark.html`, `mockups/progress-light.html` | Progress | 8.4 |
| `mockups/library-dark.html` | Library | 8.5 |
| `mockups/settings-dark.html` | Settings | 8.6 |

If a folder `mockups/png/` exists, it holds rendered images of the same mockups; use them too.

The mockups contain sample data and omit some real features. Where this plan and a mockup differ, this plan wins. Deviations are marked **Not in mockup**.

The older images in `docs/references/inshape/` are the original brief. They are superseded by this plan for visual style; they remain valid for which features exist.

## 3. Ground rules

1. Verify library APIs against the versions in `gradle/libs.versions.toml` (Compose BOM `2026.09.00`, Kotlin `2.4.21`, AGP `9.5.1`) before using them. Do not assume API shapes from memory. Do not change dependency versions unless a step below says so.
2. Keep every existing feature and every existing callback. Screens are restructured, not reduced. The only removals are listed explicitly in section 8.
3. No hardcoded colours, radii, spacings or text styles in screen files. Everything comes from the theme (section 4 to 6) or a shared component (section 7). After the change, `Color(0x...)` and `Color.White` may appear only in `ui/theme/` and in `widget/InShapeWidget.kt`.
4. No duplicated UI code. If a layout pattern appears twice, it is a component in `ui/components/`.
5. Delete dead code in the same change: unused `Palette` entries, `ObservationPlotRow`, unused imports, unused parameters.
6. Never use the characters `·`, `•` or ` | ` as a separator between words, numbers or metadata, in UI strings or anywhere else. Separate with layout (opposite ends of a row, stacked lines, a pill) or, inside a sentence, a comma.
7. Never put a coloured vertical bar on the left edge of a card, row or panel. Express state with a pill, text colour, an icon, or the whole card's background.
8. Interactive elements have a touch target of at least 48 dp (visual size may be 44 dp).
9. Keep the existing large-text fallbacks (`LocalDensity.current.fontScale > 1.3f` branches). Every new row layout must wrap or stack at font scale 1.3 and above.
10. Keep every `contentDescription`. Add one to each new icon-only control.
11. Dates shown to the user are never ISO strings. Use `Locale.ENGLISH` formatters: `"EEEE d MMMM"` (long), `"EEE d MMM"` (row), `"d MMM"` (compact), `"MMMM yyyy"` (month).
12. UI strings stay in English and stay where they are now (inline in Kotlin, a few in `strings.xml`). Do not migrate strings.
13. Instrumented tests (`app/src/androidTest/.../EndToEndJournalTest.kt`, `NavigationTest.kt`, `LauncherWidgetTest.kt`) find nodes by text and content description, 14 lookups in total. Read them first. Where this plan changes a label they use, update the test in the same change.
14. Do not run the repository's `lint-check` or `security-check` scripts. The owner runs them.
15. The folder is not a git repository. Do not initialise one, commit or push.

## 4. Colour tokens

Create `ui/theme/InShapeColors.kt` with an immutable data class and two instances.

| Token | Role | Dark | Light |
|---|---|---|---|
| `background` | Screen background | `#0E1114` | `#F3F4F6` |
| `surface` | Cards, bottom bar, sheets, dialogs | `#171B20` | `#FFFFFF` |
| `surfaceInset` | Inputs, chips, tiles, segmented track, secondary buttons | `#20262D` | `#ECEEF1` |
| `segmentTrack` | Segmented control track when it sits directly on `background` | `#20262D` | `#E4E7EB` |
| `selected` | Selected segment, stepper buttons | `#39424D` | `#FFFFFF` |
| `muted` | Off switch track, unfilled progress, below-goal bars, outline of secondary outlined controls | `#39424D` | `#D5DAE0` |
| `divider` | 1 dp lines inside cards, bar top border | `#2A313A` | `#E4E7EB` |
| `text` | Primary text and icons | `#F2F4F5` | `#12161B` |
| `textSecondary` | Secondary text, captions, inactive nav | `#A3ADB8` | `#5A6470` |
| `accent` | Primary button, hero card, done states, on switch | `#C5F25A` | `#C5F25A` |
| `onAccent` | Text and icons on `accent`; hero button container | `#141A05` | `#141A05` |
| `onAccentMuted` | Secondary text on `accent` | `#35440F` | `#35440F` |
| `onAccentInverse` | Text on the dark button inside the hero card | `#D4F77F` | `#D4F77F` |
| `accentContainer` | Pills, active nav indicator, "last time" strip | `#26330F` | `#E9F8C2` |
| `onAccentContainer` | Text on `accentContainer`, text links | `#D4F77F` | `#3F6200` |
| `mark` | Chart lines and bars, today ring, focused input border | `#C5F25A` | `#5E8C00` |
| `pain` | Pain series and labels | `#FF9A62` | `#B8490B` |
| `stiffness` | Stiffness series and labels | `#7FB2FF` | `#2459B0` |
| `error` | Destructive text, validation | `#FF8A80` | `#B3261E` |

Why `mark` differs from `accent`: lime on white has too little contrast for thin lines, so the light theme draws data in a darker green.

Pain and stiffness must never be told apart by hue alone. Stiffness lines are dashed (section 8.4).

### 4.1 Elevation

- Dark theme: no shadows and no card borders. Depth is the three steps `background` < `surface` < `surfaceInset`.
- Light theme: cards have no border and one soft shadow, CSS equivalent `0 1px 2px rgba(18,22,27,.05), 0 8px 24px rgba(18,22,27,.07)`. Implement it once in a modifier `Modifier.cardElevation(shape)` in `ui/theme/`. Prefer the Compose drop-shadow modifier that takes blur radius, offset and colour if the BOM in use provides it (check); otherwise use `Modifier.shadow(elevation = 6.dp, shape, clip = false, ambientColor = Color(0x1412161B), spotColor = Color(0x1F12161B))`. The modifier reads `InShapeTheme.colors.isDark` and does nothing in dark.
- Never use `tonalElevation`; set `surfaceTint = Color.Transparent` in both Material schemes.

### 4.2 Material 3 mapping

Build `darkColorScheme(...)` and `lightColorScheme(...)` from the tokens so stock components (dialogs, menus, bottom sheets, date picker) match:

`primary = accent`, `onPrimary = onAccent`, `primaryContainer = accentContainer`, `onPrimaryContainer = onAccentContainer`, `secondary = textSecondary`, `onSecondary = background`, `secondaryContainer = accentContainer`, `onSecondaryContainer = onAccentContainer`, `tertiary = onAccentContainer`, `background = background`, `onBackground = text`, `surface = surface`, `onSurface = text`, `surfaceVariant = surfaceInset`, `onSurfaceVariant = textSecondary`, `surfaceContainerLowest = surface`, `surfaceContainerLow = surface`, `surfaceContainer = surface`, `surfaceContainerHigh = surface`, `surfaceContainerHighest = surfaceInset`, `outline = muted`, `outlineVariant = divider`, `error = error`, `surfaceTint = Color.Transparent`.

## 5. Theme infrastructure

### 5.1 `ui/theme/InShapeTheme.kt`

- Delete `object Palette`.
- Add `val LocalInShapeColors = staticCompositionLocalOf<InShapeColors> { error("No InShapeColors") }` and an accessor object:

```kotlin
object InShapeTheme {
    val colors: InShapeColors @Composable @ReadOnlyComposable get() = LocalInShapeColors.current
}
```

- Signature: `@Composable fun InShapeTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit)`. Resolve `dark = when (mode) { System -> isSystemInDarkTheme(); Light -> false; Dark -> true }`, provide `LocalInShapeColors`, and call `MaterialTheme(colorScheme, typography, shapes, content)`.
- Do not use dynamic colour.

### 5.2 Replacing `Palette` (124 usages in 14 files)

| Old | New |
|---|---|
| `Palette.Background` | `colors.background` |
| `Palette.Surface`, `Color.White` used as a surface | `colors.surface` |
| `Palette.Text` | `colors.text` |
| `Palette.Secondary` | `colors.textSecondary` |
| `Palette.Accent` as a fill | `colors.accent` |
| `Palette.Accent` as a chart or outline colour | `colors.mark` |
| `Palette.AccentText` | `colors.onAccentContainer` |
| `Palette.Mint` | `colors.accentContainer` |
| `Palette.Soft`, `Palette.Segment` | `colors.surfaceInset` |
| `Palette.Border`, `Palette.Divider` | `colors.divider` (and remove card borders entirely) |
| `Palette.MutedMint`, `Palette.Bar` | `colors.muted` |
| `Palette.Pain`, `Palette.PainText`, `Palette.PainAfter` | `colors.pain` |
| `Palette.PainSurface`, `Palette.PainConnector` | delete with their usages (see 8.4) |
| `Palette.Stiffness` | `colors.stiffness` |
| `Color.White` as content on accent | `colors.onAccent` |

`InShapeTheme.colors` is a composable read. Inside `Canvas { }` draw lambdas (`ProgressCharts.kt`, `BodyMap.kt`) read the colours into local `val`s above the `Canvas` call.

### 5.3 System integration

`app/src/main/res/values/themes.xml`: keep the light parent; add `<item name="android:forceDarkAllowed">false</item>`; set `android:windowBackground` to `#F3F4F6` and `android:colorAccent` to `#5E8C00`.

Create `app/src/main/res/values-night/themes.xml` with the same style name, parent `android:style/Theme.Material.NoActionBar`, `android:windowLightStatusBar` false, `android:forceDarkAllowed` false, `android:windowBackground` `#0E1114`, `android:colorAccent` `#C5F25A`. This also themes the platform `TimePickerDialog` used in settings.

`MainActivity.kt`: read the theme mode as state, pass it to `InShapeTheme(mode)`, and call `enableEdgeToEdge(statusBarStyle, navigationBarStyle)` again whenever the resolved dark flag changes (a `DisposableEffect` or `SideEffect` keyed on it), using `SystemBarStyle.dark(Color.TRANSPARENT)` in dark and `SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)` in light. `HealthPrivacyActivity` and the debug `DemoActivity` must use the same theme mode.

### 5.4 Theme preference

Do not add a field to the Room `preferences` entity (`data/JournalEntities.kt:78`); that would need a schema migration and a backup-format change.

Create `ui/theme/ThemeSettings.kt`:

- `enum class ThemeMode { System, Light, Dark }`
- `class ThemeSettings(context: Context)` backed by `SharedPreferences("ui_settings")`, key `theme_mode`, default `System`, exposing `val mode: StateFlow<ThemeMode>` and `fun set(mode: ThemeMode)`.
- Create one instance in `AppContainer` (`val themeSettings`).

The theme mode is deliberately not part of backups.

## 6. Typography, spacing, shape

### 6.1 Fonts

- Display font: Space Grotesk (SIL Open Font License). Add the font files to `app/src/main/res/font/` (variable font file, or static 500, 600 and 700 weights) and the licence text to `docs/redesign/OFL-SpaceGrotesk.txt`. Get them from the official Google Fonts distribution. Define `val Display = FontFamily(...)` in `ui/theme/Type.kt`. minSdk is 26, so variable fonts work.
- Body font: `FontFamily.Default`. The mockups use Inter; the system sans is the intended substitute. Do not bundle Inter.
- If the font files cannot be obtained, stop and report it. Do not silently fall back.

### 6.2 Type scale

Define in `ui/theme/Type.kt` and assign to the Material `Typography` slots listed. Screen code uses `MaterialTheme.typography.*` only.

| Slot | Use | Family | Size / line height (sp) | Weight | Letter spacing |
|---|---|---|---|---|---|
| `displaySmall` | Big metric ("27.5 kg", "2.5") | Display | 36 / 40 | 700 | -0.02 em |
| `headlineLarge` | Screen title, greeting | Display | 32 / 38 | 700 | -0.02 em |
| `headlineMedium` | Hero title, current exercise name, sub-screen title | Display | 28 / 32 | 700 | -0.02 em |
| `headlineSmall` | Stat value, week count | Display | 24 / 28 | 700 | 0 |
| `titleLarge` | Top app bar title, numeric input text | Display | 20 / 24 | 600 | 0 |
| `titleMedium` | Card title, selector label, month name | Default | 17 / 22 | 600 | 0 |
| `titleSmall` | Section label above a card, card caption heading | Default | 15 / 20 | 600 | 0 |
| `bodyLarge` | Row title, body | Default | 16 / 23 | 400 | 0 |
| `bodyMedium` | Secondary line, helper text | Default | 14 / 20 | 400 | 0 |
| `bodySmall` | Row subtitle, caption, axis labels | Default | 13 / 18 | 400 | 0 |
| `labelLarge` | Buttons, row title emphasis | Default | 16 / 22 | 600 | 0 |
| `labelMedium` | Pills, segmented options, small buttons | Default | 14 / 20 | 600 | 0 |
| `labelSmall` | Overline, group label, table header (always upper case) | Default | 12 / 16 | 700 | 0.06 em |

Row titles in lists use `bodyLarge` with `FontWeight.SemiBold` (16 sp, 600); add a small extension or a named style `InShapeType.rowTitle` rather than repeating `.copy(...)`.

Hierarchy rule: one `headlineLarge` per screen. Section labels are `titleSmall` in `textSecondary`. Nothing inside a card is larger than the screen title except a `displaySmall` metric.

### 6.3 Spacing and shape

Create `ui/theme/Dimens.kt`:

| Token | Value | Use |
|---|---|---|
| `screenPadding` | 20 dp | Horizontal padding of every screen |
| `screenTop` | 12 dp | Space between the status bar inset and the first element |
| `itemGap` | 12 dp | Vertical gap between sibling cards and controls |
| `sectionGap` | 24 dp | Space above a section label (12 dp gap + 12 dp label top padding) |
| `cardPadding` | 20 dp | Inner padding of content cards |
| `listCardPadding` | 16 dp | Horizontal inner padding of list cards |
| `inner` | 16 dp | Gap between groups inside a card |
| `innerSmall` | 8 dp | Gap between closely related elements |
| `innerTiny` | 4 dp | Title to subtitle |
| `rowMin` | 56 dp | One-line list row |
| `rowMinTwoLine` | 64 dp | Two-line list row |
| `touch` | 48 dp | Minimum touch target |

Shapes (`ui/theme/Shapes.kt`): card 24 dp, inset field and strip 14 dp, tile 12 dp, pills and buttons fully rounded (`CircleShape`). The settings mockup shows 20 dp card corners; use 24 dp everywhere for consistency.

## 7. Shared components

All in `ui/components/`. Rewrite `Controls.kt` and `Editors.kt`; split into more files if a file exceeds about 250 lines. Each component takes a `modifier` parameter first after required parameters.

| Component | Spec |
|---|---|
| `Page(bottomBar = null, content)` | Centered column, max width 600 dp, vertical scroll, `imePadding`, horizontal padding `screenPadding`, top `screenTop`, bottom 24 dp, `spacedBy(itemGap)`. Optional `bottomBar` slot pinned below the scroll area: background `background`, padding 12 dp top, 20 dp horizontal, 16 dp bottom plus navigation bar inset. |
| `ScreenHeader(title, trailing = null)` | Row, `SpaceBetween`, centre aligned. Title `headlineLarge`. Trailing slot for one control. Wraps to two lines at large font scale. |
| `SectionLabel(text, action = null, onAction = null)` | `titleSmall`, `textSecondary`, padding top 12 dp, start 4 dp. Optional text action at the row's end in `onAccentContainer`, `labelMedium`. |
| `GroupLabel(text)` | `labelSmall`, upper case, `textSecondary`, padding top 16 dp, start 4 dp. Used in Settings and editors. |
| `InShapeCard(color = surface, contentPadding = cardPadding, onClick = null, content)` | Shape 24 dp, no border, `cardElevation`, inner column `spacedBy(inner)`. No `outlined` parameter. |
| `ListCard(content)` | `InShapeCard` with horizontal padding 16 dp, vertical 0, no inner spacing. Draws a 1 dp `divider` between children, never after the last. |
| `ListRow(title, subtitle = null, leading = null, trailing = null, onClick = null)` | Min height 56 dp, or 64 dp with a subtitle. Title: row title style. Subtitle `bodySmall`, `textSecondary`, 2 dp below. Leading and trailing slots separated by 12 dp. `onClick` makes the whole row clickable with a ripple; when set and no trailing is given, show a 20 dp chevron in `textSecondary`. |
| `DateTile(day, weekday)` | 44 dp square, 12 dp radius, `surfaceInset`. Day number `labelLarge` in Display family, weekday 11 sp `textSecondary`. |
| `InitialsTile(text)` | Same tile with up to two initials, `textSecondary`. |
| `PrimaryButton(text, onClick, icon = null, enabled = true)` | Height 56 dp when full width in a bottom bar, otherwise 52 dp. Container `accent`, content `onAccent`, `labelLarge` at weight 700, fully rounded, no elevation. Disabled: container `muted`, content `textSecondary`. |
| `HeroButton(text, onClick, icon)` | Used only inside the accent hero card: height 52 dp, container `onAccent`, content `onAccentInverse`. |
| `SecondaryButton(text, onClick, enabled = true)` | Min height 48 dp, container `surfaceInset`, content `text`, `labelMedium`, fully rounded, no border. |
| `OutlineButton(text, onClick)` | Min height 48 dp, transparent, 1.5 dp `muted` border, content `text`, `labelMedium`. Used for "Finish" and for low-emphasis actions on the plain background. |
| `TextAction(text, onClick, color = onAccentContainer, icon = null)` | Text-only button, min height 48 dp, `labelMedium`. `color = error` for destructive actions. |
| `IconCircleButton(icon, contentDescription, onClick, accent = false)` | 44 dp circle inside a 48 dp target. Container `surface` with `cardElevation`, or `accent` with `onAccent` content when `accent = true`. |
| `Pill(text, tone)` | Height 28 dp, horizontal padding 10 dp, fully rounded, `labelMedium` at 13 sp. `tone = Neutral`: `surfaceInset` and `textSecondary`. `tone = Accent`: `accentContainer` and `onAccentContainer`. Replaces `MetadataChip`. |
| `FilterPill(text, selected, onClick)` | Min height 44 dp in a 48 dp target, horizontal padding 16 dp. Selected: container `text`, content `background`, weight 600. Unselected: container `surface` with `cardElevation`, content `text`, weight 500. Semantics: `Role.Tab` or `selected` set. |
| `SegmentedControl(options, selected, onSelect, onCard = false)` | Track `segmentTrack` (or `surfaceInset` when `onCard`), radius 26 dp, padding 4 dp, gap 4 dp. Option min height 44 dp, `labelMedium`. Selected: container `selected`, content `text`; in light add a 1 dp shadow at 10 percent. Unselected: transparent, `textSecondary`. Keep the stacked layout for large font scale and the tab semantics. |
| `SelectField(label, value, options, onSelect, inline = false)` | Replaces `ChoiceField`. Default form: `GroupLabel`-style label above a 52 dp inset box (`surfaceInset`, 14 dp radius, horizontal padding 16 dp) with the value left aligned in `bodyLarge` and a chevron-down at the end. `inline = true`: a text button showing value in `titleMedium` plus a chevron-down, no box, used as a card title. Both open the existing radio list dialog. Content description: "label, value". |
| `TextField(value, label, onChange, singleLine = false)` | Material 3 filled `TextField`: container `surfaceInset`, 14 dp radius, indicator lines transparent, label and placeholder `textSecondary`, cursor `mark`. Focused: 2 dp `mark` border. Signature unchanged so call sites keep working. |
| `NumberField(...)` | Same visual as `TextField`, text style `titleLarge`, keeps its signature. A `compact = true` variant for set rows: height 52 dp, centred text, no floating label (the column header is the label; set `contentDescription` to the label). |
| `SearchField(value, onChange, placeholder)` | Height 48 dp, container `surface` with `cardElevation`, 14 dp radius, leading search icon 20 dp in `textSecondary`. |
| `InShapeSwitch(checked, onChange, contentDescription)` | Material `Switch` with `SwitchDefaults.colors`: checked track `accent`, checked thumb `onAccent`, unchecked track `muted`, unchecked thumb `text`, no border. |
| `Stepper(valueLabel, onDecrease, onIncrease, decreaseEnabled, descriptions)` | Track `surfaceInset`, radius 26 dp, padding 4 dp. Two 44 dp circular buttons in `selected` with minus and plus icons, value between them, width 32 dp minimum, `titleLarge` at 18 sp. |
| `ProgressSegments(states)` | Row of equal segments, 6 dp tall, 3 dp radius, 4 dp gap. `Done` = `mark`, `Current` = `text`, `Pending` = `muted`. Takes a content description. |
| `EmptyState(title, message, action, onAction)` | `InShapeCard`: title `titleMedium`, message `bodyMedium` `textSecondary`, optional `SecondaryButton` (not primary; the screen's primary action lives elsewhere). |
| `SaveStatus`, `ConfirmDialog`, `ActionRow` | Keep behaviour. Dialog confirm and dismiss use `TextAction` colours; destructive confirms use `error`. |

Delete: `PillButton`, `MetadataChip`, `SectionHeading`, `ChoiceField`, the `outlined` and `dark` parameters. Update every call site.

Update `app/src/debug/java/com/emmah/inshape/demo/ComponentPreview.kt` to show every component above in both themes.

## 8. Screens

### 8.1 Navigation shell: `navigation/AppNavigation.kt`

- Bottom bar: Material `NavigationBar`, container `surface`, no tonal elevation, a 1 dp `divider` line on top. Item colours: indicator `accentContainer`, selected icon `onAccentContainer`, selected label `text` at weight 700, unselected icon and label `textSecondary`. Labels 12 sp. Keep the four tabs, icons and the large-text height.
- Top app bar (sub-screens): container `background`, scrolled container `background`, title `titleLarge`, navigation icon `text`. No white bar.
- Workout route: title is the workout name, subtitle `bodySmall` `textSecondary` is `"Started HH:mm"` only (the done count moves into the screen, see 8.2). The action is `OutlineButton("Finish")`, not a filled button.
- Remove the fully qualified `com.emmah.inshape.ui.theme.Palette` references here.

### 8.2 Today: `feature/today/TodayScreen.kt`

Mockups: `mockups/today-dark.html`, `mockups/today-light.html`. Before: `current/light-01-today.png`.

Order, top to bottom:

1. **Header.** Left column: date, format `"EEEE d MMMM"`, upper case, 13 sp, weight 600, letter spacing 0.04 em, `textSecondary`; below it the greeting in `headlineLarge` (`"Hello"` or `"Hi {name}"`, unchanged logic). Right: `IconCircleButton` with a sliders or settings icon, content description `"Settings"`.
2. **Hero card.** `InShapeCard(color = accent)`, all text `onAccent`. Contents: overline (`labelSmall`, `onAccentMuted`), title (`headlineMedium`), meta line (`bodyLarge` at 15 sp, `onAccentMuted`), then a full-width `HeroButton`. Exactly one of three states:
   - **Active workout exists** (`data.active != null`): overline `"IN PROGRESS"`, title = workout title, meta = `"{done} of {total} exercises done, started HH:mm"` (omit the started part when unknown), then `ProgressSegments` drawn with `onAccent` for done and `onAccent` at 20 percent alpha for the rest, button `"Resume workout"` calling `onResume(id)`.
   - **No active workout, at least one routine:** overline `"NEXT UP"`. Suggested routine = the routine whose most recent finished workout (history rows with `sourceRoutineId == routine.id`) is the oldest; routines never done come first; ties resolve by list order. Title = routine title, meta = `"{n} exercises"` plus `", about {m} min"` when `durationMinutes` is set. Button `"Start workout"` with a play icon calling `onStart(StartSource.Routine(id))`. Put the selection rule in a pure function `suggestedRoutine(...)` next to the screen and unit test it.
   - **No routines:** overline `"READY WHEN YOU ARE"`, title `"Start a workout"`, meta `"Add exercises as you go"`, button `"Start workout"` calling `onStart(StartSource.Empty)`.
3. **Secondary starts. Not in mockup.** A row of two equal `SecondaryButton`s, shown only when the hero is in the "next up" state: `"Empty workout"` (`StartSource.Empty`) and, when history is not empty, `"Repeat last"` (`StartSource.Repeat(last.id)`). In the "no routines" state show only `"Repeat last"` when history exists. In the "active" state show nothing.
4. **This week card.** Row: `"This week"` (`titleSmall`, `textSecondary`) at the start; at the end the count in `headlineSmall` followed by `"of {goal} workouts"` or `"workouts"` in `bodyMedium` `textSecondary`, baseline aligned. Below, seven equal columns: a 36 dp circle and a 12 sp label 6 dp under it.
   - Done day: circle `accent`, 18 dp check icon in `onAccent`.
   - Today, not done: transparent circle with a 2 dp `mark` ring; label weight 700 in `text`.
   - Today and done: `accent` fill with check, label weight 700.
   - Other days: `surfaceInset` fill, label `textSecondary`.
   - Keep the per-day content descriptions and the narrow weekday names at large font scale.
5. **Routines.** `SectionLabel("Routines", action = "See all", onAction = onLibrary)`. Then a `ListCard` with one `ListRow` per routine (max 4; the rest are behind "See all"): title = routine title, subtitle = `"{n} exercises, last done {when}"` where `{when}` is the weekday name if within the last 6 days, otherwise `"d MMMM"`, or `"{n} exercises, not done yet"`. Tap starts that routine (same as the old tonal buttons). With no routines, show the existing `EmptyState("A plan that fits you", ...)` whose button opens the Library.
6. **Last workout.** `SectionLabel("Last workout")`, then a `ListCard` with one `ListRow`: leading `DateTile`, title = workout title, subtitle = `"{n} exercises, {m} min"` (omit missing parts). Tap calls `onDetails(id)`. Remove the separate "View workout" button.
7. `healthContext()` unchanged in position (last). Restyle `HealthToday` in `health/HealthControls.kt` to two equal stat cards per row using `InShapeCard`, value in `headlineSmall`, label `bodySmall`.

### 8.3 Logging a workout: `feature/workout/WorkoutScreen.kt`

Mockups: `mockups/workout-dark.html`, `mockups/workout-light.html`. This is the most used screen; one-handed use in a gym is the priority.

Use `Page(bottomBar = { ... })`.

Order:

1. Timing warning card (existing condition), restyled as an `InShapeCard` with `bodyMedium` text.
2. **Progress.** `ProgressSegments`, one segment per workout item in order: `DONE` = Done, the current item = Current, everything else = Pending. Under it, `bodySmall` weight 500 `textSecondary`: `"{done} of {total} done"`.
3. **Current exercise card** (`InShapeCard`, no outline). When `current == null`, show the existing `EmptyState("Your plan is complete", ...)` instead.
   - Header row: name in `headlineMedium` (weight 1, wraps); at the end `TextAction("Details")` and an overflow icon button (`MoreVert`, content description `"More options for {name}"`) opening a `DropdownMenu` with `"Move up"`, `"Move down"`, `"Remove exercise"` (same enabled rules and confirmation as today). This replaces the "More exercise options" toggle.
   - Setup: if `item.row.setup` is not blank, split it on commas, trim, and show each part as a neutral `Pill` in a wrapping row with 6 dp gaps.
   - **Last time strip:** 14 dp radius, `accentContainer`, padding 12 dp by 14 dp, content `onAccentContainer`. Start: `"Last time, {EEE d MMM}"` in 13 sp weight 600. End: `itemSummary(old)` in Display family 16 sp weight 700. If both do not fit on one line, stack them. If the previous item has a note, add a second line `"Your note: {note}"` in `bodySmall`. With no previous record show `"No previous record"` on a `surfaceInset` strip in `textSecondary`.
   - **Set table.** Header row once, `labelSmall` `textSecondary`: `SET`, the unit (`KG`, `ASSISTANCE KG`, `LEVEL` or `LOAD`), `REPS`, `DONE`. Columns: 28 dp, weight 1, weight 1, 52 dp; 8 dp gaps; 8 dp between rows.
   - **`SetRow`.** Set number in Display 18 sp weight 700, `textSecondary` for completed sets and `text` for the first incomplete one. The number is a 48 dp tappable target with content description `"Options for set {n}"` that opens the existing menu (Edit, Remove); this replaces the per-row `MoreVert` icon. Two compact `NumberField`s (52 dp, centred, `titleLarge`). The first incomplete set's fields have a 2 dp `mark` border; completed sets are read-only as today. Done toggle: 52 dp circle; complete = `accent` fill with `onAccent` check; incomplete = transparent with a 2 dp `muted` ring and a `textSecondary` check. Keep content descriptions `"Complete set {n}"` and `"Undo set {n}"`, and the stacked fallback for width under 280 dp or large font scale.
   - `TextAction("Add set", icon = plus)` aligned to the start.
   - Duration field: existing condition and behaviour, restyled.
   - Note: the note field and the "This movement felt uncomfortable" checkbox are hidden until the user taps `"Add note"` or when a note or the discomfort flag already exists. Hold the expanded flag in `rememberSaveable(item.row.id)`.
   - Divider (`divider`, 1 dp), then a row of three equal `SecondaryButton`s: `"Add note"` (expands the note area and focuses it; reads `"Hide note"` when expanded and empty), `"Swap"` (opens the alternatives sheet; was "Use alternative"), `"Do later"` (`deferItem`; was "Machine busy, later"). Below them, aligned to the end, `TextAction("Skip today")` in `textSecondary` (`skipItem`).
4. **Up next.** `SectionLabel("Up next")`, `ListCard` of rows: title = name, trailing text in `bodyMedium` `textSecondary` = planned summary (`"{n} sets"`, `"{m} min"` or `"Completion only"`). With none: no section at all.
5. **Done.** `SectionLabel("Done")` and a `ListCard`: title = name, subtitle = `itemSummary(item)`, trailing = icon button with a check-circle in `mark`, content description `"Reopen {name}"`. With none: no section. (The old screen showed this list first; it moves below the current exercise.)
6. **Skipped or incomplete.** `SectionLabel("Skipped")`, rows with subtitle `"Skipped today"` or `"Remainder skipped, recorded work kept"` and trailing `TextAction("Reopen")`.
7. **Footer group**, each on the plain background with `itemGap`: `SecondaryButton("Add exercise")` full width; the `"Workout note"` text field; a `ListCard` with one `ListRow("Workout check-in", "Effort, energy, pain and stiffness. Optional.")` calling `onCheckIn`; `SecondaryButton("Save changes to routine")` under its existing condition; `TextAction("Discard workout", color = error)` centred.
8. **Pinned bottom bar:** one `PrimaryButton`, 56 dp, full width.
   - A current exercise exists: `"Mark done"` with a trailing chevron, running the existing mark-done block (including the duration validation). The mockup labels it "Next exercise"; use `"Mark done"`.
   - No current exercise: `"Review and finish"` calling `onFinish`.
   - Remove the old in-scroll "Review and finish" and "Mark done" buttons.

Bottom sheets (`ExercisePicker`, alternatives): container `surface`; rows become `ListRow`s (`ExercisePicker`: title = name, subtitle = equipment, tap = choose; a `SecondaryButton("Create exercise")` on top). Remove the "Add {name}" primary buttons.

`WorkoutDetailScreen` and `WorkoutTimingEditor`: see 8.8.

### 8.4 History: `feature/history/HistoryScreen.kt`

Mockup: `mockups/history-dark.html`. Before: `current/light-02-history-top.png`, `current/light-03-history-bottom.png`.

1. `ScreenHeader("History", trailing = SecondaryButton-style pill on `surface`: plus icon and "Past workout")`. Height 44 dp, container `surface` with `cardElevation`. Opens the existing dialog.
2. **Calendar card** (`InShapeCard`, padding 12 dp top, 16 dp sides and bottom, inner gap 8 dp):
   - Month row: previous and next as 48 dp icon buttons with real chevron icons (keep the content descriptions `"Previous month"`, `"Next month"`); between them the month in `titleMedium`, centred, as a text button that opens a Material 3 `DatePickerDialog`. Confirming sets the month and the selected day. This replaces the "Choose another date" text entry, the "Select date" button and the "Enter a valid date" error; delete that state.
   - Weekday row: `labelSmall` without upper-casing, `textSecondary`, centred.
   - Day grid: 7 columns, cell height 44 dp (touch target), a 36 dp circle centred in each cell, day number 15 sp.
     - Workout recorded: circle `accent`, number `onAccent` weight 700.
     - Today without a workout: 2 dp `mark` ring, weight 700.
     - Today with a workout: `accent` fill plus a 2 dp `text` ring.
     - Selected day: circle `selected`; if also recorded, keep `accent` and add a 2 dp `text` ring.
     - Other days: no circle, number `text`.
   - Legend, separated by a 1 dp `divider` with 8 dp padding above: two items 20 dp apart, each a 12 dp sample dot and a `bodySmall` `textSecondary` label: `"Workout"` (filled `accent`) and `"Today"` (ring). Replaces the sentence "Green: workout recorded. Outline: today."
   - When a day is selected, show `TextAction("Show whole month")` at the end of the legend row.
3. Draft entries ("Unfinished past entry"): one `ListCard` with a `ListRow` per draft: title = draft title, subtitle = `"Unfinished, {EEE d MMM}"`, tap continues it.
4. **Stats card:** one `InShapeCard` (vertical padding 16 dp, horizontal 0) with three equal columns, each padded 16 dp horizontally, separated by 1 dp `divider` lines on the start edge of columns two and three. Value `headlineSmall`, label `bodySmall` at 12 sp `textSecondary`. Values: workout count; recorded time as `"{h} h {m} min"`, or `"{m} min"` under an hour; average effort to one decimal. A missing value shows `"None"` in `textSecondary` at the same size, never a dash. At large font scale stack the three as rows inside the same card.
   - Coverage note, `bodySmall` `textSecondary`, shown only when some qualifying workouts lack a duration or effort: `"Time from {a} of {n} workouts, effort from {b}."`
5. `SectionLabel("Workouts in {MMMM}")` or `"Workouts on {EEE d MMM}"` when a day is selected.
6. **Workout list:** one `ListCard`; each `ListRow`: leading `DateTile`, title = workout title, subtitle = `"{m} min, {n} exercises"` (omit missing parts; for note-only entries `"Note only, not counted"`), trailing = neutral `Pill("Effort {e}")` when an after check-in has effort. Tap opens the detail screen (`onOpen`).
   - **Removed:** the inline expandable summary inside each history card (chips, paired pain line, first two exercises, "Show more", "Open workout"). The detail screen already shows all of it. Remove the `expanded` and `summaryVisible` state.
7. Empty list: `EmptyState("No workouts in this period", "Pick another month or add a past workout.")`.

Past-workout dialog: keep, with restyled text fields.

### 8.5 Progress: `feature/progress/ProgressScreen.kt`, `ProgressCharts.kt`, `ProgressCalculator.kt`

Mockups: `mockups/progress-dark.html`, `mockups/progress-light.html`. Before: `current/light-04-progress-top.png`, `current/light-05-progress-bottom.png`.

Order: header, period control, workouts-per-week card, exercise card, personal-best card, body card. (Attendance moves above the exercise card.)

Each chart card follows one pattern: a `titleSmall` caption or an inline selector, one big value, one chart, one optional text action.

1. `ScreenHeader("Progress")`, then `SegmentedControl("4 weeks", "12 weeks", "All")`.
2. **Workouts per week card.**
   - Start: caption `"Workouts per week"` (`titleSmall`, `textSecondary`); below it the average in `displaySmall` and `"on average"` in `bodyMedium` `textSecondary`, baseline aligned. End: accent `Pill("Goal {n}")` when a goal is set.
   - Average = total workouts in completed weeks of the range divided by the number of completed weeks, one decimal. With no completed week, use the current week's count. Put this in a pure function in `ProgressCalculator.kt` and unit test it.
   - `AttendanceChart`: height 96 dp (was 180). One rounded bar per week, 6 dp corner radius, bars separated by 6 dp. With a goal: weeks at or above the goal are `mark`, below are `muted`; a 1.5 dp dashed `muted` line marks the goal. Without a goal: all `mark`. The current, incomplete week is drawn as a 1.5 dp dashed outline in `textSecondary` with no fill. A week with zero workouts draws a 4 dp tall `muted` stub so the slot is visible.
   - Under the chart, a row with `bodySmall` at 12 sp `textSecondary`: start = first week's start date as `"d MMMM"`, end = `"This week, in progress"`. Replaces the ISO sentence.
   - Keep `"View weekly counts"` as a `TextAction`; list lines read `"Week of {d MMM}: {n} workouts"`.
   - With no history in the range, show `"No workouts recorded in this period."` instead of the chart.
3. **Exercise card.** When there is no recorded exercise: `EmptyState("Your recorded progress", "Record a completed exercise to start an exercise chart.")`.
   - Header row: `SelectField(inline = true)` for the exercise (value = exercise name); at the end an accent `Pill` with the change over the period, for example `"+7.5 kg"` (only with two or more recorded points; sign included; no pill when the change is zero).
   - Big value: latest value and unit in `displaySmall`; beside it `"was {first} on {d MMM}"` in `bodyMedium` `textSecondary`. Wrap below at large font scale.
   - `SegmentedControl("Weight", "Reps", "Sets", onCard = true)`, same mapping as today.
   - `SelectField("Comparison", ...)` and, for the two parametrised metrics, the existing number fields. Show the `"Compare"` selector only when more than one basis exists (unchanged).
   - `ExerciseChart`: height 140 dp (was 220). Line 3 dp in `mark` with round caps and joins; no area fill. Points: draw a 5 dp filled dot only on the selected point and the last point. Two horizontal guide lines (bottom solid, middle dashed) in `divider`. Keep the three y-axis labels (`bodySmall`, `textSecondary`), tap-to-select and gap handling for missing values. Axis dates as `"d MMM"`. Selected-point caption: `"{d MMM}: {value} {unit}"`.
   - Keep the helper sentences for the reps and set-count metrics and `"View data values"`, as `bodySmall` `textSecondary` and a `TextAction`.
4. **Personal best card:** `InShapeCard` with caption (`titleSmall`, `textSecondary`: `"First recorded result"`, `"New record in this period"` or `"Best in this period"`), value in `headlineSmall`, date and metric description in `bodySmall`, previous best in `bodySmall`. For a new record, add an accent `Pill("New record")` at the end of the caption row.
5. **Body card.** One card replaces the old "Body observations", "Pain after workouts" and "Stiffness after workouts" cards.
   - Header: `SelectField(inline = true)` for the body area; below it `"Scale 0 to 10"` in `bodySmall` `textSecondary`.
   - `SegmentedControl("Before", "After", "Next day", onCard = true)`, default `"After"`. Maps to check-in phases `BEFORE`, `AFTER`, `NEXT_DAY`.
   - Legend row, two neutral 32 dp pills 8 dp apart: a 16 dp solid line sample in `pain` and `"Pain, now {v}"`; a 16 dp dashed line sample in `stiffness` and `"Stiffness, now {v}"`. `{v}` is the latest recorded value in the range; with none, the pill reads `"Pain, none"` or `"Stiffness, none"`.
   - Chart (new composable `ObservationChart`), height 100 dp: fixed y range 0 to 10, bottom guide solid, guide at 5 dashed, both `divider`. Pain: solid 3 dp line in `pain`. Stiffness: dashed 3 dp line (dash 7, gap 6) in `stiffness`. A 5 dp dot on each series' last point. A series breaks where a value is missing; a single isolated value draws as a dot. X positions are evenly spaced by observation index.
   - `TextAction("View values")` toggles a list, one line per date: `"{d MMM}: pain {p}, stiffness {s}"`, using `"not recorded"` for a missing value.
   - Empty: `"No observations for {area} in this period."` instead of legend and chart.
   - **Removed:** the Pain/Stiffness toggle, the "Before and after" dumbbell rows (`ObservationPlotRow`), "Missing values are not zero" copy, and the two trailing cards.
   - New pure function in `ProgressCalculator.kt`:
     `fun observationSeries(history, observations, region: String, phase: String, range: DateRange): List<ObservationPoint>` with `data class ObservationPoint(val date: LocalDate, val pain: Int?, val stiffness: Int?)`. Include only qualifying workouts in range that have a check-in of that phase with an observation for the region where at least one of the two values is present; sort by date. Add unit tests in `ProgressCalculatorTest.kt` (empty input, missing values, phase filter, range filter, ordering).

### 8.6 Library: `feature/library/LibraryScreen.kt`

Mockup: `mockups/library-dark.html`. Before: `current/light-06-library-routines.png`, `current/light-07-library-exercises.png`.

1. `ScreenHeader("Library", trailing = IconCircleButton(plus, accent = true))`. Content description and action follow the segment: `"New routine"` or `"Add exercise"`. This is the only create control in the header.
2. `SegmentedControl("Routines", "Exercises")`.
3. **Exercises segment.**
   - Row: `SearchField` (weight 1, placeholder `"Search exercises"`) and a 48 dp square toggle button (14 dp radius, `surface`, star icon; when on, the icon is filled and `mark` coloured), content description `"Favorites only"`, with toggle semantics.
   - Category filter: a horizontally scrolling row of `FilterPill`s, 8 dp apart: `"All"` followed by `exerciseCategories`. Replaces the "Category" selector button.
   - Location: `SelectField("Location", ...)` shown only when exercises have more than one distinct location.
   - Results: one `ListCard`; each `ListRow`: leading `InitialsTile` (first letters of the first two words of the name), title = name, subtitle = nickname if present, otherwise category and equipment name joined with a comma, trailing = last recorded value in Display 16 sp weight 700 with the date under it in 12 sp `textSecondary`. Tap opens the exercise (`onExercise(id)`). Remove the "Open details" buttons.
   - Last recorded value: from the most recent history workout containing a performed item for that exercise. Value = highest completed-set load with its unit (`"27.5 kg"`, `"level 4"`), or `"{m} min"` for duration items, or `"Done"` for completion-only. Date = weekday short name if within the last 6 days, otherwise `"d MMM"`. No history: no trailing content. Implement as a pure function and unit test it; compute once per snapshot with `remember(data.history)`, not per row.
   - No matches: `EmptyState("No matching exercises", "Change your filters or add an exercise.", "Add exercise", ...)`.
4. **Routines segment.**
   - One `ListCard`; each `ListRow`: title = routine title, subtitle = `"{n} exercises"` plus description or `", about {m} min"` (existing logic). Tap expands the embedded `RoutineEditor` exactly as now: the editor is rendered as its own card in place of the list (close the list card before it and reopen after it, or render expanded routines outside the list card).
   - No routines: `EmptyState("Make it your routine", ..., "Create routine", ...)`.

`RoutineEditor.kt`, `ExerciseEditor.kt`, `ExerciseScreen.kt`: see 8.8.

### 8.7 Settings: `feature/settings/SettingsScreen.kt`, `health/HealthControls.kt`, `transfer/BackupControls.kt`

Mockup: `mockups/settings-dark.html`. Before: `current/light-08-settings-top.png` to `light-10-settings-bottom.png`.

Structure: `GroupLabel` followed by a `ListCard`, five groups. Row titles are `bodyLarge` at weight 500. No section headings inside cards and no stacks of outlined buttons.

1. **YOU**
   - `"Name"` row: trailing inline text field, right aligned, `textSecondary`, placeholder `"Add name"`, content description `"Name for your greeting"`. Same save behaviour.
   - `"Weekly goal"` row, subtitle `"Workouts per week"`: trailing `Stepper`. Value shows the goal or `"Off"`. Plus from Off sets 3; plus otherwise adds 1; minus at 1 sets the goal to null (Off); minus is disabled when Off. Content descriptions `"Decrease weekly goal"` and `"Increase weekly goal"`. Replaces the unlabeled switch and the two outlined buttons.
   - `"Workout reminders"` row: trailing `InShapeSwitch` (content description `"Enable workout reminders"`). Subtitle: when on with days chosen, `"{days} at {HH:mm}"` where days are three-letter names joined with commas and `"and"` before the last; otherwise the existing state messages shortened to one line (`"Paused after restore"`, `"Notifications blocked in Android"`, `"Choose at least one day"`). When on, the card continues below the row (inside the same card, after a divider) with: seven day `FilterPill`s in a wrapping row (three-letter labels, full-name content descriptions); a `ListRow("Time", trailing = value)` opening the existing time picker; and the existing conditional actions as `SecondaryButton`s (`"Notification settings"`, `"Activate these reminders"`).
2. **APPEARANCE**
   - One card, padding 16 dp: `"Theme"` (`bodyLarge`, weight 500), then `SegmentedControl("System", "Light", "Dark", onCard = true)` bound to `ThemeSettings`. Changing it applies immediately.
3. **HEALTH CONNECT** (`HealthSettings`)
   - One `ListRow` with a trailing `InShapeSwitch` per existing toggle: `"Share completed workouts"`, `"Steps today"`, `"Sleep last night"`, `"Latest heart rate"`. Do not print "On" or "Off" as a subtitle; use the subtitle only for a real status (permission missing, pending, error text that exists today).
   - Chevron rows `"Privacy and permissions"` and `"Manage Health Connect"` with their existing actions.
   - The explanatory paragraph becomes one `bodySmall` `textSecondary` line under the card.
   - Preserve all permission launchers, availability checks and messages.
4. **YOUR DATA**
   - `ListRow("Create full backup", subtitle = "Last backup {d MMM yyyy, HH:mm}" or "No backup yet")`, `ListRow("Restore backup")`, `ListRow("Export CSV or PDF")`, `ListRow("Home screen widget", subtitle = "On your home screen" or "Not placed yet")`. Actions unchanged (`BackupControls` launchers, `onExport`, `WidgetUpdater.requestPin`).
   - Progress, results and errors that `BackupControls` shows today stay, rendered under the card in `bodySmall`; errors in `error`. The widget instruction message also goes under the card.
   - Under the group: `"Your journal is stored on this phone and works offline. A full backup includes your journal, routines, drafts and original photos; keep it somewhere private."` in `bodySmall` `textSecondary`.

`transfer/ExportScreen.kt`: see 8.8.

### 8.8 Screens without a mockup

Apply the tokens and components; no layout invention beyond these rules.

| Screen | File | Rules |
|---|---|---|
| Exercise editor | `feature/library/ExerciseEditor.kt` | Before: `current/forced-dark-08-edit-exercise-top.png`, `forced-dark-09-edit-exercise-bottom.png`. Group fields under `GroupLabel`s: `BASICS` (name, nickname, type, muscles or labels), `WHERE` (location, machine name, measurement), `MACHINE SETTINGS`, `NOTES` (instructions, personal notes), `LINKS`, `ALTERNATIVES`. Each group is one `InShapeCard`. Selectors use `SelectField`. "Add setting" and "Add link" are `TextAction`s with a plus icon. Helper sentences are `bodySmall` `textSecondary` under their card. "Save exercise" is the `PrimaryButton` in `Page(bottomBar = ...)`. |
| Exercise details | `feature/library/ExerciseScreen.kt` | Photo placeholder uses `surfaceInset`, 24 dp radius. Metadata as neutral `Pill`s. Sections as `SectionLabel` plus cards. One `PrimaryButton` at most; other actions `SecondaryButton` or `TextAction`. |
| Routine editor | `feature/library/RoutineEditor.kt` | Items as `ListRow`s in a `ListCard`; "Start routine" is the single `PrimaryButton`; reorder and remove in a per-row overflow menu or `TextAction`s. |
| Workout details | `feature/history/HistoryScreen.kt` (`WorkoutDetailScreen`) | Title `headlineMedium`; date as `"EEEE d MMMM yyyy"`; neutral `Pill`s for duration, exercise count and effort. "Repeat workout" is the `PrimaryButton` in the bottom bar. "Correct date, timing or note" and "Edit recorded sets" are `SecondaryButton`s. Exercises in a `ListCard` (subtitle = summary; tap opens exercise details); in edit mode show the `SetRow`s under each row. Check-ins as a `ListCard` with three rows (Before, After, Next day), subtitle = recorded summary or `"Not recorded"`, tap opens the check-in. "Delete workout" is a centred `TextAction` in `error`. |
| Timing editor | `feature/workout/WorkoutTimingEditor.kt` | Fields restyled; the confirm action is the `PrimaryButton` in the bottom bar. |
| Check-in | `feature/checkin/CheckInScreen.kt`, `BodyMap.kt` | Region buttons use `SecondaryButton` shape rules (no hardcoded 14 dp shapes). Body map colours from tokens: outline `muted`, selected region `accentContainer` with `mark` outline, pain marker `pain`, stiffness marker `stiffness`. Scale selectors (0 to 10) as a wrapping row of 44 dp `FilterPill`s. Save is the `PrimaryButton` in the bottom bar. |
| Export | `transfer/ExportScreen.kt` | Options as `ListRow`s with switches or `SelectField`s; one `PrimaryButton`. |
| Health privacy | `health/HealthPrivacyActivity.kt` | Wrap in `InShapeTheme(mode)`; body text `bodyLarge`. |
| Home screen widget | `widget/InShapeWidget.kt` | Use day/night colour providers so the widget follows the system theme: background `#F3F4F6` / `#171B20`, title and count `#12161B` / `#F2F4F5`, secondary `#5A6470` / `#A3ADB8`, button container `#C5F25A` with content `#141A05` in both. The widget follows the system setting, not the in-app theme choice. |
| PDF export | `transfer/PdfExporter.kt` | No change (print output). |

## 9. Order of work

Do the phases in order. After each phase the app must compile and the unit tests must pass.

1. **Foundation.** `InShapeColors.kt`, `Type.kt`, `Dimens.kt`, `Shapes.kt`, `ThemeSettings.kt`, new `InShapeTheme.kt`, font files, both `themes.xml`, `MainActivity` system bars, `AppContainer.themeSettings`. Temporarily keep `Palette` as a deprecated bridge only if needed to compile; it must be gone by the end of phase 3.
2. **Components.** Section 7, plus the debug component preview in both themes.
3. **Token migration.** Replace every `Palette`, `Color.White` and `Color(0x...)` usage outside the theme package (section 5.2). Delete `Palette`.
4. **Shell.** Navigation bar and top app bar (8.1).
5. **Screens**, in this order: Today (8.2), Workout (8.3), History (8.4), Progress with the calculator functions and tests (8.5), Library (8.6), Settings with the theme switch (8.7).
6. **Remaining screens and widget** (8.8).
7. **Cleanup and tests.** Dead code, unused imports, instrumented test labels, documentation.

## 10. Verification

Run and report the real output:

```bash
./gradlew :app:testDebugUnitTest
```

```bash
./gradlew :app:assembleDebug
```

If a device or emulator is attached:

```bash
./gradlew :app:connectedDebugAndroidTest
```

Static checks (each must return no matches):

- `Palette.` anywhere under `app/src`.
- `Color.White` or `Color(0x` under `app/src/main/java` outside `ui/theme/` and `widget/InShapeWidget.kt`.
- The characters `·` and `•`, and the sequence ` | ` inside string literals, under `app/src/main`.
- `BorderStroke` on a card (card borders are gone; borders remain only on `OutlineButton`, focused inputs, rings and dashed chart marks).
- ISO date patterns shown to the user: no `Text(` call that prints a `journalDate`, `LocalDate` or week value without a formatter.

Manual checklist on a device, in each of: system dark, system light, in-app Dark while the system is light, in-app Light while the system is dark.

1. No screen is force-darkened; cards are lighter than the background in dark and white with a soft shadow in light.
2. Status bar and navigation bar icons are readable on every screen.
3. Every screen has exactly one accent-filled primary action, or none.
4. Today: all three hero states (no routines, routines, active workout).
5. Workout: log a set, mark done, swap, do later, skip, add and hide a note, reopen a done exercise, finish from the top bar and from the bottom bar.
6. History: month navigation, date picker, day selection and clearing it, empty month.
7. Progress: empty state, one data point, missing values, with and without a weekly goal, all three body phases.
8. Library: search, category pills, favourites toggle, open an exercise, expand a routine.
9. Settings: theme switch applies at once and survives an app restart; stepper reaches Off and comes back; reminders expand and collapse; backup, restore and export still launch.
10. Font scale 1.3 and 2.0: nothing clipped or overlapping on Today, Workout and Settings.
11. Screen width 320 dp: set rows fall back to the stacked layout.
12. TalkBack reads every icon-only control.

Save new screenshots of the six main screens in both themes to `docs/verification/redesign/`.

## 11. Documentation

Update `README.md` where it describes the look, the theme or the screens, and add a short "Design system" section naming `ui/theme/InShapeColors.kt`, `Type.kt`, `Dimens.kt` and `ui/components/` as the single sources of truth. Append the verification results to `docs/verification/implementation-verification.md`.

## 12. Out of scope

- Room schema, migrations, backup and export formats.
- New features beyond the theme switch, the suggested routine and the last-recorded value in the exercise list.
- Localisation, string resource migration.
- Animations beyond Material defaults.
- Dependency upgrades.
