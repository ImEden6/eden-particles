package com.eden.particles.impl.images;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageCacheTest {

    private static final String A = "a.example:25565";
    private static final String B = "b.example:25565";

    private static final class MemoryStorage implements ImageCache.Storage {
        final Map<String, byte[]> files = new HashMap<>();
        long clock = 1000;
        boolean failing;

        @Override
        public Optional<byte[]> read(String name) throws IOException {
            check();
            return Optional.ofNullable(files.get(name));
        }

        @Override
        public void write(String name, byte[] bytes) throws IOException {
            check();
            files.put(name, bytes);
        }

        @Override
        public void delete(String name) throws IOException {
            check();
            files.remove(name);
        }

        @Override
        public long now() {
            return clock;
        }

        private void check() throws IOException {
            if (failing) throw new IOException("storage is down");
        }
    }

    /** A valid 8×8 PNG header followed by a seed, so each seed hashes differently. */
    private static byte[] image(int seed) {
        ByteBuffer buffer = ByteBuffer.allocate(28);
        buffer.put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        buffer.putInt(13).putInt(0x49484452).putInt(8).putInt(8).putInt(seed);
        return buffer.array();
    }

    private static String name(byte[] hash) {
        return HexFormat.of().formatHex(hash) + ".png";
    }

    private static long pngCount(MemoryStorage storage) {
        return storage.files.keySet().stream().filter(file -> file.endsWith(".png")).count();
    }

    @Test
    void hitsOnlyForTheServerThatSentTheImage() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        byte[] bytes = image(1);
        byte[] hash = ImageCache.sha256(bytes);
        cache.put(A, hash, bytes);

        assertTrue(cache.has(A, hash));
        assertArrayEquals(bytes, cache.get(A, hash).orElseThrow());
        assertTrue(storage.files.containsKey(name(hash)));
        assertFalse(cache.has(B, hash));
        assertTrue(cache.get(B, hash).isEmpty());
    }

    @Test
    void serversSharingAnImageShareOneFile() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        byte[] bytes = image(1);
        byte[] hash = ImageCache.sha256(bytes);
        cache.put(A, hash, bytes);
        cache.put(B, hash, bytes);

        assertEquals(1, pngCount(storage));
        assertTrue(cache.get(A, hash).isPresent());
        assertTrue(cache.get(B, hash).isPresent());
    }

    @Test
    void storesNothingForMismatchedOrInvalidImages() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        cache.put(A, ImageCache.sha256(image(2)), image(1));
        byte[] text = "this is not a PNG, but it has a hash of its own".getBytes(StandardCharsets.UTF_8);
        cache.put(A, ImageCache.sha256(text), text);

        assertTrue(storage.files.isEmpty());
        assertFalse(cache.has(A, ImageCache.sha256(image(1))));
        assertFalse(cache.has(A, ImageCache.sha256(text)));
    }

    @Test
    void corruptOrMissingFileIsAMissAndIsDropped() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        byte[] corrupt = image(1);
        byte[] missing = image(2);
        byte[] corruptHash = ImageCache.sha256(corrupt);
        byte[] missingHash = ImageCache.sha256(missing);
        cache.put(A, corruptHash, corrupt);
        cache.put(A, missingHash, missing);
        storage.files.put(name(corruptHash), image(3));
        storage.files.remove(name(missingHash));

        assertTrue(cache.get(A, corruptHash).isEmpty());
        assertFalse(cache.has(A, corruptHash));
        assertFalse(storage.files.containsKey(name(corruptHash)));
        assertTrue(cache.get(A, missingHash).isEmpty());
        assertFalse(cache.has(A, missingHash));
    }

    @Test
    void evictsLeastRecentlyUsedWhenOverCap() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, 2L * image(1).length);
        byte[] a = image(1);
        byte[] b = image(2);
        byte[] c = image(3);
        storage.clock = 1;
        cache.put(A, ImageCache.sha256(a), a);
        storage.clock = 2;
        cache.put(A, ImageCache.sha256(b), b);
        storage.clock = 3;
        assertTrue(cache.get(A, ImageCache.sha256(a)).isPresent());
        storage.clock = 4;
        cache.put(A, ImageCache.sha256(c), c);

        assertTrue(cache.has(A, ImageCache.sha256(a)));
        assertFalse(cache.has(A, ImageCache.sha256(b)));
        assertTrue(cache.has(A, ImageCache.sha256(c)));
        assertEquals(2, pngCount(storage));
    }

    @Test
    void flushWritesIndexOnlyWhenDirtyAndSurvivesRestart() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        cache.flush();
        assertFalse(storage.files.containsKey("index.json"));

        byte[] bytes = image(1);
        byte[] hash = ImageCache.sha256(bytes);
        cache.put(A, hash, bytes);
        cache.flush();
        byte[] index = storage.files.get("index.json");
        assertTrue(index != null);

        storage.files.put("index.json", "sentinel".getBytes(StandardCharsets.UTF_8));
        cache.flush();
        assertArrayEquals("sentinel".getBytes(StandardCharsets.UTF_8), storage.files.get("index.json"));
        storage.files.put("index.json", index);

        ImageCache restarted = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        assertTrue(restarted.has(A, hash));
        assertArrayEquals(bytes, restarted.get(A, hash).orElseThrow());
    }

    @Test
    void malformedIndexLoadsAsEmptyAndIsOverwritten() {
        for (String junk : new String[]{"{not json", "[1, 2]", "{\"entries\": {\"xyz\": {}}}"}) {
            MemoryStorage storage = new MemoryStorage();
            storage.files.put("index.json", junk.getBytes(StandardCharsets.UTF_8));
            ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
            byte[] bytes = image(1);
            byte[] hash = ImageCache.sha256(bytes);
            assertFalse(cache.has(A, hash));

            cache.put(A, hash, bytes);
            cache.flush();
            assertTrue(new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES).has(A, hash));
        }
    }

    @Test
    void storageFailuresAreMissesNotExceptions() {
        MemoryStorage storage = new MemoryStorage();
        ImageCache cache = new ImageCache(storage, ImageCache.DEFAULT_CAP_BYTES);
        byte[] bytes = image(1);
        byte[] hash = ImageCache.sha256(bytes);
        cache.put(A, hash, bytes);

        storage.failing = true;
        assertDoesNotThrow(() -> cache.flush());
        assertTrue(cache.get(A, hash).isEmpty());
        assertDoesNotThrow(() -> cache.put(A, ImageCache.sha256(image(2)), image(2)));

        MemoryStorage down = new MemoryStorage();
        down.failing = true;
        ImageCache offline = new ImageCache(down, ImageCache.DEFAULT_CAP_BYTES);
        assertTrue(offline.get(A, hash).isEmpty());
        assertDoesNotThrow(() -> offline.put(A, hash, bytes));
        assertDoesNotThrow(offline::flush);
    }
}
