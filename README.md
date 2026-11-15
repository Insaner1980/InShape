# InShape

Offline personal workout journal for Android, Kotlin and Jetpack Compose. The journal follows the [implementation plan](docs/superpowers/plans/2026-11-13-inshape-implementation-plan.md), with the [visual redesign](docs/redesign/implementation-plan.md) and [monochrome redesign and workout clock](docs/redesign-monochrome/implementation-plan.md). See the [implementation verification](docs/verification/implementation-verification.md) and [monochrome verification](docs/verification/monochrome-verification.md) for checks and platform limitations.

Build with Android Studio or JDK 17+ and Android SDK 37: `./gradlew.bat :app:assembleDebug`. The output is `app/build/outputs/apk/debug/app-debug.apk`. Unit tests: `./gradlew.bat :app:testDebugUnitTest`. Lint: `./gradlew.bat :app:lintDebug`.

The verified build uses JDK 21. Install with `adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk` and open InShape. A normal installation starts with an empty journal. No account or internet access is required. The Android build tools download pinned dependencies on the first build.

Create exercises and machine settings in Library, optionally attach your own photos, then create a routine or start an empty workout. Completed sets, quick completion and manual duration are separate from planned targets. Optional Before, After and Next day check-ins keep missing scores distinct from zero. History supports past entries, corrections and deletion; Progress uses recorded work only.

Settings provides local reminders, a real home-screen widget, separate Health Connect capabilities, full backup/restore and selected CSV/PDF exports. Health Connect requires an available provider and explicit permissions. Reminders use inexact Android alarms and may be delayed. Restored reminders and external sharing require confirmation on the destination device. A full backup includes original photos; CSV/PDF are reports, not restore archives.

Debug-only reference fixtures use a separate database and asset root, a fixed clock and disabled platform integrations. Open one explicitly with:

```powershell
adb -s <device> shell am start -n com.emmah.inshape/.demo.DemoActivity --es state TodayWithActiveWorkout
```

Other states: `ActiveWorkoutPartiallyDone`, `FinishSummary`, `ExerciseWithSetup`, `CheckInAfter`, `ProgressWithHistory`, `HistoryNovember`, `LibraryRoutineExpanded`, `SettingsConfigured`, `Empty`, `SparseHistory`, `FailedSave`. DemoActivity is excluded from release builds. The ordinary launcher always opens the user's journal.

Device tests: build `:app:assembleDebugAndroidTest`, install both APKs on an isolated emulator, then run `adb -s <device> shell am instrument -w -r com.emmah.inshape.test/androidx.test.runner.AndroidJUnitRunner`. The provider test grants exercise access only on a supported test device; the notification test requires notification permission. Never run destructive fixture/recovery checks against a personal journal.

For supported-provider checks, run `HealthProviderTest#aDeniedHealthPermissionLeavesLocalJournalUsable` before granting access. Grant `android.permission.health.WRITE_EXERCISE` and `android.permission.health.READ_EXERCISE` to the test installation with `adb shell pm grant`, then run `HealthProviderTest#bRealProviderStoresUpdatesAndDeletesOneIdentity` in a separate instrumentation invocation after the grants have propagated. Revoke write access and rerun the denied case to check revocation. The full API 26 suite skips these two provider-only cases.

Toolchain: AGP 9.5.1, Gradle 9.8.2, Kotlin/Compose compiler 2.4.21, KSP 2.3.13, Compose BOM 2026.09.00, Room 2.8.5. Minimum Android API 26. Dependencies are pinned in `gradle/libs.versions.toml`; no preview libraries are used.

Version and compatibility sources: [AGP](https://developer.android.com/build/releases/agp-9-5-0-release-notes), [Kotlin](https://kotlinlang.org/docs/releases.html), [KSP](https://github.com/google/ksp/releases), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Room](https://developer.android.com/jetpack/androidx/releases/room), [Health Connect](https://developer.android.com/jetpack/androidx/releases/health-connect). Selected versions were checked on 13 November 2026. AndroidX manifests and build metadata validate the API floor during assembly.

## Design system

The app supports System, Light and Dark appearance in Settings. The choice applies immediately, persists on this phone, and is deliberately excluded from journal backups. The launcher widget follows the system theme. Android forced dark is disabled.

`ui/theme/InShapeColors.kt`, `Type.kt`, `Dimens.kt`, `Shapes.kt` and `ui/components/` define the shared colours, typography, spacing, shapes and controls. Inter is bundled from the [official Google Fonts distribution](https://github.com/google/fonts/tree/main/ofl/inter), under the [SIL Open Font License](docs/redesign-monochrome/OFL-Inter.txt). All type uses tabular figures. Both themes use black, white and greys, with muted red reserved for errors and destructive actions. Outlines, inset fills and hairlines replace shadows.

Today, History and Library are the three bottom tabs; Progress is a view inside History. The active workout clock derives from its recorded start and appears in the workout, Today, the ongoing system notification and the home-screen widget. Finish captures the end instant into a persistent draft, then Save workout confirms the timing and opens the optional After check-in. Notification dismissal is remembered for that workout.

Today offers the active workout or the least recently completed routine. Workout logging keeps its main action pinned below the scrollable content. History uses a calendar date picker and compact records. Progress separates recorded work from planned targets, draws missing observations as gaps, and distinguishes pain and stiffness with solid and dashed lines. Appearance, backup/restore formats, and journal data remain separate.
