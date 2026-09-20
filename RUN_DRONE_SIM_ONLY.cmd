@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "DRONE_ROOT=%SCRIPT_DIR%"

if not exist "%DRONE_ROOT%scripts\start-drone-stack.ps1" (
    set "DRONE_ROOT=%CD%\"
)

if not exist "%DRONE_ROOT%scripts\start-drone-stack.ps1" (
    set "DRONE_ROOT=%CD%\on-demand-monitoring-system\"
)

if not exist "%DRONE_ROOT%scripts\start-drone-stack.ps1" (
    echo [DRONE] Cannot find scripts\start-drone-stack.ps1.
    echo [DRONE] Run this from on-demand-monitoring-system or the repo parent folder.
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%DRONE_ROOT%scripts\start-drone-stack.ps1" -SimWorld compact %*
endlocal
