package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ImageCache;
import com.eden.particles.impl.images.ImageConsent;
import com.eden.particles.impl.images.ImageHash;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.platform.Services;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Connects {@link ImageConsent} to the game: the consent screen, the always-allow list and the {@link SyncedAtlas}.
 * Offered images that aren't loaded are withheld, so particles using them are skipped rather than drawn with a placeholder.
 */
final class ClientImages implements ImageConsent.Port {

    private final SyncedAtlas atlas = new SyncedAtlas();
    private final ClientConfig config = new ClientConfig();
    private final Runnable onChanged;
    private final ImageConsent consent = new ImageConsent(this);
    private ImageConsentScreen shown;
    private ImageCache cache;

    /** @param onChanged runs whenever which images are usable changes (to drop cached sprite lookups) */
    ClientImages(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    SyncedAtlas atlas() {
        return atlas;
    }

    boolean isWithheld(ResourceLocation id) {
        return consent.isWithheld(id);
    }

    void handle(ImagePayloads.Offer offer) {
        Minecraft minecraft = Minecraft.getInstance();
        String server = serverAddress(minecraft);
        consent.offer(offer, minecraft.isLocalServer() || (server != null && config.alwaysAllowsImages(server)));
    }

    void handle(ImagePayloads.Images payload) {
        consent.images(payload);
    }

    void tick(Minecraft minecraft) {
        if (minecraft.getConnection() == null) {
            if (!consent.isIdle()) consent.reset();
            shown = null;
            return;
        }
        if (shown != null && minecraft.screen != shown) {
            consent.promptClosed(shown.answered());
            shown = null;
        }
        ImagePayloads.Offer offer = consent.nextPrompt(minecraft.screen == null && minecraft.player != null);
        if (offer != null) {
            String server = serverKey(minecraft);
            int saved = 0;
            long savedBytes = 0;
            for (ImagePayloads.Entry entry : offer.entries()) {
                if (cache().has(server, entry.sha256())) {
                    saved++;
                    savedBytes += entry.size();
                }
            }
            shown = new ImageConsentScreen(offer.entries().size(), offer.totalBytes(), saved, savedBytes, choice -> choose(offer, choice));
            minecraft.setScreen(shown);
        }
    }

    private void choose(ImagePayloads.Offer offer, ImageConsentScreen.Choice choice) {
        String server = serverAddress(Minecraft.getInstance());
        if (choice == ImageConsentScreen.Choice.ALWAYS_ALLOW && server != null) config.alwaysAllowImages(server);
        consent.choose(offer, choice != ImageConsentScreen.Choice.DENY);
    }

    @Override
    public void send(ImagePayloads.Consent answer) {
        Services.NETWORK.sendToServer(answer);
    }

    @Override
    public void upload(List<ImagePayloads.Image> images) {
        atlas.rebuild(images, errors -> errors.forEach(error -> Constants.LOG.error("{}", error)), consent::released);
    }

    @Override
    public void withheldChanged() {
        onChanged.run();
    }

    @Override
    public void lookup(ImagePayloads.Offer offer, Consumer<Map<Integer, byte[]>> done) {
        Minecraft minecraft = Minecraft.getInstance();
        String server = serverKey(minecraft);
        ImageCache cache = cache();
        CompletableFuture.supplyAsync(() -> {
            Map<Integer, byte[]> found = new HashMap<>();
            for (int i = 0; i < offer.entries().size(); i++) {
                ImagePayloads.Entry entry = offer.entries().get(i);
                int index = i;
                if (cache.has(server, entry.sha256())) cache.get(server, entry.sha256()).ifPresent(bytes -> found.put(index, bytes));
            }
            cache.flush();
            return found;
        }, Util.backgroundExecutor()).whenComplete((found, error) -> {
            if (error != null) Constants.LOG.warn("Couldn't read the particle image cache: {}", error.toString());
            minecraft.execute(() -> done.accept(found != null ? found : Map.of()));
        });
    }

    @Override
    public void store(List<ImagePayloads.Image> received) {
        String server = serverKey(Minecraft.getInstance());
        ImageCache cache = cache();
        CompletableFuture.runAsync(() -> {
            for (ImagePayloads.Image image : received) cache.put(server, ImageHash.sha256(image.bytes()), image.bytes());
            cache.flush();
        }, Util.backgroundExecutor());
    }

    private ImageCache cache() {
        if (cache == null) {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(Constants.MOD_ID).resolve("image_cache");
            cache = new ImageCache(new FileStorage(dir), ImageCache.DEFAULT_CAP_BYTES);
        }
        return cache;
    }

    /** Cached images count only for the server that sent them; the player's own worlds share one key. */
    private static String serverKey(Minecraft minecraft) {
        if (minecraft.isLocalServer()) return "local";
        String address = serverAddress(minecraft);
        return address != null ? address : "unknown";
    }

    /** Cache files in a folder. */
    private record FileStorage(Path dir) implements ImageCache.Storage {
        @Override
        public Optional<byte[]> read(String name) throws IOException {
            Path file = dir.resolve(name);
            return Files.exists(file) ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
        }

        @Override
        public void write(String name, byte[] bytes) throws IOException {
            Files.createDirectories(dir);
            Path temp = dir.resolve(name + ".tmp");
            Files.write(temp, bytes);
            Files.move(temp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }

        @Override
        public void delete(String name) throws IOException {
            Files.deleteIfExists(dir.resolve(name));
        }

        @Override
        public long now() {
            return System.currentTimeMillis();
        }
    }

    private static String serverAddress(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        return server == null ? null : server.ip;
    }
}
