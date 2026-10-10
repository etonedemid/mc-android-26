// vkshim.c - libvkshim.so, loaded by LWJGL as the Vulkan loader (org.lwjgl.vulkan.libname): forwards everything to the
// system's libvulkan.so, except that surface capabilities never report a rotation.
//
// On a phone whose natural orientation is portrait, a landscape window's surface reports currentTransform ROTATE_90/270
// and a portrait currentExtent. The game creates its swapchain with that transform (as Vulkan recommends) but, like any
// desktop renderer, doesn't rotate what it draws: the picture came out sideways. Reported as IDENTITY with the extent in
// the window's orientation, the game renders upright and the compositor does the rotation.
//
// The game makes its Vulkan surface once, as on a desktop, but Android destroys the window under it whenever the app is in
// the background (it came back to a black screen, VK_ERROR_SURFACE_LOST_KHR forever). So the game's surface handle
// stands for whatever window SDL has now: once SDL has a new one, a new surface is made on it and every surface call
// goes there instead (a swapchain then starts afresh rather than from the old surface's).
//
// It also holds the game while the app is in the background (vkshim_pause, from glue.c): presenting a frame waits until
// it's back, which stops the render thread, and with it the game, where it would draw next.
#include <dlfcn.h>
#include <pthread.h>
#include <string.h>
#define VK_USE_PLATFORM_ANDROID_KHR
#include <vulkan/vulkan.h>

static PFN_vkGetInstanceProcAddr real_gipa;
static PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR real_caps;
static VkInstance instance;

// --- the game's surface, made anew on SDL's current window ---
static VkSurfaceKHR game_surface, cur_surface;   // the handle the game has; the surface now behind it
static void *cur_window;                          // the ANativeWindow cur_surface was made on
static int surface_replaced;                      // cur_surface is newer than the game's last swapchain
static void *sdl_window(void) {                   // SDL's current ANativeWindow, NULL while there's none
    static void **(*get_windows)(int *); static unsigned (*props)(void *); static void *(*get_ptr)(unsigned, const char *, void *);
    static void (*sdl_free)(void *);
    if (!get_windows) {
        void *h = dlopen("libSDL3.so", RTLD_NOW | RTLD_NOLOAD);
        if (!h) return NULL;
        props = (unsigned (*)(void *))dlsym(h, "SDL_GetWindowProperties");
        get_ptr = (void *(*)(unsigned, const char *, void *))dlsym(h, "SDL_GetPointerProperty");
        sdl_free = (void (*)(void *))dlsym(h, "SDL_free");
        if (!props || !get_ptr || !sdl_free) return NULL;
        get_windows = (void **(*)(int *))dlsym(h, "SDL_GetWindows");
        if (!get_windows) return NULL;
    }
    int n = 0; void **w = get_windows(&n), *win = NULL;
    if (w && n > 0) win = get_ptr(props(w[0]), "SDL.window.android.window", NULL);
    sdl_free(w);
    return win;
}
static VkSurfaceKHR surface(VkSurfaceKHR s) {
    if (s == VK_NULL_HANDLE) return s;
    if (!game_surface) { game_surface = cur_surface = s; cur_window = sdl_window(); }
    if (s != game_surface) return s;
    void *w = sdl_window();
    if (w && w != cur_window && instance) {   // a new window: a new surface on it (the old one leaks with its window)
        PFN_vkCreateAndroidSurfaceKHR create = (PFN_vkCreateAndroidSurfaceKHR)real_gipa(instance, "vkCreateAndroidSurfaceKHR");
        VkAndroidSurfaceCreateInfoKHR ci = { VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR, NULL, 0, w };
        VkSurfaceKHR ns;
        if (create && create(instance, &ci, NULL, &ns) == VK_SUCCESS) { cur_surface = ns; cur_window = w; surface_replaced = 1; }
    }
    return cur_surface;
}
static PFN_vkGetPhysicalDeviceSurfaceSupportKHR real_support;
static PFN_vkGetPhysicalDeviceSurfaceFormatsKHR real_formats;
static PFN_vkGetPhysicalDeviceSurfaceFormats2KHR real_formats2;
static PFN_vkGetPhysicalDeviceSurfacePresentModesKHR real_modes;
static PFN_vkDestroySurfaceKHR real_destroy_surface;
static PFN_vkCreateSwapchainKHR real_create_swapchain;
static VKAPI_ATTR VkResult VKAPI_CALL support(VkPhysicalDevice pd, uint32_t q, VkSurfaceKHR s, VkBool32 *out) { return real_support(pd, q, surface(s), out); }
static VKAPI_ATTR VkResult VKAPI_CALL formats(VkPhysicalDevice pd, VkSurfaceKHR s, uint32_t *n, VkSurfaceFormatKHR *f) { return real_formats(pd, surface(s), n, f); }
static VKAPI_ATTR VkResult VKAPI_CALL formats2(VkPhysicalDevice pd, const VkPhysicalDeviceSurfaceInfo2KHR *i, uint32_t *n, VkSurfaceFormat2KHR *f) {
    VkPhysicalDeviceSurfaceInfo2KHR c = *i; c.surface = surface(i->surface);
    return real_formats2(pd, &c, n, f);
}
static VKAPI_ATTR VkResult VKAPI_CALL modes(VkPhysicalDevice pd, VkSurfaceKHR s, uint32_t *n, VkPresentModeKHR *m) { return real_modes(pd, surface(s), n, m); }
static VKAPI_ATTR void VKAPI_CALL destroy_surface(VkInstance inst, VkSurfaceKHR s, const VkAllocationCallbacks *a) {
    if (s && s == game_surface) { s = cur_surface; game_surface = cur_surface = VK_NULL_HANDLE; cur_window = NULL; }
    real_destroy_surface(inst, s, a);
}
static VKAPI_ATTR VkResult VKAPI_CALL create_swapchain(VkDevice d, const VkSwapchainCreateInfoKHR *i, const VkAllocationCallbacks *a, VkSwapchainKHR *out) {
    VkSwapchainCreateInfoKHR c = *i;
    c.surface = surface(i->surface);
    if (c.surface != i->surface && surface_replaced) c.oldSwapchain = VK_NULL_HANDLE;   // the old one is on the lost surface
    VkResult r = real_create_swapchain(d, &c, a, out);
    if (r == VK_SUCCESS) surface_replaced = 0;
    return r;
}
static PFN_vkGetPhysicalDeviceSurfaceCapabilities2KHR real_caps2;
static PFN_vkQueuePresentKHR real_present;
static PFN_vkGetDeviceProcAddr real_gdpa;

static pthread_mutex_t pause_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t pause_cond = PTHREAD_COND_INITIALIZER;
static int paused;
__attribute__((visibility("default"))) void vkshim_pause(int on) {
    pthread_mutex_lock(&pause_lock);
    paused = on;
    pthread_cond_broadcast(&pause_cond);
    pthread_mutex_unlock(&pause_lock);
}
static VKAPI_ATTR VkResult VKAPI_CALL present(VkQueue q, const VkPresentInfoKHR *i) {
    pthread_mutex_lock(&pause_lock);
    while (paused) pthread_cond_wait(&pause_cond, &pause_lock);
    pthread_mutex_unlock(&pause_lock);
    return real_present(q, i);
}
static PFN_vkVoidFunction hook_device(const char *name, PFN_vkVoidFunction f) {
    if (!f) return f;
    if (!strcmp(name, "vkQueuePresentKHR")) { real_present = (PFN_vkQueuePresentKHR)f; return (PFN_vkVoidFunction)present; }
    if (!strcmp(name, "vkCreateSwapchainKHR")) { real_create_swapchain = (PFN_vkCreateSwapchainKHR)f; return (PFN_vkVoidFunction)create_swapchain; }
    return f;
}
static VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL gdpa(VkDevice d, const char *name) {
    return name ? hook_device(name, real_gdpa(d, name)) : NULL;
}

static void unrotate(VkSurfaceCapabilitiesKHR *c) {
    if (c->currentTransform & (VK_SURFACE_TRANSFORM_ROTATE_90_BIT_KHR | VK_SURFACE_TRANSFORM_ROTATE_270_BIT_KHR)) {
        uint32_t t = c->currentExtent.width; c->currentExtent.width = c->currentExtent.height; c->currentExtent.height = t;
        t = c->minImageExtent.width; c->minImageExtent.width = c->minImageExtent.height; c->minImageExtent.height = t;
        t = c->maxImageExtent.width; c->maxImageExtent.width = c->maxImageExtent.height; c->maxImageExtent.height = t;
    }
    c->currentTransform = VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
    c->supportedTransforms |= VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR;
}
static VKAPI_ATTR VkResult VKAPI_CALL caps(VkPhysicalDevice pd, VkSurfaceKHR s, VkSurfaceCapabilitiesKHR *c) {
    VkResult r = real_caps(pd, surface(s), c);
    if (r == VK_SUCCESS) unrotate(c);
    return r;
}
static VKAPI_ATTR VkResult VKAPI_CALL caps2(VkPhysicalDevice pd, const VkPhysicalDeviceSurfaceInfo2KHR *i, VkSurfaceCapabilities2KHR *c) {
    VkPhysicalDeviceSurfaceInfo2KHR si = *i; si.surface = surface(i->surface);
    VkResult r = real_caps2(pd, &si, c);
    if (r == VK_SUCCESS) unrotate(&c->surfaceCapabilities);
    return r;
}

__attribute__((visibility("default"))) VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance inst, const char *name) {
    if (!real_gipa) {
        void *h = dlopen("/system/lib64/libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        real_gipa = h ? (PFN_vkGetInstanceProcAddr)dlsym(h, "vkGetInstanceProcAddr") : NULL;
        if (!real_gipa) return NULL;
    }
    if (inst) instance = inst;
    PFN_vkVoidFunction f = real_gipa(inst, name);
    if (!f || !name) return f;
#define HOOK(fn, var, mine) if (!strcmp(name, #fn)) { var = (PFN_##fn)f; return (PFN_vkVoidFunction)mine; }
    HOOK(vkGetPhysicalDeviceSurfaceSupportKHR, real_support, support)
    HOOK(vkGetPhysicalDeviceSurfaceFormatsKHR, real_formats, formats)
    HOOK(vkGetPhysicalDeviceSurfaceFormats2KHR, real_formats2, formats2)
    HOOK(vkGetPhysicalDeviceSurfacePresentModesKHR, real_modes, modes)
    HOOK(vkDestroySurfaceKHR, real_destroy_surface, destroy_surface)
#undef HOOK
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR")) { real_caps = (PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR)f; return (PFN_vkVoidFunction)caps; }
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceCapabilities2KHR")) { real_caps2 = (PFN_vkGetPhysicalDeviceSurfaceCapabilities2KHR)f; return (PFN_vkVoidFunction)caps2; }
    if (!strcmp(name, "vkGetInstanceProcAddr")) return (PFN_vkVoidFunction)vkGetInstanceProcAddr;   // keep asking us
    if (!strcmp(name, "vkGetDeviceProcAddr")) { real_gdpa = (PFN_vkGetDeviceProcAddr)f; return (PFN_vkVoidFunction)gdpa; }
    return hook_device(name, f);
}
