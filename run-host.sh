#!/bin/bash
# run-host.sh [java args...] - Minecraft 26.3 + Fabric + mods on the desktop JVM, off-screen (xvfb, software GL),
# offline. The classpath the native image will be built from: Fabric's libraries, Mojang's, the client jar.
cd "$(dirname "$0")"
CP=$(find game/fabric -name '*.jar' | sort | tr '\n' ':')$(find game/libraries -name '*.jar' | sort | tr '\n' ':')game/26.3.jar
mkdir -p run && ln -sfn ../game/mods run/mods
exec xvfb-run -a -s "-screen 0 1280x720x24" ~/graalvm-25/x64/bin/java -Xmx3g "$@" -cp "$CP" net.fabricmc.loader.impl.launch.knot.KnotClient \
  --username etonedemid --version 26.3 --gameDir run --assetsDir game/assets --assetIndex 34 \
  --uuid 00000000000000000000000000000000 --accessToken 0 --userType legacy --versionType release --width 1280 --height 720
