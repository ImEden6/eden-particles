package com.eden.particles.impl.images;

import com.eden.particles.Constants;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Particle images received from servers, stored by content hash and partitioned per server. */
public final class ImageCache {

    /** Where cache files live; names are {@code <sha256 hex>.png} or {@code index.json}. */
    public interface Storage {
        /** Empty if the file doesn't exist. */
        Optional<byte[]> read(String name) throws IOException;

        void write(String name, byte[] bytes) throws IOException;

        void delete(String name) throws IOException;

        /** Milliseconds, for last-used times. */
        long now();
    }

    public static final long DEFAULT_CAP_BYTES = 64L * 1024 * 1024;

    private static final String INDEX = "index.json";
    private static final Pattern KEY = Pattern.compile("[0-9a-f]{64}");

    private final Storage storage;
    private final long capBytes;
    private final Map<String, Cached> entries = new LinkedHashMap<>();
    private boolean loaded;
    private boolean dirty;

    public ImageCache(Storage storage, long capBytes) {
        this.storage = storage;
        this.capBytes = capBytes;
    }

    /** SHA-256 of the bytes (32 bytes). */
    public static byte[] sha256(byte[] bytes) {
        return ImageHash.sha256(bytes);
    }

    /** Whether the index says this server sent an image with this hash. Index only, no file read. */
    public synchronized boolean has(String server, byte[] hash) {
        String key = key(hash);
        if (key == null) return false;
        ensureLoaded();
        Cached cached = entries.get(key);
        return cached != null && cached.servers.contains(server);
    }

    /** The cached bytes if this server sent them before and the file still checks out; a bad file is dropped. */
    public synchronized Optional<byte[]> get(String server, byte[] hash) {
        String key = key(hash);
        if (key == null) return Optional.empty();
        ensureLoaded();
        Cached cached = entries.get(key);
        if (cached == null || !cached.servers.contains(server)) return Optional.empty();

        Optional<byte[]> bytes;
        try {
            bytes = storage.read(fileName(key));
        } catch (IOException e) {
            warn("read " + key, e);
            return Optional.empty();
        }
        if (bytes.isEmpty() || !Arrays.equals(sha256(bytes.get()), hash) || ImageLimits.check(bytes.get()) != null) {
            drop(key);
            return Optional.empty();
        }
        cached.lastUsed = storage.now();
        dirty = true;
        return bytes;
    }

    /** Stores an image sent by a server. Ignored if the bytes don't match the hash or fail the image limits. */
    public synchronized void put(String server, byte[] hash, byte[] bytes) {
        String key = key(hash);
        if (key == null || !Arrays.equals(sha256(bytes), hash) || ImageLimits.check(bytes) != null) return;
        ensureLoaded();

        Cached cached = entries.get(key);
        if (cached != null) {
            cached.servers.add(server);
            cached.lastUsed = storage.now();
        } else {
            try {
                storage.write(fileName(key), bytes);
            } catch (IOException e) {
                warn("write " + key, e);
                return;
            }
            cached = new Cached(bytes.length, storage.now());
            cached.servers.add(server);
            entries.put(key, cached);
        }
        dirty = true;
        evict(key);
    }

    /** Writes the index if anything changed since the last flush. */
    public synchronized void flush() {
        if (!dirty) return;
        JsonObject all = new JsonObject();
        for (Map.Entry<String, Cached> item : entries.entrySet()) {
            Cached cached = item.getValue();
            JsonArray servers = new JsonArray();
            for (String server : cached.servers) servers.add(server);
            JsonObject obj = new JsonObject();
            obj.addProperty("size", cached.size);
            obj.addProperty("last_used", cached.lastUsed);
            obj.add("servers", servers);
            all.add(item.getKey(), obj);
        }
        JsonObject root = new JsonObject();
        root.add("entries", all);
        try {
            storage.write(INDEX, new Gson().toJson(root).getBytes(StandardCharsets.UTF_8));
            dirty = false;
        } catch (IOException e) {
            warn("write the index", e);
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Optional<byte[]> bytes;
        try {
            bytes = storage.read(INDEX);
        } catch (IOException e) {
            warn("read the index", e);
            return;
        }
        if (bytes.isEmpty()) return;
        try {
            parse(bytes.get());
        } catch (RuntimeException e) {
            Constants.LOG.warn("Image cache index is malformed, starting empty: {}", e.toString());
            entries.clear();
            dirty = true;
        }
    }

    private void parse(byte[] bytes) {
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        for (Map.Entry<String, JsonElement> item : root.getAsJsonObject("entries").entrySet()) {
            if (!KEY.matcher(item.getKey()).matches()) throw new IllegalStateException("bad hash " + item.getKey());
            JsonObject obj = item.getValue().getAsJsonObject();
            Cached cached = new Cached(obj.get("size").getAsLong(), obj.get("last_used").getAsLong());
            for (JsonElement server : obj.getAsJsonArray("servers")) cached.servers.add(server.getAsString());
            entries.put(item.getKey(), cached);
        }
    }

    /** Removes least-recently-used entries other than {@code keep} until the total is within the cap. */
    private void evict(String keep) {
        long total = 0;
        for (Cached cached : entries.values()) total += cached.size;
        while (total > capBytes) {
            String oldest = null;
            for (Map.Entry<String, Cached> item : entries.entrySet()) {
                if (item.getKey().equals(keep)) continue;
                if (oldest == null || item.getValue().lastUsed < entries.get(oldest).lastUsed) oldest = item.getKey();
            }
            if (oldest == null) return;
            total -= entries.get(oldest).size;
            drop(oldest);
        }
    }

    private void drop(String key) {
        entries.remove(key);
        dirty = true;
        try {
            storage.delete(fileName(key));
        } catch (IOException e) {
            warn("delete " + key, e);
        }
    }

    private static void warn(String action, IOException e) {
        Constants.LOG.warn("Image cache couldn't {}: {}", action, e.toString());
    }

    private static String key(byte[] hash) {
        return hash != null && hash.length == 32 ? HexFormat.of().formatHex(hash) : null;
    }

    private static String fileName(String key) {
        return key + ".png";
    }

    private static final class Cached {
        final long size;
        long lastUsed;
        final Set<String> servers = new LinkedHashSet<>();

        Cached(long size, long lastUsed) {
            this.size = size;
            this.lastUsed = lastUsed;
        }
    }
}
