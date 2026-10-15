package com.eden.particles.impl.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RibbonMathTest {

    @Test
    void extrudesStraightLineSideView() {
        float[] px = {-2f, -1f, 0f, 1f, 2f};
        float[] py = {0f, 0f, 0f, 0f, 0f};
        float[] pz = {0f, 0f, 0f, 0f, 0f};
        float[] widths = {0.4f, 0.4f, 0.4f, 0.4f, 0.4f};
        int count = 5;

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        RibbonMath.computeExtrusions(
                px, py, pz, widths, count,
                0f, 0f, 5f, 0f, 1f, 0f,
                lx, ly, lz, rx, ry, rz
        );

        for (int i = 0; i < count; i++) {
            assertFalse(Float.isNaN(lx[i]));
            assertFalse(Float.isNaN(rx[i]));

            float dy = ry[i] - ly[i];
            assertEquals(0.4f, Math.abs(dy), 1e-4f);
            assertEquals(0f, rx[i] - lx[i], 1e-4f);
            assertEquals(0f, rz[i] - lz[i], 1e-4f);
        }
    }

    @Test
    void handlesDegenerateLookingDownTheBarrel() {
        float[] px = {0f, 0f, 0f};
        float[] py = {0f, 0f, 0f};
        float[] pz = {1f, 2f, 3f};
        float[] widths = {0.2f, 0.2f, 0.2f};
        int count = 3;

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        RibbonMath.computeExtrusions(
                px, py, pz, widths, count,
                0f, 0f, 5f, 0f, 1f, 0f,
                lx, ly, lz, rx, ry, rz
        );

        for (int i = 0; i < count; i++) {
            assertFalse(Float.isNaN(lx[i]));
            assertFalse(Float.isNaN(rx[i]));
            assertFalse(Float.isInfinite(lx[i]));
            assertFalse(Float.isInfinite(rx[i]));

            float dx = rx[i] - lx[i];
            float dy = ry[i] - ly[i];
            float dz = rz[i] - lz[i];
            float w = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(0.2f, w, 1e-3f);
        }
    }

    @Test
    void preservesOrientationWithoutTwisting() {
        int count = 10;
        float[] px = new float[count], py = new float[count], pz = new float[count];
        float[] widths = new float[count];

        for (int i = 0; i < count; i++) {
            double angle = 0.1 + (i / (double) count) * Math.PI;
            px[i] = (float) (3.0 * Math.cos(angle));
            py[i] = 0f;
            pz[i] = (float) (3.0 * Math.sin(angle));
            widths[i] = 0.3f;
        }

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        RibbonMath.computeExtrusions(
                px, py, pz, widths, count,
                0f, 0f, 0f, 0f, 1f, 0f,
                lx, ly, lz, rx, ry, rz
        );

        for (int i = 0; i < count; i++) {
            float dy = ry[i] - ly[i];
            assertEquals(0.3f, Math.abs(dy), 1e-3f);
            if (i > 0) {
                float prevDy = ry[i - 1] - ly[i - 1];
                assertTrue(dy * prevDy > 0f);
            }
        }
    }

    @Test
    void subTickInterpolationFormsContinuousChain() {
        float prevX = 0f, prevY = 0f, prevZ = 0f;
        float currX = 1f, currY = 2f, currZ = 0f;
        float partialTick = 0.5f;

        float headX = prevX + (currX - prevX) * partialTick;
        float headY = prevY + (currY - prevY) * partialTick;
        float headZ = prevZ + (currZ - prevZ) * partialTick;

        assertEquals(0.5f, headX);
        assertEquals(1.0f, headY);
        assertEquals(0.0f, headZ);

        float[] px = {headX, 0f, -1f};
        float[] py = {headY, 0f, -1f};
        float[] pz = {headZ, 0f, 0f};
        float[] widths = {0.2f, 0.2f, 0.2f};
        int count = 3;

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        RibbonMath.computeExtrusions(
                px, py, pz, widths, count,
                0f, 0f, 5f, 0f, 1f, 0f,
                lx, ly, lz, rx, ry, rz
        );

        for (int i = 0; i < count; i++) {
            assertFalse(Float.isNaN(lx[i]));
            assertFalse(Float.isNaN(rx[i]));
        }
    }

    @Test
    void extrudesCollinearPointsWithoutDegeneracy() {
        float[] px = {0f, 0f, 1f, 2f, 3f};
        float[] py = {0f, 0f, 0f, 0f, 0f};
        float[] pz = {0f, 0f, 0f, 0f, 0f};
        float[] widths = {0.4f, 0.4f, 0.4f, 0.4f, 0.4f};
        int count = 5;

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        RibbonMath.computeExtrusions(
                px, py, pz, widths, count,
                0f, 0f, 5f, 0f, 1f, 0f,
                lx, ly, lz, rx, ry, rz
        );

        for (int i = 0; i < count; i++) {
            assertFalse(Float.isNaN(lx[i]));
            assertFalse(Float.isNaN(rx[i]));
            float dy = ry[i] - ly[i];
            assertEquals(0.4f, Math.abs(dy), 1e-4f);
        }
    }

    @Test
    void handlesAcuteCornersWithoutInversion() {
        float[] px = {0f, 2f, 0.1f};
        float[] py = {0f, 0f, 0.1f};
        float[] pz = {0f, 0f, 0f};
        float[] widths = {0.3f, 0.3f, 0.3f};
        int count = 3;

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        RibbonMath.computeExtrusions(
                px, py, pz, widths, count,
                0f, 0f, 5f, 0f, 1f, 0f,
                lx, ly, lz, rx, ry, rz
        );

        for (int i = 0; i < count; i++) {
            assertFalse(Float.isNaN(lx[i]));
            assertFalse(Float.isNaN(rx[i]));
            float dx = rx[i] - lx[i];
            float dy = ry[i] - ly[i];
            float dz = rz[i] - lz[i];
            float width = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            assertEquals(0.3f, width, 1e-3f);
        }

        float sx0 = rx[0] - lx[0], sy0 = ry[0] - ly[0], sz0 = rz[0] - lz[0];
        float sx1 = rx[1] - lx[1], sy1 = ry[1] - ly[1], sz1 = rz[1] - lz[1];
        float sx2 = rx[2] - lx[2], sy2 = ry[2] - ly[2], sz2 = rz[2] - lz[2];
        assertTrue(sx0 * sx1 + sy0 * sy1 + sz0 * sz1 >= 0f);
        assertTrue(sx1 * sx2 + sy1 * sy2 + sz1 * sz2 >= 0f);
    }

    @Test
    void zeroAllocationDuringExtrusionLoop() {
        float[] px = {0f, 1f, 2f, 3f};
        float[] py = {0f, 0f, 0f, 0f};
        float[] pz = {0f, 0f, 0f, 0f};
        float[] widths = {0.2f, 0.2f, 0.2f, 0.2f};
        int count = 4;

        float[] lx = new float[count], ly = new float[count], lz = new float[count];
        float[] rx = new float[count], ry = new float[count], rz = new float[count];

        for (int i = 0; i < 10_000; i++) {
            RibbonMath.computeExtrusions(
                    px, py, pz, widths, count,
                    0f, 0f, 5f, 0f, 1f, 0f,
                    lx, ly, lz, rx, ry, rz
            );
        }

        for (int i = 0; i < count; i++) {
            assertFalse(Float.isNaN(lx[i]));
            assertFalse(Float.isNaN(rx[i]));
        }
    }

    @Test
    void ignoresCountLessThanTwo() {
        float[] p = {0f};
        float[] w = {1f};
        float[] out = {0f};
        assertDoesNotThrow(() -> RibbonMath.computeExtrusions(
                p, p, p, w, 1, 0, 0, 0, 0, 1, 0,
                out, out, out, out, out, out
        ));
    }
}
