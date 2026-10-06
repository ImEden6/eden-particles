package com.eden.particles.impl.definition;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

final class Json {

    private Json() {
    }

    static JsonElement fixture(String path) {
        try (Reader reader = new InputStreamReader(
                Objects.requireNonNull(Json.class.getResourceAsStream("/fixtures/" + path), path), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader);
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static <A> A decode(Codec<A> codec, JsonElement json) {
        DataResult<A> result = codec.parse(JsonOps.INSTANCE, json);
        return result.result().orElseGet(() -> fail("expected success, got: " + result.error().orElseThrow().message()));
    }

    static <A> A decode(Codec<A> codec, String json) {
        return decode(codec, JsonParser.parseString(json));
    }

    static String error(Codec<?> codec, String json) {
        DataResult<?> result = codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json));
        return result.error().map(DataResult.Error::message).orElseGet(() -> fail("expected an error, got " + result.result().orElseThrow()));
    }

    static <A> JsonElement encode(Codec<A> codec, A value) {
        return codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
    }
}
