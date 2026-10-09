package com.eden.particles.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Recorded results in {@code common/src/test/resources/baselines}, one {@code key<TAB>value} per line. Tests compare
 * against them; {@code -PupdateBaselines} rewrites them instead, and the diff goes in the same commit as the change.
 */
public final class Baselines {

    private Baselines() {
    }

    public static boolean updating() {
        return Boolean.getBoolean("eden.updateBaselines");
    }

    public static Path file(String name) {
        return Path.of(System.getProperty("eden.baselines", "src/test/resources/baselines")).resolve(name);
    }

    public static Map<String, String> read(String name) {
        Path path = file(name);
        Map<String, String> values = new TreeMap<>();
        if (!Files.exists(path)) return values;
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                int tab = line.indexOf('\t');
                values.put(line.substring(0, tab), line.substring(tab + 1));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return values;
    }

    public static void write(String name, String header, Map<String, String> values) {
        List<String> lines = new ArrayList<>();
        for (String line : header.strip().split("\n")) lines.add("# " + line);
        new TreeMap<>(values).forEach((key, value) -> lines.add(key + "\t" + value));
        try {
            Files.createDirectories(file(name).getParent());
            Files.write(file(name), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fails listing every key whose value differs from the baseline, or rewrites the baseline when updating. */
    public static void check(String name, String header, Map<String, String> actual) {
        if (updating()) {
            write(name, header, actual);
            return;
        }
        Map<String, String> expected = read(name);
        Set<String> keys = new TreeSet<>(expected.keySet());
        keys.addAll(actual.keySet());
        StringBuilder diff = new StringBuilder();
        for (String key : keys) {
            String was = expected.get(key);
            String now = actual.get(key);
            if (was != null && was.equals(now)) continue;
            diff.append("\n  ").append(key)
                    .append("\n    baseline: ").append(was == null ? "(none)" : was)
                    .append("\n    now:      ").append(now == null ? "(none)" : now);
        }
        if (!diff.isEmpty()) {
            fail("Differs from baselines/" + name + ":" + diff
                    + "\nIf the change is intended, run ./gradlew :common:test -PupdateBaselines and commit the new baseline.");
        }
    }
}
