# Gojo Satoru — Limitless (Fabric 1.21.1)

A singleplayer Fabric mod that gives the player Satoru Gojo's techniques from *Jujutsu Kaisen*, with Blender-made VFX, in-engine cutscenes, configurable terrain destruction and full shaderpack compatibility (Iris: Bliss, Eclipse, Complementary…).

## Moveset
| Key | Tap | Hold / charge |
|---|---|---|
| R | Cursed Technique Lapse: Blue (蒼) | Maximum Output: Blue — orbits you, grows, tears up terrain |
| G | Cursed Technique Reversal: Red (赫) | Incantation Red — "Phase, Paramita, Pillars of Light" |
| V | Hollow Technique: Purple (茈) | Full-incantation Purple (200%) — long erased trench, cutscene |
| B | Remote Hollow Purple ("the nuke") | — (single cinematic move, ch. 234–235) |
| Z | Domain Expansion: Unlimited Void — 0.2s (paralysis + buffs) | Domain Expansion: Unlimited Void (無量空処), full |

## Repo layout
```
PROGRESS.md            ← start here when resuming
docs/PLAN.md           ← technical plan / architecture
docs/LORE.md           ← canon reference + how each move maps to it
docs/storyboard/       ← cutscene storyboards + reference contact sheets
mod/                   ← Fabric project (gradle build)
blender/               ← every VFX texture, player animation, camera track and cutscene insert, as scripts
audio/                 ← every sound effect, as code (numpy/scipy → OGG)
tools/                 ← headless test harness + checkpoint script
```
See TESTING.md for install instructions and keys.
