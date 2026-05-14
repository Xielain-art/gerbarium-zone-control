package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.storage.modular.ZoneBase;

final class ZoneBoundsUtil {
    private ZoneBoundsUtil() {
    }

    static boolean hasInvertedBounds(ZoneBase base) {
        return base != null
                && base.min != null
                && base.max != null
                && (base.min.x > base.max.x
                || base.min.y > base.max.y
                || base.min.z > base.max.z);
    }
}
