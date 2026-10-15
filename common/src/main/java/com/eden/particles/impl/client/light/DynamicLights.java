package com.eden.particles.impl.client.light;

import com.eden.particles.impl.runtime.LightSink;

/**
 * Where effect lights go: a dynamic-lights mod's backend once one has registered, else nowhere. At most
 * {@link #MAX_LIGHTS} are lit at once, since each one relights the blocks around it as it moves.
 */
public final class DynamicLights implements LightSink {

    public static final DynamicLights INSTANCE = new DynamicLights();
    static final int MAX_LIGHTS = 32;

    private volatile LightSink backend;
    private int lit;

    private DynamicLights() {
    }

    void install(LightSink backend) {
        this.backend = backend;
    }

    public boolean isAvailable() {
        return backend != null;
    }

    @Override
    public Light start(double x, double y, double z, float level) {
        LightSink sink = backend;
        if (sink == null || lit >= MAX_LIGHTS) return null;
        Light light = sink.start(x, y, z, level);
        if (light == null) return null;
        lit++;
        return new Light() {
            private boolean ended;

            @Override
            public void update(double x, double y, double z, float level) {
                light.update(x, y, z, level);
            }

            @Override
            public void end() {
                if (ended) return;
                ended = true;
                lit--;
                light.end();
            }
        };
    }
}
