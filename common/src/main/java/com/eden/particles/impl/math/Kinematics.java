package com.eden.particles.impl.math;

/**
 * Closed forms for vanilla {@code Particle.tick()}: {@code v_y −= 0.04·g; x += v; v *= d}. A particle of lifetime
 * {@code L} moves on {@code L} ticks.
 */
public final class Kinematics {

    public static final double GRAVITY_PER_TICK = 0.04;

    private Kinematics() {
    }

    /** {@code v₀(1 − dⁿ)/(1 − d)}, no gravity. */
    public static double distanceAfter(double initialSpeed, double friction, int ticks) {
        checkFriction(friction);
        if (friction == 1.0) {
            return initialSpeed * ticks;
        }
        return initialSpeed * (1.0 - Math.pow(friction, ticks)) / (1.0 - friction);
    }

    /** {@code v₀ = D(1 − d)/(1 − dᴸ)}, no gravity. */
    public static double speedForDistance(double distance, double friction, int ticks) {
        checkFriction(friction);
        if (ticks <= 0) {
            throw new IllegalArgumentException("ticks must be positive");
        }
        if (friction == 1.0) {
            return distance / ticks;
        }
        return distance * (1.0 - friction) / (1.0 - Math.pow(friction, ticks));
    }

    /** Fixed point of {@code v = d(v + a)}: {@code a·d/(1 − d)}. */
    public static double terminalVelocity(double acceleration, double friction) {
        checkFriction(friction);
        return acceleration * friction / (1.0 - friction);
    }

    /** {@code a/(1 − d)} */
    public static double terminalDisplacementPerTick(double acceleration, double friction) {
        checkFriction(friction);
        return acceleration / (1.0 - friction);
    }

    /** {@code d = e^(−λ/20)} for damping rate λ per second. */
    public static double frictionFromDampingRate(double ratePerSecond) {
        return Math.exp(-ratePerSecond / 20.0);
    }

    private static void checkFriction(double friction) {
        if (!(friction > 0.0 && friction <= 1.0)) {
            throw new IllegalArgumentException("friction must be in (0, 1], got " + friction);
        }
    }
}
