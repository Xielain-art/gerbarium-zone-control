package com.gerbarium.runtime.util;

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
}
