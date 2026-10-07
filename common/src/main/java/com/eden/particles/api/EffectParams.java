package com.eden.particles.api;

import java.util.OptionalLong;

/**
 * Per-play adjustments. Immutable; start from {@link #DEFAULT} or {@link #builder()}.
 * Rotation uses Minecraft's conventions: yaw 0 faces south, positive pitch looks down.
 */
public final class EffectParams {

    public static final float DEFAULT_RANGE = 64f;
    public static final EffectParams DEFAULT = builder().build();

    private final float scale;
    private final float yaw;
    private final float pitch;
    private final float roll;
    private final int tint;
    private final OptionalLong seed;
    private final float range;
    private final boolean persistent;

    private EffectParams(Builder builder) {
        this.scale = builder.scale;
        this.yaw = builder.yaw;
        this.pitch = builder.pitch;
        this.roll = builder.roll;
        this.tint = builder.tint;
        this.seed = builder.seed;
        this.range = builder.range;
        this.persistent = builder.persistent;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Multiplies distances, speeds, sizes and gravity. */
    public float scale() {
        return scale;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }

    public float roll() {
        return roll;
    }

    /** {@code 0xRRGGBB} multiplied into every particle's colour. */
    public int tint() {
        return tint;
    }

    /** Fixes the random pattern; when empty a random seed is chosen, shared by every client that sees the effect. */
    public OptionalLong seed() {
        return seed;
    }

    /** Server only: players within this many blocks of the anchor receive the effect. */
    public float range() {
        return range;
    }

    /** @see Builder#persistent() */
    public boolean persistent() {
        return persistent;
    }

    public static final class Builder {
        private float scale = 1f;
        private float yaw;
        private float pitch;
        private float roll;
        private int tint = 0xFFFFFF;
        private OptionalLong seed = OptionalLong.empty();
        private float range = DEFAULT_RANGE;
        private boolean persistent;

        private Builder() {
        }

        public Builder scale(float scale) {
            if (!(scale > 0f) || !Float.isFinite(scale)) throw new IllegalArgumentException("scale must be positive, got " + scale);
            this.scale = scale;
            return this;
        }

        /** Degrees. */
        public Builder rotation(float yaw, float pitch, float roll) {
            this.yaw = yaw;
            this.pitch = pitch;
            this.roll = roll;
            return this;
        }

        public Builder tint(int rgb) {
            this.tint = rgb & 0xFFFFFF;
            return this;
        }

        public Builder seed(long seed) {
            this.seed = OptionalLong.of(seed);
            return this;
        }

        public Builder range(float range) {
            if (!(range > 0f)) throw new IllegalArgumentException("range must be positive, got " + range);
            this.range = range;
            return this;
        }

        /**
         * Server only, for looping effects: the server keeps re-sending the effect to players who come into range,
         * joined mid-loop, until {@link EffectHandle#stop()} is called or the anchor entity is removed. Ignored by
         * {@link EdenParticles#playFor}.
         */
        public Builder persistent() {
            this.persistent = true;
            return this;
        }

        public EffectParams build() {
            return new EffectParams(this);
        }
    }
}
