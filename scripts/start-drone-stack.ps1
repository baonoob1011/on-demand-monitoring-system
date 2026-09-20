param(
    [ValidateSet("legacy", "compact")]
    [string]$SimWorld = "compact",
    [switch]$ShowGazeboGui,
    [switch]$WithTelemetry,
    [switch]$WithCamera,
    [switch]$WithSensors,
    [switch]$WithWeather,
    [switch]$SkipBootstrap
)

$ErrorActionPreference = "Stop"

function ConvertTo-WslPath([string]$WindowsPath) {
    $fullPath = (Resolve-Path $WindowsPath).Path
    if ($fullPath -notmatch "^([A-Za-z]):\\(.*)$") {
        throw "Cannot convert path to WSL format: $fullPath"
    }

    $drive = $Matches[1].ToLowerInvariant()
    $rest = $Matches[2] -replace "\\", "/"
    return "/mnt/$drive/$rest"
}

function Start-WslWindow([string]$Title, [string]$Command) {
    $escapedCommand = $Command.Replace('"', '\"')
    $cmdLine = "title $Title && wsl.exe -d $ubuntuDistro -- bash -lc `"$escapedCommand`""
    Start-Process cmd.exe -ArgumentList @("/k", $cmdLine) -WindowStyle Normal
}

function Assert-DronePackage([string]$Root, [string]$World) {
    if ($World -ne "compact") { return }

    $worldFile = Join-Path $Root "Forest3D\worlds\forest_monitoring_compact.sdf"
    $modelRoot = Join-Path $Root "Forest3D\models"
    $requiredModels = @(
        "compact_terrain",
        "compact_water",
        "compact_roads",
        "compact_bridges",
        "compact_home",
        "compact_highrise",
        "compact_zones",
        "compact_forest",
        "compact_thermal_sources",
        "compact_environment_props",
        "compact_mountains",
        "compact_airport",
        "x500_mono_cam_down"
    )

    if (-not (Test-Path $worldFile)) { throw "Compact world is missing: $worldFile" }
    foreach ($model in $requiredModels) {
        $config = Join-Path $modelRoot "$model\model.config"
        $sdf = Join-Path $modelRoot "$model\model.sdf"
        if (-not (Test-Path $config)) { throw "Packaged Gazebo model config is missing: $config" }
        if (-not (Test-Path $sdf)) { throw "Packaged Gazebo model SDF is missing: $sdf" }
    }
}

$ubuntuDistro = "Ubuntu-24.04"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Assert-DronePackage $repoRoot $SimWorld

$compactModels = @(
    "compact_terrain",
    "compact_water",
    "compact_roads",
    "compact_bridges",
    "compact_home",
    "compact_highrise",
    "compact_zones",
    "compact_forest",
    "compact_environment_props",
    "compact_mountains",
    "compact_airport"
)

$simArg = $SimWorld
if ($simArg -eq "compact") {
    $missingCompactModels = @(
        $compactModels | Where-Object {
            -not (Test-Path (Join-Path $repoRoot "Forest3D/models/$_/model.config"))
        }
    )
    if ($missingCompactModels.Count -gt 0) {
        Write-Warning (
            "Compact Gazebo assets are not available in this checkout. " +
            "Falling back to the self-contained legacy world. Missing: " +
            ($missingCompactModels -join ", ")
        )
        $simArg = "legacy"
    }
}

if (-not $SkipBootstrap) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bootstrap-drone-stack.ps1") -UbuntuDistro $ubuntuDistro
}

$repoRootWsl = ConvertTo-WslPath $repoRoot
$scriptRoot = "$repoRootWsl/scripts"
$webOnly = if ($ShowGazeboGui) { "0" } else { "1" }
$baseWslEnv = "PROJECT_PATH='$repoRootWsl'"
$simCommand = "$baseWslEnv FOREST3D_WEB_ONLY=${webOnly} SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-sim-pane.sh ${simArg}"

wsl.exe -d $ubuntuDistro -- bash -lc "$baseWslEnv exec ${scriptRoot}/wsl-clean-drone-stack.sh" | Out-Null

if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
    $wtArgs = @(
        "new-tab", "--title", "RIGHT - Gazebo + PX4",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", $simCommand,
        ";", "split-pane", "--horizontal", "--size", "0.66", "--title", "LEFT - Flight Control",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv FOREST3D_WEB_ONLY=${webOnly} SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-control.sh"
    )
    if ($WithTelemetry) {
        $wtArgs += @(
            ";", "split-pane", "--vertical", "--size", "0.50", "--title", "MIDDLE - Telemetry BE",
            "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv exec ${scriptRoot}/wsl-telemetry.sh"
        )
    }
    if ($WithCamera) {
        $wtArgs += @(
            ";", "new-tab", "--title", "CAMERA - Downward View",
            "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-camera-view.sh"
        )
    }
    if ($WithSensors) {
        $wtArgs += @(
            ";", "new-tab", "--title", "SENSOR - LiDAR 2D + 3D",
            "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-sensor-monitor.sh"
        )
    }
    & wt.exe @wtArgs
    if ($WithWeather) {
        Start-WslWindow -Title "WEATHER - Controls" -Command "$baseWslEnv SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-weather-control.sh"
    }
    exit 0
}

Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", $simCommand
Start-Sleep -Seconds 2
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv FOREST3D_WEB_ONLY=${webOnly} SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-control.sh"
if ($WithTelemetry) {
    Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv exec ${scriptRoot}/wsl-telemetry.sh"
}
if ($WithCamera) {
    Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-camera-view.sh"
}
if ($WithSensors) {
    Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "$baseWslEnv SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-sensor-monitor.sh"
}
if ($WithWeather) {
    Start-WslWindow -Title "WEATHER - Controls" -Command "$baseWslEnv SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-weather-control.sh"
}
