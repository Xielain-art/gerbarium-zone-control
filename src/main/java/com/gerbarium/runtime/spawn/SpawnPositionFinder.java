package com.gerbarium.runtime.spawn;

import com.gerbarium.runtime.model.Zone;
import com.gerbarium.runtime.model.MobRule;
import net.minecraft.entity.Entity;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SpawnPositionFinder {
    private static final Random RANDOM = new Random();

    public static Optional<BlockPos> findSpawnPosition(ServerWorld world, Zone zone, List<ServerPlayerEntity> nearbyPlayers) {
        int minX = Math.min(zone.min.x, zone.max.x);
        int maxX = Math.max(zone.min.x, zone.max.x);
        int minY = Math.min(zone.min.y, zone.max.y);
        int maxY = Math.max(zone.min.y, zone.max.y);
        int minZ = Math.min(zone.min.z, zone.max.z);
        int maxZ = Math.max(zone.min.z, zone.max.z);
        int minDistance = Math.max(0, Math.min(zone.spawn.minDistanceFromPlayer, zone.spawn.maxDistanceFromPlayer));
        int maxDistance = Math.max(minDistance, Math.max(zone.spawn.minDistanceFromPlayer, zone.spawn.maxDistanceFromPlayer));

        if (nearbyPlayers.isEmpty()) {
            BlockPos fallback = new BlockPos((minX + maxX) / 2, Math.max(minY, Math.min(maxY, (minY + maxY) / 2)), (minZ + maxZ) / 2);
            if (world.getBlockState(fallback).isAir() && world.getBlockState(fallback.down()).isSolidBlock(world, fallback.down())) {
                return Optional.of(fallback);
            }
            return Optional.empty();
        }

        ServerPlayerEntity targetPlayer = nearbyPlayers.get(RANDOM.nextInt(nearbyPlayers.size()));
        BlockPos playerPos = targetPlayer.getBlockPos();

        for (int i = 0; i < zone.spawn.maxPositionAttempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = minDistance + RANDOM.nextDouble() * (maxDistance - minDistance);

            int x = playerPos.getX() + (int) (Math.cos(angle) * distance);
            int z = playerPos.getZ() + (int) (Math.sin(angle) * distance);

            // Clamp to zone bounds
            x = MathHelper.clamp(x, minX, maxX);
            z = MathHelper.clamp(z, minZ, maxZ);

            if (zone.spawn.requireLoadedChunk && !world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            for (int y = maxY; y >= minY; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isValidSpawnPosition(world, pos, zone.spawn.respectVanillaSpawnRules)) {
                    return Optional.of(pos);
                }
            }
        }

        return Optional.empty();
    }

    public static Optional<BlockPos> findReturnPosition(ServerWorld world, Zone zone, MobRule rule, Entity entity, List<ServerPlayerEntity> nearbyPlayers) {
        int minX = Math.min(zone.min.x, zone.max.x);
        int maxX = Math.max(zone.min.x, zone.max.x);
        int minY = Math.min(zone.min.y, zone.max.y);
        int maxY = Math.max(zone.min.y, zone.max.y);
        int minZ = Math.min(zone.min.z, zone.max.z);
        int maxZ = Math.max(zone.min.z, zone.max.z);

        if (nearbyPlayers != null && !nearbyPlayers.isEmpty()) {
            ServerPlayerEntity player = nearbyPlayers.get(RANDOM.nextInt(nearbyPlayers.size()));
            BlockPos playerPos = player.getBlockPos();
            Optional<BlockPos> aroundPlayer = findSafeInsidePosition(world, minX, maxX, minY, maxY, minZ, maxZ, playerPos.getX(), playerPos.getZ(), Math.max(4, zone.spawn.minDistanceFromPlayer), Math.max(8, zone.spawn.maxDistanceFromPlayer));
            if (aroundPlayer.isPresent()) {
                return aroundPlayer;
            }
        }

        Optional<BlockPos> randomInside = findSafeInsidePosition(world, minX, maxX, minY, maxY, minZ, maxZ, entity.getBlockX(), entity.getBlockZ(), 0, Math.max(8, Math.min(maxX - minX, maxZ - minZ)));
        if (randomInside.isPresent()) {
            return randomInside;
        }

        BlockPos center = new BlockPos((minX + maxX) / 2, Math.max(minY, Math.min(maxY, (minY + maxY) / 2)), (minZ + maxZ) / 2);
        if (isSafeInsidePosition(world, center)) {
            return Optional.of(center);
        }

        for (int y = maxY; y >= minY; y--) {
            BlockPos pos = new BlockPos((minX + maxX) / 2, y, (minZ + maxZ) / 2);
            if (isSafeInsidePosition(world, pos)) {
                return Optional.of(pos);
            }
        }

        return Optional.empty();
    }

    private static Optional<BlockPos> findSafeInsidePosition(ServerWorld world, int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
                                                             int centerX, int centerZ, int minDistance, int maxDistance) {
        int attempts = Math.max(12, (maxDistance - minDistance + 1) * 2);

        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = minDistance + RANDOM.nextDouble() * Math.max(1, maxDistance - minDistance + 1);

            int x = centerX + (int) (Math.cos(angle) * distance);
            int z = centerZ + (int) (Math.sin(angle) * distance);
            x = MathHelper.clamp(x, minX, maxX);
            z = MathHelper.clamp(z, minZ, maxZ);

            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                continue;
            }

            for (int y = maxY; y >= minY; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isSafeInsidePosition(world, pos)) {
                    return Optional.of(pos);
                }
            }
        }

        return Optional.empty();
    }

    private static boolean isValidSpawnPosition(ServerWorld world, BlockPos pos, boolean respectVanillaRules) {
        BlockState state = world.getBlockState(pos);
        if (!state.isAir()) return false;

        BlockState below = world.getBlockState(pos.down());
        if (!below.isSolidBlock(world, pos.down())) return false;

        if (respectVanillaRules) {
            // Very basic vanilla-like check: check if entity can be at this position
            // For a more robust check, we'd need the EntityType
            return world.getBlockState(pos.up()).isAir();
        }

        return true;
    }

    private static boolean isSafeInsidePosition(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }

        BlockState state = world.getBlockState(pos);
        if (!state.isAir()) {
            return false;
        }

        BlockState above = world.getBlockState(pos.up());
        if (!above.isAir()) {
            return false;
        }

        BlockState below = world.getBlockState(pos.down());
        return below.isSolidBlock(world, pos.down());
    }
}
