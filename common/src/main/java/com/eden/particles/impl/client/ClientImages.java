package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client side of server images: answers offers (asking the player unless it's their own world or a server they always
 * allow), receives the images, and keeps them in the {@link SyncedAtlas}. Offered images that aren't loaded are
 * withheld, so particles using them are skipped rather than drawn with a placeholder.
 */
final class ClientImages {

    private final SyncedAtlas atlas = new SyncedAtlas();
    private final ClientConfig config = new ClientConfig();
    private final Runnable onChanged;
    private final List<ImagePayloads.Image> incoming = new ArrayList<>();
    private List<ImagePayloads.Image> applied = List.of();
    private Set<ResourceLocation> withheld = Set.of();
    private Boolean sessionAnswer;
    private ImagePayloads.Offer waiting;
    private ImagePayloads.Offer shownOffer;
    private ImageConsentScreen shown;

    /** @param onChanged runs whenever which images are usable changes (to drop cached sprite lookups) */
    ClientImages(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    SyncedAtlas atlas() {
        return atlas;
    }

    boolean isWithheld(ResourceLocation id) {
        return withheld.contains(id);
    }

    void handle(ImagePayloads.Offer offer) {
        if (isPending(offer)) return;
        withheld = Set.copyOf(offer.ids());
        onChanged.run();
        Minecraft minecraft = Minecraft.getInstance();
        String server = serverAddress(minecraft);
        if (minecraft.isLocalServer() || Boolean.TRUE.equals(sessionAnswer) || (server != null && config.alwaysAllowsImages(server))) {
            Constants.LOG.info("Server offered {} particle image(s); accepting", offer.ids().size());
            answer(offer, true);
        } else if (Boolean.FALSE.equals(sessionAnswer)) {
            Constants.LOG.info("Server offered {} particle image(s); declined earlier this session", offer.ids().size());
            answer(offer, false);
        } else {
            Constants.LOG.info("Server offered {} particle image(s); asking", offer.ids().size());
            waiting = offer;
        }
    }

    /** Already asking (or about to ask) about this exact set. */
    private boolean isPending(ImagePayloads.Offer offer) {
        return (waiting != null && waiting.revision() == offer.revision())
                || (shownOffer != null && shownOffer.revision() == offer.revision());
    }

    void handle(ImagePayloads.Images payload) {
        if (payload.first()) incoming.clear();
        incoming.addAll(payload.images());
        if (!payload.last()) return;
        List<ImagePayloads.Image> received = List.copyOf(incoming);
        incoming.clear();
        if (received.equals(applied)) {
            release();
            return;
        }
        applied = received;
        Constants.LOG.info("Received {} particle image(s) from the server", received.size());
        atlas.rebuild(received, errors -> errors.forEach(error -> Constants.LOG.error("{}", error)), this::release);
    }

    void tick(Minecraft minecraft) {
        if (minecraft.getConnection() == null) {
            if (!applied.isEmpty() || sessionAnswer != null || waiting != null || !withheld.isEmpty()) reset();
            return;
        }
        if (shown != null && minecraft.screen != shown) {
            // Replaced by another screen (e.g. world loading) before the player answered: ask again later.
            if (!shown.answered()) waiting = shownOffer;
            shown = null;
            shownOffer = null;
        }
        if (waiting != null && minecraft.screen == null && minecraft.player != null) {
            ImagePayloads.Offer offer = waiting;
            waiting = null;
            shownOffer = offer;
            shown = new ImageConsentScreen(offer.ids().size(), offer.totalBytes(), choice -> choose(offer, choice));
            minecraft.setScreen(shown);
        }
    }

    private void choose(ImagePayloads.Offer offer, ImageConsentScreen.Choice choice) {
        String server = serverAddress(Minecraft.getInstance());
        if (choice == ImageConsentScreen.Choice.ALWAYS_ALLOW && server != null) config.alwaysAllowImages(server);
        boolean accepted = choice != ImageConsentScreen.Choice.DENY;
        sessionAnswer = accepted;
        answer(offer, accepted);
    }

    private void answer(ImagePayloads.Offer offer, boolean accepted) {
        Services.NETWORK.sendToServer(new ImagePayloads.Consent(offer.revision(), accepted));
    }

    /** Images uploaded (or unchanged): nothing is withheld any more. */
    private void release() {
        withheld = Set.of();
        onChanged.run();
    }

    private void reset() {
        applied = List.of();
        sessionAnswer = null;
        waiting = null;
        shown = null;
        shownOffer = null;
        withheld = Set.of();
        incoming.clear();
        atlas.rebuild(List.of(), errors -> { }, onChanged);
    }

    private static String serverAddress(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        return server == null ? null : server.ip;
    }
}
