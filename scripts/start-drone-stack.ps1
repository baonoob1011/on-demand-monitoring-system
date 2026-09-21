param(
    [ValidateSet("legacy", "compact")]
    [string]$SimWorld = "compact",
    [switch]$ShowGazeboGui,
    [switch]$WithTelemetry,
    [switch]$WithCamera,
    [switch]$WithSensors,
    [switch]$WithWeather,
    [ValidateRange(0.1, 180.0)]
    [double]$YawStepDeg = 5.0,
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

function Write-Utf8NoBomLines([string]$Path, [string[]]$Lines) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $Lines, $encoding)
}

function Initialize-StackEnv([string]$Root) {
    $backendEnvFile = Join-Path $Root "ondemandmonitoring\.env"
    if (Test-Path $backendEnvFile) { return }

    $droneEnvExample = Join-Path $Root "drone\.env.example"
    if (Test-Path $droneEnvExample) {
        $lines = @(Get-Content -Path $droneEnvExample -ErrorAction Stop)
        Write-Utf8NoBomLines $backendEnvFile $lines
        return
    }

    New-Item -ItemType File -Path $backendEnvFile -Force | Out-Null
}

function Update-Forest3DAssetsFromGit([string]$Root) {
    $gitDir = Join-Path $Root ".git"
    if (-not (Test-Path $gitDir)) { return }
    if (-not (Get-Command git.exe -ErrorAction SilentlyContinue)) { return }

    Write-Host "Forest3D compact assets are missing. Trying to download latest Git assets..." -ForegroundColor Yellow
    Push-Location $Root
    try {
        & git.exe pull --ff-only
        if ($LASTEXITCODE -ne 0) {
            Write-Host "git pull did not complete. Continuing with local files." -ForegroundColor Yellow
            return
        }

        & git.exe lfs version *> $null
        if ($LASTEXITCODE -eq 0) {
            & git.exe lfs pull
        }
    } finally {
        Pop-Location
    }
}

function Resolve-Forest3DPath([string]$Root) {
    $rootParent = Split-Path -Parent $Root
    $candidates = @(
        (Join-Path $Root "Forest3D"),
        (Join-Path $Root "drone\Forest3D"),
        (Join-Path $Root "on-demand-monitoring-system\Forest3D"),
        (Join-Path $rootParent "Forest3D"),
        (Join-Path $rootParent "on-demand-monitoring-system\Forest3D")
    ) | Select-Object -Unique

    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "models\compact_terrain\model.config")) {
            return (Resolve-Path $candidate).Path
        }
    }

    Update-Forest3DAssetsFromGit $Root
    foreach ($candidate in $candidates) {
        if (Test-Path (Join-Path $candidate "models\compact_terrain\model.config")) {
            return (Resolve-Path $candidate).Path
        }
    }

    $checked = ($candidates | ForEach-Object { "  - $_" }) -join [Environment]::NewLine
    throw "Cannot find Forest3D compact Gazebo assets. Make sure the package includes Forest3D\models\compact_terrain\model.config. Checked:$([Environment]::NewLine)$checked"
}

function Assert-DronePackage([string]$Forest3DPath, [string]$World) {
    if ($World -ne "compact") { return }

    $worldFile = Join-Path $Forest3DPath "worlds\forest_monitoring_compact.sdf"
    $modelRoot = Join-Path $Forest3DPath "models"
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
$forest3DPath = Resolve-Forest3DPath $repoRoot
Assert-DronePackage $forest3DPath $SimWorld
Initialize-StackEnv $repoRoot

if (-not $SkipBootstrap) {
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bootstrap-drone-stack.ps1") -UbuntuDistro $ubuntuDistro
}

$repoRootWsl = ConvertTo-WslPath $repoRoot
$forest3DPathWsl = ConvertTo-WslPath $forest3DPath
$scriptRoot = "$repoRootWsl/scripts"
$simArg = $SimWorld
$webOnly = if ($ShowGazeboGui) { "0" } else { "1" }
$baseWslEnv = "PROJECT_PATH='$repoRootWsl' FOREST3D_PATH='$forest3DPathWsl' CONTROL_YAW_STEP_DEG='$YawStepDeg'"
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
