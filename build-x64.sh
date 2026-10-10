#!/bin/bash
# build-x64.sh [native-image flags...] - desktop native image of Minecraft 26.3 (shakes out native-image problems on the PC
# before the Android build). Reflection: every class of the game and Mojang's libraries (config-gen) plus a tracing-agent
# recording of the title screen (config-agent). netty-codec-http's native-image settings are ignored: they initialize
# all of netty at build time, which bakes log4j loggers (netty logs through SLF4J -> log4j here) into the image.
cd "$(dirname "$0")"
. ./classpath.sh
mkdir -p build-x64
exec ~/graalvm-25/x64/bin/native-image -cp "$CP" -H:ConfigurationFileDirectories=$PWD/config-agent,$PWD/config-gen,$PWD/config-extra --no-fallback -J-Xmx24g \
  --exclude-config '.*/netty-codec-http-[^/]*\.jar' '^META-INF/native-image/.*' \
  --initialize-at-run-time=io.netty \
  --enable-native-access=ALL-UNNAMED --enable-url-protocols=http,https -H:+ReportExceptionStackTraces -H:+AddAllCharsets "$@" \
  -o build-x64/mc net.minecraft.client.main.Main
