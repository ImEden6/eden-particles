package com.eden.particles.api;

/** A playing effect. */
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
}
