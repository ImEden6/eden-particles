package com.eden.particles.impl.images;

import com.eden.particles.impl.net.ImagePayloads;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImagesTest {

    /** Signature plus an IHDR chunk header; enough for header checks. */
    private static byte[] png(int width, int height, int totalLength) {
        ByteBuffer buffer = ByteBuffer.allocate(Math.max(24, totalLength));
        buffer.put(new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'});
        buffer.putInt(13).putInt(0x49484452).putInt(width).putInt(height);
        return buffer.array();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    @Test
    void readsPngDimensions() {
        assertEquals(new PngHeader(64, 32), PngHeader.read(png(64, 32, 40)));
    }

    @Test
    void rejectsWhatIsntAPng() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PngHeader.read(new byte[10])).getMessage().contains("only 10 bytes"));
        byte[] gif = png(8, 8, 30);
        gif[1] = 'G';
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PngHeader.read(gif)).getMessage().contains("wrong signature"));
        byte[] noIhdr = png(8, 8, 30);
        noIhdr[12] = 'X';
        assertTrue(assertThrows(IllegalArgumentException.class, () -> PngHeader.read(noIhdr)).getMessage().contains("isn't IHDR"));
    }

    @Test
    void enforcesPerImageLimits() {
        assertNull(ImageLimits.check(png(256, 256, 100)));
        assertTrue(ImageLimits.check(png(257, 16, 100)).contains("257×16; the limit is 256×256"));
        assertTrue(ImageLimits.check(png(16, 16, ImageLimits.MAX_FILE_BYTES + 1)).contains("the limit is 256 KiB"));
    }

    @Test
    void batchesStayWithinBudgetAndKeepOrder() {
        List<ImagePayloads.Image> images = new ArrayList<>();
        for (int i = 0; i < 20; i++) images.add(new ImagePayloads.Image(id("img" + i), new byte[300]));
        List<ImagePayloads.Images> batches = ServerImages.batches(images, 1000);
        assertTrue(batches.size() > 1);
        List<ImagePayloads.Image> rejoined = new ArrayList<>();
        for (int i = 0; i < batches.size(); i++) {
            assertEquals(i == 0, batches.get(i).first());
            assertEquals(i == batches.size() - 1, batches.get(i).last());
            rejoined.addAll(batches.get(i).images());
        }
        assertEquals(images, rejoined);
        assertEquals(1, ServerImages.batches(List.of(), 1000).size());
    }

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertFalse(buf.isReadable());
        return decoded;
    }

    @Test
    void payloadsRoundTrip() {
        ImagePayloads.Offer offer = new ImagePayloads.Offer(3, List.of(id("a"), id("b/c")), 12345L);
        assertEquals(offer, roundTrip(ImagePayloads.Offer.STREAM_CODEC, offer));
        ImagePayloads.Consent consent = new ImagePayloads.Consent(3, true);
        assertEquals(consent, roundTrip(ImagePayloads.Consent.STREAM_CODEC, consent));
        ImagePayloads.Images images = new ImagePayloads.Images(true, false, List.of(new ImagePayloads.Image(id("a"), png(4, 4, 50))));
        assertEquals(images, roundTrip(ImagePayloads.Images.STREAM_CODEC, images));
    }
}
