param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $scriptDir "scripts\start-drone-stack.ps1"

if (-not (Test-Path $target)) {
    $fromParent = Join-Path (Get-Location) "on-demand-monitoring-system\scripts\start-drone-stack.ps1"
    if (Test-Path $fromParent) {
        $target = $fromParent
    }
}

if (-not (Test-Path $target)) {
    throw "Cannot find scripts\start-drone-stack.ps1. Run this from on-demand-monitoring-system or the repo parent folder."
}

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $target -SimWorld compact @Arguments
exit $LASTEXITCODE
