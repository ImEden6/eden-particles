package com.eden.particles.impl.runtime;

/** A template's input-dependent values, evaluated before spawning and kept by each particle for its life. */
public final class TemplateValues {

    /** Multipliers on the size, alpha and speed curves. */
    public float size = 1f;
    public float alpha = 1f;
    public float speed = 1f;
    public float gravity;
    public float drag = 0.98f;
}
