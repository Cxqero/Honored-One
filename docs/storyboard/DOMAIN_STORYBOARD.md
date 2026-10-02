# Storyboard: Domain Expansion: Unlimited Void (無量空処)

Reference: Cxqero's clip of S1 E7 (vs Jogo), 13.7 s at 29.97 fps, 4K. The contact sheets in this folder run at 4 frames/sec. Key frames: `domain_key_ink_tunnel.jpg`, `domain_key_blackhole.jpg`.

## Shot breakdown of the reference

| # | Time | What happens | Palette |
|---|---|---|---|
| 1 | 0.0–0.75s | Extreme close-up of the Six Eyes (cyan iris), then a push-out to the face. | Warm orange light from Jogo's domain, cyan iris |
| 2 | 1.0–3.0s | The hand rises into the seal (index wrapped over middle). Subtitle: "Unlimited Void." Behind him is Jogo's volcanic domain. | Dark red-brown rock, hot highlights |
| 3 | 3.25–4.0s | **Hard cut: the whole background becomes flat pale white-pink.** Characters stay, slightly desaturated with pink/violet shading. This is the silent beat. | #F4EEF0-ish white, violet shadows |
| 4 | 4.25s | **Black ink erupts from behind him**, jagged splatter edges, with thin radial speed lines (violet, magenta, red, white) shooting out from the center. | Ink black, violet #7A3CFF, magenta #FF3FA8, red #FF3050 |
| 5 | 4.5–4.75s | The ink engulfs the frame. A **hyperspace tunnel**: radial streaks converge to a vanishing point, with white "paper-tear" nebula fragments and a dark purple starfield. | Deep purple-black #12051C, streaks as above |
| 6 | 5.0–5.5s | The camera travels sideways. Streaks turn horizontal and fly past (the "information flood"). | same |
| 7 | 5.75–6.25s | White ink blobs wipe in from the right and reveal the victim. | White |
| 8 | 6.5–7.75s | The victim, frozen, stands in the tunnel with radial streaks converging behind him. A white burst flashes at the vanishing point. | same |
| 9 | 8.0–9.0s | Close-ups of the victim's face and eye (information overload). Horizontal streaks behind. | same |
| 10 | 9.25–10.25s | Over the victim's shoulder, looking down the tunnel. White fragments drift. | same |
| 11 | 10.5s | **One frame of full white flash.** | White |
| 12 | 10.75–12.25s | **The interior: a giant black hole.** A pure black disk ringed by a thin, very bright white ring with a **prismatic rim** (warm yellow-orange to cyan to violet). Blue-white smoke swirls around the ring and **streams off to one side** as wispy nebula. Deep navy space around it. The victim stands silhouetted in front. | Void #04060C, navy #0B1426, ring white, rim hues, smoke #9FB8E6 |
| 13 | 12.5–13.5s | Exit: white ink splatter wipes across the frame horizontally. | White on black |

## In-game translation: full domain (hold)

| Beat | Duration | In-game | Method |
|---|---|---|---|
| A: Six Eyes | 0.8s | Camera pushes in on the player's face; a blue eye-glow overlay on the skin's eyes. | Blender camera path + emissive face overlay |
| B: Seal | 1.6s | Hand-seal close-up (Minecraft has no fingers). Kanji 無量空処 + "Domain Expansion: Unlimited Void" subtitle. The player's pose raises the right arm. | Blender-rendered hand insert + pose |
| C: White | 0.7s | **The world goes white.** An inside-out sphere around the caster (radius ~8 blocks) hides the terrain. Entities inside stay visible, tinted violet. Audio drops out. | Geometry (works under any shaderpack) |
| D: Ink burst | 0.5s | Black ink erupts from behind the caster, with radial neon speed lines. | Blender ink flipbook on billboards + streak geometry |
| E: Tunnel | 2.0s | Hyperspace tunnel: thousands of streaks (violet, magenta, red, white) rushing past, white nebula fragments, dark purple space. The camera orbits to the frozen victims. | Streak geometry + Blender nebula textures |
| F: Flash | 0.1s | Full white frame. | HUD overlay (capped by the flash-intensity setting) |
| G: Interior | persistent | The domain's inside: navy starfield sphere, a **giant black hole with a prismatic ring** hanging in the sky, blue-white smoke streaming off it. Terrain inside the barrier is hidden. Victims stand frozen. | Starfield sphere + black-hole and smoke layers rendered in Blender + terrain-hiding |
| H: Collapse | 0.6s | White ink splatter wipe, then the barrier dissolves and the terrain returns. | Blender ink flipbook on the HUD layer |

**Gameplay (canon):**

- A barrier (default radius 32) covers everything inside it. Every living entity inside is paralyzed. Anything touching the player (tamed pets, villagers right next to them) is excluded.
- Victims get the information-overload streaks overlaid on them.
- Duration is configurable.
- There is no cooldown (canon: reverse cursed technique repairs the technique).

## In-game translation: 0.2s domain (tap)

A compressed version, about 1.6s total:

1. Seal insert (0.35s)
2. White (0.15s)
3. Ink burst plus a streak flash (0.2s)
4. **A one-frame black-hole ring flash (the "0.2 seconds")**
5. White ink wipe back to the normal world (0.3s)

**Effect:** everything in radius (default 24) is **knocked out standing** (paralysis). Passive mobs and villagers recover later but take no harm. Bosses recover sooner (the special-grade rule). The caster gets the configured buffs (Cxqero's decision).


## As built (session 2) — `DomainEntity` timeline, ticks from the cast

The caster is the player's own model (pose `domain_seal` / `domain_instant`, blocky fingers: the seal's crossed
fingers and the collar grip). The inside of the void is made in Blender (Cycles) and streamed into the game:
`loops/void_pano` (a 360° panorama of deep space) and `loops/void_hole` (the black hole as a seamless animated
loop: the flat S1E7 ring, a volumetric smoke shell turning with it, and the smoke stream pouring off to one side),
plus `textures/vfx/void_wisps.png` (volumetric wisps that drift around you in 3D).
Builder: `blender/inserts/build_void_interior.py`; the seal/white/ink/tunnel textures: `build_domain_assets.py`.

| Ticks | S1E7 | Beat | Camera (`domain_full`) | What you see |
|---|---|---|---|---|
| 0–14 | 0–0.75 s | Six Eyes | close on his face, pushing out | eyes blazing |
| 14–44 | 1.0–3.0 s | the seal, the collar pulled down | head and chest, front-right | title 無量空処 |
| 44–58 | 3.25–4.0 s | the white | same framing, drifting back | the world beyond ~9 blocks goes flat white-pink; the people near stay, tinted violet; a thin 1.78 kHz ring over silence |
| 58–66 | 4.25 s | the ink | pulled back | black ink erupts behind him with neon speed lines; the sure-hit lands; buffs on the caster |
| 66–104 | 4.5–10 s | the tunnel of information | gliding past him down the tunnel | violet/magenta/red/white streaks rushing past, torn white fragments; everyone caught floats in it |
| 104 | 10.5 s | flash | | full white |
| 106–… | 10.75 s– | the void | low behind him, the black hole ahead | terrain, sky, clouds and block entities gone; the panorama, the black hole, wisps and motes; nothing outside the barrier can be seen |
| close → +14 | 12.5–13.5 s | the collapse | | the white ink wipe, a white fade back to the world |

The 0.2-second domain (tap): seal 0–8, white 8–11, ink 11–15, the void 15–19 (the black hole for 0.2 s), the white
ink wipe 19–31. Its victims stay paralysed for `instantParalysisSeconds`.

**Cancel:** press the domain key again while it is up — it collapses (the wipe starts at once, the world returns
~0.7 s later). **Cooldown** (default none) counts from the moment it ends.

**Sure-hit:** the Unlimited Void status effect — no walking, jumping, attacking, block breaking/placing or item use;
mobs lose their goals and targets. Refreshed every half second on anything inside the barrier (walking in counts).
Whatever touches the caster when it expands is left out (canon: Itadori). Bosses recover sooner.

**Seen from outside:** a black dome with a faint violet rim (toggle: `domainBarrier`).
