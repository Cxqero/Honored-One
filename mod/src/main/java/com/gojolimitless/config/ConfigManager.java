package com.gojolimitless.config;

import com.gojolimitless.GojoLimitless;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    private static LimitlessConfig config = new LimitlessConfig();

    private ConfigManager() {}

    public static LimitlessConfig get() { return config; }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("gojolimitless.json");
    }

    public static void load() {
        Path p = path();
        if (Files.exists(p)) {
            try (Reader r = Files.newBufferedReader(p)) {
                LimitlessConfig loaded = GSON.fromJson(r, LimitlessConfig.class);
                if (loaded != null) {
                    // missing sections (older files) fall back to defaults
                    if (loaded.general == null) loaded.general = new LimitlessConfig.General();
                    if (loaded.blue == null) loaded.blue = new LimitlessConfig.Blue();
                    if (loaded.red == null) loaded.red = new LimitlessConfig.Red();
                    if (loaded.purple == null) loaded.purple = new LimitlessConfig.Purple();
                    if (loaded.infinity == null) loaded.infinity = new LimitlessConfig.Infinity();
                    if (loaded.client == null) loaded.client = new LimitlessConfig.Client();
                    config = loaded;
                }
            } catch (Exception e) {
                GojoLimitless.LOG.error("Could not read {}, using defaults", p, e);
            }
        }
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer w = Files.newBufferedWriter(path())) {
                GSON.toJson(config, w);
            }
        } catch (Exception e) {
            GojoLimitless.LOG.error("Could not write config", e);
        }
    }
}
