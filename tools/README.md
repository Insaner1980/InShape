# InShape checks

Run commands in PowerShell 7 from the project root. The project uses the shared
`../Android-check` installation, or the directory specified by `ANDROID_CHECK_HOME`.
The configured PowerShell aliases also work from this project directory.

```powershell
pwsh -NoProfile -File tools/lc.ps1
pwsh -NoProfile -File tools/tc.ps1 -Full
```

Run one check at a time. Reports are written under `reports/`; native evidence and
input fingerprints are retained in `reports/runs/<run-id>/`.

| Script | Check |
| --- | --- |
| `lc.ps1` | ktlint, Detekt and Android Lint |
| `ac.ps1` | Android Semgrep rules and MobSF |
| `dc.ps1` | Strict Gradle dependency verification, OSV and OWASP Dependency-Check |
| `ss.ps1` | Gitleaks, TruffleHog and Semgrep secrets |
| `ds.ps1` | DeepSec scan, report, custom report and revalidation |
| `ms.ps1` | MobSF static analysis |
| `os.ps1` | OSV dependency vulnerability scan |
| `ql.ps1` | Local CodeQL analysis and GitHub workflow/status |
| `db.ps1` | Dependabot configuration and GitHub alerts |
| `pc.ps1` | PMD CPD duplicate scan, 50-token threshold |
| `cs.ps1` | Debug and release Compose stability baselines |
| `cr.ps1` | Compose rules through ktlint and Detekt |
| `ga.ps1` | Google Android Security Lints |
| `sc.ps1` | Dependency, secret and Semgrep checks; `-Full` adds Android checks |
| `bc.ps1` | Debug APK build |
| `tc.ps1` | JVM tests; `-Full` also runs connected Android tests |
| `sonar.ps1` | SonarCloud analysis and quality gate |

The lint profile follows the moderate dBcheck-style limits in
`config/detekt.yml`. Reviewed false positives have narrow, expiring entries in
`config/check-exceptions.json` or `config/dependency-check-suppressions.xml`.
Stability baselines are checked in under `app/stability/`; update them only after
reviewing intentional API changes, using `:app:debugStabilityDump` and
`:app:releaseStabilityDump`.

`tc.ps1 -Full` requires an authorized emulator or device. Verification on
2026-11-14 passed 24 instrumented tests on an isolated API 36 emulator. The later
warning fixes passed all 67 JVM tests; device tests were not repeated for those
fixes. The earlier eight-screen layout check also passed at 390 dp / 200%,
320 dp / 100%, and 320 dp / 200%. Evidence is in
`reports/test-results-summary.json` and `reports/ui-layout/`. The saved-duration
and stale finish-draft regressions also passed at each of these three sizes.
The native decoder's `incomplete input` message comes from the intentional
invalid-photo regression test; the test itself passes.

DeepSec sends source context to its configured AI provider. A request to run
DeepSec, Sonar, all checks, or to implement and test this script list authorizes
their actual analyses and necessary uploads. Use the runners' consent flags to
record that authorization; do not ask for it again on retries or verification runs.
The current shared `android-check` does not automatically run DeepSec; use
`ds.ps1` separately. Its project matcher includes every production Kotlin file;
the scanner ignores generated reports and Gradle caches. `ds.ps1 -Revalidate`
rechecks findings at every severity, including previously validated findings. It
then exports the current unresolved findings, so a completed revalidation cannot
be reported as clean while active findings remain.

The completed 2026-11-14 scan covers 67 files with no unresolved findings.
Revalidation confirmed 12 fixes and one false positive; the subsequent source
analysis found no new issues. Evidence is in `reports/deepsec.txt` and the
retained revalidation run `reports/runs/20261114T113827006-0a3fcb1d/`.

The pinned DeepSec patch also invalidates a cached analysis when its source hash
changes. `pwsh -NoProfile -File .deepsec/tests/scanner-cache.ps1` verifies this with
an isolated fixture, without contacting an AI provider, and removes the fixture.

SonarCloud uploads source and analysis metadata to the public
`Insaner1980_InShape` project. Review the plan with:

```powershell
pwsh -NoProfile -File tools/sonar.ps1 -PlanOnly
```

An actual upload uses `-AllowExternalUpload` and `SONAR_TOKEN` from the existing
credential setup. The configured `sonar` PowerShell command loads the Sonar CLI's
Windows credential automatically. The default uses JVM coverage. To add coverage
from a connected Android emulator or test device, run the following in PowerShell:

```powershell
.\tools\sonar.ps1 -IncludeDeviceCoverage -AllowExternalUpload
```

A successful local build does not prove a GitHub workflow or cloud scan.

The actual SonarCloud analysis on 2026-11-14 passed the unchanged Sonar way gate
with 89.4% new-code coverage, no open bugs or vulnerabilities, and 56 remaining
maintainability notices. Evidence: `reports/sonar.txt`,
`reports/sonar-quality-gate.json`, and `reports/sonar-issues.json`.

The warning follow-up reduced Sonar issues from 66 to 56 and Android Lint warnings
from 24 to 10. The remaining findings and seven MobSF informational categories
are assessed in `reports/warning-review.md`; no check was weakened to reduce them.

KnitTools-specific `rs` and `rst` checks are intentionally excluded.
