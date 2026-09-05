#!/usr/bin/env bash
set -e

sleep 22
cd ~/drone-controller
source ~/drone-env/bin/activate
python telemetry_sender.py
