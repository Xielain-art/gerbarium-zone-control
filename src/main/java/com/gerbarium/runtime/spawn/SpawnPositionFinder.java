package com.gerbarium.runtime.spawn;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.SpawnMode;
import com.gerbarium.runtime.model.Zone;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;

import java.util.List;
import java.util.Optional;
import java.util.Random;

public class SpawnPositionFinder {
    private static final Random RANDOM = new Random();

    public static Optional<BlockPos> findSpawnPosition(ServerWorld world, Zone zone, MobRule rule, List<ServerPlayerEntity> nearbyPlayers) {
        return findSpawnPositionResult(world, zone, rule, nearbyPlayers, Math.max(128, zone.spawn.maxPositionAttempts)).position();
    }

    public static SpawnPositionResult findSpawnPositionResult(ServerWorld world, Zone zone, MobRule rule, List<ServerPlayerEntity> nearbyPlayers, int maxAttempts) {
        return findSpawnPositionResult(world, zone, rule, nearbyPlayers, maxAttempts, List.of());
    }

    public static SpawnPositionResult findSpawnPositionResult(ServerWorld world, Zone zone, MobRule rule, List<ServerPlayerEntity> nearbyPlayers, int maxAttempts, List<BlockPos> selectedPositions) {
        Optional<EntityType<?>> type = getEntityType(rule == null ? null : rule.entity);
        PositionRejectStats stats = new PositionRejectStats();
        int attempts = Math.max(1, Math.max(maxAttempts, rule == null ? 1 : rule.positionAttempts));
        if (type.isEmpty()) return SpawnPositionResult.fail("invalid_entity", stats, attempts);
        if (rule != null) {
            rule.normalize();
        }

        int minDistance = Math.max(0, Math.min(zone.spawn.minDistanceFromPlayer, zone.spawn.maxDistanceFromPlayer));
        int maxDistance = Math.max(minDistance, Math.max(zone.spawn.minDistanceFromPlayer, zone.spawn.maxDistanceFromPlayer));
        List<ServerPlayerEntity> players = nearbyPlayers == null ? List.of() : nearbyPlayers;
        List<BlockPos> selected = selectedPositions == null ? List.of() : selectedPositions;
        SpawnMode mode = rule == null || rule.spawnMode == null ? SpawnMode.RANDOM_VALID_POSITION : rule.spawnMode;

        if (mode == SpawnMode.FIXED_POINT) {
            return findFixedPosition(world, zone, rule, type.get(), attempts, zone.spawn.allowNonSolidGround, zone.spawn.requireLoadedChunk, stats, selected);
        }
        if (mode == SpawnMode.CENTER || mode == SpawnMode.BOSS_ROOM || mode == SpawnMode.NEAR_CENTER) {
            SpawnPositionResult centered = findNearestCenterPosition(world, zone, rule, type.get(), attempts, zone.spawn.allowNonSolidGround, zone.spawn.requireLoadedChunk, stats, selected);
            if (centered.success() || mode == SpawnMode.CENTER || mode == SpawnMode.BOSS_ROOM) {
                return centered;
            }
        }

        return findValidSpawnPosition(world, zone, rule, type.get(), players, minDistance, maxDistance, RANDOM, attempts, zone.spawn.allowNonSolidGround, zone.spawn.requireLoadedChunk, stats, selected);
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
                PositionRejectStats stats = new PositionRejectStats();
                if (isSafeSpawnPosition(world, type, pos, zone.spawn.allowNonSolidGround, stats)) return Optional.of(pos);
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
        PositionRejectStats stats = new PositionRejectStats();
        int minY = Math.max(world.getBottomY(), Math.min(zone.getMinY(), zone.getMaxY()));
        int maxY = Math.min(world.getTopY() - 2, Math.max(zone.getMinY(), zone.getMaxY()));
        if (minY > maxY) return Optional.empty();
        SpawnPositionResult result = scanColumn(world, zone, type, x, z, minY, maxY, allowNonSolidGround, stats, 1);
        if (result.success()) return result.position();
        if (minY == maxY) {
            return scanFlatFloorColumn(world, type, x, z, minY, maxY, allowNonSolidGround, stats, 1).position();
        }
        return Optional.empty();
    }

    private static SpawnPositionResult findValidSpawnPosition(ServerWorld world, Zone zone, MobRule rule, EntityType<?> type, List<ServerPlayerEntity> players, int minDistance, int maxDistance, Random random, int attempts, boolean allowNonSolidGround, boolean requireLoadedChunk, PositionRejectStats stats, List<BlockPos> selectedPositions) {
        int minX = Math.min(zone.min.x, zone.max.x);
        int maxX = Math.max(zone.min.x, zone.max.x);
        int minY = Math.max(world.getBottomY(), Math.min(zone.min.y, zone.max.y));
        int maxY = Math.min(world.getTopY() - 2, Math.max(zone.min.y, zone.max.y));
        int minZ = Math.min(zone.min.z, zone.max.z);
        int maxZ = Math.max(zone.min.z, zone.max.z);

        if (minX > maxX || minY > maxY || minZ > maxZ) {
            return SpawnPositionResult.fail("invalid_zone_bounds", stats, attempts);
        }
        boolean flatZone = minY == maxY;
        if ((maxY - minY) < 1) {
            stats.zoneTooShort++;
        }

        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = randomBetween(random, minX, maxX);
            int z = randomBetween(random, minZ, maxZ);

            if (!players.isEmpty() && !isDistanceAllowed(x, z, players, minDistance, maxDistance)) {
                stats.outsideZone++;
                continue;
            }

            if (requireLoadedChunk && !world.isChunkLoaded(x >> 4, z >> 4)) {
                stats.chunkUnloaded++;
                continue;
            }

            SpawnPositionResult main = scanColumn(world, zone, type, x, z, minY, maxY, allowNonSolidGround, stats, attempts);
            if (main.success() && isBatchDistanceAllowed(main.position().get(), rule, selectedPositions, stats, false)) return main;

            if (flatZone) {
                SpawnPositionResult flat = scanFlatFloorColumn(world, type, x, z, minY, maxY, allowNonSolidGround, stats, attempts);
                if (flat.success() && isBatchDistanceAllowed(flat.position().get(), rule, selectedPositions, stats, false)) return flat;
            }
        }

        if (rule != null && rule.allowSmallRoom && rule.spreadSpawns && !selectedPositions.isEmpty()) {
            SpawnPositionResult relaxed = findAnyValidPosition(world, zone, type, attempts, allowNonSolidGround, requireLoadedChunk, stats, selectedPositions);
            if (relaxed.success()) return relaxed;
        }

        return SpawnPositionResult.fail("no_valid_position", stats, attempts);
    }

    private static SpawnPositionResult findFixedPosition(ServerWorld world, Zone zone, MobRule rule, EntityType<?> type, int attempts, boolean allowNonSolidGround, boolean requireLoadedChunk, PositionRejectStats stats, List<BlockPos> selectedPositions) {
        if (rule == null || rule.fixedX == null || rule.fixedY == null || rule.fixedZ == null) {
            return SpawnPositionResult.fail("missing_fixed_point", stats, attempts);
        }
        BlockPos pos = new BlockPos(rule.fixedX, rule.fixedY, rule.fixedZ);
        if (!isInsideZone(zone, pos)) {
            stats.outsideZone++;
            return SpawnPositionResult.fail("fixed_point_outside_zone", stats, attempts);
        }
        if (requireLoadedChunk && !world.isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4)) {
            stats.chunkUnloaded++;
            return SpawnPositionResult.fail("chunk_unloaded", stats, attempts);
        }
        if (!isSafeSpawnPosition(world, type, pos, allowNonSolidGround, stats)) {
            return SpawnPositionResult.fail("fixed_point_invalid", stats, attempts);
        }
        if (!isBatchDistanceAllowed(pos, rule, selectedPositions, stats, rule.allowSmallRoom)) {
            return SpawnPositionResult.fail("too_close_to_other_spawn", stats, attempts);
        }
        stats.validPositions++;
        return SpawnPositionResult.success(pos, stats, attempts);
    }

    private static SpawnPositionResult findNearestCenterPosition(ServerWorld world, Zone zone, MobRule rule, EntityType<?> type, int attempts, boolean allowNonSolidGround, boolean requireLoadedChunk, PositionRejectStats stats, List<BlockPos> selectedPositions) {
        int centerX = (zone.getMinX() + zone.getMaxX()) / 2;
        int centerZ = (zone.getMinZ() + zone.getMaxZ()) / 2;
        int minY = Math.max(world.getBottomY(), Math.min(zone.getMinY(), zone.getMaxY()));
        int maxY = Math.min(world.getTopY() - 2, Math.max(zone.getMinY(), zone.getMaxY()));
        int radiusLimit = Math.max(zone.getMaxX() - zone.getMinX(), zone.getMaxZ() - zone.getMinZ()) + 1;
        int checked = 0;

        for (int radius = 0; radius <= radiusLimit && checked < attempts; radius++) {
            for (int dx = -radius; dx <= radius && checked < attempts; dx++) {
                for (int dz = -radius; dz <= radius && checked < attempts; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    checked++;
                    if (!SpawnMathUtil.isInsideZoneXZ(x, z, zone.getMinX(), zone.getMaxX(), zone.getMinZ(), zone.getMaxZ())) {
                        stats.outsideZone++;
                        continue;
                    }
                    if (requireLoadedChunk && !world.isChunkLoaded(x >> 4, z >> 4)) {
                        stats.chunkUnloaded++;
                        continue;
                    }
                    SpawnPositionResult result = scanColumn(world, zone, type, x, z, minY, maxY, allowNonSolidGround, stats, attempts);
                    if (!result.success() && minY == maxY) {
                        result = scanFlatFloorColumn(world, type, x, z, minY, maxY, allowNonSolidGround, stats, attempts);
                    }
                    if (result.success() && isBatchDistanceAllowed(result.position().get(), rule, selectedPositions, stats, rule == null || rule.allowSmallRoom)) {
                        return result;
                    }
                }
            }
        }
        return SpawnPositionResult.fail("no_valid_position", stats, attempts);
    }

    private static SpawnPositionResult findAnyValidPosition(ServerWorld world, Zone zone, EntityType<?> type, int attempts, boolean allowNonSolidGround, boolean requireLoadedChunk, PositionRejectStats stats, List<BlockPos> selectedPositions) {
        int minX = Math.min(zone.min.x, zone.max.x);
        int maxX = Math.max(zone.min.x, zone.max.x);
        int minY = Math.max(world.getBottomY(), Math.min(zone.min.y, zone.max.y));
        int maxY = Math.min(world.getTopY() - 2, Math.max(zone.min.y, zone.max.y));
        int minZ = Math.min(zone.min.z, zone.max.z);
        int maxZ = Math.max(zone.min.z, zone.max.z);
        for (int attempt = 0; attempt < attempts; attempt++) {
            int x = randomBetween(RANDOM, minX, maxX);
            int z = randomBetween(RANDOM, minZ, maxZ);
            if (requireLoadedChunk && !world.isChunkLoaded(x >> 4, z >> 4)) {
                stats.chunkUnloaded++;
                continue;
            }
            SpawnPositionResult result = scanColumn(world, zone, type, x, z, minY, maxY, allowNonSolidGround, stats, attempts);
            if (result.success() && !selectedPositions.contains(result.position().get())) return result;
        }
        return SpawnPositionResult.fail("no_valid_position", stats, attempts);
    }

    private static SpawnPositionResult scanColumn(ServerWorld world, Zone zone, EntityType<?> type, int x, int z, int minY, int maxY, boolean allowNonSolidGround, PositionRejectStats stats, int attempts) {
        if (!SpawnMathUtil.isInsideZoneXZ(x, z, zone.getMinX(), zone.getMaxX(), zone.getMinZ(), zone.getMaxZ())) {
            stats.outsideZone++;
            return SpawnPositionResult.fail("outside_zone", stats, attempts);
        }

        for (int y = maxY; y >= minY + 1; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (!isInsideZone(zone, feet)) {
                stats.outsideZone++;
                continue;
            }
            if (isSafeSpawnPosition(world, type, feet, allowNonSolidGround, stats)) {
                stats.validPositions++;
                return SpawnPositionResult.success(feet, stats, attempts);
            }
        }
        return SpawnPositionResult.fail("no_valid_position", stats, attempts);
    }

    private static SpawnPositionResult scanFlatFloorColumn(ServerWorld world, EntityType<?> type, int x, int z, int minY, int maxY, boolean allowNonSolidGround, PositionRejectStats stats, int attempts) {
        for (int floorY = maxY; floorY >= minY; floorY--) {
            BlockPos floor = new BlockPos(x, floorY, z);
            BlockPos feet = floor.up();
            BlockPos head = feet.up();

            if (!isInsideWorldHeight(world, floor) || !isInsideWorldHeight(world, feet) || !isInsideWorldHeight(world, head)) {
                stats.outsideWorldHeight++;
                continue;
            }

            BlockState floorState = world.getBlockState(floor);
            if (!isValidFloor(world, floor, floorState, allowNonSolidGround)) {
                stats.noSolidFloor++;
                continue;
            }

            if (isSafeSpawnPosition(world, type, feet, allowNonSolidGround, stats)) {
                stats.validPositions++;
                return SpawnPositionResult.success(feet, stats, attempts);
            }
        }
        return SpawnPositionResult.fail("no_valid_position", stats, attempts);
    }

    private static boolean isSafeSpawnPosition(ServerWorld world, EntityType<?> type, BlockPos pos, boolean allowNonSolidGround, PositionRejectStats stats) {
        BlockPos head = pos.up();
        BlockPos below = pos.down();

        if (!isInsideWorldHeight(world, pos) || !isInsideWorldHeight(world, head) || !isInsideWorldHeight(world, below)) {
            stats.outsideWorldHeight++;
            return false;
        }

        BlockState feet = world.getBlockState(pos);
        if (!feet.isAir()) {
            stats.feetNotAir++;
            return false;
        }

        BlockState headState = world.getBlockState(head);
        if (!headState.isAir()) {
            stats.headNotAir++;
            return false;
        }

        BlockState belowState = world.getBlockState(below);
        if (!isValidFloor(world, below, belowState, allowNonSolidGround)) {
            stats.noSolidFloor++;
            return false;
        }

        if (type != null) {
            EntityDimensions dimensions = type.getDimensions();
            Box box = dimensions.getBoxAt(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            if (!world.isSpaceEmpty(box)) {
                stats.collision++;
                return false;
            }
        }

        return true;
    }

    private static boolean isValidFloor(ServerWorld world, BlockPos pos, BlockState state, boolean allowNonSolidGround) {
        return SpawnGroundUtil.isValidSpawnGround(state.isSideSolidFullSquare(world, pos, Direction.UP), allowNonSolidGround);
    }

    private static boolean isInsideZone(Zone zone, BlockPos pos) {
        return pos.getX() >= zone.getMinX() && pos.getX() <= zone.getMaxX()
                && pos.getY() >= zone.getMinY() && pos.getY() <= zone.getMaxY()
                && pos.getZ() >= zone.getMinZ() && pos.getZ() <= zone.getMaxZ();
    }

    private static boolean isInsideWorldHeight(ServerWorld world, BlockPos pos) {
        return pos.getY() >= world.getBottomY() && pos.getY() < world.getTopY();
    }

    private static boolean isBatchDistanceAllowed(BlockPos pos, MobRule rule, List<BlockPos> selectedPositions, PositionRejectStats stats, boolean relaxDistance) {
        if (selectedPositions == null || selectedPositions.isEmpty() || rule == null || !rule.spreadSpawns) return true;
        int minDistance = Math.max(0, rule.minDistanceBetweenSpawns);
        int minDistanceSq = minDistance * minDistance;
        for (BlockPos selected : selectedPositions) {
            if (selected.equals(pos)) {
                stats.tooCloseToOtherSpawn++;
                return false;
            }
            if (!relaxDistance && minDistance > 0 && selected.getSquaredDistance(pos) < minDistanceSq) {
                stats.tooCloseToOtherSpawn++;
                return false;
            }
        }
        return true;
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
        return randomBetween(RANDOM, min, max);
    }

    private static int randomBetween(Random random, int min, int max) {
        if (max <= min) return min;
        return min + random.nextInt(max - min + 1);
    }
}
