"""Lapse: Blue + Infinity sound effects (original designs).
Run: python3 audio/build_blue_sfx.py  → mod/src/main/resources/assets/gojolimitless/sounds/*.ogg"""
import os, sys
import numpy as np
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sfxlib import *

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "sounds")


def band_path(x, centres, q=2.5):
    """Band-pass whose centre frequency follows `centres` (one value per 1024-sample hop)."""
    hop = 1024
    n = len(x)
    out = np.zeros(n + 2 * hop)
    win = np.hanning(2 * hop)
    xp = np.pad(x, (hop, hop))
    for i, c in enumerate(centres):
        s0 = i * hop
        seg = xp[s0:s0 + 2 * hop]
        if len(seg) < 2 * hop:
            break
        bw = c / q
        lo, hi = max(20, c - bw / 2), min(SR / 2 - 200, c + bw / 2)
        y = signal.sosfilt(signal.butter(2, [lo, hi], btype="band", fs=SR, output="sos"), np.pad(seg, (2048, 0)))[2048:]
        out[s0:s0 + 2 * hop] += y * win
    return out[hop:hop + n]


def centres_for(n, fn):
    hop = 1024
    k = np.arange(n // hop + 2) * hop / SR
    return fn(k)


# ---------------------------------------------------------------- blue.form
def blue_form():
    dur = 1.8; n = int(dur * SR)
    inhale_n = int(0.45 * SR)
    inhale = band_path(noise(inhale_n, "pink"), centres_for(inhale_n, lambda t: 6000 * (400 / 6000) ** (t / 0.45)), q=1.8)
    inhale *= np.linspace(0, 1, inhale_n) ** 2.5
    thump_n = int(0.6 * SR)
    thump = sweep_tone(95, 42, 0.6) * exp_env(thump_n, 0.14)
    click = highpass(noise(int(0.006 * SR)), 2500) * np.hanning(int(0.006 * SR))
    hum_n = int(1.4 * SR)
    t = t_axis(1.4)
    hum = (np.sin(2 * np.pi * 110 * t) + np.sin(2 * np.pi * 111.4 * t) + 0.35 * np.sin(2 * np.pi * 220.7 * t)
           + 0.15 * np.sin(2 * np.pi * 331 * t)) * env_adsr(hum_n, 0.08, 0.3, 0.35, 0.7)
    whine = np.sin(2 * np.pi * (1900 * t + 6 * np.sin(2 * np.pi * 5.5 * t))) * env_adsr(hum_n, 0.15, 0.2, 0.4, 0.8) * 0.12
    x = mix(n, (inhale, 0.0, 0.8), (thump, 0.42, 1.0), (click, 0.42, 0.6), (hum, 0.42, 0.28), (whine, 0.45, 1.0))
    x = saturate(x, 1.4)
    return fade(normalize(reverb(x, 2.2, 0.25)[: int(2.6 * SR)], -1.5), 0.002, 0.3)


# ---------------------------------------------------------------- blue.loop
def blue_loop(heavy=False):
    dur = 4.35; n = int(dur * SR)   # 4.0 s after the loop crossfade
    t = t_axis(dur)
    if not heavy:
        drone = (np.sin(2 * np.pi * 55 * t) + 0.7 * np.sin(2 * np.pi * 82.75 * t) + 0.45 * np.sin(2 * np.pi * 110.25 * t))
        drone *= 0.75 + 0.25 * np.sin(2 * np.pi * 0.5 * t)
        whirl = band_path(noise(n, "pink"), centres_for(n, lambda k: 700 + 380 * np.sin(2 * np.pi * 1.0 * k)), q=2.2)
        sub = lowpass(noise(n, "brown"), 80) * 0.6
        whine = np.sin(2 * np.pi * (2400 * t + 3 * np.sin(2 * np.pi * 5 * t))) * 0.05
        x = drone * 0.35 + whirl * 1.1 + sub + whine
    else:
        drone = saturate(np.sin(2 * np.pi * 41.2 * t) + 0.8 * np.sin(2 * np.pi * 61.8 * t) + 0.5 * np.sin(2 * np.pi * 82.4 * t), 2.2)
        drone *= 0.8 + 0.2 * np.sin(2 * np.pi * 0.75 * t)
        w1 = band_path(noise(n, "pink"), centres_for(n, lambda k: 520 + 300 * np.sin(2 * np.pi * 0.75 * k)), q=1.8)
        w2 = band_path(noise(n, "white"), centres_for(n, lambda k: 1600 + 800 * np.sin(2 * np.pi * 1.25 * k + 1.0)), q=2.5) * 0.35
        sub = lowpass(noise(n, "brown"), 60) * 1.1
        crack = np.zeros(n)
        for _ in range(int(dur * 22)):
            p = rng.integers(0, n - 4000)
            L = int(rng.uniform(0.004, 0.03) * SR)
            g = rng.uniform(0.1, 0.5)
            crack[p:p + L] += lowpass(noise(L), 3000) * np.hanning(L) * g
            if rng.random() < 0.3:
                L2 = int(0.08 * SR)
                crack[p:p + L2] += np.sin(2 * np.pi * rng.uniform(60, 110) * np.arange(L2) / SR) * exp_env(L2, 0.02) * g
        x = drone * 0.4 + w1 * 1.2 + w2 + sub + crack * 0.8
    x = make_loop(x, 0.35)
    return normalize(x, -2.0)


# ---------------------------------------------------------------- blue.charge
def blue_charge():
    dur = 2.4; n = int(dur * SR)
    sw = band_path(noise(n, "pink"), centres_for(n, lambda k: 300 * (2600 / 300) ** np.clip(k / 2.2, 0, 1)), q=2.0)
    rise = sweep_tone(70, 150, dur) * 0.5 + sweep_tone(140, 300, dur) * 0.2
    env = np.linspace(0, 1, n) ** 1.8
    x = (sw * 0.9 + rise) * env
    x = saturate(x, 1.6)
    return fade(normalize(reverb(x, 1.6, 0.2)[: int(3.0 * SR)], -2), 0.05, 0.5)


# ---------------------------------------------------------------- blue.throw
def blue_throw():
    dur = 1.6; n = int(dur * SR)
    w = band_path(noise(n, "pink"), centres_for(n, lambda k: 2600 * (260 / 2600) ** np.clip(k / 0.9, 0, 1)), q=1.6)
    hump = np.exp(-((t_axis(dur) - 0.22) / 0.16) ** 2)
    whump = sweep_tone(80, 45, 0.7) * exp_env(int(0.7 * SR), 0.18)
    x = mix(n, (w * hump, 0, 1.2), (whump, 0.05, 0.9))
    return fade(normalize(reverb(saturate(x, 1.5), 2.0, 0.28)[: int(2.4 * SR)], -1.5), 0.002, 0.4)


# ---------------------------------------------------------------- blue.collapse
def blue_collapse():
    dur = 3.2; n = int(dur * SR)
    sn = int(0.30 * SR)
    suck = band_path(noise(sn, "pink"), centres_for(sn, lambda k: 5200 * (220 / 5200) ** (k / 0.30)), q=1.6)
    suck *= np.linspace(0, 1, sn) ** 3
    tension = sweep_tone(220, 1300, 0.30) * np.linspace(0, 1, sn) ** 2 * 0.25
    bn = int(1.4 * SR)
    boom = saturate(sweep_tone(72, 30, 1.4) * exp_env(bn, 0.45), 2.5)
    burst = lowpass(noise(int(0.5 * SR)), 1400) * exp_env(int(0.5 * SR), 0.07)
    crack = highpass(noise(int(0.004 * SR)), 3000)
    rumble = lowpass(noise(int(2.6 * SR), "brown"), 120) * exp_env(int(2.6 * SR), 0.7)
    gl_n = int(0.6 * SR)
    glint = (np.sin(2 * np.pi * 3200 * t_axis(0.6)) + 0.6 * np.sin(2 * np.pi * 4810 * t_axis(0.6))) * exp_env(gl_n, 0.12) * 0.12
    x = mix(n, (suck, 0, 0.9), (tension, 0, 1.0), (boom, 0.33, 1.1), (burst, 0.33, 0.7), (crack, 0.33, 0.8),
            (rumble, 0.36, 0.7), (glint, 0.34, 1.0))
    return fade(normalize(reverb(x, 3.0, 0.3)[: int(4.2 * SR)], -1.0), 0.002, 0.6)


# ---------------------------------------------------------------- blue.tear
def blue_tear():
    dur = 1.1; n = int(dur * SR)
    x = np.zeros(n)
    for i in range(140):
        p = int(min(n - 3000, abs(rng.normal(0.15, 0.2)) * SR))
        L = int(rng.uniform(0.005, 0.04) * SR)
        x[p:p + L] += bandpass(noise(L), 180, 2400) * np.hanning(L) * rng.uniform(0.2, 1.0)
    rumble = lowpass(noise(n, "brown"), 110) * exp_env(n, 0.35)
    x = x * 0.8 + rumble
    return fade(normalize(reverb(x, 1.2, 0.15)[: int(1.5 * SR)], -3), 0.002, 0.3)


# ---------------------------------------------------------------- infinity
def infinity_stop():
    dur = 0.8; n = int(dur * SR)
    thw = sweep_tone(190, 135, 0.5) * exp_env(int(0.5 * SR), 0.09)
    glass = np.sin(2 * np.pi * 1420 * t_axis(0.5)) * exp_env(int(0.5 * SR), 0.05) * 0.25
    air = highpass(noise(int(0.3 * SR)), 4500) * exp_env(int(0.3 * SR), 0.04) * 0.3
    x = mix(n, (thw, 0, 1.0), (glass, 0, 1.0), (air, 0, 1.0))
    return fade(normalize(reverb(x, 1.2, 0.3)[: int(1.3 * SR)], -4), 0.001, 0.2)


def infinity_toggle():
    dur = 1.0; n = int(dur * SR)
    parts = []
    for i, f in enumerate([660, 990, 1320, 1980]):
        L = int(0.8 * SR)
        parts.append((np.sin(2 * np.pi * f * t_axis(0.8)) * exp_env(L, 0.25) * (0.5 / (i + 1) ** 0.5), i * 0.025, 1.0))
    x = mix(n, *parts)
    return fade(normalize(reverb(x, 2.0, 0.4)[: int(1.8 * SR)], -5), 0.002, 0.3)


if __name__ == "__main__":
    write_ogg(blue_form(), os.path.join(OUT, "blue_form.ogg"))
    write_ogg(blue_loop(False), os.path.join(OUT, "blue_loop.ogg"))
    write_ogg(blue_loop(True), os.path.join(OUT, "blue_max_loop.ogg"))
    write_ogg(blue_charge(), os.path.join(OUT, "blue_charge.ogg"))
    write_ogg(blue_throw(), os.path.join(OUT, "blue_throw.ogg"))
    write_ogg(blue_collapse(), os.path.join(OUT, "blue_collapse.ogg"))
    write_ogg(blue_tear(), os.path.join(OUT, "blue_tear.ogg"))
    write_ogg(infinity_stop(), os.path.join(OUT, "infinity_stop.ogg"))
    write_ogg(infinity_toggle(), os.path.join(OUT, "infinity_toggle.ogg"))
