#!/bin/bash
# build-natives.sh - LWJGL 3.4.3 JNI libraries for Android with the NDK, one per Linux name as LWJGL loads them:
# liblwjgl.so (+ libffi), liblwjgl_opengl.so, liblwjgl_stb.so, liblwjgl_vma.so (C++, Vulkan Memory Allocator).
# The other LWJGL modules bind their library at run time (SDL3, OpenAL, Vulkan, FreeType, shaderc, SPIRV-Cross).
set -e
cd "$(dirname "$0")"
L=$PWD/deps/lwjgl3-3.4.3/modules/lwjgl; O=$PWD/natives; G=~/graalvm-25/x64; FFI=$PWD/deps/libffi-lwjgl/and   # LWJGL-CI fork: LWJGL 3.4 uses its ffi_call_plan API
T=~/Android/Sdk/ndk/26.3.11579264/toolchains/llvm/prebuilt/linux-x86_64/bin
CC=$T/aarch64-linux-android31-clang; CXX=$T/aarch64-linux-android31-clang++
FLAGS="-O2 -fPIC -DNDEBUG -DLWJGL_LINUX -DLWJGL_arm64 -D_GNU_SOURCE -I$G/include -I$G/include/linux \
  -I$L/core/src/main/c -I$L/core/src/main/c/linux -w"
mkdir -p $O && rm -rf $O/tmp && mkdir -p $O/tmp
build() {  # build <lib> <compiler> <extra flags> <files...>
  local lib=$1 cc=$2 fl=$3; shift 3; local objs=()
  mkdir -p $O/tmp/$lib
  for f in "$@"; do o=$O/tmp/$lib/$(basename "$f").o; $cc -c $FLAGS $fl "$f" -o $o & objs+=($o); done
  wait
  $cc -shared -o $O/$lib.so "${objs[@]}" $EXTRA -ldl -lm -llog
  echo "$lib.so: ${#objs[@]} files"
}
EXTRA="$FFI/.libs/libffi.a" build liblwjgl $CC "-std=gnu11 -I$L/core/src/main/c/libffi -I$L/core/src/main/c/libffi/aarch64 -I$FFI/include" \
  $L/core/src/main/c/*.c $(ls $L/core/src/generated/c/*.c) $(ls $L/core/src/generated/c/linux/*.c | grep -v liburing)
EXTRA= build liblwjgl_opengl $CC "-std=gnu11 -I$L/opengl/src/main/c" $(ls $L/opengl/src/generated/c/*.c | grep -vE 'GLX|WGL')
EXTRA= build liblwjgl_stb $CC "-std=gnu11 -include string.h -I$L/stb/src/main/c -isystem$L/stb/src/main/c" $L/stb/src/generated/c/*.c
EXTRA=-static-libstdc++ build liblwjgl_vma $CXX "-std=c++17 -I$L/vma/src/main/c -I$L/vulkan/src/main/c" $L/vma/src/generated/c/*.cpp
