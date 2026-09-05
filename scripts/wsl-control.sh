#!/usr/bin/env bash
set -e

echo 'Waiting 35s for PX4 + Gazebo to fully initialize...'
sleep 35

cd ~/drone-controller
source ~/drone-env/bin/activate
python flight_controller.py
