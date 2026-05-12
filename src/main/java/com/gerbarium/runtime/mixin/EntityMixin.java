package com.gerbarium.runtime.mixin;

import com.gerbarium.runtime.access.EntityPersistentDataHolder;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public class EntityMixin implements EntityPersistentDataHolder {
    @Unique
    private NbtCompound gerbarium$persistentData;

    @Override
    public NbtCompound getPersistentData() {
        if (this.gerbarium$persistentData == null) {
            this.gerbarium$persistentData = new NbtCompound();
        }
        return this.gerbarium$persistentData;
    }

    @Inject(method = "writeNbt", at = @At("TAIL"))
    private void gerbarium$writeCustomData(NbtCompound nbt, CallbackInfoReturnable<NbtCompound> cir) {
        if (this.gerbarium$persistentData != null) {
            nbt.put("GerbariumData", this.gerbarium$persistentData);
        }
    }

    @Inject(method = "readNbt", at = @At("TAIL"))
    private void gerbarium$readCustomData(NbtCompound nbt, CallbackInfo ci) {
        if (nbt.contains("GerbariumData", 10)) {
            this.gerbarium$persistentData = nbt.getCompound("GerbariumData");
        }
    }
}