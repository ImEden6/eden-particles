package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.eden.particles.impl.images.ImageLimits;
import com.eden.particles.impl.load.DefinitionError;
import com.eden.particles.impl.net.ImagePayloads;
import com.eden.particles.mixin.ParticleEngineAccessor;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.SpriteLoader;
import net.minecraft.client.renderer.texture.StitcherException;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceMetadata;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Eden's own atlas for images a server sent. Vanilla's particle atlas is stitched during resource reload and can't
 * take sprites afterwards. Survives F3+T: {@code TextureManager.reload} only re-registers custom textures.
 */
final class SyncedAtlas {

    static final ResourceLocation LOCATION = Constants.id("textures/atlas/synced.png");

    private TextureAtlas atlas;
    private Set<ResourceLocation> ids = Set.of();
    private int generation;

    boolean has(ResourceLocation id) {
        return ids.contains(id);
    }

    TextureAtlasSprite sprite(ResourceLocation id) {
        return atlas().getSprite(id);
    }

    /**
     * Render thread. Decodes and stitches off-thread, then uploads on the render thread. A newer rebuild supersedes an
     * older one still in flight.
     */
    void rebuild(List<ImagePayloads.Image> images, Consumer<List<DefinitionError>> onErrors, Runnable onUploaded) {
        int build = ++generation;
        TextureAtlas target = atlas(); // created here: its constructor queries the GPU's max texture size
        CompletableFuture.supplyAsync(() -> stitch(target, images), Util.backgroundExecutor())
                .thenCompose(result -> result.preparations().waitForUpload().thenApply(ready -> result))
                .thenAcceptAsync(result -> {
                    if (build != generation) {
                        result.preparations().regions().values().forEach(sprite -> sprite.contents().close());
                        return;
                    }
                    clearLiveParticles();
                    target.upload(result.preparations());
                    ids = result.ids();
                    onErrors.accept(result.errors());
                    onUploaded.run();
                }, Minecraft.getInstance())
                .exceptionally(error -> {
                    Constants.LOG.error("Couldn't build the atlas for server particle images; they won't show", error);
                    return null;
                });
    }

    private record Stitched(SpriteLoader.Preparations preparations, Set<ResourceLocation> ids, List<DefinitionError> errors) {
    }

    private static Stitched stitch(TextureAtlas target, List<ImagePayloads.Image> images) {
        List<DefinitionError> errors = new ArrayList<>();
        List<SpriteContents> contents = new ArrayList<>();
        Set<ResourceLocation> ids = new HashSet<>();
        for (ImagePayloads.Image image : images) {
            String problem = ImageLimits.check(image.bytes());
            if (problem != null) {
                errors.add(new DefinitionError(DefinitionError.Kind.IMAGE, image.id(), "server datapack", problem));
                continue;
            }
            try {
                NativeImage decoded = NativeImage.read(image.bytes());
                contents.add(new SpriteContents(image.id(), new FrameSize(decoded.getWidth(), decoded.getHeight()), decoded, ResourceMetadata.EMPTY));
                ids.add(image.id());
            } catch (IOException e) {
                errors.add(new DefinitionError(DefinitionError.Kind.IMAGE, image.id(), "server datapack", "couldn't decode: " + e.getMessage()));
            }
        }
        contents.add(MissingTextureAtlasSprite.create()); // stitching requires it
        try {
            SpriteLoader.Preparations preparations = SpriteLoader.create(target).stitch(contents, 0, Util.backgroundExecutor());
            return new Stitched(preparations, Set.copyOf(ids), errors);
        } catch (StitcherException e) {
            contents.forEach(SpriteContents::close);
            errors.add(new DefinitionError(DefinitionError.Kind.IMAGE, LOCATION, "server datapack",
                    "images don't fit in one texture on this GPU; none of them will show"));
            SpriteLoader.Preparations empty = SpriteLoader.create(target)
                    .stitch(new ArrayList<>(List.of(MissingTextureAtlasSprite.create())), 0, Util.backgroundExecutor());
            return new Stitched(empty, Set.of(), errors);
        }
    }

    /** Live particles hold UVs into the old atlas; drop them before its contents change. */
    private static void clearLiveParticles() {
        var engine = (ParticleEngineAccessor) Minecraft.getInstance().particleEngine;
        for (ParticleRenderType type : List.of(EdenRenderTypes.SYNCED_TRANSLUCENT, EdenRenderTypes.SYNCED_ADDITIVE)) {
            Queue<Particle> queue = engine.eden_particles$getParticles().get(type);
            if (queue != null) queue.clear();
        }
    }

    private TextureAtlas atlas() {
        if (atlas == null) {
            atlas = new TextureAtlas(LOCATION);
            Minecraft.getInstance().getTextureManager().register(LOCATION, atlas);
        }
        return atlas;
    }
}
