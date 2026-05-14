package com.gerbarium.runtime.util;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.BoundaryMode;
import com.gerbarium.runtime.model.RefillMode;
import com.gerbarium.runtime.model.SpawnType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class RuntimeRuleValidationUtilTest {
    @Test
    void uniqueTimedIsInvalid() {
        MobRule rule = new MobRule();
        rule.spawnType = SpawnType.UNIQUE;
        rule.refillMode = RefillMode.TIMED;

        assertEquals("FAILED_INVALID_RULE_CONFIG", RuntimeRuleValidationUtil.getConfigStatus(rule));
    }

    @Test
    void packAfterDeathIsInvalid() {
        MobRule rule = new MobRule();
        rule.spawnType = SpawnType.PACK;
        rule.refillMode = RefillMode.AFTER_DEATH;

        assertEquals("FAILED_INVALID_RULE_CONFIG", RuntimeRuleValidationUtil.getConfigStatus(rule));
    }

    @Test
    void uniqueAfterDeathConfigPasses() {
        MobRule rule = new MobRule();
        rule.spawnType = SpawnType.UNIQUE;
        rule.refillMode = RefillMode.AFTER_DEATH;

        assertNull(RuntimeRuleValidationUtil.getConfigStatus(rule));
    }

    @Test
    void uniqueOnActivationIsInvalid() {
        MobRule rule = new MobRule();
        rule.spawnType = SpawnType.UNIQUE;
        rule.refillMode = RefillMode.ON_ACTIVATION;

        assertEquals("FAILED_INVALID_RULE_CONFIG", RuntimeRuleValidationUtil.getConfigStatus(rule));
    }

    @Test
    void blankEntityIsInvalid() {
        MobRule rule = new MobRule();
        rule.entity = " ";

        assertEquals("FAILED_INVALID_ENTITY", RuntimeRuleValidationUtil.getEntityStatus(rule));
    }

    @Test
    void validPackActivationConfigPasses() {
        MobRule rule = new MobRule();
        rule.spawnType = SpawnType.PACK;
        rule.refillMode = RefillMode.ON_ACTIVATION;

        assertNull(RuntimeRuleValidationUtil.getConfigStatus(rule));
    }

    @Test
    void defaultBoundaryModeIsLeash() {
        MobRule rule = new MobRule();

        assertEquals(BoundaryMode.LEASH, RuntimeRuleValidationUtil.getBoundaryMode(rule));
        assertEquals(10, rule.boundaryMaxOutsideSeconds);
        assertEquals(40, rule.boundaryCheckIntervalTicks);
        assertEquals(true, rule.boundaryTeleportBack);
    }

    @Test
    void invalidBoundaryModeFailsValidation() {
        MobRule rule = new MobRule();
        rule.boundaryMode = "BAD";

        assertEquals("FAILED_INVALID_BOUNDARY_MODE", RuntimeRuleValidationUtil.getConfigStatus(rule));
        assertEquals("Invalid boundaryMode in zone JSON. Expected NONE, LEASH, TELEPORT_BACK, REMOVE_OUTSIDE.", RuntimeRuleValidationUtil.getBoundaryModeHint(rule));
    }
}
