package com.eden.particles.impl;

import com.eden.particles.api.EffectParams;

/** Parses command input arguments like {@code power=2 spread=0.5} into params. */
public final class InputArgs {

    private InputArgs() {
    }

    /** @throws IllegalArgumentException naming the bad part */
    public static EffectParams.Builder apply(String text, EffectParams.Builder params) {
        for (String part : text.trim().split("[\\s,]+")) {
            if (part.isEmpty()) continue;
            int eq = part.indexOf('=');
            if (eq <= 0 || eq == part.length() - 1) throw new IllegalArgumentException("expected name=value, got '" + part + "'");
            String name = part.substring(0, eq);
            float value;
            try {
                value = Float.parseFloat(part.substring(eq + 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + part.substring(eq + 1) + "' is not a number");
            }
            params.input(name, value);
        }
        return params;
    }
}
