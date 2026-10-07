package com.eden.particles.impl.definition;

import com.eden.particles.impl.codec.Codecs;
import com.eden.particles.impl.codec.ObjectCodec;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The image(s) a sprite particle shows: one sprite, a list of frames, or a sheet sliced into frames (row-major).
 * JSON: {@code "ns:img"}, {@code {"frames": [...]}} or {@code {"sheet": "ns:img", "columns": c, "rows": r}}, each with an
 * optional {@code mode}.
 */
public record SpriteRef(Source source, Mode mode, int ticksPerFrame) {

    public static final int MAX_FRAMES = 64;
    public static final int MAX_SHEET_FRAMES = 256;

    public enum Mode {
        /** Frames spread evenly over the particle's life. */
        OVER_LIFE,
        /** Cycles every {@code ticks_per_frame} ticks. */
        LOOP,
        /** Each particle shows one frame, chosen at spawn. */
        RANDOM
    }

    public sealed interface Source {
        record Frames(List<ResourceLocation> ids) implements Source {
        }

        record Sheet(ResourceLocation id, int columns, int rows, int count) implements Source {
        }
    }

    public static SpriteRef single(ResourceLocation id) {
        return new SpriteRef(new Source.Frames(List.of(id)), Mode.OVER_LIFE, 1);
    }

    public int frameCount() {
        return switch (source) {
            case Source.Frames f -> f.ids().size();
            case Source.Sheet s -> s.count();
        };
    }

    /** Every image id this sprite needs. */
    public List<ResourceLocation> images() {
        return switch (source) {
            case Source.Frames f -> f.ids();
            case Source.Sheet s -> List.of(s.id());
        };
    }

    /** @param variant a per-particle uniform in [0,1), used by {@link Mode#RANDOM} */
    public int frameAt(int age, int lifetime, float variant) {
        int count = frameCount();
        if (count == 1) return 0;
        return switch (mode) {
            case OVER_LIFE -> Math.min(count - 1, (int) ((float) age / Math.max(1, lifetime) * count));
            case LOOP -> Math.floorMod(age / ticksPerFrame, count);
            case RANDOM -> Math.min(count - 1, (int) (variant * count));
        };
    }

    private static final Codec<Mode> MODE = Codecs.enumCodec(Mode.class, "sprite mode");

    private static final Codec<SpriteRef> OBJECT = ObjectCodec.of("sprite", f -> {
        boolean hasFrames = f.has("frames");
        boolean hasSheet = f.has("sheet");
        if (hasFrames == hasSheet) throw f.fail("needs either 'frames' or 'sheet'");
        Source source;
        if (hasFrames) {
            List<ResourceLocation> frames = f.required("frames", Codecs.nonEmptyList(Codecs.ID));
            if (frames.size() > MAX_FRAMES) throw f.failAt("frames", "at most " + MAX_FRAMES + " frames, got " + frames.size());
            source = new Source.Frames(frames);
        } else {
            ResourceLocation sheet = f.required("sheet", Codecs.ID);
            int columns = f.required("columns", Codecs.intAtLeast(1));
            int rows = f.required("rows", Codecs.intAtLeast(1));
            int count = f.optional("count", Codecs.intAtLeast(1), columns * rows);
            if (count > columns * rows) throw f.failAt("count", count + " frames don't fit a " + columns + "×" + rows + " sheet");
            if (count > MAX_SHEET_FRAMES) throw f.failAt("count", "at most " + MAX_SHEET_FRAMES + " frames, got " + count);
            source = new Source.Sheet(sheet, columns, rows, count);
        }
        Mode mode = f.optional("mode", MODE, Mode.OVER_LIFE);
        boolean hasTicks = f.has("ticks_per_frame");
        if (hasTicks && mode != Mode.LOOP) throw f.failAt("ticks_per_frame", "only applies to mode 'loop'");
        int ticksPerFrame = hasTicks ? f.required("ticks_per_frame", Codecs.intAtLeast(1)) : 1;
        return new SpriteRef(source, mode, ticksPerFrame);
    }, (ref, out) -> {
        switch (ref.source) {
            case Source.Frames frames -> out.put("frames", Codecs.list(Codecs.ID), frames.ids());
            case Source.Sheet sheet -> out.put("sheet", Codecs.ID, sheet.id()).put("columns", Codecs.INT, sheet.columns())
                    .put("rows", Codecs.INT, sheet.rows()).put("count", Codecs.INT, sheet.count());
        }
        out.put("mode", MODE, ref.mode);
        if (ref.mode == Mode.LOOP) out.put("ticks_per_frame", Codecs.INT, ref.ticksPerFrame);
    });

    public static final Codec<SpriteRef> CODEC = Codecs.byKind("a sprite id or sprite object", Codecs.<SpriteRef>encoder((ref, ops) ->
                    ref.source instanceof Source.Frames frames && frames.ids().size() == 1 && ref.mode == Mode.OVER_LIFE
                            ? Codecs.encodeWith(Codecs.ID, frames.ids().get(0), ops)
                            : Codecs.encodeWith(OBJECT, ref, ops)))
            .when(Codecs.Kind.STRING, Codecs.ID.xmap(SpriteRef::single, ref -> ref.images().get(0)))
            .when(Codecs.Kind.MAP, OBJECT)
            .build();
}
