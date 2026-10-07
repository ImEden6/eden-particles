package com.eden.particles.impl.net;

import com.eden.particles.api.EffectParams;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PayloadTest {

    private static <T> T roundTrip(StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buf, value);
        T decoded = codec.decode(buf);
        assertFalse(buf.isReadable(), "trailing bytes");
        return decoded;
    }

    @Test
    void playRoundTrips() {
        PlayEffectPayload payload = new PlayEffectPayload(7, ResourceLocation.fromNamespaceAndPath("eden_particles", "examples/arcane_burst"),
                new Vec3(1.5, 64, -3.25), 42, new Vec3(0, 1, 0), 2f, 90f, -10f, 5f, 0xFF8800, -123456789L, 345);
        assertEquals(payload, roundTrip(PlayEffectPayload.STREAM_CODEC, payload));
    }

    @Test
    void fixedPositionUsesNoEntity() {
        PlayEffectPayload payload = new PlayEffectPayload(0, ResourceLocation.withDefaultNamespace("x"),
                Vec3.ZERO, -1, Vec3.ZERO, 1f, 0f, 0f, 0f, 0xFFFFFF, 0L, 0);
        assertEquals(-1, roundTrip(PlayEffectPayload.STREAM_CODEC, payload).entityId());
    }

    @Test
    void stopRoundTripsIncludingAll() {
        assertEquals(new StopEffectPayload(12), roundTrip(StopEffectPayload.STREAM_CODEC, new StopEffectPayload(12)));
        assertEquals(StopEffectPayload.ALL, roundTrip(StopEffectPayload.STREAM_CODEC, new StopEffectPayload(StopEffectPayload.ALL)).instanceId());
    }

    @Test
    void paramsValidate() {
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().scale(0));
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().range(-1));
        EffectParams params = EffectParams.builder().tint(0xFF123456).seed(5).build();
        assertEquals(0x123456, params.tint());
        assertEquals(5L, params.seed().getAsLong());
        assertFalse(EffectParams.DEFAULT.seed().isPresent());
    }
}
