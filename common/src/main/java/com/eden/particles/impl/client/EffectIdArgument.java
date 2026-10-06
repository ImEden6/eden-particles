package com.eden.particles.impl.client;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Client-only effect id argument. Suggestions live on the type rather than in {@code .suggests(...)}, because Forge
 * swaps custom suggestion providers on client commands and they stop completing.
 */
final class EffectIdArgument implements ArgumentType<ResourceLocation> {

    @Override
    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        return ResourceLocation.read(reader);
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggestResource(ClientEffects.STORE.effectIds(), builder);
    }

    @Override
    public List<String> getExamples() {
        return List.of("eden_particles:examples/arcane_burst");
    }
}
