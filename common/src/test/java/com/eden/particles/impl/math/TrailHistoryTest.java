package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TrailHistoryTest {

    @Test
    void recordsInitialPoint() {
        TrailHistory history = new TrailHistory(5, 0.1f);
        assertEquals(0, history.size());

        history.record(1f, 2f, 3f, 0f);
        assertEquals(1, history.size());
        assertEquals(1f, history.getX(0));
        assertEquals(2f, history.getY(0));
        assertEquals(3f, history.getZ(0));
        assertEquals(0f, history.getTime(0));
    }

    @Test
    void preservesHeadOnSubThresholdDisplacementUntilAccumulated() {
        TrailHistory history = new TrailHistory(5, 0.5f);
        history.record(0f, 0f, 0f, 0f);

        history.record(0.2f, 0f, 0f, 1f);
        assertEquals(1, history.size());
        assertEquals(0f, history.getX(0));
        assertEquals(0f, history.getTime(0));

        history.record(0.6f, 0f, 0f, 2f);
        assertEquals(2, history.size());
        assertEquals(0.6f, history.getX(0));
        assertEquals(2f, history.getTime(0));
        assertEquals(0f, history.getX(1));
    }

    @Test
    void appendsOnSufficientDisplacement() {
        TrailHistory history = new TrailHistory(5, 0.2f);
        history.record(0f, 0f, 0f, 0f);
        history.record(0f, 1f, 0f, 1f);

        assertEquals(2, history.size());
        assertEquals(0f, history.getX(0));
        assertEquals(1f, history.getY(0));
        assertEquals(1f, history.getTime(0));

        assertEquals(0f, history.getX(1));
        assertEquals(0f, history.getY(1));
        assertEquals(0f, history.getTime(1));
    }

    @Test
    void rollsOverAtCapacity() {
        TrailHistory history = new TrailHistory(3, 0.1f);
        history.record(0f, 0f, 0f, 0f);
        history.record(1f, 0f, 0f, 1f);
        history.record(2f, 0f, 0f, 2f);
        assertEquals(3, history.size());

        history.record(3f, 0f, 0f, 3f);
        assertEquals(3, history.size());
        assertEquals(3f, history.getX(0));
        assertEquals(2f, history.getX(1));
        assertEquals(1f, history.getX(2));
    }

    @Test
    void prunesExpiredNodes() {
        TrailHistory history = new TrailHistory(5, 0.1f);
        history.record(0f, 0f, 0f, 10f);
        history.record(1f, 0f, 0f, 15f);
        history.record(2f, 0f, 0f, 20f);
        assertEquals(3, history.size());

        history.pruneOlderThan(25f, 11f);
        assertEquals(2, history.size());
        assertEquals(2f, history.getX(0));
        assertEquals(1f, history.getX(1));

        history.pruneOlderThan(30f, 5f);
        assertEquals(0, history.size());
    }

    @Test
    void validatesCapacityBounds() {
        assertThrows(IllegalArgumentException.class, () -> new TrailHistory(2, 0.1f));
        assertThrows(IllegalArgumentException.class, () -> new TrailHistory(65, 0.1f));
        assertDoesNotThrow(() -> new TrailHistory(3, 0.1f));
        assertDoesNotThrow(() -> new TrailHistory(64, 0.1f));
    }
}
