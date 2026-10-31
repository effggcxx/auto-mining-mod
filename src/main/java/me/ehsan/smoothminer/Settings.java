package me.ehsan.smoothminer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

final class Settings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("smoothminer.json");

    String targetBlock = "minecraft:diamond_ore";
    int searchRadius = 4;
    float turnDegreesPerTick = 4.0f;

    static Settings load() {
        try {
            if (Files.exists(FILE)) {
                try (Reader reader = Files.newBufferedReader(FILE)) {
                    Settings loaded = GSON.fromJson(reader, Settings.class);
                    if (loaded != null) {
                        loaded.searchRadius = Math.max(1, Math.min(8, loaded.searchRadius));
                        loaded.turnDegreesPerTick =
                                Math.max(0.5f, Math.min(15.0f, loaded.turnDegreesPerTick));
                        if (loaded.targetBlock == null || loaded.targetBlock.isBlank()) {
                            loaded.targetBlock = "minecraft:diamond_ore";
                        }
                        return loaded;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to safe defaults.
        }

        Settings defaults = new Settings();
        defaults.save();
        return defaults;
    }

    void save() {
        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            System.err.println("[SmoothMiner] Could not save config: " + exception.getMessage());
        }
    }
}
