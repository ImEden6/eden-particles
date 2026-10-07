package com.eden.particles.impl.net;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

public final class Batches {

    private Batches() {
    }

    /**
     * Splits {@code items} in order into groups whose cost stays within {@code budget}. An item over budget gets a
     * group of its own. Always returns at least one (possibly empty) group, so a sync can still clear the client.
     */
    public static <T> List<List<T>> split(List<T> items, ToIntFunction<T> cost, int budget) {
        List<List<T>> groups = new ArrayList<>();
        List<T> current = new ArrayList<>();
        int size = 0;
        for (T item : items) {
            int itemCost = cost.applyAsInt(item);
            if (!current.isEmpty() && size + itemCost > budget) {
                groups.add(List.copyOf(current));
                current = new ArrayList<>();
                size = 0;
            }
            current.add(item);
            size += itemCost;
        }
        groups.add(List.copyOf(current));
        return groups;
    }
}
