package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.model.Zone;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ZoneRepository {
    private static final Map<String, Zone> zones = new ConcurrentHashMap<>();

    public static Collection<Zone> getAll() {
        return Collections.unmodifiableCollection(zones.values());
    }

    public static Optional<Zone> getById(String id) {
        return Optional.ofNullable(zones.get(id));
    }

    public static Collection<Zone> getEnabledZones() {
        return zones.values().stream()
                .filter(z -> z.enabled)
                .collect(Collectors.toList());
    }

    public static Collection<Zone> getEnabledZonesForDimension(RegistryKey<World> dimension) {
        String dimensionStr = dimension.getValue().toString();
        return zones.values().stream()
                .filter(z -> z.enabled && z.dimension.equals(dimensionStr))
                .collect(Collectors.toList());
    }

    public static void replaceAll(Collection<Zone> newZones) {
        zones.clear();
        for (Zone zone : newZones) {
            zones.put(zone.id, zone);
        }
    }
}