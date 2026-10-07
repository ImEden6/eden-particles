package com.eden.particles.impl.client;

import com.eden.particles.Constants;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** {@code config/eden_particles-client.json}: servers the player always allows to send particle images. */
final class ClientConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        List<String> always_allow_images_from = new ArrayList<>();
    }

    private Data data;

    boolean alwaysAllowsImages(String server) {
        return load().always_allow_images_from.contains(server);
    }

    void alwaysAllowImages(String server) {
        if (load().always_allow_images_from.contains(server)) return;
        data.always_allow_images_from.add(server);
        save();
    }

    private Data load() {
        if (data != null) return data;
        Path file = path();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                Data read = GSON.fromJson(reader, Data.class);
                if (read != null && read.always_allow_images_from != null) data = read;
            } catch (IOException | JsonParseException e) {
                Constants.LOG.warn("Couldn't read {}; starting with defaults: {}", file, e.getMessage());
            }
        }
        if (data == null) data = new Data();
        return data;
    }

    private void save() {
        Path file = path();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            Constants.LOG.warn("Couldn't save {}: {}", file, e.getMessage());
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("eden_particles-client.json");
    }
}
