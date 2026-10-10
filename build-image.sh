#!/bin/bash
# build-image.sh [flags...] - Minecraft 26.3 as an android-aarch64 native image: relocatable object build/mc.o for
# link-apk.sh. Same classpath and configuration as build-x64.sh. The CAP cache (bionic struct layouts and constants) is
# measured on the phone through ndk-cap-cc; CAP=use skips that once cap/ covers the classpath.
cd "$(dirname "$0")"
. ./classpath.sh
CP=$(echo "$CP" | tr ':' '\n' | sed "s|^|$PWD/|" | paste -sd:)   # absolute: the build runs in build/
if [ "$CAP" = use ]; then CAPF="-H:+UseCAPCache"; else CAPF="-H:-UseCAPCache -H:+NewCAPCache"; mkdir -p cap; fi
mkdir -p build && cd build && rm -rf tmp mc.o
~/graalvm-25/x64/bin/native-image -cp "$CP" \
  -H:ConfigurationFileDirectories=$OLDPWD/config-agent,$OLDPWD/config-gen,$OLDPWD/config-extra --no-fallback -J-Xmx24g \
  --exclude-config '.*/netty-codec-http-[^/]*\.jar' '^META-INF/native-image/.*' --initialize-at-run-time=io.netty \
  --enable-native-access=ALL-UNNAMED --enable-url-protocols=http,https -H:+ReportExceptionStackTraces -H:+AddAllCharsets \
  "$@" --target=android-aarch64 -march=compatibility -H:+UnlockExperimentalVMOptions -H:-ForeignAPISupport \
  $CAPF -H:CAPCacheDir=$OLDPWD/cap -H:CCompilerPath=$OLDPWD/ndk-cap-cc \
  -H:+ExitAfterRelocatableImageWrite -H:TempDirectory=$PWD/tmp -o mc net.minecraft.client.main.Main
cp tmp/SVM-*/mc.o mc.o && ls -la mc.o
