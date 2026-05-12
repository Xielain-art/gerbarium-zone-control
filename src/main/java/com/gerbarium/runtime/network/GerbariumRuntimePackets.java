package com.gerbarium.runtime.network;

import net.minecraft.util.Identifier;

public class GerbariumRuntimePackets {
    public static final Identifier OPEN_RUNTIME_GUI = new Identifier("gerbarium_regions_runtime", "open_runtime_gui");
    public static final Identifier REQUEST_RUNTIME_SNAPSHOT = new Identifier("gerbarium_regions_runtime", "request_runtime_snapshot");
    public static final Identifier SYNC_RUNTIME_SNAPSHOT = new Identifier("gerbarium_regions_runtime", "sync_runtime_snapshot");
    public static final Identifier RUN_GLOBAL_ACTION = new Identifier("gerbarium_regions_runtime", "run_global_action");
    public static final Identifier ACTION_RESULT = new Identifier("gerbarium_regions_runtime", "action_result");
}