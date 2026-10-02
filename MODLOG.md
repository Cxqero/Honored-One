# MODLOG: Gojo Satoru: Limitless

The journal: what we tried, what worked, what broke, and the fix. Newest session at the top.
(`PROGRESS.md` holds the mod's design decisions; this file is the hands-on log.)

## Session 3: moving to this PC (2026-10-02)

### Setup
- Unpacked checkpoint 07 (`checkpoints/gojo-mod_checkpoint-07_2026-09-30_part1-source.zip` + `_part2-textures.zip`)
  into `C:\mods\minecraft`. The test jar from that checkpoint is `checkpoints/gojo-limitless-cp07.jar`.
- Git repo started here. The first commit is checkpoint 07 as it was.
- Wrote `CLAUDE.md` (project rules) and this log.

### This PC (found, nothing changed)
| Thing | Found |
|---|---|
| OS | Windows 11 Home |
| Java | Oracle JDK 22 (`C:\Program Files\Java\jdk-22`), plus a Java 8 JRE. JDK 22 can build the mod (it targets Java 21) |
| Gradle | not installed: goes in `.tools/` (see CLAUDE.md) |
| Python / Blender / ffmpeg | not installed (only needed to rebuild textures and sounds) |
| Modrinth App | `%APPDATA%\ModrinthApp`. Profile **FABRIC MOD TESTING ZONE** (1.21.1 + Fabric loader 0.19.5) is new and still empty: no mods, no worlds |

### Route
Same as before: a Fabric code mod (Loader API + Mixin). Built with Gradle + Fabric Loom 1.13.6 against Yarn mappings.
Two places to test:
1. **Dev client** (`tools/gradle.ps1 runClient`): everything stays in `mod/run/`, so it can't touch real worlds.
2. **The Modrinth profile**: the real game with the real shaderpack and GPU. Only after a backup, and with an OK
   for each change.

### Backup before the first modded launch (2026-10-02, with your OK)
- Copied from `%APPDATA%\ModrinthApp\profiles\FABRIC MOD TESTING ZONE`: `saves\MOD TESTER WORLD`, `config\`,
  `options.txt` → `backups\2026-10-02_FABRIC-MOD-TESTING-ZONE\` (31 files), plus `mods-list-before.txt`
  (the mods that were installed: C2ME, Fabric API, Iris 1.8.14 beta, Reese's Sodium Options, Sodium 0.8.13).
- **To restore:** close the game, then copy `saves\MOD TESTER WORLD`, `config\` and `options.txt` from that backup
  folder back into the profile folder (replace when asked), and delete `mods\gojo-limitless-*.jar` from the profile
  to remove the mod.

### Tools installed (all inside `.tools/`, official sources)
| Tool | Version | From | Why |
|---|---|---|---|
| Gradle | 9.5.1 | services.gradle.org (checksum verified) | builds the mod (Loom 1.17.21 needs Gradle 9.5) |
| Blender | 4.5.14 LTS (portable zip) | download.blender.org (checksum verified) | the same version the effects were made with |
| uv + Python 3.11 venv | uv 0.12.22 | astral-sh/uv GitHub | numpy/scipy/pillow/soundfile/pyloudnorm for audio + image scripts |
| ffmpeg | BtbN build (2026-10-01) | github.com/BtbN/FFmpeg-Builds (approved) | recording the game window (`tools/record.ps1`) |

Gotchas found on the way:
- uv's `python install` drops a `python3.11.exe` shim in `%USERPROFILE%\.local\bin`; removed it.
- The newest ffmpeg's NVENC needs NVIDIA driver 610+ (this PC has 581.57), so recording encodes with x264.
- Blender's OptiX (GPU) runs OSL but cut nuke_collide's filaments at a hard edge; renders stay on the CPU
  (`blender/lib/device.py`, opt in with `GOJO_BLENDER_DEVICE=GPU`). First OptiX run compiles kernels for ~8 min.
- A Gradle 9.4 daemon kept files locked; daemons are now off (`org.gradle.daemon=false`).
- Loom refuses mods built with a newer Loom: Sodium 0.8.13 needs Loom 1.17.21 in the dev client.
- In the dev client Iris 1.8.14-beta crashes (its refmap can't be read) and C2ME's nested modules don't load:
  the real Modrinth profile is the test for those.

### Real-game test, FABRIC MOD TESTING ZONE + Bliss (2026-10-02)
Setup: Fabric Loader 0.19.5, Sodium 0.8.13, Iris 1.8.14-beta.1, C2ME 0.4.0-alpha.0.29, Reese's Sodium Options,
Bliss v2.1.2, render distance 6. Recorded with `tools/record.ps1`, reviewed as contact sheets.
Everything loads and every technique runs, including Unlimited Void hiding the terrain under Sodium 0.8 (never
tested before). Found and fixed:

| Problem | Cause | Fix | Verified |
|---|---|---|---|
| R did nothing (Blue never cast) | Iris binds R to "Reload Shaders"; Minecraft gives a key to one binding only | `KeyBindingMixin`: while playing, technique keys are served first; one chat note on join names any shared key | yes, R casts Blue and holding R charges Maximum Output |
| Very quick taps sometimes ignored | keys were only polled once a tick | a press is latched until the next tick | built, to verify |
| Maximum Output let go overhead whited out the screen for ~0.2 s | the collapse flash (glow ~18 radii wide) engulfed the camera | flash shrinks and dims with camera distance | yes |
| Debris blocks flew through the camera and filled the screen | no near-camera handling | pieces shrink away within ~3 blocks of the camera | yes |
| Dust/smoke cut by the ground in a hard straight line | puffs placed below ground level | `Vfx.restOnGround`: centre ≥ 0.78 half-sizes above the ground (the puff texture is clear beyond that) | yes (Red) |
| Nuke wide shot: ~4 s of flat blue, no explosion | at 6 chunks the camera sat outside the loaded area; Sodium draws nothing from there | `CutsceneDirector.keepInLoadedArea`: camera slides in along its line, lens widens to keep the framing | yes |

Still open:
- Sound: the game reports "Failed to open OpenAL device" on this PC (Windows audio, not the mod).
- Sound design: remake toward the anime with AI SFX (fal, ElevenLabs SFX), then `audio/master.py` for the mix.
- Smoke flipbook is grainy (rendered at 16 samples); re-rendering at 96.
- Purple's leftover smoke reads near-black in daylight (by design since CP07; ask).

### Overhaul pass 1: feel (2026-10-02)
- **Body physics** on every casting animation (`CastAnimation.physics`): each part chases its animated pose through a
  damped spring (arms ω 20 ζ 0.56, torso ω 25 ζ 0.7, legs/head firm), so moves follow through and settle, and
  switching animations never pops. Setting: Client → "animationPhysics" (0 off, 1 default, 2 loose).
- **Recoil**: Red firing, Purple launching and Blue's throw kick the caster's arms, torso and head back.
- **Camera punch**: the view snaps in and springs back when your shot leaves your hands.
- **Impact frames** (HUD, inverted-strobe): nuke collision, eruption and detonation; 200% Purple's collision;
  full Red's detonation; the domain opening and the 0.2-second domain. Off when flash strength < 0.25.
- **Speed lines** (HUD): nuke throw, boost, leap and rush; Purple's launch.
- Verified in the dev client (nuke test): speed lines on the leap/rush, impact frames at the eruption.
  Physics, recoil and the punch still need a look in game.
- Note: the project's `tools/` folder was deleted by accident at one point; restored from git (`git restore tools/`).

### Sound pass: "Shinjuku" (2026-10-02)
- Brief from Cxqero: intimidating, like Gojo at the Shinjuku Showdown. No fal key, so made in code:
  `audio/heavy.py` (toolkit: pitch-dropping sub hits, metallic space cracks, saturated booms, risers, reverse
  swells, dark formant choir, temple bells, shatter, convolution halls, stereo) and `audio/build_shinjuku_sfx.py`
  (all 43 sounds, same names and lengths so every cue stays in sync). The old build_*_sfx.py stay for reference.
- Mixed by `audio/master.py`'s table. Big booms are sub-heavy, so they reach their loudness with a band-split
  saturation (sub → harmonics that small speakers can play) and at most 8 dB of limiting.
- Checked without ears: lengths, NaNs, loop seams, spectrograms (fixed layers cut off mid-ring: every layer now
  fades over its last sixth).
- Preview reel for listening outside the game: `checkpoints/sfx_preview_shinjuku.ogg` (+ .txt timestamps).
- If a fal key arrives later, AI layers can be added on top of these.

### GitHub release setup (2026-10-02)
- Public name: **Honored One - Gojo Moveset Mod** (mod id stays `gojolimitless`). Repo name default: `Honored-One`.
- `setup-github.bat` → `tools/github-setup.ps1`: signs in with the GitHub CLI (browser), creates/updates the public repo,
  pushes `main`, publishes `checkpoints/gojo-limitless-<version>.jar` as release v<version> with the changelog txt as
  notes, and pushes `wiki/` to the repo's wiki (the first time GitHub needs one "Save page" click; `{{REPO}}`,
  `{{REPO_NAME}}`, `{{VERSION}}` in the pages are filled in on push). Git uses the gh login for this repo only and the
  GitHub no-reply email. It warns if the old `gojo-testing` repo from the cloud sessions still exists.
- Wiki art: `tools/make_wiki_art.py` (frames from `.tools/caps/wiki_cand/`, recorded in game with Bliss).
- Checks before publishing: every commit authored as Cxqero with a no-reply email, no old name anywhere in the history
  or the jar, no personal paths, no anime reference frames ever committed, `um publish check` PASS (560 files), no
  network/process code in the mod (`http`, `URL`, `Socket`, `ProcessBuilder`... none).
- Gradle wrapper added (`mod/gradlew.bat`), wrapper jar checksum = Gradle's official one, distribution checksum pinned.

### Offline
Minecraft has no anti-cheat in the game itself. The online parts are multiplayer servers and Realms. Keeping it
offline means: play the mod in Singleplayer worlds only, and don't join public servers with this profile.
