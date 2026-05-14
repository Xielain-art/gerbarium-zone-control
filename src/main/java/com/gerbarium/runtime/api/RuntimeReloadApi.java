package com.gerbarium.runtime.api;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.admin.RuntimeAdminService;

public final class RuntimeReloadApi {
    private RuntimeReloadApi() {
    }

    public static void reload() {
        try {
            RuntimeAdminService.reload("bridge");
        } catch (Exception e) {
            GerbariumRegionsRuntime.LOGGER.warn("Runtime reload API called before runtime fully ready or reload failed: {}", e.getMessage());
        }
    }
}
