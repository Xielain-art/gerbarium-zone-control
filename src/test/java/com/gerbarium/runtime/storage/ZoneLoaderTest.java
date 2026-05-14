package com.gerbarium.runtime.storage;

import com.gerbarium.runtime.model.Vec3iJson;
import com.gerbarium.runtime.storage.modular.ZoneBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoneLoaderTest {
    @Test
    void detectsInvertedBoundsFromRawBaseCoordinates() {
        ZoneBase base = new ZoneBase();
        base.min = new Vec3iJson(10, 20, 30);
        base.max = new Vec3iJson(1, 19, 29);

        assertTrue(ZoneBoundsUtil.hasInvertedBounds(base));
    }

    @Test
    void acceptsOrderedBounds() {
        ZoneBase base = new ZoneBase();
        base.min = new Vec3iJson(1, 2, 3);
        base.max = new Vec3iJson(10, 20, 30);

        assertFalse(ZoneBoundsUtil.hasInvertedBounds(base));
    }
}
