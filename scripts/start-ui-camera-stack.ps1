param(
    [string]$UbuntuDistro = "Ubuntu-24.04",
    [switch]$SkipBootstrap,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$NoBrowser,
    [switch]$AllowNonPackagedWorld,
    [ValidateSet("compact", "legacy")]
    [string]$SimWorld = "compact"
)

$ErrorActionPreference = "Stop"

function Write-Step([string]$Message) {
    Write-Host ""
    Write-Host "==> $Message" -ForegroundColor Cyan
}

function ConvertTo-WslPath([string]$WindowsPath) {
    $fullPath = (Resolve-Path $WindowsPath).Path
    if ($fullPath -notmatch "^([A-Za-z]):\\(.*)$") {
        throw "Cannot convert path to WSL format: $fullPath"
    }

    $drive = $Matches[1].ToLowerInvariant()
    $rest = $Matches[2] -replace "\\", "/"
    return "/mnt/$drive/$rest"
}

function Start-TerminalTab([string]$Title, [string]$Command, [string]$WorkingDirectory) {
    $escapedCommand = $Command.Replace('"', '\"')
    $escapedDirectory = $WorkingDirectory.Replace('"', '\"')

    if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
        Start-Process wt.exe -ArgumentList @(
            "new-tab",
            "--title", $Title,
            "--startingDirectory", $WorkingDirectory,
            "powershell.exe",
            "-NoExit",
            "-ExecutionPolicy", "Bypass",
            "-Command", "Set-Location `"$escapedDirectory`"; $escapedCommand"
        )
        return
    }

    Start-Process powershell.exe -ArgumentList @(
        "-NoExit",
        "-ExecutionPolicy", "Bypass",
        "-Command", "Set-Location `"$escapedDirectory`"; $escapedCommand"
    )
}

function Start-WslTab([string]$Title, [string]$Command) {
    if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
        Start-Process wt.exe -ArgumentList @(
            "new-tab",
            "--title", $Title,
            "wsl.exe",
            "-d", $UbuntuDistro,
            "--",
            "bash",
            "-lc",
            $Command
        )
        return
    }

    Start-Process wsl.exe -ArgumentList @("-d", $UbuntuDistro, "--", "bash", "-lc", $Command)
}

function Wait-HttpOk([string]$Url, [int]$TimeoutSeconds = 90) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        } catch {
            Start-Sleep -Seconds 2
        }
    }
    return $false
}

function Set-EnvValue([string]$Path, [string]$Name, [string]$Value) {
    if (-not (Test-Path $Path)) {
        New-Item -ItemType File -Path $Path -Force | Out-Null
    }

    $lines = @(Get-Content -Path $Path -ErrorAction SilentlyContinue)
    $entry = "$Name=$Value"
    $found = $false

    $nextLines = foreach ($line in $lines) {
        if ($line -match "^\s*#") {
            $line
            continue
        }

        if ($line -match "^\s*$([regex]::Escape($Name))\s*=") {
            $found = $true
            $entry
        } else {
            $line
        }
    }

    if (-not $found) {
        $nextLines += $entry
    }

    Set-Content -Path $Path -Value $nextLines -Encoding UTF8
}

function Assert-CompactMapAssets([string]$Root) {
    $worldFile = Join-Path $Root "Forest3D\worlds\forest_monitoring_compact.sdf"
    $mapImage = Join-Path $Root "ondemandmonitoring\src\main\resources\static\simulation-viewer\simulation_map_top.png"
    $mapMeta = Join-Path $Root "ondemandmonitoring\src\main\resources\static\simulation-viewer\simulation-map.json"

    if (-not (Test-Path $worldFile)) {
        throw "Full compact world is missing: $worldFile"
    }
    if (-not (Test-Path $mapImage)) {
        throw "Full simulation map image is missing: $mapImage"
    }
    if (-not (Test-Path $mapMeta)) {
        throw "Simulation map metadata is missing: $mapMeta"
    }

    $meta = Get-Content -Raw -Path $mapMeta | ConvertFrom-Json
    if ($meta.worldName -ne "forest_monitoring_compact") {
        throw "Simulation map metadata is not compact world. Expected forest_monitoring_compact, got $($meta.worldName)."
    }
}

$systemRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$workspaceRoot = (Resolve-Path (Join-Path $systemRoot "..")).Path
$backendRoot = Join-Path $systemRoot "ondemandmonitoring"
$webRoot = Join-Path $workspaceRoot "ondemand-monitoring-web"

if (-not (Test-Path $backendRoot)) {
    throw "Backend folder not found: $backendRoot"
}

if (-not (Test-Path $webRoot)) {
    throw "Frontend folder not found: $webRoot"
}

if ($SimWorld -ne "compact" -and -not $AllowNonPackagedWorld) {
    throw "This packaged UI camera demo is compact-map only. Run scripts\start-ui-camera-stack.cmd with no -SimWorld argument. If you really need legacy, add -AllowNonPackagedWorld."
}

Assert-CompactMapAssets $systemRoot

$backendEnvFile = Join-Path $backendRoot ".env"
$packagedWorld = "compact"
$packagedWorldName = "forest_monitoring_compact"
$packagedDownTopic = "/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_down/image"
$packagedFrontTopic = "/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_front/image"

Set-EnvValue $backendEnvFile "SIM_WORLD" $packagedWorld
Set-EnvValue $backendEnvFile "GAZEBO_CAMERA_TOPIC" $packagedDownTopic
Set-EnvValue $backendEnvFile "GAZEBO_CAMERA_DOWN_TOPIC" $packagedDownTopic
Set-EnvValue $backendEnvFile "GAZEBO_CAMERA_FRONT_TOPIC" $packagedFrontTopic
Set-EnvValue $backendEnvFile "CAMERA_DEFAULT_VIEW" "DOWN"

Write-Host "========================================" -ForegroundColor Green
Write-Host " OMSS UI Camera Stack" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "Backend : http://localhost:8080"
Write-Host "Frontend: http://localhost:5173/#portal/drone-operator"
Write-Host "Control : http://localhost:8090/stream.mjpg"
Write-Host "World   : $packagedWorldName"
Write-Host "Map     : full compact simulation map"
Write-Host "Mode    : Web UI camera only, no separate Gazebo camera window"

if (-not $SkipBackend) {
    Write-Step "Starting Backend API"
    Start-TerminalTab `
        -Title "BE - OMSS API" `
        -WorkingDirectory $backendRoot `
        -Command ".\mvnw.cmd spring-boot:run"
} else {
    Write-Step "Skipping Backend API"
}

if (-not $SkipFrontend) {
    Write-Step "Preparing and starting Frontend UI"
    $envFile = Join-Path $webRoot ".env.local"
    $envContent = @"
VITE_API_BASE_URL=http://localhost:8080
VITE_FLIGHT_CONTROL_API_URL=http://localhost:8090
"@
    Set-Content -Path $envFile -Value $envContent -Encoding UTF8

    $installCommand = "if (-not (Test-Path node_modules)) { npm install }; npm run dev -- --host 0.0.0.0 --port 5173"
    Start-TerminalTab `
        -Title "FE - OMSS UI" `
        -WorkingDirectory $webRoot `
        -Command $installCommand
} else {
    Write-Step "Skipping Frontend UI"
}

if (-not $SkipBootstrap) {
    Write-Step "Checking WSL/PX4/Gazebo dependencies"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bootstrap-drone-stack.ps1") -UbuntuDistro $UbuntuDistro
    if ($LASTEXITCODE -ne 0) {
        throw "Drone dependency bootstrap failed."
    }
}

Write-Step "Cleaning old PX4/Gazebo/MAVSDK processes"
$systemRootWsl = ConvertTo-WslPath $systemRoot
$scriptRootWsl = "$systemRootWsl/scripts"
& wsl.exe -d $UbuntuDistro -- bash -lc "PROJECT_PATH='$systemRootWsl' exec '$scriptRootWsl/wsl-clean-drone-stack.sh'" | Out-Null

Write-Step "Starting PX4 + Gazebo headless for UI camera stream"
$webOnly = "1"
$simCommand = "PROJECT_PATH='$systemRootWsl' FOREST3D_WEB_ONLY=$webOnly SIM_WORLD=$packagedWorld exec '$scriptRootWsl/wsl-sim-pane.sh' '$packagedWorld'"
$controlCommand = "PROJECT_PATH='$systemRootWsl' FOREST3D_WEB_ONLY=$webOnly SIM_WORLD=$packagedWorld exec '$scriptRootWsl/wsl-control.sh'"

Start-WslTab -Title "SIM - PX4 + Gazebo Headless" -Command $simCommand
Start-Sleep -Seconds 3
Start-WslTab -Title "CTRL - Flight Control Stream" -Command $controlCommand

if (-not $NoBrowser) {
    Write-Step "Waiting for UI, then opening Mission Control"
    if (Wait-HttpOk "http://localhost:8080/simulation-viewer/simulation-map.json" 60) {
        try {
            $servedMeta = Invoke-RestMethod -Uri "http://localhost:8080/simulation-viewer/simulation-map.json" -TimeoutSec 5
            if ($servedMeta.worldName -ne $packagedWorldName) {
                Write-Host "Backend is serving map '$($servedMeta.worldName)' instead of '$packagedWorldName'." -ForegroundColor Red
                Write-Host "Stop old backend processes and run this script again." -ForegroundColor Red
            }
        } catch {
            Write-Host "Could not verify served simulation map metadata yet." -ForegroundColor Yellow
        }
    }

    if (Wait-HttpOk "http://localhost:5173" 90) {
        Start-Process "http://localhost:5173/#portal/drone-operator"
    } else {
        Write-Host "Frontend did not answer yet. Open manually: http://localhost:5173/#portal/drone-operator" -ForegroundColor Yellow
    }
}

Write-Step "Started"
Write-Host "Keep the opened terminal tabs running." -ForegroundColor Yellow
Write-Host "Use the UI Mission control page. The camera stream comes from http://localhost:8090/stream.mjpg."
