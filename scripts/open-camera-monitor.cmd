@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "Start-Process wsl.exe -ArgumentList '-d','Ubuntu-24.04','--','bash','-lc','SIM_WORLD=compact exec /mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/scripts/wsl-camera-view.sh'"
