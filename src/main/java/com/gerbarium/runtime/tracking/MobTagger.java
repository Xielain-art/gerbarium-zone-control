package com.gerbarium.runtime.tracking;

import com.gerbarium.runtime.mixin.EntityPersistentDataHolder;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;

import java.util.Optional;

public class MobTagger {
    public static final String TAG_MANAGED = "gerbarium_managed";
    public static final String TAG_ZONE_ID = "gerbarium_zone_id";
    public static final String TAG_RULE_ID = "gerbarium_rule_id";
    public static final String TAG_MOB_ROLE = "gerbarium_mob_role";
    public static final String TAG_FORCED = "gerbarium_forced";
    public static final String TAG_PARENT_RULE_ID = "gerbarium_parent_rule_id";
    public static final String TAG_COMPANION_ID = "gerbarium_companion_id";
    public static final String TAG_CLEANUP = "gerbarium_cleanup_active";

    public static void tagPrimary(Entity entity, String zoneId, String ruleId, boolean forced) {
        NbtCompound nbt = ((EntityPersistentDataHolder) entity).getPersistentData();
        nbt.putBoolean(TAG_MANAGED, true);
        nbt.putString(TAG_ZONE_ID, zoneId);
        nbt.putString(TAG_RULE_ID, ruleId);
        nbt.putString(TAG_MOB_ROLE, "PRIMARY");
        nbt.putBoolean(TAG_FORCED, forced);
    }

    public static void tagCompanion(Entity entity, String zoneId, String parentRuleId, String companionId, boolean forced) {
        NbtCompound nbt = ((EntityPersistentDataHolder) entity).getPersistentData();
        nbt.putBoolean(TAG_MANAGED, true);
        nbt.putString(TAG_ZONE_ID, zoneId);
        nbt.putString(TAG_RULE_ID, parentRuleId); // Rule ID for companions is parent rule ID
        nbt.putString(TAG_MOB_ROLE, "COMPANION");
        nbt.putBoolean(TAG_FORCED, forced);
        nbt.putString(TAG_PARENT_RULE_ID, parentRuleId);
        nbt.putString(TAG_COMPANION_ID, companionId);
    }

    public static Optional<ManagedMobInfo> getInfo(Entity entity) {
        NbtCompound nbt = ((EntityPersistentDataHolder) entity).getPersistentData();
        if (!nbt.getBoolean(TAG_MANAGED)) {
            return Optional.empty();
        }

        return Optional.of(new ManagedMobInfo(
                nbt.getString(TAG_ZONE_ID),
                nbt.getString(TAG_RULE_ID),
                nbt.getString(TAG_MOB_ROLE),
                nbt.getBoolean(TAG_FORCED)
        ));
    }
}