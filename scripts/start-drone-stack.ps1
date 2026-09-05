$ErrorActionPreference = "Stop"

$ubuntuDistro = "Ubuntu-24.04"
$scriptRoot = "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts"

$killOldCommand = "pkill -u `$USER -f '[m]avsdk_server' 2>/dev/null || true; pkill -u `$USER -f '[m]ake px4_sitl' 2>/dev/null || true; pkill -u `$USER -f '[b]in/px4' 2>/dev/null || true; pkill -u `$USER -f '[g]z sim' 2>/dev/null || true; pkill -u `$USER -f '[g]z gui' 2>/dev/null || true"
wsl.exe -d $ubuntuDistro -- bash -lc $killOldCommand | Out-Null
Start-Sleep -Seconds 2

if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
    $wtArgs = @(
        "new-tab", "--title", "LEFT - Flight Control",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-control.sh",
        ";", "split-pane", "--horizontal", "--size", "0.66", "--title", "MIDDLE - Telemetry BE",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-telemetry.sh",
        ";", "split-pane", "--vertical", "--size", "0.50", "--title", "CAMERA - Downward View",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-camera-view.sh",
        ";", "split-pane", "--horizontal", "--size", "0.50", "--title", "RIGHT - Gazebo + PX4",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-sim.sh"
    )

    & wt.exe @wtArgs
    exit 0
}

Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-control.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-telemetry.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-camera-view.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-sim.sh"
