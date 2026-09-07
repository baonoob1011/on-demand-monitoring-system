#!/usr/bin/env bash
set -e

MODEL_NAME="${PX4_GZ_MODEL_NAME:-x500_mono_cam_down_0}"
SIM_WORLD="${SIM_WORLD:-legacy}"
if [ "$SIM_WORLD" = "compact" ]; then
    WORLD_NAME="forest_monitoring_compact"
else
    WORLD_NAME="${GZ_WORLD_NAME:-forest_monitoring}"
fi
CAMERA_TOPIC="${GAZEBO_CAMERA_TOPIC:-/world/${WORLD_NAME}/model/${MODEL_NAME}/link/camera_link/sensor/camera/image}"

echo '== Gazebo models =='
gz model --list 2>/dev/null || true

echo
echo "== Camera topics for ${MODEL_NAME} =="
gz topic -l | grep -E "camera|${MODEL_NAME}" || true

echo
echo "== Camera topic info =="
gz topic -i -t "${CAMERA_TOPIC}" || true

echo
echo "== One camera frame test =="
timeout 5 gz topic -e -t "${CAMERA_TOPIC}" -n 1 >/tmp/downward_camera_frame.txt 2>/tmp/downward_camera_frame.err \
  && echo "Camera publishes: ${CAMERA_TOPIC}" \
  || { echo "No frame received from: ${CAMERA_TOPIC}"; cat /tmp/downward_camera_frame.err; }
