# 🔍 Building from Source

Every file that goes into the mod is in this repository: the Java code, the textures, sounds, animations and camera
tracks, **and the scripts that made them**. Nothing is hidden. If you'd rather not trust a downloaded jar, build your own.

## Build the jar
You need a **JDK 21** or newer ([Adoptium Temurin](https://adoptium.net/) works).

```
git clone https://github.com/{{REPO}}.git
cd {{REPO_NAME}}/mod
gradlew.bat build        (Windows)
./gradlew build          (macOS / Linux)
```

The jar appears in `mod/build/libs/`. The first build downloads Gradle (its checksum is pinned in
`mod/gradle/wrapper/gradle-wrapper.properties`), Minecraft and Fabric from their official servers.

## What's where
| Folder | Contents |
|---|---|
| `mod/src/main/java` | the techniques, entities, destruction, config, networking (runs on the server side of your game) |
| `mod/src/client/java` | rendering, effects, cutscenes, animations, HUD, sounds (your client only) |
| `mod/src/main/resources/assets` | textures, sounds, animations, camera tracks, cutscene frames |
| `blender/` | Blender scripts that render every texture, animation, camera track and insert |
| `audio/` | the Python scripts that synthesize every sound effect |
| `docs/` | the plan, the lore bible and the storyboards |
| `tools/` | test and build helpers |

## What the mod does on your computer
- Reads and writes its config file, `config/gojolimitless.json`
- Changes blocks and creatures **in your own world** when you cast
- **No network access** beyond Minecraft's own: no telemetry, no downloads, no web requests

Don't take our word for it: search the code for `http`, `URL` or `Socket`.

## Rebuilding the assets (optional)
- Textures and inserts: Blender 4.5 LTS, e.g. `blender -b --factory-startup -P blender/assets/build_common_vfx.py`
- Sounds: Python 3.11 with numpy, scipy, soundfile and pyloudnorm: `python audio/build_shinjuku_sfx.py`
