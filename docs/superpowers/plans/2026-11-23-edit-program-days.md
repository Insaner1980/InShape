# Editable training program days — implementation plan and handoff

**Status on 23 November 2026.** The feature is implemented locally on `codex/inshape-ui-polish` in `C:\Dev\InShape`. The feature changes and Today test correction are uncommitted alongside earlier work. Both editor flows passed on the Pixel 9 at normal and narrow widths.

## Device instructions from the owner

The owner authorized the Pixel 9 for the editor verification at its current font scale and excluded 200% font scale, Samsung and the emulator. The run explicitly targeted the Pixel 9 by serial.

## Already implemented

| Area | Existing behavior and where to find it |
| --- | --- |
| Entry points | **Muokkaa ohjelmaa** opens from the Library program details and from the next-workout preview on Today. The Library entry selects the first program day. Today selects the scheduled day. See `navigation/AppNavigation.kt`, `feature/program/ProgramScreen.kt` and `ProgramSetupScreen.kt`. |
| Editing a day | `ProgramDayEditor.kt` keeps a draft across day changes and uses Compose saved state. A user can remove a movement, undo a removal, switch days, save changes together or discard them. Save requires at least one movement in each edited day. |
| Finding and creating movements | `ProgramMovementPicker.kt` searches the exercise library and all bundled program days. It can filter by source. A catalog result carries the selected source day's prescription. A typed name opens set and repetition fields with defaults of two sets of 8–12. The new library exercise is written when the program is saved. The picker and Undo share the check for an existing link between a catalog movement and a library exercise. |
| Persistence | Room schema 4 and the 3-to-4 migration include `ProgramDayOverride`, keyed by catalog version, program ID and day ID. The journal snapshot observes the overrides. `JournalRepository.saveProgramDays` saves changed days and new exercises in one transaction and checks for stale edits. Unedited days use the bundled catalog. |
| Starting and recording workouts | The overview, guides and setup use the effective program day. Personal movements reference the selected library exercise by ID. Added movements participate in short mode. If all previous short movements are removed, the first remaining movement is used. Saving an edit preserves enrollment and rotation. Starting a workout copies its prescription into the session, so later edits leave its records intact. |
| Backups | Archive format 3 includes the edited days. Restore validates them and replaces the stored overrides in the restore transaction. The reader still accepts version 1 and 2 backups. See `transfer/BackupArchive.kt`, `BackupValidation.kt` and `BackupService.kt`. |

Source paths above are relative to `app/src/main/java/com/emmah/inshape/`. Focused tests for migration, backup, enrollment, movement identity and the editor flows already exist.

## Verification already performed

- The debug app and instrumentation APK built successfully. The final targeted JVM run passed **31 tests across seven suites**, with zero failures, errors or skips. The suites were `ProgramMigrationTest`, `ProgramBackupTest`, `BackupRestoreTest`, `ProgramCatalogTest`, `ProgramEnrollmentTest`, `ProgramEditorIdentityTest` and `ProgramImportReviewTest`.
- On the Android 16 emulator at default size and font scale, the final run passed **three of four** selected instrumentation tests. The passing cases covered removing and undoing a removal, copying **Dead bug**, creating a named movement, saving and starting the edited workout with the expected order and targets, browsing a guide, and restoring a backup.
- On an **earlier build**, the two editor flow tests passed at 320 dp width with normal text and at default width with 200% text. Those runs preceded the expanded Today test. The final four-configuration run stopped at its first configuration. There is no result for the combined 320 dp and 200% setting.

## Pixel 9 verification on 23 November 2026

- The Today test timed out because **Kirjasto** also matched search result rows. The test now selects the **Kirjasto** item within the source menu. Robolectric reproduced the timeout before the change and passed afterward.
- On Pixel 9 with Android 17 and 115% font scale, the Today flow and the edit, save and start flow both passed at 411 dp and 320 dp. The narrow run also verified that the Android keyboard was visible while editing the search and numeric fields and while scrolling to the **Lisää liike** button. The program was saved and the edited workout started.
- The run used a temporary app with a separate application ID. Both temporary packages were removed, the original APKs on the phone were verified, and the 411 dp display setting was restored.

No commit, push or pull request was made.
