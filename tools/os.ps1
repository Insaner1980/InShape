#Requires -Version 7.0
$projectRoot = Split-Path -Parent $PSScriptRoot
$installationRoot = if ([string]::IsNullOrWhiteSpace($env:ANDROID_CHECK_HOME)) {
    Join-Path $projectRoot '../Android-check'
} else {
    $env:ANDROID_CHECK_HOME
}
$entryPoint = Join-Path $installationRoot 'tools/InvokeProjectCheck.ps1'
if (-not (Test-Path -LiteralPath $entryPoint -PathType Leaf)) {
    Write-Error 'Android-check is missing. Set ANDROID_CHECK_HOME to its installation directory.'
    exit 2
}
& $entryPoint -ProjectCheckCommand osv-scan -Root $projectRoot -ProjectId inshape @args
exit $LASTEXITCODE

