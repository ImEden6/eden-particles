package com.eden.particles.impl.images;

import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.net.Batches;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.platform.Services;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;

/**
 * Datapack images ({@code data/<ns>/eden_particles/textures/**.png}). They're offered to each player and only sent
 * after the player consents (keyed weakly by the player object, which is new on every login).
 */
public final class ServerImages {

    private static final FileToIdConverter TEXTURES = new FileToIdConverter("eden_particles/textures", ".png");
    static final int BATCH_BYTES = 512 * 1024;

    private static List<ImagePayloads.Image> images = List.of();
    private static long totalBytes;
    private static int revision;
    /** Revision last offered to each player, so repeated syncs don't re-offer the same set. */
    private static final Map<ServerPlayer, Integer> delivered = new WeakHashMap<>();
    /** Revision whose images each player already has, so repeated consents can't trigger repeated sends. */
    private static final Map<ServerPlayer, Integer> sentImages = new WeakHashMap<>();

    private ServerImages() {
    }

    /** Reads and validates images; anything over a limit is reported and skipped. Safe off-thread. */
    public static List<ImagePayloads.Image> read(ResourceManager resources, List<DefinitionError> errors) {
        Map<ResourceLocation, Resource> found = new TreeMap<>(TEXTURES.listMatchingResources(resources));
        List<ImagePayloads.Image> accepted = new ArrayList<>();
        long total = 0;
        for (Map.Entry<ResourceLocation, Resource> entry : found.entrySet()) {
            ResourceLocation id = TEXTURES.fileToId(entry.getKey());
            String source = entry.getValue().sourcePackId();
            byte[] bytes;
            try (InputStream in = entry.getValue().open()) {
                bytes = in.readNBytes(ImageLimits.MAX_FILE_BYTES + 1);
            } catch (IOException e) {
                errors.add(new DefinitionError(DefinitionError.Kind.IMAGE, id, source, "couldn't read: " + e.getMessage()));
                continue;
            }
            String problem = ImageLimits.check(bytes);
            if (problem == null && accepted.size() >= ImageLimits.MAX_IMAGES) {
                problem = "more than " + ImageLimits.MAX_IMAGES + " images";
            }
            if (problem == null && total + bytes.length > ImageLimits.MAX_TOTAL_BYTES) {
                problem = "images would total over " + ImageLimits.MAX_TOTAL_BYTES / (1024 * 1024) + " MiB";
            }
            if (problem != null) {
                errors.add(new DefinitionError(DefinitionError.Kind.IMAGE, id, source, problem));
                continue;
            }
            accepted.add(new ImagePayloads.Image(id, bytes));
            total += bytes.length;
        }
        return List.copyOf(accepted);
    }

    /** Server thread. A changed set gets a new revision, which is offered again on the next sync. */
    public static void replace(List<ImagePayloads.Image> next) {
        if (next.equals(images)) return;
        images = next;
        totalBytes = next.stream().mapToLong(image -> image.bytes().length).sum();
        revision++;
    }

    public static int count() {
        return images.size();
    }

    /** On join and after {@code /reload}: an empty set clears the client; otherwise offer (clients that agreed accept automatically). */
    public static void syncTo(ServerPlayer player) {
        if (!Services.NETWORK.canSend(player, ImagePayloads.Offer.TYPE)) return;
        Integer last = delivered.put(player, revision);
        if (last != null && last == revision) return;
        if (images.isEmpty()) {
            Services.NETWORK.send(player, new ImagePayloads.Images(true, true, List.of()));
        } else {
            Services.NETWORK.send(player, new ImagePayloads.Offer(revision,
                    images.stream().map(ImagePayloads.Image::id).toList(), totalBytes));
        }
    }

    /** Server thread, from the client's answer. Answers to an older offer are ignored; a fresh offer follows. */
    public static void onConsent(ServerPlayer player, ImagePayloads.Consent answer) {
        if (answer.revision() != revision) return;
        if (!answer.accepted()) return;
        Integer has = sentImages.put(player, revision);
        if (has != null && has == revision) return;
        batches(images, BATCH_BYTES).forEach(payload -> Services.NETWORK.send(player, payload));
    }

    static List<ImagePayloads.Images> batches(List<ImagePayloads.Image> all, int budgetBytes) {
        List<List<ImagePayloads.Image>> groups = Batches.split(all, image -> image.bytes().length + image.id().toString().length() + 16, budgetBytes);
        List<ImagePayloads.Images> payloads = new ArrayList<>(groups.size());
        for (int i = 0; i < groups.size(); i++) {
            payloads.add(new ImagePayloads.Images(i == 0, i == groups.size() - 1, groups.get(i)));
        }
        return payloads;
    }
}
