package com.eden.particles.api;

/**
 * A playing effect. The setters change it while it plays: new particles use the new values, particles already
 * spawned keep theirs. On a server handle, changes are sent to every recipient at the end of the tick.
 */
public interface EffectHandle {

    /** Returned when nothing was started, for example an unknown effect id or no players in range. */
    EffectHandle NONE = new EffectHandle() {
        @Override
        public void stop() {
        }

        @Override
        public boolean isAlive() {
            return false;
        }
    };

    /** Stops spawning new particles; particles already spawned finish their lives. */
    void stop();

    /**
     * On the client: whether the effect is still playing. On the server, which can't know an effect's duration,
     * this stays true until {@link #stop()} is called or the anchor entity is removed.
     */
    boolean isAlive();

    /** Sets an input the effect declares. On the client, names the effect doesn't declare are ignored with a warning. */
    default void setInput(String name, float value) {
    }

    /** {@code 0xRRGGBB} multiplied into new particles' colour. */
    default void setTint(int rgb) {
    }

    /** Multiplies distances, speeds, sizes and gravity of new particles. */
    default void setScale(float scale) {
    }

    /** Degrees, as in {@link EffectParams.Builder#rotation}. With a turning anchor, relative to the anchor's facing. */
    default void setRotation(float yaw, float pitch, float roll) {
    }
}
