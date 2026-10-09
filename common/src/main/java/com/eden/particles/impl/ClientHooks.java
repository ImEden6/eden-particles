package com.eden.particles.impl;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.api.EffectHandle;
import com.eden.particles.api.EffectParams;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.impl.net.PlayEffectPayload;
import com.eden.particles.impl.net.StopEffectPayload;
import com.eden.particles.impl.net.SyncDefinitionsPayload;
import com.eden.particles.impl.net.UpdateEffectPayload;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/** Client functionality reached from side-agnostic code; implemented in {@code impl.client}. */
public interface ClientHooks {

    EffectHandle play(ResourceLocation effect, EffectAnchor anchor, EffectParams params);

    boolean isLoaded(ResourceLocation effect);

    void onReload(Runnable listener);

    List<String> registerEffect(ResourceLocation id, JsonElement json);

    List<String> registerTemplate(ResourceLocation id, JsonElement json);

    boolean unregister(ResourceLocation id);

    void handle(PlayEffectPayload payload);

    void handle(StopEffectPayload payload);

    void handle(UpdateEffectPayload payload);

    void handle(SyncDefinitionsPayload payload);

    void handle(ImagePayloads.Offer payload);

    void handle(ImagePayloads.Images payload);
}
