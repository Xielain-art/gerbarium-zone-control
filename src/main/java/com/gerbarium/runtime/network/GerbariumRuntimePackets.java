package com.gerbarium.runtime.network;

import net.minecraft.util.Identifier;

public class GerbariumRuntimePackets {
    public static final Identifier OPEN_RUNTIME_GUI = new Identifier("gerbarium_regions_runtime", "open_runtime_gui");
    public static final Identifier REQUEST_RUNTIME_SNAPSHOT = new Identifier("gerbarium_regions_runtime", "request_runtime_snapshot");
    public static final Identifier REQUEST_ZONE_DETAILS = new Identifier("gerbarium_regions_runtime", "request_zone_details");
    public static final Identifier REQUEST_RULE_DETAILS = new Identifier("gerbarium_regions_runtime", "request_rule_details");
    public static final Identifier REQUEST_RUNTIME_EVENTS = new Identifier("gerbarium_regions_runtime", "request_runtime_events");
    public static final Identifier SYNC_RUNTIME_SNAPSHOT = new Identifier("gerbarium_regions_runtime", "sync_runtime_snapshot");
    public static final Identifier SYNC_ZONE_DETAILS = new Identifier("gerbarium_regions_runtime", "sync_zone_details");
    public static final Identifier SYNC_RULE_DETAILS = new Identifier("gerbarium_regions_runtime", "sync_rule_details");
    public static final Identifier SYNC_RUNTIME_EVENTS = new Identifier("gerbarium_regions_runtime", "sync_runtime_events");
    public static final Identifier RUN_GLOBAL_ACTION = new Identifier("gerbarium_regions_runtime", "run_global_action");
    public static final Identifier RUN_ZONE_ACTION = new Identifier("gerbarium_regions_runtime", "run_zone_action");
    public static final Identifier RUN_RULE_ACTION = new Identifier("gerbarium_regions_runtime", "run_rule_action");
    public static final Identifier ACTION_RESULT = new Identifier("gerbarium_regions_runtime", "action_result");
}
