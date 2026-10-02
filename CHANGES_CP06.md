# Checkpoint #6 — what was done and added (2026-09-30)

Everything here runs on **your own Minecraft character and skin**. There is no Gojo model anywhere. Hand signs use
blocky Minecraft-style fingers in your skin's arm colour.

## Animations: reworked around Gojo's gestures, with weight
- Every casting animation was rebuilt (playerAnimator + bendy-lib, embedded): planted stances, strides, recoil the body
  absorbs, overlap and follow-through, breathing holds. Elbows, knees and torso bend. Your arms animate in first
  person too.
- **Blocky fingers** (third person, close-ups, first person) for the Red sign, the Purple sign and the domain seal.
- **Canon chants per technique** (see `docs/LORE.md` §4b). Each move has its own words and voice cues; Red's
  incantation is spoken in the nuke.

## Remote Hollow Purple ("the nuke", key B): redone shot for shot after the clip
- Red forms at your fingertip with rays, then the two-finger sign in close-up on your own model, then the throw.
  The camera follows Red into the sky.
- **Six Eyes close-up** on your own face (cyan halo, iris and glint).
- Blue far across the sky, then the **cyan boost beam and flare**, and brush-stroke spiral arms winding in.
- The leap and the rush. Red and Blue close on the target with lightning between them.
- Blender inserts (VFX only, no characters): the **collision**, a silent **implosion** and the **star glint**.
- The bloom: a white heart in a magenta sky with you in **silhouette** against it. A soft screen grade leaves the
  heart itself untouched. The flash comes later and softer.
- The camera stays clear of terrain and tall grass. Wild plants are flattened where the camera stands.

## After your video (round 2)
- **The nuke now explodes.** Once the purple mass forms and glints, it detonates:
  - a huge roiling purple fireball swells, then cools into dark smoke glowing from inside
  - the smoke climbs into a column with a cap
  - a shock sphere and a ring race out along the ground, pushing a wall of dust
  - lightning, streaks and embers
  - a new wide shot shows the whole blast (the cutscene is 2 s longer), and a new explosion sound
  - the smoke lingers after the cutscene but thins around your camera, so you're not stuck inside it
- **Hollow Purple and 200% explode where they hit.** At the first terrain or creature they strike:
  - a flash, a purple fireball, a shock dome, a ground ring with dust, and embers
  - creatures around it are thrown and hurt
  - the mass keeps flying and erasing until it fades, as before. New impact sound.
- **Blue missing in hills (your video):** the nuke lays Red and Blue out 22 blocks to either side of the target and
  the camera shoots from below. On a mountainside, Blue ended up inside the mountain and Red half-buried in the
  hillside. That's why it looked like a shaders difference. The whole layout now rises clear of the terrain
  around the target, and the one shot anchored behind you tilts up to match.
- **The oval in the sky during Red:** that was the edge of the red screen vignette. Both vignettes now rise
  smoothly to the rim with no visible edge. Energy effects also no longer fade into distance fog.
- **The pink ball left after the nuke:** the bloom now gives way to the explosion. The long white-out is now a
  short flash.
- **Unlimited Void's build-up is longer:**

  | Phase | Ticks | Duration |
  |---|---|---|
  | White | 44–62 | 0.9 s |
  | Ink, with the neon lines manifesting | 62–102 | 2 s |
  | Tunnel | 102–158 | 2.8 s |
  | Void opens | 160 | |

  More and more lines draw themselves out of the ink and pulse, torn fragments of the void show through, and the
  tunnel gathers speed into the flash. New camera track, a longer seal hold and a longer tunnel sound.

## Domain Expansion: Unlimited Void (key Z): new
- **Hold Z: the full domain** (~6.5 s), after S1E7:
  1. Six Eyes close-up.
  2. The seal, in close-up on your blocky fingers.
  3. The world turns white with a pink cast on the characters.
  4. Ink bursts in with neon lines.
  5. A tunnel of information, then the flash.
  6. **The void**.
- **Tap Z: the 0.2-second domain** (Shibuya). It opens for an instant and everyone in range is paralysed (10 s by
  default). You keep your buffs (your call, listed as a deviation in `docs/LORE.md` §7).
- **The inside is made in Blender** (Cycles):
  - a 360° void panorama (deep navy and teal nebula, dense stars, violet dust)
  - the giant black hole as a seamless animated loop, with the ring turning, the white limb and a smoke stream pouring off
  - volumetric smoke wisps drifting around you in 3D
- The world outside disappears while you're inside: terrain, sky, clouds, weather and anything beyond the barrier.
  Everyone floats in the void (S1E7's look).
- **Paralysis ("sure-hit")**: everyone caught can't move, turn, attack or use items. Their screen floods with
  information (neon streaks). Bosses shake it off after 3 s (adjustable).
- **Cancel it any time: press Z again.** The void is wiped away by white ink and you're back in the world. The
  cooldown starts when the domain ends, so the instant domain is ready again straight away.
- Sounds: seal, white, ink, tunnel, open, the void's ambience (a seamless loop), collapse and instant.
- **Settings** (new "Domain Expansion" category, plus Client toggles):
  - Gameplay:
    - radius and duration
    - press again to collapse
    - how long victims stay paralysed after it ends
    - instant-domain radius and paralysis time
    - spare those touching you
    - who is caught: players, passive mobs, hostile mobs
    - how fast bosses shake it off
    - your buffs (Strength, Speed, Resistance, Regeneration levels, and how long they last after the instant domain)
    - root while expanding
    - cooldown
  - Client:
    - hide the world inside
    - the black hole
    - the wisps
    - void brightness
    - the barrier
    - show the expansion to victims
    - the victims' information flood

## 200% Hollow Purple: release reworked
- The mass is a **deep violet body** with a burning rim, dim turbulent plasma inside, the two infinities still
  turning, lightning crawling over it and a small white-violet heart. It reads as mass, not as a lamp.
- New release camera: from the side, then low behind you, then far and wide, then the launch.
- Softer flashes at the collision and the launch. Plants flattened at the charge so you're never hidden in grass.

## Shader support (Iris + Sodium)
- Every energy effect is drawn **after the shaderpack has finished the frame**, with vanilla shaders and
  depth-tested against the scene. Blue, Red, Purple, the nuke and the domain look the same on every pack and
  without one. Packs used to re-shade them their own way: Bliss tore the 200% Purple mass into black fragments.
  The pack still shades terrain, mobs and your skin (lighting, shadows, fog).
- Energy effects never cast shadows in the pack's shadow pass.
- Inside Unlimited Void the terrain stays hidden under Sodium (plus a direct hook on Sodium's own chunk draw for
  newer Sodium versions).
- Tested in the dev client with **Sodium 0.6.13 + Iris 1.8.8 + Bliss v2.1.2**: Blue, Red, 200% Purple, the full
  domain (white, ink, tunnel, void, black hole, ink wipe on cancel) and the whole nuke. The effects no longer
  depend on the pack, so other packs (Eclipse, Complementary) should look the same (not tested here).
- New client option **Effects after shaderpack** (on). Turn it off to let the pack shade the effects itself.

## Files of note
- Blender: `blender/inserts/build_void_interior.py` (void panorama, black hole loop, wisps),
  `blender/inserts/build_domain_assets.py`, `blender/inserts/build_nuke_inserts.py`,
  `blender/cutscene/{nuke_preview,purple_preview}.py`, `blender/osl/{ribbon_brush,astroid,blackhole,ink}.osl`
- Mod: `DomainEntity`, `DomainAbility`, `VoidParalysisEffect`, `DomainClient`, `DomainRenderer`, `LoopTextures`,
  `NukeRenderer`, `PurpleRenderer`, `CutsceneDirector` (hard cuts, focus-aware terrain and plant avoidance)
- Audio: `audio/build_domain_sfx.py`
