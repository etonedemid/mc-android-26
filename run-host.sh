#!/bin/bash
# run-host.sh [game args...] - plain Minecraft 26.3 on the desktop JVM, off-screen (xvfb), offline. The classpath the
# native image is built from: Mojang's libraries and the client jar. Options in run/options.txt (Vulkan preferred).
# JVM options in $JAVA_OPTS.
cd "$(dirname "$0")"
GAME_JAR=game/26.3.jar . ./classpath.sh
mkdir -p run
# SDL3 would pick Wayland from the session and show the window on the desktop: force X11, i.e. xvfb
export SDL_VIDEO_DRIVER=x11; unset WAYLAND_DISPLAY
exec xvfb-run -a -s "-screen 0 1280x720x24" ~/graalvm-25/x64/bin/java -Xmx3g $JAVA_OPTS -cp "$CP" net.minecraft.client.main.Main \
  --username etonedemid --version 26.3 --gameDir run --assetsDir game/assets --assetIndex 34 \
  --uuid 00000000000000000000000000000000 --accessToken 0 --userType legacy --versionType release --width 1280 --height 720 "$@"
