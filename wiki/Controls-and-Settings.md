# 🎮 Controls and Settings

## Keys
All rebindable under **Options → Controls → Gojo Satoru: Limitless**.

| Key | Tap | Hold |
|:-:|---|---|
| `R` | [Lapse: Blue](Lapse-Blue) | Maximum Output: Blue |
| `G` | [Reversal: Red](Reversal-Red) | Incantation Red |
| `V` | [Hollow Purple](Hollow-Purple) | 200% Hollow Purple |
| `B` | [Remote Hollow Purple](Remote-Hollow-Purple) | — |
| `Z` | [0.2-second Unlimited Void](Unlimited-Void) | Full Unlimited Void (press again to collapse) |
| `N` | [Infinity](Infinity) on/off | — |
| `Enter` | skip a cutscene | — |

A tap is anything shorter than 240 ms (adjustable: *General → hold threshold*).

> **Shared keys:** Iris also uses `R` (Reload Shaders). While you're in a world the Gojo key wins, and the mod tells you
> once in chat when one of its keys is shared. Rebind either one if you want both.

## No cooldowns, no cursed energy bar
Canon: the Six Eyes make Gojo's cursed energy use almost free, and reverse cursed technique repairs his burnt-out
technique. Every technique is ready whenever you are (the domain can be given a cooldown in the settings).

## Settings
Open them from **Mod Menu** (needs Mod Menu + YACL), or edit `config/gojolimitless.json` and run `/limitless reload`.

| Category | What's in it |
|---|---|
| **General** | terrain destruction on/off, respect the mobGriefing rule, blocks broken per tick, erase containers (off), spare your pets, hold threshold |
| **Lapse: Blue** | range, size, pull, damage, terrain for the tap and Maximum Output; what happens when you let go |
| **Reversal: Red** | range, speed, blast size, damage, knockback, drilling depth, chant time |
| **Hollow Purple** | size, range, speed, damage, terrain for the tap and 200%; chant time |
| **Remote Hollow Purple** | target distance, lift, erasure radius and speed, damage, self-damage, slow falling |
| **Domain Expansion** | radius, duration, collapse on recast, who gets caught, boss recovery, your buffs, rooting, cooldown |
| **Infinity** | on when you join, slow and stop distances, what it blocks, ripples |
| **Client** | VFX quality, effects over shader packs, debris, flash strength, camera shake, subtitles, cutscenes, poses, first-person arms, **animation physics**, **impact frames**, **speed lines**, glow, every domain visual |

### Comfort settings
- **Flash strength**: lower it if flashes bother you. Below 0.25, impact frames switch off entirely
- **Impact frames**: the inverted black/white flicker on big hits; switch off if strobing bothers you
- **Camera shake**: 0 turns off shake and the camera punch
- **Animation physics**: 0 plays the raw keyframes, 2 is extra loose
- Sounds follow Minecraft's **Players** and **Master** volume sliders

## Commands (operators)
| Command | |
|---|---|
| `/limitless cast <blue\|red\|purple\|nuke\|domain> [holdTicks]` | cast a technique (a hold time gives the charged version) |
| `/limitless infinity` | toggle Infinity |
| `/limitless reload` | reload `config/gojolimitless.json` without restarting |
