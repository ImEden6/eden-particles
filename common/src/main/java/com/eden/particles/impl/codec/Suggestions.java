package com.eden.particles.impl.codec;

import java.util.Collection;
import java.util.Optional;

final class Suggestions {

    private Suggestions() {
    }

    /** Closest candidate within an edit distance of {@code max(1, length/3)}. */
    static Optional<String> closest(String input, Collection<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshtein(input, candidate);
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        int limit = Math.max(1, input.length() / 3);
        return bestDistance <= limit ? Optional.ofNullable(best) : Optional.empty();
    }

    static String didYouMean(String input, Collection<String> candidates) {
        return closest(input, candidates).map(s -> " (did you mean '" + s + "'?)").orElse("");
    }

    static int levenshtein(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
