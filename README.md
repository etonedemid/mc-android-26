# mc-android-26

Minecraft Java 26.3 (vanilla) compiled ahead of time to a native Android app with GraalVM native-image. No JVM on the phone. It renders with the game's own Vulkan backend on the phone's Vulkan driver. Windowing and input come from SDL3, through LWJGL 3.4.3 over JNI.

It installs alongside the 1.12.2 build (mc-android) as a separate app, com.etonedemid.mc26.

Private, for personal use. This repo has no Minecraft code or assets, apart from the font tables in McFont.java (generated from the game's own font). The APK it builds does contain the compiled game and the resources from its jar, so share an APK only with people who own Minecraft. Sounds, music and the other languages come from Mojang's asset server and are copied to the phone separately.

Features:

- on-screen controls, with a layout you can move around and save
- a Minecraft-styled on-screen keyboard
- Microsoft account sign-in, or offline play
- render resolution setting, as a percentage of the screen
- pauses frames and sound in the background
- the display surface is recreated after the app comes back from the background
- either landscape orientation

Requirements: an arm64 phone with Vulkan, GraalVM 25 CE, Android NDK 26, adb, Python 3.

Build, in order:

    ./fetch.py 26.3         client jar, libraries and assets into game/
    ./build-natives.sh      LWJGL natives for Android (libffi from LWJGL-CI)
    ./patch-jar.sh          patched/26.3.jar for native-image
    ./build-image.sh        native image (build/mc.o)
    ./link-apk.sh           libmc.so, libvkshim.so and out/mc26.apk
    adb install -r out/mc26.apk
    adb push game/assets /sdcard/Android/data/com.etonedemid.mc26/files/

SDL3 3.4.14, FreeType, shaderc, SPIRV-Cross and OpenAL Soft were built by hand with the NDK into natives/. No script covers them yet. The sources are in deps/ (git-ignored). link-apk.sh signs the APK with the debug key from ~/mc-android.

build-x64.sh and run-x64.sh make and run a desktop native image, off-screen under xvfb. Use them to find native-image problems on the PC before building for Android. run-host.sh runs the game on a normal JVM.

Microsoft sign-in needs an Azure client ID in a file named msa-client-id (git-ignored). Without it, sign-in is off and you play offline.

Layout:

    app/glue.c       entry point: starts the game, account, background pause
    app/vkshim.c     Vulkan layer: unrotated surfaces, surface recreation, pause on present
    app/java/        activity, controls and keyboard (Controls.java), SDL's Java side (patched)
    config-*/        native-image reachability metadata
    tools/           class patchers, config generator
