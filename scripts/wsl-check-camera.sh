#!/usr/bin/env bash
set -e

MODEL_NAME="${PX4_GZ_MODEL_NAME:-x500_mono_cam_down_0}"
CAMERA_TOPIC="${GAZEBO_CAMERA_TOPIC:-/world/forest_monitoring/model/${MODEL_NAME}/link/camera_link/sensor/camera/image}"

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
