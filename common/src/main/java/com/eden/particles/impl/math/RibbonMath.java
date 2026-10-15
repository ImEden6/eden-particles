package com.eden.particles.impl.math;

/** Generates camera-facing extruded vertices for connected ribbon strips. */
public final class RibbonMath {

    private static final float EPS = 1e-6f;
    private static final float DEGENERATE_EPS = 1e-4f;

    private RibbonMath() {
    }

    public static void computeExtrusions(
            float[] px, float[] py, float[] pz, float[] widths, int count,
            float camX, float camY, float camZ,
            float camUpX, float camUpY, float camUpZ,
            float[] outLeftX, float[] outLeftY, float[] outLeftZ,
            float[] outRightX, float[] outRightY, float[] outRightZ
    ) {
        if (count < 2) return;

        float prevSx = 0f, prevSy = 0f, prevSz = 0f;
        boolean hasPrevSide = false;

        for (int i = 0; i < count; i++) {
            float dx, dy, dz;
            if (i == 0) {
                dx = px[0] - px[1];
                dy = py[0] - py[1];
                dz = pz[0] - pz[1];
                if (dx * dx + dy * dy + dz * dz < EPS * EPS && count > 2) {
                    dx = px[1] - px[2];
                    dy = py[1] - py[2];
                    dz = pz[1] - pz[2];
                }
            } else if (i == count - 1) {
                dx = px[count - 2] - px[count - 1];
                dy = py[count - 2] - py[count - 1];
                dz = pz[count - 2] - pz[count - 1];
                if (dx * dx + dy * dy + dz * dz < EPS * EPS && count > 2) {
                    dx = px[count - 3] - px[count - 2];
                    dy = py[count - 3] - py[count - 2];
                    dz = pz[count - 3] - pz[count - 2];
                }
            } else {
                dx = px[i - 1] - px[i + 1];
                dy = py[i - 1] - py[i + 1];
                dz = pz[i - 1] - pz[i + 1];
                if (dx * dx + dy * dy + dz * dz < EPS * EPS) {
                    dx = px[i - 1] - px[i];
                    dy = py[i - 1] - py[i];
                    dz = pz[i - 1] - pz[i];
                    if (dx * dx + dy * dy + dz * dz < EPS * EPS) {
                        dx = px[i] - px[i + 1];
                        dy = py[i] - py[i + 1];
                        dz = pz[i] - pz[i + 1];
                    }
                }
            }

            float tLen = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float tx = tLen > EPS ? dx / tLen : 0f;
            float ty = tLen > EPS ? dy / tLen : 1f;
            float tz = tLen > EPS ? dz / tLen : 0f;

            float vx = px[i] - camX;
            float vy = py[i] - camY;
            float vz = pz[i] - camZ;
            float vLen = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            if (vLen > EPS) {
                vx /= vLen; vy /= vLen; vz /= vLen;
            } else {
                vx = 0f; vy = 0f; vz = 1f;
            }

            float sx = ty * vz - tz * vy;
            float sy = tz * vx - tx * vz;
            float sz = tx * vy - ty * vx;
            float sLen = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);

            if (sLen < DEGENERATE_EPS) {
                if (hasPrevSide) {
                    sx = prevSx; sy = prevSy; sz = prevSz;
                } else {
                    float fallbackSx = camUpY * vz - camUpZ * vy;
                    float fallbackSy = camUpZ * vx - camUpX * vz;
                    float fallbackSz = camUpX * vy - camUpY * vx;
                    float fLen = (float) Math.sqrt(fallbackSx * fallbackSx + fallbackSy * fallbackSy + fallbackSz * fallbackSz);
                    if (fLen > DEGENERATE_EPS) {
                        sx = fallbackSx / fLen; sy = fallbackSy / fLen; sz = fallbackSz / fLen;
                    } else {
                        sx = 1f; sy = 0f; sz = 0f;
                    }
                }
            } else {
                sx /= sLen; sy /= sLen; sz /= sLen;
            }

            if (hasPrevSide && (sx * prevSx + sy * prevSy + sz * prevSz) < 0f) {
                sx = -sx; sy = -sy; sz = -sz;
            }

            prevSx = sx; prevSy = sy; prevSz = sz;
            hasPrevSide = true;

            float relX = px[i] - camX;
            float relY = py[i] - camY;
            float relZ = pz[i] - camZ;
            float halfW = widths[i] * 0.5f;

            outLeftX[i] = relX - sx * halfW;
            outLeftY[i] = relY - sy * halfW;
            outLeftZ[i] = relZ - sz * halfW;

            outRightX[i] = relX + sx * halfW;
            outRightY[i] = relY + sy * halfW;
            outRightZ[i] = relZ + sz * halfW;
        }
    }
}
