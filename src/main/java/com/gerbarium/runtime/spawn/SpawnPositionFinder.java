package com.gerbarium.runtime.spawn;

import com.gerbarium.runtime.model.Zone;
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
}
