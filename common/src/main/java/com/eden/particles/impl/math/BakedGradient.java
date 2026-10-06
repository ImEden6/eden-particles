package com.eden.particles.impl.math;

/** Nearest-entry lookup of packed {@code 0xRRGGBB} over {@code t ∈ [0,1]}. */
public final class BakedGradient {

    private final int[] table;

    private BakedGradient(int[] table) {
        this.table = table;
    }

    static BakedGradient constant(int rgb) {
        return new BakedGradient(new int[]{rgb});
    }

    static BakedGradient table(int[] table) {
        return new BakedGradient(table);
    }

    public int sample(float t) {
        int last = table.length - 1;
        if (last == 0 || !(t > 0f)) return table[0];
        if (t >= 1f) return table[last];
        return table[(int) (t * last + 0.5f)];
    }

    public boolean isConstant() {
        return table.length == 1;
    }
}
