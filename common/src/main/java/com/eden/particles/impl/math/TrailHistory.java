package com.eden.particles.impl.math;

/** Fixed-capacity ring buffer of 3D positions and timestamps for ribbon trails. */
public final class TrailHistory {

    private final float[] xs;
    private final float[] ys;
    private final float[] zs;
    private final float[] times;
    private final int capacity;
    private final float minDistanceSq;

    private int head = -1;
    private int size = 0;

    public TrailHistory(int capacity, float minDistance) {
        if (capacity < 3 || capacity > 64) {
            throw new IllegalArgumentException("Capacity must be between 3 and 64, got: " + capacity);
        }
        this.capacity = capacity;
        this.xs = new float[capacity];
        this.ys = new float[capacity];
        this.zs = new float[capacity];
        this.times = new float[capacity];
        this.minDistanceSq = minDistance * minDistance;
    }

    public void record(float x, float y, float z, float time) {
        if (size == 0) {
            head = 0;
            xs[0] = x;
            ys[0] = y;
            zs[0] = z;
            times[0] = time;
            size = 1;
            return;
        }

        float dx = x - xs[head];
        float dy = y - ys[head];
        float dz = z - zs[head];
        float distSq = dx * dx + dy * dy + dz * dz;
        if (distSq < minDistanceSq) {
            return;
        }

        head = (head + 1) % capacity;
        xs[head] = x;
        ys[head] = y;
        zs[head] = z;
        times[head] = time;
        if (size < capacity) {
            size++;
        }
    }

    public void pruneOlderThan(float currentTime, float maxAge) {
        while (size > 0) {
            int tailIndex = (head - size + 1 + capacity * 2) % capacity;
            if (currentTime - times[tailIndex] <= maxAge) {
                break;
            }
            size--;
        }
    }

    public int size() {
        return size;
    }

    public int capacity() {
        return capacity;
    }

    public float getX(int i) {
        return xs[index(i)];
    }

    public float getY(int i) {
        return ys[index(i)];
    }

    public float getZ(int i) {
        return zs[index(i)];
    }

    public float getTime(int i) {
        return times[index(i)];
    }

    public void clear() {
        head = -1;
        size = 0;
    }

    private int index(int i) {
        if (i < 0 || i >= size) {
            throw new IndexOutOfBoundsException("Index " + i + " out of bounds for size " + size);
        }
        return (head - i + capacity) % capacity;
    }
}
