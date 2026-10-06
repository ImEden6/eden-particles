package com.eden.particles.impl.math;

import java.util.Arrays;

/** Colour keyframe curve: sRGB keys, interpolated in the chosen {@link Space}. Alpha is a separate curve. */
public final class Gradient {

    public enum Space {
        OKLAB,
        SRGB,
        LINEAR
    }

    public static final int BAKED_SIZE = 256;

    private final Space space;
    private final double[] times;
    private final int[] colors;
    private final Easing[] easings;
    private final double[][] coords; // each key converted into the interpolation space

    private Gradient(Space space, double[] times, int[] colors, Easing[] easings) {
        if (times.length == 0 || times.length != colors.length) {
            throw new IllegalArgumentException("A gradient needs matching, non-empty times and colours");
        }
        if (easings.length != times.length - 1) {
            throw new IllegalArgumentException("Expected " + (times.length - 1) + " segment easings, got " + easings.length);
        }
        for (int i = 1; i < times.length; i++) {
            if (!(times[i] > times[i - 1])) {
                throw new IllegalArgumentException("Gradient key times must be strictly increasing");
            }
        }
        this.space = space;
        this.times = times;
        this.colors = colors;
        this.easings = easings;
        this.coords = new double[colors.length][];
        for (int i = 0; i < colors.length; i++) {
            coords[i] = toSpace(colors[i]);
        }
    }

    public static Gradient constant(int rgb) {
        return new Gradient(Space.OKLAB, new double[]{0.0}, new int[]{rgb}, new Easing[0]);
    }

    public static Gradient linear(Space space, double[] times, int[] colors) {
        Easing[] easings = new Easing[Math.max(0, times.length - 1)];
        Arrays.fill(easings, Easings.LINEAR);
        return new Gradient(space, times.clone(), colors.clone(), easings);
    }

    public static Gradient eased(Space space, double[] times, int[] colors, Easing[] easings) {
        return new Gradient(space, times.clone(), colors.clone(), easings.clone());
    }

    /** Returns {@code 0xRRGGBB}. */
    public int evaluate(double t) {
        int n = times.length;
        if (n == 1 || t <= times[0]) return colors[0];
        if (t >= times[n - 1]) return colors[n - 1];

        int i = 0;
        while (times[i + 1] <= t) {
            i++;
        }
        double s = easings[i].apply((t - times[i]) / (times[i + 1] - times[i]));
        double[] a = coords[i];
        double[] b = coords[i + 1];
        return fromSpace(a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s, a[2] + (b[2] - a[2]) * s);
    }

    public BakedGradient bake() {
        if (times.length == 1) {
            return BakedGradient.constant(colors[0]);
        }
        int[] table = new int[BAKED_SIZE];
        for (int i = 0; i < BAKED_SIZE; i++) {
            table[i] = evaluate(i / (double) (BAKED_SIZE - 1));
        }
        return BakedGradient.table(table);
    }

    public Space space() {
        return space;
    }

    public int keyCount() {
        return times.length;
    }

    public double keyTime(int index) {
        return times[index];
    }

    public int keyColor(int index) {
        return colors[index];
    }

    /** Easing of the segment ending at key {@code index}. */
    public Easing easingInto(int index) {
        return easings[index - 1];
    }

    private double[] toSpace(int rgb) {
        double r = ColorMath.red(rgb);
        double g = ColorMath.green(rgb);
        double b = ColorMath.blue(rgb);
        return switch (space) {
            case SRGB -> new double[]{r, g, b};
            case LINEAR -> new double[]{ColorMath.srgbToLinear(r), ColorMath.srgbToLinear(g), ColorMath.srgbToLinear(b)};
            case OKLAB -> {
                double[] lab = new double[3];
                ColorMath.linearToOklab(ColorMath.srgbToLinear(r), ColorMath.srgbToLinear(g), ColorMath.srgbToLinear(b), lab);
                yield lab;
            }
        };
    }

    private int fromSpace(double x, double y, double z) {
        return switch (space) {
            case SRGB -> ColorMath.packRgb(x, y, z);
            case LINEAR -> ColorMath.packRgb(ColorMath.linearToSrgb(x), ColorMath.linearToSrgb(y), ColorMath.linearToSrgb(z));
            case OKLAB -> {
                double[] rgb = new double[3];
                ColorMath.oklabToLinear(x, y, z, rgb);
                yield ColorMath.packRgb(
                        ColorMath.linearToSrgb(Math.max(0.0, rgb[0])),
                        ColorMath.linearToSrgb(Math.max(0.0, rgb[1])),
                        ColorMath.linearToSrgb(Math.max(0.0, rgb[2])));
            }
        };
    }
}
