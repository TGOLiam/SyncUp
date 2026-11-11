#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")"

usage() {
    cat <<'HELP'
Usage: ./install-debug.sh [device-serial]

Build and install the debug app on one connected Android device.
Verify the installed APK and restart the app after installation.
Pass a serial or set ANDROID_SERIAL when more than one device is connected.
Run 'adb devices -l' to see available devices.
HELP
}

if [[ ${1:-} == --help || ${1:-} == -h ]]; then
    usage
    exit 0
fi
if (( $# > 1 )); then
    usage >&2
    exit 2
fi

serial=${1:-${ANDROID_SERIAL:-}}

# Android Studio often configures the SDK in local.properties without adding adb to PATH.
if command -v adb >/dev/null 2>&1; then
    adb_cmd=$(command -v adb)
else
    sdk_dir=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}
    if [[ -z $sdk_dir && -f local.properties ]]; then
        sdk_dir=$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' local.properties | head -n 1)
        sdk_dir=${sdk_dir//\\ / }
        sdk_dir=${sdk_dir//\\:/:}
        sdk_dir=${sdk_dir//\\\\/\\}
    fi
    if [[ -z $sdk_dir || ! -x $sdk_dir/platform-tools/adb ]]; then
        echo "Error: adb not found. Install Android SDK platform-tools or add adb to PATH." >&2
        exit 1
    fi
    adb_cmd=$sdk_dir/platform-tools/adb
fi

if ! devices=$("$adb_cmd" devices 2>&1); then
    printf 'Error: could not contact adb.\n%s\n' "$devices" >&2
    exit 1
fi

if [[ -n $serial ]]; then
    if ! awk -v serial="$serial" '$1 == serial && $2 == "device" {found=1} END {exit !found}' <<< "$devices"; then
        printf 'Error: device "%s" is not connected and authorized.\n%s\n' "$serial" "$devices" >&2
        exit 1
    fi
else
    mapfile -t available < <(awk '$2 == "device" {print $1}' <<< "$devices")
    if (( ${#available[@]} != 1 )); then
        printf 'Error: expected one authorized device, found %s.\n%s\n' "${#available[@]}" "$devices" >&2
        echo "Connect and authorize a device, or pass its serial when multiple devices are connected." >&2
        exit 1
    fi
    serial=${available[0]}
fi

printf 'Building debug APK for device %s...\n' "$serial"
./gradlew :app:assembleDebug
apk=app/build/outputs/apk/debug/app-debug.apk
package=com.example.sync_up
if [[ ! -f $apk ]]; then
    echo "Error: build completed but the debug APK was not found at $apk." >&2
    exit 1
fi

printf 'Installing on %s...\n' "$serial"
if ! "$adb_cmd" -s "$serial" install -r "$apk"; then
    echo "Error: installation failed. Check the device's installation prompt and adb output above." >&2
    echo "A signing-key conflict may require removing the existing app, which deletes its data." >&2
    exit 1
fi

installed_path=$("$adb_cmd" -s "$serial" shell pm path "$package" | tr -d '\r' | sed -n 's/^package://p' | head -n 1)
if [[ -z $installed_path ]]; then
    echo "Error: installation reported success, but the installed APK could not be found." >&2
    exit 1
fi
local_hash=$(sha256sum "$apk" | awk '{print $1}')
if ! remote_hash=$("$adb_cmd" -s "$serial" shell sha256sum "$installed_path"); then
    echo "Error: could not verify the installed APK on the device." >&2
    exit 1
fi
remote_hash=${remote_hash%% *}
if [[ $local_hash != "$remote_hash" ]]; then
    echo "Error: the installed APK does not match the APK built by Gradle." >&2
    exit 1
fi
echo "Installed APK verified: $local_hash"

printf 'Restarting SyncUp...\n'
if ! "$adb_cmd" -s "$serial" shell am start -S -n "$package/.MainActivity"; then
    echo "Error: the APK was installed, but the app could not be started." >&2
    exit 1
fi
echo "Debug app installed, verified, and started successfully."
