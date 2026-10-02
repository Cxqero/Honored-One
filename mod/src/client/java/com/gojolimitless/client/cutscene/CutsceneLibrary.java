package com.gojolimitless.client.cutscene;

import com.gojolimitless.GojoLimitless;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resource.Resource;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

/** Loads assets/gojolimitless/cutscenes/*.json (camera tracks exported from Blender). */
public final class CutsceneLibrary implements SimpleSynchronousResourceReloadListener {
    private static final Map<String, CameraTrack> TRACKS = new HashMap<>();

    public static CameraTrack get(String id) { return TRACKS.get(id); }

    @Override public Identifier getFabricId() { return Identifier.of(GojoLimitless.MOD_ID, "cutscenes"); }

    @Override
    public void reload(ResourceManager manager) {
        TRACKS.clear();
        Map<Identifier, Resource> found = manager.findResources("cutscenes", id -> id.getNamespace().equals(GojoLimitless.MOD_ID) && id.getPath().endsWith(".json"));
        for (var en : found.entrySet()) {
            String path = en.getKey().getPath();
            String name = path.substring(path.lastIndexOf('/') + 1, path.length() - 5);
            try (Reader r = en.getValue().getReader()) {
                TRACKS.put(name, CameraTrack.parse(name, JsonParser.parseReader(r).getAsJsonObject()));
            } catch (Exception ex) {
                GojoLimitless.LOG.error("Bad cutscene {}", en.getKey(), ex);
            }
        }
        GojoLimitless.LOG.info("Loaded {} cutscene camera tracks", TRACKS.size());
    }
}
