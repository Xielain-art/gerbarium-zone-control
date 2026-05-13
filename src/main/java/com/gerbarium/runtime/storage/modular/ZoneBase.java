package com.gerbarium.runtime.storage.modular;

import com.gerbarium.runtime.model.Vec3iJson;
import com.gerbarium.runtime.model.ZoneActivationSettings;

public class ZoneBase {
    public int version;
    public String id;
    public String name;
    public boolean enabled = true;
    public String dimension;
    public Vec3iJson min;
    public Vec3iJson max;
    public ZoneActivationSettings activation = new ZoneActivationSettings();
}