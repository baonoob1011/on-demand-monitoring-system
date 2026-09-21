param(
    [string]$UbuntuDistro = "Ubuntu-24.04",
    [switch]$SkipBootstrap,
    [switch]$SkipBuild,
    [switch]$SkipBackend,
    [switch]$SkipFrontend,
    [switch]$SkipDrone,
    [switch]$NoBrowser
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

function Write-Utf8NoBomLines([string]$Path, [string[]]$Lines) {
    $encoding = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($Path, $Lines, $encoding)
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
        } elseif ($line -match "^\s*$([regex]::Escape($Name))\s*=") {
            $found = $true
            $entry
        } else {
            $line
        }
    }
    if (-not $found) {
        $nextLines += $entry
    }
    Write-Utf8NoBomLines $Path $nextLines
}

function Start-TerminalTab([string]$Title, [string]$Command, [string]$WorkingDirectory) {
    $escapedCommand = $Command.Replace('"', '\"')
    $escapedDirectory = $WorkingDirectory.Replace('"', '\"')

    if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
        Start-Process wt.exe -ArgumentList @(
            "new-tab", "--title", $Title, "--startingDirectory", $WorkingDirectory,
            "powershell.exe", "-NoExit", "-ExecutionPolicy", "Bypass",
            "-Command", "Set-Location `"$escapedDirectory`"; $escapedCommand"
        )
        return
    }

    Start-Process powershell.exe -ArgumentList @(
        "-NoExit", "-ExecutionPolicy", "Bypass",
        "-Command", "Set-Location `"$escapedDirectory`"; $escapedCommand"
    )
}

function Start-WslTab([string]$Title, [string]$Command) {
    if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
        Start-Process wt.exe -ArgumentList @(
            "new-tab", "--title", $Title,
            "wsl.exe", "-d", $UbuntuDistro, "--", "bash", "-lc", $Command
        )
        return
    }

    Start-Process wsl.exe -ArgumentList @("-d", $UbuntuDistro, "--", "bash", "-lc", $Command)
}

function Start-WslWindow([string]$Title, [string]$Command) {
    $escapedCommand = $Command.Replace('"', '\"')
    $cmdLine = "title $Title && wsl.exe -d $UbuntuDistro -- bash -lc `"$escapedCommand`""
    Start-Process cmd.exe -ArgumentList @("/k", $cmdLine) -WindowStyle Normal
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

function Test-CommandExists([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Get-JavaMajorVersion {
    if (-not (Test-CommandExists "java.exe")) { return 0 }

    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $versionOutput = & java.exe -version 2>&1 | ForEach-Object { $_.ToString() } | Select-Object -First 1
        if ($versionOutput -match '"(?<version>[0-9]+)(\.|")') {
            return [int]$Matches["version"]
        }
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    return 0
}

function Install-WithWinget([string]$PackageId, [string]$DisplayName) {
    if (-not (Test-CommandExists "winget.exe")) {
        throw "$DisplayName is missing and winget is not available. Install $DisplayName, then run RUN_DRONE_STACK.cmd again."
    }

    Write-Step "Installing $DisplayName"
    & winget.exe install --id $PackageId --exact --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        throw "Could not install $DisplayName automatically. Install it manually, then run RUN_DRONE_STACK.cmd again."
    }

    $machinePath = [Environment]::GetEnvironmentVariable("Path", "Machine")
    $userPath = [Environment]::GetEnvironmentVariable("Path", "User")
    $env:Path = "$machinePath;$userPath"
}

function Ensure-WindowsBuildTools {
    if ((Get-JavaMajorVersion) -lt 21) {
        Install-WithWinget "EclipseAdoptium.Temurin.21.JDK" "Java 21 JDK"
    }

    if (-not (Test-CommandExists "node.exe")) {
        Install-WithWinget "OpenJS.NodeJS.LTS" "Node.js LTS"
    }

    if (-not (Test-CommandExists "npm.cmd")) {
        throw "npm is still unavailable after checking Node.js. Reopen the terminal or restart Windows, then run RUN_DRONE_STACK.cmd again."
    }

    if ((Get-JavaMajorVersion) -lt 21) {
        throw "Java 21 is still unavailable. Reopen the terminal or restart Windows, then run RUN_DRONE_STACK.cmd again."
    }
}

function Invoke-Checked([string]$Label, [string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory) {
    Write-Step $Label
    Push-Location $WorkingDirectory
    try {
        & $FilePath @Arguments
        if ($LASTEXITCODE -ne 0) {
            throw "$Label failed with exit code $LASTEXITCODE."
        }
    } finally {
        Pop-Location
    }
}

function Assert-CompactMapAssets([string]$Root) {
    $worldFile = Join-Path $Root "Forest3D\worlds\forest_monitoring_compact.sdf"
    $mapImage = Join-Path $Root "ondemandmonitoring\src\main\resources\static\simulation-viewer\simulation_map_top.png"
    $mapMeta = Join-Path $Root "ondemandmonitoring\src\main\resources\static\simulation-viewer\simulation-map.json"
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

    if (-not (Test-Path $worldFile)) { throw "Full compact world is missing: $worldFile" }
    if (-not (Test-Path $mapImage)) { throw "Full simulation map image is missing: $mapImage" }
    if (-not (Test-Path $mapMeta)) { throw "Simulation map metadata is missing: $mapMeta" }
    foreach ($model in $requiredModels) {
        $config = Join-Path $modelRoot "$model\model.config"
        $sdf = Join-Path $modelRoot "$model\model.sdf"
        if (-not (Test-Path $config)) { throw "Packaged Gazebo model config is missing: $config" }
        if (-not (Test-Path $sdf)) { throw "Packaged Gazebo model SDF is missing: $sdf" }
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
$packagedWorld = "compact"
$packagedWorldName = "forest_monitoring_compact"
$downTopic = "/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_down/image"
$frontTopic = "/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_front/image"

if (-not (Test-Path $backendRoot)) { throw "Backend folder not found: $backendRoot" }
if (-not (Test-Path $webRoot)) { throw "Frontend folder not found: $webRoot" }
Assert-CompactMapAssets $systemRoot

$backendEnvFile = Join-Path $backendRoot ".env"
Set-EnvValue $backendEnvFile "SIM_WORLD" $packagedWorld
Set-EnvValue $backendEnvFile "FOREST3D_WEB_ONLY" "1"
Set-EnvValue $backendEnvFile "GAZEBO_CAMERA_TOPIC" $downTopic
Set-EnvValue $backendEnvFile "GAZEBO_CAMERA_DOWN_TOPIC" $downTopic
Set-EnvValue $backendEnvFile "GAZEBO_CAMERA_FRONT_TOPIC" $frontTopic
Set-EnvValue $backendEnvFile "CAMERA_DEFAULT_VIEW" "DOWN"

Write-Host "========================================" -ForegroundColor Green
Write-Host " OMSS UI Camera Stack" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host "Backend : http://localhost:8080"
Write-Host "Frontend: http://localhost:5173/#portal/drone-operator"
Write-Host "Control : http://localhost:8090/stream.mjpg"
Write-Host "World   : $packagedWorldName"
Write-Host "Weather : separate WEATHER - Controls window"
Write-Host "Mode    : Web UI camera only, no separate Gazebo camera window"

Ensure-WindowsBuildTools

if (-not $SkipBuild) {
    Invoke-Checked `
        -Label "Building Backend API" `
        -FilePath (Join-Path $backendRoot "mvnw.cmd") `
        -Arguments @("-DskipTests", "package") `
        -WorkingDirectory $backendRoot

    Invoke-Checked `
        -Label "Installing Frontend packages" `
        -FilePath "npm.cmd" `
        -Arguments @("install") `
        -WorkingDirectory $webRoot

    Invoke-Checked `
        -Label "Building Frontend UI" `
        -FilePath "npm.cmd" `
        -Arguments @("run", "build") `
        -WorkingDirectory $webRoot
}

if (-not $SkipBackend) {
    Write-Step "Starting Backend API"
    Start-TerminalTab -Title "BE - OMSS API" -WorkingDirectory $backendRoot -Command ".\mvnw.cmd spring-boot:run"
}

if (-not $SkipFrontend) {
    Write-Step "Preparing and starting Frontend UI"
    $envFile = Join-Path $webRoot ".env.local"
    Write-Utf8NoBomLines $envFile @(
        "VITE_API_BASE_URL=http://localhost:8080",
        "VITE_FLIGHT_CONTROL_API_URL=http://localhost:8090"
    )
    Start-TerminalTab -Title "FE - OMSS UI" -WorkingDirectory $webRoot -Command "if (-not (Test-Path node_modules)) { npm install }; npm run dev -- --host 0.0.0.0 --port 5173"
}

if (-not $SkipBootstrap) {
    Write-Step "Checking WSL/PX4/Gazebo dependencies"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bootstrap-drone-stack.ps1") -UbuntuDistro $UbuntuDistro
    if ($LASTEXITCODE -ne 0) { throw "Drone dependency bootstrap failed." }
}

$systemRootWsl = ConvertTo-WslPath $systemRoot
$scriptRootWsl = "$systemRootWsl/scripts"

if ($SkipDrone) {
    Write-Step "Build/setup finished; drone startup was skipped"
    exit 0
}

Write-Step "Cleaning old PX4/Gazebo/MAVSDK processes"
& wsl.exe -d $UbuntuDistro -- bash -lc "PROJECT_PATH='$systemRootWsl' exec '$scriptRootWsl/wsl-clean-drone-stack.sh'" | Out-Null

Write-Step "Starting PX4/Gazebo, Flight Control, and Weather Controls"
$simCommand = "PROJECT_PATH='$systemRootWsl' FOREST3D_WEB_ONLY=1 SIM_WORLD=$packagedWorld exec '$scriptRootWsl/wsl-sim-pane.sh' '$packagedWorld'"
$controlCommand = "PROJECT_PATH='$systemRootWsl' FOREST3D_WEB_ONLY=1 SIM_WORLD=$packagedWorld exec '$scriptRootWsl/wsl-control.sh'"
$weatherCommand = "PROJECT_PATH='$systemRootWsl' SIM_WORLD=$packagedWorld exec '$scriptRootWsl/wsl-weather-control.sh'"

Start-WslTab -Title "SIM - PX4 + Gazebo Headless" -Command $simCommand
Start-Sleep -Seconds 3
Start-WslTab -Title "CTRL - Flight Control Stream" -Command $controlCommand
Start-Sleep -Seconds 1
Start-WslWindow -Title "WEATHER - Controls" -Command $weatherCommand

if (-not $NoBrowser) {
    Write-Step "Waiting for UI, then opening Mission Control"
    if (Wait-HttpOk "http://localhost:5173" 90) {
        Start-Process "http://localhost:5173/#portal/drone-operator"
    } else {
        Write-Host "Frontend did not answer yet. Open manually: http://localhost:5173/#portal/drone-operator" -ForegroundColor Yellow
    }
}

Write-Step "Started"
Write-Host "Weather works in Flight Control with one key: u clear, y sunset, i night, g cloudy, j foggy, m windy, b light, z heavy." -ForegroundColor Yellow
Write-Host "The separate WEATHER - Controls window supports the same one-key controls." -ForegroundColor Yellow
