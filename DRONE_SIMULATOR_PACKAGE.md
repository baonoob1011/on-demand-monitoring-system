# Drone Simulator Package

This folder is portable. Do not edit scripts to point at a personal path like
`C:\Users\...`; the launcher resolves the current repo location automatically.

## One-click Run

- Full UI + backend + drone simulator:
  - `RUN_DRONE_STACK.cmd`
- Drone simulator only:
  - `RUN_DRONE_SIM_ONLY.cmd`
- Create a clean zip package for another Windows machine:
  - `PACKAGE_DRONE_STACK.cmd`
- Vietnamese A-to-Z guide that can also start the stack:
  - `HUONG_DAN_CHAY_DRONE_STACK.cmd`

Both launchers use the folder they are stored in as `PROJECT_PATH`, convert it to
WSL format, then start the scripts from that location.

`RUN_DRONE_STACK.cmd` now checks the Windows-side tools too. If Java 21 or
Node.js LTS are missing, it tries to install them with `winget`; if Windows needs
a restart or a new terminal session afterward, run the same file again. It then
builds the Spring Boot backend and React frontend before starting the stack.

## Packaged Assets

The compact simulator package includes:

- `Forest3D/worlds/forest_monitoring_compact.sdf`
- `Forest3D/models/x500_mono_cam_down`
- `Forest3D/models/compact_terrain`
- `Forest3D/models/compact_water`
- `Forest3D/models/compact_roads`
- `Forest3D/models/compact_bridges`
- `Forest3D/models/compact_home`
- `Forest3D/models/compact_highrise`
- `Forest3D/models/compact_zones`
- `Forest3D/models/compact_forest`
- `Forest3D/models/compact_thermal_sources`
- `Forest3D/models/compact_environment_props`
- `Forest3D/models/compact_mountains`
- `Forest3D/models/compact_airport`
- `ondemandmonitoring/src/main/resources/static/simulation-viewer`

The launch scripts validate these files before starting, so missing map or model
files fail early with a clear message instead of Gazebo `model://...` errors.

## Requirements

- Windows with WSL available. If Ubuntu is missing, the launcher asks WSL to
  install `Ubuntu-24.04`.
- Ubuntu distro name defaults to `Ubuntu-24.04`.
- Internet access on first bootstrap so PX4 and Python dependencies can install.
- Frontend folder `ondemand-monitoring-web` should sit next to this folder when
  using `RUN_DRONE_STACK.cmd`.

## Useful Options

```powershell
.\RUN_DRONE_STACK.cmd -SkipBootstrap
.\RUN_DRONE_STACK.cmd -SkipBuild
.\RUN_DRONE_STACK.cmd -SkipDrone
.\RUN_DRONE_STACK.cmd -NoBrowser
.\RUN_DRONE_SIM_ONLY.cmd -WithWeather
.\PACKAGE_DRONE_STACK.cmd
.\HUONG_DAN_CHAY_DRONE_STACK.cmd
```
