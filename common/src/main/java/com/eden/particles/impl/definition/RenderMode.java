package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * {@code sprite} renders our own particle, which follows every curve. {@code vanilla} spawns an existing particle
 * type, which only honours emitter-side settings.
 */
public sealed interface RenderMode {

    enum Blend { TRANSLUCENT, ADDITIVE }

    record Sprite(SpriteRef sprite, Blend blend, boolean emissive) implements RenderMode {
    }

    record Vanilla(ResourceLocation particle) implements RenderMode {
    }

    List<String> TYPES = List.of("sprite", "vanilla");

    Codec<Blend> BLEND = Codecs.enumCodec(Blend.class, "blend mode");

    Codec<RenderMode> CODEC = ObjectCodec.of("render", f -> {
        String type = f.required("type", Codecs.STRING);
        return switch (type) {
            case "sprite" -> new Sprite(f.required("sprite", SpriteRef.CODEC), f.optional("blend", BLEND, Blend.TRANSLUCENT),
                    f.optional("emissive", Codecs.BOOL, false));
            case "vanilla" -> new Vanilla(f.required("particle", Codecs.ID));
            default -> throw f.failAt("type", Codecs.unknownChoice("render type", type, TYPES));
        };
    }, (render, out) -> {
        switch (render) {
            case Sprite s -> out.put("type", Codecs.STRING, "sprite").put("sprite", SpriteRef.CODEC, s.sprite())
                    .put("blend", BLEND, s.blend()).put("emissive", Codecs.BOOL, s.emissive());
            case Vanilla v -> out.put("type", Codecs.STRING, "vanilla").put("particle", Codecs.ID, v.particle());
        }
    });
}
