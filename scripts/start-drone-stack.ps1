param(
    [ValidateSet("legacy", "compact")]
    [string]$SimWorld = "compact"
)

$ErrorActionPreference = "Stop"

$ubuntuDistro = "Ubuntu-24.04"
$scriptRoot = "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts"
$simArg = $SimWorld

wsl.exe -d $ubuntuDistro -- bash -lc "exec ${scriptRoot}/wsl-clean-drone-stack.sh" | Out-Null

if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
    $wtArgs = @(
        "new-tab", "--title", "RIGHT - Gazebo + PX4",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-sim.sh ${simArg}",
        ";", "split-pane", "--horizontal", "--size", "0.66", "--title", "LEFT - Flight Control",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-control.sh",
        ";", "split-pane", "--vertical", "--size", "0.50", "--title", "MIDDLE - Telemetry BE",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-telemetry.sh",
        ";", "split-pane", "--horizontal", "--size", "0.50", "--title", "LiDAR Sensor",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-lidar-sensor.sh",
        ";", "new-tab", "--title", "CAMERA - Downward View",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-camera-view.sh",
        ";", "new-tab", "--title", "WEATHER - Controls",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-weather-control.sh"
    )

    & wt.exe @wtArgs
    exit 0
}

Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-sim.sh ${simArg}"
Start-Sleep -Seconds 2
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-control.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-telemetry.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "exec ${scriptRoot}/wsl-lidar-sensor.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-camera-view.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", "SIM_WORLD=${simArg} exec ${scriptRoot}/wsl-weather-control.sh"
