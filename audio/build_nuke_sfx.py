"""Remote Hollow Purple ("the nuke") sound effects — original designs.  Run: python3 audio/build_nuke_sfx.py

nuke_throw      Red hurled into the sky: crack, a rising shriek that races away
nuke_boost      Blue boosted by its incantation: a deep swell wound with a spiralling whirl
nuke_sky_hum    Blue hanging in the sky (loop)
nuke_converge   both infinities rushing together: two rising voices and an accelerating roar that stops dead
nuke_collide    the meeting: a glassy crack that sucks back into total silence (the implosion beat is silent)
nuke_bloom      the erasure: a sub-bass drop, a roar tearing open, lightning, then a ringing in the ears
nuke_glint      the star glint on white
nuke_aftermath  wind and a far rumble over the crater
"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sfxlib import *
from build_blue_sfx import band_path, centres_for, OUT
from build_red_sfx import crackle


def tone_path(freqs, amp=None):
    ph = 2 * np.pi * np.cumsum(freqs) / SR
    x = np.sin(ph)
    return x if amp is None else x * amp


def nuke_throw():
    dur = 1.6; n = int(dur * SR); t = t_axis(dur)
    crack = highpass(noise(int(0.012 * SR)), 1200) * np.hanning(int(0.012 * SR))
    thump = saturate(sweep_tone(120, 45, 0.25) * exp_env(int(0.25 * SR), 0.06), 2.0)
    # a shriek climbing and racing away: pitch up, then the air closing behind it
    f = 500 * (3800 / 500) ** np.clip(t / 0.55, 0, 1)
    shriek = band_path(noise(n, "pink"), centres_for(n, lambda k: 500 * (3800 / 500) ** np.clip(k / 0.55, 0, 1)), q=6.0)
    whine = tone_path(f * 0.5, np.clip(t / 0.05, 0, 1) * np.exp(-t / 0.5)) * 0.25
    env = np.clip(t / 0.03, 0, 1) * np.exp(-np.maximum(0, t - 0.25) / 0.35)
    fizz = crackle(n, 60, 2500, 9000) * np.exp(-t / 0.4)
    x = mix(n, (crack, 0, 0.9), (thump, 0, 0.9), (shriek * env, 0, 1.0), (whine, 0, 1.0), (fizz, 0.02, 0.5))
    x = lowpass(x, 9000)
    return fade(normalize(reverb(x, 2.2, 0.28), -1.5), 0.002, 0.4)


def nuke_boost():
    dur = 4.2; n = int(dur * SR); t = t_axis(dur)
    rise = np.clip(t / 2.2, 0, 1) ** 1.6
    sub = tone_path(48 + 34 * rise, 0.9 * np.clip(t / 0.6, 0, 1))
    sub += tone_path(96 + 68 * rise, 0.25 * rise)
    # the spiral: a whirl whose rotation speeds up as the words land
    rate = 1.5 + 7.0 * rise
    phase = np.cumsum(rate) / SR
    whirl = band_path(noise(n, "pink"), centres_for(n, lambda k: 700 + 520 * np.sin(2 * np.pi * np.interp(k, t, phase))), q=3.0)
    shimmer = np.zeros(n)
    for fr, a in [(1760, 0.5), (2349, 0.35), (2960, 0.25), (3951, 0.15)]:
        shimmer += np.sin(2 * np.pi * fr * t + rng.uniform(0, 6)) * a
    shimmer *= (0.5 + 0.5 * np.sin(2 * np.pi * 11 * t)) * rise * 0.12
    x = sub * 0.8 + whirl * (0.2 + 0.9 * rise) + shimmer
    x *= np.where(t < 3.4, 1.0, np.exp(-(t - 3.4) / 0.35))
    return fade(normalize(reverb(saturate(x, 1.3), 2.6, 0.3), -1.5), 0.05, 0.5)


def nuke_incant():
    dur = 1.6; n = int(dur * SR); t = t_axis(dur)
    strike = np.zeros(n)
    for fr, a, tau in [(73.4, 1.0, 0.9), (146.8, 0.45, 0.6), (211.0, 0.3, 0.35), (329.6, 0.18, 0.25), (587.3, 0.07, 0.2)]:
        strike += np.sin(2 * np.pi * fr * t) * a * np.exp(-t / tau)
    strike *= np.clip(t / 0.004, 0, 1)
    breath = bandpass(noise(n, "pink"), 500, 2600) * np.clip(t / 0.08, 0, 1) * np.exp(-t / 0.35) * 0.35
    x = saturate(strike, 1.4) + breath
    return fade(normalize(reverb(x, 2.4, 0.35), -3), 0.002, 0.4)


def nuke_sky_hum():
    dur = 4.0; t = t_axis(dur); n = len(t)
    x = tone_path(np.full(n, 55.0), 0.8) + tone_path(np.full(n, 55.6), 0.6) + tone_path(np.full(n, 110.3), 0.25)
    x *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * t)
    air = band_path(noise(n, "pink"), centres_for(n, lambda k: 420 + 160 * np.sin(2 * np.pi * 0.75 * k)), q=2.0) * 0.5
    x = lowpass(x + air, 3000)
    return normalize(make_loop(x, 0.6), -3)


def nuke_converge():
    dur = 3.05; n = int(dur * SR); t = t_axis(dur)
    k = np.clip(t / 3.0, 0, 1)
    acc = k ** 2.2
    blue = tone_path(60 + 150 * acc, 0.6 * (0.3 + 0.7 * k))
    red = crackle(n, 30 + 400 * acc.mean(), 1500, 8000) * (0.2 + 0.8 * acc)
    red_tone = tone_path(310 + 900 * acc, 0.18 * (0.2 + 0.8 * k))
    roar = band_path(noise(n, "pink"), centres_for(n, lambda kk: 250 + 3800 * np.clip(kk / 3.0, 0, 1) ** 2.2), q=1.6)
    roar *= 0.25 + 1.4 * acc
    x = blue + red * 0.7 + red_tone + roar
    x *= np.where(t < 2.98, 1.0, 0.0)             # stops dead: the collision takes over
    return fade(normalize(saturate(x, 1.6), -1.0), 0.2, 0.004)


def nuke_collide():
    dur = 1.35; n = int(dur * SR); t = t_axis(dur)
    crack = highpass(noise(int(0.01 * SR)), 900) * 1.2
    glass = np.zeros(n)
    for fr, a in [(2217, 1.0), (2960, 0.7), (3729, 0.5), (4978, 0.35), (6645, 0.2)]:
        glass += np.sin(2 * np.pi * fr * t) * a * np.exp(-t / 0.25)
    boom = saturate(sweep_tone(70, 35, 0.4) * exp_env(int(0.4 * SR), 0.12), 2.5)
    # everything is dragged back into the point: a reversed swell that cuts to nothing
    swell_n = int(0.75 * SR)
    swell = reverb(bandpass(noise(swell_n, "pink"), 300, 6000) * exp_env(swell_n, 0.2), 1.0, 1.0)[:swell_n][::-1]
    swell *= np.linspace(0.2, 1.0, swell_n) ** 2
    x = mix(n, (crack, 0, 1.0), (glass, 0, 0.25), (boom, 0, 0.9), (swell, 0.35, 0.8))
    x[int(1.12 * SR):] = 0.0
    return fade(normalize(x, -1.0), 0.001, 0.003)


def nuke_bloom():
    dur = 9.5; n = int(dur * SR); t = t_axis(dur)
    drop = saturate(tone_path(50 * (22 / 50) ** np.clip(t / 2.5, 0, 1), np.exp(-t / 2.2)), 2.2)
    # the roar tears open: brown noise whose lowpass opens wide, then closes over a long tail
    roar = noise(n, "brown")
    roar = band_path(roar, centres_for(n, lambda k: 180 + 2600 * np.exp(-((k - 0.6) / 1.4) ** 2)), q=0.8)
    roar_env = np.clip(t / 0.12, 0, 1) * (0.35 + 0.65 * np.exp(-t / 2.6))
    crack = highpass(noise(int(0.02 * SR)), 700) * np.hanning(int(0.02 * SR)) * 1.5
    lightning = crackle(n, 120, 1200, 9000, 1.2) * np.exp(-t / 2.4)
    for _ in range(9):
        p = rng.uniform(0.05, 3.5)
        L = int(rng.uniform(0.05, 0.25) * SR)
        zap = highpass(noise(L), 1500) * exp_env(L, rng.uniform(0.02, 0.07))
        s = int(p * SR)
        lightning[s:s + L] += zap[: max(0, min(L, n - s))] * rng.uniform(0.4, 1.0)
    ring = np.sin(2 * np.pi * 5200 * t) * 0.05 * np.clip((t - 1.2) / 1.5, 0, 1) * np.exp(-np.maximum(0, t - 3.5) / 2.0)
    x = mix(n, (drop, 0, 1.0), (roar * roar_env, 0, 1.2), (crack, 0, 1.0), (lightning, 0, 0.45), (ring, 0, 1.0))
    x = reverb(saturate(x, 1.8), 3.5, 0.3)[:n]
    return fade(normalize(x, -0.5), 0.001, 1.5)


def nuke_glint():
    dur = 2.2; n = int(dur * SR); t = t_axis(dur)
    x = np.zeros(n)
    for fr, a in [(3136, 1.0), (4186, 0.6), (5274, 0.45), (6272, 0.3), (8372, 0.18)]:
        x += np.sin(2 * np.pi * fr * t + rng.uniform(0, 6)) * a * np.exp(-t / 0.55)
    x *= np.clip(t / 0.003, 0, 1) * (0.75 + 0.25 * np.sin(2 * np.pi * 17 * t))
    air = highpass(noise(n), 6000) * np.exp(-t / 0.3) * 0.15
    return fade(normalize(reverb(x + air, 3.0, 0.45)[:n], -4), 0.001, 0.6)


def nuke_aftermath():
    dur = 7.0; n = int(dur * SR); t = t_axis(dur)
    wind = band_path(noise(n, "pink"), centres_for(n, lambda k: np.maximum(140.0, 360 + 170 * np.sin(2 * np.pi * 0.12 * k) + 80 * np.sin(2 * np.pi * 0.31 * k))), q=1.4)
    rumble = lowpass(noise(n, "brown"), 120) * 2.5
    fizz = crackle(n, 6, 2000, 7000, 0.6) * np.exp(-t / 2.5)
    env = np.clip(t / 1.0, 0, 1) * np.exp(-np.maximum(0, t - 3.0) / 2.0)
    x = (wind * 0.8 + rumble + fizz) * env
    return fade(normalize(reverb(x, 2.0, 0.25)[:n], -3), 0.5, 1.5)


def nuke_explosion():
    """The purple mass detonates: a crack, a sub-bass blow that sinks, a roar tearing open, then a long rumble."""
    dur = 8.0; n = int(dur * SR); t = t_axis(dur)
    crack = highpass(noise(int(0.03 * SR)), 900) * np.hanning(int(0.03 * SR)) * 2.0
    boom = saturate(tone_path(46 * (18 / 46) ** np.clip(t / 1.8, 0, 1), np.exp(-t / 1.6)), 2.6)
    roar = band_path(noise(n, "brown"), centres_for(n, lambda k: 120 + 1800 * np.exp(-((k - 0.25) / 1.1) ** 2)), q=0.7)
    roar = roar * np.clip(t / 0.05, 0, 1) * (0.25 + 0.75 * np.exp(-t / 2.2))
    rumble = lowpass(noise(n, "brown"), 90) * 3.0 * np.clip(t / 0.3, 0, 1) * np.exp(-np.maximum(0, t - 2.5) / 2.2)
    sizzle = crackle(n, 90, 1500, 8000, 1.0) * np.exp(-t / 1.8)
    x = mix(n, (crack, 0, 1.0), (boom, 0, 1.0), (roar, 0, 0.9), (rumble, 0, 0.8), (sizzle, 0.05, 0.35))
    return fade(normalize(reverb(x, 3.5, 0.35)[:n], -1.0), 0.002, 1.5)


def nuke_shockwave():
    """The shock front passing over you: a pressure crack, a thump in the chest, a roaring wall of wind and grit."""
    dur = 4.5; n = int(dur * SR); t = t_axis(dur)
    crack = highpass(noise(int(0.05 * SR)), 400) * np.hanning(int(0.05 * SR)) * 1.5
    thump = saturate(tone_path(60 * (25 / 60) ** np.clip(t / 0.8, 0, 1), np.exp(-t / 0.5)), 2.5)
    roar = band_path(noise(n, "brown"), centres_for(n, lambda k: 200 + 2200 * np.exp(-((k - 0.15) / 0.9) ** 2)), q=0.6)
    roar = roar * np.clip(t / 0.03, 0, 1) * np.exp(-t / 1.4)
    grit = crackle(n, 300, 1500, 8000, 0.9) * np.exp(-t / 1.0)
    wind = lowpass(noise(n, "pink"), 800) * np.clip(t / 0.1, 0, 1) * np.exp(-np.maximum(0, t - 0.5) / 1.5)
    x = mix(n, (crack, 0, 1.0), (thump, 0, 0.9), (roar, 0, 1.0), (grit, 0.02, 0.4), (wind, 0, 0.5))
    return fade(normalize(reverb(x, 2.5, 0.3)[:n], -1.0), 0.001, 1.0)


if __name__ == "__main__":
    for fn in (nuke_throw, nuke_boost, nuke_sky_hum, nuke_converge, nuke_collide, nuke_bloom, nuke_glint, nuke_aftermath, nuke_explosion, nuke_shockwave):
        write_ogg(fn(), os.path.join(OUT, fn.__name__ + ".ogg"))
        print("[sfx] wrote", fn.__name__)
