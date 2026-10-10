# classpath.sh - sourced: CP, the game's classpath for the JVM runs and the native images, with two patched jars:
# - LWJGL core without its Java 25/27 classes (patched/lwjgl-3.4.3-jni.jar): those route every native call through FFM
#   downcalls, which the Android native image can't do; the Java 8 classes use LWJGL's own JNI library instead.
# - joml without its package-info classes (patched/joml-1.10.9.jar): class-file version 46, too old for the name
#   package-info, which native-image loads while registering packages (a JVM never does).
# - the game jar: patched/26.3.jar (patch-jar.sh) for the native images; JVM runs set GAME_JAR=game/26.3.jar.
CP=$(find game/libraries -name '*.jar' ! -name 'lwjgl-3.4.3.jar' ! -name 'joml-1.10.9.jar' | sort | tr '\n' ':')patched/lwjgl-3.4.3-jni.jar:patched/joml-1.10.9.jar:${GAME_JAR:-patched/26.3.jar}
