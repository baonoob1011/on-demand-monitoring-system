#!/usr/bin/env bash
set -e

SIM_WORLD="${1:-${SIM_WORLD:-light}}"

case "$SIM_WORLD" in
    light) WORLD_NAME="default" ;;
    compact) WORLD_NAME="forest_monitoring_compact" ;;
    legacy) WORLD_NAME="forest_monitoring" ;;
    *) WORLD_NAME="$SIM_WORLD" ;;
esac

echo "[GAZEBO] Waiting for Gazebo server: $WORLD_NAME"
for _ in $(seq 1 90); do
    if pgrep -f "gz sim .* -s .*worlds/${WORLD_NAME}\.sdf" >/dev/null 2>&1 \
        || pgrep -f "gz sim .* -s" >/dev/null 2>&1; then
        break
    fi
    sleep 1
done

if [ -d "/run/user/$(id -u)" ]; then
    chmod 700 "/run/user/$(id -u)" 2>/dev/null || true
fi

echo "[GAZEBO] Opening Gazebo GUI..."
exec gz sim -g
