#!/usr/bin/env bash
# Runtime weather hotkeys for Gazebo Sim. This does not restart PX4/Gazebo.
set +e

SIM_WORLD="${SIM_WORLD:-legacy}"
if [ "$SIM_WORLD" = "compact" ]; then
    WORLD="forest_monitoring_compact"
else
    WORLD="${GZ_WORLD_NAME:-forest_monitoring}"
fi
LIGHT_SERVICE="/world/${WORLD}/light_config"
WIND_TOPIC="/world/${WORLD}/wind"
CURRENT_WEATHER="CLEAR_DAY"

print_menu() {
    echo '========================================'
    echo ' Weather Controls'
    echo '========================================'
    echo 'u / clear  = Clear Day'
    echo 'y / sunset = Sunset'
    echo 'i / night  = Night'
    echo 'g / cloudy = Cloudy'
    echo 'j / foggy  = Foggy'
    echo 'm / windy  = Windy'
    echo 'b / light  = Light Rain'
    echo 'z / heavy  = Heavy Rain'
    echo '0 = Status'
    echo 'p = Print menu'
    echo '9 = Exit'
    echo '========================================'
    echo 'Press one safe key to switch immediately.'
    echo 'Avoided Flight Control keys: w a s d f q e t c r l x 1 2 3 4 5.'
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
    return 0
}

fog_notice() {
    return 0
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
        echo "[WEATHER] Applied ${preset} (lighting only)"
        CURRENT_WEATHER="$preset"
        return 1
    fi

    CURRENT_WEATHER="$preset"
    echo "[WEATHER] Applied ${preset}"
}

print_menu
echo "[WEATHER] Waiting for Gazebo world: ${WORLD}"
until service_exists "$LIGHT_SERVICE" || topic_exists "$WIND_TOPIC"; do
    sleep 1
done
echo "[WEATHER] Runtime controller ready."
echo "[WEATHER] Current preset: ${CURRENT_WEATHER}"

while IFS= read -rsn1 key; do
    key="$(printf '%s' "$key" | tr '[:upper:]' '[:lower:]')"
    case "$key" in
        "") ;;
        0) echo "[WEATHER] Current preset: ${CURRENT_WEATHER}" ;;
        u) apply_preset "$key" "CLEAR_DAY" ;;
        y) apply_preset "$key" "SUNSET" ;;
        i) apply_preset "$key" "NIGHT" ;;
        g) apply_preset "$key" "CLOUDY" ;;
        j) apply_preset "$key" "FOGGY" ;;
        m) apply_preset "$key" "WINDY" ;;
        b) apply_preset "$key" "LIGHT_RAIN" ;;
        z) apply_preset "$key" "HEAVY_RAIN" ;;
        p) print_menu ;;
        9) echo "[WEATHER] Exit"; exit 0 ;;
        menu|help|\?) print_menu ;;
        *) echo "[WEATHER] Unknown command: ${key}. Type help to show menu." ;;
    esac
done
