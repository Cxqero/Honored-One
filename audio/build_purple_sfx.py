"""Hollow Purple sound effects (original designs). Run: python3 audio/build_purple_sfx.py"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sfxlib import *
from build_blue_sfx import band_path, centres_for, OUT
from build_red_sfx import crackle


def pad(freqs, dur, cutoff=900, detune=0.4):
    t = t_axis(dur); x = np.zeros(len(t))
    for f in freqs:
        for d in (-detune, 0, detune):
            ph = rng.uniform(0, 2 * np.pi)
            x += signal.sawtooth(2 * np.pi * (f + d) * t + ph) * 0.3
    return lowpass(x, cutoff, 2)


def purple_form():
    dur = 1.9; n = int(dur * SR); t = t_axis(dur)
    rise = np.clip(t / 1.0, 0, 1) ** 2
    whirl = band_path(noise(n, "pink"), centres_for(n, lambda k: 500 + 400 * np.sin(2 * np.pi * (2 + 4 * np.clip(k, 0, 1)) * k)), q=2.2)
    blue_tone = np.sin(2 * np.pi * np.cumsum(110 + 110 * np.clip(t, 0, 1)) / SR)
    red_tone = np.sin(2 * np.pi * np.cumsum(155 + 180 * np.clip(t, 0, 1)) / SR)
    arcs = crackle(n, 50) * rise
    x = (whirl * 0.8 + 0.3 * blue_tone + 0.25 * red_tone) * rise + arcs * 0.6
    x *= np.where(t < 1.0, 1.0, np.exp(-(t - 1.0) / 0.25))
    return fade(normalize(reverb(saturate(x, 1.5), 1.8, 0.25)[: int(2.6 * SR)], -2), 0.01, 0.4)


def purple_collide():
    dur = 4.0; n = int(dur * SR)
    crack = highpass(noise(int(0.008 * SR)), 1500)
    boom = saturate(sweep_tone(80, 28, 1.6) * exp_env(int(1.6 * SR), 0.5), 2.8)
    chord = pad([55, 58.3, 82.4, 116.5], 3.5, 700) * exp_env(int(3.5 * SR), 1.2)
    bells = np.zeros(int(3.0 * SR))
    for f, a in [(1318, 1.0), (1397, 0.8), (1975, 0.6), (2637, 0.4), (3520, 0.25)]:
        bells += np.sin(2 * np.pi * f * t_axis(3.0)) * exp_env(len(bells), 0.8) * a
    x = mix(n, (crack, 0, 1.0), (boom, 0, 1.1), (chord, 0.02, 0.5), (bells, 0.01, 0.06),
            (lowpass(noise(int(0.6 * SR)), 2000) * exp_env(int(0.6 * SR), 0.1), 0, 0.7))
    return fade(normalize(reverb(x, 3.5, 0.35)[: int(5.0 * SR)], -1), 0.001, 0.8)


def purple_hum():
    dur = 4.35; n = int(dur * SR); t = t_axis(dur)
    drone = saturate(np.sin(2 * np.pi * 36.7 * t) + 0.8 * np.sin(2 * np.pi * 55.0 * t) + 0.5 * np.sin(2 * np.pi * 73.6 * t), 2.2)
    drone *= 0.75 + 0.25 * np.sin(2 * np.pi * 1.0 * t)
    p = pad([110, 164.8, 207.7], dur, 800) * (0.6 + 0.4 * np.sin(2 * np.pi * 0.5 * t))
    x = drone * 0.5 + p * 0.25 + crackle(n, 30) * 0.45 + lowpass(noise(n, "brown"), 70) * 0.5
    return normalize(make_loop(x, 0.35), -2)


def purple_launch():
    dur = 4.5; n = int(dur * SR)
    sub = saturate(sweep_tone(62, 20, 2.5) * exp_env(int(2.5 * SR), 0.9), 2.5)
    roar_n = int(3.5 * SR)
    roar = lowpass(noise(roar_n, "brown"), 450) * np.minimum(1, np.arange(roar_n) / (0.05 * SR)) * exp_env(roar_n, 1.1)
    whoosh = band_path(noise(int(1.2 * SR), "pink"), centres_for(int(1.2 * SR), lambda k: 3000 * (250 / 3000) ** np.clip(k / 1.0, 0, 1)), q=1.4)
    crack = highpass(noise(int(0.006 * SR)), 1500)
    x = mix(n, (crack, 0, 1.0), (sub, 0, 1.1), (roar, 0.01, 1.0), (whoosh * exp_env(len(whoosh), 0.35), 0, 0.8))
    return fade(normalize(reverb(x, 4.0, 0.3)[: int(5.5 * SR)], -1), 0.001, 0.8)


def purple_rush():
    dur = 1.3; n = int(dur * SR)
    r = band_path(noise(n, "pink"), centres_for(n, lambda k: 900 + 600 * np.sin(2 * np.pi * 1.6 * k)), q=1.2)
    x = (r + lowpass(noise(n, "brown"), 200) * 0.8) * env_adsr(n, 0.08, 0.2, 0.8, 0.5)
    return fade(normalize(x, -4), 0.02, 0.3)


def purple_charge():
    dur = 4.35; n = int(dur * SR); t = t_axis(dur)
    blue = band_path(noise(n, "pink"), centres_for(n, lambda k: 600 + 300 * np.sin(2 * np.pi * 1.0 * k)), q=2.5)
    red = crackle(n, 55)
    drone = saturate(np.sin(2 * np.pi * 49 * t) + 0.6 * np.sin(2 * np.pi * 36.7 * t), 2.0) * (0.7 + 0.3 * np.sin(2 * np.pi * 2 * t))
    x = blue * 0.8 + red * 0.6 + drone * 0.45 + pad([98, 146.8], dur, 600) * 0.2
    return normalize(make_loop(x, 0.35), -2.5)


def purple_incant():
    dur = 3.0; n = int(dur * SR)
    drum = sweep_tone(58, 38, 1.1) * exp_env(int(1.1 * SR), 0.3)
    thud = lowpass(noise(int(0.15 * SR)), 700) * exp_env(int(0.15 * SR), 0.04)
    bell = np.zeros(int(2.6 * SR))
    for f, a, d in [(392, 1.0, 1.1), (905, 0.45, 0.7), (1260, 0.3, 0.5), (1790, 0.18, 0.35)]:
        bell += np.sin(2 * np.pi * f * t_axis(2.6)) * exp_env(len(bell), d) * a
    choir = pad([196, 233.1, 293.7], 2.6, 1400, 0.8) * env_adsr(int(2.6 * SR), 0.2, 0.3, 0.6, 1.4)
    x = mix(n, (drum, 0, 1.0), (thud, 0, 0.8), (bell, 0.01, 0.2), (choir, 0.02, 0.12))
    return fade(normalize(reverb(x, 3.2, 0.38)[: int(3.8 * SR)], -2), 0.001, 0.6)


def purple_impact():
    """Where the mass strikes: a crack, a hard low blow, a glassy violet shimmer and debris raining down."""
    from build_red_sfx import crackle
    from build_nuke_sfx import tone_path
    dur = 3.2; n = int(dur * SR); t = t_axis(dur)
    crack = highpass(noise(int(0.02 * SR)), 1200) * np.hanning(int(0.02 * SR)) * 1.8
    blow = saturate(tone_path(85 * (30 / 85) ** np.clip(t / 0.6, 0, 1), np.exp(-t / 0.45)), 2.4)
    body = lowpass(noise(n, "brown"), 400) * 2.0 * np.clip(t / 0.01, 0, 1) * np.exp(-t / 0.7)
    shimmer = sum(np.sin(2 * np.pi * f * t + i) * a for i, (f, a) in enumerate([(1661, 0.5), (2489, 0.35), (3322, 0.25)])) * np.exp(-t / 0.9) * 0.2
    debris = crackle(n, 40, 900, 6000, 0.8) * np.clip((t - 0.15) / 0.2, 0, 1) * np.exp(-t / 1.1)
    x = mix(n, (crack, 0, 1.0), (blow, 0, 1.0), (body, 0, 0.7), (shimmer, 0.01, 1.0), (debris, 0, 0.5))
    return fade(normalize(reverb(x, 2.2, 0.3)[:n], -1.0), 0.001, 0.8)


if __name__ == "__main__":
    write_ogg(purple_form(), os.path.join(OUT, "purple_form.ogg"))
    write_ogg(purple_collide(), os.path.join(OUT, "purple_collide.ogg"))
    write_ogg(purple_hum(), os.path.join(OUT, "purple_hum.ogg"))
    write_ogg(purple_launch(), os.path.join(OUT, "purple_launch.ogg"))
    write_ogg(purple_rush(), os.path.join(OUT, "purple_rush.ogg"))
    write_ogg(purple_charge(), os.path.join(OUT, "purple_charge.ogg"))
    write_ogg(purple_incant(), os.path.join(OUT, "purple_incant.ogg"))
