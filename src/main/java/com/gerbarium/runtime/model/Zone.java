package com.gerbarium.runtime.model;

import java.util.ArrayList;
import java.util.List;

public class Zone {
    public String id;
    public boolean enabled;
    public String dimension;
    public Vec3iJson min;
    public Vec3iJson max;
    public int priority = 0;
    public ZoneActivationSettings activation = new ZoneActivationSettings();
    public ZoneSpawnSettings spawn = new ZoneSpawnSettings();
    public List<MobRule> mobs = new ArrayList<>();
}
