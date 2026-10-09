package com.eden.particles.impl.images;

import com.eden.particles.Constants;
import com.eden.particles.impl.net.ImagePayloads;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Client side of the image handshake, without Minecraft: answers offers (or queues a prompt), gates and limits incoming
 * images, and tracks which offered images aren't usable yet. One instance per client, {@link #reset} on disconnect.
 */
public final class ImageConsent {

    /** What the state machine asks of the game. */
    public interface Port {

        void send(ImagePayloads.Consent consent);

        /** Replace the usable images; call {@link #released} once they're ready. */
        void upload(List<ImagePayloads.Image> images);

        /** Which images are withheld changed. */
        void withheldChanged();
    }

    private final Port port;
    private final List<ImagePayloads.Image> incoming = new ArrayList<>();
    private List<ImagePayloads.Image> applied = List.of();
    private Set<ResourceLocation> withheld = Set.of();
    private Boolean sessionAnswer;
    /** Set once this client has accepted an offer this session; images arriving otherwise are refused. */
    private boolean accepted;
    private long incomingBytes;
    private boolean incomingRejected;
    private ImagePayloads.Offer waiting;
    private ImagePayloads.Offer prompting;

    public ImageConsent(Port port) {
        this.port = port;
    }

    public boolean isWithheld(ResourceLocation id) {
        return withheld.contains(id);
    }

    /** @param trusted the player's own world, or a server they always allow */
    public void offer(ImagePayloads.Offer offer, boolean trusted) {
        if (isPending(offer)) return;
        withheld = Set.copyOf(offer.ids());
        port.withheldChanged();
        if (trusted || Boolean.TRUE.equals(sessionAnswer)) {
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
                || (prompting != null && prompting.revision() == offer.revision());
    }

    public void images(ImagePayloads.Images payload) {
        if (payload.first()) {
            incoming.clear();
            incomingBytes = 0;
            incomingRejected = false;
        }
        if (!payload.images().isEmpty() && !accepted) {
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
        if (incomingRejected) {
            incoming.clear();
            return;
        }
        List<ImagePayloads.Image> received = List.copyOf(incoming);
        incoming.clear();
        if (received.equals(applied)) {
            released();
            return;
        }
        applied = received;
        Constants.LOG.info("Received {} particle image(s) from the server", received.size());
        port.upload(received);
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

    private void answer(ImagePayloads.Offer offer, boolean accepted) {
        if (accepted) this.accepted = true;
        port.send(new ImagePayloads.Consent(offer.revision(), accepted));
    }

    /** Images uploaded (or unchanged): nothing is withheld any more. */
    public void released() {
        withheld = Set.of();
        port.withheldChanged();
    }

    public boolean isIdle() {
        return applied.isEmpty() && sessionAnswer == null && waiting == null && prompting == null && withheld.isEmpty();
    }

    public void reset() {
        applied = List.of();
        sessionAnswer = null;
        accepted = false;
        waiting = null;
        prompting = null;
        withheld = Set.of();
        incoming.clear();
        port.upload(List.of());
    }
}
