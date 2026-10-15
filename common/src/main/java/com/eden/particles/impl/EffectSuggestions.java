package com.eden.particles.impl;

import com.eden.particles.Constants;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.synchronization.SuggestionProviders;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

/**
 * Effect id suggestions for server commands. The provider is sent to clients by name, so a client with the mod lists
 * the effects it has loaded, resource packs included; one without it asks the server, which only knows datapack effects.
 */
public final class EffectSuggestions {

    public static final SuggestionProvider<CommandSourceStack> EFFECTS = SuggestionProviders.register(Constants.id("effects"),
            (context, builder) -> SharedSuggestionProvider.suggestResource(ids(context.getSource()), builder));

    private EffectSuggestions() {
    }

    /** Registers the provider; it must exist on both sides before command trees are sent. */
    public static void init() {
    }

    private static Collection<ResourceLocation> ids(SharedSuggestionProvider source) {
        if (!(source instanceof CommandSourceStack)) {
            var client = Sides.client();
            if (client.isPresent()) return client.get().effectIds();
        }
        return DatapackDefinitions.effectIds();
    }
}
