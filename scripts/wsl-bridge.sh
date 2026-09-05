#!/usr/bin/env bash
set -e

echo 'Waiting 24s for Gazebo camera topic...'
sleep 24

source /opt/ros/jazzy/setup.bash 2>/dev/null || source /opt/ros/humble/setup.bash 2>/dev/null || true

if ! command -v ros2 >/dev/null 2>&1; then
  echo '[ROS Bridge] ros2 command not found.'
  echo '[ROS Bridge] Install/source ROS 2 and ros_gz_bridge to enable rqt_image_view.'
  echo '[ROS Bridge] Flight control and telemetry can still run without this bridge.'
  exec bash
fi

CAMERA_TOPIC="${GAZEBO_CAMERA_TOPIC:-/world/forest_monitoring/model/x500_mono_cam_down_0/link/camera_link/sensor/camera/image}"

ros2 run ros_gz_bridge parameter_bridge \
  "${CAMERA_TOPIC}@sensor_msgs/msg/Image@gz.msgs.Image"
