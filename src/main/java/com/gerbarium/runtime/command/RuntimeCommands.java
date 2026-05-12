package com.gerbarium.runtime.command;

import com.gerbarium.runtime.admin.ActionResultDto;
import com.gerbarium.runtime.admin.RuntimeAdminService;
import com.gerbarium.runtime.admin.RuntimeQueryService;
import com.gerbarium.runtime.network.GerbariumRuntimePackets;
import com.gerbarium.runtime.permission.PermissionUtil;
import com.gerbarium.runtime.storage.RuntimeStateStorage;
import com.gerbarium.runtime.storage.ZoneRepository;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.CommandManager;
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
                .then(literal("reload")
                    .executes(context -> {
                        ActionResultDto result = RuntimeAdminService.reload(context.getSource().getName());
                        context.getSource().sendFeedback(() -> Text.literal(result.message + ": Loaded " + result.loadedZones + ", Enabled " + result.enabledZones), true);
                        return 1;
                    })
                )
                .then(literal("status")
                    .executes(context -> {
                        int total = ZoneRepository.getAll().size();
                        int enabled = ZoneRepository.getEnabledZones().size();
                        context.getSource().sendFeedback(() -> Text.literal("Runtime Status: " + enabled + "/" + total + " zones enabled."), false);
                        return 1;
                    })
                )
                .then(literal("states")
                    .executes(context -> {
                        Collection<Zone> zones = ZoneRepository.getAll();
                        context.getSource().sendFeedback(() -> Text.literal("Zone States:"), false);
                        for (Zone zone : zones) {
                            var zf = RuntimeStateStorage.getZoneState(zone.id);
                            context.getSource().sendFeedback(() -> Text.literal("- " + zone.id + ": Rules=" + zf.rules.size() + ", Events=" + zf.recentEvents.size()), false);
                        }
                        return 1;
                    })
                )
                .then(literal("zone")
                    .then(argument("zoneId", StringArgumentType.string())
                        .then(literal("status")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                String status = RuntimeQueryService.getZoneStatusString(zoneId);
                                context.getSource().sendFeedback(() -> Text.literal(status), false);
                                return 1;
                            })
                        )
                        .then(literal("force-spawn")
                            .executes(context -> {
                                String zoneId = StringArgumentType.getString(context, "zoneId");
                                ActionResultDto result = RuntimeAdminService.forceSpawnZone(zoneId, context.getSource().getServer(), context.getSource().getName());
                                context.getSource().sendFeedback(() -> Text.literal(result.message + ": Spawned " + result.primarySpawned + " primary mobs"), true);
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
                .then(literal("rule")
                    .then(argument("zoneId", StringArgumentType.string())
                        .then(argument("ruleId", StringArgumentType.string())
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
                .then(literal("cleanup-orphans")
                    .executes(context -> {
                        ActionResultDto result = RuntimeAdminService.cleanupOrphans(context.getSource().getServer(), context.getSource().getName());
                        context.getSource().sendFeedback(() -> Text.literal(result.message + ": Removed " + result.removed + " orphans"), true);
                        return 1;
                    })
                )
                .then(literal("state")
                    .then(literal("save")
                        .executes(context -> {
                            RuntimeStateStorage.save();
                            context.getSource().sendFeedback(() -> Text.literal("Runtime state saved to disk."), true);
                            return 1;
                        })
                    )
                )
            );
        });
    }
}