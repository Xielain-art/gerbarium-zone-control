package com.gerbarium.runtime.command;

import com.gerbarium.runtime.admin.ActionResultDto;
import com.gerbarium.runtime.admin.RuntimeAdminService;
import com.gerbarium.runtime.admin.RuntimeQueryService;
import com.gerbarium.runtime.config.RuntimeConfigStorage;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.permission.PermissionUtil;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class RuntimeCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(literal("gerbzone")
                .requires(source -> PermissionUtil.hasAdminPermission(source))
                
                // GUI
                .then(literal("gui")
                    .executes(context -> {
                        ServerCommandSource source = context.getSource();
                        if (source.getEntity() instanceof ServerPlayerEntity player) {
                            ServerPlayNetworking.send(player, GerbariumRuntimePackets.OPEN_RUNTIME_GUI, PacketByteBufs.create());
                            return 1;
                        } else {
                            source.sendError(Text.literal("GUI is only available for players."));
                            return 0;
                        }
                    })
                )
                
                // Reload
                .then(literal("reload")
                    .executes(context -> {
                        ActionResultDto result = RuntimeAdminService.reload(context.getSource().getName());
                        context.getSource().sendFeedback(() -> Text.literal(result.message + ": Loaded " + result.loadedZones + ", Enabled " + result.enabledZones), true);
                        return 1;
                    })
                )
                
                // Status
                .then(literal("status")
                    .executes(context -> {
                        String status = RuntimeQueryService.getRuntimeStatusString();
                        context.getSource().sendFeedback(() -> Text.literal(status), false);
                        return 1;
                    })
                )
                
                // Zones
                .then(literal("zones")
                    .executes(context -> {
                        String zones = RuntimeQueryService.getZonesListString();
                        context.getSource().sendFeedback(() -> Text.literal(zones), false);
                        return 1;
                    })
                )
                
                // States
                .then(literal("states")
                    .executes(context -> {
                        String states = RuntimeQueryService.getStateFilesString();
                        context.getSource().sendFeedback(() -> Text.literal(states), false);
                        return 1;
                    })
                )
                
                // Debug on/off
                .then(literal("debug")
                    .then(literal("on")
                        .executes(context -> {
                            RuntimeConfigStorage.getConfig().debug = true;
                            RuntimeConfigStorage.save();
                            context.getSource().sendFeedback(() -> Text.literal("Debug enabled."), true);
                            return 1;
                        })
                    )
                    .then(literal("off")
                        .executes(context -> {
                            RuntimeConfigStorage.getConfig().debug = false;
                            RuntimeConfigStorage.save();
                            context.getSource().sendFeedback(() -> Text.literal("Debug disabled."), true);
                            return 1;
                        })
                    )
                )
                
                // Events
                .then(literal("events")
                    .executes(context -> {
                        String events = RuntimeQueryService.getEventsString(20);
                        context.getSource().sendFeedback(() -> Text.literal(events), false);
                        return 1;
                    })
                    .then(argument("zoneId", StringArgumentType.string())
                        .executes(context -> {
                            String zoneId = StringArgumentType.getString(context, "zoneId");
                            String events = RuntimeQueryService.getZoneEventsString(zoneId, 20);
                            context.getSource().sendFeedback(() -> Text.literal(events), false);
                            return 1;
                        })
                        .then(argument("ruleId", StringArgumentType.string())
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                String ruleId = StringArgumentType.getString(context, "ruleId");
                                String events = RuntimeQueryService.getRuleEventsString(zoneId, ruleId, 20);
                                context.getSource().sendFeedback(() -> Text.literal(events), false);
                                return 1;
                            })
                        )
                    )
                )
                
                // State commands
                .then(literal("state")
                    .then(literal("save")
                        .executes(context -> {
                            RuntimeStateStorage.saveAllDirty();
                            context.getSource().sendFeedback(() -> Text.literal("Runtime state saved to disk."), true);
                            return 1;
                        })
                    )
                    .then(literal("cleanup-missing-zones")
                        .executes(context -> {
                            ActionResultDto result = RuntimeAdminService.cleanupMissingZoneStates(context.getSource().getName());
                            context.getSource().sendFeedback(() -> Text.literal(result.message), true);
                            return 1;
                        })
                    )
                )
                
                // Cleanup orphans
                .then(literal("cleanup-orphans")
                    .executes(context -> {
                        ActionResultDto result = RuntimeAdminService.cleanupOrphans(context.getSource().getServer(), context.getSource().getName());
                        context.getSource().sendFeedback(() -> Text.literal(result.message + ": Removed " + result.removed + " orphans"), true);
                        return 1;
                    })
                )
                
                // Zone commands
                .then(literal("zone")
                    .then(argument("zoneId", StringArgumentType.string())
                        .then(literal("status")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                String status = RuntimeQueryService.getZoneStatusString(zoneId, context.getSource().getServer());
                                context.getSource().sendFeedback(() -> Text.literal(status), false);
                                return 1;
                            })
                        )
                        .then(literal("schedule")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                String schedule = RuntimeQueryService.getZoneScheduleString(zoneId, context.getSource().getServer());
                                context.getSource().sendFeedback(() -> Text.literal(schedule), false);
                                return 1;
                            })
                        )
                        .then(literal("history")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                String events = RuntimeQueryService.getZoneEventsString(zoneId, 20);
                                context.getSource().sendFeedback(() -> Text.literal(events), false);
                                return 1;
                            })
                        )
                        .then(literal("force-spawn")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                ActionResultDto result = RuntimeAdminService.forceSpawnZone(zoneId, context.getSource().getServer(), context.getSource().getName());
                                context.getSource().sendFeedback(() -> Text.literal(result.message + ": Spawned " + result.primarySpawned + " primary, " + result.companionsSpawned + " companions"), true);
                                return 1;
                            })
                        )
                        .then(literal("force-activate")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                ActionResultDto result = RuntimeAdminService.forceActivateZone(zoneId, context.getSource().getName());
                                context.getSource().sendFeedback(() -> Text.literal(result.message), true);
                                return 1;
                            })
                        )
                        .then(literal("force-deactivate")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                ActionResultDto result = RuntimeAdminService.forceDeactivateZone(zoneId, context.getSource().getServer(), context.getSource().getName());
                                context.getSource().sendFeedback(() -> Text.literal(result.message + ": Removed " + result.removed + " mobs"), true);
                                return 1;
                            })
                        )
                        .then(literal("clear")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                ActionResultDto result = RuntimeAdminService.clearZoneMobs(zoneId, context.getSource().getServer(), context.getSource().getName());
                                context.getSource().sendFeedback(() -> Text.literal(result.message + ": Removed " + result.removed + " mobs"), true);
                                return 1;
                            })
                        )
                        .then(literal("clear-state")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                RuntimeAdminService.clearZoneState(zoneId, context.getSource().getName());
                                context.getSource().sendFeedback(() -> Text.literal("State cleared for zone: " + zoneId), true);
                                return 1;
                            })
                        )
                    )
                )
                
                // Rule commands
                .then(literal("rule")
                    .then(argument("zoneId", StringArgumentType.string())
                        .then(argument("ruleId", StringArgumentType.string())
                            .then(literal("status")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    String status = RuntimeQueryService.getRuleStatusString(zoneId, ruleId, context.getSource().getServer());
                                    context.getSource().sendFeedback(() -> Text.literal(status), false);
                                    return 1;
                                })
                            )
                            .then(literal("history")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    String events = RuntimeQueryService.getRuleEventsString(zoneId, ruleId, 20);
                                    context.getSource().sendFeedback(() -> Text.literal(events), false);
                                    return 1;
                                })
                            )
                            .then(literal("force-spawn")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    ActionResultDto result = RuntimeAdminService.forceSpawnRule(zoneId, ruleId, context.getSource().getServer(), context.getSource().getName());
                                    context.getSource().sendFeedback(() -> Text.literal(result.message + ": Spawned " + result.primarySpawned + " primary, " + result.companionsSpawned + " companions"), true);
                                    return 1;
                                })
                            )
                            .then(literal("force-spawn-primary")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    ActionResultDto result = RuntimeAdminService.forceSpawnPrimary(zoneId, ruleId, context.getSource().getServer(), context.getSource().getName());
                                    context.getSource().sendFeedback(() -> Text.literal(result.message), true);
                                    return 1;
                                })
                            )
                            .then(literal("force-spawn-companions")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    ServerPlayerEntity player = context.getSource().getEntity() instanceof ServerPlayerEntity ? (ServerPlayerEntity) context.getSource().getEntity() : null;
                                    ActionResultDto result = RuntimeAdminService.forceSpawnCompanions(zoneId, ruleId, context.getSource().getServer(), player, context.getSource().getName());
                                    context.getSource().sendFeedback(() -> Text.literal(result.message + ": Spawned " + result.companionsSpawned + " companions"), true);
                                    return 1;
                                })
                            )
                            .then(literal("reset-cooldown")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    ActionResultDto result = RuntimeAdminService.resetRuleCooldown(zoneId, ruleId, context.getSource().getName());
                                    context.getSource().sendFeedback(() -> Text.literal(result.message), true);
                                    return 1;
                                })
                            )
                            .then(literal("kill-managed")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    ActionResultDto result = RuntimeAdminService.killManagedMobs(zoneId, ruleId, context.getSource().getServer(), context.getSource().getName());
                                    context.getSource().sendFeedback(() -> Text.literal(result.message + ": Removed " + result.removed + " mobs"), true);
                                    return 1;
                                })
                            )
                            .then(literal("clear-state")
                                .executes(context -> {
                                    String zoneId = StringArgumentType.getString(context, "zoneId");
                                    String ruleId = StringArgumentType.getString(context, "ruleId");
                                    RuntimeAdminService.clearRuleState(zoneId, ruleId, context.getSource().getName());
                                    context.getSource().sendFeedback(() -> Text.literal("State cleared for rule: " + zoneId + ":" + ruleId), true);
                                    return 1;
                                })
                            )
                        )
                    )
                )
            );
        });
    }
}
