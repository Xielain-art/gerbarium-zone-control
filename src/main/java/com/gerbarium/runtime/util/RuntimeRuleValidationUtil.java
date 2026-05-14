package com.gerbarium.runtime.util;

import com.gerbarium.runtime.model.BoundaryMode;
import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.model.SpawnType;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public final class RuntimeRuleValidationUtil {
    private RuntimeRuleValidationUtil() {
    }

    public static String getConfigStatus(MobRule rule) {
        if (rule == null) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (rule.spawnType == null || rule.refillMode == null) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (rule.spawnType == SpawnType.UNIQUE && rule.refillMode == RefillMode.TIMED) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (rule.spawnType == SpawnType.UNIQUE && rule.refillMode == RefillMode.ON_ACTIVATION) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (rule.spawnType == SpawnType.PACK && rule.refillMode == RefillMode.AFTER_DEATH) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (rule.maxAlive <= 0 || rule.spawnCount <= 0 || rule.respawnSeconds < 0 || rule.chance < 0.0D || rule.chance > 1.0D) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (getBoundaryMode(rule) == null) {
            return "FAILED_INVALID_BOUNDARY_MODE";
        }

        if (rule.boundaryMaxOutsideSeconds < 0) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        if (rule.boundaryCheckIntervalTicks <= 0) {
            return "FAILED_INVALID_RULE_CONFIG";
        }

        return null;
    }

    public static String getEntityStatus(MobRule rule) {
        if (rule == null || rule.entity == null || rule.entity.isBlank()) {
            return "FAILED_INVALID_ENTITY";
        }

        Identifier identifier = Identifier.tryParse(rule.entity);
        if (identifier == null || Registries.ENTITY_TYPE.getOrEmpty(identifier).isEmpty()) {
            return "FAILED_INVALID_ENTITY";
        }

        return null;
    }

    public static BoundaryMode getBoundaryMode(MobRule rule) {
        return rule == null ? null : BoundaryMode.from(rule.boundaryMode);
    }

    public static String getBoundaryModeHint(MobRule rule) {
        if (rule == null) {
            return null;
        }
        return getBoundaryMode(rule) == null ? "Invalid boundaryMode in zone JSON. Expected NONE, LEASH, TELEPORT_BACK, REMOVE_OUTSIDE." : null;
    }

    public static String getBoundaryIntervalWarning(MobRule rule) {
        if (rule == null) {
            return null;
        }
        return rule.boundaryCheckIntervalTicks < 20 ? "Boundary check interval below the recommended 20 ticks." : null;
    }
}
