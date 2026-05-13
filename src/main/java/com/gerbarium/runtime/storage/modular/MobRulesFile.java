package com.gerbarium.runtime.storage.modular;

import com.gerbarium.runtime.model.MobRule;
import com.gerbarium.runtime.model.ZoneSpawnSettings;

import java.util.ArrayList;
import java.util.List;

public class MobRulesFile {
    public int version;
    public String zoneId;
    public ZoneSpawnSettings spawn;
    public List<MobRule> rules = new ArrayList<>();
}