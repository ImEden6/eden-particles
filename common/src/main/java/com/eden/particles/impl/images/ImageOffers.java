package com.eden.particles.impl.images;

import com.eden.particles.impl.net.Batches;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.sync.SentRevisions;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Server side of the image handshake, without networking: what to send each player for a sync or a consent. */
public final class ImageOffers<P> {

    static final int BATCH_BYTES = 512 * 1024;

    private List<ImagePayloads.Image> images = List.of();
    private long totalBytes;
    private int revision;
    private final SentRevisions<P> offered;
    private final SentRevisions<P> delivered;

    public ImageOffers(Supplier<Map<P, Integer>> maps) {
        this.offered = new SentRevisions<>(maps.get());
        this.delivered = new SentRevisions<>(maps.get());
    }

    /** A changed set gets a new revision, which is offered again on the next sync. */
    public void replace(List<ImagePayloads.Image> next) {
        if (next.equals(images)) return;
        images = List.copyOf(next);
        totalBytes = next.stream().mapToLong(image -> image.bytes().length).sum();
        revision++;
    }

    public int count() {
        return images.size();
    }

    /** An empty set clears the client; otherwise an offer. Nothing if this player was already offered this revision. */
    public List<CustomPacketPayload> sync(P player) {
        if (!offered.mark(player, revision)) return List.of();
        if (images.isEmpty()) return List.of(new ImagePayloads.Images(true, true, List.of()));
        return List.of(new ImagePayloads.Offer(revision, images.stream().map(ImagePayloads.Image::id).toList(), totalBytes));
    }

    /** Answers to an older offer are ignored (a fresh offer follows), and a revision is sent to a player at most once. */
    public List<ImagePayloads.Images> consent(P player, ImagePayloads.Consent answer) {
        if (answer.revision() != revision || !answer.accepted()) return List.of();
        if (!delivered.mark(player, revision)) return List.of();
        return batches(images, BATCH_BYTES);
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
