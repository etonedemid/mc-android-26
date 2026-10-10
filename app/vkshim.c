// vkshim.c - libvkshim.so, loaded by LWJGL as the Vulkan loader (org.lwjgl.vulkan.libname): forwards everything to the
// system's libvulkan.so, except that surface capabilities never report a rotation.
//
// On a phone whose natural orientation is portrait, a landscape window's surface reports currentTransform ROTATE_90/270
// and a portrait currentExtent. The game creates its swapchain with that transform (as Vulkan recommends) but, like any
// desktop renderer, doesn't rotate what it draws: the picture came out sideways. Reported as IDENTITY with the extent in
// the window's orientation, the game renders upright and the compositor does the rotation.
#include <dlfcn.h>
#include <string.h>
#include <vulkan/vulkan.h>

static PFN_vkGetInstanceProcAddr real_gipa;
static PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR real_caps;
static PFN_vkGetPhysicalDeviceSurfaceCapabilities2KHR real_caps2;

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
    VkResult r = real_caps(pd, s, c);
    if (r == VK_SUCCESS) unrotate(c);
    return r;
}
static VKAPI_ATTR VkResult VKAPI_CALL caps2(VkPhysicalDevice pd, const VkPhysicalDeviceSurfaceInfo2KHR *i, VkSurfaceCapabilities2KHR *c) {
    VkResult r = real_caps2(pd, i, c);
    if (r == VK_SUCCESS) unrotate(&c->surfaceCapabilities);
    return r;
}

__attribute__((visibility("default"))) VKAPI_ATTR PFN_vkVoidFunction VKAPI_CALL vkGetInstanceProcAddr(VkInstance inst, const char *name) {
    if (!real_gipa) {
        void *h = dlopen("/system/lib64/libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        real_gipa = h ? (PFN_vkGetInstanceProcAddr)dlsym(h, "vkGetInstanceProcAddr") : NULL;
        if (!real_gipa) return NULL;
    }
    PFN_vkVoidFunction f = real_gipa(inst, name);
    if (!f || !name) return f;
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR")) { real_caps = (PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR)f; return (PFN_vkVoidFunction)caps; }
    if (!strcmp(name, "vkGetPhysicalDeviceSurfaceCapabilities2KHR")) { real_caps2 = (PFN_vkGetPhysicalDeviceSurfaceCapabilities2KHR)f; return (PFN_vkVoidFunction)caps2; }
    if (!strcmp(name, "vkGetInstanceProcAddr")) return (PFN_vkVoidFunction)vkGetInstanceProcAddr;   // keep asking us
    return f;
}
