package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.net.AnchorData;
import com.eden.particles.impl.runtime.Anchor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Anchor building and placement for fixed points; entity anchors need a running game. */
class AnchorsTest {

    @Test
    void fixedAnchorsPlaceWithNoTurning() {
        EffectAnchor anchor = EffectAnchor.at(new BlockPos(1, 2, 3)).offset(0, 1, 0);
        assertEquals(new Vec3(1.5, 3, 3.5), anchor.position());
        Anchor.Placement out = new Anchor.Placement();
        assertTrue(AnchorMath.place(anchor, out));
        assertEquals(1.5, out.position.x);
        assertEquals(3.0, out.position.y);
        assertEquals(0f, out.distance);
        assertEquals(new Quaternionf(), out.facing);
        assertTrue(anchor.entity().isEmpty());
        assertTrue(anchor.target().isEmpty());
        assertSame(anchor, anchor.start());
    }

    @Test
    void betweenAimsPlusZAtTheFarEndAndMeasuresDistance() {
        EffectAnchor beam = EffectAnchor.between(EffectAnchor.at(0, 64, 0), EffectAnchor.at(3, 68, 0));
        Anchor.Placement out = new Anchor.Placement();
        assertTrue(AnchorMath.place(beam, out));
        assertEquals(5f, out.distance, 1e-6);
        assertEquals(new Vec3(0, 64, 0), beam.position());
        Vector3f forward = out.facing.transform(new Vector3f(0, 0, 1));
        assertEquals(0.6f, forward.x, 1e-6);
        assertEquals(0.8f, forward.y, 1e-6);
        assertEquals(0f, forward.z, 1e-6);
        assertTrue(beam.start().target().isEmpty());
        assertEquals(new Vec3(3, 68, 0), beam.target().orElseThrow().position());
    }

    @Test
    void misuseIsRejected() {
        EffectAnchor fixed = EffectAnchor.at(0, 0, 0);
        EffectAnchor beam = EffectAnchor.between(fixed, fixed);
        assertThrows(IllegalStateException.class, fixed::eyes);
        assertThrows(IllegalStateException.class, () -> fixed.turning(EffectAnchor.Turning.LOOK));
        assertThrows(IllegalStateException.class, () -> beam.offset(1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> EffectAnchor.between(beam, fixed));
    }

    @Test
    void wireAnchorsOfFixedPointsComeBackFixed() {
        AnchorData data = AnchorData.of(EffectAnchor.at(4, 5, 6));
        assertEquals(new AnchorData(new Vec3(4, 5, 6), -1, Vec3.ZERO, false, EffectAnchor.Turning.NONE), data);
        assertEquals(new Vec3(4, 5, 6), data.toAnchor(null).position());
    }

    @Test
    void commandInputsParse() {
        EffectParams params = InputArgs.apply(" power=2, spread=-0.5  heat=1e1", EffectParams.builder()).build();
        assertEquals(Map.of("power", 2f, "spread", -0.5f, "heat", 10f), params.inputs());
        assertTrue(InputArgs.apply("", EffectParams.builder()).build().inputs().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> InputArgs.apply("power", EffectParams.builder()));
        assertThrows(IllegalArgumentException.class, () -> InputArgs.apply("power=", EffectParams.builder()));
        assertThrows(IllegalArgumentException.class, () -> InputArgs.apply("=2", EffectParams.builder()));
        assertThrows(IllegalArgumentException.class, () -> InputArgs.apply("power=high", EffectParams.builder()));
    }

    @Test
    void paramChecksRejectBadValues() {
        assertThrows(IllegalArgumentException.class, () -> ParamChecks.input(null, 1));
        assertThrows(IllegalArgumentException.class, () -> ParamChecks.scale(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> ParamChecks.rotation(0, Float.NaN, 0));
        ParamChecks.rotation(90, -10, 5);
        EffectParams params = EffectParams.builder().rotation(10, 20, 30).scale(2).range(10).persistent().build();
        assertEquals(10f, params.yaw());
        assertEquals(20f, params.pitch());
        assertEquals(30f, params.roll());
        assertEquals(2f, params.scale());
        assertEquals(10f, params.range());
        assertTrue(params.persistent());
    }
}
