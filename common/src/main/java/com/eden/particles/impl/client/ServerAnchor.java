package com.eden.particles.impl.client;

import com.eden.particles.api.EffectAnchor;
import com.eden.particles.impl.AnchorMath;
import com.eden.particles.impl.net.AnchorData;
import com.eden.particles.impl.runtime.Anchor;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.Optional;

final class ServerAnchor implements Anchor {

    private final AnchorData from;
    private final Optional<AnchorData> to;
    private EffectAnchor resolved;

    ServerAnchor(AnchorData from, Optional<AnchorData> to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean resolve(Placement out) {
        if (resolved != null) return AnchorMath.place(resolved, out);
        Level level = Minecraft.getInstance().level;
        EffectAnchor start = from.toAnchor(level);
        EffectAnchor anchor = to.map(end -> EffectAnchor.between(start, end.toAnchor(level))).orElse(start);
        if (!from.waiting(level) && to.map(end -> !end.waiting(level)).orElse(true)) resolved = anchor;
        return AnchorMath.place(anchor, out);
    }
}
