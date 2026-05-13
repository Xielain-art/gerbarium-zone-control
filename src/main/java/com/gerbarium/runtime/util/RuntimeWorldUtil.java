package com.gerbarium.runtime.util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Optional;

public final class RuntimeWorldUtil {
    private RuntimeWorldUtil() {
    }

    public static Optional<ServerWorld> getWorld(MinecraftServer server, String dimensionId) {
        if (server == null || dimensionId == null || dimensionId.isBlank()) {
            return Optional.empty();
        }

        Identifier identifier = Identifier.tryParse(dimensionId);
        if (identifier == null) {
            return Optional.empty();
        }

        RegistryKey<World> key = RegistryKey.of(RegistryKeys.WORLD, identifier);
        return Optional.ofNullable(server.getWorld(key));
    }
}
