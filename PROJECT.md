# InShape project reference

This document describes the Android implementation and the verification evidence available on 2026-11-15. Use it to scope code reviews, choose regression tests and check UI changes against the current app. [README](README.md) covers basic setup. [Android check instructions](tools/README.md) cover runner configuration and contain results from earlier runs.

- [Build and entry points](#build-and-entry-points)
- [Architecture and data ownership](#architecture-and-data-ownership)
- [UI implementation and design references](#ui-implementation-and-design-references)
- [Testing guide](#testing-guide)
- [Check runners and dated evidence](#check-runners-and-dated-evidence)

## Product and boundaries

InShape is a personal, offline workout journal. The normal launcher opens the device's own journal, which starts empty on a fresh installation. A user can build an exercise and machine library, order exercises into routines, start or resume a workout, record performed work, add optional check-ins, correct past entries and inspect progress. Settings exposes appearance, reminders, the launcher widget, Health Connect, backup and export.

Journal data lives in a local Room database and original photo files. The main manifest has no Internet permission, disallows cleartext traffic, disables Android automatic backup and excludes app data from Android backup and device-transfer rules. File pickers, the launcher, notifications, share targets and an available Health Connect provider are Android integrations. Repository scans such as SonarCloud and DeepSec run in the development environment under their configured authorization. [Main manifest](app/src/main/AndroidManifest.xml), [data extraction rules](app/src/main/res/xml/data_extraction_rules.xml)

## Build and entry points

| Area | Current repository configuration |
| --- | --- |
| Project | Single Gradle Android application module, :app, with application ID and namespace com.emmah.inshape |
| Android | compileSdk and targetSdk 37, minSdk 26, versionName 1.0 and versionCode 1 |
| Toolchain | Gradle 9.8.2, AGP 9.5.1, Kotlin and Compose compiler 2.4.21, KSP 2.3.13, Java and Kotlin bytecode target 17. Recorded builds use JDK 21. |
| UI and platform libraries | Compose BOM 2026.09.00, Material 3, Navigation Compose 2.10.2, Room 2.8.5, WorkManager 2.12.0, Glance 1.2.0, Health Connect client 1.1.0 and AndroidX ExifInterface 1.4.2 |
| Tests and coverage | JUnit 4 and Robolectric 4.17 for host tests, AndroidJUnitRunner and Compose UI Test for instrumentation, JaCoCo 0.8.16 for debug JVM coverage |
| Build inputs | Version catalog for pinned versions, Gradle dependency-verification metadata for artifact integrity, Google and Maven Central for project dependencies |

[Gradle settings](settings.gradle.kts), [app build configuration](app/build.gradle.kts), [version catalog](gradle/libs.versions.toml), [wrapper version](gradle/wrapper/gradle-wrapper.properties), [dependency verification metadata](gradle/verification-metadata.xml)

From the project root in PowerShell, use the Android SDK 37 and a configured JDK. The Android SDK location is machine-specific.

~~~powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
.\gradlew.bat :app:lintDebug --console=plain
.\gradlew.bat :app:assembleDebugAndroidTest --console=plain
~~~

The debug APK is at app/build/outputs/apk/debug/app-debug.apk. The instrumentation APK is at app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk. The main manifest registers the regular launcher and Health Connect permission-rationale entry points. The debug manifest adds a separate InShape Demo launcher. [Main manifest](app/src/main/AndroidManifest.xml), [debug manifest](app/src/debug/AndroidManifest.xml)

The exported main activity handles the app's Today, Open Workout and Finish actions. The start-or-resume entry action is accepted by the non-exported widget activity. Review action routing together with the repository's single-active-workout transaction. [MainActivity](app/src/main/java/com/emmah/inshape/MainActivity.kt), [WidgetActivity](app/src/main/java/com/emmah/inshape/widget/WidgetActivity.kt)

## Architecture and data ownership

| Owner | Responsibility and review boundary |
| --- | --- |
| [InShapeApplication](app/src/main/java/com/emmah/inshape/InShapeApplication.kt), [MainActivity](app/src/main/java/com/emmah/inshape/MainActivity.kt), [AppContainer](app/src/main/java/com/emmah/inshape/AppContainer.kt) | Application entry, edge-to-edge theme and navigation, construction of Room and services, injected clock, startup recovery of owned restore staging. The regular database is journal.db. Named test databases use isolated asset roots and can disable integrations. |
| [AppNavigation](app/src/main/java/com/emmah/inshape/navigation/AppNavigation.kt), [JournalViewModel](app/src/main/java/com/emmah/inshape/JournalViewModel.kt) | Destination wiring, lifecycle collection, journal snapshot and save feedback. JournalViewModel waits for storage recovery before exposing the snapshot and serializes writes through a FIFO channel. A retryable failure holds its place until Retry. SQLite constraint failures and other terminal failures can release the originating screen's submission guard. Coroutine cancellation propagates. |
| [JournalDatabase](app/src/main/java/com/emmah/inshape/data/JournalDatabase.kt), [JournalEntities](app/src/main/java/com/emmah/inshape/data/JournalEntities.kt), [JournalRepository](app/src/main/java/com/emmah/inshape/data/JournalRepository.kt) | Room schema version 1, DAOs, transactions, revision changes and relationships. The unique nullable activeKey index enforces at most one active workout. Session rows retain recorded exercise and equipment metadata. The exported schema is the review baseline for storage changes. |
| [JournalModels](app/src/main/java/com/emmah/inshape/data/JournalModels.kt), [NumericInput](app/src/main/java/com/emmah/inshape/data/NumericInput.kt), [RecordedTiming](app/src/main/java/com/emmah/inshape/data/RecordedTiming.kt) | Snapshot assembly, performed-work and attendance eligibility, numeric parsing and timestamp resolution. |
| [TransferController](app/src/main/java/com/emmah/inshape/transfer/TransferController.kt), [BackupService](app/src/main/java/com/emmah/inshape/transfer/BackupService.kt), [ExportService](app/src/main/java/com/emmah/inshape/transfer/ExportService.kt), [PhotoStore](app/src/main/java/com/emmah/inshape/media/PhotoStore.kt) | Picker and operation state, validation and replacement of a journal, temporary export ownership, original-photo persistence. File I/O and image decoding run on the I/O dispatcher. |
| [HealthSharing](app/src/main/java/com/emmah/inshape/health/HealthSharing.kt), [ReminderScheduler](app/src/main/java/com/emmah/inshape/reminders/ReminderScheduler.kt), [InShapeWidget](app/src/main/java/com/emmah/inshape/widget/InShapeWidget.kt), [ActiveWorkoutNotifier](app/src/main/java/com/emmah/inshape/feature/workout/ActiveWorkoutNotifier.kt) | Platform effects derived from persisted journal state or preferences. Check permission, cancellation and stale work at these boundaries. |

[Exported Room schema](app/schemas/com.emmah.inshape.data.JournalDatabase/1.json)

### Workouts, drafts and analytics

- Start sources are empty, routine and repeat. Starting with an existing active workout resumes that identity. Past entry creation makes a separate draft. Multiple taps on the past-workout confirmation are guarded while the write is outstanding.
- A set draft can hold unfinished input such as `27,`. Completing a set validates the numeric values and records its completion time. Routine targets and repeated suggestions are planning data. Performed work is a completed set or a quick completion, including duration-mode completion. Check exercise and equipment identity, unit basis and recorded metadata when changing comparisons or edits.
- A workout may be saved with a note alone. Attendance and exercise progress require a completed workout with performed work. Compare compatible exercise, equipment and basis identities, preserve numeric zero, and keep a gap when the selected measurement is missing. The weekly chart caps its display at 260 recent weeks. The average still covers the full chosen range and calculates complete weeks without allocating a bucket for each older week.
- A regular live workout finishes through a persistent finish draft. The summary captures an end instant and allows timestamp and note edits before confirmation. Past entries and sessions needing timing review use the timing editor. Saving leads to the optional After check-in and workout details. Corrections use revisions to detect stale edits. Unknown timing, manually entered duration and inferred interval duration have distinct representations.
- Start and end are stored as instants. Journal date, workout zone and recorded offsets convey the local context. During a daylight saving overlap, a new ambiguous local time requires an occurrence selection. An unchanged recorded minute can retain its original instant and seconds. A nonexistent local time remains invalid. The date picker can recover from a malformed draft date and allows cancellation before replacement. Backup and persisted timing writes reject workout timestamps outside the signed epoch-millisecond range.
- Check-ins support Before, After and Next day. Pain and stiffness are independent per body region. None is numeric zero and a missing value remains null. New effort choices store Easy 3, Just right 6 and Hard 8. New body choices store None 0, Mild 2, Medium 5 and Strong 8. Untouched historical scores and the original observation timestamp survive a note-only edit. A delayed save may close only the originating back-stack entry.
- Editor drafts are versioned payloads in Room. The finish ID uses the finish: prefix and must match the finish kind. Invalid finish timestamps recover in the editor as unknown timing while retaining the decoded note. A routine's planned duration is validated when the item uses Duration mode. The photo replacement target uses saveable state keyed by exercise ID and is cleared after the picker returns.

[Workout screen](app/src/main/java/com/emmah/inshape/feature/workout/WorkoutScreen.kt), [finish screen](app/src/main/java/com/emmah/inshape/feature/workout/FinishScreen.kt), [timing editor](app/src/main/java/com/emmah/inshape/feature/workout/WorkoutTimingEditor.kt), [check-in levels](app/src/main/java/com/emmah/inshape/feature/checkin/CheckInLevels.kt), [progress calculator](app/src/main/java/com/emmah/inshape/feature/progress/ProgressCalculator.kt)

### Storage and external effects

| Flow | Contract to preserve |
| --- | --- |
| Photo add, replace and retry | Copy the original into owned app storage, bound the input to 32 MiB, validate decoding and keep a checksum. Resolve asset paths beneath the canonical photo root. Replacement commits the new asset before retiring the old file. A failed multi-photo batch resumes at the failed item so successful earlier imports occur once. Thumbnail orientation follows EXIF. |
| Full backup | Explicit version 1 ZIP with manifest.json, journal.json, library.json, preferences.json and original photo entries. Manifest declarations bind allowed paths, byte counts and SHA-256 hashes. Caps include a 1 MiB manifest, 16 MiB per metadata JSON file, 32 MiB per photo and 1 GiB total declared content. Validate path confinement, entry identity and completeness, relationships, dates, timing, drafts and images. |
| Restore | Validate in owned staging before confirmation. Replace the Room journal in a transaction and bind originals to a destination-owned photo generation. Keep the old journal and assets usable on failure. Startup recovery cleans owned abandoned staging. Restored reminders are held if enabled, incoming Health Connect settings are disabled and transferred sharing identities or deletion intent require explicit handling. Appearance is stored in device-local preferences. |
| Export | Selection is by completed workout ID, with independent notes and check-in options. CSV is a ZIP containing workout, exercise, set, check-in and body observation tables plus README.txt. Exercise rows represent performed work, and set rows represent completed sets. PDF is a report for the selected records. The grant-enabled FileProvider is non-exported and exposes only cache exports/. |
| Health Connect | Separate capabilities for workout sharing, steps, sleep and heart rate. Resolve provider availability and permissions at use time. Use stable client record identity, a monotonic revision and an outbox for upsert/delete. Reconciliation and removal coordinate with the repository's sharing lock. Pending deletes survive permission loss. Restore holds transferred identities instead of triggering external changes. |
| Reminders and active timer | Reminder alarms are inexact, local-time schedules. Reconcile on boot, time change, timezone change and package replacement. Reminder and active-workout notifications are separate. The active notification and widget derive from the stored start instant. Dismissal of the ongoing notification belongs to the active workout ID. Widget start/resume uses the non-exported WidgetActivity entry point. |

[Backup archive](app/src/main/java/com/emmah/inshape/transfer/BackupArchive.kt), [backup validation](app/src/main/java/com/emmah/inshape/transfer/BackupValidation.kt), [restore service](app/src/main/java/com/emmah/inshape/transfer/BackupService.kt), [CSV export](app/src/main/java/com/emmah/inshape/transfer/JournalExports.kt), [provider paths](app/src/main/res/xml/export_paths.xml), [Health Connect gateway](app/src/main/java/com/emmah/inshape/health/AndroidHealthGateway.kt)

## UI implementation and design references

[Monochrome plan](docs/redesign-monochrome/implementation-plan.md), [HTML mockups](docs/redesign-monochrome/mockups.html), [screen overview](docs/redesign-monochrome/screens/00-overview.png), [historical reference images](docs/references/inshape/), [UI verification](docs/verification/monochrome-verification.md)

| Surface | Entry or source | What to inspect |
| --- | --- | --- |
| Today | Today tab and [TodayScreen](app/src/main/java/com/emmah/inshape/feature/today/TodayScreen.kt) | Active workout versus next-routine recommendation, elapsed time, week summary, optional Health Connect context and actions with data absent. |
| History and Progress | History?view={view}&exercise={exercise}, [HistoryScreen](app/src/main/java/com/emmah/inshape/feature/history/HistoryScreen.kt), [ProgressScreen](app/src/main/java/com/emmah/inshape/feature/progress/ProgressScreen.kt) | Calendar, past-entry dialog, unfinished records, workout details and corrections. Progress lives inside History and can be opened for a specific exercise. Inspect empty and sparse time series as well as long histories. |
| Library, exercise and routine | Library tab, exercise/{id}?item={item}, exercise-edit/{id}, routine/{id} | Plans versus recorded results, stable IDs for duplicate names, settings and alternatives, photo placeholders and previews, grouped items and mode-dependent fields. |
| Workout and finish | workout/{id}, finish/{id}, timing/{id}/{mode}, detail/{id} | Editable and completed sets, done and skipped rows, pinned primary action, input with the IME open, time correction and unknown or stale timing. |
| Check-in | checkin/{id}/{phase}, [CheckInScreen](app/src/main/java/com/emmah/inshape/feature/checkin/CheckInScreen.kt), [BodyMap](app/src/main/java/com/emmah/inshape/feature/checkin/BodyMap.kt) | Clear phase and selection state, front/back anatomical sides, text alternative for body regions and optional severity values. Filled shapes and solid lines indicate pain. Rings and dashed lines indicate stiffness. |
| Settings and export | Settings and Export destinations | Theme changes, reminder permission and restore holds, independent Health Connect capabilities, restore confirmation, selected records and export privacy options. |
| System surfaces | Glance widget, native ongoing notification, system photo/document pickers, permission screens and keyboard | Check the actual platform surface. The widget follows system appearance and uses a native chronometer. Android controls notification templates, launcher allocation and whether promoted ongoing presentation appears. |

Shared UI tokens live in [InShapeColors](app/src/main/java/com/emmah/inshape/ui/theme/InShapeColors.kt), [Type](app/src/main/java/com/emmah/inshape/ui/theme/Type.kt), [Dimens](app/src/main/java/com/emmah/inshape/ui/theme/Dimens.kt) and [InShapeShapes](app/src/main/java/com/emmah/inshape/ui/theme/InShapeShapes.kt). System, Light and Dark appearance apply through the shared theme and persist in ui_settings. App typography uses bundled Inter with tabular figures. The palette uses black, white and greys, with muted red for errors and destructive actions. Outlines, inset surfaces, typography and spacing create hierarchy. Avoid decorative coloured side stripes and round inline separators. [Inter license](docs/redesign-monochrome/OFL-Inter.txt)

Use [shared components](app/src/main/java/com/emmah/inshape/ui/components/) when reviewing changes to buttons, rows, fields, menus and selectors. Page provides scrollable content, a constrained content width of 600 dp, insets for the keyboard and an optional fixed bottom action. ResponsiveRow uses a flow layout. Main navigation switches to more vertical space at larger font sizes. The shared time picker switches to compact 24-hour input below 360 dp or above 130% font scale and exposes labelled UTC choices when a local time repeats.

Review layouts at 320 dp and 100%, 390 dp and 200%, and 320 dp and 200%. Exercise both explicit appearance modes when changing shared tokens or components. Check the complete scroll extent, tap area and focus order, long labels, truncation, selected semantics, system bars and visibility of the action while the keyboard is open. Compare actual Android screenshots with design references at the same effective width and theme.

## Testing guide

Run the narrowest relevant check first. Use JVM tests for decisions, stored values and failure boundaries. Use Compose instrumentation for click paths, disabled states, focus and reflow. Use a real or isolated platform installation when evidence depends on a launcher, native picker, notification permission or Health Connect. Keep fixture databases and destructive test flows separate from any personal journal.

| Area | Existing tests |
| --- | --- |
| Save retry and navigation | SaveRecoveryTest, RedesignInteractionTest#repeatedPastWorkoutConfirmationCreatesOneDraft, RedesignInteractionTest#completedCheckInSaveDoesNotPopTheWorkoutWhenCheckInWasClosed |
| Workout lifecycle and persistence | JournalRepositoryTest, JournalPersistenceTest, EndToEndJournalTest |
| Numeric input and routine targets | NumericInputTest, RoutineDurationDraftTest, LastRecordedValuesTest |
| Workout timing and date recovery | RecordedTimingTest, SystemLocalClockTest, WorkoutTimingEditorTest, FinishSummaryTest |
| Check-ins, progress and export | CheckInLevelsTest, CheckInDraftTest, BodyMapTest, ProgressCalculatorTest, ExportTest |
| Backup, restore and photo retry | BackupRestoreTest, StorageRecoveryTest, RestoreRecoveryTest, PhotoStoreTest#retryingASelectedBatchKeepsEachPhotoOnce |
| Health Connect capabilities and sync | HealthContextTest, HealthSharingTest, HealthSyncQueueTest, HealthProviderTest |
| Notifications, widget and reminders | ActiveWorkoutNotifierTest, WidgetStateTest, LauncherWidgetTest, ReminderNotificationTest, ScheduleChangeReceiverTest, ReminderReconcileTest |
| Layout and interaction | ReferenceLayoutTest, RedesignInteractionTest, WorkoutTimingEditorTest, BodyMapTest |

### Running focused checks

~~~powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*BackupRestoreTest' --console=plain
.\gradlew.bat :app:testDebugUnitTest --tests '*RecordedTimingTest' --tests '*ProgressCalculatorTest' --console=plain
pwsh -NoProfile -File tools/tc.ps1
~~~

Ordinary tc runs the JVM task defined in the [check manifest](config/android-check.json). The optional Full switch also requests connectedDebugAndroidTest and needs a ready, authorized device. For a focused device run, build and install both APKs on an isolated emulator, then select a class or method with -e class. Replace SERIAL with the actual test device serial.

~~~powershell
adb -s SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s SERIAL shell am instrument -w -r -e class com.emmah.inshape.WorkoutTimingEditorTest com.emmah.inshape.test/androidx.test.runner.AndroidJUnitRunner
adb -s SERIAL shell am instrument -w -r -e class com.emmah.inshape.ReferenceLayoutTest com.emmah.inshape.test/androidx.test.runner.AndroidJUnitRunner
~~~

For an isolated emulator at 320 dpi, a 640 px width represents 320 dp and a 780 px width represents 390 dp before activity insets. Set and verify the size, density and system font scale on the test installation. Record the actual configuration and check the instrumented case count and result. An APK install, Activity recreation and a device reboot provide different evidence.

Example for the combined 320 dp and 200% case on a disposable emulator

~~~powershell
adb -s SERIAL shell wm density 320
adb -s SERIAL shell wm size 640x1656
adb -s SERIAL shell settings put system font_scale 2.0
adb -s SERIAL shell wm size
adb -s SERIAL shell wm density
adb -s SERIAL shell settings get system font_scale
~~~

### Fixtures and reference checks

[DemoActivity](app/src/debug/java/com/emmah/inshape/demo/DemoActivity.kt) and [DemoFixtures](app/src/debug/java/com/emmah/inshape/demo/DemoFixtures.kt) use a fixed clock, separate database and assets and disabled integrations. The first nine fixture names are used by [ReferenceLayoutTest](app/src/androidTest/java/com/emmah/inshape/ReferenceLayoutTest.kt). The test traverses natural scroll content, checks text bounds and can emit captures. Fixture names are case-sensitive.

~~~powershell
adb -s SERIAL shell am start -n com.emmah.inshape/.demo.DemoActivity --es state TodayWithActiveWorkout
~~~

| Reference surface | Fixture |
| --- | --- |
| Today | TodayWithActiveWorkout |
| Active workout | ActiveWorkoutPartiallyDone |
| Finish summary | FinishSummary |
| Exercise details | ExerciseWithSetup |
| After check-in | CheckInAfter |
| Progress | ProgressWithHistory |
| Calendar | HistoryNovember |
| Expanded routine | LibraryRoutineExpanded |
| Settings | SettingsConfigured |

Additional fixture states are Empty, SparseHistory and FailedSave. Use actual platform integration tests for the widget lifecycle, native notification and Health Connect provider behavior. A permission test should keep denied, granted and revoked states distinct, identify the provider/API and use synthetic records. [Provider test procedure](README.md)

## Check runners and dated evidence

PowerShell 7 check wrappers live under [tools](tools/). They use the shared Android-check installation through the sibling directory or ANDROID_CHECK_HOME. Run them one at a time. The run manifest and individual report under reports/runs/ identify what actually executed and whether the result was clean, a finding, a tool error or inapplicable. Check that a report belongs to the intended run, especially for convenience files under reports/.

| Runner | Scope and interpretation |
| --- | --- |
| lc, cr, ga | ktlint/Detekt/Android Lint, Compose rules and Google Android Security Lints. Interpret advisory warnings using the actual report and risk policy. |
| ac, ms, sc, ss | Android Semgrep and MobSF, standalone MobSF, combined security checks and standalone secret scans. The recorded sc run includes project Semgrep and MobSF, and ss covers both the working tree and Git history. |
| dc, os | Strict Gradle artifact verification, OSV and OWASP Dependency-Check, plus standalone OSV. Debug and release runtime dependency scopes are declared in the check manifest. |
| ds | DeepSec scan and report with a production Kotlin matcher. It sends source context to the configured Codex provider. Revalidate recorded findings against current source and inspect the exported active finding count. |
| ql, db | Local CodeQL and remote workflow status, plus local Dependabot configuration and GitHub alerts. The CodeQL workflow in this tree uses a manual Java/Kotlin debug build. Verify remote workflow availability independently of the local scan. |
| pc, cs | PMD CPD at 50 tokens and debug/release Compose stability checks. Update baselines only after reviewing intentional signature or classification changes. |
| bc, tc | Debug APK and JVM test tasks from the check manifest. Use the Gradle XML results for test-case totals if the wrapper only confirms task execution. |
| sonar | Builds debug and imports JVM JaCoCo XML from app/build/reports/coverage/test/debug/report.xml. Uploads source and analysis metadata to the configured public SonarCloud project and waits for its quality gate. Device runs are outside the current coverage report. |

[Runner and authorization details](tools/README.md), [check exceptions](config/check-exceptions.json), [Compose baselines](app/stability/), [CodeQL workflow](.github/workflows/codeql.yml), [Dependabot configuration](.github/dependabot.yml), [Sonar runner](tools/sonar.ps1)

### 2026-11-15 script and regression run

All 17 applicable scripts were invoked individually. Sixteen finished successfully after the required reruns. The run used ordinary tc and separately selected emulator cases. These results apply to that session's working tree and the scope of each check. Links under `reports/` point to locally generated evidence that may be absent from a fresh checkout.

| Check | Observed result |
| --- | --- |
| Debug build and JVM tests | Debug APK built. The Gradle XML files contained 81 tests, zero failures, zero errors and zero skipped tests. [Build report](reports/runs/20261115T013743015-fba2e424/build.txt), [test report](reports/runs/20261115T013844330-a768d873/test.txt) |
| Targeted API 36 emulator tests | Seven cases passed in each of three configurations, 320 dp at 100%, 390 dp at 200%, and 320 dp at 200%. The selected cases covered timing and invalid date recovery, rapid past-workout confirmation, delayed Check-in save, photo batch retry and reference layout traversal. Photo replacement target recovery was inspected in code. |
| Lint, duplication and Compose | Android Lint reported zero errors and four warnings. The warnings concern three version suggestions and the conservative free-space check in BackupArchive. PMD passed after consolidating shared card appearance values. Both Compose stability variants passed against updated baselines. [Lint](reports/runs/20261115T012957941-05d55fcf/google-android-security.txt), [PMD](reports/runs/20261115T012330130-e86199b5/pmd-cpd.txt), [Compose Stability](reports/runs/20261115T012751025-9c593f32/compose-stability.txt) |
| Dependency, secret and Android security checks | OSV reported zero findings across 774 packages. The combined security run had no blocking findings. MobSF reported seven informational advisories. [Combined security report](reports/runs/20261115T013319032-d7ae23e8/security-summary.txt) |
| DeepSec | Eleven findings were corrected during the script run. Final revalidation checked 24 findings in total, with 23 fixed, one false positive and zero active exported findings. [Revalidation report](reports/runs/20261115T010610553-a2571bde/deepsec.txt) |
| CodeQL and Dependabot | Local CodeQL covered 71 of 71 production source files, with zero active findings, one configured suppression and zero extraction diagnostics. The remote workflow was absent from the GitHub default branch at the time of the run. GitHub returned zero open Dependabot alerts. [CodeQL](reports/runs/20261115T011338421-265d59ec/codeql.txt), [Dependabot](reports/runs/20261115T012224710-e79a572d/dependabot.txt) |
| SonarCloud | The upload and analysis completed. The quality gate failed on new-code coverage at 13.3% against an 80% threshold. The other gate conditions passed. Sonar counted 1,646 new lines to cover and 1,226 new conditions. The current JVM-only report leaves much of the UI redesign uncovered. [Run log](reports/sonar.txt), [SonarCloud project](https://sonarcloud.io/dashboard?id=Insaner1980_InShape) |

Earlier evidence has different scope. The 2026-11-14 monochrome verification records the broader Light/Dark layout matrix, real API 36 provider cases, widget and notification behavior, and emulator rotation, relaunch and reboot checks. The initial verification includes API 26 and cross-device restore evidence on an earlier build. Physical-device behavior, manual TalkBack speech, promoted status-chip rendering and photo replacement through a recreated picker remain unverified by the cited runs. [Monochrome verification](docs/verification/monochrome-verification.md), [implementation verification](docs/verification/implementation-verification.md)
