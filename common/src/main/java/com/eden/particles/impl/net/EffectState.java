package com.eden.particles.impl.net;

import com.eden.particles.api.EffectParams;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The parts of a playing effect that can change: rotation is yaw, pitch, roll in degrees. */
public record EffectState(float scale, float yaw, float pitch, float roll, int tint, Map<String, Float> inputs) {

    static final int MAX_INPUTS = 64;
    static final int MAX_NAME_LENGTH = 64;

    public static final StreamCodec<FriendlyByteBuf, EffectState> STREAM_CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeFloat(s.scale);
                buf.writeFloat(s.yaw);
                buf.writeFloat(s.pitch);
                buf.writeFloat(s.roll);
                buf.writeInt(s.tint);
                buf.writeVarInt(s.inputs.size());
                s.inputs.forEach((name, value) -> {
                    buf.writeUtf(name, MAX_NAME_LENGTH);
                    buf.writeFloat(value);
                });
            },
            buf -> {
                float scale = buf.readFloat();
                float yaw = buf.readFloat();
                float pitch = buf.readFloat();
                float roll = buf.readFloat();
                int tint = buf.readInt();
                int count = buf.readVarInt();
                if (count < 0 || count > MAX_INPUTS) throw new IllegalArgumentException("Too many inputs: " + count);
                Map<String, Float> inputs = new LinkedHashMap<>();
                for (int i = 0; i < count; i++) inputs.put(buf.readUtf(MAX_NAME_LENGTH), buf.readFloat());
                return new EffectState(scale, yaw, pitch, roll, tint, Collections.unmodifiableMap(inputs));
            });

    public static EffectState of(EffectParams params) {
        return new EffectState(params.scale(), params.yaw(), params.pitch(), params.roll(), params.tint(), params.inputs());
    }

    public EffectState withScale(float scale) {
        return new EffectState(scale, yaw, pitch, roll, tint, inputs);
    }

    public EffectState withRotation(float yaw, float pitch, float roll) {
        return new EffectState(scale, yaw, pitch, roll, tint, inputs);
    }

    public EffectState withTint(int tint) {
        return new EffectState(scale, yaw, pitch, roll, tint, inputs);
    }

    public EffectState withInput(String name, float value) {
        Map<String, Float> next = new LinkedHashMap<>(inputs);
        next.put(name, value);
        return new EffectState(scale, yaw, pitch, roll, tint, Collections.unmodifiableMap(next));
    }
}
