package com.eden.particles.impl.runtime;

import com.eden.particles.impl.definition.Trigger;
import com.eden.particles.impl.expr.Expr;

/** A template's input-dependent values, evaluated before spawning and kept by each particle for its life. */
public final class TemplateValues {

    /** Multipliers on the size, alpha and speed curves. */
    public float size = 1f;
    public float alpha = 1f;
    public float speed = 1f;
    public float gravity;
    public float drag = 0.98f;
    /** Trigger chances and input values; a new array each evaluation, so particles can keep them. */
    public float deathChance;
    public float landChance;
    public float[] deathInputs = EMPTY;
    public float[] landInputs = EMPTY;
    /** See {@link BakedForces#evaluate}. */
    public float[] forces = EMPTY;

    private static final float[] EMPTY = new float[0];

    static float[] evaluate(Trigger trigger, float[] inputs) {
        if (trigger.inputs().isEmpty()) return EMPTY;
        float[] values = new float[trigger.inputs().size()];
        int i = 0;
        for (Expr expr : trigger.inputs().values()) values[i++] = expr.eval(inputs);
        return values;
    }
}
