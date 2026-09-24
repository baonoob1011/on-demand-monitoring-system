# On-Demand Monitoring Drone Controller

Small Python adapter for the capstone prototype:

```text
PX4 SITL -> MAVSDK / MAVLink -> Python Drone Controller -> Spring Boot -> PostgreSQL
```

This project only forwards telemetry. It does not modify PX4 and does not implement missions, arm, takeoff, landing, waypoints, MQTT, WebSocket, frontend, camera, or video streaming.

## Terminal 1 - PX4

```bash
cd ~/PX4-Autopilot
make px4_sitl gz_x500
```

Expected PX4 output:

```text
Ready for takeoff!
```

Keep this terminal running.

## Terminal 2 - Spring Boot

Start the Spring Boot backend and make sure it listens on:

```text
http://localhost:8080
```

This controller posts telemetry to:

```text
http://localhost:8080/api/devices/DRONE-01/telemetry
```

## Terminal 3 - Drone Controller

```bash
cd ~/drone-controller
source ~/drone-env/bin/activate
pip install -r requirements.txt
python telemetry_sender.py
```

If the backend is not running yet, this is acceptable:

```text
[BACKEND] Unavailable - retrying later
```

The important first test is seeing:

```text
[PX4] Connected
```

and real telemetry values from PX4 SITL.

## Configuration

Local development values are stored in `.env`:

```properties
PX4_SYSTEM_ADDRESS=udp://:14540
BACKEND_BASE_URL=http://localhost:8080
DEVICE_CODE=DRONE-01
TELEMETRY_INTERVAL_SECONDS=2
```

## Send Gazebo Screenshots To Spring Boot

Configure this value in `.env`:

```properties
GAZEBO_PICTURES_DIR=/home/acer/.gz/gui/pictures
```

AWS S3 upload is handled by the Spring Boot backend. Configure AWS in the
Spring Boot `.env`, not in this Python project.

Run the screenshot watcher in a separate terminal:

```bash
cd ~/drone-controller
source ~/drone-env/bin/activate
pip install -r requirements.txt
python s3_image_uploader.py
```

When you click the camera button in Gazebo, the image is saved under
`/home/acer/.gz/gui/pictures`, sent to Spring Boot, then Spring Boot uploads it
to S3.
