package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.Force;
import com.eden.particles.impl.expr.InputNames;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcesTest {

    private static final double EPS = 1e-6;

    private static BakedForces bake(String json) {
        return BakedForces.bake(Force.LIST.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow());
    }

    private static ParticleForces particle(String json, EffectFrame frame, float scale) {
        BakedForces forces = bake(json);
        return new ParticleForces(forces, forces.evaluate(new float[InputNames.count()]), frame, scale);
    }

    private static ParticleForces particle(String json) {
        return particle(json, new EffectFrame(1), 1f);
    }

    private static void assertAccel(double x, double y, double z, ParticleForces forces) {
        assertEquals(x, forces.ax, EPS, "ax");
        assertEquals(y, forces.ay, EPS, "ay");
        assertEquals(z, forces.az, EPS, "az");
    }

    @Test
    void attractorPullsTowardItsPoint() {
        ParticleForces forces = particle("[{\"type\": \"attractor\", \"strength\": 0.1}]");
        assertTrue(forces.accelerate(3, 0, 0, 0, 0f));
        assertAccel(-0.1, 0, 0, forces);
        forces = particle("[{\"type\": \"attractor\", \"strength\": -0.1}]");
        forces.accelerate(0, 0, 2, 0, 0f);
        assertAccel(0, 0, 0.1, forces);
    }

    @Test
    void falloffs() {
        ParticleForces linear = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"falloff\": \"linear\", \"radius\": 4}]");
        linear.accelerate(2, 0, 0, 0, 0f);
        assertAccel(-0.05, 0, 0, linear);
        linear.accelerate(5, 0, 0, 0, 0f);
        assertAccel(0, 0, 0, linear);

        ParticleForces square = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"falloff\": \"inverse_square\"}]");
        square.accelerate(2, 0, 0, 0, 0f);
        assertAccel(-0.025, 0, 0, square);
        square.accelerate(0.1, 0, 0, 0, 0f);
        assertAccel(-0.4, 0, 0, square);

        ParticleForces cut = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"radius\": 1}]");
        cut.accelerate(0.5, 0, 0, 0, 0f);
        assertAccel(-0.1, 0, 0, cut);
        cut.accelerate(1.5, 0, 0, 0, 0f);
        assertAccel(0, 0, 0, cut);
    }

    @Test
    void absorbEndsTheParticle() {
        ParticleForces forces = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"absorb\": 0.5}]");
        assertTrue(forces.accelerate(0.6, 0, 0, 0, 0f));
        assertFalse(forces.accelerate(0.4, 0, 0, 0, 0f));
    }

    @Test
    void effectScaleScalesStrengthAndLengths() {
        ParticleForces forces = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"falloff\": \"linear\", \"radius\": 4, "
                + "\"point\": [1, 0, 0]}]", new EffectFrame(1), 2f);
        forces.accelerate(6, 0, 0, 0, 0f);
        // Point at x=2, radius 8: half way out, at twice the strength.
        assertAccel(-0.1, 0, 0, forces);
    }

    @Test
    void pointsFollowTheFrame() {
        EffectFrame frame = new EffectFrame(1);
        ParticleForces offset = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"point\": [1, 0, 0]}]", frame, 1f);
        frame.position.set(10, 0, 0);
        frame.facing.rotationY((float) Math.toRadians(90));
        // +X turned 90° about Y points along -Z.
        offset.accelerate(10, 0, 3, 0, 0f);
        assertAccel(0, 0, -0.1, offset);

        ParticleForces target = particle("[{\"type\": \"attractor\", \"strength\": 0.1, \"point\": \"target\"}]", frame, 1f);
        frame.target.set(0, 5, 0);
        target.accelerate(0, 0, 0, 0, 0f);
        assertAccel(0, 0.1, 0, target);
        frame.target.set(0, -5, 0);
        target.accelerate(0, 0, 0, 0, 0f);
        assertAccel(0, -0.1, 0, target);
    }

    @Test
    void vortexSpinsPullsAndLifts() {
        ParticleForces spin = particle("[{\"type\": \"vortex\", \"strength\": 0.1}]");
        spin.accelerate(2, 7, 0, 0, 0f);
        // Counter-clockwise seen from above: +X turns toward -Z.
        assertAccel(0, 0, -0.1, spin);

        ParticleForces all = particle("[{\"type\": \"vortex\", \"strength\": 0.1, \"pull\": 0.05, \"lift\": 0.02}]");
        all.accelerate(2, 0, 0, 0, 0f);
        assertAccel(-0.05, 0.02, -0.1, all);

        ParticleForces sideways = particle("[{\"type\": \"vortex\", \"strength\": 0.1, \"axis\": [0, 0, 5]}]");
        sideways.accelerate(2, 0, 9, 0, 0f);
        assertAccel(0, 0.1, 0, sideways);

        ParticleForces limited = particle("[{\"type\": \"vortex\", \"strength\": 0.1, \"lift\": 1, \"radius\": 1}]");
        limited.accelerate(2, 0, 0, 0, 0f);
        assertAccel(0, 0, 0, limited);
    }

    @Test
    void strengthFollowsItsCurveOverLife() {
        ParticleForces forces = particle("[{\"type\": \"attractor\", \"strength\": [[0, 0], [1, 0.2]]}]");
        forces.accelerate(1, 0, 0, 0, 0.5f);
        assertEquals(-0.1, forces.ax, 1e-3);
        forces.accelerate(1, 0, 0, 0, 0f);
        assertEquals(0, forces.ax, 1e-6);
    }

    @Test
    void valuesComeFromInputsWhenTheyNeedThem() {
        BakedForces constant = bake("[{\"type\": \"attractor\", \"strength\": 0.1}]");
        float[] inputs = new float[InputNames.count() + 1];
        assertSame(constant.evaluate(inputs), constant.evaluate(inputs));

        BakedForces live = bake("[{\"type\": \"attractor\", \"strength\": \"distance * 2\"}]");
        inputs[InputNames.DISTANCE_ID] = 0.05f;
        ParticleForces forces = new ParticleForces(live, live.evaluate(inputs), new EffectFrame(1), 1f);
        forces.accelerate(1, 0, 0, 0, 0f);
        assertAccel(-0.1, 0, 0, forces);
    }

    @Test
    void turbulenceIsSeededAndDrifts() {
        String json = "[{\"type\": \"turbulence\", \"strength\": 0.05, \"scale\": 2, \"flow\": 0.1}]";
        ParticleForces a = particle(json, new EffectFrame(7), 1f);
        ParticleForces same = particle(json, new EffectFrame(7), 1f);
        ParticleForces other = particle(json, new EffectFrame(8), 1f);
        a.accelerate(1, 2, 3, 0, 0f);
        same.accelerate(1, 2, 3, 0, 0f);
        other.accelerate(1, 2, 3, 0, 0f);
        assertAccel(a.ax, a.ay, a.az, same);
        assertNotEquals(a.ax, other.ax);

        double before = a.ax;
        a.accelerate(1, 2, 3, 40, 0f);
        assertNotEquals(before, a.ax, "flow moves the field over time");

        double sum = 0;
        for (int i = 0; i < 2000; i++) {
            a.accelerate(i * 0.37, i * 0.11, i * -0.23, 0, 0f);
            sum += Math.sqrt(a.ax * a.ax + a.ay * a.ay + a.az * a.az);
        }
        assertEquals(0.05, sum / 2000, 0.01, "strength is about the mean push");
    }
}
