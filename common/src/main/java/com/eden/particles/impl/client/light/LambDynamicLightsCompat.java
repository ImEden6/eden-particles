package com.eden.particles.impl.client.light;

import com.eden.particles.Constants;
import com.eden.particles.impl.runtime.LightSink;
import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehavior;
import dev.lambdaurora.lambdynlights.api.behavior.DynamicLightBehaviorManager;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

/** LambDynamicLights' entrypoint: only it loads this class, so the rest of the mod never touches its API. */
public final class LambDynamicLightsCompat implements DynamicLightsInitializer {

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        DynamicLightBehaviorManager manager = context.dynamicLightBehaviorManager();
        DynamicLights.INSTANCE.install((x, y, z, level) -> {
            PointLight light = new PointLight(x, y, z, level);
            manager.add(light);
            return light;
        });
        Constants.LOG.info("Effect lights enabled through LambDynamicLights");
    }

    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager manager) {
    }

    /** Fades linearly with distance, like block light. Fields are read on LambDynamicLights' thread. */
    private static final class PointLight implements DynamicLightBehavior, LightSink.Light {

        private volatile double x, y, z;
        private volatile float level;
        private volatile boolean removed;
        private long lastPos = Long.MIN_VALUE;
        private int lastLevel = -1;

        PointLight(double x, double y, double z, float level) {
            update(x, y, z, level);
        }

        @Override
        public void update(double x, double y, double z, float level) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.level = level;
        }

        @Override
        public void end() {
            removed = true;
        }

        @Override
        public double lightAtPos(BlockPos pos, double falloffRatio) {
            double dx = pos.getX() + 0.5 - x, dy = pos.getY() + 0.5 - y, dz = pos.getZ() + 0.5 - z;
            return Math.max(0, level - Math.sqrt(dx * dx + dy * dy + dz * dz) * falloffRatio);
        }

        @Override
        public BoundingBox getBoundingBox() {
            int r = Mth.ceil(level);
            int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
            return new BoundingBox(bx - r, by - r, bz - r, bx + r, by + r, bz + r);
        }

        @Override
        public boolean hasChanged() {
            long pos = BlockPos.asLong(Mth.floor(x), Mth.floor(y), Mth.floor(z));
            int rounded = Math.round(level);
            if (pos == lastPos && rounded == lastLevel) return false;
            lastPos = pos;
            lastLevel = rounded;
            return true;
        }

        @Override
        public boolean isRemoved() {
            return removed;
        }
    }
}
