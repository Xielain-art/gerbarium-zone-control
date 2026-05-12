package com.gerbarium.runtime.mixin;

import net.minecraft.nbt.NbtCompound;

public interface EntityPersistentDataHolder {
    NbtCompound getPersistentData();
}