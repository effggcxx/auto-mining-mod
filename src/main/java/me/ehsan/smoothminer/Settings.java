package me.ehsan.smoothminer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** User-editable options, stored in config/smoothminer.json and editable in-game through Mod Menu. */
public final class Settings {
    public static final String DEFAULT_TARGET = "minecraft:diamond_ore";

    public static final int RADIUS_MIN = 1;
    public static final int RADIUS_MAX = 8;
    public static final int SPEED_MIN = 10;
    public static final int SPEED_MAX = 360;
    public static final int SPEED_STEP = 5;
    public static final int SMOOTH_MIN = 1;
    public static final int SMOOTH_MAX = 10;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Settings instance;

    /** Registry ID of the block to mine, e.g. minecraft:diamond_ore. */
    public String targetBlock = DEFAULT_TARGET;
    /** Search distance in blocks around the player. */
    public int searchRadius = 4;
    /** Maximum camera turn speed in degrees per second. */
    public float maxTurnSpeed = 60.0f;
    /** 1 = quick and direct, 10 = very soft easing. */
    public int smoothness = 7;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("smoothminer.json");
    }

    public static Settings get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Settings load() {
        Path file = file();
        try {
            if (Files.exists(file)) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    Settings loaded = GSON.fromJson(reader, Settings.class);
                    if (loaded != null) {
                        loaded.sanitize();
                        return loaded;
                    }
                }
            }
        } catch (Exception ignored) {
            // Fall back to defaults below.
        }
        Settings defaults = new Settings();
        defaults.save();
        return defaults;
    }

    public void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception exception) {
            System.err.println("[SmoothMiner] Could not save config: " + exception.getMessage());
        }
    }

    /** Clamps every value into its valid range and normalises the block ID. */
    public void sanitize() {
        searchRadius = Math.max(RADIUS_MIN, Math.min(RADIUS_MAX, searchRadius));
        maxTurnSpeed = Math.max(SPEED_MIN, Math.min(SPEED_MAX, maxTurnSpeed));
        smoothness = Math.max(SMOOTH_MIN, Math.min(SMOOTH_MAX, smoothness));

        Identifier id = targetBlock == null ? null : Identifier.tryParse(targetBlock.trim());
        targetBlock = id == null ? DEFAULT_TARGET : id.toString();
    }

    public void copyFrom(Settings other) {
        this.targetBlock = other.targetBlock;
        this.searchRadius = other.searchRadius;
        this.maxTurnSpeed = other.maxTurnSpeed;
        this.smoothness = other.smoothness;
    }

    public Settings copy() {
        Settings copy = new Settings();
        copy.copyFrom(this);
        return copy;
    }

    /** Looks up a block by ID. Returns null for unknown IDs and for air. */
    public static Block resolveBlock(String idText) {
        if (idText == null) return null;
        Identifier id = Identifier.tryParse(idText.trim());
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return null;
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        return block.defaultBlockState().isAir() ? null : block;
    }
}
