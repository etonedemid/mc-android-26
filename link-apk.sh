#!/bin/bash
# link-apk.sh - libmc.so (native image + glue + JDK static libraries) and the APK around it: out/mc26.apk, app ID
# com.etonedemid.mc26 (installs next to the 1.12.2 build), signed with ~/mc-android's debug key. Inputs: build/mc.o
# (build-image.sh), natives/ (build-natives.sh and the SDL3/FreeType/shaderc/SPIRV-Cross/OpenAL builds).
set -e
cd "$(dirname "$0")"
SDK=~/Android/Sdk; NDK=$SDK/ndk/26.3.11579264/toolchains/llvm/prebuilt/linux-x86_64/bin; BT=$SDK/build-tools/34.0.0
CC=$NDK/aarch64-linux-android31-clang; J=$SDK/platforms/android-34/android.jar
S=~/graalvm-25/x64/lib/static/linux-aarch64/glibc; C=~/graalvm-25/x64/lib/svm/clibraries/linux-aarch64/glibc
mkdir -p out/apk/lib/arm64-v8a
$NDK/llvm-objcopy --redefine-sym main=mc_main build/mc.o out/mc.o
JDK=""; for l in java nio net zip prefs management_ext awt awt_headless mlib_image javajpeg lcms fdlibm extnet; do [ -f $S/lib$l.a ] && JDK="$JDK $S/lib$l.a"; done
$CC -shared -fPIC -O2 -o out/apk/lib/arm64-v8a/libmc.so out/mc.o app/glue.c app/bionic_compat.c -Ideps/SDL3-3.4.14/include \
  -Wl,--whole-archive $JDK -Wl,--no-whole-archive $C/liblibchelper.a $C/libjvm.a $C/libsvm_container.a \
  -Wl,--wrap=dlopen,--wrap=getaddrinfo,--wrap=freeaddrinfo -Wl,--no-undefined -Lnatives -lSDL3 -lz -lm -ldl -llog -landroid \
  > out/link.log 2>&1 || { grep -vE '^>>>' out/link.log | sed 's/.*error: //' | sort -u | head -40; exit 1; }
cp natives/*.so out/apk/lib/arm64-v8a/
# Vulkan loader shim: surfaces report no rotation, so the game renders upright in landscape (app/vkshim.c)
$CC -shared -fPIC -O2 -o out/apk/lib/arm64-v8a/libvkshim.so app/vkshim.c -ldl
$NDK/llvm-strip --strip-debug out/apk/lib/arm64-v8a/*.so
# Java: SDL's activity layer and ours. MSA_CLIENT_ID (or the git-ignored file msa-client-id): the Microsoft application
# ID for sign-in; without one the dialog only offers offline play.
[ -z "$MSA_CLIENT_ID" ] && [ -f msa-client-id ] && MSA_CLIENT_ID=$(cat msa-client-id)
rm -rf out/classes out/dex out/gen && mkdir -p out/classes out/dex out/gen/com/etonedemid/mc26
printf 'package com.etonedemid.mc26;\nfinal class Config { static final String MSA_CLIENT_ID = "%s"; }\n' "${MSA_CLIENT_ID:-}" > out/gen/com/etonedemid/mc26/Config.java
javac -source 8 -target 8 -Xlint:none -nowarn -cp $J -d out/classes $(find app/java out/gen -name '*.java')
$BT/d8 --release --min-api 31 --lib $J --output out/dex $(find out/classes -name '*.class')
rm -rf out/res && mkdir -p out/res && $BT/aapt2 compile --dir app/res -o out/res/
rm -f out/mc26.unaligned.apk out/mc26.apk
$BT/aapt2 link -o out/mc26.unaligned.apk --manifest app/AndroidManifest.xml -I $J out/res/*.flat
(cd out/apk && zip -qr -0 ../mc26.unaligned.apk lib)   # stored, so the libraries can be mapped straight from the APK
(cd out/dex && zip -q ../mc26.unaligned.apk classes.dex)
$BT/zipalign -p -f 4 out/mc26.unaligned.apk out/mc26.apk
$BT/apksigner sign --ks ~/mc-android/debug.keystore --ks-pass pass:android out/mc26.apk
ls -la out/mc26.apk
