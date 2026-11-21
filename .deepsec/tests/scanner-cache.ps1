#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$scratch = Join-Path $projectRoot '.gradle/deepsec-cache-test'
$cli = Join-Path $projectRoot '.deepsec/node_modules/deepsec/dist/cli.mjs'
if (Test-Path -LiteralPath $scratch) { throw "Fixture path already exists: $scratch" }
New-Item -ItemType Directory -Path (Join-Path $scratch 'source') | Out-Null
try {
    Set-Content -LiteralPath (Join-Path $scratch 'deepsec.config.mjs') -Value @'
export default {
  projects: [{ id: "fixture", root: "source" }],
  plugins: [{ name: "fixture", matchers: [{
    slug: "fixture-kotlin", noiseTier: "noisy", description: "Cache regression fixture",
    filePatterns: ["**/*.kt"], examples: ["package fixture"],
    match(content) { return [{ vulnSlug: "fixture-kotlin", lineNumbers: [1], snippet: content, matchedPattern: "fixture" }]; }
  }] }]
};
'@
    $source = Join-Path $scratch 'source/Test.kt'
    $recordPath = Join-Path $scratch 'data/fixture/files/Test.kt.json'
    Set-Content -LiteralPath $source -Value 'package fixture'
    function Invoke-FixtureScan {
        $process = Start-Process -FilePath (Get-Command node).Source -ArgumentList ('"{0}"' -f $cli), 'scan', '--project-id', 'fixture' `
            -WorkingDirectory $scratch -WindowStyle Hidden -Wait -PassThru `
            -RedirectStandardOutput (Join-Path $scratch 'scan.log') -RedirectStandardError (Join-Path $scratch 'scan-error.log')
        if ($process.ExitCode -ne 0) { throw 'Fixture scan failed.' }
    }
        Invoke-FixtureScan
        & sonar analyze secrets $recordPath | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Fixture record secret scan failed.' }
        $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
        if ($record.status -ne 'pending') { throw 'New source was not pending.' }
        $record.status = 'analyzed'
        $record | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $recordPath
        Invoke-FixtureScan
        & sonar analyze secrets $recordPath | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Fixture record secret scan failed.' }
        $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
        if ($record.status -ne 'analyzed') { throw 'Unchanged source lost its cached analysis.' }
        Add-Content -LiteralPath $source -Value '// changed source'
        Invoke-FixtureScan
        & sonar analyze secrets $recordPath | Out-Null
        if ($LASTEXITCODE -ne 0) { throw 'Fixture record secret scan failed.' }
        $record = Get-Content -LiteralPath $recordPath -Raw | ConvertFrom-Json
        if ($record.status -ne 'pending') { throw 'Changed source incorrectly reused cached analysis.' }
        Write-Output 'PASS: new source is pending, unchanged analysis is cached, changed source is pending again.'
} finally {
    $resolved = (Resolve-Path -LiteralPath $scratch).Path
    $expected = [IO.Path]::GetFullPath((Join-Path $projectRoot '.gradle/deepsec-cache-test'))
    $links = @(Get-Item -LiteralPath $scratch; Get-ChildItem -LiteralPath $scratch -Force -Recurse) |
        Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }
    if ($resolved -ne $expected -or $links) { throw 'Unsafe fixture cleanup path.' }
    Remove-Item -LiteralPath $resolved -Recurse -Force
}
