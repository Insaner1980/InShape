# InShape implementation verification

Date: 13 November 2026. Scope: T01–T15 of the supplied implementation plan. All changes are local. The original workspace contained the specification and reference files, without a Git repository; no commit, push or pull request was made.

## Implementation

The app implements an offline Room journal, exercise/machine library with durable photos, ordered routines, one resumable active session, decimal set entry, quick and manual-duration completion, optional phased body observations, historical correction/deletion, actual-record charts, local reminders, a Glance launcher widget, scoped Health Connect integration, full validated backup/restore, and selected CSV/PDF exports. A serial write queue retains failed writes until retry. Normal launch contains no sample data; debug reference fixtures are isolated and disable integrations.

Health sharing uses stable client IDs and increasing revisions. Delete operations survive permission loss. Restore holds incoming external identities and transferred deletion intentions for explicit confirmation; it does not silently upload or delete remote records. Backup validates relationships, sizes, hashes and original image bytes before transactional replacement. Local photo paths are reconstructed for the destination installation.

The fresh read-only review found five actionable issues, all corrected: dangling alternative references after removal, old-date active-session timing review, routine entry-mode preservation, live device-zone changes, and pending shared-record deletions surviving backup/restore. Focused regression tests cover these cases.

## Build and test evidence

Commands actually used from the project root:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.12'
./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
adb -s emulator-5590 shell am instrument -w -r com.emmah.inshape.test/androidx.test.runner.AndroidJUnitRunner
```

Focused device reruns selected a class with `-e class <class>`; reference runs also passed `-e captureLabel <width-font>`. Actual per-class JVM counts and final instrumentation outcome codes are retained in [test-results.json](test-results.json). No failed historical run is counted as a pass.

The full JVM suite passed: 41 tests, zero failures/errors. It covers journal identity and transitions, immutable machine/session snapshots, numeric input, calendar/DST/timing, progress calculations, local-zone changes, ordered failed-write retry, reminder occurrence selection, widget state, Health Connect context/outbox races, archive validation and export selection/escaping. Final debug and instrumentation APK builds passed. Lint reports zero errors and 21 warnings.

The final API 26 suite executed 15 cases: **13 passed, zero failures, two skipped**. The skipped Health Connect cases explicitly require Android 14+ and are not counted as passes. The real launcher-widget test now passes, including pinning support, native launcher navigation, start/resume using one active ID and cleanup. Earlier harness failures were corrected by traversing native RemoteViews, advancing Compose's test clock after platform intents, and closing the activities opened by the widget instead of imposing an ActivityScenario lifecycle on them. [Actual launcher widget](widget.png).

Passed on API 26: actual photo import/reopen and rejected invalid replacement; real SQLite one-active uniqueness and durable reopen; decimal set entry, finish within 25 seconds and repeat without completed targets; anatomical front/back body-map selection; all main tabs/settings; immediate real notification delivery, preservation while rescheduling and cancellation when disabled; full asset restore to a different storage root, injected transaction failure/rollback/retry; five-page Unicode PDF, CSV and FileProvider reads.

Passed on the separate API 36 installation: **two integration cases and five platform cases**, zero failures in those final targeted runs. The integration cases restored the exact API 26 archive into a fresh database/storage root and exercised real Health Connect insertion, version-2 update with the same provider ID, own-record version lookup, deletion and absence afterward. The archive's source/destination SHA-256 matched before validation; both original images decoded, completed 27.5 kg remained distinct from the raw unfinished `27,` draft, zero pain stayed distinct from missing stiffness, and reminders/sharing remained held or disabled.

The five modern platform cases covered revoked Health Connect write permission with usable local storage, all eight reference screens with a tall display-cutout overlay, the decimal/finish/repeat journey, actual notification delivery/cancellation, and PDF/CSV/FileProvider export. Android reported root 780 x 1680 px, width 390 dp, font scale 1.0 and top/bottom insets 96/48 px. All eight scroll/text checks passed.

A final visual check found that the secondary-screen save message overlapped the gesture-navigation area. The bottom status container now applies navigation-bar insets when there is no main tab bar. The final rebuild (`:app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`) passed. Navigation and all eight reference screens passed again on the rebuilt APK at 390 dp/100%, with 48/48 px system insets. These two reruns are recorded separately, not added to a unique-test total.

The native Android notification dialog was also exercised manually: choosing Don't allow left reminders enabled locally, showed accurate blocked-permission feedback, and Android AppOps reported `POST_NOTIFICATION: ignore`. The final [permission-denial capture](modern-permission-denied.png) shows Saved automatically at y=1591–1624 px, entirely above the navigation area beginning at y=1632 px. The native UI bounds and the image were both checked. Intermittent emulator System UI ANRs required Wait; these runs do not establish startup or device performance.

Modern test setup limitations were handled explicitly: the first cold instrumentation process timed out before application startup under host memory pressure. Android package speed compilation completed, normal launch then succeeded, and targeted tests ran. An immediate Health Connect grant/write attempt was rejected by the provider before grants propagated; the successful provider run used settled ADB grants in a fresh instrumentation invocation. These initial attempts are not counted as passes. Reconciliation's Android 14+ own-record access follows the [platform readRecords contract](https://developer.android.com/reference/android/health/connect/HealthConnectManager).

Process/reboot check: the ordinary launcher first had zero journal rows (exercises, photos, routines, workouts, sets and observations). For the destructive recovery check, only the owned test installation then received the isolated synthetic reference database. Its nine core tables had identical SHA-256 `29c27731873c005b3ec2d5dfc967588f557cd09f76a89547f27e0457dbbca7e3` before termination, after normal relaunch, and after emulator reboot plus normal relaunch. The active ID and start `2026-11-13T08:42:00Z` remained identical; Lat pulldown retained completed 27.5 kg x 10 and 27.5 kg x 9, plus an unfinished 27.5 kg x 10 suggestion with null performed values. This proves process and device recovery, beyond an activity recreation. The owned test installation was cleared afterward; the final widget capture shows its empty journal.

Independent export check: pypdf read five PDF pages, all Paragraph 1–90 markers and `Ää Öö`. Poppler rendered the final page; the first and final pages were visually inspected for wrapping, margins and footer separation. Python's CSV reader opened all five linked tables: one selected workout, one completion-only exercise, no fabricated set rows, and empty optional check-in/observation tables. The focused export tests separately cover decimal sets, suggestions, notes, missing/zero scores and escaping.

## Reference and accessibility evidence

Actual captures use natural scroll content, retain fixed chrome once, and exclude only measured Android status/navigation insets in comparisons. Original references are unchanged under `docs/references/inshape/`. Comparison images preserve aspect ratio and do not stretch content to force equal page heights.

All eight screens were traversed to their natural end at 390 dp/100%, 320 dp/100%, 390 dp/200%, 320 dp/200%, 600 dp/100%, and landscape (1280 x 720 px, effective width 592 dp). Every run passed the text extent/line-overflow check. [Layout measurements](device-layout-results.txt) record the actual root, scroll and inset bounds. The [combined stress overview](responsive-320-200.png) shows the final large-text reflow: vertical selectors, full-width month heading, narrow weekday labels, larger app bars, stacked set fields and machine settings. The overview shows first viewports; the automated traversal checks the entire scroll content.

The end-to-end form journey also passed at 320 dp/200%. Separately, an actual native numeric keyboard was opened on the unfinished third set at this size. Hardware input changed repetitions from 10 to 11; Android reported the IME visible and the focused field remained above it alongside Complete set 3. [Keyboard capture](keyboard-320-200.png). This is actual IME evidence, separate from Compose semantic text replacement.

The eight supplied originals, all full actual pages, and 50% overlays were inspected. Core flat colors match exactly: background `#F3F6F4`, surface `#FFFFFF`, accent `#2F6B5E`, primary text `#15211D`. Main card edges use the reference's 16 dp outer inset. Typeface rasterization and full page heights are not claimed pixel-identical. Extra controls below the references remain reachable rather than being cropped out of evidence.

| Reference and comparison | Actual capture | Review outcome and required differences |
| --- | --- | --- |
| [R01 Check-in](reference-comparison/R01-comparison.png) | [Actual](reference-comparison/CheckInAfter.png) | Phase selectors, effort/energy grouping, selectable silhouette and separate pain/stiffness retained. Optional clear actions, feelings, observation time, notes and text-based area selection add height (T07). |
| [R02 Today](reference-comparison/R02-comparison.png) | [Actual](reference-comparison/TodayWithActiveWorkout.png) | Resume card, seven-day row, action hierarchy, routines, last record and four tabs retained. Counts follow actual fixture rows; absent Health Connect data is not fabricated (T06/T12). |
| [R03 Exercise](reference-comparison/R03-comparison.png) | [Actual](reference-comparison/ExerciseWithSetup.png) | Photo region, grouped settings, instructions, alternatives and history retained. Empty photo state is actionable; thumbnails appear only for imported files (T03). Real two-photo import/restore is separately tested. |
| [R04 Workout](reference-comparison/R04-comparison.png) | [Actual](reference-comparison/ActiveWorkoutPartiallyDone.png) | Done/Now/Up next, green outline, previous note/setup, visibly completed versus editable suggested sets and primary actions retained. Correction, exercise notes, optional discomfort, routine save and discard extend the page (T05/T06). |
| [R05 Progress](reference-comparison/R05-comparison.png) | [Actual](reference-comparison/ProgressWithHistory.png) | Real area, weekly bar and paired-observation plots retained. Period, metric basis, score/phase selectors and accessible values add height; records with no selected observation are filtered before the recent-observation limit (T09). |
| [R06 History](reference-comparison/R06-comparison.png) | [Actual](reference-comparison/HistoryNovember.png) | Monday calendar, today outline, summary hierarchy and expandable records retained. Older records collapse; full date entry is optional. Coverage text distinguishes missing effort/duration from zero (T08/T09). |
| [R07 Library](reference-comparison/R07-comparison.png) | [Actual](reference-comparison/LibraryRoutineExpanded.png) | Segments, outlined expanded routine, ordered handles, add/start and collapsed routines retained. Accessible overflow reorder/target controls and routine details are required additions (T04). |
| [R08 Settings](reference-comparison/R08-comparison.png) | [Actual](reference-comparison/SettingsConfigured.png) | Goal/reminder/widget, Health Connect and data groups retained. Name editing, disableable goal, independent heart-rate permission, restore holds and backup success feedback are required. API 26 truthfully reports no provider (T10–T14). |

Accessibility checks cover semantic labels, selected state, body-map anatomical sides, numeric editing, text alternatives and chart value views. TalkBack speech output, switch hardware and an external physical keyboard were not exercised.

## Platform limitations

Status: **Passed** covers the completed build, tests and manual checks above. **Failed** initial startup/grant and harness attempts are described above and superseded by explicit successful reruns, not counted as passes. **Blocked** covers the unavailable exact API images and the bounded cleanup restriction below. **Not run** covers the physical-device, populated third-party health-source and assistive-hardware checks listed here.

API 33 and API 37 system-image directories on this machine contain no bootable system image. Those exact API levels and a physical device have not been exercised. API 26 and the available modern emulator provide the locally available coverage. No personal Health Connect data is read for these tests.

Populated third-party steps, sleep and heart-rate sources were not available on the isolated emulators; their aggregation/absence/permission behavior is covered by unit adapters, not claimed as live populated-source evidence. Long-term alarm delivery through real device sleep/vendor restrictions, TalkBack speech output, external switch/keyboard hardware and release signing/publication were not run. The actual notification and widget checks above do not imply those separate checks passed.

## Deliverables and cleanup

Final APK: `app/build/outputs/apk/debug/app-debug.apk`, 25,265,977 bytes. SHA-256: `4801D4E711FD5C75ED2781AC23DFD11464B816CCC2AC78F93D8CF344E758B455`. Source, Room schema, Gradle wrapper, dependency catalog and run instructions are retained in the project. The APK exists, local report links resolve, and the source secret scan is clean.

Both owned emulators were shut down. Exact recorded launcher, QEMU, console and Gradle process IDs were checked and are no longer running. Captures used one-shot ADB/UiAutomation operations, not persistent Windows capture sessions. No task server or watcher remains.

Superseded captures, intermediate exports/logs/databases, test APK, project build caches and build intermediates were removed. Only the final APK and its small build metadata remain under `app/build`. The final evidence set is under `docs/verification`.

At the end of that run, two read-only emulator `pstore.bin` files (65,536 bytes each) and their enclosing directories remained beneath `C:\Dev\InShape\.superpowers`. All other scratch contents were removed. Automatic approval review rejected both directory removal and forced single-file removal.

## Visual redesign, 14 November 2026

This section supersedes the earlier visual-style and APK details above. Implemented [the redesign plan](../redesign/implementation-plan.md), using its HTML mockups and the three supplied desktop captures found in `C:\Users\EmmaH\Pictures\Screenshots`: `Screenshot 2026-11-13 174602.png`, `Screenshot 2026-11-13 174623.png`, and `Screenshot 2026-11-13 174640.png`. The original references and captures were preserved.

The app now has persistent System/Light/Dark appearance, matching system-bar icon contrast, bundled Space Grotesk, shared colour/type/spacing/shape tokens and shared controls. All six main screens and the secondary editors use the new design; the widget follows system day/night appearance. Added the specified suggested-routine, last-recorded-value and progress calculations. Journal schema, backup/export formats, reminder scheduling and Health Connect logic were preserved. README documents the design system and font licence. No Git repository was created.

### Build and functional checks

Each implementation phase compiled and passed its unit tests. The final build used JDK `21.0.12`:

```powershell
./gradlew.bat --no-daemon :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest
```

**Passed: 49 JVM tests, zero failures/errors, and both APK builds.** New regression coverage checks theme persistence, routine selection/ties, latest completed exercise results, completed-week averages and observation phase/range/missing-value handling.

`./gradlew.bat --no-daemon :app:connectedDebugAndroidTest` passed all **18 tests**, with zero skipped cases, on an isolated API 36 emulator. After the final visual contrast corrections, the same full suite passed again on the rebuilt APK via `adb shell am instrument -w -r com.emmah.inshape.test/androidx.test.runner.AndroidJUnitRunner`. [Final device output](redesign/device-tests.txt).

Coverage includes creating an exercise, decimal-set entry, finishing and repeating a workout, note visibility and persistence, completing/reopening/swapping/deferring/skipping exercises, date picker and month navigation, library search/favourites/opening, immediate theme changes, weekly goal Off/back to 3, reminder expansion, all tabs, SQLite persistence, photo import, actual launcher widget, notification delivery/cancellation, denied and granted Health Connect writes, backup/restore rollback, CSV and multipage PDF/FileProvider access.

Earlier unsuccessful runs exposed two centred-text layout issues, ambiguous test selectors and a missing test-installation notification permission. Text layout and selectors were corrected; notification permission was explicitly granted on the isolated test installation. Final captures also exposed low-contrast legacy text buttons in light mode; these now use the shared TextAction component. Failed attempts are not counted as passes.

Static checks found no legacy Palette/components, forbidden metadata separators, screen-local hardcoded dimensions/colours or card borders. Remaining borders are intentional input focus, button outlines and date rings. Display dates use formatters; editable date/time input formats remain explicit. Source secret scans passed. The owner's `lint-check` and `security-check` scripts were not run, as required by the plan.

### Current screenshots

The captures show actual Compose content, scroll through the entire page and retain fixed navigation/actions once. Sample data comes from isolated debug fixtures, not personal journal or health data.

| Screen | Light | Dark |
| --- | --- | --- |
| Today | [Light](redesign/light-today.png) | [Dark](redesign/dark-today.png) |
| Workout | [Light](redesign/light-workout.png) | [Dark](redesign/dark-workout.png) |
| History | [Light](redesign/light-history.png) | [Dark](redesign/dark-history.png) |
| Progress | [Light](redesign/light-progress.png) | [Dark](redesign/dark-progress.png) |
| Library, expanded routine | [Light](redesign/light-library.png) | [Dark](redesign/dark-library.png) |
| Settings | [Light](redesign/light-settings.png) | [Dark](redesign/dark-settings.png) |

### Layout, appearance and native interaction checks

All eight reference screens passed complete scroll and text-extent checks in **12 configurations, 96 screen traversals**: system Light and Dark at 390 dp/100%, 320 dp/100%, 390 dp/130%, 390 dp/200%, and 320 dp/200%, plus app Dark on a light system and app Light on a dark system at 390 dp/100%. [Measured geometry and results](redesign/layout-matrix.txt). The four theme combinations also passed rendered-background pixel checks: light `#F3F4F6`, dark `#0E1114`. Normal-size captures and the 320 dp/200% Today, Workout and Settings pages were visually inspected. At the smallest large-text setting, navigation labels can wrap; they remain readable and reachable.

The success line in the archived geometry logs refers to the text layout's own measured bounds and line limit. Clipping at the viewport and parent containers was assessed through visual inspection of the captured pages.

Native ADB/UIAutomator checks confirmed that both explicit theme choices survived force-stop and cold relaunch; settled Settings screens showed contrasting status/navigation icons in both themes. Today showed all three hero states: empty journal, a suggested routine and an active workout. Progress showed empty and single-workout fixtures; Before and After selected different recorded observations, while Next day correctly showed no observations. Create full backup and Restore backup opened Android DocumentsUI and were cancelled; Export CSV or PDF opened the export selection screen. No external file was saved or restored in these launcher checks.

At 320 dp/200%, the real numeric IME opened on the unfinished third set. Android reported `mInputShown=true` and `mIsInputViewShown=true`; the focused repetitions field and pinned Mark done action remained above the keyboard. The expanded workout header ended at y=345 px and the content began below it; its title/subtitle did not overlap the scroll content.

Accessibility semantics, icon labels, selected states and large-text layout were checked. TalkBack speech output, assistive hardware, physical devices and a full repetition of every interaction in all four theme combinations were **not run**; the four-combination coverage is the layout/theme matrix, separate from the functional suite. This redesign run used API 36 only; earlier API-level evidence above concerns the earlier implementation.

Final debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 25,226,491 bytes, SHA-256 `784114048CF6769D925F135DEA155CB07A62132A7DC38DEFA288D73FCD5B6C69`.

Cleanup verified: the task-owned emulator, QEMU process, console helper and ADB server stopped; their recorded PIDs are absent. The owned `app/build/redesign-scratch` directory, including its isolated AVD, temporary screenshots, helper script and logs, was removed after checking its absolute boundary and absence of junctions/symlinks. The obsolete Gradle problems report was removed. The verified APK, 12 final screenshots and small verification outputs remain. Existing project caches, original references and earlier verification artifacts were preserved. Captures used one-shot Android screenshot operations; no Windows capture session, preview server or watcher was started.
