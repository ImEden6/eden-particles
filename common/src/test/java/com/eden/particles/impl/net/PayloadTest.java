package com.eden.particles.impl.net;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectParams;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        AnchorData from = new AnchorData(new Vec3(1.5, 64, -3.25), 42, new Vec3(0, 0, 0.5), true, EffectAnchor.Turning.LOOK);
        AnchorData to = new AnchorData(new Vec3(8, 70, 2), -1, Vec3.ZERO, false, EffectAnchor.Turning.NONE);
        EffectState state = new EffectState(2f, 90f, -10f, 5f, 0xFF8800, Map.of("power", 0.75f, "spread", -2f));
        PlayEffectPayload payload = new PlayEffectPayload(7, ResourceLocation.fromNamespaceAndPath("eden_particles", "examples/arcane_burst"),
                from, Optional.of(to), state, -123456789L, 345);
        assertEquals(payload, roundTrip(PlayEffectPayload.STREAM_CODEC, payload));
    }

    @Test
    void fixedPositionUsesNoEntityOrTarget() {
        PlayEffectPayload payload = new PlayEffectPayload(0, ResourceLocation.withDefaultNamespace("x"),
                new AnchorData(Vec3.ZERO, -1, Vec3.ZERO, false, EffectAnchor.Turning.NONE), Optional.empty(),
                EffectState.of(EffectParams.DEFAULT), 0L, 0);
        PlayEffectPayload decoded = roundTrip(PlayEffectPayload.STREAM_CODEC, payload);
        assertEquals(-1, decoded.anchor().entityId());
        assertTrue(decoded.target().isEmpty());
        assertEquals(Map.of(), decoded.state().inputs());
    }

    @Test
    void updateRoundTrips() {
        UpdateEffectPayload payload = new UpdateEffectPayload(3, new EffectState(1f, 0f, 0f, 0f, 0x123456, Map.of("power", 1.5f)));
        assertEquals(payload, roundTrip(UpdateEffectPayload.STREAM_CODEC, payload));
    }

    @Test
    void stateKeepsInputsInOrderAndWithersReplaceValues() {
        EffectState state = EffectState.of(EffectParams.builder().input("b", 1).input("a", 2).build()).withInput("b", 3).withTint(0xABCDEF);
        assertEquals(List.of("b", "a"), List.copyOf(state.inputs().keySet()));
        assertEquals(3f, state.inputs().get("b"));
        assertEquals(0xABCDEF, state.tint());
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
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().input("distance", 1));
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().input("2x", 1));
        assertThrows(IllegalArgumentException.class, () -> EffectParams.builder().input("x", Float.NaN));
    }
}
