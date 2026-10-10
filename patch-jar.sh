#!/bin/bash
# patch-jar.sh - patched/26.3.jar for the native images: the vanilla pack (VanillaPackResourcesBuilder) only accepts
# jar: and file: URLs for its assets/ and data/ roots, and inside a native image they are resource: URLs (GraalVM ships
# that filesystem), so its "jar" becomes "resource". Without it every data-driven registry is empty and worlds don't load.
set -e
cd "$(dirname "$0")"
rm -rf build/patch && mkdir -p build/patch patched && cp game/26.3.jar patched/26.3.jar
(cd build/patch && unzip -q ../../patched/26.3.jar net/minecraft/server/packs/VanillaPackResourcesBuilder.class)
CP=$(find game/libraries -name '*.jar' | tr '\n' ':')game/26.3.jar
~/graalvm-25/x64/bin/java tools/LdcPatch.java build/patch/net/minecraft/server/packs/VanillaPackResourcesBuilder.class jar resource "$CP"
(cd build/patch && zip -q ../../patched/26.3.jar net/minecraft/server/packs/VanillaPackResourcesBuilder.class)
# Mojang signs the jar; a changed class fails verification, so the copy goes unsigned
zip -qd patched/26.3.jar 'META-INF/MOJANGCS.SF' 'META-INF/MOJANGCS.RSA'
