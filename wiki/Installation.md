# 📦 Installation

## You need
| | |
|---|---|
| Minecraft Java Edition | **1.21.1** |
| Fabric Loader | **0.16 or newer** |
| [Fabric API](https://modrinth.com/mod/fabric-api) | **required** |
| [Mod Menu](https://modrinth.com/mod/modmenu) + [YetAnotherConfigLib](https://modrinth.com/mod/yacl) | recommended, for the in-game settings screen |
| [Sodium](https://modrinth.com/mod/sodium) + [Iris](https://modrinth.com/mod/iris) | optional, for shader packs |

Player Animator and bendy-lib (the libraries behind the casting animations) are **bundled inside the jar**, so you don't
install them yourself.

## With the Modrinth App (recommended)
1. Create a profile: **Minecraft 1.21.1**, loader **Fabric**
2. Add **Fabric API** (and Mod Menu + YACL, and Sodium + Iris if you want shaders) from *Browse content*
3. Download `gojo-limitless-<version>.jar` from the [Releases](https://github.com/{{REPO}}/releases) page
4. In the profile: **Add content → From file** and pick the jar
5. Press **Play**

## With the vanilla launcher
1. Install [Fabric Loader](https://fabricmc.net/use/installer/) for 1.21.1
2. Put Fabric API and `gojo-limitless-<version>.jar` in `.minecraft/mods`
3. Start the **Fabric** profile

## Updating
Delete the old `gojo-limitless-*.jar` from your `mods` folder before adding the new one: two copies make Fabric refuse
to start.

## Before you cast
**Terrain destruction is on by default.** Test in a world you don't mind losing, or turn destruction off under
**Settings → General** first. The 200% Hollow Purple and the remote Hollow Purple are *massive*.

*Singleplayer only. Don't bring it onto public servers.*
