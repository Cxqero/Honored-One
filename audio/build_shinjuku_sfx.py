"""The Shinjuku pass: every sound in the mod redesigned to be intimidating, after Gojo at the Shinjuku Showdown.
Replaces the earlier build_*_sfx.py output (those scripts stay for reference). Same file names and lengths, so every
cue stays in sync with its animation. Each sound is mastered to its place in the mix (audio/master.py's table) before
it is written.

    .tools/venv/Scripts/python audio/build_shinjuku_sfx.py [name ...]

The palette:
  Blue    cold and inward: sounds sucked backwards into the moment, a gravitational drone, glassy shimmer
  Red     hot and outward: metal-hard cracks, saturated booms, fire crackle, air thrown away
  Purple  void and dread: a dark choir, a temple bell, sub-bass that erases, a breath of silence before it hits
  Nuke    all of it at once, larger: the loudest things in the mod
  Domain  vast, in stereo around you: bell, choir, glass, a flood of information
"""
import os, sys
import numpy as np
import soundfile as sf
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sfxlib import SR, lowpass, highpass, bandpass
from heavy import *
import master as M

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources", "assets",
                   "gojolimitless", "sounds")
SOUNDS = {}


def sound(dur, loop=False):
    def deco(fn):
        SOUNDS[fn.__name__] = (fn, dur, loop)
        return fn
    return deco


def N(dur):
    return int(dur * SR)


# ============================================================== Blue
@sound(2.6)
def blue_form():
    d = 2.6; n = N(d)
    hit = crack(1.0, base=700, seed=11, ring=1.2) + 0.6 * bell(98, 1.4, seed=12)[: N(1.0)]
    suck = reverse_swell(hit, 1.4, seed=13)
    suck = suck[-N(0.95):]                                 # the swell ends exactly on the impact at 0.95 s
    x = place(n,
              (suck, 0.0, 0.9),
              (kick(0.9, 40, 0.3, seed=14), 0.95, 1.0),
              (sub_drop(1.6, 72, 27, 0.55), 0.95, 0.9),
              (shimmer(1.6, seed=15) * env(N(1.6), 0.01, 0.5), 0.95, 0.35),
              (drone(1.7, 36, 380, seed=16) * env(N(1.7), 0.3, 0.9), 0.9, 0.45))
    return finish(hall(x, 2.2, 0.3, seed=17), d, 0.5)


def vortex(d, root, cutoff, heavy, seed):
    """Blue's gravitational vortex: a beating drone and noise whirling around a filter centre."""
    n = N(d); t = np.arange(n) / SR
    whirl = np.zeros(n)
    for i, (lo, hi) in enumerate([(220, 520), (420, 900), (800, 1600)]):
        band = bandpass(white(n, seed + i), lo, hi, 2)
        whirl += band * (0.55 + 0.45 * np.sin(TAU * (1.5 + i * 0.5) * t + i * 2.1))
    base = drone(d, root, cutoff, seed=seed + 5, beat=0.6)
    x = base * 1.0 + whirl * (0.35 if not heavy else 0.5) + shimmer(d, seed=seed + 6) * 0.12
    if heavy:
        x = sat(x + 0.6 * rumble(d, 120, wobble=1.0, seed=seed + 7) + 0.25 * crackle(d, 10, seed + 8, 1500, 6000), 2.4)
    return x


@sound(4.0, loop=True)
def blue_loop():
    return loop_seam(vortex(4.4, 41, 420, False, 21), 0.4)


@sound(4.0, loop=True)
def blue_max_loop():
    return loop_seam(vortex(4.4, 33, 520, True, 31), 0.4)


@sound(3.0)
def blue_charge():
    d = 3.0; n = N(d)
    inward = reverse_swell(boom(1.2, 0.7, seed=41), 2.0, seed=42)[-N(2.6):]
    x = place(n,
              (riser(2.8, 38, 140, seed=43, noise=0.2), 0.1, 0.7),
              (inward, 0.0, 0.8),
              (rumble(3.0, 110, wobble=4.0, seed=44) * env_curve(n, [(0, 0), (2.2, 1), (3.0, 1)]), 0.0, 0.7),
              (shimmer(3.0, seed=45) * env_curve(n, [(0, 0), (2.5, 0.6), (3.0, 0.6)]), 0.0, 0.3))
    return finish(sat(x, 1.8), d, 0.25)


@sound(2.4)
def blue_throw():
    d = 2.4; n = N(d)
    x = place(n,
              (kick(0.7, 46, 0.25, seed=51), 0.0, 0.9),
              (crack(0.6, base=900, seed=52, ring=0.5), 0.0, 0.5),
              (whoosh(2.2, 2400, 160, peak=0.18, seed=53), 0.02, 1.0),
              (sub_drop(1.4, 66, 30, 0.45), 0.0, 0.7))
    return finish(hall(x, 1.8, 0.25, seed=54), d, 0.4)


@sound(4.2)
def blue_collapse():
    d = 4.2; n = N(d)
    impact = 0.75
    suck = reverse_swell(boom(1.0, 0.8, seed=61), 1.6, seed=62)[-N(impact - 0.06):]   # in, then 60 ms of nothing
    x = place(n,
              (suck, 0.0, 0.9),
              (crack(1.2, base=480, seed=63, ring=1.0), impact, 0.8),
              (boom(3.4, 1.15, seed=64), impact, 1.0),
              (shatter(1.2, 120, seed=65), impact + 0.02, 0.35),
              (rumble(3.2, 130, tau=1.4, seed=66), impact + 0.1, 0.6))
    return finish(hall(x, 3.0, 0.3, seed=67), d, 0.8)


@sound(1.5)
def blue_tear():
    d = 1.5; n = N(d)
    rg = R(71)
    gate = (rg.uniform(0, 1, n // 300 + 1) > 0.35).astype(float).repeat(300)[:n]
    rip = sat(bandpass(white(n, 72), 120, 1800) * gate * env(n, 0.01, 0.4), 3.5)
    rocks = sum(modal([rg.uniform(90, 260)], [1.0], [rg.uniform(0.05, 0.15)], 0.4, seed=73 + i) for i in range(4))
    x = place(n, (rip, 0.0, 0.9), (rocks, 0.05, 0.5), (sub_drop(0.9, 60, 32, 0.3), 0.0, 0.6))
    return finish(x, d, 0.4)


@sound(3.6)
def blue_incant():
    d = 3.6; n = N(d)
    word = 0.8
    b = bell(73.4, 2.8, seed=81)
    suck = reverse_swell(b[: N(1.0)] + 0.5 * crack(1.0, 640, seed=82), 1.6, seed=83)[-N(word):]
    x = place(n,
              (suck, 0.0, 0.7),
              (b, word, 0.9),
              (sub_drop(2.0, 64, 26, 0.8), word, 0.9),
              (choir(2.6, 36.7, (0, 7, 12), "u", attack=0.2, seed=84) * env(N(2.6), 0.15, 1.2), word, 0.55))
    return finish(hall(x, 3.2, 0.35, seed=85), d, 0.8)


# ============================================================== Red
@sound(1.5)
def red_form():
    d = 1.5; n = N(d)
    x = place(n,
              (crack(0.8, base=1100, seed=101, ring=0.7), 0.0, 0.9),
              (kick(0.5, 58, 0.15, seed=102), 0.0, 0.6),
              (crackle(1.2, 60, seed=103) * env(N(1.2), 0.01, 0.5), 0.02, 0.6),
              (whoosh(1.0, 600, 4000, peak=0.25, seed=104), 0.0, 0.5))
    return finish(sat(hall(x, 1.2, 0.2, seed=105), 1.6), d, 0.3)


@sound(4.0, loop=True)
def red_charge():
    d = 4.4; n = N(d); t = np.arange(n) / SR
    pulse = 0.6 + 0.4 * np.sin(TAU * 2.0 * t) ** 2                       # throbbing like a pulse
    heat = sat(drone(d, 55, 900, seed=111, beat=1.2), 3.0) * pulse
    fire = crackle(d, 90, seed=112, lo=1200, hi=7000)
    whine = osc(1760 + 30 * np.sin(TAU * 0.5 * t), n) * 0.06
    x = heat * 0.9 + fire * 0.55 + whine + 0.4 * rumble(d, 160, wobble=2.0, seed=113)
    return loop_seam(x, 0.4)


@sound(2.2)
def red_fire():
    d = 2.2; n = N(d)
    x = place(n,
              (crack(1.0, base=820, seed=121, ring=1.0), 0.0, 1.0),
              (kick(0.8, 50, 0.22, seed=122), 0.0, 1.0),
              (boom(1.6, 0.55, seed=123), 0.0, 0.8),
              (whoosh(1.6, 5000, 300, peak=0.08, seed=124), 0.0, 0.9),
              (crackle(1.4, 50, seed=125) * env(N(1.4), 0.01, 0.4), 0.03, 0.4))
    return finish(sat(hall(x, 1.6, 0.22, seed=126), 2.0), d, 0.4)


@sound(0.9)
def red_fly():
    d = 0.9; n = N(d); t = np.arange(n) / SR
    roar = sat(whoosh(d, 1800, 500, peak=0.45, seed=131, width=1.4), 2.5)
    x = roar + 0.4 * crackle(d, 60, seed=132) + 0.3 * osc(220 - 80 * t / d, n, "saw") * env_curve(n, [(0, 0), (0.4, 1), (0.9, 0)])
    return finish(x, d, 0.15, 0.03)


@sound(4.0)
def red_detonate():
    d = 4.0; n = N(d)
    x = place(n,
              (crack(1.2, base=600, seed=141, ring=0.8), 0.0, 0.9),
              (boom(3.6, 0.95, seed=142), 0.0, 1.0),
              (whoosh(1.2, 3500, 200, peak=0.05, seed=143), 0.0, 0.6),
              (shatter(1.0, 80, seed=144), 0.02, 0.25))
    return finish(hall(x, 2.6, 0.28, seed=145), d, 0.8)


@sound(6.0)
def red_detonate_big():
    d = 6.0; n = N(d)
    x = place(n,
              (crack(1.4, base=420, seed=151, ring=1.2), 0.0, 1.0),
              (kick(1.0, 38, 0.4, seed=152), 0.0, 1.0),
              (boom(5.4, 1.6, seed=153), 0.0, 1.1),
              (crack(0.9, base=760, seed=154, ring=0.6), 0.12, 0.5),                 # the second report
              (whoosh(2.0, 3000, 120, peak=0.04, seed=155, width=1.5), 0.0, 0.7),
              (rumble(5.0, 110, tau=2.2, seed=156), 0.3, 0.7))
    return finish(hall(x, 4.0, 0.3, seed=157), d, 1.2)


@sound(2.8)
def red_incant():
    d = 2.8; n = N(d)
    x = place(n,
              (crack(1.6, base=330, seed=161, ring=1.6), 0.0, 0.9),
              (kick(0.8, 44, 0.3, seed=162), 0.0, 0.9),
              (choir(1.8, 55, (0, 7, 12, 19), "a", attack=0.02, seed=163) * env(N(1.8), 0.01, 0.45), 0.0, 0.6),
              (crackle(1.6, 40, seed=164) * env(N(1.6), 0.02, 0.6), 0.03, 0.35),
              (sub_drop(1.5, 70, 30, 0.5), 0.0, 0.7))
    return finish(hall(x, 2.6, 0.32, seed=165), d, 0.6)


# ============================================================== Purple
@sound(2.6)
def purple_form():
    d = 2.6; n = N(d); meet = 1.0
    cold = whoosh(meet, 500, 2400, peak=0.95, seed=201) + 0.4 * shimmer(meet, seed=202) * np.linspace(0, 1, N(meet))
    hot = crackle(meet, 70, seed=203) * np.linspace(0, 1, N(meet)) ** 2 + 0.6 * whoosh(meet, 300, 1800, peak=0.95, seed=204)
    x = place(n,
              (cold, 0.0, 0.7),
              (hot, 0.0, 0.7),
              (crack(1.0, base=380, seed=205, ring=1.2), meet, 0.9),
              (sub_drop(1.6, 62, 24, 0.7), meet, 1.0),
              (choir(1.6, 46.25, (0, 3, 7, 12), "o", attack=0.05, seed=206) * env(N(1.6), 0.02, 0.8), meet, 0.6))
    return finish(hall(x, 2.6, 0.3, seed=207), d, 0.5)


@sound(4.0, loop=True)
def purple_charge():
    d = 4.4; n = N(d); t = np.arange(n) / SR
    ch = choir(d, 49, (0, 3, 7, 10, 15), "o", attack=0.01, seed=211)
    x = ch * (0.8 + 0.2 * np.sin(TAU * 0.5 * t)) + 0.5 * drone(d, 24.5, 260, seed=212) + 0.3 * crackle(d, 30, seed=213) \
        + 0.35 * rumble(d, 120, wobble=1.5, seed=214)
    return loop_seam(sat(x, 1.6), 0.4)


@sound(4.0, loop=True)
def purple_hum():
    d = 4.4; n = N(d); t = np.arange(n) / SR
    void = sat(drone(d, 31, 340, seed=221, beat=0.8), 2.8)
    phase = bandpass(white(n, 222), 200, 900, 2) * (0.5 + 0.5 * np.sin(TAU * 0.75 * t))
    x = void + 0.35 * phase + 0.25 * choir(d, 62, (0, 7), "u", attack=0.01, seed=223) + 0.2 * crackle(d, 12, seed=224)
    return loop_seam(x, 0.4)


@sound(5.0)
def purple_collide():
    d = 5.0; n = N(d)
    x = place(n,
              (crack(1.6, base=300, seed=231, ring=1.6), 0.0, 1.0),
              (kick(1.0, 36, 0.45, seed=232), 0.0, 1.0),
              (boom(4.6, 1.3, seed=233, dark=1.6), 0.0, 0.9),
              (choir(3.5, 41.2, (0, 3, 7, 12, 15), "o", attack=0.03, seed=234) * env(N(3.5), 0.02, 1.4), 0.0, 0.8),
              (shatter(1.4, 160, seed=235), 0.02, 0.35),
              (bell(55, 4.0, seed=236), 0.0, 0.4))
    return finish(hall(x, 3.8, 0.33, seed=237), d, 1.0)


@sound(5.5)
def purple_launch():
    d = 5.5; n = N(d)
    x = place(n,
              (sub_drop(3.0, 70, 22, 1.4, drive=2.4), 0.0, 1.0),
              (crack(1.2, base=260, seed=241, ring=1.2), 0.0, 0.8),
              (sat(whoosh(4.5, 900, 90, peak=0.06, seed=242, width=1.6), 3.0), 0.0, 1.0),
              (sat(rumble(5.0, 200, tau=1.8, seed=243), 2.0), 0.05, 0.8),
              (crackle(3.0, 45, seed=244) * env(N(3.0), 0.01, 1.0), 0.0, 0.4),
              (choir(3.0, 36.7, (0, 3, 7), "u", attack=0.02, seed=245) * env(N(3.0), 0.02, 1.2), 0.0, 0.5))
    return finish(hall(x, 4.0, 0.3, seed=246), d, 1.2)


@sound(1.3)
def purple_rush():
    d = 1.3; n = N(d)
    x = sat(whoosh(d, 700, 150, peak=0.4, seed=251, width=1.8), 3.0) + 0.4 * rumble(d, 160, seed=252) * env(n, 0.05, 0.6) \
        + 0.3 * crackle(d, 40, seed=253)
    return finish(x, d, 0.2, 0.02)


@sound(3.2)
def purple_impact():
    d = 3.2; n = N(d)
    x = place(n,
              (crack(1.0, base=340, seed=261, ring=1.0), 0.0, 0.9),
              (kick(0.9, 40, 0.35, seed=262), 0.0, 1.0),
              (boom(3.0, 1.2, seed=263, dark=1.5), 0.0, 1.0),
              (shatter(1.0, 100, seed=264), 0.02, 0.3))
    return finish(hall(x, 2.8, 0.3, seed=265), d, 0.8)


@sound(4.4)
def purple_incant():
    d = 4.4; n = N(d)
    x = place(n,
              (bell(49, 4.3, seed=271, decay=1.2), 0.0, 1.0),
              (sub_drop(2.4, 58, 22, 1.0), 0.0, 0.9),
              (choir(3.5, 49, (0, 3, 7, 12), "o", attack=0.04, seed=272) * env(N(3.5), 0.03, 1.4), 0.0, 0.7),
              (crack(1.0, base=250, seed=273, ring=0.6), 0.0, 0.4))
    return finish(hall(x, 3.6, 0.38, seed=274), d, 0.9)


# ============================================================== the nuke
@sound(3.8)
def nuke_throw():
    d = 3.8; n = N(d)
    x = place(n,
              (crack(1.0, base=900, seed=301, ring=0.8), 0.0, 1.0),
              (kick(0.8, 48, 0.25, seed=302), 0.0, 0.9),
              (riser(2.4, 300, 2600, seed=303, noise=0.8) * env_curve(N(2.4), [(0, 1), (1.8, 1), (2.4, 0)]), 0.05, 0.6),
              (whoosh(3.2, 600, 6000, peak=0.2, seed=304), 0.0, 0.8),
              (crackle(2.0, 60, seed=305) * env(N(2.0), 0.01, 0.6), 0.0, 0.4))
    return finish(hall(x, 2.4, 0.25, seed=306), d, 0.8)


@sound(6.8)
def nuke_boost():
    d = 6.8; n = N(d); hit = 2.6
    swell = reverse_swell(boom(1.4, 1.0, seed=311) + bell(61.7, 1.4, seed=312), 2.8, seed=313)[-N(hit):]
    t = np.arange(N(4.2)) / SR
    whirl = sum(bandpass(white(N(4.2), 314 + i), 200 * (i + 1), 500 * (i + 1), 2)
                * (0.5 + 0.5 * np.sin(TAU * (2.0 + i) * t + i)) for i in range(3))
    x = place(n,
              (swell, 0.0, 0.9),
              (kick(1.0, 34, 0.5, seed=317), hit, 1.0),
              (sub_drop(3.0, 60, 22, 1.4), hit, 1.0),
              (choir(4.2, 30.9, (0, 7, 12, 15, 19), "o", attack=0.4, seed=318) * env(N(4.2), 0.4, 2.0), hit, 0.7),
              (whirl * env(N(4.2), 0.3, 1.8), hit, 0.5))
    return finish(hall(x, 3.5, 0.3, seed=319), d, 1.2)


@sound(3.4, loop=True)
def nuke_sky_hum():
    d = 3.8; n = N(d); t = np.arange(n) / SR
    x = drone(d, 36.7, 300, seed=321, beat=0.5) + 0.3 * lowpass(white(n, 322), 900, 2) * (0.6 + 0.4 * np.sin(TAU * 0.6 * t)) \
        + 0.12 * shimmer(d, seed=323)
    return loop_seam(x, 0.4)


@sound(3.05)
def nuke_converge():
    d = 3.05; n = N(d)
    cold = riser(d, 220, 1800, seed=331, noise=0.3) + 0.4 * shimmer(d, seed=332) * np.linspace(0, 1, n) ** 2
    hot = sat(riser(d, 110, 1300, seed=333, noise=0.6), 2.5) + 0.5 * crackle(d, 80, seed=334) * np.linspace(0, 1, n) ** 2
    roar = sat(rumble(d, 300, seed=335), 2.0) * np.linspace(0, 1, n) ** 2.5
    x = cold * 0.7 + hot * 0.7 + roar * 0.8
    x[-N(0.02):] *= np.linspace(1, 0, N(0.02))            # stops dead
    return finish(x, d, 0.0, 0.3)


@sound(1.35)
def nuke_collide():
    d = 1.35; n = N(d)
    hit = crack(1.3, base=1400, seed=341, ring=1.8) + 0.5 * shatter(1.3, 140, seed=342)
    hit *= env_curve(len(hit), [(0, 1), (0.12, 1), (0.5, 0.25), (1.3, 0)])   # sucked back into silence
    x = place(n, (hit, 0.0, 1.0), (kick(0.5, 60, 0.12, seed=343), 0.0, 0.6))
    return finish(x, d, 0.4)


@sound(9.5)
def nuke_bloom():
    d = 9.5; n = N(d); t = np.arange(n) / SR
    tinnitus = osc(5200, n) * env_curve(n, [(0, 0), (1.2, 0), (2.0, 1), (6.0, 0.6), (9.5, 0)]) * 0.05
    x = place(n,
              (sub_drop(4.0, 55, 18, 2.2, drive=2.6), 0.0, 1.0),
              (kick(1.2, 32, 0.6, seed=351), 0.0, 1.0),
              (sat(boom(8.5, 2.4, seed=352, dark=1.4), 1.6), 0.0, 1.0),
              (crackle(5.0, 35, seed=353, lo=1500, hi=8000) * env(N(5.0), 0.2, 2.0), 0.3, 0.45),
              (choir(6.0, 27.5, (0, 3, 7, 12, 15), "u", attack=0.5, seed=354) * env(N(6.0), 0.5, 2.5), 0.2, 0.6),
              (tinnitus, 0.0, 1.0))
    return finish(hall(x, 5.0, 0.3, seed=355), d, 2.0)


@sound(2.2)
def nuke_glint():
    d = 2.2; n = N(d)
    ping = modal([2637, 3951, 5274, 7902], [1, 0.6, 0.4, 0.25], [0.9, 0.6, 0.4, 0.25], 1.6, seed=361)
    pre = reverse_swell(ping[: N(0.6)], 1.0, seed=362)[-N(0.35):]
    x = place(n, (pre, 0.0, 0.5), (ping, 0.35, 0.8), (shimmer(1.5, seed=363) * env(N(1.5), 0.01, 0.6), 0.35, 0.5),
              (sub_drop(1.2, 50, 28, 0.6), 0.35, 0.4))
    return finish(hall(x, 2.5, 0.35, seed=364), d, 0.6)


@sound(8.0)
def nuke_explosion():
    d = 8.0; n = N(d)
    x = place(n,
              (crack(1.6, base=260, seed=371, ring=1.4), 0.0, 1.0),
              (kick(1.4, 30, 0.7, seed=372), 0.0, 1.0),
              (sub_drop(5.0, 65, 18, 2.6, drive=2.8), 0.0, 1.1),
              (sat(boom(7.5, 2.8, seed=373), 1.8), 0.0, 1.1),
              (crack(1.0, base=480, seed=374, ring=0.8), 0.18, 0.6),
              (shatter(2.0, 260, seed=375), 0.05, 0.3),
              (rumble(7.0, 90, tau=3.0, seed=376), 0.4, 0.9))
    return finish(hall(x, 5.0, 0.3, seed=377), d, 2.0)


@sound(4.5)
def nuke_shockwave():
    d = 4.5; n = N(d)
    x = place(n,
              (sat(whoosh(3.0, 200, 2500, peak=0.12, seed=381, width=1.8), 2.5), 0.0, 1.0),
              (crack(1.0, base=380, seed=382, ring=0.6), 0.25, 0.8),
              (kick(0.8, 36, 0.4, seed=383), 0.25, 0.9),
              (sat(rumble(4.0, 180, tau=1.6, seed=384), 2.2), 0.25, 0.9),
              (shatter(1.5, 120, seed=385), 0.3, 0.25))
    return finish(hall(x, 3.0, 0.25, seed=386), d, 1.0)


@sound(7.0)
def nuke_aftermath():
    d = 7.0; n = N(d); t = np.arange(n) / SR
    wind = whoosh(d, 250, 700, peak=0.5, seed=391, width=2.0) + 0.6 * lowpass(white(n, 392), 600, 2) * (0.6 + 0.4 * np.sin(TAU * 0.2 * t))
    x = place(n, (wind, 0.0, 0.7), (rumble(d, 80, wobble=0.4, seed=393), 0.0, 0.8), (drone(d, 27.5, 200, seed=394), 0.0, 0.4),
              (crackle(d, 6, seed=395, lo=800, hi=3000), 0.0, 0.2))
    return finish(x * env_curve(n, [(0, 0), (0.8, 1), (5.0, 0.8), (7.0, 0)]), d, 1.0, 0.3)


# ============================================================== Infinity
@sound(1.8)
def infinity_toggle():
    d = 1.8; n = N(d)
    x = place(n, (sub_drop(1.2, 70, 34, 0.35), 0.0, 0.8), (shimmer(1.6, seed=401) * env(N(1.6), 0.05, 0.5), 0.0, 0.6),
              (reverse_swell(crack(0.6, 1200, seed=402, ring=1.0), 0.8, seed=403)[-N(0.3):], 0.0, 0.4),
              (crack(0.6, 1200, seed=402, ring=1.0), 0.3, 0.3))
    return finish(hall(x, 1.8, 0.3, seed=404), d, 0.5)


@sound(1.3)
def infinity_stop():
    d = 1.3; n = N(d)
    thud = lowpass(kick(0.4, 70, 0.08, seed=411), 600, 2)                  # it never quite lands
    ring = modal([1567, 2349, 3135], [1, 0.6, 0.4], [0.5, 0.35, 0.2], 1.2, seed=412)
    x = place(n, (thud, 0.0, 0.6), (ring, 0.01, 0.4), (shimmer(1.0, seed=413) * env(N(1.0), 0.01, 0.3), 0.0, 0.3))
    return finish(hall(x, 1.4, 0.3, seed=414), d, 0.4)


# ============================================================== Domain (stereo, around you)
@sound(2.35)
def domain_seal():
    d = 2.35; n = N(d)
    cloth = whoosh(0.4, 1500, 600, peak=0.4, seed=501)
    tick = highpass(white(N(0.02), 502), 2500, 2) * env(N(0.02), 0.0003, 0.003)
    eyes = shimmer(1.8, (2093, 2637, 3136, 4186), seed=503) * env_curve(N(1.8), [(0, 0), (1.6, 1), (1.8, 1)])
    press = rumble(2.0, 90, seed=504) * env_curve(N(2.0), [(0, 0), (2.0, 1)])
    dread = choir(1.8, 36.7, (0, 3, 7), "u", attack=1.2, seed=505)
    x = place(n, (pan(cloth, -0.3), 0.0, 0.5), (pan(tick, 0.0), 0.5, 1.0), (stereo_hall(eyes, 2.0, 0.4, seed=506)[: N(1.8)], 0.5, 0.45),
              (pan(press, 0.0), 0.35, 0.7), (stereo_hall(dread, 2.0, 0.3, seed=507)[: N(1.8)], 0.55, 0.4),
              (pan(sub_drop(1.0, 60, 30, 0.4), 0), 0.5, 0.6))
    return finish(x, d, 0.05)


@sound(1.05)
def domain_white():
    d = 1.05; n = N(d)
    ring = osc(3136, n) * env_curve(n, [(0, 0), (0.05, 1), (0.8, 0.6), (1.05, 0)]) * 0.25
    breath = choir(1.0, 110, (0, 7, 12), "a", attack=0.2, seed=511)
    press = sub_drop(1.05, 40, 30, 1.0, curve=1.0) * env_curve(n, [(0, 0), (0.5, 1), (1.05, 0.6)])
    inhale = whoosh(0.35, 400, 3000, peak=0.95, seed=512)
    x = place(n, (pan(ring, 0), 0.0, 1.0), (stereo_hall(breath, 1.5, 0.5, seed=513), 0.0, 0.35), (pan(press, 0), 0.0, 0.6),
              (pan(inhale, 0), 0.7, 0.6))
    return finish(x, d, 0.02)


@sound(2.4)
def domain_ink():
    d = 2.4; n = N(d); rg = R(521)
    splash = sat(lowpass(white(N(1.2), 522), 700, 3) * env(N(1.2), 0.004, 0.3), 2.0) + 0.5 * kick(1.2, 42, 0.3, seed=523)
    lines = np.zeros((n, 2))
    for i in range(26):                                    # neon lines zipping into being, faster and faster
        s = 0.7 + 1.55 * (i / 26) ** 0.6
        L = N(0.12)
        f0 = rg.uniform(800, 2500)
        z = osc(np.linspace(f0, f0 * rg.uniform(1.5, 3.0), L), L, "saw") * np.linspace(1, 0, L) ** 2 * 0.25
        z = bandpass(z, 600, 6000, 2)
        o = N(s)
        if o + L < n:
            lines[o:o + L] += pan(z, rg.uniform(-0.9, 0.9))
    chord = choir(1.7, 55, (0, 3, 7, 12, 15), "o", attack=1.2, seed=530)
    x = place(n, (stereo_hall(splash, 2.0, 0.35, seed=531), 0.0, 1.0), (lines, 0.0, 0.8),
              (stereo_hall(chord, 2.0, 0.4, seed=532), 0.7, 0.55),
              (pan(crackle(1.7, 50, seed=533), 0.2), 0.7, 0.3))
    return finish(x, d, 0.1)


@sound(2.8)
def domain_tunnel():
    d = 2.8; n = N(d); t = np.arange(n) / SR
    # endless Shepard rise that keeps accelerating
    rate = 0.6 + 2.6 * (t / d) ** 2
    phase = np.cumsum(rate) / SR
    shep = np.zeros(n)
    for k in range(8):
        pos = (phase + k / 8) % 1.0
        f = 40 * 2 ** (pos * 8)
        amp = np.sin(np.pi * pos) ** 2
        shep += osc(f, n) * amp
    shep /= 4
    air = whoosh(d, 300, 5000, peak=0.98, seed=541, width=1.5)
    data = crackle(d, 160, seed=542, lo=2500, hi=9000) * (t / d) ** 1.5
    beat = np.zeros(n)
    bt = 0.0; gap = 0.6
    while bt < d - 0.1:                                     # a heartbeat, quickening
        for o, g in ((0.0, 1.0), (0.16, 0.6)):
            k = kick(0.3, 50, 0.08, seed=543)
            s = N(bt + o)
            if s + len(k) < n:
                beat[s:s + len(k)] += k * g
        bt += gap; gap = max(0.18, gap * 0.82)
    x = place(n, (stereo_hall(shep, 1.5, 0.3, seed=544), 0.0, 0.7), (width(np.stack([air, air[::-1]], 1), 1.4), 0.0, 0.6),
              (pan(data, -0.3), 0.0, 0.35), (pan(crackle(d, 140, seed=545, lo=2500, hi=9000) * (t / d) ** 1.5, 0.3), 0.0, 0.35),
              (pan(beat, 0), 0.0, 0.8))
    x[-N(0.015):] *= np.linspace(1, 0, N(0.015))[:, None]    # cut dead at the flash
    return finish(x, d, 0.0)


@sound(6.5)
def domain_open():
    d = 6.5; n = N(d)
    big = bell(41.2, 6.4, seed=551, decay=1.4) + 0.4 * np.pad(bell(82.4, 5.0, seed=552), (0, N(1.4)))
    crystal = modal([3520, 4699, 5920, 7040], [1, 0.7, 0.5, 0.3], [2.0, 1.6, 1.2, 0.8], 5.0, seed=553)
    rise = choir(5.5, 41.2, (0, 7, 12, 15, 19), "u", attack=3.5, seed=554)
    x = place(n,
              (stereo_hall(big, 6.0, 0.55, seed=555), 0.0, 1.0),
              (pan(sub_drop(4.0, 50, 18, 2.0, drive=2.4), 0), 0.0, 0.9),
              (pan(kick(1.2, 34, 0.5, seed=556), 0), 0.0, 0.8),
              (stereo_hall(crystal, 5.0, 0.6, seed=557), 0.05, 0.35),
              (stereo_hall(rise, 4.0, 0.5, seed=558), 0.5, 0.5))
    return finish(x, d, 1.5)


@sound(8.0, loop=True)
def domain_void():
    d = 8.6; n = N(d); t = np.arange(n) / SR; rg = R(561)
    breathe = choir(d, 36.7, (0, 7, 12, 15), "u", attack=0.01, seed=562) * (0.65 + 0.35 * np.sin(TAU * t / 4.3))
    wind_l = whoosh(d, 200, 700, peak=0.5, seed=563, width=2.0)
    wind_r = whoosh(d, 700, 200, peak=0.5, seed=564, width=2.0)
    stars = np.zeros((n, 2))
    for i in range(16):
        ping = modal([rg.uniform(2000, 6000)], [1], [rg.uniform(0.3, 1.0)], 1.2, seed=565 + i) * 0.15
        s = N(rg.uniform(0.2, d - 1.4))
        stars[s:s + len(ping)] += pan(ping, rg.uniform(-1, 1))
    song = choir(3.0, 220, (0, 7), "a", attack=1.2, seed=590) * env_curve(N(3.0), [(0, 0), (1.5, 1), (3.0, 0)])
    x = place(n, (stereo_hall(breathe, 4.0, 0.4, seed=591), 0.0, 0.8), (np.stack([wind_l, wind_r], 1), 0.0, 0.35),
              (stars, 0.0, 1.0), (stereo_hall(song, 4.0, 0.6, seed=592), 3.5, 0.18),
              (pan(drone(d, 27.5, 160, seed=593), 0), 0.0, 0.5))
    return loop_seam(x, 0.6)


@sound(2.4)
def domain_collapse():
    d = 2.4; n = N(d); hit = 0.55
    inhale = reverse_swell(choir(1.0, 36.7, (0, 7, 12), "u", attack=0.01, seed=601), 1.2, seed=602)[-N(hit):]
    glass = shatter(1.8, 320, seed=603)
    x = place(n,
              (pan(inhale, 0), 0.0, 0.8),
              (pan(crack(1.2, base=520, seed=604, ring=1.4), 0), hit, 1.0),
              (width(np.stack([glass, shatter(1.8, 320, seed=605)], 1), 1.3), hit, 0.8),
              (pan(kick(0.8, 40, 0.3, seed=606), 0), hit, 0.9),
              (stereo_hall(lowpass(white(N(0.8), 607), 700, 3) * env(N(0.8), 0.004, 0.2), 1.5, 0.3, seed=608), hit + 0.1, 0.6),
              (width(np.stack([whoosh(1.2, 2500, 200, peak=0.2, seed=609), whoosh(1.2, 2600, 190, peak=0.2, seed=610)], 1), 1.2), hit + 0.4, 0.6))
    return finish(x, d, 0.5)


@sound(1.9)
def domain_instant():
    d = 1.9; n = N(d)
    snap = highpass(white(N(0.03), 611), 2000, 2) * env(N(0.03), 0.0003, 0.004)
    ring = osc(3136, N(0.4)) * env(N(0.4), 0.005, 0.15) * 0.3
    ink = sat(lowpass(white(N(0.5), 612), 800, 3) * env(N(0.5), 0.003, 0.12), 2.0)
    toll = bell(41.2, 1.3, seed=613, decay=0.5)
    wipe = whoosh(0.6, 3000, 300, peak=0.2, seed=614)
    x = place(n, (pan(snap, 0), 0.0, 1.0), (pan(ring, 0), 0.08, 0.8), (stereo_hall(ink, 1.0, 0.3, seed=615), 0.25, 0.8),
              (stereo_hall(toll, 1.5, 0.5, seed=616), 0.4, 1.0), (pan(sub_drop(1.2, 55, 25, 0.5), 0), 0.4, 0.8),
              (pan(kick(0.6, 40, 0.2, seed=617), 0), 0.4, 0.7),
              (width(np.stack([wipe, whoosh(0.6, 3100, 290, peak=0.2, seed=618)], 1), 1.3), 0.95, 0.7))
    return finish(x, d, 0.3)


# ============================================================== write
MAX_GR = 8.0
# the payoffs: driven harder so they stand above everything else
HEAVIEST = {"nuke_explosion", "nuke_bloom", "nuke_collide", "nuke_shockwave", "purple_impact", "purple_collide",
            "purple_launch", "red_detonate_big", "red_detonate", "blue_collapse"}


def glue(y, sub_drive=2.6, top_drive=1.5):
    """Split at 110 Hz and saturate each band on its own: the sub's peaks turn into harmonics (louder, and audible on
    laptop speakers that can't play the sub itself), the top's spikes round off. Peaks drop, weight stays."""
    out = np.empty_like(y)
    for c in range(y.shape[1]):
        low = lowpass(y[:, c], 110, 4)
        high = y[:, c] - low
        pl, ph = np.max(np.abs(low)) + 1e-9, np.max(np.abs(high)) + 1e-9
        low = sat(low / pl, sub_drive) * pl
        high = sat(high / ph, top_drive) * ph
        out[:, c] = low + high
    return out


def write(name):
    fn, dur, loop = SOUNDS[name]
    x = fn()
    if x.ndim == 1:
        x = x[: N(dur)] if len(x) >= N(dur) else np.pad(x, (0, N(dur) - len(x)))
    else:
        x = x[: N(dur)] if len(x) >= N(dur) else np.pad(x, ((0, N(dur) - len(x)), (0, 0)))
    if not np.all(np.isfinite(x)):
        raise ValueError(name + ": not finite")
    y = x if x.ndim == 2 else x[:, None]
    if not loop:
        y = glue(y, 4.0, 2.4) if name in HEAVIEST else glue(y)
    # place it in the mix: the loudest moment on its target, true peaks under -1 dBTP, never more than MAX_GR of
    # limiting (a little quieter beats crushed)
    target = M.MIX[name]
    gain_db = target - M.loudest_moment(y, SR)
    for _ in range(8):
        z, gr = M.limit(y * 10 ** (gain_db / 20), SR, loop)
        if gr > MAX_GR + 0.3:
            gain_db -= (gr - MAX_GR) * 0.8
            continue
        got = M.loudest_moment(z, SR)
        if abs(target - got) < 0.15 or (target > got and gr > MAX_GR - 0.5):
            break
        gain_db += target - got
    got = M.loudest_moment(z, SR)
    path = os.path.join(OUT, name + ".ogg")
    sf.write(path, (z if z.shape[1] == 2 else z[:, 0]).astype(np.float32), SR, format="OGG", subtype="VORBIS",
             compression_level=M.VORBIS_LEVEL)
    print(f"{name:18s} {dur:5.2f}s {'stereo' if y.shape[1] == 2 else 'mono  '} -> {got:6.1f} LUFS (limiter {gr:4.1f} dB)"
          f"{'  loop' if loop else ''}", flush=True)


if __name__ == "__main__":
    names = sys.argv[1:] or sorted(SOUNDS)
    for nm in names:
        write(nm)
