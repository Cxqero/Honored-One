# PROGRESS — Gojo Satoru Mod (Fabric 1.21.1)

> **Resuming in a new chat?** Upload the latest checkpoint zip and say:
> "Continue the Gojo mod from this checkpoint. Read PROGRESS.md first."
> Everything needed to pick up is in this file + `docs/`.

## Current status
- **Phase (session 2):** everything from CP05 + **the nuke reworked shot for shot after the clip** (on the player's
  own model, blocky fingers) · **Domain Expansion: Unlimited Void built** (full + 0.2 s, cancel on recast, the
  interior made in Blender: void panorama, animated black hole loop, volumetric wisps) · **200% Purple release
  reworked** (camera, mass look) · **shader support** (Iris + Sodium: effects drawn after the pack; tested with
  Bliss).
- **Latest checkpoint:** #7 — 2026-09-30 (see CHANGES_CP07.md). Before it: #6 — 2026-09-30 (see CHANGES_CP06.md; the final zip includes round 2 after the user's video:
  the nuke's explosion, Purple's impact explosions, the nuke layout in hills, the longer domain build-up, the
  finished black hole loop).
- **Session 2 feedback, all addressed or in progress:** the nuke "kinda bad" → reworked; animations "weird, no
  weight" → weight pass + rework; chants "same chant for every move is lore-breaking" → per-technique canon chants
  (LORE §4b); "keep the Minecraft character" → no Gojo model anywhere; domain "looks odd" → rebuilt, interior made in
  Blender; "cancel the domain" → press again; "lots of settings" → Domain + Client sections; "full shader support".

## Locked decisions
| Topic | Decision | Date |
|---|---|---|
| Minecraft / loader | 1.21.1, Fabric, Java 21 | 2026-09-29 |
| Blender | Runs in the cloud session (headless, CPU). No local connector. | 2026-09-29 |
| Controls | 5 dedicated rebindable keybinds; tap vs hold on the same key | 2026-09-29 |
| Resource limits | None (no cursed energy bar, no cooldowns). Canon-justified: Six Eyes efficiency + RCT repairing burnout. | 2026-09-29 |
| Multiplayer | Singleplayer only (integrated-server split kept clean, no multi-client sync) | 2026-09-29 |
| Shader compat | Energy effects use vanilla programs only. With an Iris pack active they're collected during the world pass and drawn after the pack's frame (`DeferredVfx`), vanilla shaders, depth-tested; terrain, entities and skins stay with the pack | 2026-09-30 |
| Cutscenes | In-engine camera paths authored in Blender + Blender-rendered full-screen inserts (HUD layer, shader-safe) | 2026-09-29 |
| Character | No Gojo model; the player's own skin is the caster | 2026-09-29 |
| Audio | Original SFX; every sound is a resource-pack slot so it can be replaced | 2026-09-29 |
| Lore | Canon-first. See `docs/LORE.md`. Deviations must be listed there. | 2026-09-29 |
| 0.2s domain | Canon paralysis of everyone in range **+ keep the caster buffs** (Cxqero's call; listed as deviation) | 2026-09-29 |
| 200% Hollow Purple | **Solo-usable** via full incantation + hand signs (Cxqero's call; listed as deviation) | 2026-09-29 |
| Domain name | **Domain Expansion: Unlimited Void** (無量空処) | 2026-09-29 |
| Infinity passive | Added (toggle key, default on) | 2026-09-29 |
| Cutscene cast (session 2) | **The player's own model and skin, in-engine, in every cutscene.** No Gojo character model, anime or otherwise. Blender makes the camera tracks, body animations, VFX and VFX-only full-screen inserts (collision, implosion, glint) | 2026-09-29 |
| Hand signs (session 2) | **Blocky Minecraft-style fingers** in the skin's arm colour, shown during casts, in third person, close-ups and first person. Replaces the smooth hand-rig inserts | 2026-09-29 |
| Player animation tech (session 2) | playerAnimator 2.0.4+1.21.1 (MIT) + bendy-lib 5.1 (CC-BY-4.0), embedded jar-in-jar: elbow/knee/torso bends, part positions, animated arms in first person | 2026-09-29 |

## Open decisions (waiting on Cxqero)
1. **Reference clips** — domain clip (S1 E7) received and broken down (`docs/storyboard/DOMAIN_STORYBOARD.md`). Still useful: S2 E9 (0.2s domain), S2 E3 (Maximum Output: Blue).

## Checkpoint log
| # | Date | Contents |
|---|---|---|
| 1 | 2026-09-29 | Technical plan, lore bible, nuke storyboard + reference contact sheets. No code yet. |
| 2 | 2026-09-29 | WIP: Fabric project, core framework, Blender texture pipeline, Lapse: Blue (tap + Maximum Output), Infinity, headless test harness, domain storyboard. |
| 4 | 2026-09-29 | Reversal: Red (tap + 3-word incantation, laser rays, red vignette, drilling charged shot, volumetric dust flipbook), Hollow Purple (tap + 200% with 4-word incantation and hand signs), in-engine cutscene system (Blender camera tracks, letterbox, events, inserts, terrain-aware camera, tick-synced), technique entities keep flying past simulation distance, subtitle cards for every incantation. |
| 5 | 2026-09-29 | Remote Hollow Purple ("the nuke", key B): full 27 s two-part cutscene following the reference clip, Blender hand-rig inserts, erasure crater, SFX, poses, Six Eyes glow. Domain work started (seal pose, void textures). See CHANGES_CP05.md. |
| 6 | 2026-09-30 | The nuke rework, Unlimited Void (Blender interior, black hole loop), 200% rework, shader support (effects after the pack), explosions for the nuke and Purple, the nuke in hills. See CHANGES_CP06.md. |
| 7 | 2026-09-30 | Domain SFX redesign (stereo), the mid-air domain floor, Maximum Output: Blue conducted overhead and raised to disperse, the nuke's shockwave. See CHANGES_CP07.md. |
| 3 | 2026-09-29 | First test build. Blue polished (tuning, swing-to-front throw, safe placement), original SFX (audio/), Blender player rig + blue_tap/blue_throw animations, title cards for every technique, grouped config screen, TESTING.md. |

## How to build / test (for a new session)
- **Since session 2 the project lives in git:** `Cxqero/gojo-testing`, branch `claude/mod-dev-continuation-ionrhy` (zips still go out as checkpoints). Reference frames from the anime / fan edits are git-ignored and only travel in the zips.
- **Fresh cloud container:** run `tools/setup_env.sh` (ffmpeg, numpy/scipy/pillow/nbtlib, Blender 4.5.14 LTS as the PyPI `bpy` module + the OSL path fix). `tools/blender` stands in for the Blender binary, so every `blender -b --factory-startup -P …` command below works as `tools/blender -b --factory-startup -P …`. Renders are bit-identical to the Blender 4.5 binary used before.
- **Network needed for the mod build and the test client:** maven.fabricmc.net, meta.fabricmc.net, piston-meta.mojang.com, piston-data.mojang.com, launchermeta.mojang.com, libraries.minecraft.net, resources.download.minecraft.net, maven.isxander.dev, maven.terraformersmc.com, api.modrinth.com, cdn.modrinth.com. (Maven Central, the Gradle plugin portal and PyPI were already reachable.)
- Build: `cd mod && gradle build` (Java 21, Gradle 8.14, Loom 1.13.6). If Maven Central rate-limits, use the init script in `tools/central-mirror.gradle` (copy to `~/.gradle/init.d/`).
- Blender textures: `blender -b --factory-startup -P blender/assets/build_common_vfx.py` (Blender 4.5 LTS, Cycles CPU + OSL).
- Blender player animations: `blender -b --factory-startup -P blender/anim/player_rig.py -- --preview`
- Blender title cards: `blender -b --factory-startup -P blender/gui/build_titles.py`
- Sound effects: `python3 audio/build_blue_sfx.py`, `build_red_sfx.py`, `build_purple_sfx.py` (numpy/scipy + ffmpeg/libvorbis)
- Smoke flipbook: `blender -b --factory-startup -P blender/assets/build_smoke.py -- --frames 64 --step 2 --size 256 --samples 16` (slow on CPU, ~30 min)
- Red / Purple textures: `blender/assets/build_red_vfx.py`, `blender/assets/build_purple_vfx.py`
- Cutscene camera tracks: `blender -b --factory-startup -P blender/cutscene/build_cutscenes.py -- [--preview]`
- Visual test (headless): see `tools/` scripts — `mcauto blue` runs a scripted in-game scenario under Xvfb + llvmpipe and saves screenshots to `mod/run/screenshots/`.

## Verified in the headless test client
- Blue tap + Maximum Output: forms, pulls mobs/items, tears terrain into debris, throws, collapses; crater persists.
- Infinity: 6 arrows + a zombie → 0 damage with Infinity on; damage returns when toggled off.
- Red: tap (form → flight → blast + dust) and charged (rays, vignette, subtitles, blast at the aimed point).
- Purple: tap + 200% with cutscenes (letterboxed Blender camera moves), erasure trench, lightning.
- Config screen (YACL) renders with all categories.
- Shaderpacks: `GOJO_SHADERS=stable GOJO_W=640 GOJO_H=360 tools/mcauto shaders` (Sodium 0.6.13 + Iris 1.8.8; pick the
  pack in `mod/run/config/iris.properties`, packs in `mod/run/shaderpacks/`). Runs at ~2 fps on software GL; the
  AutoTest steps wait for rendered frames. Verified with Bliss v2.1.2: every technique, the domain (terrain hidden,
  void, ink wipe) and the nuke. Eclipse needs OpenGL 4.6 (llvmpipe has 4.5; it runs with
  `MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460`) and was left untested at Cxqero's call.
- `GOJO_SHADERS=newest` (Sodium 0.8 + Iris 1.8.14 beta) needs Loom 1.16 to load in the dev client; not run.
  `SodiumTerrainMixin` also hides terrain at Sodium's own draw call, in case a newer Sodium stops drawing from
  WorldRenderer.renderLayer.
- Not verifiable here: audio (no sound device), real-GPU performance.

## Domain (as built, session 2)
See `docs/storyboard/DOMAIN_STORYBOARD.md` → "As built". Terrain inside the void is hidden **client-side by not
drawing it** (WorldRenderer.renderLayer / sky / clouds / weather / block entities cancelled while the camera is inside
the void; entities outside the barrier aren't drawn). The ground still exists, so everyone stands on nothing visible
— S1E7's floating look. Needs checking under Sodium + Iris (their terrain path goes through renderLayer too).
Timeline (ticks): seal 0-44, white 44-62, ink 62-102, tunnel 102-158, flash 158, void from 160 (lengthened after the
user's video). Blender interior: `blender/inserts/build_void_interior.py` (Cycles; `--only void_pano,void_wisps,void_hole`), streamed
by `LoopTextures` from `assets/gojolimitless/loops/`.

## Known risks (tracked)
- Shaderpacks: effects no longer depend on how a pack treats emissive geometry (drawn after it). If another mod
  changes the frame so the flush never runs, `DeferredVfx` logs it and falls back to the world pass.
- Hiding terrain inside the domain: verified under Sodium 0.6 + Iris (renderLayer cancel); newer Sodium covered by
  `SodiumTerrainMixin` (untested).
- Cloud Blender is CPU-only (2 cores) → render flipbooks at efficient sizes, full-res only for hero inserts.
- Very large destruction (nuke) → strict per-tick block budget.
- Minecraft hands have no fingers → blocky fingers (FingerFeature) since session 2.
