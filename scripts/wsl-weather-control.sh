#!/usr/bin/env bash
# Runtime weather hotkeys for Gazebo Sim. This does not restart PX4/Gazebo.
set +e

WORLD="${GZ_WORLD_NAME:-forest_monitoring}"
LIGHT_SERVICE="/world/${WORLD}/light_config"
WIND_TOPIC="/world/${WORLD}/wind"
CURRENT_WEATHER="CLEAR_DAY"

print_menu() {
    echo '========================================'
    echo ' Weather Controls'
    echo '========================================'
    echo '1 = Clear Day'
    echo '2 = Sunset'
    echo '3 = Night'
    echo '4 = Cloudy'
    echo '5 = Foggy'
    echo '6 = Windy'
    echo '7 = Light Rain'
    echo '8 = Heavy Rain'
    echo '0 = Status'
    echo '========================================'
}

service_exists() {
    gz service -l 2>/dev/null | grep -qx "$1"
}

topic_exists() {
    gz topic -l 2>/dev/null | grep -qx "$1"
}

set_light() {
    local intensity="$1"
    local direction="$2"
    local diffuse="$3"
    local specular="$4"

    if ! service_exists "$LIGHT_SERVICE"; then
        echo "[WEATHER][WARN] Light service not ready: $LIGHT_SERVICE"
        return 1
    fi

    timeout 3 gz service -s "$LIGHT_SERVICE" \
        --reqtype gz.msgs.Light \
        --reptype gz.msgs.Boolean \
        --timeout 2000 \
        --req "name: \"sunUTC\" type: DIRECTIONAL cast_shadows: true intensity: ${intensity} direction { ${direction} } diffuse { ${diffuse} } specular { ${specular} }" \
        >/dev/null 2>&1
}

set_wind() {
    local linear_velocity="$1"

    if ! topic_exists "$WIND_TOPIC"; then
        echo "[WEATHER][WARN] Wind topic not ready: $WIND_TOPIC"
        return 1
    fi

    timeout 2 gz topic -t "$WIND_TOPIC" \
        -m gz.msgs.Wind \
        -d 0.2 \
        -p "enable_wind: true linear_velocity { ${linear_velocity} }" \
        >/dev/null 2>&1
}

disable_wind() {
    if topic_exists "$WIND_TOPIC"; then
        timeout 2 gz topic -t "$WIND_TOPIC" \
            -m gz.msgs.Wind \
            -d 0.2 \
            -p 'enable_wind: false linear_velocity { x: 0 y: 0 z: 0 }' \
            >/dev/null 2>&1
    fi
}

rain_notice() {
    local preset="$1"
    case "$preset" in
        LIGHT_RAIN|HEAVY_RAIN)
            echo "[WEATHER][WARN] Visible rain particles are not runtime-wired in this world yet; light/wind changed only."
            ;;
        *)
            echo "[WEATHER] Rain disabled/not spawned."
            ;;
    esac
}

fog_notice() {
    local preset="$1"
    if [ "$preset" = "FOGGY" ] || [ "$preset" = "HEAVY_RAIN" ]; then
        echo "[WEATHER][WARN] Runtime fog service not exposed by this Gazebo setup; use startup weather for real fog."
    fi
}

apply_preset() {
    local key="$1"
    local preset="$2"

    if [ "$CURRENT_WEATHER" = "$preset" ]; then
        echo "[WEATHER] ${preset} already active"
        return 0
    fi

    echo "[WEATHER] ${key} -> ${preset}"
    local status=0

    case "$preset" in
        CLEAR_DAY)
            set_light "1.2" "x: -0.5 y: 0.5 z: -0.8" "r: 0.95 g: 0.93 b: 0.88 a: 1" "r: 0.3 g: 0.3 b: 0.25 a: 1" || status=1
            disable_wind || status=1
            ;;
        SUNSET)
            set_light "0.75" "x: -0.9 y: 0.15 z: -0.25" "r: 1.0 g: 0.48 b: 0.22 a: 1" "r: 0.55 g: 0.25 b: 0.12 a: 1" || status=1
            disable_wind || status=1
            ;;
        NIGHT)
            set_light "0.12" "x: -0.25 y: 0.35 z: -0.9" "r: 0.08 g: 0.1 b: 0.18 a: 1" "r: 0.02 g: 0.03 b: 0.06 a: 1" || status=1
            disable_wind || status=1
            ;;
        CLOUDY)
            set_light "0.45" "x: -0.35 y: 0.4 z: -0.85" "r: 0.45 g: 0.5 b: 0.58 a: 1" "r: 0.12 g: 0.13 b: 0.15 a: 1" || status=1
            disable_wind || status=1
            ;;
        FOGGY)
            set_light "0.35" "x: -0.25 y: 0.25 z: -0.9" "r: 0.55 g: 0.58 b: 0.6 a: 1" "r: 0.08 g: 0.08 b: 0.08 a: 1" || status=1
            disable_wind || status=1
            ;;
        WINDY)
            set_light "0.9" "x: -0.5 y: 0.5 z: -0.8" "r: 0.8 g: 0.82 b: 0.78 a: 1" "r: 0.2 g: 0.22 b: 0.2 a: 1" || status=1
            set_wind "x: 12 y: 4 z: 0" || status=1
            ;;
        LIGHT_RAIN)
            set_light "0.35" "x: -0.35 y: 0.4 z: -0.85" "r: 0.32 g: 0.36 b: 0.42 a: 1" "r: 0.08 g: 0.08 b: 0.1 a: 1" || status=1
            set_wind "x: 5 y: 2 z: 0" || status=1
            ;;
        HEAVY_RAIN)
            set_light "0.22" "x: -0.35 y: 0.4 z: -0.85" "r: 0.22 g: 0.25 b: 0.3 a: 1" "r: 0.04 g: 0.04 b: 0.05 a: 1" || status=1
            set_wind "x: 14 y: 5 z: 0" || status=1
            ;;
    esac

    rain_notice "$preset"
    fog_notice "$preset"

    if [ "$status" -ne 0 ]; then
        echo "[WEATHER][ERROR] Failed to switch to ${preset}"
        echo "Reason: Gazebo runtime service/topic not ready or request failed."
        return 1
    fi

    CURRENT_WEATHER="$preset"
}

print_menu
echo "[WEATHER] Waiting for Gazebo world: ${WORLD}"
until service_exists "$LIGHT_SERVICE" || topic_exists "$WIND_TOPIC"; do
    sleep 1
done
echo "[WEATHER] Runtime controller ready."
echo "[WEATHER] Current preset: ${CURRENT_WEATHER}"

while IFS= read -rsn1 key; do
    case "$key" in
        0) echo "[WEATHER] Current preset: ${CURRENT_WEATHER}" ;;
        1) apply_preset "1" "CLEAR_DAY" ;;
        2) apply_preset "2" "SUNSET" ;;
        3) apply_preset "3" "NIGHT" ;;
        4) apply_preset "4" "CLOUDY" ;;
        5) apply_preset "5" "FOGGY" ;;
        6) apply_preset "6" "WINDY" ;;
        7) apply_preset "7" "LIGHT_RAIN" ;;
        8) apply_preset "8" "HEAVY_RAIN" ;;
        9) apply_preset "9" "CLEAR_DAY" ;;
        q|x) echo "[WEATHER] Exit"; exit 0 ;;
    esac
done
