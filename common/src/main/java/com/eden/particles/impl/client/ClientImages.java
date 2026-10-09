package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ImageConsent;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

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
            shown = new ImageConsentScreen(offer.ids().size(), offer.totalBytes(), choice -> choose(offer, choice));
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

    private static String serverAddress(Minecraft minecraft) {
        ServerData server = minecraft.getCurrentServer();
        return server == null ? null : server.ip;
    }
}
