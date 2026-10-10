#!/bin/bash
# run-x64.sh [game args...] - the desktop native image (build-x64.sh), off-screen like run-host.sh, same game dir.
cd "$(dirname "$0")"
# SDL3 would pick Wayland from the session and show the window on the desktop: force X11, i.e. xvfb
export SDL_VIDEO_DRIVER=x11; unset WAYLAND_DISPLAY
exec xvfb-run -a -s "-screen 0 1280x720x24" build-x64/mc -Xmx3g -Dorg.lwjgl.librarypath=$PWD/build-x64/natives \
  --username etonedemid --version 26.3 --gameDir run --assetsDir game/assets --assetIndex 34 \
  --uuid 00000000000000000000000000000000 --accessToken 0 --userType legacy --versionType release --width 1280 --height 720 "$@"
