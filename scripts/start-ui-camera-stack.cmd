@echo off
setlocal
set SIM_WORLD=compact
set FOREST3D_WEB_ONLY=1
set GAZEBO_CAMERA_TOPIC=/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_down/image
set GAZEBO_CAMERA_DOWN_TOPIC=/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_down/image
set GAZEBO_CAMERA_FRONT_TOPIC=/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_front/image
set CAMERA_DEFAULT_VIEW=DOWN
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-ui-camera-stack.ps1" %*
endlocal
