package com.eden.particles.impl.images;

import com.eden.particles.impl.load.DefinitionError;
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

/** Datapack images ({@code data/<ns>/eden_particles/textures/**.png}): reading them, and the networking for {@link ImageOffers}. */
public final class ServerImages {

    private static final FileToIdConverter TEXTURES = new FileToIdConverter("eden_particles/textures", ".png");
    /** Player objects are new on every login, so weak keys clear themselves. */
    private static final ImageOffers<ServerPlayer> OFFERS = new ImageOffers<>(WeakHashMap::new);

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

    /** Server thread. */
    public static void replace(List<ImagePayloads.Image> next) {
        OFFERS.replace(next);
    }

    public static int count() {
        return OFFERS.count();
    }

    /** On join and after {@code /reload}: offers the current set (clients that agreed accept automatically). */
    public static void syncTo(ServerPlayer player) {
        if (!Services.NETWORK.canSend(player, ImagePayloads.Offer.TYPE)) return;
        OFFERS.sync(player).forEach(payload -> Services.NETWORK.send(player, payload));
    }

    /** Server thread, from the client's answer. */
    public static void onConsent(ServerPlayer player, ImagePayloads.Consent answer) {
        OFFERS.consent(player, answer).forEach(payload -> Services.NETWORK.send(player, payload));
    }
}
