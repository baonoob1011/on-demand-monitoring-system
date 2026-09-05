#!/usr/bin/env bash
set -euo pipefail

pkill -u "$USER" -f '[m]avsdk_server' 2>/dev/null || true
pkill -u "$USER" -f '[m]ake px4_sitl' 2>/dev/null || true
pkill -u "$USER" -f '[b]in/px4' 2>/dev/null || true
pkill -u "$USER" -f '[g]z sim' 2>/dev/null || true
pkill -u "$USER" -f '[g]z gui' 2>/dev/null || true
sleep 2
