package com.eden.particles.impl.runtime;

/** Places an effect's dynamic lights. Positions are world-space; levels are block light, 0 to 15. */
@FunctionalInterface
public interface LightSink {

    /** @return the light, or null if it wasn't placed */
    Light start(double x, double y, double z, float level);

    interface Light {

        void update(double x, double y, double z, float level);

        void end();
    }
}
