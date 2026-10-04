param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$Arguments
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$target = Join-Path $scriptDir "scripts\start-drone-stack.ps1"

& powershell.exe -NoProfile -ExecutionPolicy Bypass -File $target -SimWorld light -ShowGazeboGui -SkipBootstrap -NoTelemetry @Arguments
exit $LASTEXITCODE
