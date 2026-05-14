package com.gerbarium.runtime.api;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.admin.RuntimeAdminService;
import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

public final class RuntimeReloadApi {
    private RuntimeReloadApi() {
    }

    public static void reload() {
        reloadLocal();
        triggerResourcesReloadLocal();
    }

    public static void reloadLocal() {
        try {
            RuntimeAdminService.reload("bridge");
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.warn("Runtime reload API called before runtime fully ready or reload failed: {}", e.getMessage());
        }
    }

    private static void triggerResourcesReloadLocal() {
        final String resourcesModId = "gerbarium_regions_resources_runtime";
        final String resourcesApiClass = "com.gerbarium.resources.api.ResourcesReloadApi";
        if (!FabricLoader.getInstance().isModLoaded(resourcesModId)) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName(resourcesApiClass);
            Method reloadMethod = apiClass.getMethod("reloadLocal");
            reloadMethod.invoke(null);
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.warn("Failed to trigger resources runtime local reload: {}", e.getMessage());
        }
    }
}
