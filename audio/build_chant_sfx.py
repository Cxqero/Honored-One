"""One cue per chanted word, a different voice for each technique's incantation (they must never sound alike):

red_incant      Red — 位相・波羅蜜・光の柱 (Phase, Paramita, Pillar of Light): hot and outward — a bright struck
                ring, a crackle of fire, a short rising sizzle that pushes away.
blue_incant     Blue — 位相・黄昏・智慧の瞳 (Phase, Twilight, Eyes of Wisdom): cold and inward — a swell sucked
                in backwards, a glassy chime at the point of collapse, a sub-bass pull.
purple_incant   Purple — 九綱・偏光・烏と声明・表裏の間: deep and ritual — a low temple bell with beating
                overtones, a dark choir, a sub drop.

Every cue is a resource-pack sound slot, so a voice pack can replace them with spoken lines.
    python3 audio/build_chant_sfx.py
"""
import os
import numpy as np
from sfxlib import *

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources", "assets",
                   "gojolimitless", "sounds")


def partials(spec, dur):
    """Sum of decaying sines: [(freq, amp, tau), ...]."""
    t = t_axis(dur)
    x = np.zeros(len(t))
    for f, a, tau in spec:
        x += np.sin(2 * np.pi * f * t + np.random.default_rng(int(f)).uniform(0, 6.28)) * a * np.exp(-t / tau)
    return x


def red_incant():
    dur = 2.2; n = int(dur * SR); t = t_axis(dur)
    ring = partials([(880, 1.0, 0.5), (1320, 0.55, 0.35), (2210, 0.4, 0.25), (3480, 0.22, 0.15), (5200, 0.1, 0.08)], dur)
    ring *= np.clip(t / 0.002, 0, 1)
    crackle = noise(n) * (np.random.default_rng(3).random(n) < 0.012)
    crackle = bandpass(crackle, 1500, 7000) * np.exp(-t / 0.35) * 4.0
    sizzle = bandpass(noise(n), 2500, 9000) * np.clip((t - 0.03) / 0.12, 0, 1) * np.exp(-np.maximum(t - 0.15, 0) / 0.18) * 0.35
    kick = sweep_tone(140, 55, 0.25) * exp_env(int(0.25 * SR), 0.06)
    x = mix(n, (ring, 0, 0.55), (crackle, 0, 0.6), (sizzle, 0, 1.0), (kick, 0, 0.9))
    return fade(normalize(reverb(saturate(x, 1.3), 2.0, 0.28)[: int(2.8 * SR)], -2), 0.001, 0.5)


def blue_incant():
    dur = 2.6; n = int(dur * SR); t = t_axis(dur)
    pull_at = 0.55                                            # the moment the swell collapses into the chime
    swell = bandpass(noise(n, "pink"), 300, 3000) * np.where(t < pull_at, (t / pull_at) ** 3, np.exp(-(t - pull_at) / 0.05))
    whistle = sweep_tone(900, 2400, pull_at) * (t_axis(pull_at) / pull_at) ** 2 * 0.25
    chime = partials([(1567, 1.0, 1.2), (2349, 0.6, 0.9), (3322, 0.45, 0.7), (4435, 0.3, 0.5), (6270, 0.15, 0.3)], dur - pull_at)
    sub = sweep_tone(62, 34, dur - pull_at) * np.exp(-t_axis(dur - pull_at) / 0.7)
    x = mix(n, (swell, 0, 0.8), (whistle, 0, 1.0), (chime, pull_at, 0.35), (sub, pull_at, 0.9))
    return fade(normalize(reverb(x, 3.0, 0.4)[: int(3.6 * SR)], -2.5), 0.001, 0.8)


def purple_incant():
    dur = 3.6; n = int(dur * SR); t = t_axis(dur)
    # a bonshō: low fundamental, close pairs of partials that beat against each other
    bell = partials([(98, 1.0, 2.2), (99.6, 0.8, 2.0), (196.8, 0.5, 1.4), (271, 0.45, 1.1), (274, 0.35, 1.0),
                     (412, 0.25, 0.8), (620, 0.12, 0.5)], dur)
    bell *= np.clip(t / 0.003, 0, 1)
    strike = lowpass(noise(int(0.08 * SR)), 1200) * exp_env(int(0.08 * SR), 0.02)
    choir = np.zeros(n)
    for f in (146.8, 174.6, 220.0, 293.7):
        choir += np.sin(2 * np.pi * f * t + 0.3 * np.sin(2 * np.pi * 5.1 * t)) * 0.25
    choir = lowpass(choir, 1100) * env_adsr(n, 0.35, 0.4, 0.55, 1.2)
    drop = sweep_tone(55, 28, 1.6) * exp_env(int(1.6 * SR), 0.5)
    x = mix(n, (bell, 0, 0.9), (strike, 0, 0.6), (choir, 0.05, 0.3), (drop, 0, 0.8))
    return fade(normalize(reverb(saturate(x, 1.15), 3.4, 0.42)[: int(4.4 * SR)], -2), 0.001, 1.0)


if __name__ == "__main__":
    for fn in (red_incant, blue_incant, purple_incant):
        write_ogg(fn(), os.path.join(OUT, fn.__name__ + ".ogg"))
        print("wrote", fn.__name__)
