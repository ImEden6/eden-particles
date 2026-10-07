package com.eden.particles.impl.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

/** A {@code SpriteRef}'s frames as UV rectangles ({@code u0, u1, v0, v1} per frame), all in one atlas. */
record ResolvedSprite(boolean synced, TextureAtlasSprite first, float[] uvs) {

    int frames() {
        return uvs.length / 4;
    }
}
