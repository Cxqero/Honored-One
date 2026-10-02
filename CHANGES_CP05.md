# Checkpoint #5 — what was done and added (2026-09-29)

## New technique: Remote Hollow Purple — "the nuke" (key **B**, canon ch. 234–235)
Aim at a creature (or the ground) and tap B. A ~27-second cinematic that follows Cxqero's reference clip beat by beat:
1. Red forms at your raised fingertip, laser rays lance out, the screen edges bleed red, your Six Eyes start to glow.
2. **Hand-sign close-up** (Blender-rendered insert): two fingers raised, Red igniting between them, rays, the storm sky flooding red.
3. You hurl Red into the sky; the camera follows it up.
4. **Six Eyes close-up**: your own skin's eyes blaze cyan (new emissive eye layer, works with shaderpacks).
5. Part 2 of the cutscene: Blue forms far across the sky from Red; a cyan beam rises from you into it.
6. **Blue incantation after the fact** — 位相・黄昏・智慧の瞳 (Phase, Twilight, Eyes of Wisdom): subtitles + cues, cyan spiral arms wind into Blue.
7. Blue's pull tears your target (and creatures near it) off the ground and holds them in the air; Blue carries you up to hover behind them.
8. **Purple incantation** — 九綱・偏光・烏と声明・表裏の間: Red and Blue rush in on the target, arcing lightning between them.
9. Collision insert → implosion insert (silent beat) → the **imaginary mass blooms**: white-hot heart, violet erasure field sweeping outward, lightning, shock rings, the whole sky turns magenta, title card 茈.
10. Star-glint insert, white-out, fade onto an aerial view of the crater. You drift down with slow falling.

Gameplay: erases a sphere (default radius 64 → a ~120-block crater) with its own block budget so the crater keeps pace with the visuals; everything inside takes 5000 damage; items/projectiles vanish. **You take only minimal damage (canon), never lethal.** Other techniques are locked while it runs. Skip with Enter (skipping part 1 also skips part 2).
New config category "Remote Hollow Purple": max/min distance, lift height, lift radius, max lifted, radius, expansion time, damage, erase terrain, blocks per tick, self damage, slow-fall time.

Verified headlessly on the plains test world: whole sequence runs, caster flies to the hover point, crater forms (floor at y≈29 from y≈72 ground), no fall damage on landing, no crash.

## New assets
- **Blender hand rig** (`blender/lib/handrig.py`): procedural stylised right hand + uniform sleeve, voxel-fused, skinned, posable (poses: open, relaxed, sword = Red sign, cross = Unlimited Void seal). Manga cel shading (`osl/toon.osl`) with ink outlines and contact shadows.
- **Insert pipeline** (`blender/lib/insertlib.py`): JPEG/PNG sequences, camera helper, screen-space vignette, glare/dispersion compositor.
- Inserts: `nuke_red_sign` (done). `nuke_collide`, `nuke_implode`, `nuke_glint` scripted and previewed; **still rendering** (not in this jar yet — the cutscene simply skips them).
- Camera tracks `nuke_ground`, `nuke_sky`; player animations `nuke_red`, `nuke_throw`, `nuke_air`, `nuke_brace`.
- 9 new original sound effects (`audio/build_nuke_sfx.py`): throw, boost, incantation cue, sky hum, converge, collide (cuts to silence), bloom, glint, aftermath.
- OSL shaders: toon, storm sky, orb glow, filament, fresnel orb, star glint, camera vignette.

## Engine improvements
- Inserts: JPEG support, bulk decode, pre-decoding before they're due, newest-ready-frame playback (no stalls).
- Cutscenes: multi-part cutscenes (a skipped part skips its follow-ups).
- HUD: multiply colour grade (the magenta sky) and white-out, both drawn on the HUD so shaderpacks can't break them.
- Player hand position is now computed from the actual rendered arm (effects sit exactly at the fingertip).
- Poses can now drive the legs too.
- Config screen: friendly category names.
- Checkpoints: the source zip and the test jar are now separate files (the combined zip went over the 30 MB upload limit).

## Started: Domain Expansion: Unlimited Void
- Unlimited Void hand seal pose (index and middle fingers crossed) on the hand rig.
- Blender scripts for the void's textures (black hole with prismatic rim, streaming smoke, navy starfield, nebula fragments, ink flipbook) and inserts (seal, fast seal, 0.2 s black-hole flash, white ink wipe) — first look-dev passes done, not yet in the mod.
- Technical plan written into PROGRESS.md ("Domain plan").

## How to test
Install as before (TESTING.md). Keys: R Blue · G Red · V Purple · **B remote Purple** · Z domain (not yet) · N Infinity · Enter skip.
Debug: `/limitless cast nuke`.
