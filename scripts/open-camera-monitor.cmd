@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$repo=(Resolve-Path (Join-Path '%~dp0' '..')).Path; $wsl=(& wsl.exe -d Ubuntu-24.04 -- wslpath -a $repo).Trim(); Start-Process wsl.exe -ArgumentList '-d','Ubuntu-24.04','--','bash','-lc',\"PROJECT_PATH='$wsl' SIM_WORLD=compact exec '$wsl/scripts/wsl-camera-view.sh'\""
