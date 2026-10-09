package com.eden.particles.impl.images;

import com.eden.particles.impl.net.ImagePayloads;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The server and client halves of the image handshake, wired together through an in-memory network. */
class ImageHandshakeTest {

    /** One client: its state machine, what reached the game, and a loopback to the shared server. */
    private final class Client implements ImageConsent.Port {
        final String name;
        final ImageConsent consent = new ImageConsent(this);
        final List<ImagePayloads.Consent> answers = new ArrayList<>();
        final List<List<ImagePayloads.Image>> uploads = new ArrayList<>();
        boolean trusted;

        Client(String name) {
            this.name = name;
        }

        @Override
        public void send(ImagePayloads.Consent answer) {
            answers.add(answer);
            server.consent(name, answer).forEach(this::receive);
        }

        @Override
        public void upload(List<ImagePayloads.Image> images) {
            uploads.add(images);
            consent.released();
        }

        @Override
        public void withheldChanged() {
        }

        void receive(CustomPacketPayload payload) {
            switch (payload) {
                case ImagePayloads.Offer offer -> consent.offer(offer, trusted);
                case ImagePayloads.Images images -> consent.images(images);
                default -> throw new AssertionError(payload);
            }
        }

        void join() {
            server.sync(name).forEach(this::receive);
        }

        ImagePayloads.Offer prompt() {
            return consent.nextPrompt(true);
        }

        void choose(boolean accepted) {
            ImagePayloads.Offer offer = prompt();
            assertNotNull(offer, "expected a prompt");
            consent.choose(offer, accepted);
            consent.promptClosed(true);
        }

        List<ImagePayloads.Image> current() {
            return uploads.isEmpty() ? List.of() : uploads.get(uploads.size() - 1);
        }
    }

    private static final List<ImagePayloads.Image> SET = List.of(image("a", 100), image("b", 200));
    private static final List<ImagePayloads.Image> OTHER = List.of(image("c", 10));

    private ImageOffers<String> server;
    private Client client;

    @BeforeEach
    void setUp() {
        server = new ImageOffers<>(HashMap::new);
        client = new Client("alice");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    private static ImagePayloads.Image image(String path, int bytes) {
        return new ImagePayloads.Image(id(path), new byte[bytes]);
    }

    @Test
    void trustedClientsAcceptWithoutAsking() {
        server.replace(SET);
        client.trusted = true;
        client.join();
        assertEquals(List.of(new ImagePayloads.Consent(1, true)), client.answers);
        assertEquals(SET, client.current());
        assertFalse(client.consent.isWithheld(id("a")));
        assertNull(client.prompt());
    }

    @Test
    void untrustedClientsAreAskedAndOfferedImagesStayWithheldUntilThen() {
        server.replace(SET);
        client.join();
        assertTrue(client.answers.isEmpty());
        assertTrue(client.consent.isWithheld(id("a")));
        assertNull(client.consent.nextPrompt(false), "no prompt while another screen is open");

        client.choose(true);
        assertEquals(SET, client.current());
        assertFalse(client.consent.isWithheld(id("a")));
    }

    @Test
    void denyingSendsNothingAndKeepsImagesWithheld() {
        server.replace(SET);
        client.join();
        client.choose(false);
        assertEquals(List.of(new ImagePayloads.Consent(1, false)), client.answers);
        assertTrue(client.uploads.isEmpty());
        assertTrue(client.consent.isWithheld(id("b")));
    }

    @Test
    void theSessionAnswerCoversLaterOffersUntilReset() {
        server.replace(SET);
        client.join();
        client.choose(false);

        server.replace(OTHER);
        client.join();
        assertNull(client.prompt());
        assertEquals(new ImagePayloads.Consent(2, false), client.answers.get(1));

        client.consent.reset();
        server.replace(SET);
        client.join();
        assertNotNull(client.prompt(), "a new session asks again");
    }

    @Test
    void anOfferQueuedWhileAskingUsesTheAnswer() {
        server.replace(SET);
        client.join();
        ImagePayloads.Offer first = client.prompt();
        server.replace(OTHER);
        client.join();
        client.consent.choose(first, true);
        client.consent.promptClosed(true);

        assertNull(client.prompt());
        assertEquals(List.of(new ImagePayloads.Consent(1, true), new ImagePayloads.Consent(2, true)), client.answers);
        assertEquals(OTHER, client.current());
    }

    @Test
    void aPromptClosedWithoutAnAnswerIsShownAgain() {
        server.replace(SET);
        client.join();
        ImagePayloads.Offer offer = client.prompt();
        client.consent.promptClosed(false);
        assertEquals(offer, client.prompt());
    }

    @Test
    void aRepeatedOfferDoesNotAskTwice() {
        server.replace(SET);
        CustomPacketPayload offer = server.sync(client.name).get(0);
        client.receive(offer);
        client.receive(offer);
        assertNotNull(client.prompt());
        client.receive(offer);
        assertNull(client.prompt());
    }

    @Test
    void eachRevisionIsOfferedAndSentOnce() {
        server.replace(SET);
        client.trusted = true;
        client.join();
        client.join();
        assertEquals(1, client.answers.size());
        assertTrue(server.consent(client.name, new ImagePayloads.Consent(1, true)).isEmpty());

        server.replace(List.copyOf(SET));
        client.join();
        assertEquals(1, client.answers.size(), "an equal set keeps its revision");
    }

    @Test
    void staleAnswersAreIgnoredAndTheNewSetIsOffered() {
        server.replace(SET);
        client.join();
        ImagePayloads.Offer old = client.prompt();
        server.replace(OTHER);
        client.consent.choose(old, true);
        assertTrue(client.uploads.isEmpty());

        client.join();
        assertEquals(OTHER, client.current());
    }

    @Test
    void playersAreTrackedSeparately() {
        server.replace(SET);
        Client bob = new Client("bob");
        client.trusted = bob.trusted = true;
        client.join();
        bob.join();
        assertEquals(SET, client.current());
        assertEquals(SET, bob.current());
    }

    @Test
    void imagesSentWithoutConsentAreRefused() {
        client.receive(new ImagePayloads.Images(true, true, SET));
        assertTrue(client.uploads.isEmpty());
    }

    @Test
    void anEmptySetClearsTheClient() {
        server.replace(SET);
        client.trusted = true;
        client.join();
        server.replace(List.of());
        client.join();
        assertEquals(2, client.uploads.size());
        assertEquals(List.of(), client.current());
    }

    @Test
    void unchangedImagesAreNotUploadedAgain() {
        client.trusted = true;
        client.receive(new ImagePayloads.Offer(1, List.of(id("a")), 100));
        client.receive(new ImagePayloads.Images(true, true, List.of(image("a", 100))));
        client.receive(new ImagePayloads.Offer(2, List.of(id("a")), 100));
        assertTrue(client.consent.isWithheld(id("a")));
        client.receive(new ImagePayloads.Images(true, true, List.of(image("a", 100))));
        assertEquals(1, client.uploads.size());
        assertFalse(client.consent.isWithheld(id("a")));
    }

    @Test
    void limitsApplyAcrossBatches() {
        client.trusted = true;
        client.receive(new ImagePayloads.Offer(1, List.of(), 0));
        int half = (int) (ImageLimits.MAX_TOTAL_BYTES / 2) + 1;
        client.receive(new ImagePayloads.Images(true, false, List.of(image("a", half))));
        client.receive(new ImagePayloads.Images(false, true, List.of(image("b", half))));
        assertTrue(client.uploads.isEmpty(), "total size");

        List<ImagePayloads.Image> many = new ArrayList<>();
        for (int i = 0; i <= ImageLimits.MAX_IMAGES; i++) many.add(image("i" + i, 1));
        client.receive(new ImagePayloads.Images(true, false, many.subList(0, ImageLimits.MAX_IMAGES)));
        client.receive(new ImagePayloads.Images(false, true, many.subList(ImageLimits.MAX_IMAGES, many.size())));
        assertTrue(client.uploads.isEmpty(), "image count");
    }

    @Test
    void largeSetsArriveInBatchesAndAreReassembled() {
        List<ImagePayloads.Image> big = new ArrayList<>();
        for (int i = 0; i < 8; i++) big.add(image("big" + i, ImageOffers.BATCH_BYTES / 3));
        server.replace(big);
        assertTrue(server.consent("someone", new ImagePayloads.Consent(1, true)).size() > 1);

        client.trusted = true;
        client.join();
        assertEquals(big, client.current());
    }

    @Test
    void resetClearsEverythingIncludingConsent() {
        server.replace(SET);
        client.trusted = true;
        client.join();
        client.consent.reset();
        assertTrue(client.consent.isIdle());
        assertEquals(List.of(), client.current());
        client.receive(new ImagePayloads.Images(true, true, SET));
        assertEquals(List.of(), client.current());
    }
}
