package com.eden.particles.impl.math;

/** sRGB / linear / OKLab conversions. OKLab matrices from Ottosson (2020). */
public final class ColorMath {

    private ColorMath() {
    }

    public static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    public static double linearToSrgb(double c) {
        return c <= 0.0031308 ? c * 12.92 : 1.055 * Math.pow(c, 1.0 / 2.4) - 0.055;
    }

    public static void linearToOklab(double r, double g, double b, double[] out) {
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        out[0] = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
        out[1] = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
        out[2] = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
    }

    /** Output may leave [0,1] for out-of-gamut colours. */
    public static void oklabToLinear(double lightness, double a, double b, double[] out) {
        double l = lightness + 0.3963377774 * a + 0.2158037573 * b;
        double m = lightness - 0.1055613458 * a - 0.0638541728 * b;
        double s = lightness - 0.0894841775 * a - 1.2914855480 * b;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        out[0] = 4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s;
        out[1] = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s;
        out[2] = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s;
    }

    public static int packRgb(double r, double g, double b) {
        return (toByte(r) << 16) | (toByte(g) << 8) | toByte(b);
    }

    public static double red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0;
    }

    public static double green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0;
    }

    public static double blue(int rgb) {
        return (rgb & 0xFF) / 255.0;
    }

    private static int toByte(double c) {
        double clamped = c < 0.0 ? 0.0 : (c > 1.0 ? 1.0 : c);
        return (int) Math.round(clamped * 255.0);
    }
}
