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

### Offline
Minecraft has no anti-cheat in the game itself. The online parts are multiplayer servers and Realms. Keeping it
offline means: play the mod in Singleplayer worlds only, and don't join public servers with this profile.
