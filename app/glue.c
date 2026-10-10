// glue.c - Android entry of Minecraft 26.3 as a native image. SDL3's activity (McActivity, an SDLActivity) loads
// libSDL3.so and this library, then runs SDL_main on its own thread; SDL_main starts the game. The game drives SDL3
// itself through LWJGL (window, input, Vulkan surface), bound to the same libSDL3.so the activity loaded.
#include <SDL3/SDL.h>
#include <android/log.h>
#include <dlfcn.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <unistd.h>

#define LOG(...) __android_log_print(ANDROID_LOG_INFO, "mc26", __VA_ARGS__)

int mc_main(int argc, char **argv);   // the native image's main (renamed from main in link-apk.sh)

// stdout/stderr -> logcat, line by line
static void *log_pump(void *fdp) {
    int fd = (int)(long)fdp; char buf[2048]; size_t len = 0;
    for (;;) {
        ssize_t n = read(fd, buf + len, sizeof buf - 1 - len);
        if (n <= 0) return NULL;
        len += n;
        char *s = buf, *nl;
        while ((nl = memchr(s, '\n', buf + len - s))) { *nl = 0; __android_log_write(ANDROID_LOG_INFO, "mc26-java", s); s = nl + 1; }
        len -= s - buf; memmove(buf, s, len);
        if (len == sizeof buf - 1) { buf[len] = 0; __android_log_write(ANDROID_LOG_INFO, "mc26-java", buf); len = 0; }
    }
}
static void redirect_stdio(void) {
    int p[2]; pipe(p);
    dup2(p[1], 1); dup2(p[1], 2);
    setvbuf(stdout, NULL, _IOLBF, 0); setvbuf(stderr, NULL, _IONBF, 0);
    pthread_t t; pthread_create(&t, NULL, log_pump, (void *)(long)p[0]); pthread_detach(t);
}

__attribute__((visibility("default"))) int SDL_main(int argc_in, char **argv_in) {
    (void)argc_in; (void)argv_in;
    redirect_stdio();
    SDL_SetHint(SDL_HINT_ORIENTATIONS, "LandscapeLeft LandscapeRight");   // else SDL follows the window's shape
    static char data[512], tmp[600], libs[512], gamedir[600], assets[600];
    snprintf(data, sizeof data, "%s", SDL_GetAndroidExternalStoragePath());
    snprintf(tmp, sizeof tmp, "%s/tmp", SDL_GetAndroidInternalStoragePath());
    Dl_info di; dladdr((void *)SDL_main, &di);
    snprintf(libs, sizeof libs, "%s", di.dli_fname); *strrchr(libs, '/') = 0;
    snprintf(gamedir, sizeof gamedir, "%s/minecraft", data);
    snprintf(assets, sizeof assets, "%s/assets", data);
    mkdir(gamedir, 0777); mkdir(tmp, 0777);
    chdir(gamedir);

    static char a[24][700];
    int n = 0;
#define PROP(...) snprintf(a[n++], sizeof a[0], __VA_ARGS__)
    PROP("-Dorg.lwjgl.librarypath=%s", libs);
    PROP("-Dorg.lwjgl.sdl.libname=%s/libSDL3.so", libs);
    PROP("-Dorg.lwjgl.openal.libname=%s/libopenal.so", libs);
    PROP("-Dorg.lwjgl.freetype.libname=%s/libfreetype.so", libs);
    PROP("-Dorg.lwjgl.shaderc.libname=%s/libshaderc.so", libs);
    PROP("-Dorg.lwjgl.spvc.libname=%s/libspirv-cross.so", libs);
    PROP("-Dorg.lwjgl.vulkan.libname=%s/libvkshim.so", libs);   // the system's Vulkan loader, through vkshim.c (rotation)
    PROP("-Dorg.lwjgl.system.allocator=system");
    // the game loads OpenGL at startup even with the Vulkan backend: through Android's EGL, as there is no GLX
    PROP("-Dorg.lwjgl.opengl.contextAPI=EGL");
    PROP("-Dorg.lwjgl.egl.libname=/system/lib64/libEGL.so");   // malloc: LWJGL would pick jemalloc, which isn't bundled
    PROP("-Djava.io.tmpdir=%s", tmp);
    PROP("-Duser.home=%s", data);
    PROP("-Duser.dir=%s", gamedir);
#undef PROP
    static char *argv[64];
    int argc = 0;
    argv[argc++] = "mc";
    argv[argc++] = "-Xmx2g";
    argv[argc++] = "-Djava.awt.headless=true";
    for (int i = 0; i < n; i++) argv[argc++] = a[i];
    char *game[] = { "--username", "etonedemid", "--version", "26.3", "--gameDir", gamedir, "--assetsDir", assets,
                     "--assetIndex", "34", "--uuid", "00000000000000000000000000000000", "--accessToken", "0",
                     "--userType", "legacy", "--versionType", "release" };
    for (size_t i = 0; i < sizeof game / sizeof *game; i++) argv[argc++] = game[i];
    argv[argc] = NULL;
    LOG("starting Minecraft 26.3: data %s, libraries %s", data, libs);
    int rc = mc_main(argc, argv);
    LOG("game main returned %d", rc);
    exit(rc);
}
