# Testing the build

## Install
1. Minecraft **1.21.1** with **Fabric Loader 0.16+**.
2. Put in `mods/`:
   - `gojo-limitless-<version>.jar` (from `test-build/` in the checkpoint zip)
   - **Fabric API** for 1.21.1 (required)
   - **YetAnotherConfigLib (YACL) v3** + **Mod Menu** (optional, for the in-game settings screen)
   - **Iris + Sodium** (optional, for shaderpacks: Bliss, Eclipse, Complementary…)
3. Settings are also in `config/gojolimitless.json`.

## Default keys (rebindable under Options → Controls → Gojo Satoru: Limitless)
| Key | Technique |
|---|---|
| R | Lapse: Blue: tap = singularity at the crosshair · hold = Maximum Output (orbits you and grows; let go to throw it) |
| G | Reversal: Red: tap = quick Red · hold = incantation Red (Phase, Paramita, Pillars of Light), release to fire |
| V | Hollow Purple: tap = Purple with a short cutscene · hold = full incantation, 200% Purple with cutscene |
| B | Remote Hollow Purple ("the nuke"): aim at a creature (or the ground) and tap — Red into the sky, the Blue incantation, you and the target lifted, the Purple incantation, the erasure. ~27 s cinematic; other techniques are locked until it ends |
| Z | Domain Expansion: Unlimited Void: tap = the 0.2-second domain (everyone in range paralysed) · hold = the full domain with its cutscene · press again to collapse it |
| N | Toggle Infinity (on by default) |
| Enter | Skip a cutscene |

Debug command: `/limitless cast <blue|red|purple|nuke> [holdTicks]`, `/limitless infinity`, `/limitless reload`.

## What to look for (please send clips/screenshots)
- **Remote Hollow Purple**: does the pacing feel like the reference? Is the crater size right (config → Remote Hollow Purple → radius)? FPS during the erasure on your PC? Do the full-screen inserts play smoothly?
- How Blue reads **with your shaderpack** (Bliss / Eclipse): glow strength, colours, whether anything looks black or invisible.
- Maximum Output at full size: performance (FPS) while it tears up terrain.
- Whether the crater renders fully after the attack (any see-through holes that don't fill in).
- Sounds: volume balance, anything that sounds cheap.
- Infinity: arrows should hang in the air around you; melee from mobs should do nothing.
