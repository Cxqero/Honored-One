# Technical Plan

## 1. Core principle: Blender is the factory, Minecraft is the player
Minecraft cannot play Blender scenes. Everything is authored in Blender and **exported into formats the game can render in real time**. Quality comes from what survives the export:

| Authored in Blender | Exported as | Used for |
|---|---|---|
| Meshes & animated shells | Custom mesh JSON/OBJ (+ per-frame transforms or vertex-animation data) | Orb layers, spiral ribbons, shockwave rings, lightning, debris |
| Cycles renders | PNG flipbook atlases + metadata (fps, frames, blend mode) | Plasma, sparks, smoke, glow cards |
| Camera animation | Camera track JSON (pos / rot / FOV per frame, relative to caster & target) | In-engine cutscenes |
| Player-model rig animation | Player Animation Library keyframe format | Throws, casting poses, domain pose |
| Stylized 2D shots | Pre-rendered frame sequences / video | Full-screen cutscene inserts (collision streaks, implosion, star glint, hand-sign close-ups) |

All exporters are Blender Python scripts in `blender/` so every asset can be rebuilt headlessly.

## 2. Shader compatibility (Iris / Bliss / Eclipse / Complementary)
**Fact:** Iris ignores custom shaders from mods whenever a shaderpack is loaded (Iris docs: `docs/development/compatibility/core-shaders.md`). Therefore:

- **Shaderpack path** — VFX render only through vanilla render types that packs already handle (translucent/emissive entity-style types, beacon/eyes/lightning-style glow). The shaderpack's own bloom/tonemapping then applies on top. No custom GLSL → no black boxes or invisible orbs.
- **Vanilla path (no pack)** — same assets + custom GLSL extras: gravitational lensing around Blue, heat haze, chromatic aberration, radial blur.
- **Auto-detect** via Iris API at runtime (`isShaderPackInUse`), with manual override in config.
- **Full-screen inserts** draw on the HUD/GUI layer, which shaderpacks do not process → full Cycles quality with any pack.
- **Not using Veil** — maintainers state it doesn't work with Iris yet (FoundryMC/Veil issue #34).
- **Test matrix:** No pack · Bliss · Eclipse · Complementary Reimagined (+ Sodium alone).

## 3. Cutscenes
- In-engine: Blender camera path played relative to the caster/target → real world, player skin, shaderpack lighting, real crater.
- Letterbox bars, HUD hidden, skippable, toggleable.
- Singleplayer: world keeps ticking but targets are held in place for the cinematic duration (configurable).
- Hand-sign close-ups: Minecraft hands have no fingers → Blender-rendered inserts of a hand.

## 4. Destruction engine
- Server-side, **per-tick block budget** (configurable), direct chunk-section writes, deferred light updates, **no item drops**.
- Shapes: sphere (Blue collapse, nuke), capsule/trench (Purple, 200% Purple), cone/crater (Red).
- Technique-specific behavior (from lore):
  - **Blue** — blocks pulled *inward*, crushed into the singularity.
  - **Red** — blocks blasted *outward*.
  - **Purple** — blocks *erased*: clean, smooth-edged voids, no debris.
- Flying debris is client-side instanced geometry (not entities) → thousands of pieces at low cost.
- Respects `mobGriefing` (toggle), global + per-move destruction toggles, block blacklist (bedrock etc.).

## 5. Config (Mod Menu + YACL)
**Gameplay (server side of the integrated server):** per move — damage, radius/size, range, charge time, pull/knockback strength, destruction on/off, trench length (Purple), crater radius (nuke), domain radius & duration, paralysis durations, self-damage from nuke; global — destruction master switch, block budget per tick, blacklist, mobGriefing respect.
**Client:** VFX quality tier (Ultra/High/Medium/Low), debris count, flipbook resolution, cutscenes on/off/skippable, letterbox, camera shake, **flash intensity (photosensitivity)**, FOV effects, subtitles (incantations, JP/EN), per-sound volume, render path override (Auto / Force shaderpack-safe / Force fancy).

## 6. Controls
5 rebindable keys. Tap vs hold detected client-side (threshold configurable, default 250 ms). Charge ring on HUD (shader-safe).

## 7. Dependencies
- Fabric Loader + Fabric API (1.21.1)
- Player Animation Library (1.21.1 Fabric) — player poses
- YACL + Mod Menu — config UI
- Iris / Sodium — optional (compile-only API for detection)

## 8. Build order
0. **Foundations** — mod skeleton, move/timeline system, networking (client↔integrated server), config, Blender exporters, both render paths, domain terrain-hiding prototype.
1. **Lapse: Blue** (tap + Maximum Output) — vertical slice; proves the full pipeline under shaderpacks.
2. **Reversal: Red** (tap + incantation).
3. **Hollow Purple** (tap + full-incantation 200%) with cutscene.
4. **Remote Hollow Purple (nuke)** — full storyboard.
5. **Unlimited Void** (0.2s + full).
6. SFX pass, polish, performance, config UX.

Each phase ends with a **checkpoint zip** + a test build for Cxqero to try in-game.

## 9. Environment notes
- Cloud container: Java 21, 2 CPU cores, ~7 GB RAM, no GPU. Blender headless (Cycles CPU). Fabric Maven + Blender downloads reachable.
- Visual sign-off happens on Cxqero's machine with Bliss/Eclipse.
