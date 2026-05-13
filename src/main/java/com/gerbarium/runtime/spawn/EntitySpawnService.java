package com.gerbarium.runtime.spawn;

import com.gerbarium.runtime.GerbariumRegionsRuntime;
import com.gerbarium.runtime.model.CompanionRule;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.tracking.MobTagger;
import com.gerbarium.runtime.tracking.MobTracker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class EntitySpawnService {
    private static final Random RANDOM = new Random();

    public static Entity spawnPrimary(ServerWorld world, Zone zone, MobRule rule, BlockPos pos, SpawnContext context) {
        Identifier entityId = Identifier.tryParse(rule.entity);
        if (entityId == null) {
            GerbariumRegionsRuntime.LOGGER.warn("Invalid entity id for zone={} rule={}: {}", zone.id, rule.id, rule.entity);
            return null;
        }

        Optional<EntityType<?>> typeOpt = Registries.ENTITY_TYPE.getOrEmpty(entityId);
        if (typeOpt.isEmpty()) {
            GerbariumRegionsRuntime.LOGGER.warn("Unknown entity type for zone={} rule={}: {}", zone.id, rule.id, rule.entity);
            return null;
        }

        EntityType<?> type = typeOpt.get();
        Entity entity = type.create(world);
        if (entity == null) {
            return null;
        }

        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);

        if (entity instanceof MobEntity mob) {
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.SPAWNER, null, null);
            mob.setPersistent();
        }

        MobTagger.tagPrimary(entity, zone.id, rule.id, context == SpawnContext.FORCED);
        if (world.spawnEntity(entity)) {
            MobTracker.incrementPrimary(zone.id, rule.id, context == SpawnContext.FORCED);
            GerbariumRegionsRuntime.LOGGER.debug(
                    "Spawned primary mob zone={} rule={} role=PRIMARY forced={} uuid={} pos={}",
                    zone.id, rule.id, context == SpawnContext.FORCED, entity.getUuid(), pos
            );

            if (rule.announceOnSpawn && context == SpawnContext.NORMAL) {
                String displayName = rule.name != null && !rule.name.isBlank() ? rule.name : rule.id;
                String message = "[Gerbarium] " + displayName + " spawned in zone " + zone.id;
                world.getServer().getPlayerManager().broadcast(Text.literal(message), false);
            }
            return entity;
        }

        return null;
    }

    public static int spawnCompanions(ServerWorld world, Zone zone, MobRule parentRule, Entity primaryEntity, SpawnContext context) {
        List<CompanionRule> companions = parentRule.companions;
        if (companions == null || companions.isEmpty()) {
            return 0;
        }

        int spawnedCount = 0;
        BlockPos primaryPos = primaryEntity.getBlockPos();

        int minX = zone.getMinX();
        int maxX = zone.getMaxX();
        int minY = zone.getMinY();
        int maxY = zone.getMaxY();
        int minZ = zone.getMinZ();
        int maxZ = zone.getMaxZ();

        for (CompanionRule companion : companions) {
            if (companion == null || companion.entity == null) continue;
            if (RANDOM.nextDouble() > companion.chance) continue;

            Identifier entityId = Identifier.tryParse(companion.entity);
            if (entityId == null) {
                continue;
            }

            Optional<EntityType<?>> typeOpt = Registries.ENTITY_TYPE.getOrEmpty(entityId);
            if (typeOpt.isEmpty()) {
                continue;
            }

            EntityType<?> type = typeOpt.get();

            for (int i = 0; i < companion.count; i++) {
                BlockPos pos = findCompanionPos(world, primaryPos, companion.radius, minX, maxX, minY, maxY, minZ, maxZ);
                if (pos == null) continue;

                Entity entity = type.create(world);
                if (entity == null) continue;

                entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);

                if (entity instanceof MobEntity mob) {
                    mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.SPAWNER, null, null);
                    mob.setPersistent();
                }

                MobTagger.tagCompanion(entity, zone.id, parentRule.id, companion.id, context == SpawnContext.FORCED);
                if (world.spawnEntity(entity)) {
                    MobTracker.incrementCompanion(zone.id, parentRule.id, context == SpawnContext.FORCED);
                    GerbariumRegionsRuntime.LOGGER.debug(
                            "Spawned companion mob zone={} rule={} companion={} forced={} uuid={} pos={}",
                            zone.id, parentRule.id, companion.id, context == SpawnContext.FORCED, entity.getUuid(), pos
                    );
                    spawnedCount++;
                }
            }
        }
        return spawnedCount;
    }

    private static BlockPos findCompanionPos(ServerWorld world, BlockPos center, int radius, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        for (int i = 0; i < 5; i++) {
            int x = center.getX() + RANDOM.nextInt(radius * 2 + 1) - radius;
            int z = center.getZ() + RANDOM.nextInt(radius * 2 + 1) - radius;

            x = Math.max(minX, Math.min(maxX, x));
            z = Math.max(minZ, Math.min(maxZ, z));

            for (int y = Math.min(maxY, center.getY() + 2); y >= Math.max(minY, center.getY() - 2); y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (world.getBlockState(pos).isAir() && world.getBlockState(pos.down()).isSolidBlock(world, pos.down())) {
                    return pos;
                }
            }
        }
        return null;
    }
}