package com.eden.particles.impl.expr;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Interns input names to small ids shared by every effect, so expressions read inputs by index. Ids are never
 * reused; the table only grows with distinct names.
 */
public final class InputNames {

    /** Built in: length of a two-point anchor, 0 otherwise. */
    public static final String DISTANCE = "distance";
    public static final int DISTANCE_ID;

    private static final Pattern VALID = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Map<String, Integer> IDS = new HashMap<>();

    static {
        DISTANCE_ID = id(DISTANCE);
    }

    private InputNames() {
    }

    public static synchronized int id(String name) {
        return IDS.computeIfAbsent(name, n -> IDS.size());
    }

    public static synchronized int count() {
        return IDS.size();
    }

    public static boolean isValid(String name) {
        return VALID.matcher(name).matches();
    }
}
