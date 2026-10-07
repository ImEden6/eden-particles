package com.eden.particles.impl.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Asks before accepting particle images from a server, like vanilla's server resource pack prompt. */
final class ImageConsentScreen extends Screen {

    enum Choice { ALLOW, ALWAYS_ALLOW, DENY }

    private final Component message;
    private final Consumer<Choice> onChoice;
    private MultiLineLabel label = MultiLineLabel.EMPTY;
    private boolean answered;

    ImageConsentScreen(int count, long totalBytes, Consumer<Choice> onChoice) {
        super(Component.literal("Particle images from this server"));
        this.message = Component.literal("This server wants to send " + count + (count == 1 ? " image" : " images")
                + " (" + Math.max(1, totalBytes / 1024) + " KiB) for its Eden's Particles effects. "
                + "They're only used by particles from this server.");
        this.onChoice = onChoice;
    }

    @Override
    protected void init() {
        label = MultiLineLabel.create(font, message, width - 50);
        int y = Math.min(height / 2 + 20, height - 30);
        addRenderableWidget(Button.builder(Component.literal("Allow"), b -> choose(Choice.ALLOW))
                .bounds(width / 2 - 155, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Always allow"), b -> choose(Choice.ALWAYS_ALLOW))
                .bounds(width / 2 - 50, y, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Don't allow"), b -> choose(Choice.DENY))
                .bounds(width / 2 + 55, y, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, height / 2 - 50, 0xFFFFFF);
        label.renderCentered(graphics, width / 2, height / 2 - 30);
    }

    @Override
    public void onClose() {
        choose(Choice.DENY);
    }

    private void choose(Choice choice) {
        if (answered) return;
        answered = true;
        minecraft.setScreen(null);
        onChoice.accept(choice);
    }

    boolean answered() {
        return answered;
    }
}
