package com.gerbarium.runtime.model;

import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.List;

public class Zone {
    public String id;
    public String name;
    public boolean enabled = true;
    public String dimension;
    public Vec3iJson min;
    public Vec3iJson max;
    public int priority = 0;
    public ZoneActivationSettings activation = new ZoneActivationSettings();
    public ZoneSpawnSettings spawn = new ZoneSpawnSettings();
    public List<MobRule> mobs = new ArrayList<>();

    public int getMinX() { return Math.min(min.x, max.x); }
    public int getMaxX() { return Math.max(min.x, max.x); }
    public int getMinY() { return Math.min(min.y, max.y); }
    public int getMaxY() { return Math.max(min.y, max.y); }
    public int getMinZ() { return Math.min(min.z, max.z); }
    public int getMaxZ() { return Math.max(min.z, max.z); }

    public Box getZoneBox() {
        return new Box(
                getMinX(), getMinY(), getMinZ(),
                getMaxX() + 1, getMaxY() + 1, getMaxZ() + 1
        );
    }

    public Box getExpandedBox(double expansion) {
        return getZoneBox().expand(expansion);
    }

    public void normalize() {
        if (mobs == null) {
            mobs = new ArrayList<>();
        }
        for (MobRule rule : mobs) {
            if (rule != null) {
                rule.normalize();
            }
        }
        if (activation == null) {
            activation = new ZoneActivationSettings();
        }
        if (spawn == null) {
            spawn = new ZoneSpawnSettings();
        }
        if (dimension == null) {
            dimension = "";
        }
    }
}