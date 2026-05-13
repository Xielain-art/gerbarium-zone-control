package com.gerbarium.runtime.model;

public enum BoundaryMode {
    NONE,
    LEASH,
    TELEPORT_BACK,
    REMOVE_OUTSIDE;

    public static BoundaryMode from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        for (BoundaryMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }

        return null;
    }
}
