package com.eden.particles.impl;

import com.eden.particles.impl.expr.InputNames;

/** Argument checks shared by {@code EffectParams} and the handles' setters. */
public final class ParamChecks {

    private ParamChecks() {
    }

    public static void input(String name, float value) {
        if (name == null || !InputNames.isValid(name)) throw new IllegalArgumentException("invalid input name '" + name + "'");
        if (name.equals(InputNames.DISTANCE)) throw new IllegalArgumentException("'distance' is set by between() anchors");
        if (!Float.isFinite(value)) throw new IllegalArgumentException("input '" + name + "' must be finite, got " + value);
    }

    public static void scale(float scale) {
        if (!(scale > 0f) || !Float.isFinite(scale)) throw new IllegalArgumentException("scale must be positive, got " + scale);
    }

    public static void rotation(float yaw, float pitch, float roll) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch) || !Float.isFinite(roll)) {
            throw new IllegalArgumentException("rotation must be finite, got " + yaw + ", " + pitch + ", " + roll);
        }
    }
}
