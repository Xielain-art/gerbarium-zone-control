package com.gerbarium.runtime.access;

import net.minecraft.nbt.NbtCompound;

public interface EntityPersistentDataHolder {
    NbtCompound getPersistentData();
}
