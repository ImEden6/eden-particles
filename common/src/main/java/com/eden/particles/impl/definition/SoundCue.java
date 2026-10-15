package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plays {@code sound} from {@code start}, following the effect's anchor and {@code offset}. A looping sound repeats
 * for its {@code duration} ({@link #REST} until the timeline ends); a one-shot plays once and takes one tick.
 * {@code volume} and {@code pitch} are rolled when it starts.
 */
public record SoundCue(
        Optional<String> name,
        ResourceLocation sound,
        int start,
        int duration,
        boolean loop,
        RandomFloat volume,
        RandomFloat pitch,
        ExprVec3 offset,
        SoundSource category) implements Track {

    public static final int REST = -1;

    private static final List<String> CATEGORIES = Arrays.stream(SoundSource.values()).map(SoundSource::getName).toList();

    private static final Codec<SoundSource> CATEGORY = Codecs.STRING.comapFlatMap(
            s -> Arrays.stream(SoundSource.values()).filter(c -> c.getName().equals(s)).findFirst()
                    .map(DataResult::success)
                    .orElseGet(() -> DataResult.error(() -> Codecs.unknownChoice("sound category", s, CATEGORIES))),
            SoundSource::getName);

    public static final Codec<SoundCue> CODEC = ObjectCodec.of("sound", f -> {
        Optional<String> name = f.optional("name", Codecs.STRING);
        ResourceLocation sound = f.required("sound", Codecs.ID);
        int start = f.optional("start", Codecs.intAtLeast(0), 0);
        boolean loop = f.optional("loop", Codecs.BOOL, false);
        Optional<Integer> duration = f.optional("duration", Codecs.intAtLeast(1));
        if (duration.isPresent() && !loop) {
            throw f.failAt("duration", "only looping sounds have a duration; add \"loop\": true or remove it");
        }
        return new SoundCue(name, sound, start, loop ? duration.orElse(REST) : 1, loop,
                f.optional("volume", RandomFloat.CODEC, new RandomFloat.Constant(1f)),
                f.optional("pitch", RandomFloat.CODEC, new RandomFloat.Constant(1f)),
                f.optional("offset", ExprCodecs.VEC3, ExprVec3.ZERO),
                f.optional("category", CATEGORY, SoundSource.PLAYERS));
    }, (s, out) -> out
            .put("name", Codecs.STRING, s.name)
            .put("sound", Codecs.ID, s.sound)
            .put("start", Codecs.INT, s.start)
            .put("loop", Codecs.BOOL, s.loop)
            .put("duration", Codecs.INT, s.loop ? Optional.of(s.duration) : Optional.empty())
            .put("volume", RandomFloat.CODEC, s.volume)
            .put("pitch", RandomFloat.CODEC, s.pitch)
            .put("offset", ExprCodecs.VEC3, s.offset)
            .put("category", CATEGORY, s.category));

    SoundCue withDuration(int duration) {
        return new SoundCue(name, sound, start, duration, loop, volume, pitch, offset, category);
    }

    @Override
    public void names(Set<String> out) {
        volume.names(out);
        pitch.names(out);
        offset.names(out);
    }
}
