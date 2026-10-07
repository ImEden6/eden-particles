package com.eden.particles.impl.load;

import net.minecraft.resources.ResourceLocation;

/** @param source the pack the file came from, or empty if unknown */
public record DefinitionError(Kind kind, ResourceLocation id, String source, String message) {

    public enum Kind { EFFECT, TEMPLATE, IMAGE }

    @Override
    public String toString() {
        String from = source.isEmpty() ? "" : " (pack '" + source + "')";
        return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + id + from + ": " + message;
    }
}
