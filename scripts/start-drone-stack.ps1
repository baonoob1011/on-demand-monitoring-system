$ErrorActionPreference = "Stop"

$ubuntuDistro = "Ubuntu-24.04"

$controlCommand = "cd ~/drone-controller && source ~/drone-env/bin/activate && python flight_controller.py"
$telemetryCommand = "cd ~/drone-controller && source ~/drone-env/bin/activate && python telemetry_sender.py"
$px4Command = "cd ~/PX4-Autopilot && PX4_GZ_WORLD=forest make px4_sitl gz_x500"

wsl.exe -d $ubuntuDistro -- bash -lc "pkill -f 'px4_sitl|bin/px4|gz sim|ruby.*gz|gzclient|gzserver' || true; sleep 1"

if (Get-Command wt.exe -ErrorAction SilentlyContinue) {
    $wtArgs = @(
        "new-tab", "--title", "LEFT - Flight Control",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", $controlCommand,
        ";", "split-pane", "--horizontal", "--size", "0.66", "--title", "MIDDLE - Telemetry BE",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", $telemetryCommand,
        ";", "split-pane", "--horizontal", "--size", "0.50", "--title", "RIGHT - PX4 Gazebo Forest",
        "wsl.exe", "-d", $ubuntuDistro, "--", "bash", "-lc", $px4Command
    )

    & wt.exe @wtArgs
    exit 0
}

Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", $controlCommand
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", $telemetryCommand
Start-Process wsl.exe -ArgumentList "-d", $ubuntuDistro, "--", "bash", "-lc", $px4Command
