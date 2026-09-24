$ErrorActionPreference = "Stop"

$projectPath = "C:\Users\ACER\Documents\GitHub\doan\on-demand-monitoring-system\Forest3D"
$wslProjectPath = "/mnt/c/Users/ACER/Documents/GitHub/doan/on-demand-monitoring-system/Forest3D"

Write-Host "Launching Forest3D capstone world from:"
Write-Host "  $projectPath"
Write-Host ""
Write-Host "Expected Gazebo entities include:"
Write-Host "  drone_landing_pad, control_cabin, trail_base, communication_mast, burnt_ground_patch"
Write-Host ""

wsl.exe -d Ubuntu-24.04 -- /bin/sh -lc "pkill -f '[g]z sim' 2>/dev/null || true; pkill -x gzclient 2>/dev/null || true; pkill -x gzserver 2>/dev/null || true; cd '$wslProjectPath' && gz sim -v 2 -r worlds/forest_monitoring.sdf"
