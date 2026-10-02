package com.gojolimitless.client.pose;

import com.gojolimitless.GojoLimitless;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Loads assets/gojolimitless/animations/player/*.json on resource reload. */
public final class PoseLibrary implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, PoseAnimation> ANIMS = new HashMap<>();

    public static PoseAnimation get(String name) { return ANIMS.get(name); }

    @Override
    public Identifier getFabricId() { return Identifier.of(GojoLimitless.MOD_ID, "player_animations"); }

    @Override
    public void reload(ResourceManager manager) {
        ANIMS.clear();
        Map<Identifier, Resource> found = manager.findResources("animations/player", id -> id.getNamespace().equals(GojoLimitless.MOD_ID) && id.getPath().endsWith(".json"));
        for (var en : found.entrySet()) {
            String path = en.getKey().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            try (Reader r = en.getValue().getReader()) {
                JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
                ANIMS.put(name, PoseAnimation.parse(name, o));
            } catch (Exception ex) {
                GojoLimitless.LOG.error("Bad player animation {}", en.getKey(), ex);
            }
        }
        GojoLimitless.LOG.info("Loaded {} player animations", ANIMS.size());
    }
}
