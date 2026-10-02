"""Reversal: Red sound effects (original designs). Run: python3 audio/build_red_sfx.py"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sfxlib import *
from build_blue_sfx import band_path, centres_for, OUT


def crackle(n, density, lo=1800, hi=7000, gain=1.0):
    x = np.zeros(n)
    for _ in range(int(density * n / SR)):
        p = rng.integers(0, max(1, n - 3000))
        L = int(rng.uniform(0.002, 0.018) * SR)
        buzz = np.sign(np.sin(2 * np.pi * rng.uniform(80, 240) * np.arange(L) / SR)) * 0.3
        x[p:p + L] += (bandpass(noise(L), lo, hi) + buzz) * np.hanning(L) * rng.uniform(0.2, 1.0) * gain
    return x


def red_form():
    dur = 1.0; n = int(dur * SR); t = t_axis(dur)
    f = 400 * (1600 / 400) ** np.clip(t / 0.18, 0, 1)
    zap = np.sin(2 * np.pi * np.cumsum(f) / SR + 3 * np.sin(2 * np.pi * 90 * t)) * exp_env(n, 0.12)
    pop = sweep_tone(120, 50, 0.3) * exp_env(int(0.3 * SR), 0.06)
    x = mix(n, (zap, 0, 0.5), (crackle(n, 60) * exp_env(n, 0.2), 0, 0.6), (pop, 0, 0.9))
    return fade(normalize(reverb(saturate(x, 1.6), 1.2, 0.2)[: int(1.5 * SR)], -2), 0.001, 0.2)


def red_charge_loop():
    dur = 4.35; n = int(dur * SR); t = t_axis(dur)
    drone = saturate(np.sin(2 * np.pi * 49 * t) + 0.7 * np.sin(2 * np.pi * 69.3 * t) + 0.4 * np.sin(2 * np.pi * 98 * t), 2.5)
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * 3.0 * t)
    sizzle = highpass(noise(n, "white"), 5000) * 0.06
    arcs = crackle(n, 45)
    whirl = band_path(noise(n, "pink"), centres_for(n, lambda k: 1400 + 700 * np.sin(2 * np.pi * 1.5 * k)), q=3.0) * 0.5
    x = drone * 0.45 * trem + sizzle + arcs * 0.7 + whirl
    return normalize(make_loop(x, 0.35), -2.5)


def red_incant():
    dur = 2.4; n = int(dur * SR)
    drum = sweep_tone(72, 46, 0.9) * exp_env(int(0.9 * SR), 0.22)
    thud = lowpass(noise(int(0.12 * SR)), 900) * exp_env(int(0.12 * SR), 0.03)
    bell = np.zeros(int(2.0 * SR))
    for f, a, d in [(523, 1.0, 0.9), (1190, 0.5, 0.6), (1682, 0.35, 0.45), (2350, 0.2, 0.3), (3120, 0.12, 0.2)]:
        bell += np.sin(2 * np.pi * f * t_axis(2.0)) * exp_env(len(bell), d) * a
    x = mix(n, (drum, 0, 1.0), (thud, 0, 0.8), (bell, 0.01, 0.22))
    return fade(normalize(reverb(x, 3.0, 0.35)[: int(3.2 * SR)], -2), 0.001, 0.5)


def red_fire():
    dur = 1.4; n = int(dur * SR)
    crack = highpass(noise(int(0.01 * SR)), 2000) * np.hanning(int(0.01 * SR))
    whoosh = band_path(noise(int(0.8 * SR), "pink"), centres_for(int(0.8 * SR), lambda k: 4200 * (300 / 4200) ** np.clip(k / 0.7, 0, 1)), q=1.5)
    whoosh *= exp_env(len(whoosh), 0.25)
    punch = saturate(sweep_tone(110, 45, 0.4) * exp_env(int(0.4 * SR), 0.09), 2.0)
    x = mix(n, (crack, 0, 1.0), (whoosh, 0.0, 0.9), (punch, 0, 1.0), (crackle(int(0.5 * SR), 40) * exp_env(int(0.5 * SR), 0.12), 0, 0.5))
    return fade(normalize(reverb(x, 1.8, 0.22)[: int(2.2 * SR)], -1.5), 0.001, 0.3)


def red_fly():
    dur = 0.9; n = int(dur * SR); t = t_axis(dur)
    tear = band_path(noise(n, "white"), centres_for(n, lambda k: 1800 + 900 * np.sin(2 * np.pi * 7 * k)), q=2.0)
    whistle = np.sin(2 * np.pi * (900 * t + 40 * np.sin(2 * np.pi * 6 * t))) * 0.15
    x = (tear * 0.8 + whistle) * env_adsr(n, 0.05, 0.1, 0.8, 0.4)
    return fade(normalize(x, -6), 0.02, 0.2)


def red_detonate(big=False):
    dur = 5.0 if big else 3.0; n = int(dur * SR)
    crack = highpass(noise(int(0.006 * SR)), 1800)
    L = 1.8 if big else 1.0
    whomp = saturate(sweep_tone(95 if not big else 70, 34 if not big else 22, L) * exp_env(int(L * SR), 0.35 if not big else 0.7), 2.8)
    burst = lowpass(noise(int(0.8 * SR)), 2600) * exp_env(int(0.8 * SR), 0.09 if not big else 0.16)
    rl = 2.6 if not big else 4.5
    rumble = lowpass(noise(int(rl * SR), "brown"), 140) * exp_env(int(rl * SR), 0.6 if not big else 1.2)
    debris = crackle(int(1.5 * SR), 70, 300, 3500) * exp_env(int(1.5 * SR), 0.4)
    x = mix(n, (crack, 0, 1.0), (whomp, 0, 1.1), (burst, 0, 0.9), (rumble, 0.02, 0.8), (debris, 0.12, 0.5))
    return fade(normalize(reverb(x, 3.5 if big else 2.5, 0.3 if big else 0.25)[: int((dur + 1.0) * SR)], -1.0), 0.001, 0.8)


if __name__ == "__main__":
    write_ogg(red_form(), os.path.join(OUT, "red_form.ogg"))
    write_ogg(red_charge_loop(), os.path.join(OUT, "red_charge.ogg"))
    write_ogg(red_incant(), os.path.join(OUT, "red_incant.ogg"))
    write_ogg(red_fire(), os.path.join(OUT, "red_fire.ogg"))
    write_ogg(red_fly(), os.path.join(OUT, "red_fly.ogg"))
    write_ogg(red_detonate(False), os.path.join(OUT, "red_detonate.ogg"))
    write_ogg(red_detonate(True), os.path.join(OUT, "red_detonate_big.ogg"))
