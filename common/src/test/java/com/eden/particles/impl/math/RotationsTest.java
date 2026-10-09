package com.eden.particles.impl.math;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationsTest {

    @Test
    void lookAtTurnsPlusZTowardTheTargetWithoutRoll() {
        float[][] targets = {{1, 0, 0}, {0, 0, -3}, {2, 2, 1}, {0, -1, 0}, {-1, 5, -2}};
        for (float[] t : targets) {
            Vector3f forward = Rotations.lookAt(t[0], t[1], t[2], new Quaternionf()).transform(new Vector3f(0, 0, 1));
            Vector3f expected = new Vector3f(t[0], t[1], t[2]).normalize();
            assertEquals(expected.x, forward.x, 1e-5);
            assertEquals(expected.y, forward.y, 1e-5);
            assertEquals(expected.z, forward.z, 1e-5);
        }
        Vector3f right = Rotations.lookAt(1, 1, 0, new Quaternionf()).transform(new Vector3f(1, 0, 0));
        assertEquals(0f, right.y, 1e-6, "local x stays horizontal");
        assertEquals(new Quaternionf(), Rotations.lookAt(0, 0, 0, new Quaternionf()));
    }

    @Test
    void yawFollowsMinecraftConvention() {
        // Yaw 90 turns a south-facing (+Z) vector to face west (−X).
        Vector3f v = Rotations.fromYawPitchRoll(90, 0, 0).transform(new Vector3f(0, 0, 1));
        assertVector(-1, 0, 0, v);
    }

    @Test
    void positivePitchLooksDown() {
        Vector3f v = Rotations.fromYawPitchRoll(0, 90, 0).transform(new Vector3f(0, 0, 1));
        assertVector(0, -1, 0, v);
    }

    @Test
    void defaultAxisIsIdentity() {
        Quaternionf q = Rotations.alignYTo(0, 1, 0);
        assertVector(1, 0, 0, q.transform(new Vector3f(1, 0, 0)));
        assertVector(0, 0, 1, q.transform(new Vector3f(0, 0, 1)));
    }

    @Test
    void alignsYOntoAnyAxisIncludingTheOpposite() {
        Random random = new Random(3);
        for (int i = 0; i < 1000; i++) {
            Vector3f axis = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian()).normalize();
            Vector3f mapped = Rotations.alignYTo(axis.x, axis.y, axis.z).transform(new Vector3f(0, 1, 0));
            assertVector(axis.x, axis.y, axis.z, mapped);
        }
        assertVector(0, -1, 0, Rotations.alignYTo(0, -1, 0).transform(new Vector3f(0, 1, 0)));
    }

    private static void assertVector(float x, float y, float z, Vector3f actual) {
        assertEquals(x, actual.x, 1e-5, "x of " + actual);
        assertEquals(y, actual.y, 1e-5, "y of " + actual);
        assertEquals(z, actual.z, 1e-5, "z of " + actual);
    }
}
