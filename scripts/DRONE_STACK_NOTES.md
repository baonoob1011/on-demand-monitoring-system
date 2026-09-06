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
PX4_GZ_WORLD=forest_monitoring PX4_GZ_MODEL_POSE="0,0,0.3,0,0,0" make px4_sitl gz_x500_mono_cam_down
```

Notes:

- `gz_x500_mono_cam_down` gives the drone its downward camera.
- Keep `PX4_GZ_MODEL_POSE="0,0,0.3,0,0,0"` so PX4 spawns one drone at the center/origin.
- `x500_mono_cam_down_0` is the expected spawned Gazebo model name.
- Pressing `p` in the Flight Control tab captures from the drone camera sensor topic, not from the Gazebo window or desktop screen.
- The camera topic should be:

```text
/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image
```

## Locked main Gazebo camera behavior

Do not change this behavior unless the user explicitly asks to replace the camera system.

The main Gazebo Sim viewport camera must keep the user's current third-person overview angle and follow the spawned drone model:

```text
target model: x500_mono_cam_down_0
fallback target prefix: x500_mono_cam_down
follow offset: x=-8, y=0, z=4
view purpose: main Gazebo viewport follows the drone while keeping the current visible angle around the landing pad/world
```

Important rules:

- Keep the main Gazebo viewport camera following the drone model, not a building, landing pad, world origin, or fixed pose.
- Keep the separate `Downward Camera` window unchanged; it is the onboard camera sensor viewer.
- Do not switch to an overly close PUBG-style offset such as `x=-3, z=1.6` unless the user explicitly asks again.
- Do not replace the GUI with a minimal or blank camera-tracking-only config.
- If camera tracking stops working, first verify `/gui/follow` and `/gui/follow/offset` services exist, then set:

```bash
gz service -s /gui/follow \
  --reqtype gz.msgs.StringMsg \
  --reptype gz.msgs.Boolean \
  --timeout 5000 \
  --req 'data: "x500_mono_cam_down_0"'

gz service -s /gui/follow/offset \
  --reqtype gz.msgs.Vector3d \
  --reptype gz.msgs.Boolean \
  --timeout 5000 \
  --req 'x: -8 y: 0 z: 4'
```

This camera angle and follow logic is considered known-good and should be preserved.

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
