# OMSS UI Camera Stack

Use this when you want the drone camera inside the OMSS web UI only.

It starts:

- Backend API on `http://localhost:8080`
- Frontend UI on `http://localhost:5173`
- PX4 + Gazebo in headless/web-only mode
- Flight Controller API and camera stream on `http://localhost:8090`

It does not open:

- A separate Gazebo GUI window
- A separate camera viewer window
- LiDAR/sensor visualizer windows

## One-command start

From `on-demand-monitoring-system`:

```cmd
scripts\start-ui-camera-stack.cmd
```

Use exactly this command for the packaged demo. Do not pass `-SimWorld legacy`.

Then open:

```text
http://localhost:5173/#portal/drone-operator
```

The script normally opens this page automatically after the UI is ready.

This default command is the correct full compact map package:

- world: `forest_monitoring_compact`
- map image: `simulation_map_top.png`
- map metadata: `simulation-map.json`
- drone spawn: compact world spawn
- camera topic: compact world downward camera

The launcher also writes these values into `ondemandmonitoring/.env` before
starting the stack, so an old local environment cannot accidentally switch the
demo to another map:

```text
SIM_WORLD=compact
FOREST3D_WEB_ONLY=1
GAZEBO_CAMERA_TOPIC=/world/forest_monitoring_compact/model/x500_mono_cam_down_0/link/camera_link/sensor/camera_down/image
CAMERA_DEFAULT_VIEW=DOWN
```

## Faster restart

After the first successful setup, use:

```cmd
scripts\start-ui-camera-stack.cmd -SkipBootstrap
```

## Compact vs legacy world

Default is compact and is the expected full map:

```cmd
scripts\start-ui-camera-stack.cmd
```

The packaged launcher blocks legacy by default because it will not match the
full compact map UI. Only use this for old demos:

```cmd
scripts\start-ui-camera-stack.cmd -AllowNonPackagedWorld -SimWorld legacy
```

## If Backend or Frontend is already running

Skip what is already running:

```cmd
scripts\start-ui-camera-stack.cmd -SkipBackend
scripts\start-ui-camera-stack.cmd -SkipFrontend
scripts\start-ui-camera-stack.cmd -SkipBackend -SkipFrontend
```

## Healthy result

On the OMSS `Mission control` page:

- Drone status shows `LIVE`
- The main video panel shows the Gazebo drone camera stream
- Telemetry panel updates from `http://localhost:8090/api/control/status`
- Controls send commands to `http://localhost:8090/api/control/command`

## Common fixes

If the UI opens but the video is offline:

1. Wait 30-60 seconds for PX4/Gazebo and MAVSDK to connect.
2. Check the `CTRL - Flight Control Stream` terminal is still running.
3. Open `http://localhost:8090/api/control/status`.
4. If the endpoint does not load, close the stack terminals and run:

```cmd
scripts\start-ui-camera-stack.cmd -SkipBootstrap
```

If dependencies are missing on another machine, run without `-SkipBootstrap` once.

If another machine opens a smaller/old map, ask them to use the default command only:

```cmd
scripts\start-ui-camera-stack.cmd
```

Do not add `-SimWorld legacy`, `-ShowGazeboGui`, or `-WithCamera` for the UI-only demo.
