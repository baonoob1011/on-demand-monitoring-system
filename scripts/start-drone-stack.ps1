param(
    [ValidateSet("legacy", "compact")]
    [string]$SimWorld = "compact"
)

$ErrorActionPreference = "Stop"

$ubuntuDistro = "Ubuntu-24.04"
$scriptRoot = "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts"
$simArg = $SimWorld
$simCommand = "SIM_WORLD=${simArg} ${scriptRoot}/wsl-sim.sh ${simArg}; code=`$?; echo; echo ""[STACK] Gazebo/PX4 exited with code `$code. Press Ctrl+D to close this pane.""; exec bash"

wsl.exe -d $ubuntuDistro -- bash -lc "exec ${scriptRoot}/wsl-clean-drone-stack.sh" | Out-Null

if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
    $wtArgs = @(
        "new-tab", "--title", "RIGHT - Gazebo + PX4",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", $simCommand,
        ";", "split-pane", "--horizontal", "--size", "0.66", "--title", "LEFT - Flight Control",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-control.sh",
        ";", "split-pane", "--vertical", "--size", "0.50", "--title", "MIDDLE - Telemetry BE",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-telemetry.sh",
        ";", "new-tab", "--title", "CAMERA - Downward View",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-camera-view.sh",
        ";", "new-tab", "--title", "SENSOR - LiDAR 2D + 3D",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-sensor-monitor.sh"
    )

    & wt.exe @wtArgs
    exit 0
}

Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", $simCommand
Start-Sleep -Seconds 2
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-control.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-telemetry.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-camera-view.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-sensor-monitor.sh"
