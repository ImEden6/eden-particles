package com.eden.particles.impl.images;

import com.eden.particles.Constants;
import com.eden.particles.impl.net.ImagePayloads;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Client side of the image handshake, without Minecraft: answers offers (or queues a prompt), asks only for images it
 * hasn't cached, checks and limits what arrives, puts the set together, and tracks which offered images aren't usable
 * yet. One instance per client, {@link #reset} on disconnect.
 */
public final class ImageConsent {

    /** What the state machine asks of the game. */
    public interface Port {

        void send(ImagePayloads.Consent consent);

        /** Replace the usable images; call {@link #released} once they're ready. */
        void upload(List<ImagePayloads.Image> images);

        /** Which images are withheld changed. */
        void withheldChanged();

        /**
         * Finds cached copies of the offered images, checked against their hashes, then calls {@code done} on the
         * game thread with them by offer index.
         */
        void lookup(ImagePayloads.Offer offer, Consumer<Map<Integer, byte[]>> done);

        /** Keeps images the server sent, for next time. */
        void store(List<ImagePayloads.Image> received);
    }

    private final Port port;
    private final List<ImagePayloads.Image> incoming = new ArrayList<>();
    private List<ImagePayloads.Image> applied = List.of();
    private Set<ResourceLocation> withheld = Set.of();
    private Boolean sessionAnswer;
    /** The accepted offer whose images are on their way, and the cached ones found for it. */
    private ImagePayloads.Offer delivering;
    private Map<Integer, byte[]> cached = Map.of();
    private long incomingBytes;
    private boolean incomingRejected;
    private ImagePayloads.Offer waiting;
    private ImagePayloads.Offer prompting;
    /** Revisions only grow, so an empty batch newer than every offer means the server's set is now empty. */
    private int latestOffer = -1;

    public ImageConsent(Port port) {
        this.port = port;
    }

    public boolean isWithheld(ResourceLocation id) {
        return withheld.contains(id);
    }

    /** @param trusted the player's own world, or a server they always allow */
    public void offer(ImagePayloads.Offer offer, boolean trusted) {
        if (isPending(offer)) return;
        latestOffer = Math.max(latestOffer, offer.revision());
        withheld = Set.copyOf(offer.ids());
        port.withheldChanged();
        if (trusted || Boolean.TRUE.equals(sessionAnswer)) {
            Constants.LOG.info("Server offered {} particle image(s); accepting", offer.entries().size());
            answer(offer, true);
        } else if (Boolean.FALSE.equals(sessionAnswer)) {
            Constants.LOG.info("Server offered {} particle image(s); declined earlier this session", offer.entries().size());
            answer(offer, false);
        } else {
            Constants.LOG.info("Server offered {} particle image(s); asking", offer.entries().size());
            waiting = offer;
        }
    }

    /** Already asking (or about to ask, or fetching) this exact set. */
    private boolean isPending(ImagePayloads.Offer offer) {
        return (waiting != null && waiting.revision() == offer.revision())
                || (prompting != null && prompting.revision() == offer.revision())
                || (delivering != null && delivering.revision() == offer.revision());
    }

    public void images(ImagePayloads.Images payload) {
        if (payload.first()) {
            incoming.clear();
            incomingBytes = 0;
            incomingRejected = false;
        }
        boolean expected = delivering != null && payload.revision() == delivering.revision();
        if (!payload.images().isEmpty() && !expected) {
            incomingRejected = true;
            Constants.LOG.warn("Ignoring particle images the server sent without consent");
        }
        for (ImagePayloads.Image image : payload.images()) {
            incomingBytes += image.bytes().length;
            if (incoming.size() >= ImageLimits.MAX_IMAGES || incomingBytes > ImageLimits.MAX_TOTAL_BYTES) {
                if (!incomingRejected) Constants.LOG.error("Server sent more particle images than allowed; ignoring them");
                incomingRejected = true;
            }
            if (!incomingRejected) incoming.add(image);
        }
        if (!payload.last()) return;
        List<ImagePayloads.Image> received = List.copyOf(incoming);
        incoming.clear();
        if (incomingRejected) return;
        if (!expected) {
            if (payload.revision() > latestOffer) apply(List.of());
            return;
        }
        ImagePayloads.Offer offer = delivering;
        Map<Integer, byte[]> fromCache = cached;
        delivering = null;
        cached = Map.of();
        List<ImagePayloads.Image> set = assemble(offer, received, fromCache);
        if (set == null) return;
        Constants.LOG.info("Received {} particle image(s) from the server and {} from the cache", received.size(), set.size() - received.size());
        if (!received.isEmpty()) port.store(received);
        apply(set);
    }

    /** The offered set in order, from received and cached bytes, each checked against its hash; null if anything's off. */
    private static List<ImagePayloads.Image> assemble(ImagePayloads.Offer offer, List<ImagePayloads.Image> received,
                                                      Map<Integer, byte[]> fromCache) {
        Map<ResourceLocation, byte[]> byId = new HashMap<>();
        for (ImagePayloads.Image image : received) byId.put(image.id(), image.bytes());
        List<ImagePayloads.Image> set = new ArrayList<>(offer.entries().size());
        for (int i = 0; i < offer.entries().size(); i++) {
            ImagePayloads.Entry entry = offer.entries().get(i);
            byte[] bytes = byId.remove(entry.id());
            if (bytes == null) bytes = fromCache.get(i);
            if (bytes == null || !Arrays.equals(ImageHash.sha256(bytes), entry.sha256())) {
                Constants.LOG.error("Particle image {} from the server is missing or doesn't match what it offered; ignoring the set", entry.id());
                return null;
            }
            set.add(new ImagePayloads.Image(entry.id(), bytes));
        }
        if (!byId.isEmpty()) {
            Constants.LOG.error("Server sent particle images it didn't offer ({}); ignoring the set", byId.keySet());
            return null;
        }
        return set;
    }

    private void apply(List<ImagePayloads.Image> set) {
        if (set.equals(applied)) {
            released();
            return;
        }
        applied = set;
        port.upload(set);
    }

    /**
     * Once per tick. Applies an answer given since an offer was queued, otherwise returns the offer to prompt for if
     * {@code canPrompt}; the caller shows it and later reports {@link #choose} and {@link #promptClosed}.
     */
    public ImagePayloads.Offer nextPrompt(boolean canPrompt) {
        if (waiting == null) return null;
        if (sessionAnswer != null) {
            answer(waiting, sessionAnswer);
            waiting = null;
            return null;
        }
        if (!canPrompt) return null;
        prompting = waiting;
        waiting = null;
        return prompting;
    }

    /** The prompt left the screen; unanswered (e.g. replaced by world loading) means ask again later. */
    public void promptClosed(boolean answered) {
        if (!answered && prompting != null) waiting = prompting;
        prompting = null;
    }

    public void choose(ImagePayloads.Offer offer, boolean accepted) {
        sessionAnswer = accepted;
        answer(offer, accepted);
    }

    /** Accepting looks in the cache first, then asks the server for the rest. */
    private void answer(ImagePayloads.Offer offer, boolean accepted) {
        if (!accepted) {
            port.send(ImagePayloads.Consent.decline(offer.revision()));
            return;
        }
        delivering = offer;
        cached = Map.of();
        port.lookup(offer, found -> {
            if (delivering != offer) return;
            cached = Map.copyOf(found);
            List<Integer> need = new ArrayList<>();
            for (int i = 0; i < offer.entries().size(); i++) {
                if (!cached.containsKey(i)) need.add(i);
            }
            port.send(new ImagePayloads.Consent(offer.revision(), true, List.copyOf(need)));
        });
    }

    /** Images uploaded (or unchanged): nothing is withheld any more. */
    public void released() {
        withheld = Set.of();
        port.withheldChanged();
    }

    public boolean isIdle() {
        return applied.isEmpty() && sessionAnswer == null && waiting == null && prompting == null && delivering == null
                && withheld.isEmpty();
    }

    public void reset() {
        applied = List.of();
        sessionAnswer = null;
        delivering = null;
        cached = Map.of();
        waiting = null;
        prompting = null;
        latestOffer = -1;
        withheld = Set.of();
        incoming.clear();
        port.upload(List.of());
    }
}
