# Storyboard — Remote Hollow Purple ("the nuke")

Reference: Cxqero's clip (27.4 s, 60 fps). Contact sheets in this folder (2 frames/sec, timestamps burned in).
Canon basis: ch. 234–235 (see `../LORE.md` §3).

| # | Ref time | Reference shot | In-game translation | Render method |
|---|---|---|---|---|
| 1 | 0.0–2.5s | Low angle, dusk sky. Red orb at fingertip, thin red laser-like rays radiating across frame. | Caster raises hand; Red forms at fingertip; ray cards sweep; world tint shifts toward red. | In-engine camera path + mesh/flipbook VFX |
| 2 | 3.0–4.5s | Hand-sign close-up; Red grows; red light floods scene. | Hand close-up. | Blender-rendered insert (MC hands have no fingers) |
| 3 | 5.0–5.5s | Arm thrown upward, Red launched into the sky. | Player throw animation; Red flies up and holds position. | In-engine + Player Animation Library |
| 4 | 6.0–8.0s | Eye-glow close-ups. | Close push-in on the player's head with a blue eye-glow overlay (optional, configurable). | In-engine |
| 5 | 8.5–12.5s | Blue and Red far apart in the sky; Blue trails cyan spiral ribbons (Blue incantation boost). | Blue positioned opposite Red around the target; spiral ribbon meshes swirl; subtitles: *"Phase, Twilight, Eyes of Wisdom."* | In-engine mesh ribbons + flipbooks |
| 6 | 13.0–16.0s | Wide low shot: caster airborne; target caught between; orbs close in. Purple incantation. | Caster + target lifted; wide camera; orbs converge; subtitles: *"Nine Ropes, Polarized Light, Crow and Declaration, Between Front and Back."* | In-engine |
| 7 | 16.5–17.5s | Stylized comet-streak collision; merge into a white-hot core with blue/red rims. | Full-screen insert. | Blender Cycles insert |
| 8 | 18.0–18.5s | Implosion to a single point on black; sparkles. Silent beat. | Full-screen insert; audio drops out. | Blender Cycles insert |
| 9 | 19.0–20.5s | Purple sphere blooms with lightning; shockwave ring swallows frame. | Back in engine: purple sphere + lightning meshes + ring; bloom from shaderpack. | In-engine |
| 10 | 21.0–22.5s | Wide: whole sky magenta; core with lightning; silhouettes; buildings tinted. | Wide shot; sky/fog tint; erasure sphere expands; terrain erased progressively. | In-engine + destruction engine |
| 11 | 23.0s | Inverted four-point star glint on white. | Full-screen insert. | Blender insert |
| 12 | 23.5–27.0s | White-out, slow fade to black. | Flash (intensity capped by photosensitivity setting) → fade → return to gameplay showing the crater. | HUD overlay |

Notes
- Caster takes minimal self-damage (canon, ch. 235).
- Purple = **erasure**: no flying rubble; smooth-edged crater.

## As built (session 2 rework) — timeline in ticks from the cast (`NukeEntity.T_*`)

Everything is shot in-engine on the player's own model (blocky fingers for the signs); only the collision, the
implosion and the glint are pre-rendered inserts. Clip time ≈ ticks / 20.

Layout ("C space", origin at the convergence point C, x = right, y = caster → target, z = up):
C = the target lifted `liftHeight` (18) above its ground · caster hover (0, −16, −4) · Red holds (22, 3, 9) · Blue holds (−22, 3, 9).
Camera tracks: `blender/cutscene/build_cutscenes.py` (`nuke_ground` in caster space; `nuke_sky` in C space, with a
caster-space stretch marked `space:alt`). `--preview` renders every shot with the posed character (`nuke_preview.py`).

| Ticks | Clip | Beat | Camera | Pose / VFX |
|---|---|---|---|---|
| 0–52 | 0–2.5 s | Red ignites at the raised fingertips; Red's incantation T12/30/48 | low front-left, waist up, slow push | `nuke_red`: the sign rises with each word; white-hot core, red corona, two laser rays per word |
| 52–64 | 3.0 s | the sign | over the right shoulder: the fingers against Red and the sky | red grade floods in |
| 64–84 | 3.5–4.0 s | Red beside his eye | frontal close: face, sign, Red | |
| 84–110 | 4.5–5.5 s | Red hurled straight up | low front, full figure; Red streaks out of the top | `nuke_throw`: dip, whip overhead; red flash + shake |
| 110–140 | 6.0–7.0 s | Six Eyes | head and shoulders, the two fingers before his face, pushing in | cyan eye glow with halo and glint |
| 140–158 | 7.5–8.0 s | the target's moment; Blue ignites | low beside the target looking up | cyan flash |
| 158–196 | 8.5–10 s | both in the sky; the boost streak T160 lands in Blue T169; flare ring; first word T172 | far below, both orbs | brush-stroke streak, ring + spark flare |
| 196–232 | 10.5–12.5 s | Blue's incantation T194/214 | close on Blue | brush-stroke spiral arms wind in, swallowed by T238 |
| 232–266 | 13–14 s | the target torn into the sky; he crouches, leaps T262 | behind him on the ground (caster space) | `nuke_throw` ends in the crouch |
| 266–300 | 14.5–15 s | carried up to the hover point | far below and to the side | `nuke_air`: push-off, knees tucked |
| 300–342 | 15–16 s | Purple incantation T290/308/326/344; they close in | under him, looking up past him | signs in the air |
| 342–356 | 16 s | the rush | over his shoulder | |
| 356–380 | 16.5–17.5 s | collision | insert `nuke_collide` | silence |
| 380–390 | 18–18.5 s | implosion to a point, sparks | insert `nuke_implode` | |
| 390–420 | 19–20.5 s | the point hangs; the bloom erupts T396 | his face in profile, the point ahead of him | eyes blaze; `nuke_brace` (shoved back, calm) |
| 420–462 | 21–22.5 s | the field; silhouettes | low behind him, his silhouette against the white heart | magenta grade (multiply), creatures drawn unlit |
| 462 | 23 s | glint | insert `nuke_glint` (black astroid star on white) | |
| 470–540 | 23.5–27 s | white-out, fade | high over the crater | |
