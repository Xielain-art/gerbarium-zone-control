package com.gerbarium.runtime.client.dto;

import java.util.ArrayList;
import java.util.List;

public class RuntimeEventsDto {
    public String zoneId = "";
    public String ruleId = "";
    public List<RuntimeEventDto> events = new ArrayList<>();
}
