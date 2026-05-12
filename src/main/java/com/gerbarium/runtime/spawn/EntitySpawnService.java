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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.registry.Registries;

import java.util.Optional;
import java.util.Random;

public class EntitySpawnService {
    private static final Random RANDOM = new Random();

    public static SpawnResult spawnPrimary(ServerWorld world, Zone zone, MobRule rule, BlockPos pos, boolean forced) {
        Optional<EntityType<?>> typeOpt = Registries.ENTITY_TYPE.getOrEmpty(new Identifier(rule.entity));
        if (typeOpt.isEmpty()) {
            return SpawnResult.FAILED_INVALID_ENTITY;
        }

        EntityType<?> type = typeOpt.get();
        Entity entity = type.create(world);
        if (entity == null) {
            return SpawnResult.FAILED_UNKNOWN;
        }

        entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);
        
        if (entity instanceof MobEntity mob) {
            mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.SPAWNER, null, null);
        }

        MobTagger.tagPrimary(entity, zone.id, rule.id, forced);
        if (world.spawnEntity(entity)) {
            MobTracker.incrementPrimary(zone.id, rule.id);
            spawnCompanions(world, zone, rule, entity, forced);
            return SpawnResult.SUCCESS;
        }

        return SpawnResult.FAILED_SPAWN_REJECTED;
    }

    public static int spawnCompanions(ServerWorld world, Zone zone, MobRule parentRule, Entity primaryEntity, boolean forced) {
        int spawnedCount = 0;
        BlockPos primaryPos = primaryEntity.getBlockPos();

        int minX = Math.min(zone.min.x, zone.max.x);
        int maxX = Math.max(zone.min.x, zone.max.x);
        int minY = Math.min(zone.min.y, zone.max.y);
        int maxY = Math.max(zone.min.y, zone.max.y);
        int minZ = Math.min(zone.min.z, zone.max.z);
        int maxZ = Math.max(zone.min.z, zone.max.z);

        for (CompanionRule companion : parentRule.companions) {
            if (!forced && RANDOM.nextDouble() > companion.chance) continue;

            Optional<EntityType<?>> typeOpt = Registries.ENTITY_TYPE.getOrEmpty(new Identifier(companion.entity));
            if (typeOpt.isEmpty()) continue;

            EntityType<?> type = typeOpt.get();

            for (int i = 0; i < companion.count; i++) {
                BlockPos pos = findCompanionPos(world, primaryPos, companion.radius, minX, maxX, minY, maxY, minZ, maxZ);
                if (pos == null) continue;

                Entity entity = type.create(world);
                if (entity == null) continue;

                entity.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, RANDOM.nextFloat() * 360.0F, 0.0F);

                if (entity instanceof MobEntity mob) {
                    mob.initialize(world, world.getLocalDifficulty(pos), SpawnReason.SPAWNER, null, null);
                }

                MobTagger.tagCompanion(entity, zone.id, parentRule.id, companion.id, forced);
                if (world.spawnEntity(entity)) {
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