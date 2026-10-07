package com.eden.particles.impl.definition;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpriteRefTest {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }

    @Test
    void plainIdIsOneFrame() {
        SpriteRef ref = Json.decode(SpriteRef.CODEC, "\"test:glow\"");
        assertEquals(SpriteRef.single(id("glow")), ref);
        assertEquals(1, ref.frameCount());
        assertEquals(0, ref.frameAt(10, 20, 0.9f));
    }

    @Test
    void framesSpreadOverLife() {
        SpriteRef ref = Json.decode(SpriteRef.CODEC, "{\"frames\": [\"test:a\", \"test:b\", \"test:c\", \"test:d\"]}");
        assertEquals(SpriteRef.Mode.OVER_LIFE, ref.mode());
        assertEquals(0, ref.frameAt(0, 20, 0f));
        assertEquals(0, ref.frameAt(4, 20, 0f));
        assertEquals(1, ref.frameAt(5, 20, 0f));
        assertEquals(3, ref.frameAt(19, 20, 0f));
        assertEquals(3, ref.frameAt(25, 20, 0f)); // past the end holds the last frame
    }

    @Test
    void loopCyclesAtItsSpeed() {
        SpriteRef ref = Json.decode(SpriteRef.CODEC, "{\"frames\": [\"test:a\", \"test:b\", \"test:c\"], \"mode\": \"loop\", \"ticks_per_frame\": 2}");
        int[] expected = {0, 0, 1, 1, 2, 2, 0, 0};
        for (int age = 0; age < expected.length; age++) {
            assertEquals(expected[age], ref.frameAt(age, 1000, 0f), "age " + age);
        }
    }

    @Test
    void randomPicksOneFrameForLife() {
        SpriteRef ref = Json.decode(SpriteRef.CODEC, "{\"frames\": [\"test:a\", \"test:b\", \"test:c\", \"test:d\"], \"mode\": \"random\"}");
        assertEquals(2, ref.frameAt(0, 20, 0.6f));
        assertEquals(2, ref.frameAt(19, 20, 0.6f));
        assertEquals(3, ref.frameAt(0, 20, 0.9999f));
    }

    @Test
    void sheetSlicesRowMajorAndDefaultsToEveryCell() {
        SpriteRef ref = Json.decode(SpriteRef.CODEC, "{\"sheet\": \"test:smoke\", \"columns\": 4, \"rows\": 2}");
        SpriteRef.Source.Sheet sheet = assertInstanceOf(SpriteRef.Source.Sheet.class, ref.source());
        assertEquals(8, sheet.count());
        assertEquals(List.of(id("smoke")), ref.images());

        SpriteRef partial = Json.decode(SpriteRef.CODEC, "{\"sheet\": \"test:smoke\", \"columns\": 4, \"rows\": 2, \"count\": 6}");
        assertEquals(6, partial.frameCount());
    }

    @Test
    void mistakesAreReported() {
        assertTrue(Json.error(SpriteRef.CODEC, "{\"mode\": \"loop\"}").contains("needs either 'frames' or 'sheet'"));
        assertTrue(Json.error(SpriteRef.CODEC, "{\"frames\": [\"test:a\"], \"sheet\": \"test:b\", \"columns\": 1, \"rows\": 1}")
                .contains("needs either 'frames' or 'sheet'"));
        assertTrue(Json.error(SpriteRef.CODEC, "{\"frames\": [\"test:a\", \"test:b\"], \"ticks_per_frame\": 2}")
                .contains("at ticks_per_frame: only applies to mode 'loop'"));
        assertTrue(Json.error(SpriteRef.CODEC, "{\"sheet\": \"test:s\", \"columns\": 2, \"rows\": 2, \"count\": 5}")
                .contains("5 frames don't fit a 2×2 sheet"));
        assertTrue(Json.error(SpriteRef.CODEC, "{\"frames\": [\"test:a\"], \"mode\": \"lop\"}")
                .contains("unknown sprite mode 'lop' (did you mean 'loop'?)"));
    }

    @Test
    void roundTrips() {
        for (String json : List.of("\"test:glow\"",
                "{\"frames\": [\"test:a\", \"test:b\"], \"mode\": \"loop\", \"ticks_per_frame\": 3}",
                "{\"sheet\": \"test:s\", \"columns\": 3, \"rows\": 2, \"count\": 5, \"mode\": \"random\"}")) {
            SpriteRef ref = Json.decode(SpriteRef.CODEC, json);
            assertEquals(ref, Json.decode(SpriteRef.CODEC, Json.encode(SpriteRef.CODEC, ref)), json);
        }
    }
}
