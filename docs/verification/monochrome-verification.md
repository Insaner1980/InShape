# Monochrome redesign verification

Date: 14 November 2026.

Implemented the [monochrome plan](../redesign-monochrome/implementation-plan.md), using its HTML mockups and reference images. The work was performed on the existing feature branch without committing or pushing. Existing unrelated changes were preserved.

## Delivered behavior

Inter with tabular figures, the monochrome palette, outlined surfaces, shared controls and compact menus replace the previous visual system. Today, History and Library are the three bottom tabs; Progress lives inside History. Exercise, routine, settings, check-in, history and export flows use the new components.

The workout clock derives from the stored start instant. Finish captures a persistent end-time draft, offers time and note corrections, and saves into the optional After check-in followed by workout details. Untouched legacy check-in numbers and observation timestamps remain unchanged. Native ongoing notifications and the home-screen widget show the running clock. This redesign does not change the database schema, backup format or exported numeric observation values.

## Validation

The four required Gradle tasks run together in the requested order: `assembleDebug`, `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`. The build uses the repository's pinned toolchain and JDK 21. Kotlin build telemetry collection was disabled for these invocations; no checks were disabled.

| Check | Result |
| --- | --- |
| JVM tests | 75 passed, no failures or skipped tests |
| Android lint | No errors, 15 warnings |
| Instrumentation | 25 passed, including the compact-picker regression and the strengthened launcher widget test |
| Health Connect provider | Denied access, actual insert/update/delete, and revoked access passed in separate invocations |
| Layout matrix | Nine screens at 320 and 360 dp, 100% and 200% font scale, Light and Dark: all 72 combinations passed |
| Static design checks | No `Color(0x...)` outside theme tokens, no old font references or prohibited text separators in app sources; `git diff --check` passed |

Layout tests traverse the full scrollable content and check text bounds, including the combined 320 dp / 200% case. Screenshots were also inspected visually. The compact time picker is checked separately because it is a modal overlay rather than a main reference screen.

Both compact-picker themes were captured at 320 dp / 200% after the correction. Photo selection, full-photo display and the removal confirmation were exercised using a generated test fixture. A backup was created through Android's document picker, then selected and validated for restore; the replacement confirmation was cancelled. No personal photos or journal were used.

All device work uses the task-owned API 36 emulator. The connected physical phone was not used. A screenshot automation connection initially interfered with instrumentation teardown; closing that connection allowed the suite to finish successfully. The final suite has 25 tests, with two additional unique Health Connect provider tests run separately: 27 unique instrumented tests passed.

## Runtime checks

The clock ticked without notification permission and remained correct on Today and in the workout after rotation, process termination/relaunch and emulator reboot. The boot receiver restored the notification before the app was opened. The notification retained the original start timestamp; its native chronometer and the widget's chronometer agreed with that start within the timestamped capture intervals.

Notification Open opened the workout. Finish opened the persistent summary. Dismissal survived returning to the foreground, while a new workout received a new notification. Saving removed the notification and opened the After check-in; Skip then opened workout details. Discarding an active workout also removed the notification. The inactive widget returned after discard.

The widget observes repository changes inside its composition, so starting a workout updates an already active widget session. This follows the [Glance lifecycle contract](https://developer.android.com/reference/kotlin/androidx/glance/appwidget/GlanceAppWidget): updating a running session does not restart `provideGlance`. The launcher regression now requires the visible "In progress" state before exercising Resume. The native clock's light/dark colour comes from Android colour resources and was checked across a live system-theme change.

## Reference differences and limits

Large text uses stacked controls and metric tiles, with at least 48 dp interaction areas, instead of forcing the reference's compact rows. At small widths or large font scales the time picker uses labelled 24-hour input to avoid clipping Android's AM/PM selector. Fixture data and existing detailed exercise fields remain available even where the mockups simplify them. The existing explicit backup labels are retained.

Health Connect uses its actual product name and availability state. Android owns the notification template, its fonts and accent colour, the keyboard, permission dialogs, document/photo pickers, launcher wallpaper and widget allocation. The widget's native clock uses Inter; other Glance text uses the platform font. These system surfaces therefore differ from the illustrative mockups. The notification requests promoted ongoing presentation, but a promoted status chip was not verified on this API 36.0 emulator.

Physical-device behavior and a manual TalkBack session were not tested.

## Evidence and output

The APK is `app/build/outputs/apk/debug/app-debug.apk`. Build, instrumentation, provider, runtime and layout evidence is retained under [`reports/monochrome-verification/`](../../reports/monochrome-verification/). The [lint report](../../app/build/reports/lint-results-debug.txt) lists the 15 warnings.

All 89 existing images in [`reports/emulator-screenshots/`](../../reports/emulator-screenshots/) were regenerated, with eight additional images for the new finish/time and native notification/widget states. The [image inventory](../../reports/monochrome-verification/screenshots.txt) lists the retained files. Some instrumented captures contain only the app surface, without Android's status-bar icons. Layout overviews, full-scroll evidence for both 320 dp / 200% themes, and four compact-picker/sheet images are retained separately.

## Cleanup restriction

The task-owned emulator, its capture connection and its host helper processes were stopped and their absence was verified. No task-owned build or capture process remained running.

Automatic approval review rejected file deletion with "blocked by policy", including a second command using only the exact scratch directory and AVD metadata file. The disposable `reports/monochrome-verification/scratch/` directory (including the test AVD), its external `.ini` file and 126 redundant layout frames therefore remain. The exact frame paths are recorded in `cleanup-files.txt`, which also lists itself for removal. The final APK, screenshots, overviews, large-font full-scroll evidence and verification logs are excluded from these targets. Absolute path boundaries, parent directories and the absence of junctions/symlinks were checked before preparing these commands.

```powershell
Remove-Item -LiteralPath 'C:\Dev\InShape\reports\monochrome-verification\scratch' -Recurse -Force
Remove-Item -LiteralPath 'C:\Users\EmmaH\.android\avd\InShapeMonochrome.ini' -Force
Remove-Item -LiteralPath (Get-Content -LiteralPath 'C:\Dev\InShape\reports\monochrome-verification\cleanup-files.txt') -Force
```
