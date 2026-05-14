package com.gerbarium.runtime.spawn;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.Zone;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SpawnPositionFinder {
    private static final Random RANDOM = new Random();

    public static Optional<BlockPos> findSpawnPosition(ServerWorld world, Zone zone, MobRule rule, List<ServerPlayerEntity> nearbyPlayers) {
        Optional<EntityType<?>> type = getEntityType(rule == null ? null : rule.entity);
        if (type.isEmpty()) return Optional.empty();

        int minDistance = Math.max(0, Math.min(zone.spawn.minDistanceFromPlayer, zone.spawn.maxDistanceFromPlayer));
        int maxDistance = Math.max(minDistance, Math.max(zone.spawn.minDistanceFromPlayer, zone.spawn.maxDistanceFromPlayer));
        List<ServerPlayerEntity> players = nearbyPlayers == null ? List.of() : nearbyPlayers;

        if (players.isEmpty()) {
            Optional<BlockPos> center = scanColumn(world, zone, type.get(), (zone.getMinX() + zone.getMaxX()) / 2, (zone.getMinZ() + zone.getMaxZ()) / 2, zone.spawn.allowNonSolidGround);
            return center.or(() -> findRandomInside(world, zone, type.get(), Math.max(24, zone.spawn.maxPositionAttempts), zone.spawn.allowNonSolidGround));
        }

        ServerPlayerEntity targetPlayer = players.get(RANDOM.nextInt(players.size()));
        BlockPos playerPos = targetPlayer.getBlockPos();

        for (int i = 0; i < Math.max(1, zone.spawn.maxPositionAttempts); i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = minDistance + RANDOM.nextDouble() * Math.max(1, maxDistance - minDistance);
            int x = playerPos.getX() + (int) (Math.cos(angle) * distance);
            int z = playerPos.getZ() + (int) (Math.sin(angle) * distance);

            x = MathHelper.clamp(x, zone.getMinX(), zone.getMaxX());
            z = MathHelper.clamp(z, zone.getMinZ(), zone.getMaxZ());

            if (!isDistanceAllowed(x, z, players, minDistance, maxDistance)) continue;

            Optional<BlockPos> pos = scanColumn(world, zone, type.get(), x, z, zone.spawn.allowNonSolidGround);
            if (pos.isPresent()) return pos;
        }

        return Optional.empty();
    }

    public static Optional<BlockPos> findReturnPosition(ServerWorld world, Zone zone, MobRule rule, Entity entity, List<ServerPlayerEntity> nearbyPlayers) {
        EntityType<?> type = entity == null ? null : entity.getType();
        List<ServerPlayerEntity> players = nearbyPlayers == null ? List.of() : nearbyPlayers;

        if (!players.isEmpty()) {
            ServerPlayerEntity player = players.get(RANDOM.nextInt(players.size()));
            Optional<BlockPos> aroundPlayer = findSafeInsidePosition(world, zone, type, player.getBlockX(), player.getBlockZ(),
                    Math.max(4, zone.spawn.minDistanceFromPlayer), Math.max(8, zone.spawn.maxDistanceFromPlayer), zone.spawn.allowNonSolidGround);
            if (aroundPlayer.isPresent()) return aroundPlayer;
        }

        Optional<BlockPos> aroundEntity = findSafeInsidePosition(world, zone, type,
                entity == null ? (zone.getMinX() + zone.getMaxX()) / 2 : entity.getBlockX(),
                entity == null ? (zone.getMinZ() + zone.getMaxZ()) / 2 : entity.getBlockZ(),
                0, Math.max(8, Math.min(zone.getMaxX() - zone.getMinX(), zone.getMaxZ() - zone.getMinZ())), zone.spawn.allowNonSolidGround);
        if (aroundEntity.isPresent()) return aroundEntity;

        return scanColumn(world, zone, type, (zone.getMinX() + zone.getMaxX()) / 2, (zone.getMinZ() + zone.getMaxZ()) / 2, zone.spawn.allowNonSolidGround);
    }

    public static Optional<BlockPos> findCompanionPosition(ServerWorld world, Zone zone, EntityType<?> type, BlockPos center, int radius) {
        int safeRadius = Math.max(1, radius);
        for (int i = 0; i < Math.max(8, safeRadius * 2); i++) {
            int x = center.getX() + RANDOM.nextInt(safeRadius * 2 + 1) - safeRadius;
            int z = center.getZ() + RANDOM.nextInt(safeRadius * 2 + 1) - safeRadius;
            x = MathHelper.clamp(x, zone.getMinX(), zone.getMaxX());
            z = MathHelper.clamp(z, zone.getMinZ(), zone.getMaxZ());

            int startY = Math.min(zone.getMaxY(), center.getY() + 2);
            int endY = Math.max(zone.getMinY(), center.getY() - 3);
            for (int y = startY; y >= endY; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isSafeSpawnPosition(world, type, pos, false, zone.spawn.allowNonSolidGround)) return Optional.of(pos);
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findSafeInsidePosition(ServerWorld world, Zone zone, EntityType<?> type, int centerX, int centerZ, int minDistance, int maxDistance, boolean allowNonSolidGround) {
        int attempts = Math.max(12, (maxDistance - minDistance + 1) * 2);
        for (int i = 0; i < attempts; i++) {
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double distance = minDistance + RANDOM.nextDouble() * Math.max(1, maxDistance - minDistance + 1);
            int x = MathHelper.clamp(centerX + (int) (Math.cos(angle) * distance), zone.getMinX(), zone.getMaxX());
            int z = MathHelper.clamp(centerZ + (int) (Math.sin(angle) * distance), zone.getMinZ(), zone.getMaxZ());
            Optional<BlockPos> pos = scanColumn(world, zone, type, x, z, allowNonSolidGround);
            if (pos.isPresent()) return pos;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findRandomInside(ServerWorld world, Zone zone, EntityType<?> type, int attempts, boolean allowNonSolidGround) {
        for (int i = 0; i < attempts; i++) {
            Optional<BlockPos> pos = scanColumn(world, zone, type, randomBetween(zone.getMinX(), zone.getMaxX()), randomBetween(zone.getMinZ(), zone.getMaxZ()), allowNonSolidGround);
            if (pos.isPresent()) return pos;
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> scanColumn(ServerWorld world, Zone zone, EntityType<?> type, int x, int z, boolean allowNonSolidGround) {
        if (!SpawnMathUtil.isInsideZoneXZ(x, z, zone.getMinX(), zone.getMaxX(), zone.getMinZ(), zone.getMaxZ())) return Optional.empty();
        if (zone.spawn.requireLoadedChunk && !world.isChunkLoaded(x >> 4, z >> 4)) return Optional.empty();

        for (int y = zone.getMaxY(); y >= zone.getMinY(); y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (isSafeSpawnPosition(world, type, pos, zone.spawn.respectVanillaSpawnRules, allowNonSolidGround)) return Optional.of(pos);
        }
        return Optional.empty();
    }

    private static boolean isSafeSpawnPosition(ServerWorld world, EntityType<?> type, BlockPos pos, boolean respectVanillaRules, boolean allowNonSolidGround) {
        if (!world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) return false;

        BlockState feet = world.getBlockState(pos);
        if (!feet.isAir()) return false;

        BlockState head = world.getBlockState(pos.up());
        if (!head.isAir()) return false;

        BlockState below = world.getBlockState(pos.down());
        if (!SpawnGroundUtil.isValidSpawnGround(below.isSolidBlock(world, pos.down()), allowNonSolidGround)) return false;

        if (type != null) {
            EntityDimensions dimensions = type.getDimensions();
            Box box = dimensions.getBoxAt(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (!world.isSpaceEmpty(box)) return false;
        }

        return !respectVanillaRules || canSpawnByVanillaRules(world, type, pos);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static boolean canSpawnByVanillaRules(ServerWorld world, EntityType<?> type, BlockPos pos) {
        if (type == null) return true;
        return SpawnRestriction.canSpawn((EntityType) type, world, SpawnReason.SPAWNER, pos, world.random);
    }

    private static Optional<EntityType<?>> getEntityType(String entityId) {
        Identifier id = Identifier.tryParse(entityId == null ? "" : entityId);
        return id == null ? Optional.empty() : Registries.ENTITY_TYPE.getOrEmpty(id);
    }

    public static boolean isDistanceAllowed(int x, int z, List<ServerPlayerEntity> players, int minDistance, int maxDistance) {
        if (players == null || players.isEmpty()) return true;
        int min = Math.max(0, minDistance);
        int max = Math.max(min, maxDistance);
        double minSq = min * min;
        double maxSq = max * max;
        for (ServerPlayerEntity player : players) {
            double dx = (x + 0.5D) - player.getX();
            double dz = (z + 0.5D) - player.getZ();
            double distSq = dx * dx + dz * dz;
            if (distSq >= minSq && distSq <= maxSq) return true;
        }
        return false;
    }

    private static int randomBetween(int min, int max) {
        if (max <= min) return min;
        return min + RANDOM.nextInt(max - min + 1);
    }
}
