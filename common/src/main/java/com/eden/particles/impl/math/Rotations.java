package com.eden.particles.impl.math;

import org.joml.Quaternionf;

/** Minecraft conventions: yaw 0 faces +Z, yaw 90 faces −X, positive pitch looks down. */
public final class Rotations {

    private Rotations() {
    }

    /** {@code Ry(−yaw) · Rx(pitch) · Rz(roll)} */
    public static Quaternionf fromYawPitchRoll(float yawDegrees, float pitchDegrees, float rollDegrees) {
        return new Quaternionf()
                .rotateY((float) Math.toRadians(-yawDegrees))
                .rotateX((float) Math.toRadians(pitchDegrees))
                .rotateZ((float) Math.toRadians(rollDegrees));
    }

    /** Shortest-arc rotation from +Y, so the default axis is an exact identity. */
    public static Quaternionf alignYTo(float x, float y, float z) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        if (length < 1e-6f) {
            throw new IllegalArgumentException("Axis must be non-zero");
        }
        return new Quaternionf().rotationTo(0f, 1f, 0f, x / length, y / length, z / length);
    }
}
