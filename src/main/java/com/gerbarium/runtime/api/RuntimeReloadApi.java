package com.gerbarium.runtime.api;

import com.gerbarium.runtime.admin.RuntimeAdminService;

public final class RuntimeReloadApi {
    private RuntimeReloadApi() {
    }

    public static void reload() {
        RuntimeAdminService.reload("bridge");
    }
}
