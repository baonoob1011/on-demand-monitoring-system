$ErrorActionPreference = "Stop"

$ubuntuDistro = "Ubuntu-24.04"
$scriptRoot = "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts"

wsl.exe -d $ubuntuDistro -- bash "${scriptRoot}/wsl-clean-drone-stack.sh" | Out-Null

if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
    $wtArgs = @(
        "new-tab", "--title", "RIGHT - Gazebo + PX4",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-sim.sh",
        ";", "split-pane", "--horizontal", "--size", "0.66", "--title", "LEFT - Flight Control",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-control.sh",
        ";", "split-pane", "--vertical", "--size", "0.50", "--title", "MIDDLE - Telemetry BE",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-telemetry.sh",
        ";", "split-pane", "--horizontal", "--size", "0.50", "--title", "CAMERA - Downward View",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-camera-view.sh"
    )

    & wt.exe @wtArgs
    exit 0
}

Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-sim.sh"
Start-Sleep -Seconds 2
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-control.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-telemetry.sh"
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "${scriptRoot}/wsl-camera-view.sh"
