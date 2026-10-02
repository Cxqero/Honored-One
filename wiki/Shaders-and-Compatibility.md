# 🌌 Shaders and Compatibility

## Shader packs
Shader packs re-shade everything the world draws, and every pack does it differently: some relight glowing effects as
solid surfaces, some fog them out, some drop them. So with a pack active, **the energy effects are drawn on top of the
pack's finished image**, still hidden correctly behind terrain. Blue, Red, Purple, the nuke and the domain look the same
on every pack, while the pack still shades the world, mobs and your skin.

- Effects never cast shadows in the pack's shadow pass
- Unlimited Void hides the world correctly under Sodium and Iris
- Cutscene inserts, flashes, colour grades, impact frames and speed lines are drawn on the HUD, so packs never touch them
- Setting: **Client → Effects after shaderpack** (on). Turn it off to let the pack render the effects itself

**Tested with:** Sodium 0.8.13 + Iris 1.8.14 + **Bliss**, and Sodium 0.6.13 + Iris 1.8.8 + Bliss.

## Other mods
| Mod | Status |
|---|---|
| Sodium, Iris | ✅ supported |
| Reese's Sodium Options | ✅ works |
| C2ME | ✅ works |
| Mod Menu, YetAnotherConfigLib | ✅ settings screen |
| Other mods using `R`, `G`, `V`, `B`, `Z`, `N` | ⚠️ the Gojo key wins in a world; rebind one of them |

## Low render distance
The cutscene camera stays inside the area your game has loaded, so even at 6 chunks the nuke's wide shot shows the whole
explosion. It moves in and widens the lens to keep the shot framed.

## Multiplayer
Made for **singleplayer**. It isn't built or tested for servers.
