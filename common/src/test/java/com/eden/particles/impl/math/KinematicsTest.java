package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KinematicsTest {

    /** Replays vanilla's per-tick order for one axis: accelerate, move, apply friction. */
    private static double[] simulate(double v0, double acceleration, double friction, int ticks) {
        double x = 0.0;
        double v = v0;
        for (int i = 0; i < ticks; i++) {
            v += acceleration;
            x += v;
            v *= friction;
        }
        return new double[]{x, v};
    }

    @Test
    void closedFormDistanceMatchesSimulation() {
        for (double d : new double[]{0.5, 0.85, 0.98, 1.0}) {
            for (int ticks : new int[]{1, 7, 40}) {
                assertEquals(simulate(0.3, 0, d, ticks)[0], Kinematics.distanceAfter(0.3, d, ticks), 1e-9, "d=" + d + " n=" + ticks);
            }
        }
    }

    @Test
    void speedForDistanceCoversThatDistanceOverTheLifetime() {
        for (double d : new double[]{0.6, 0.92, 0.98, 1.0}) {
            double v0 = Kinematics.speedForDistance(1.5, d, 20);
            assertEquals(1.5, simulate(v0, 0, d, 20)[0], 1e-9, "d=" + d);
        }
    }

    @Test
    void terminalVelocityIsTheFixedPoint() {
        double a = -Kinematics.GRAVITY_PER_TICK;
        double d = 0.98;
        double[] state = simulate(0, a, d, 5000);
        assertEquals(Kinematics.terminalVelocity(a, d), state[1], 1e-9);
        double[] next = simulate(state[1], a, d, 1);
        assertEquals(Kinematics.terminalDisplacementPerTick(a, d), next[0], 1e-9);
    }

    @Test
    void dampingRateConversion() {
        // Damping at λ per second over one second (20 ticks) leaves e^(−λ) of the speed.
        double d = Kinematics.frictionFromDampingRate(2.0);
        assertEquals(Math.exp(-2.0), Math.pow(d, 20), 1e-12);
    }

    @Test
    void rejectsInvalidFriction() {
        assertThrows(IllegalArgumentException.class, () -> Kinematics.distanceAfter(1, 0, 5));
        assertThrows(IllegalArgumentException.class, () -> Kinematics.distanceAfter(1, 1.2, 5));
    }
}
