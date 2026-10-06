package com.eden.particles.impl.load;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

/** A definition file read from a pack, before parsing. */
public record RawDefinition(ResourceLocation id, String source, JsonElement json) {
}
