#!/usr/bin/env bash
# Runs the real-client game tests (src/gametest/.../gametest/client) on a headless machine.
# Needs Xvfb and Mesa's EGL (Debian/Ubuntu: xvfb libegl1 libgl1-mesa-dri) and JDK 25 on PATH.
# Screenshots land in build/run/clientGameTest/screenshots/.
set -euo pipefail
cd "$(dirname "$0")/.."
export SDL_VIDEO_FORCE_EGL=1
export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-$(mktemp -d)}"
chmod 700 "$XDG_RUNTIME_DIR"
rm -rf build/run/clientGameTest/screenshots
exec xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClientGameTest --console=plain "$@"
