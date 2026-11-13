# InShape Implementation Plan

> **For agentic workers:** Implement this plan task by task with `superpowers:executing-plans`, or `superpowers:subagent-driven-development` if the user explicitly selects delegation. Checkboxes track future implementation. This delivery is the requested plan; it does not authorize implementation, commits, pushes, or pull requests.

**Goal:** Build the complete offline Android workout journal described in the supplied brief, with an interface that closely reproduces all eight supplied reference images.

**Architecture:** One native Android application module using Kotlin and Jetpack Compose. Room is the durable source of truth for the journal, active session, settings and integration work; feature ViewModels expose observable state. Keep calculations in small pure Kotlin functions and isolate Android file, notification, widget and Health Connect APIs behind narrowly scoped adapters.

**Tech stack:** Kotlin, Compose UI and a deliberately customized Material 3 foundation, AndroidX navigation, Lifecycle ViewModel, coroutines and Flow, Room with KSP, Kotlin serialization, WorkManager, Glance, Android Photo Picker, Storage Access Framework, `PdfDocument`, and the stable Health Connect client.

**Spec:** [Original supplied brief](../../references/inshape/original-brief.txt). Visual sources are the [eight reference images](#reference-register). The user's current request is to produce a detailed English Markdown implementation plan. The implementation instructions inside the original brief describe the future application scope.

**Prepared:** 13 November 2026. **Workspace:** `C:\Dev\InShape`.

**Reading order:** [Starting point](#1-verified-starting-point-and-planning-decisions), [visual contract](#2-visual-contract), [structure](#3-application-structure-and-navigation), [data rules](#4-durable-data-and-behavioral-rules), [metrics](#5-observation-and-progress-calculation-contract), [integrations](#6-android-integrations-and-portable-data), [fixtures](#7-deterministic-reference-fixtures), [implementation tasks](#8-executable-implementation-sequence), [verification](#9-verification-protocol-and-release-evidence), [traceability](#10-requirement-traceability), [completion criteria](#11-completion-criteria).

## Global constraints

1. Application name: **InShape**. All application interface text is English. Preserve user-entered names, nicknames, instructions and notes as entered.
2. Implement the full brief through internal stages. None of the required features become an unspecified later MVP phase.
3. The supplied images govern visual design. Written requirements govern behavior when an illustrative image conflicts with them. Accessibility and responsive reflow may change geometry where necessary.
4. The main tabs are **Today**, **History**, **Progress**, **Library**, in that order. Settings is reachable from Today. Secondary destinations have a Back action.
5. Core journal functions, personal images and charts work offline without accounts, servers, subscriptions or a network dependency.
6. Normal installation starts with real empty states. Example dates, names, exercise instructions, counts, health values and connection badges belong only in isolated preview/demo fixtures.
7. No swimming, pool, sauna, rest timer, countdown, stopwatch, live elapsed time, membership balances, visit credits, payments, cost tracking, social feed, calorie/nutrition tracking, subscription, automatic progression, readiness score or sleep-based prescription.
8. Static start times, saved duration metadata, manually entered activity duration and routine duration estimates are allowed. They must never become a running clock.
9. Goals, reminders, observations and Health Connect are optional. They never gate saving or using the local journal.
10. Never use middle dots, bullets, circles or other round glyphs as separators between words or values in interface text. Use sentences, commas, spacing or separate lines. Preserve functional circular controls and chart markers shown in the references; those are not text separators.
11. Do not add decorative colored strips on the left edge of cards. The complete green outlines in the active-exercise and routine references are intentional and should be retained.
12. Validate **320 dp width**, **200% font scale**, and **320 dp with 200% font scale together**, as well as the reference comparison size.
13. Work locally. No commit, push, publishing, deployment or pull request without the user's applicable explicit authorization. Never create a pull request for this user.
14. Follow applicable workspace instructions, including scanning files with `sonar analyze secrets <path>` before reading them. Preserve existing work and clean up only disposable artifacts created by the implementation task.

## Review focus

These additional conditions are assigned to tests in the task list rather than left to final manual review.

| Condition | Required result | Owning task |
| --- | --- | --- |
| The process dies between recording a set and the next screen, or two start/finish requests overlap | Recover committed work, keep one active session, finish only once | T02, T05, T15 |
| An exercise/machine is renamed or archived after a workout | Historical names, measurement basis and recorded setup retain their meaning | T03, T08 |
| A score is missing, intentionally zero, cleared, or recorded in another phase | These remain distinct; paired graphs match the correct workout, region and phase | T07, T09 |
| Local dates change across midnight, a Monday boundary, daylight saving or travel | Stable historical dates, correct current week and corrected reminder/widget schedules | T02, T10, T11 |
| A backup or Health Connect operation is interrupted after partially completing external work | Current local data survives; retries do not duplicate exported workouts or activate restored permissions | T12, T13 |

## 1. Verified starting point and planning decisions

The workspace was empty when inspected. It has no Git repository, Gradle project, implementation, dependency catalog or tests. No existing `AGENTS.md` was found in the workspace or the checked `C:\Dev` and `C:\` ancestors; the instructions supplied in the conversation apply. Consequently, every source path below is **proposed**, not a claim that the file already exists.

All eight PNGs were found at `C:\Users\EmmaH\Downloads\Gym Journal-png` and visually inspected. Their filenames differ from the numbered names in the brief, but their contents match. Unmodified copies and the original brief are retained under `docs/references/inshape/` so this plan is usable without the Downloads directory or the chat attachment.

Planning decisions, rather than additional user requirements:

| Decision | Rationale and boundary |
| --- | --- |
| Start with one `app` module | This personal local application does not need a multi-module architecture or a server layer. |
| Proposed namespace/application ID `com.emmah.inshape` | A concrete local project identifier; confirm ownership only if distribution later makes that material. |
| Proposed `minSdk = 26` | Supports modern date/time APIs and a useful older-device baseline. Verify every selected dependency against this floor during T01; do not silently raise it. |
| Stable compatible toolchain, pinned in a version catalog | No toolchain exists to preserve. Select current stable versions when scaffolding, record compatibility, and avoid alpha examples copied from documentation. |
| Manual dependency construction in `AppContainer` | Room, repositories and platform adapters can be wired without a DI framework. |
| Store portable preferences in Room | Simplifies consistent backup/restore with the journal. Runtime Android grants remain external state. |
| Reference light appearance is the initial theme | The supplied design is light only. Keep it stable even if the system uses dark mode. A separately designed dark theme is outside this request. |
| Replacement restore, with explicit confirmation | The brief permits replacement or merge when clearly described. Transactional replacement is sufficient; implementing an additional merge engine is unnecessary. |
| Custom Compose charts and body map | Three small chart families and region hit testing need precise reference styling and accessible companion text; no chart framework is necessary. |
| No automatic OS cloud backup of the journal | Configure backup rules consistently with user-controlled portable backup. Do not imply that an untested device-transfer mechanism is the journal backup feature. |

No Android build, dependency resolution, emulator, device or integration check has been run for this planning delivery. SDK/JDK availability and device capabilities are intentionally verified at the beginning of implementation, not inferred from other projects.

## 2. Visual contract

### Reference register

Use these stable IDs in implementation tasks, preview names and visual review evidence.

| ID | Brief filename | Actual source and project copy | Dimensions | Main responsibility |
| --- | --- | --- | --- | --- |
| R01 | `16445.png` | [Pain and stiffness check.png](../../references/inshape/Pain%20and%20stiffness%20check.png) | 780 x 2080 px | Optional phase selector, effort, energy, body map and observations |
| R02 | `16446.png` | [Today.png](../../references/inshape/Today.png) | 780 x 2130 px | Greeting, unfinished session, weekly activity and start actions |
| R03 | `16447.png` | [Exercise and machine.png](../../references/inshape/Exercise%20and%20machine.png) | 780 x 2788 px | Photo gallery, machine settings, instructions, alternatives and history |
| R04 | `16448.png` | [Logging a workout.png](../../references/inshape/Logging%20a%20workout.png) | 780 x 2660 px | Done, Now, Up next, set entry and completion actions |
| R05 | `16449.png` | [Progress.png](../../references/inshape/Progress.png) | 780 x 2760 px | Exercise line/area graph, attendance bars and paired body observations |
| R06 | `16450.png` | [History.png](../../references/inshape/History.png) | 780 x 2290 px | Monday-first calendar, summaries and expandable workout cards |
| R07 | `16451.png` | [Library and routines.png](../../references/inshape/Library%20and%20routines.png) | 780 x 1920 px | Segmented library, expanded routine editor and bottom navigation |
| R08 | `16452.png` | [Settings and data.png](../../references/inshape/Settings%20and%20data.png) | 780 x 2080 px | Grouped settings, reminders, widget, Health Connect and exports |

All are tall content references, not single-device viewport specifications. The 780 px width supports an initial **390 dp comparison canvas at 2 px per dp**. This is a calibration assumption, not known source export metadata. Never fit the whole image into one phone screen or use it as the screen background.

### 2.1 Measured color tokens

These are actual repeated RGB colors sampled from the PNGs, rather than approximate verbal color choices. Use named tokens in one place; do not let dynamic system colors replace them.

| Token | Value | Application |
| --- | --- | --- |
| `Background` | `#F3F6F4` | Pale green-grey screen background |
| `Surface` | `#FFFFFF` | Cards, headers, navigation and unselected buttons |
| `TextPrimary` | `#15211D` | Main headings, body text, darkest primary action |
| `TextSecondary` | `#56645F` | Dates, metadata and helper labels |
| `Accent` | `#2F6B5E` | Resume card, selected states, finish/save buttons, chart line |
| `AccentText` | `#1F4A40` | Text on mint chips and pale panels |
| `Mint` | `#DCEBE5` | Chips, selected navigation background and graph area |
| `SoftSurface` | `#EEF4F1` | Previous-performance panels and metadata badges |
| `SegmentTrack` | `#E3EBE7` | Segmented-control background |
| `Border` | `#DDE5E1` | Subtle card borders |
| `Divider` | `#EDF2EF` | Fine row separators |
| `MutedMint` | `#C9DDD5` | Reference thumbnail surfaces, if no user image is present |
| `AttendanceBar` | `#9CC3B6` | Historical weekly bars |
| `PainMarker` | `#C05A1C` | Pain markers and outlined before points |
| `PainText` | `#A34A14` | Orange numerical text |
| `PainAfter` | `#8A3A0E` | Filled after points |
| `PainSurface` | `#FBEDE4` | Pain summary badge background |
| `PainConnector` | `#E2B79B` | Before/after connector |
| `Stiffness` | `#2D5C99` | Blue stiffness scores and trend values |

Validate contrast for every text/background pairing. Use the darker semantic variant for small text where needed, preserving the reference hue. Missing/disabled states must also have readable text and a non-color explanation.

### 2.2 Geometry, type and components

The following dp/sp values are **initial implementation targets inferred from the images**. Tune them against actual renders before accepting T01 and T15; the source font family is not supplied and cannot be identified conclusively from the raster images.

| Element | Initial target at 390 dp and 100% font scale |
| --- | --- |
| Page horizontal inset | 16 dp; Today header and some actions use 20 dp |
| Section/card vertical gap | 14-16 dp, with 8 dp inside tight groups |
| Card padding | 16-18 dp |
| Standard card radius | 20-24 dp |
| Active exercise/routine card | 20 dp radius, 2 dp full green outline |
| Border/divider | Approximately 1 dp; no heavy shadows |
| Large page heading | 28 sp, bold, approximately 34 sp line height |
| Prominent greeting and graph value | 28-30 sp, bold |
| Card/exercise heading | 20-22 sp, semibold/bold |
| Row title and important action | 16-18 sp, semibold |
| Body and metadata | 14-16 sp, approximately 20-23 sp line height |
| Main Start action | About 56 dp high, full pill radius |
| Secondary actions and segments | Approximately 44 dp visible height, minimum 48 dp interactive target |
| Icons | 20-24 dp, restrained rounded line style, 48 dp touch region |
| Numeric set input | About 44 dp visible height, 12 dp radius, minimum 48 dp usable row |
| Main photo | Approximately 1.79:1, clipped to a 20 dp rounded rectangle |
| Bottom navigation | Four equal cells, mint pill behind selected icon, separate bold selected label |

Start with Android's sans-serif family and explicit weights. Compare glyph widths and line breaks using “Hi Emma”, “Lat pulldown”, “Workouts per week” and “Machine busy, later”. If the system font is materially different, select one bundled licensed sans font after visual comparison; record its source and license. Do not claim an exact font match without evidence.

Implement a small set of shared visual primitives: `InShapeCard`, `PillButton`, `SegmentedControl`, `MetadataChip`, `SectionHeading`, `NumberField`, `EmptyState` and `SaveStatus`. Keep screen-specific compositions inside their features. Reuse accessible Material controls where useful, but override reference-sensitive shapes, padding, colors and typography explicitly.

### 2.3 Responsive behavior

1. Use natural vertical scrolling and safe status/navigation bar insets. Pin secondary headers containing Back and Save/Finish. Keep bottom navigation fixed on main tabs; it is absent on secondary detail/edit destinations.
2. At 390 dp, preserve the reference section order and approximate component proportions. At wider phone sizes, retain the composition. On a wide window, center a column with a proposed 600 dp maximum width rather than inventing a new dashboard.
3. At 320 dp or large text, allow headings and action rows to wrap. Stack paired actions, setting tiles, health cards and summary cards when needed. Do not shrink typography to force a screenshot fit.
4. The active set table becomes per-set stacked fields if four columns no longer fit. Keep Set number, Load/Resistance, Reps and completion labels clear. Do not make the user pan sideways while entering values.
5. Keep the calendar seven columns. At 320 dp the cells cannot each occupy a non-overlapping 48 dp width, so retain clear focusable calendar cells and provide a 48 dp accessible date-selection alternative. Record this limited geometry exception in visual review.
6. Allow phase/metric controls to wrap or use a labeled selection sheet if large text would clip. A horizontally scrollable row is acceptable only when its extra choices are discoverable.
7. Use keyboard insets and bring the focused field into view. Finishing, saving, moving to the next numeric field and dismissing the keyboard must remain reachable.
8. At 200% font scale, increase row height and allow multi-line labels. Charts retain readable axis labels and offer equivalent selectable text rows. Body-map selection always has a textual alternative.
9. Avoid animated charts, pulsing markers, flashing and unnecessary transitions. Routine reordering must work through Move up/Move down actions as well as drag.

### 2.4 Screen contracts tied to the images

#### R02: Today

Reference: [Today.png](../../references/inshape/Today.png).

Preserve this order: date and large greeting, conditional dark-green unfinished-workout card, white weekly-attendance card, dark Start workout pill, outlined Repeat last workout pill, mint routine shortcuts, last-workout card, optional health cards, bottom navigation.

The unfinished card has light text, a horizontal progress track and white Resume workout button. Its numerator, denominator and static started-at time come from the active session. The reference greeting icon is decorative; use a recognizable line settings icon in that same top-right circular space with the label “Settings”. The editable name lives in Settings. With no name, show a natural greeting without an invented “Emma”.

The weekly card has seven Monday-first day markers. Completed days are green with checks, today has a distinct outline, and labels identify the days. Multiple workouts on one day increase the weekly count but do not create fictional extra days. If the optional goal is off, show the count without “of 3”. Missing last workout or routines receive concise useful empty actions. Optional heart-rate context extends the same small white-card style; it does not replace steps or sleep.

#### R04: Active workout

Reference: [Logging a workout.png](../../references/inshape/Logging%20a%20workout.png).

Use a white fixed header with Back, workout name, static start/progress subtitle and green Finish pill. The scroll content has **Done**, **Now**, **Up next** in that order. Done uses a grouped white list with checks; completion-only entries show “Marked done” rather than invented sets.

The Now card has a full green outline, large exercise name, Details action, setup summary, mint previous-performance panel, numeric set rows, Add set, exercise note, then the four actions Mark done, Machine busy, later, Use alternative and Skip today. Keep completed and suggested rows visibly distinct. Details returns to the same session and current item.

The next list shows real upcoming exercises and their planned hints. A bottom check-in card and truthful save status follow it. The source image's “4 of 7” conflicts with its displayed item list; recalculate all counts from the fixture/session instead of reproducing that inconsistency. Reorder and remove actions can be in an item menu without changing the main composition.

#### R03: Exercise and machine details

Reference: [Exercise and machine.png](../../references/inshape/Exercise%20and%20machine.png).

Keep Back, favorite and Edit across the top, then the large title, category/muscle subtitle, large mint photo area, thumbnail strip and dashed Add photo tile. Follow with separate white cards for machine settings, My instructions and saved links, Alternatives, then History with a See chart action.

Settings use pale tiles with a small label and prominent value. The original “Your photo of the machine” becomes an actionable empty state; a real photo replaces it after import. Do not retain three fake thumbnails on an empty exercise. The History card uses real completed performances and accurate record wording. Custom machine names and settings are visible enough to distinguish two similar machines.

#### R07: Library and routine editing

Reference: [Library and routines.png](../../references/inshape/Library%20and%20routines.png).

Keep Library and New routine at the top, the wide Routines/Exercises selector below, then routine cards. The expanded routine uses the reference full green outline, title, calculated exercise count, optional estimate, Done, draggable rows, pale type/duration labels, Add exercise and Start routine. Collapsed routines remain simple title/subtitle/chevron cards.

On the unpictured Exercises segment, reuse the same heading, selector and cards. Provide Add exercise, search, type/location filters and a Favorites filter. On narrow screens the creation action moves below the heading rather than truncating it. Routine duplication/removal and accessible reorder actions live in clearly named menus.

#### R01: Check-in

Reference: [Pain and stiffness check.png](../../references/inshape/Pain%20and%20stiffness%20check.png).

Keep the white header with “Check-in”, “Optional, skip any part” and Save. Below it, use the large Before/After/Next day segment, an effort/energy card, then the larger Pain and stiffness card with Front/Back control, simple mint silhouette, orange/blue explanation and selected-region cards. Place the workout note beneath.

The Before phase shows energy and general feelings without asking for retrospective workout effort. After can include effort. Next day can include energy/feelings and observations without duplicating effort by default. Display Not recorded and Clear controls explicitly. Orange pain and blue stiffness each have a separate labeled 0-10 control and endpoint labels. Make all content beyond the source image's cropped bottom accessible by scrolling.

#### R06: History

Reference: [History.png](../../references/inshape/History.png).

Preserve the large heading and Past workout action, rounded calendar with centered month and chevrons, day labels, workout/today legend, three summary cards, then expandable workout cards. The expanded card shows date, title, menu, pale metadata badges, optional orange observation comparison and exercise rows separated by fine lines.

The first two exercise rows may be the collapsed preview. If seven performed exercise entries exist, the remaining count is five, not the reference's illustrative “4 more”. Include partial performed entries truthfully and identify skipped/incomplete items in details. A selected date filters the cards; a clear selection action returns to the month. Summary labels explain whether they describe the month or selected date.

#### R05: Progress

Reference: [Progress.png](../../references/inshape/Progress.png).

Preserve the exercise selector, segmented metric selector, large current value/change caption, green line/area chart, separate record card, weekly bar-chart card with optional dashed goal, paired before/after observation card, then two compact observation-summary cards and bottom navigation.

Add period controls using the same pill system. The weight line has visible nodes, a restrained grid and pale mint fill; do not smooth it into invented intermediate performance. Attendance bars have rounded upper corners, with the current week darker. Pain comparisons use open Before points, filled After points and horizontal connectors only when both exist. Blue is reserved for stiffness. Include selected-point details and an accessible data view. Keep absent metrics absent and qualify every change caption with its actual period/basis.

#### R08: Settings and data

Reference: [Settings and data.png](../../references/inshape/Settings%20and%20data.png).

Use a Back/header row and three main white grouped cards: goal/reminders/widget, Health Connect, Your data. Add a compact profile-name field in the same design language. Preserve the goal stepper, chosen-day controls, green switches, widget Add pill and row-based export actions.

Add a clear weekly-goal enabled control; default goal and reminders to off until selected. Day initials must have full accessible names, including the two T and two S days. Make reminder time tappable. Add heart rate as a separate Health Connect toggle and source selector when needed. Connection status is derived from capability, permissions and preferences.

Replace the reference's overly broad sharing/privacy copy with: “Your journal is stored locally and works offline. Health Connect sharing, backups and exports are optional.” Describe workout sharing as “Share completed workout summaries through Health Connect.” Do not promise delivery to another named app. Backup status shows the last successful backup from this installation, or “No backup yet”.

### 2.5 Unpictured flows and required states

| Flow/state | Required design and behavior |
| --- | --- |
| Exercise editor | Same rounded fields and cards; name, nickname, type, muscles, location, machine, measurement basis, settings, instructions, notes, links, photos and alternatives. |
| Exercise picker | Search/filter list with selected state, accessible Add action and Create exercise route. Returning preserves the caller's draft/session. |
| Start conflict | Show the actual unfinished workout with Resume, Finish current and explicit Discard. Starting another requires resolving the current one. |
| Finish review | List performed work and incomplete/skipped remainder, editable timing/duration, optional session note, then Save workout. Offer check-in after local saving. |
| Past workout editor | Date first; time and duration optional. Reuse exercise/set entry without a live-session clock or fictitious start time. |
| Timing correction | Date, start/end when known, optional recorded duration and a clear unknown state; show a review prompt for sessions resumed on another date. |
| Backup/restore | System file picker, progress/error state, validated summary and destructive replacement confirmation. |
| Export selection | Date/workout selection and switches for session notes, exercise notes and check-in content; preview or precise content summary before saving/sharing. |
| Empty data | A meaningful next action: Add exercise, Create routine, Start workout or Add past workout. Do not show fixture charts or counts. |
| Loading | Reserve the final layout's approximate space without fabricated numbers or connection badges. |
| Save/file failure | Preserve editable content, show “Could not save” with Retry, and do not display Saved. |
| Unavailable integration | Keep the normal screen usable; show a factual unavailable/permission/no-data state in the relevant card. |

## 3. Application structure and navigation

### 3.1 Proposed file map

All Kotlin production paths below are relative to `app/src/main/java/com/emmah/inshape/`. Test paths mirror their feature under `app/src/test/java/com/emmah/inshape/` or `app/src/androidTest/java/com/emmah/inshape/`. Create only files a task actually needs; this map sets ownership and avoids one giant screen or repository.

| Path/group | Responsibility |
| --- | --- |
| `InShapeApplication.kt`, `MainActivity.kt`, `AppContainer.kt` | Process entry, activity entry and dependency wiring |
| `navigation/AppNavigation.kt`, `navigation/Destination.kt` | Main-tab stacks, secondary routes and validated widget/notification destinations |
| `ui/theme/Color.kt`, `Type.kt`, `Shape.kt`, `InShapeTheme.kt` | Reference design tokens |
| `ui/components/Controls.kt`, `Cards.kt`, `SaveStatus.kt` | Small shared primitives and persistence feedback |
| `data/JournalDatabase.kt`, `JournalEntities.kt`, `JournalDao.kt`, `DatabaseMigrations.kt` | Room schema, constraints, relations, queries and explicit migrations |
| `data/JournalModels.kt`, `JournalRepository.kt` | Typed models, transactional domain operations and reactive queries |
| `data/JournalDateRules.kt`, `NumericInput.kt` | Stable date semantics and locale-tolerant decimal parsing |
| `feature/today/TodayScreen.kt`, `TodayViewModel.kt` | Today data composition and start/resume entry points |
| `feature/workout/WorkoutScreen.kt`, `WorkoutViewModel.kt`, `WorkoutActions.kt`, `WorkoutTimingEditor.kt` | Active session, atomic commands, completion semantics and timing correction |
| `feature/library/LibraryScreen.kt`, `LibraryViewModel.kt`, `ExerciseScreen.kt`, `ExerciseEditor.kt`, `ExercisePicker.kt`, `RoutineEditor.kt` | Personal library, photos/setup presentation and routine editing |
| `feature/checkin/CheckInScreen.kt`, `CheckInViewModel.kt`, `BodyMap.kt`, `BodyRegion.kt` | Optional observations and accessible region selection |
| `feature/history/HistoryScreen.kt`, `HistoryViewModel.kt`, `WorkoutDetailScreen.kt`, `PastWorkoutEditor.kt` | Calendar, record browsing/correction and past entry |
| `feature/progress/ProgressScreen.kt`, `ProgressViewModel.kt`, `ProgressCalculator.kt`, `ProgressCharts.kt` | Metric definitions, records and rendering |
| `feature/settings/SettingsScreen.kt`, `SettingsViewModel.kt` | Name, goal, reminders, integrations and data actions |
| `media/PhotoStore.kt`, `media/PhotoViewer.kt` | Durable owned image import, viewing and replacement |
| `reminders/ReminderScheduler.kt`, `ReminderReceiver.kt`, `ScheduleChangeReceiver.kt` | Local wall-clock scheduling, notifications and schedule recovery |
| `widget/InShapeWidget.kt`, `InShapeWidgetReceiver.kt`, `WidgetUpdater.kt` | Real Android widget and refresh work |
| `health/HealthConnectGateway.kt`, `HealthSharingWorker.kt`, `HealthContextReader.kt`, `HealthModels.kt` | Optional platform availability, export outbox and scoped context reads |
| `transfer/BackupService.kt`, `BackupFormat.kt`, `RestoreService.kt` | Portable versioned backup and validated replacement |
| `transfer/CsvExporter.kt`, `PdfExporter.kt`, `ExportScreen.kt` | Actual-performed CSV bundle and paginated PDF export |
| `app/src/debug/java/com/emmah/inshape/demo/DemoData.kt` | Explicit deterministic debug-only data; never seeded into the normal journal |
| `app/src/main/res/values/strings.xml` | English product text, including accessibility names |
| `app/src/main/res/xml/` | Widget metadata, file-provider paths and OS backup rules |
| `app/schemas/` | Exported Room schema versions |
| `docs/verification/implementation-verification.md` | Actual implementation checks and limitations, created during execution |

Build scaffolding consists of `settings.gradle.kts`, root/app `build.gradle.kts`, `gradle/libs.versions.toml`, Gradle wrapper files, `gradle.properties`, `.gitignore`, `AndroidManifest.xml` and a concise `README.md`. Do not create unrelated automation, CI, publishing configuration or signing material for this local implementation.

### 3.2 Navigation contract

| Destination | Inputs | Back/result behavior |
| --- | --- | --- |
| Today / History / Progress / Library | Optional selected date/exercise/library segment | Retain main-tab state and list scroll positions |
| Active workout | `workoutId` | Back leaves the session persisted and returns to its caller |
| Exercise details | `exerciseId`, optional historical `sessionItemId` | Return to the same workout item or library position |
| Exercise editor/picker | Existing ID or draft ID, caller context | Return stable IDs; never pass mutable entity graphs through route strings |
| Routine editor | `routineId` | Explicit save-to-routine semantics, independent of active sessions |
| Workout details / past editor | `workoutId` or draft ID | Refresh all database-derived summaries after correction |
| Check-in | `workoutId`, `phase` | Preserve optional draft; Save commits the observation and returns |
| Settings / export / restore | Selected operation and stable record IDs | Return to Settings with actual outcome |
| Widget/notification entry | Start-or-resume intent | Re-query the active session before acting; a stale intent cannot create a duplicate |

Use lifecycle-aware Flow collection. Persist tab selection/filter state with saved state as needed; persist journal edits in the database. A navigation stack, ViewModel or `rememberSaveable` is never the only storage for performed work.

## 4. Durable data and behavioral rules

### 4.1 Data model

Use UUID strings as stable identities. Store instants in UTC, local dates as ISO `YYYY-MM-DD`, and the relevant zone ID/offset separately. Preserve user text; validate a trimmed copy for emptiness without silently rewriting its stored spelling. Use Room foreign keys and indexes for parent IDs, date/status queries, exercise history and pending integration work. Room is an appropriate structured local persistence foundation; use its transaction/migration support rather than destructive recreation. [Room documentation](https://developer.android.com/training/data-storage/room).

| Entity | Essential fields and invariants |
| --- | --- |
| `Exercise` | ID, name, optional nickname, category, muscle labels, gym/home location, favorite, archived, instructions, general notes, optional equipment ID, measurement-basis key. Categories cover machine, free weight, bodyweight, warm-up, stretching and mobility. |
| `Equipment` | ID, recognizable name/nickname, optional location. Two similarly named machines keep different IDs. |
| `MachineSetting` | ID, equipment/exercise association, label, string value and order. Accept custom labels and values such as `None` or `Wide bar`. |
| `ExerciseLink` | ID, exercise ID, label, saved HTTP(S) URL and order. No external video download. |
| `PhotoAsset` | ID, exercise ID, relative app-owned path, media type, byte length, checksum and order. No old-device absolute paths in the portable format. |
| `ExerciseAlternative` | Owner exercise ID, alternative exercise ID and order. Unique pair; no self-alternative. Selection is user-defined, not coaching. |
| `Routine` | ID, title, optional description and manual duration estimate, modification revision. |
| `RoutineItem` | ID, routine ID, exercise/equipment IDs, order, target mode, optional duration target and note. |
| `RoutineSet` | ID, routine item ID, order, optional load/resistance and repetition targets. These are plans only. |
| `Workout` | ID, title snapshot, source routine/workout ID when relevant, kind `LIVE` or `PAST`, state `DRAFT`/`ACTIVE`/`COMPLETED`, journal date, optional start/end instants and offsets, optional recorded duration, duration source, timing-review flag, session note, revision and timestamps. |
| `Workout.activeKey` | `1` only for the active live session, otherwise null, with a unique index. Database constraints back the one-active-session rule. Past-entry drafts do not consume the active slot. |
| `SessionItem` | ID, workout ID, source exercise/equipment IDs, frozen display name/category/measurement basis/setup, order, disposition, optional alternative-origin item ID, exercise note, discomfort flag, quick-completion timestamp and optional performed duration. |
| `SessionSet` | ID, item ID, order, suggested load/reps, raw draft input, nullable performed load/reps and nullable completion timestamp. An uncompleted suggestion is never a performed set. |
| `CheckIn` | ID, workout ID, phase `BEFORE`/`AFTER`/`NEXT_DAY`, actual observed-at time with zone, optional energy, general feelings, effort and phase note. One editable observation per workout/phase. |
| `BodyObservation` | Check-in ID, stable anatomical region ID, independently nullable pain and stiffness integers in `0..10`, optional note. Unique region per check-in. |
| `Preferences` | Locally editable name, optional weekly goal, reminder preference/days/local time, optional health read/share preferences and last successful local backup metadata. Default optional integrations and reminders to off. |
| `EditorDraft` | ID, editor kind, schema-versioned draft payload and modification time for incomplete exercise/routine/check-in forms. Delete after successful application or explicit discard. |
| `HealthShareState` | Workout UUID/client record ID, highest known client version, last acknowledged revision, returned platform record ID if known, status, last error and restored-hold flag. |
| `HealthOutbox` | Stable operation ID, workout/client record ID, desired revision, upsert/delete operation, retry state and minimal payload needed for deletion after a local record is removed. |

Keep deleted-workout Health Connect tombstones until the corresponding deletion is confirmed or explicitly left unresolved with a visible explanation. Do not cascade them away with the workout.

The table uses semantic entity names. Use an `Entity` suffix for persistence classes where necessary to distinguish them from domain projections. The domain `Workout` consumed by screens/calculators includes its joined session items, sets and check-ins; do not pass a header-only row into a metric that needs performed data. Each draft/query/result type in the task contracts is defined in `JournalModels.kt` or its owning feature and contains the fields specified in this plan, rather than an unrelated parallel model.

Snapshot the exercise's name, machine identity, measurement basis and setup when adding it to a session. A later library edit does not mutate that snapshot. Historical correction of a session is explicit and affects only that record. Old performances retain their stable exercise IDs, so renaming does not split one exercise's history; differing machines or measurement bases still remain separate series.

### 4.2 Numeric and missing-value semantics

Use `BigDecimal` for comparable load/resistance values and a canonical decimal-string Room converter. Do not compare lexicographic strings or binary floating-point weights. Accept both `27.5` and a single locale decimal comma `27,5`, normalize on validation, and preserve an incomplete input such as `27.` as a draft rather than a completed value.

The initial mass unit is kilograms. Support a clearly labeled machine resistance level when it is not kilograms. Distinguish external load, resistance level, bodyweight and assistance; these are different measurement bases. Do not combine differently labeled levels or machines, and do not treat a larger assistance value as a strength record.

Non-negative load including an intentional `0 kg` is valid. Repetitions, when supplied for completed work, must be positive whole numbers. A manually entered performed activity duration must be positive; a missing duration is null. A completed set may have only a known load or only repetitions; it contributes only to metrics for which it has the required values. If neither is supplied, offer completion-only recording instead of creating an empty performed set.

Energy choices are nullable `LOW`, `OK`, `GOOD`. Pain, stiffness and effort are nullable integer scores `0..10`; zero is deliberate data. Clear restores null. No slider's initial handle position is saved until the user explicitly records a score. Display missing values as “Not recorded”, never `0`, an empty chart point or an invented default.

### 4.3 Session state transitions

| Action | Transactional behavior |
| --- | --- |
| Start empty/routine/repeat | In one transaction, return an existing active session if present; otherwise create the workout and copied planned items with `activeKey = 1`. All completion state starts empty. |
| Add exercise | Insert a session-specific snapshot and optional suggestions. Do not modify the library exercise or source routine. |
| Edit numeric field | Persist the raw draft promptly, parse separately, and expose Saving/Saved/Failed based on the durable write result. |
| Complete a set | Validate entered values, copy confirmed values into performed fields and set completion time atomically. A displayed suggestion is usable only after the user explicitly accepts/completes that row. |
| Correct a completed set | Open an explicit edit action, preserve the old performed values while editing, then atomically apply validated values on Update. Cancel retains the old values. An invalid partial draft must not silently replace a completed result. |
| Undo set completion | Clear completion/performed status while retaining editable values as draft suggestions; summaries immediately stop counting the set. |
| Mark done with completed sets | Close the exercise item using the actual completed sets; remaining suggestions stay unperformed. |
| Mark done with no performed details | Explicitly record completion-only, with optional manually entered duration. Show “Marked done”. Never convert all suggested rows to performed sets. |
| Undo exercise completion | Reopen the item, clearing the quick-completion marker where applicable; previously completed detailed sets remain until individually corrected. |
| Machine busy, later | Move the item after the remaining upcoming items, preserving its ID, completed sets and notes. If it is the only remaining item, explain that nothing can move ahead of it. |
| Reorder | Persist new positions atomically; the selected current item follows its stable ID. |
| Skip today | Mark the remaining plan skipped. If sets already exist, keep them and show that only the remainder was skipped. |
| Use alternative | Preserve the original performed work. Add a new snapshot for the alternative linked to the original item, with fresh uncompleted suggestions. Mark the original remainder replaced/skipped, never transfer sets to another exercise ID. |
| Remove an untouched planned item | Remove that planned item. If performed work exists, require explicit confirmation to delete that actual work or offer skipping the remainder. |
| Finish | Review performed work and timing; atomically change ACTIVE to COMPLETED, clear `activeKey`, retain skipped/incomplete dispositions and schedule eligible sharing work once. Repeated finish requests return the already-finished workout. |
| Discard active session | Explicitly confirm deletion, especially when performed work exists. Clear the active slot in the same transaction. |

Use item dispositions `PLANNED`, `IN_PROGRESS`, `DONE`, `SKIPPED`, `INCOMPLETE`. Deferral is a reorder, not a separate performed status. On finish, unresolved planned/in-progress items become INCOMPLETE. `hasPerformedWork(item)` means at least one completed set, explicit quick completion, or explicitly completed manual-duration activity.

The active progress numerator counts DONE items. The denominator is the actual current session-item count, including added alternatives; skipped items are not quietly called done. History's performed-exercise count instead counts items satisfying `hasPerformedWork`. A partial item can contribute actual sets to history without being a fully done exercise.

A completed workout qualifies for attendance only if it contains performed work. Saving a useful note-only historical entry is allowed and labeled as such, but it does not become a workout day, graph point or Health Connect workout. An entirely blank entry is offered Discard rather than counted as a visit.

### 4.4 Repeating, previous values and routine isolation

Repeat from Today or History creates a new ID, new date and new start time. Copy the source exercise order and useful entered values as **suggestions**, reset all set/item completion, duration, check-in and discomfort state, and leave the original unchanged. Preserve skipped source items only as clearly optional planned items, not performed work.

Show the latest prior **completed workout** performance for the same exercise, equipment and basis, together with its date and the user's exercise note. Do not use another in-progress session or an uncompleted set as “Last time”. When no prior performance exists, show “No previous record”.

Routine defaults are copied into session plans. Changing the session does not change the routine. An explicit “Save changes to routine” action previews exercise order and targets, then updates that template only. Routine removal cannot remove session snapshots. Optional starter routines are an explicit import choice; none are silently populated on first launch.

### 4.5 Timing, dates and autosave

`journalDate` is the stable local date chosen when starting/adding the workout. Calendar, attendance and weekly grouping use that date; time-zone changes never rewrite it. Weeks start Monday. Store live-session instants and offsets separately for truthful timing/export. A date-only past entry stays date-only without manufacturing midnight or a Health Connect interval.

Finishing a normal live session offers its actual start/end times and recorded duration. Always make timing editable. Resuming on another local date sets `timingNeedsReview`; the Finish review requires acknowledging or correcting timing, but may retain an unknown duration and still save locally. Do not silently present a long interruption as exercise. A user can correct duration independently of known elapsed span; label the saved duration as recorded duration. Export only an explicitly confirmed supported interval, never derive a fake interval from duration alone.

Use an injected `Clock` and zone provider for calculation tests. Refresh Today on foreground entry and the next local date boundary; refresh widget/reminder schedules independently as described below. No per-second UI updates.

Serialize editor writes in order using an application-scoped repository operation queue and database transactions where required. Persist valid edits and raw field drafts off the main thread without waiting for a navigation event. Completed-set actions await durable commit before showing their saved state. A failed write leaves the draft visible, announces failure accessibly and offers retry. Do not promise recovery of keystrokes that never reached a successful write.

For explicit Save editors, persist a separate recoverable draft as the user types; only Save applies it to the canonical exercise, routine or observation. Back explains unsaved changes and offers keep draft, save or discard where needed. This preserves the reference Save/Done controls while meeting recovery requirements.

## 5. Observation and progress calculation contract

### 5.1 Body observations

Define anatomical region IDs independently of the drawing: neck, upper back, lower back, chest, abdomen, left/right shoulder, upper arm, elbow, forearm/wrist, hip, thigh, knee, calf/shin and ankle/foot. The map can group some visual shapes, but selection must resolve to a labeled region and side before saving.

Front and back share the same anatomical IDs. “Right knee” means the user's anatomical right; the front silhouette's screen position is mirrored appropriately. Use vector paths with actual hit regions, and provide an “Add body area” list with the same labels and current values. Both interaction methods update the same state.

Use labels `0, No pain` to `10, Worst pain` and `0, No stiffness` to `10, Extreme stiffness`. Effort uses `0, Very easy` to `10, Max effort`, distinct from symptoms. Save an actual observation time independently of the workout time; allow correction when adding a check-in later.

For After, the comparison reads the same workout's Before observation for that region, not the latest unrelated observation. Next-day comparisons identify the phase being compared. The workout note is the single session note field, shown consistently in workout/check-in screens; phase-specific observations may additionally have their own note, without silently overwriting the session note.

### 5.2 Exact metrics

All metrics use completed workouts with performed records and apply the selected date range. The proposed initial period options are **4 weeks**, **12 weeks**, **All**, initially 12 weeks. A numbered period includes the current Monday-first week through today and the preceding N-1 full weeks; label the current bar as an incomplete/current week. All starts at the earliest relevant recorded date. Use the same inclusive range for the screen's metrics and summarize the actual comparison dates, not an assumed full period of observations. Offer explicit basis/filter selectors where needed; default to a useful available exercise rather than a fictional Lat pulldown.

| Metric | Calculation | Missing/tie behavior |
| --- | --- | --- |
| General Weight/Resistance | Maximum recorded completed-set load in each workout for the selected exercise, equipment and basis | Rep-only/quick entries have no weight point. Resistance levels show their own unit, not kg. |
| Repetitions | Highest completed-set repetition count per workout; show the associated load when known | Load-only entries have no rep point. Label mixed loads clearly. |
| Reps at X kg/level | Highest reps among completed sets whose normalized load equals selected X | No matching sets means no point, not zero. |
| Kg/level at N+ reps | Highest completed-set load where reps are at least selected N | Label `10+ reps` when N is 10. Do not label it as exactly 10. |
| Set count | Number of explicitly completed detailed sets per selected workout/exercise | Completion-only is reported separately and never becomes one invented set. |
| Personal best | Maximum for the selected comparable metric across eligible history | First qualifying result is “First recorded result”. A tie is not a new record. Show previous strictly lower best with its date when available. |
| Attendance | Count qualifying workouts per Monday-first week | Two workouts on one day count as two workouts. The day marker is still one marked day. |
| Duration total | Sum recorded durations of qualifying workouts in the selected scope | Missing duration is excluded; label known-duration coverage when incomplete. |
| Average effort | Arithmetic mean of recorded AFTER effort for qualifying workouts in scope | Include intentional zero; exclude missing. Show denominator/coverage and at most one decimal. |
| Paired body plot | One row per workout, region and metric; plot recorded BEFORE and AFTER scores | Draw a connector only for a complete pair. A lone score remains a labeled lone point. |
| Next-day trend | Recorded NEXT_DAY values for the chosen region/metric in workout-date order, with observation time in details | Do not substitute After values for missing Next day. |
| Observation summary change | First and last available values within the selected period for the same region, metric and phase | State both dates/basis and sample count; fewer than two records means no change claim. |

For an exercise trend, missing eligible-workout observations break line segments; do not connect across them as though recorded. For one point, show a point and details without an invented slope. Use straight segments between actual comparable observations and discrete markers, not a smoothed health-improvement narrative. Attendance may correctly have a zero bar for a week with no completed workouts; missing personal observations are different.

Recompute these projections from Room updates after corrections, deletions and restoration. Do not store an independently mutable personal-record total that becomes stale. Keep the initial calculations direct and tested; optimize only if measured journal size requires it.

## 6. Android integrations and portable data

### 6.1 Photos, links and sharing

Use `PickVisualMedia`/`PickMultipleVisualMedia`; the AndroidX contract provides a document-picker fallback where the system picker is unavailable. Copy selected content into app-controlled files before confirming addition, so external URI lifetime does not determine journal durability. [Photo Picker documentation](https://developer.android.com/training/data-storage/shared/photo-picker).

Import to a task-owned temporary file, validate that it is a supported decodable image, then rename to a stable asset ID and insert its metadata. On failure, retain the previous photo and remove only the new incomplete file. Decode display thumbnails off the main thread, honor orientation and bound decoded dimensions. Replacement changes the asset reference only after the new image succeeds. Removal deletes an image only when no surviving record references it. The initial new-exercise flow saves the named exercise first and then offers Add photo on its details page, so imported assets always have a durable owning exercise; existing-exercise editing can manage its photos directly.

Saved links accept HTTP(S), show their saved label/destination and open through an Android view intent. Handle no available handler or unavailable connectivity without losing the saved link. Opening an external page does not require embedding a browser or downloading video.

Use `ACTION_CREATE_DOCUMENT` for exports/backups, `ACTION_OPEN_DOCUMENT` for restore, and Android's share sheet with temporary content-URI grants for explicitly selected sharing. Cancellation is not success. Do not auto-send anything or request broad storage access for these picker-based flows. [Storage Access Framework documentation](https://developer.android.com/training/data-storage/shared/documents-files).

### 6.2 Reminders and widget

Persist chosen weekdays, local time and an enabled preference. Request notification permission only when reminders are enabled on platforms that require it; derive effective enabled status from both preference and permission/channel state. Denial never blocks logging. [Notification permission documentation](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Schedule the next selected local occurrence with an inexact `AlarmManager` alarm; reschedule after delivery. These supportive reminders do not require exact-alarm privileges. Settings must describe the selected time as approximate when the OS may defer it. On changes, cancel old pending intents before replacing them. Recalculate after reboot, time/time-zone changes and app update. For a skipped DST time choose the next valid local instant; for a repeated time send only once. [Alarm scheduling documentation](https://developer.android.com/develop/background-work/services/alarms).

Build a real `GlanceAppWidget` with a provider receiver and size metadata. Reuse colors and typography intent, while using Glance's own components rather than ordinary Compose UI controls. Show weekly count/optional goal, Start or Resume, and the last workout if space permits. Widget actions open the app and re-check current database state. [Glance documentation](https://developer.android.com/develop/ui/compose/glance).

Update widgets after relevant data/goal mutations, on provider updates and after restore. Schedule a one-time refresh near the next local midnight plus periodic WorkManager fallback while widgets exist; refresh on clock/zone/boot recovery and app foreground entry. Label the represented week/date so stale content is not ambiguous. Android may defer background updates during idle/force-stop; verify eventual correction and do not claim guaranteed exact-midnight rendering. Cancel unnecessary widget refresh work when the last widget is removed. [Widget state and update documentation](https://developer.android.com/develop/ui/compose/glance/glance-app-widget).

The Settings Add action requests launcher pinning when supported; otherwise show concise launcher instructions. A successful request is not proof the user placed the widget. Determine actual widget instances before saying it has been added.

### 6.3 Health Connect

The official release page checked during planning lists **1.1.0** as stable and **1.2.0-alpha06** as alpha. Use the stable line unless a newer compatible stable release is available at implementation. Some current guides/API pages show preview-only fields; do not adopt those fields just because a sample compiles against an alpha. [Health Connect releases](https://developer.android.com/jetpack/androidx/releases/health-connect).

Check actual SDK/provider availability before offering connection. Health Connect is integrated into Android 14+ and uses a separate provider on supported earlier platforms. Implement the permission/rationale activities and manifest declarations required by the selected supported SDK; avoid hardcoded Connected state. [Health Connect setup](https://developer.android.com/health-and-fitness/health-connect/get-started).

Initial capability permissions are limited to writing exercise sessions and, separately when enabled, reading steps, sleep and heart rate. No routes, calories, location, continuous sensors or broad health history/background reading are required for Today context. Re-evaluate grants on return from the permission UI and every foreground refresh. If only some capabilities are allowed, explain that partial state and keep those controls independent.

| Capability | InShape behavior |
| --- | --- |
| Share workouts | Only eligible locally completed workouts with confirmed valid start/end times. Default mixed gym sessions to strength training; allow an appropriate supported stretching/other-workout category for mobility. Keep detailed sets, photos, setup, observations and private notes local. |
| Steps today | Aggregate from local day start to now. Display date and returned source information. Null/no records is not zero. |
| Sleep last night | Use a clearly defined previous-evening-to-current-morning window and label it; proposed window is previous day 18:00 to current day 12:00, clipped to now. State the period in details rather than calling partial data a full night. |
| Heart rate | Show a labeled latest recorded sample with its timestamp/source, not “live heart rate”. If several origins exist, let the user select one; do not combine overlapping sources into a synthetic measurement. |

Use the platform aggregate API for activity and sleep, preserving null results and returned origins. Its priority-based deduplication applies to activity/sleep, not universally to heart-rate data. Do not sum raw step records or duplicate overlapping sleep records. [Aggregation documentation](https://developer.android.com/health-and-fitness/health-connect/aggregate-data). Read heart-rate records only for the enabled recent period and selected origin, with pagination; no record means No recent data. [Read-data documentation](https://developer.android.com/health-and-fitness/health-connect/read-data).

Write supported `ExerciseSessionRecord` summary fields only, with actual timing and suitable metadata. The journal duration may differ from elapsed time; if the interval is unresolved, retain the local workout and show what must be corrected to share it. Do not export effort using a preview-only field. [Exercise session API](https://developer.android.com/reference/kotlin/androidx/health/connect/client/records/ExerciseSessionRecord).

Use deterministic `clientRecordId = "inshape:workout:<uuid>"` and a persisted increasing client version. Commit desired export revision with the local mutation, then let one worker process the outbox. Retry with the same ID/version, and upsert edits with a larger version. The platform supports versioned upsert through `insertRecords`; never issue a fresh random external identity on a retry. [Write-data documentation](https://developer.android.com/health-and-fitness/health-connect/write-data).

Delete only this app's exported workout by its stored record/client ID, retaining an outbox tombstone until acknowledged. Never delete by a broad date range or modify another app's record. Revoked permission leaves a visible pending/unresolved action. [Delete-data documentation](https://developer.android.com/health-and-fitness/health-connect/delete-data).

Restoration preserves workout UUIDs and external client identities, clears transferred platform record IDs/grants, turns sharing off and marks restored workouts held. Enabling sharing afterwards applies to newly finished workouts; it must not enqueue the entire restored journal. Any explicit re-sharing of a restored workout first reconciles this app's existing record/version, requesting exercise-read access only for that user-selected reconciliation if needed and querying only this app's origin. If reconciliation is unavailable, keep it on hold with a reason. Never choose a new ID to bypass uncertain existing state.

Disabling sharing cancels pending upserts and stops future uploads; it does not silently delete already shared records. Provide an explicit action to remove an individual shared workout where needed. A local deletion made while sharing/access is off retains the necessary pending-deletion state and explains that the shared copy has not yet been removed.

### 6.4 Backup and restore format

Use a ZIP-based `.inshape-backup` file with a versioned portable schema rather than copying the live SQLite database/WAL files.

```text
manifest.json       formatVersion, createdAt, appVersion, record counts, entry sizes/checksums
journal.json        workouts, session items, suggestions, performed sets, observations
library.json        exercises, equipment/settings, photos metadata, links, alternatives, routines
preferences.json    portable preferences; no platform permission or widget grants
photos/<asset-id>.<extension>   actual image bytes
```

Include the active session and recoverable editor drafts as well as completed records. Include original IDs, snapshot fields, completion status, dates/times, custom settings, favorites and applicable preferences. Do not include imported Health Connect context caches or private Android grants. Retain client identities for duplicate prevention but not blindly executable sync queues from another device.

Last successful backup on this installation is operational metadata, not a transferred preference. Restore must not turn the old phone's backup timestamp into a claim that the new phone has created a backup. Keep the archive's creation time in the restore summary and report a local backup date only after an actual successful export here.

Create a consistent database snapshot in a read transaction, then copy the referenced immutable assets while preventing their physical deletion until export ends. Generate and close a complete archive before marking success. Record last successful backup only after the destination write closes successfully; opening or canceling a picker does not update the timestamp.

Restore sequence:

1. Read into a project/app-owned staging location without changing the current journal.
2. Validate format version, checksums, sizes, supported media, UUID relationships, unique active session, numeric ranges and date/timing consistency. Reject path traversal, absolute paths, duplicate archive entry names and undeclared/oversized entries. Stream large files with limits rather than allocating the archive in memory.
3. Preflight declared uncompressed sizes against actual available staging storage while retaining the existing journal. Enforce declared entry lengths and the total storage budget while streaming, with bounded buffers and bounded image decoding. Apply the same format validation during backup creation so the app cannot knowingly create an archive its own restore rejects. If storage becomes insufficient, fail visibly without truncating data or changing the current journal.
4. Show a summary of what will replace the current journal, including active-session effects and integration resets. Require explicit confirmation. Cancel leaves existing data untouched.
5. Stage validated photos under new owned relative paths. In one Room transaction replace journal/library/preferences rows and reference the new photo paths. Keep current assets until that transaction commits. This avoids a mixed database/files state on failure.
6. On success, remove only old unreferenced assets and staging leftovers; if the process died before commit, old rows/assets remain authoritative and staged unreferenced files can be safely removed on recovery.
7. Reset device-specific widget/pinning state and platform grants. Keep restored reminder preferences dormant until permission/schedule setup is confirmed. Apply the Health Connect hold rules above. Refresh all local summaries and any existing widget instances.

Reject unsupported newer backup versions without touching current records. Add explicit format migrations when the format evolves; never silently drop unknown essential fields to claim restoration succeeded.

### 6.5 CSV and PDF

CSV export is a ZIP bundle because related records need more than one table. Clearly label the action/result as “Export CSV” and “CSV files in a ZIP”. Document UTF-8, comma separation, quoting, ISO dates, canonical dot decimals, empty null fields and stable IDs in an included `README.txt`.

| CSV file | Required columns |
| --- | --- |
| `workouts.csv` | workout_id, journal_date, title, start_utc, end_utc, zone_id, duration_seconds, effort, session_note |
| `exercises.csv` | session_item_id, workout_id, exercise_id, equipment_id, name_snapshot, category, measurement_basis, disposition, completion_kind, performed_duration_seconds, exercise_note, discomfort |
| `sets.csv` | set_id, session_item_id, set_order, completed_at, load_value, load_unit, reps |
| `check_ins.csv` | check_in_id, workout_id, phase, observed_at, zone_id, energy, effort, feelings, note |
| `body_observations.csv` | check_in_id, region_id, pain, stiffness, note |

`sets.csv` contains completed sets only; draft targets are never exported as work. Exercise rows cover performed items, including partial items with recorded work; completion-only is explicit. Observations retain missing versus zero. Escape quotes/newlines correctly. Neutralize spreadsheet-formula-leading user text in spreadsheet-facing CSV, explain the reversible escape in its README, and preserve exact original text in the full backup.

Generate PDF with `PdfDocument` and actual text/layout primitives. Include selected workout dates/timing, performed exercise values, notes and only the check-in content selected in the export screen. Use repeated page headers, units, readable text sizes and proper page breaks; long notes and exercise lists must wrap across pages without clipping. Include a concise content preview/count before the file picker or share sheet. Do not add medical interpretations or automatically include photos or imported health context.

## 7. Deterministic reference fixtures

Create a separate debug/demo database and file directory, activated explicitly. It must never write to the production journal, issue notifications, export real Health Connect records or seed a normal installation. Fix the clock to **13 November 2026** and a documented zone such as `Europe/Helsinki`; make generated UUIDs and data stable.

Use named states rather than one inconsistent fixture: `TodayWithActiveWorkout`, `ActiveWorkoutPartiallyDone`, `ExerciseWithSetup`, `CheckInAfter`, `ProgressWithHistory`, `HistoryNovember`, `LibraryRoutineExpanded`, `SettingsConfigured`. This allows the Today/history references to precede completion while the Progress reference demonstrates a later recorded result. Also create empty, sparse-history and failed-save states.

| Fixture fact | Purpose |
| --- | --- |
| Profile name Emma; Friday 13 November 2026 | Reproduce R02 typography and date hierarchy only in demo |
| Weekly completed workouts on Monday 9 and Wednesday 11 | Reproduce 2 of 3 in Today while Friday is still active |
| Five qualifying November entries, durations 45, 63, 25, 45, 52 minutes, After effort 6 each | Produce History's 5 workouts, 3 h 50 and average 6 consistently |
| Active start 10:42; four closed items; current Lat pulldown; three remaining items | Preserve R04 content with the corrected actual denominator of eight |
| Current set 1: 27.5 kg x 10 completed; set 2: 27.5 kg x 9 completed; set 3: 27.5 kg x 10 suggested | Prove visual/performed-state separation |
| Exercise setup Seat 4, Thigh pad 2, Handle Wide bar, Backrest None | Reproduce R03 and the active setup summary |
| Prior performed loads 20, 20, 22.5, 22.5, 22.5, 25, 25, 27.5 kg | Reproduce R05 shape using real completed fixture sets |
| Lower-back Before pain 5/stiffness 7, After pain 3/stiffness 4; right-knee After pain 1/stiffness 3 | Reproduce R01 while testing independent metrics |
| Attendance bars 1, 2, 2, 1, 3, 2, 2, 2 in the selected demo period | Reproduce R05 reference proportions from actual fixture sessions |

The progress/history/Today named states may use distinct complete datasets because the source screens do not form one perfectly consistent database. Each rendered screen must be internally correct. Label demo entry visibly outside the comparison crop or via a debug-only launcher; never expose demo activation in a normal release.

## 8. Executable implementation sequence

These tasks describe future work. No checkbox is checked by producing this document. Each task includes its own meaningful verification; do not postpone correctness until the final visual pass. Use the file map in section 3; expand paths from the stated production/test roots.

For behavior changes, add the named focused tests first, run them to observe the missing behavior, implement, then rerun. Expected pre-implementation failures are missing behavior/definitions, not a reason to weaken tests. For scaffolding and visual token setup, use the build/render checks rather than artificial tests of constants. Do not commit at task boundaries unless separately requested.

### T01. Establish the Android project and reference design foundation

**Dependencies:** None. **References:** R01-R08, especially R02 and R07.

**Create:** Build scaffolding, app entry/container, navigation files, theme/components, English resources, debug fixture entry and `README.md`.

**Interfaces:** `InShapeTheme(content: @Composable () -> Unit)`; `AppNavigation(container: AppContainer)`; typed `Destination` values for the route table. `AppContainer` owns one database/repository and the platform adapters, with a replaceable clock for tests.

- [ ] Inspect the actual workspace and instructions again. If Git exists by then, inspect branch/status and use a `codex/` feature branch when the current branch is protected. If it remains non-Git, keep work local; do not create commits as setup.
- [ ] Verify installed JDK, Android SDK/build tools and available devices. Select and pin a compatible stable AGP, Gradle, Kotlin, Compose compiler/BOM, KSP and AndroidX combination; record chosen versions and documentation links in the README. Confirm `minSdk 26` compatibility before using any new API.
- [ ] Configure one application module, the proposed application ID, app label, required English resources and separate debug demo entry. Keep demo data/assets out of normal release initialization.
- [ ] Implement the exact color tokens and inferred type/spacing targets. Disable dynamic color and keep the supplied light appearance stable. Use a Compose BOM to align UI dependencies; verify the compiler against Kotlin separately. [Official BOM guidance](https://developer.android.com/develop/ui/compose/bom).
- [ ] Implement main-tab routing, secondary Back behavior, safe insets and a clearly discoverable Settings entry from Today. Pass IDs through navigation.
- [ ] Render a component preview with heading, ordinary card, outlined active card, both pill types, segmented control, numeric row and navigation bar. Compare against R02/R04/R07 before composing all screens.
- [ ] Run `./gradlew.bat :app:assembleDebug :app:lintDebug`. Install on an available emulator/device and verify launch/navigation. Record actual commands, selected versions and any blocked environment step.

**Acceptance:** A buildable, locally runnable shell reproduces the reference visual vocabulary and all navigation destinations are owned by subsequent tasks. Temporary placeholder destinations are tracked and cannot survive final acceptance.

### T02. Implement durable storage, date rules and recoverable drafts

**Dependencies:** T01. **Create:** `data/*`, application-owned persistence wiring, schema exports. **Tests:** `data/JournalRepositoryTest.kt`, `data/JournalDateRulesTest.kt`, `data/NumericInputTest.kt`; instrumented `data/JournalPersistenceTest.kt`.

**Interfaces:** `fun observeWorkout(id: String): Flow<Workout?>`; `fun observeActiveWorkout(): Flow<Workout?>`; `fun observeWorkouts(range: DateRange): Flow<List<Workout>>`; `suspend fun saveEditorDraft(draft: EditorDraft): Unit`; `fun weekStart(date: LocalDate): LocalDate`; `fun parseDecimalInput(raw: String): NumericInputResult`. `DateRange` is inclusive local start/end dates; `NumericInputResult` is Missing, Incomplete, Valid(BigDecimal), or Invalid(message).

- [ ] Define schema version 1 and all stable IDs, snapshots, relations, nullability and constraints from section 4. Export the schema; prohibit destructive migration fallback.
- [ ] Add tests for Monday boundaries, year crossover, a date-only workout after time-zone travel, `27.5`/`27,5`, incomplete input, intentional zero and invalid negative input.
- [ ] Add a database test proving two competing active-session inserts cannot both succeed, while multiple completed/past draft rows remain legal.
- [ ] Implement serialized durable edits, explicit draft persistence and SaveStatus driven by real commit results. Inject write failures and preserve recoverable draft content.
- [ ] Reopen a file-backed database after creating a partially entered session and drafts; assert values, order, completion and current item survive. Use schema validation and test explicit migrations whenever a schema version is later added.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*Journal*Test' --tests '*NumericInputTest'`; run the instrumented persistence test on the supported test device.

**Key assertions:** `weekStart(2026-11-15) == 2026-11-09`; `parse("27,5") == 27.5`; null score differs from zero; successful saves survive close/reopen; failed writes never report Saved.

### T03. Build the exercise/equipment library and durable photos

**Dependencies:** T02. **References:** R03 and Exercises extension of R07.

**Create:** Library/exercise screens, picker and editors, `PhotoStore.kt`, `PhotoViewer.kt`. **Tests:** `feature/library/ExerciseLibraryTest.kt`, `media/PhotoStoreTest.kt`, instrumented `feature/library/ExerciseFlowTest.kt`.

**Interfaces:** `suspend fun saveExercise(draft: ExerciseDraft): String`; `suspend fun archiveExercise(id: String): Unit`; `fun observeExercises(query: ExerciseQuery): Flow<List<Exercise>>`. `PhotoStore` exposes `suspend fun importPhoto(exerciseId: String, uri: Uri): PhotoAsset`, `suspend fun replacePhoto(assetId: String, uri: Uri): PhotoAsset` and `suspend fun removePhoto(assetId: String): Unit`. `ExerciseQuery` carries search text, category/location and favorites-only filters.

- [ ] Test name/nickname search, filter combinations, favorites, distinct machine identity and archive exclusion from new-selection defaults.
- [ ] Implement create/edit/archive and the exact R03 card order. Add custom setting labels/values, free-form instructions/notes, multiple saved links and user-defined alternatives.
- [ ] Implement working photo import, gallery selection, full-image viewer, replacement and removal. Keep the original photo visible if replacement/import fails. Never keep fake occupied thumbnails.
- [ ] Implement external-link opening and useful no-handler/error feedback without changing the saved text.
- [ ] Seed a historical snapshot, rename/archive its exercise and edit the machine setup; assert the historical snapshot is unchanged and the current library reflects the edit.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*ExerciseLibraryTest' --tests '*PhotoStoreTest'`. On device, select a real image, restart the app, revoke the original external access where possible and confirm the app-owned image still works offline.

**Acceptance:** An exercise with multiple real photos, custom setup, instructions, saved link and alternative can be created and found; R03 hierarchy and photo proportions remain recognizable.

### T04. Build routine creation, editing and alternatives

**Dependencies:** T03. **Reference:** R07.

**Create:** `RoutineEditor.kt` and routine repository operations. **Tests:** `feature/library/RoutineTest.kt`, instrumented `feature/library/RoutineEditorTest.kt`.

**Interfaces:** `suspend fun saveRoutine(draft: RoutineDraft): String`; `suspend fun duplicateRoutine(id: String): String`; `suspend fun removeRoutine(id: String): Unit`; `suspend fun moveRoutineItem(itemId: String, targetIndex: Int): Unit`; `suspend fun saveSessionPlanToRoutine(workoutId: String, routineId: String): Unit`. Drafts carry ordered exercise IDs and nullable planned set/duration targets.

- [ ] Test creation, duplication with fresh IDs, removal, order changes and both strength targets and duration/completion-only items.
- [ ] Implement the expanded bordered routine card, counts, optional duration estimate, Done, Add exercise and Start routine exactly as R07's visual contract.
- [ ] Provide drag and Move up/Move down actions, with focus/announcement after movement. Handle first/last boundaries without a confusing no-op.
- [ ] Implement saved alternatives and selection UI. Reject only self/duplicate alternative links; do not infer alternatives from muscles or pain.
- [ ] Implement explicit Save changes to routine with a preview; assert it changes the template but no historical/active snapshots. Template deletion leaves those snapshots intact.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*RoutineTest'` and the routine editor UI test with large text.

**Acceptance:** Full body, short and mobility routines are equally usable, with no forced strength fields. Starter content is offered only through an explicit optional action or demo fixture.

### T05. Implement the workout state machine and completion rules

**Dependencies:** T02-T04. **Reference:** R04 behavior.

**Create:** `WorkoutActions.kt` and transactional repository commands. **Tests:** `feature/workout/WorkoutActionsTest.kt`, instrumented `feature/workout/WorkoutTransactionTest.kt`.

**Interfaces:**

```kotlin
suspend fun startWorkout(source: StartSource): StartResult
suspend fun saveSetDraft(setId: String, load: String, reps: String): Unit
suspend fun setSetCompleted(setId: String, completed: Boolean): Unit
suspend fun correctCompletedSet(setId: String, load: BigDecimal?, reps: Int?): Unit
suspend fun markItemDone(itemId: String, durationSeconds: Long?): Unit
suspend fun reopenItem(itemId: String): Unit
suspend fun deferItem(itemId: String): Unit
suspend fun skipItem(itemId: String): Unit
suspend fun useAlternative(itemId: String, alternativeExerciseId: String): String
suspend fun finishWorkout(workoutId: String, timing: WorkoutTiming): FinishResult
```

`StartSource` is Empty, Routine(id), or Repeat(workoutId). `StartResult` is Started(id) or ExistingActive(id). `FinishResult` is Finished(id), AlreadyFinished(id), or ValidationError(message). `WorkoutTiming` contains optional start/end, optional recorded duration and explicit timing-review acknowledgement. Validation/I/O failures leave the stored session intact and surface through the ViewModel.

- [ ] Test that starting from a routine/repeat creates no completed sets, no copied observation scores and no attendance. Double start returns one active workout.
- [ ] Test completing 27.5 kg x 10, completing a second row, leaving a third suggested, correcting a completed value, canceling an invalid correction, undoing one, and reopening/closing the exercise. Assert exact performed rows after every action.
- [ ] Test quick completion and manual-duration completion without forced sets/reps/load. Closing an item with completed sets does not complete remaining suggestions.
- [ ] Test deferral, accessible reorder, skip-after-partial-work and alternative-after-two-sets; the original two sets must keep their original exercise/item IDs.
- [ ] Implement finish as one idempotent transaction and preserve incomplete/skipped remainder. Distinguish note-only records and completely empty discard from attendance-qualified workouts.
- [ ] Test repeat after a historical correction; suggestions reflect corrected source values, but all performed state is reset. Test duplicate finish under concurrent requests, including one integration outbox event only.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*WorkoutActionsTest'` and the transaction test against Room.

**Acceptance:** Domain tests prove the distinction between planned values, explicit completed sets, quick completion and incomplete work before these values drive screens or charts.

### T06. Complete Today and active workout screens

**Dependencies:** T05. **References:** R02, R04 and R03 when returning from Details.

**Create:** Today/workout screens and ViewModels, timing editor. **Tests:** instrumented `feature/workout/WorkoutFlowTest.kt`, `feature/today/TodayScreenTest.kt`.

**Interfaces:** `TodayViewModel.uiState: StateFlow<TodayUiState>`; `WorkoutViewModel.uiState: StateFlow<WorkoutUiState>`. State contains actual repository projections, pending writes and errors; user actions call T05, never maintain an independent authoritative workout list.

- [ ] Implement all Today start paths: empty, routine, repeat last, resume; route History repeat through the same command. Resolve an active conflict without duplicate creation.
- [ ] Implement R02's conditional resume card, weekly activity, editable greeting name, routine shortcuts and latest qualifying workout. Add optional health card slots for T12 without fake values.
- [ ] Implement R04 Done/Now/Up next, exact setup/last-performance panels, decimal input, add/remove/edit sets, note/discomfort recording and all four exercise actions.
- [ ] Keep secondary Details navigation and Back lossless. Test switching main tabs, background/foreground and activity recreation while the same session remains active.
- [ ] Implement Finish review with skipped/incomplete summary, whole-session note and static timing corrections. A next-day resume cannot silently commit the interruption as known exercise duration. Optional check-in follows successful local save.
- [ ] Test numeric input at 320 dp with IME visible, 200% font scale, and their combination. Use stacked rows/actions where the reference four-column layout no longer fits.
- [ ] Run the Today and WorkoutFlow instrumented tests. Capture R02/R04 demo renders and correct major structural/color/spacing differences before adding more integrations.

**Acceptance:** A user can complete a useful offline workout from Today, leave and return, and finish without recording check-ins or resolving external integration problems.

### T07. Implement optional check-ins and selectable body regions

**Dependencies:** T02, T06. **Reference:** R01.

**Create:** Check-in screen/ViewModel/body map/region definitions. **Tests:** `feature/checkin/CheckInTest.kt`, instrumented `feature/checkin/BodyMapTest.kt`.

**Interfaces:** `suspend fun saveCheckIn(draft: CheckInDraft): String`; `fun observeCheckIn(workoutId: String, phase: CheckInPhase): Flow<CheckIn?>`; `suspend fun matchingBefore(workoutId: String, regionId: String): BodyObservation?`. Draft contains nullable scores and actual observation time; map and list share the same `BodyRegion` IDs.

- [ ] Test a missing pain score, intentional 0, score 10, cleared score, different regions and different workout phases. Assert no default slider position is stored automatically.
- [ ] Build the three phase segments and R01's effort/energy, map and region cards. Hide retrospective effort in Before, allow feelings/notes, and make every field optional.
- [ ] Implement real path hit testing plus a fully accessible region list; confirm anatomical left/right in both front and back views.
- [ ] Add independent pain/stiffness controls with endpoint labels, Clear and keyboard/screen-reader increment/decrement behavior. Distinguish labels and marker styles as well as orange/blue colors.
- [ ] Show only matching Before comparisons. Allow adding/editing observations after completion from workout details, including an entry without any existing check-in.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*CheckInTest'` and `BodyMapTest`. Manually traverse the whole screen using TalkBack or the available accessibility service and large text.

**Acceptance:** Starting/finishing a workout never depends on an observation, and the stored record can distinguish all missing, zero, cleared and phase-specific cases.

### T08. Implement History, past entries and historical correction

**Dependencies:** T05, T07. **Reference:** R06.

**Create:** History screen/ViewModel, workout details and past editor. **Tests:** `feature/history/HistoryTest.kt`, instrumented `feature/history/HistoryFlowTest.kt`.

**Interfaces:** `suspend fun savePastWorkout(draft: PastWorkoutDraft): String`; `suspend fun updateWorkout(workoutId: String, correction: WorkoutCorrection): Unit`; `suspend fun deleteWorkout(workoutId: String): Unit`; `fun historySummary(range: DateRange): Flow<HistorySummary>`. A correction carries an expected revision to prevent a stale editor overwriting a newer change unnoticed.

- [ ] Test month grids including leading/trailing blanks, leap February, Monday-first order, today plus workout on one cell and several workouts on one date.
- [ ] Implement R06 calendar navigation/date selection, scoped summary cards, collapsed/expanded entries, real remaining counts and complete workout details.
- [ ] Implement past entries with required actual date but optional time/duration. Reuse detailed/quick/manual-duration recording without inventing measurements.
- [ ] Add Edit, Delete, Repeat and Add check-in actions. Confirm deletion and state the effect on local data and any pending shared copy.
- [ ] Correct exercise values, date, notes, duration and check-ins; assert Today, calendar, attendance, previous-performance lookup and record inputs observe the change. Reject stale revision overwrites with a refresh/review path.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*HistoryTest'` and the HistoryFlow UI test. Compare the November fixture with R06, including correct counts rather than the reference's inconsistent example remainder.

**Acceptance:** The user can correct or remove an old entry without corrupting templates, unrelated sessions or date-only history.

### T09. Implement progress, personal records and real charts

**Dependencies:** T07, T08. **Reference:** R05.

**Create:** Progress screen/ViewModel/calculator/charts. **Tests:** `feature/progress/ProgressCalculatorTest.kt`, instrumented `feature/progress/ProgressScreenTest.kt`.

**Interfaces:** `calculateExerciseSeries(records: List<Workout>, query: ExerciseMetricQuery): List<MetricPoint>`; `calculatePersonalBest(records: List<Workout>, query: ExerciseMetricQuery): PersonalBest?`; `calculateObservationPairs(records: List<CheckIn>, regionId: String, metric: BodyMetric): List<ObservationPair>`; `weeklyAttendance(records: List<Workout>, range: DateRange): List<WeekCount>`. A metric query contains exercise/equipment/basis, date range and the chosen metric/filter.

- [ ] Test the exact definitions in section 5 with completed, suggested, skipped, quick-only and partial items. Include normalized `25` versus `25.0`, ties, missing values and different machines.
- [ ] Test `27.5 kg x 9` excluded from `10+ reps`, `25 kg x 12` included, and a corrected/deleted best falling back to the actual previous result.
- [ ] Test mismatched phases/workouts, a zero Before score, one missing paired value and a missing intermediate observation. Assert no fabricated zero or connector.
- [ ] Implement R05's line/area, attendance and paired observation chart styles with period/filter selectors, units, selected-point details and accessible data rows.
- [ ] Implement actual personal-record wording and the two summary tiles with explicit dates/phase/sample coverage. Show First recorded result and insufficient-data states where appropriate.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*ProgressCalculatorTest'`; run ProgressScreenTest with empty, single-point and full fixtures, then compare all R05 sections visually.

**Acceptance:** Every graph point, bar, summary and record is traceable to specific actual stored rows and recalculates after historical edits.

### T10. Implement Settings, optional goals and local reminders

**Dependencies:** T06, T08. **Reference:** R08.

**Create:** Settings screen/ViewModel, reminder scheduler/receivers and notification resources. **Tests:** `reminders/ReminderSchedulerTest.kt`, instrumented `feature/settings/SettingsTest.kt`.

**Interfaces:** `suspend fun updatePreferences(change: PreferencesChange): Unit`; `fun nextReminder(days: Set<DayOfWeek>, time: LocalTime, now: ZonedDateTime): ZonedDateTime?`. `ReminderScheduler` exposes `suspend fun apply(settings: ReminderSettings): Unit` and `fun cancel(): Unit`. Use one stable reminder channel and explicit immutable pending intents with non-colliding request identities.

- [ ] Implement the editable name, goal enable/disable and reference stepper. Use a positive integer goal when enabled; zero is represented as Off rather than a failing target. Do not cap workouts to one per day.
- [ ] Test no selected days, next selected weekday, changing time, disabling, permission denial, channel disabled, DST gap/overlap and time-zone changes.
- [ ] Implement chosen-day and local-time controls, contextual notification permission flow, schedule/cancel/reschedule and boot/update recovery.
- [ ] Keep status truthful: a saved reminder preference without effective permission shows that reminders are unavailable, not a successful notification schedule.
- [ ] Add the Settings card structure and real navigation entries for widget, Health Connect, backup/restore and exports. Their final actions are completed in T11-T14.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*ReminderSchedulerTest'`. On device, verify a notification actually appears, opens the correct destination, and no notification appears after disable/cancel.

**Acceptance:** Goals can be absent, reminders can be denied/disabled without impairing logging, and changes do not leave old schedules active.

### T11. Implement and verify the Android home-screen widget

**Dependencies:** T06, T10. **Reference:** R08 for configuration; R02 for content/style.

**Create:** Glance widget/receiver/updater, widget XML and preview resources. **Tests:** `widget/WidgetStateTest.kt`, instrumented `widget/WidgetEntryTest.kt`.

**Interfaces:** `fun buildWidgetState(today: LocalDate, records: List<Workout>, active: Workout?, goal: Int?): WidgetState`. `WidgetUpdater` exposes `suspend fun refreshAll(): Unit` and `suspend fun scheduleNextDateRefresh(now: ZonedDateTime): Unit`.

- [ ] Test weekly count, absent goal, active Resume versus Start, last qualifying workout and Monday rollover without a new database write.
- [ ] Implement a real launcher widget with compact and expanded content based on available dimensions. Avoid exposing private exercise notes or check-ins on the home screen.
- [ ] Connect mutation events to refreshAll; register the specified date/clock/boot/provider triggers and bounded fallback work. Refresh after restore and historical corrections.
- [ ] Implement pinning support/fallback instructions and validate stale widget intents against the repository before starting a session.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*WidgetStateTest'`; place the widget on a real emulator/device launcher, resize it, finish/correct a workout and confirm it changes.
- [ ] Test a local week transition without inserting a workout, plus app process termination. Record launcher/OS scheduling limitations rather than claiming an exact refresh guarantee.

**Acceptance:** The feature exists outside the app on Android's home screen and opens the actual intended screen without duplicate sessions.

### T12. Implement optional Health Connect with retry-safe sharing

**Dependencies:** T08, T10. **References:** R08 and health context extension of R02.

**Create:** Health gateway/models/context reader/sharing worker, manifest/rationale wiring. **Tests:** `health/HealthSharingTest.kt`, `health/HealthContextTest.kt`, instrumented `health/HealthConnectFlowTest.kt`.

**Interfaces:** `fun availability(): HealthAvailability`; `suspend fun grantedCapabilities(): Set<HealthCapability>`; `suspend fun readContext(date: LocalDate, enabled: Set<HealthCapability>): HealthContext`; `suspend fun upsertWorkout(payload: WorkoutSharePayload): ShareReceipt`; `suspend fun deleteOwnWorkout(clientRecordId: String): Unit`. Capability values are ShareWorkouts, Steps, Sleep and HeartRate; state distinguishes unavailable, permission-needed, enabled, no-data and failed/pending.

- [ ] Confirm the stable SDK surface and official platform setup again. Implement actual availability/grant checks and separate capabilities; no fake Connected switch.
- [ ] Test disabled capability causes no read, revoked permission clears displayed context, null differs from zero and overlapping-source steps/sleep use aggregation. Heart rate must retain its selected source/time.
- [ ] Implement eligible summary mapping, outbox writes and one worker per journal; verify local save is complete before any external work.
- [ ] Test network/provider failure before write, failure after platform acceptance but before local acknowledgement, retry with unchanged identity/version, higher-revision edit and a deletion tombstone.
- [ ] Test disabling sharing during queued work and deleting while permission is revoked. Workers re-check current desired revision/capabilities before a request and do not resurrect deleted workouts through stale upserts.
- [ ] Implement restoration holds and selected-record reconciliation. Test re-enabling after restore enqueues zero historical uploads and never regenerates client identities.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*HealthSharingTest' --tests '*HealthContextTest'`. Exercise grant/deny/revoke, real export, edit and deletion against a supported Health Connect provider where available.

**Acceptance:** Fake-client tests and live-platform tests are reported separately. Missing provider/device capability remains an explicit unverified integration check, never a completed mock integration.

### T13. Implement full portable backup and atomic replacement restore

**Dependencies:** T03-T12 data models. **Reference:** R08.

**Create:** Backup format/service/restore service and Settings transfer UI. **Tests:** `transfer/BackupRestoreTest.kt`, instrumented `transfer/RestoreRecoveryTest.kt`.

**Interfaces:** `suspend fun createBackup(destination: Uri): BackupResult`; `suspend fun validateBackup(source: Uri): ValidatedBackup`; `suspend fun replaceJournal(backup: ValidatedBackup, confirmation: RestoreConfirmation): RestoreResult`. The validation result owns a bounded staging directory and summary; confirmation is tied to that validated archive identity, not a different later file.

- [ ] Write a round-trip fixture containing every entity, an active partial session, null/zero scores, decimal loads, multi-line Unicode notes, several real image files and external identity metadata.
- [ ] Implement the versioned archive, coherent database/photo snapshot, checksums and actual destination-success timestamp. Track and clean owned staging files on success, cancel and failure.
- [ ] Validate corrupt/truncated data, unsupported versions, path traversal, missing photo bytes, checksum mismatch, invalid relationships/ranges, oversized content and insufficient space before replacement.
- [ ] Implement the summary/confirmation and transaction sequence from section 6.4. Inject failures before asset staging, during the database transaction and immediately after commit; assert the journal is always wholly old or wholly restored with usable photo references.
- [ ] Preserve necessary existing local external-deletion tombstones outside replaced content. Cancel old upserts; restoration itself neither deletes remote history nor re-uploads the archive. Explain pending remote state accurately.
- [ ] Reset permissions/effective reminder/widget/Health Connect state while retaining appropriate portable preferences. Recalculate every dependent summary and record.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*BackupRestoreTest'` and RestoreRecoveryTest. Restore on a second fresh app installation/emulator with different storage paths and inspect all photographs offline.

**Acceptance:** Damaged backup leaves current data unchanged; successful restore preserves the complete journal and actual assets without transferring permissions or creating duplicate shared workouts.

### T14. Implement usable CSV and PDF exports

**Dependencies:** T08, T09, T13 transfer UI conventions. **Reference:** R08.

**Create:** CSV/PDF exporters, export selection/preview screen and content-provider paths. **Tests:** `transfer/ExportTest.kt`, instrumented `transfer/ExportFlowTest.kt`.

**Interfaces:** `suspend fun exportCsv(selection: ExportSelection, destination: Uri): ExportResult`; `suspend fun exportPdf(selection: ExportSelection, destination: Uri): ExportResult`. Selection contains workout IDs plus explicit note/check-in inclusion preferences; an export snapshot is fixed before rendering to avoid mixed concurrent edits.

- [ ] Test a partial workout with completed/suggested sets, completion-only exercise, missing effort, zero pain, commas/quotes/newlines, non-ASCII text and spreadsheet-leading formula characters.
- [ ] Generate the documented linked CSV files and format README; confirm no planned set appears in performed rows.
- [ ] Implement the selection/count/content preview, generate paginated PDFs and test long notes, long exercise names, many rows and Unicode. Render/inspect an exported PDF rather than accepting successful file creation alone.
- [ ] Implement save/share through normal Android mechanisms with temporary URI access, meaningful cancellation/failure states and bounded cache cleanup after use. Do not delete a shared file before its recipient has had a reasonable opportunity to read it.
- [ ] Run `./gradlew.bat :app:testDebugUnitTest --tests '*ExportTest'`; run ExportFlowTest and open the actual CSV bundle/PDF in independent viewers.

**Acceptance:** The user chooses exactly which records/notes to export; files are readable and consistent with history, and nothing is sent without a deliberate share action.

### T15. Perform complete flow, reference and accessibility verification

**Dependencies:** T01-T14. **References:** R01-R08, all mandatory.

**Create/update:** `app/src/androidTest/java/com/emmah/inshape/EndToEndJournalTest.kt`, final debug fixtures, README and `docs/verification/implementation-verification.md`. Retain only the final necessary reference comparison images under `docs/verification/reference-comparison/`.

- [ ] Run the end-to-end journeys in section 9 using a fresh normal installation and a separate reference demo installation. Check that normal data remains empty until the user acts.
- [ ] Run all domain tests, instrumented tests, debug assembly and lint. Use actual task names resolved from the pinned build; document any unavailable task/tool instead of fabricating a pass.
- [ ] Capture every reference state at the calibrated width and perform the full visual comparison protocol below. Correct material differences in composition, color, typography, spacing, iconography, controls and charts.
- [ ] Verify the entire responsive/accessibility matrix, including keyboard behavior, TalkBack equivalents, 320 dp, 200% font scale and their combination. Validate release/default launch has no sample records or mock integrations.
- [ ] Exercise process death, device reboot, denied/revoked permissions, historical correction, corrupt restore and actual exports. Distinguish real-provider/device evidence from fake-adapter tests.
- [ ] Stop only temporary servers/emulators/helpers started for this task, close/reset every capture session and verify owned capture helpers stopped without starting another capture. Remove owned disposable artifacts; preserve final evidence and the verified APK.
- [ ] Record the exact APK path, build/run instructions, checks actually run and specific remaining limitations. If functional scope remains blocked, do not call the complete application finished.

**Acceptance:** The full requested scope is implemented and verified to the environment's actual capabilities, with all eight reference comparisons documented and no undisclosed placeholder actions.

## 9. Verification protocol and release evidence

### 9.1 Main end-to-end journey

Use an isolated test journal. Keep the journal under test offline except for explicitly tested platform integrations.

| Step | User action | Required evidence |
| --- | --- | --- |
| 1 | Install normally and open each main tab | English empty states, no Emma/date/sample workouts, no connected health badge, no default reminder |
| 2 | Create a Lat pulldown exercise with a recognizable machine name, two actual photos, Seat 4 and Thigh pad 2, a note and a saved link | Details matches R03; photos and setup remain after reopening offline |
| 3 | Create another exercise and set it as a personal alternative | Alternative picker identifies the actual saved exercise |
| 4 | Create Full body A with a quick-completion warm-up, the machine exercise and a manual-duration mobility item | R07 layout, correct order/count, optional fields and accessible reorder |
| 5 | Start that routine and try starting another from Today/widget | One active ID and a useful Resume/resolve path |
| 6 | Enter 27.5 kg x 10 and 27.5 kg x 9, complete both, leave a third suggestion | Two performed sets only; distinct unfinished row; truthful autosave |
| 7 | Navigate to Details, switch tabs, terminate/relaunch the app process, then reboot the test device | Same session, two completed sets, draft suggestion, notes, order and static start time recovered |
| 8 | Select an alternative, defer another item, skip the remainder and mark the warm-up done without sets | Original sets remain on the original machine; quick completion is not a fabricated set |
| 9 | Finish twice rapidly, correcting timing if needed, and skip the offered check-in | One saved workout, one attendance increment, no duplicate sharing operation |
| 10 | Add Before and After observations later, including a zero, a missing score and then a cleared score | Correct phase/time association; missing/zero remain distinct; no starting/finishing gate |
| 11 | Inspect History, all exercise rows, Progress, selected chart points and personal records | Values trace to the two completed sets/quick record; no suggested-set contribution |
| 12 | Correct a past set and workout date, then delete another test record | Today, calendar, period summaries, chart, record and widget all recompute |
| 13 | Repeat the corrected workout and inspect the new session | Fresh IDs/timing, corrected suggestions, zero completed sets and no copied observations |
| 14 | Back up, restore on a fresh installation, then attempt corrupt restore | Full assets/data survive valid restore; corrupt restore changes nothing; integrations remain ungranted/held |
| 15 | Export selected workouts to CSV and PDF, with selected notes/check-ins | Independent file viewers show usable content matching selection and performed records |
| 16 | Enable/deny/revoke reminders and optional health capabilities; place/use the real widget | Local journal remains usable; external states and retry behavior are truthful |

Separately test a note-only past entry, an entirely empty active workout, a mobility-only routine, long user-entered names/notes and a file-write failure. These must not be disguised as strength sets or successful saves.

### 9.2 Responsive, accessibility and platform matrix

| Configuration | Required checks |
| --- | --- |
| 390 dp width, 100% font scale, calibrated 2x image export | All R01-R08 reference states and full scroll content |
| 320 dp width, 100% font scale | All screens, seven-column calendar, action reflow, bottom navigation and set entry with IME |
| 390 dp width, 200% font scale | All main and secondary flows, no clipped labels/values or unreachable controls |
| 320 dp width, 200% font scale | Combined worst-case text/layout check, stacked numeric entry, body-area list and chart data view |
| Larger phone/window, landscape and display cutout | Insets, scrolling, centered content, no essential content beneath system bars or keyboard |
| Screen reader and keyboard/switch navigation where available | Labeled controls, selected state, score values, logical focus order, reorder actions, body-map alternative and chart equivalents |
| API 26 baseline | Core offline journal, photo-picker fallback, persistent storage and export behavior |
| API 33 permission behavior | Notification permission denial/revocation and supported pre-framework Health Connect availability |
| API 34+ with Health Connect provider, plus the chosen target-SDK device | Actual health grants, real write/edit/delete, file sharing and modern system insets |
| Process death and reboot | Durable recovery, schedules re-established as allowed, no duplicate active session |
| Changed local date/time zone and DST | Monday grouping, unchanged historical dates, correct current content and one reminder occurrence |

Select the smallest available device/emulator set that covers these behaviors. A missing test environment is recorded as blocked/unverified with its exact missing capability. Do not claim a physical-device check when only an emulator ran.

### 9.3 Visual comparison protocol

1. Open the exact supplied PNG, not a regenerated approximation. Use the stable R01-R08 IDs and named demo state. Record device API, logical width, density, font scale and fixture state.
2. Render at 390 dp and 100% font scale first. Normalize image scale for comparison, preserve aspect ratio and exclude only Android status/navigation chrome that the reference did not include. Do not crop away a mismatched card or missing content.
3. Compare natural scrolling content in sections. Capture/stitch content once and compare fixed headers/navigation separately; do not duplicate them repeatedly in a tall stitched image. Source images have different whitespace and cropped endings, so compare meaningful landmarks rather than forcing equal canvas height.
4. Compare in order: overall section order and widths; typography and line breaks; card radii/padding/gaps; exact colors and borders; control sizes/states; line icons; chart shape/axes/markers; empty/error behavior.
5. Use side-by-side and 50% overlay/difference views. For initial reference-size calibration, aim for main card edges and horizontal padding within approximately 4 dp, and ordinary vertical gaps within 4 dp once line wrapping is equivalent. These are review targets, not an excuse to ignore visibly different typography or distorted proportions.
6. Inspect color samples from flat regions against section 2.1. Permit antialiasing differences; do not approve substituted accent colors, heavy shadows, square buttons, generic default components or lost mint surfaces.
7. Verify functional state appearance: actual completed versus suggested sets, selected segment, active tab, Before/After markers, missing scores and real connection state. Visual similarity cannot justify incorrect data.
8. Repeat required small-width/large-text cases. Record deliberate reflow as an accessibility adaptation. Never stretch the whole page, shrink text below usability or hide required controls to improve a pixel diff.
9. Retain one final actual capture and one annotated comparison per reference where useful. Record discrepancies and their resolution in the verification report; remove superseded captures made during the task.
10. Close/reset capture sessions immediately after the final capture, verify owned helpers are stopped by read-only process/session inspection and do not take another screenshot to check cleanup.

| Reference | Material mismatch that blocks visual acceptance |
| --- | --- |
| R01 | Missing phase segments, wrong effort/energy grouping, nonselectable silhouette, merged pain/stiffness field, inaccessible region list, omitted lower content |
| R02 | Missing prominent conditional resume card, altered section order, different action hierarchy, absent seven-day row, wrong four-tab navigation |
| R03 | Missing main photo/thumbnail structure, flat ungrouped settings, absent instructions/alternatives/history cards, fake photo placeholders after import |
| R04 | Missing Done/Now/Up next structure, no full green Now outline, hidden previous note/setup, indistinguishable suggestions, timer or absent actions |
| R05 | Static chart images, missing area/bar/paired charts, generic chart styling, fabricated observations, unclear metric basis or unavailable point details |
| R06 | Incorrect Monday-first grid, missing today distinction, absent summary hierarchy, nonexpandable records or hardcoded counts |
| R07 | Missing Routines/Exercises segments, different expanded editor composition, absent reorder/add/start actions, default unstyled navigation |
| R08 | Missing grouped cards, inert toggles, no disableable goal, imitation widget, false connection/backup status or incorrect privacy/sharing copy |

A difference is acceptable only when needed for actual data correctness, accessibility, screen size, platform behavior or an unpictured required function. Name it and cite the applicable requirement. “Looks modern” or personal preference is not justification for replacing the reference design.

### 9.4 Commands and evidence format

After T01 pins the actual build, the expected baseline commands from the project root are:

```powershell
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

Use targeted test filters during development and the full relevant suite for final acceptance. Expected success is a zero exit code with the relevant Gradle tasks successful and no failing test/lint report. A command being prepared or a task being skipped is not execution evidence.

Document the actually generated debug APK, normally `app/build/outputs/apk/debug/app-debug.apk`, only after verifying it exists. Include how to open/build/install/run the project and how to activate the isolated demo. No release signing, Play publication or network deployment is part of this plan.

The implementation verification report must distinguish **Passed**, **Failed**, **Blocked** and **Not run** and include:

1. Build configuration and exact commands that actually ran, with concise outcomes.
2. Unit/instrumented tests and the device/API/width/font settings used.
3. All eight reference IDs with the actual render and visual review outcome.
4. The real-widget, notification and Health Connect checks, separate from test doubles.
5. Backup cross-installation result and independently opened export results.
6. Specific limitations and the missing capability needed to verify them.
7. Retained deliverable paths and confirmation that task-owned disposable files/processes/capture sessions were checked.

## 10. Requirement traceability

| Supplied brief section | Implementation coverage | Proof |
| --- | --- | --- |
| Reference design and approach | Sections 1-3 and 7; T01, T06-T10, T15 | All eight R01-R08 comparisons, responsive matrix, real component rendering |
| 1. Today and starting | T05, T06, T10, T11 | Single-active test; each start/repeat/resume path; attendance excludes empty/unfinished work |
| 2. Workout logging | T02, T05, T06, T08 | Decimal/quick/duration recording; partial alternative; undo; idempotent finish; timing correction |
| 3. Exercise/equipment library | T03, T08, T09 | Actual durable photos, settings/links/search/favorites, immutable historical meaning |
| 4. Routines and alternatives | T04, T05 | CRUD/duplicate/reorder, all entry modes, explicit template update, original sets retained |
| 5. Optional observations | T07, T08, T09 | Optional phases, anatomical regions, null versus zero, paired matching and accessible map |
| 6. History/calendar | T08, T09 | Monday-first month/date handling, actual totals, past entry, correction/deletion propagation |
| 7. Progress/graphs | T09 | Precisely defined metrics, complete-record filtering, records recalculated, sparse data and accessible charts |
| 8. Goals/reminders/widget | T10, T11 | Disableable goals, actual notifications, real launcher widget and date/week refresh |
| 9. Health Connect | T12 | Scoped real permissions, aggregate reads, heart rate, stable upserts/deletes and restore holds |
| 10. Offline/autosave/recovery | T02, T03, T05, T06, T13 | File-backed reopen/process/reboot, durable photos, failure feedback, stable identities/migrations |
| 11. Backup/restore/exports | T13, T14 | Complete asset round trip, transactional validation, selected CSV/PDF and truthful backup status |
| 12. Accessibility/scope/verification | T01-T15, section 9 | English resources, all required widths/scales, meaningful tests and end-to-end flow, no forbidden features |

## 11. Completion criteria

The implementation is complete only when the following are true, or a precise external verification limitation is explicitly reported without calling that check passed:

- [ ] Every required feature in the traceability table has working local implementation rather than a placeholder or static demo.
- [ ] A normal install contains no fixture journal, fake measurements, fake photos, fabricated dates or optimistic integration status.
- [ ] Planned and performed data remain separate through repeat, edits, history, charts, export, backup and restoration.
- [ ] All eight actual screen compositions have been compared to the supplied images and material differences corrected.
- [ ] The 320 dp and 200% font requirements, including their combination, have been checked and all primary actions remain usable.
- [ ] Offline logging, durable photos and active-session recovery have been exercised beyond activity recreation.
- [ ] Real Android notifications and a real launcher widget have been exercised where supported; OS scheduling limits are accurately stated.
- [ ] Health Connect has real supported code, separate capability controls and duplicate-safe operations; real-provider results are distinguished from fake tests.
- [ ] Complete backup restoration with actual images and readable CSV/PDF output has been verified.
- [ ] No prohibited scope, unwanted timer, autonomous training advice, unrequested cloud service or publishing action has been introduced.
- [ ] The final verified APK and concise run/verification documentation are easy to locate; task-owned disposable resources have been checked and cleaned up.

## 12. Status of this planning delivery

This document is an implementation specification and task plan, not an implementation report. The workspace, source brief and eight reference images were inspected; their dimensions and repeated colors were measured; relevant official Android integration documentation was consulted. The source brief and images were copied unchanged into the project for stable references.

The plan's local links, reference-copy integrity, coverage and structure are checked as part of this delivery. All application builds, test cases, screen captures, APK generation and device/integration checks above remain future implementation work. Final dependency versions, exact font matching and OS/device behavior are resolved and verified in their assigned tasks.



