# Drone Stack Notes

## Why the drone disappears

The drone can disappear from Gazebo when old PX4/Gazebo/MAVSDK processes are killed at the wrong time.

The dangerous pattern is:

1. `start-drone-stack.ps1` opens several Windows Terminal panes.
2. `wsl-control.sh` starts the Python Flight Controller and MAVSDK server.
3. `wsl-sim.sh` starts later and runs `pkill` again.
4. That late cleanup kills `mavsdk_server`, `px4`, `gz sim`, or `gz gui` while the stack is already starting.
5. Gazebo may still show the world, but the PX4 drone process or bridge is broken.

Result:

- Gazebo world opens.
- Landing pad is visible.
- `x500_mono_cam_down_0` may be missing, or PX4 dies after spawning.
- Downward Camera may subscribe to a topic but show a dark/empty image.
- Flight Controller can fail on arm with `grpc.aio._call.AioRpcError: StatusCode.UNAVAILABLE`.

## Correct startup logic

Cleanup must happen once, before any panes start:

```text
start-drone-stack.ps1
  -> wsl-clean-drone-stack.sh
  -> open RIGHT - Gazebo + PX4
  -> open LEFT - Flight Control
  -> open MIDDLE - Telemetry BE
  -> open CAMERA - Downward View
```

`wsl-sim.sh` must not run `pkill` after the other panes are open.

## Important scripts

- `scripts/start-drone-stack.ps1`: starts the whole stack from Windows.
- `scripts/wsl-clean-drone-stack.sh`: kills old PX4/Gazebo/MAVSDK processes once at the beginning.
- `scripts/wsl-sim.sh`: starts PX4 SITL, Gazebo, and spawns the drone.
- `scripts/wsl-control.sh`: starts the Python Flight Controller.
- `scripts/wsl-camera-view.sh`: waits for the downward camera topic, then opens the camera viewer.

## Known-good drone launch command

`wsl-sim.sh` should use PX4 to launch Gazebo and spawn the camera drone:

```bash
PX4_GZ_WORLD=forest_monitoring PX4_GZ_MODEL_POSE="0,1.4,0.3,0,0,0" make px4_sitl gz_x500_mono_cam_down
```

Notes:

- `gz_x500_mono_cam_down` gives the drone its downward camera.
- `x500_mono_cam_down_0` is the expected spawned Gazebo model name.
- The camera topic should be:

```text
/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image
```

## Quick checks

Run these in WSL if the drone looks missing:

```bash
pgrep -a -f "make px4_sitl|bin/px4|gz sim|mavsdk_server"
gz model --list | grep -E "x500_mono_cam_down_0|drone_landing_pad"
gz topic -l | grep "x500_mono_cam_down_0/link/camera_link/sensor/camera/image"
```

Healthy output should include:

```text
make px4_sitl gz_x500_mono_cam_down
bin/px4
gz sim
x500_mono_cam_down_0
/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image
```

## If arm crashes

If Flight Controller crashes on takeoff/arm with:

```text
StatusCode.UNAVAILABLE
Stream removed
Socket closed
Connection reset by peer
```

it usually means MAVSDK was killed or disconnected. Restart the whole stack from `scripts/start-drone-stack.cmd` after closing old Gazebo/terminal windows.

`flight_controller.py` should catch `grpc.aio.AioRpcError` during arm so the app prints a warning and retries instead of exiting with a traceback.
