package com.eden.particles.impl.math;

/** Maps {@code s ∈ [0,1]} to eased progress with {@code E(0) = 0}, {@code E(1) = 1}. May overshoot in between. */
@FunctionalInterface
public interface Easing {

    double apply(double s);

    default boolean isDiscontinuous() {
        return false;
    }
}
