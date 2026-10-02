# CLAUDE.md: rules for this project

## The project
- **Game:** Minecraft Java Edition **1.21.1**, **Fabric** loader. Single-player / offline only, on my own copy.
- **Mod:** "Gojo Satoru: Limitless" (mod id `gojolimitless`). Mod author: **Cxqero**. Everything is published and
  credited under that name.
- **Mod manager:** Modrinth App. Test profile: **FABRIC MOD TESTING ZONE** (Fabric, 1.21.1, with shaders through
  Iris + Sodium), at `%APPDATA%\ModrinthApp\profiles\FABRIC MOD TESTING ZONE`.
- Use the **mod-any-game** skill and its Minecraft playbook (`references/engines/minecraft.md`).

## Rules (always follow these)
1. **Offline only.** Never touch online modes or anti-cheat. Minecraft's online parts are public multiplayer servers
   and Realms. The mod is only played in **Singleplayer** worlds (or a LAN/server I run myself). Never join a public
   server with it. Many public servers run anti-cheat plugins, and the mod isn't built for multiplayer.
2. **Official downloads only:** the tool's official GitHub, Nexus Mods, Thunderstore, mod.io, Steam, or **Modrinth**
   (my mod manager). Never mirror sites, re-uploads or forks. The build uses each project's own official Maven
   repository (Fabric, Mojang, Modrinth Maven, the library authors' own Maven) and Gradle from gradle.org.
   Approved exception: ffmpeg from github.com/BtbN/FFmpeg-Builds (the Windows build ffmpeg.org links to).
3. **Keep the game folder clean.** Mods are added through the Modrinth App profile, never into a vanilla install.
4. **Ask before touching anything outside `C:\mods\minecraft`.** That covers the Modrinth profile (mods, saves,
   config), `%APPDATA%`, `%USERPROFILE%` (including `.gradle`), the registry and system installs. List exactly
   what will change, then wait for my OK. Build tools and caches live inside this folder (`.tools/`, with
   `GRADLE_USER_HOME` pointed there) so the build never writes to my user folder.
5. **Back up first.** Before the first modded launch of a profile, copy its `saves/`, `config/` and `options.txt`
   to `backups/<date>-<profile>/` in this folder. Write the restore steps in MODLOG.md.
6. **Git.** The mod's source lives here, in git. Commit after each feature that works. Commits are authored as
   Cxqero.
7. **MODLOG.md** is the journal: what we tried, what worked, what broke, and the fix. Update it as we go.
8. **Never share or commit the game's own files or decompiled code.** No Minecraft jars, no `genSources` output,
   nothing from `mod/run/` or the Gradle cache. Anime reference frames stay out of git too (see `.gitignore`).
9. **Explain simply.** I'm new to modding.

## Where things are
| Path | What |
|---|---|
| `mod/` | the Fabric project (Gradle). Java in `mod/src/main` (both sides) and `mod/src/client` (client only) |
| `blender/` | scripts that make every texture, animation, camera track and cutscene insert |
| `audio/` | scripts that make every sound effect |
| `docs/` | plan, lore bible (`LORE.md`), storyboards |
| `PROGRESS.md` | the mod's status and decisions; `CHANGES_CP0x.md` are the checkpoint notes |
| `checkpoints/` | the checkpoint zips and test jars (not in git) |
| `backups/` | save/config backups (not in git) |
| `.tools/` | local build tools and caches (not in git) |
| `wiki/` | the GitHub wiki's pages and images (published by `setup-github.bat`) |
| `setup-github.bat` | double-click to publish or update the GitHub repo, release and wiki |

## Building (Windows, this PC)
- JDK 22 is installed (`C:\Program Files\Java\jdk-22`); the mod compiles for Java 21.
- Gradle lives in `.tools/gradle-*`, and `GRADLE_USER_HOME=.tools/gradle-home`. Use `tools/gradle.ps1 <task>` to run it.
- `tools/gradle.ps1 build` puts the jar in `mod/build/libs/`. `tools/gradle.ps1 runClient` opens a dev client
  whose files all stay in `mod/run/`.
- Keys in game: R Blue, G Red, V Purple, B Remote Hollow Purple, Z Unlimited Void, N Infinity, Enter skips a
  cutscene.
