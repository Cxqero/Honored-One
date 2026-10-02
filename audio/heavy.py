"""Heavy sound-design toolkit for the Shinjuku pass (built on sfxlib). The brief: Gojo at the Shinjuku Showdown -
intimidating. Hits you feel in the chest (pitch-dropping sub-bass), space that cracks like struck metal and tears,
booms that distort, tension that rises and is cut dead, a breath of silence before the biggest hits, dark choir and
bell tones, and huge tails. Everything is deterministic (seeded) so a sound rebuilds identically.
"""
import numpy as np
from scipy import signal
from scipy.signal import resample_poly
from sfxlib import SR, t_axis, lowpass, highpass, bandpass, fit, fade

TAU = 2 * np.pi


def R(seed):
    return np.random.default_rng(seed)


def white(n, seed):
    return R(seed).standard_normal(n)


def brown(n, seed):
    x = np.cumsum(R(seed).standard_normal(n))
    x = highpass(x, 15, 2)
    return x / (np.max(np.abs(x)) + 1e-9)


def sat(x, drive=3.0, os=4):
    """Oversampled tanh saturation: grit and loudness without aliasing fizz."""
    up = resample_poly(x, os, 1)
    up = np.tanh(up * drive) / np.tanh(drive)
    return resample_poly(up, 1, os)[: len(x)]


def env(n, attack=0.005, tau=0.3, hold=0.0):
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1) ** 1.5
    return a * np.exp(-np.maximum(t - attack - hold, 0) / tau)


def env_curve(n, points):
    """Piecewise-linear envelope from [(time_s, gain), ...]."""
    t = np.arange(n) / SR
    ts, gs = zip(*points)
    return np.interp(t, ts, gs)


def osc(freq, n, shape="sine", phase0=0.0):
    """Oscillator with a per-sample frequency (array or scalar)."""
    f = np.broadcast_to(np.asarray(freq, dtype=float), (n,))
    ph = phase0 + TAU * np.cumsum(f) / SR
    if shape == "sine":
        return np.sin(ph)
    if shape == "saw":
        return 2 * ((ph / TAU) % 1.0) - 1
    if shape == "tri":
        return 2 * np.abs(2 * ((ph / TAU) % 1.0) - 1) - 1
    raise ValueError(shape)


# ------------------------------------------------------------------ low end
def sub_drop(dur, f0=85, f1=30, tau=0.7, curve=7.0, drive=1.8):
    """The chest hit: a sine whose pitch falls fast from f0 to f1, a touch of saturation so it carries on small speakers."""
    t = t_axis(dur)
    f = f1 + (f0 - f1) * np.exp(-t * curve)
    return sat(osc(f, len(t)) * env(len(t), 0.003, tau), drive)


def kick(dur=0.6, f=48, tau=0.22, seed=0):
    """A pitched thump (body 2.6×f → f) with a click: the 'punch' layer."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    body = osc(f + f * 1.6 * np.exp(-t * 38), n) * env(n, 0.001, tau)
    click = highpass(white(n, seed), 2500, 2) * env(n, 0.0005, 0.004)
    return sat(body + 0.35 * click, 2.2)


def rumble(dur, cutoff=140, tau=None, wobble=2.5, seed=0):
    """Ground rumble: brown noise, low-passed, slowly throbbing."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    x = lowpass(brown(n, seed), cutoff, 3)
    x *= 0.75 + 0.25 * np.sin(TAU * wobble * t + R(seed).uniform(0, TAU))
    if tau is not None:
        x *= env(n, 0.02, tau)
    return x / (np.max(np.abs(x)) + 1e-9)


# ------------------------------------------------------------------ transients
def modal(freqs, amps, taus, dur, seed=0, jitter=0.0):
    """Sum of decaying sines (struck metal, glass, bells)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    rg = R(seed)
    x = np.zeros(n)
    for f, a, tau in zip(freqs, amps, taus):
        f = f * (1 + jitter * rg.uniform(-1, 1))
        x += a * np.sin(TAU * f * t + rg.uniform(0, TAU)) * np.exp(-t / tau)
    return x


def crack(dur=0.8, base=520, seed=0, ring=0.6):
    """Space cracking: a hard white transient, a short tearing rip and an inharmonic metallic ring."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    rg = R(seed)
    hit = highpass(white(n, seed), 1800, 2) * env(n, 0.0004, 0.012)
    gate = (rg.uniform(0, 1, n // 96 + 1) > 0.45).astype(float).repeat(96)[:n]
    rip = bandpass(white(n, seed + 1), 900, 6500) * gate * env(n, 0.002, 0.07)
    ratios = [1.0, 2.76, 5.40, 8.93, 13.34, 19.8]
    metal = modal([base * r for r in ratios], [1, 0.7, 0.5, 0.35, 0.22, 0.12],
                  [0.45, 0.3, 0.2, 0.12, 0.08, 0.05], dur, seed + 2, jitter=0.02)
    return hit * 1.0 + rip * 0.6 + metal * 0.25 * ring


def boom(dur, size=1.0, seed=0, dark=1.0):
    """An explosion body: click, a dark burst of air, the sub drop, a saturated mid crunch and a long rumble."""
    n = int(dur * SR)
    click = highpass(white(n, seed), 2000, 2) * env(n, 0.0005, 0.01)
    air = lowpass(white(n, seed + 1), 1100 / (size * dark), 3) * env(n, 0.003, 0.32 * size)
    sub = fit(sub_drop(dur, 80, 26, 0.9 * size), n)
    crunch = sat(bandpass(white(n, seed + 2), 180, 2400) * env(n, 0.002, 0.14 * size), 4.0)
    tail = rumble(dur, 160, tau=1.4 * size, seed=seed + 3)
    x = 0.4 * click + 1.0 * air + 1.1 * sub + 0.45 * crunch + 0.55 * tail
    return sat(x, 2.0)


# ------------------------------------------------------------------ motion
def whoosh(dur, f_from=300, f_to=3000, peak=0.6, q=1.6, seed=0, width=1.0):
    """Air rushing past: band-passed noise whose centre sweeps, swelling to `peak` (fraction) and away."""
    n = int(dur * SR)
    x = white(n, seed)
    out = np.zeros(n)
    steps = 48
    edges = np.linspace(0, n, steps + 1).astype(int)
    for i in range(steps):
        k = i / (steps - 1)
        fc = f_from * (f_to / f_from) ** k
        bw = fc / q * width
        lo, hi = max(30, fc - bw / 2), min(SR / 2 - 100, fc + bw / 2)
        seg = slice(max(0, edges[i] - 2048), min(n, edges[i + 1] + 2048))
        y = bandpass(x[seg], lo, hi, 2)
        a, b = edges[i] - seg.start, edges[i + 1] - seg.start
        out[edges[i]:edges[i + 1]] = y[a:b]
    shape = env_curve(n, [(0, 0), (peak * dur, 1), (dur, 0)]) ** 1.6
    return out * shape


def riser(dur, f0=80, f1=900, seed=0, noise=0.6):
    """Tension: detuned saws gliding up exponentially, brightening noise and a tremolo that speeds up. Ends at full."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = t / dur
    f = f0 * (f1 / f0) ** (k ** 1.6)
    x = sum(osc(f * d, n, "saw") for d in (0.993, 1.0, 1.008)) / 3
    x = lowpass(x, 2500, 2)
    nz = highpass(white(n, seed), 1500, 2) * k ** 2
    trem = 0.7 + 0.3 * np.sin(TAU * np.cumsum(3 + 22 * k ** 2) / SR)
    return (x * 0.7 + nz * noise) * trem * (k ** 1.3)


def reverse_swell(x, seconds=2.0, seed=0):
    """Played backwards through a hall: sound sucked inward toward the moment (Blue)."""
    wet = hall(x[::-1], seconds, mix=1.0, seed=seed)
    return wet[::-1]


# ------------------------------------------------------------------ tone
def formant(x, vowel="o"):
    """Choir-like vowel colour: the source through three formant band-passes."""
    F = {"a": [(800, 1.0), (1150, 0.6), (2900, 0.25)], "o": [(450, 1.0), (800, 0.55), (2830, 0.18)],
         "u": [(325, 1.0), (700, 0.4), (2700, 0.12)], "e": [(400, 1.0), (1600, 0.5), (2700, 0.25)]}[vowel]
    return sum(bandpass(x, f * 0.88, f * 1.12, 2) * a for f, a in F)


def choir(dur, root=55.0, chord=(0, 7, 12, 15), vowel="o", attack=0.6, seed=0):
    """A dark choir pad: detuned saw voices with slow vibrato, through vowel formants."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    rg = R(seed)
    x = np.zeros(n)
    for s in chord:
        f = root * 2 ** (s / 12)
        for d in (-0.12, 0.0, 0.11):
            vib = 1 + 0.004 * np.sin(TAU * rg.uniform(4.5, 5.8) * t + rg.uniform(0, TAU))
            x += osc(f * 2 ** (d / 12) * vib, n, "saw", rg.uniform(0, TAU))
    y = formant(x / (3 * len(chord)), vowel)
    return y * np.clip(t / attack, 0, 1) ** 2


def bell(freq, dur, seed=0, decay=1.0):
    """A deep struck temple bell (inharmonic partials, slow beating)."""
    ratios = [0.5, 1.0, 1.183, 1.506, 2.0, 2.514, 2.662, 3.011, 4.166]
    amps = [0.9, 1.0, 0.6, 0.5, 0.45, 0.3, 0.28, 0.2, 0.12]
    taus = [4.0, 2.8, 2.2, 1.8, 1.4, 1.0, 0.9, 0.7, 0.4]
    x = modal([freq * r for r in ratios], amps, [tt * decay for tt in taus], dur, seed, jitter=0.002)
    x += 0.5 * modal([freq * r * 1.003 for r in ratios[:4]], amps[:4], taus[:4], dur, seed + 1)   # beating pair
    return x


def drone(dur, root=40.0, cutoff=500, seed=0, beat=0.35):
    """A dark detuned drone with slow beating (dread underneath everything)."""
    n = int(dur * SR)
    rg = R(seed)
    x = sum(osc(root * m * (1 + beat * 0.01 * d), n, "saw", rg.uniform(0, TAU))
            for m in (1, 2, 3) for d in (-1, 0, 1))
    return lowpass(x / 9, cutoff, 3)


def shimmer(dur, freqs=(1760, 2349, 2637, 3520), seed=0, rate=7.0):
    """Six Eyes / Infinity glassy shimmer: high sines with fast random amplitude glints."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    rg = R(seed)
    x = np.zeros(n)
    for f in freqs:
        am = lowpass(rg.uniform(0, 1, n), rate * 2, 2)
        am = (am - am.min()) / (np.ptp(am) + 1e-9)
        x += np.sin(TAU * f * t + rg.uniform(0, TAU)) * am ** 3
    return x / len(freqs)


def crackle(dur, rate=40, seed=0, lo=2000, hi=9000):
    """Cursed-energy arcs: a crackle of short bright bursts."""
    n = int(dur * SR)
    rg = R(seed)
    x = np.zeros(n)
    count = int(rate * dur)
    for _ in range(count):
        s = rg.integers(0, max(1, n - 4000))
        L = rg.integers(80, 2400)
        x[s:s + L] += rg.standard_normal(L) * np.exp(-np.arange(L) / (L / 4)) * rg.uniform(0.3, 1)
    return bandpass(x, lo, hi, 2)


def shatter(dur, density=180, seed=0):
    """Glass breaking: hundreds of tiny high pings and grit bursts, front-loaded."""
    n = int(dur * SR)
    rg = R(seed)
    x = np.zeros(n)
    for _ in range(density):
        s = int(abs(rg.normal(0, 0.18)) * SR)
        if s >= n - 2000:
            continue
        L = int(rg.uniform(0.01, 0.12) * SR)
        L = min(L, n - s)
        tt = np.arange(L) / SR
        f = rg.uniform(2500, 9000)
        x[s:s + L] += np.sin(TAU * f * tt) * np.exp(-tt / rg.uniform(0.008, 0.05)) * rg.uniform(0.2, 1)
    grit = highpass(white(n, seed + 1), 3000, 2) * env(n, 0.001, 0.06)
    return x * 0.6 + grit * 0.8


# ------------------------------------------------------------------ space
def _ir(seconds, seed, damp=5200, early=True):
    rg = R(seed)
    n = int(seconds * SR)
    t = np.arange(n) / SR
    tail = rg.standard_normal(n) * np.exp(-t / (seconds / 6.9))
    # darkens as it decays: two bands, the high one dying faster
    tail = lowpass(tail, damp, 2) * 0.75 + highpass(tail, damp, 2) * np.exp(-t / (seconds / 14)) * 0.25
    if early:
        for _ in range(14):
            d = int(rg.uniform(0.006, 0.085) * SR)
            tail[d] += rg.uniform(-1, 1) * 3.0
    return tail / (np.sqrt(np.sum(tail ** 2)) + 1e-9)


def hall(x, seconds=3.0, mix=0.35, predelay=0.025, damp=5200, seed=11):
    """Convolution reverb with early reflections and a darkening tail. Returns len(x) + the tail."""
    ir = np.concatenate([np.zeros(int(predelay * SR)), _ir(seconds, seed, damp)])
    wet = signal.fftconvolve(x, ir)
    dry = np.pad(x, (0, len(wet) - len(x)))
    return dry * (1 - mix) + wet * mix * 3.0


def stereo_hall(x, seconds=4.0, mix=0.45, predelay=0.03, damp=4800, seed=11):
    """Two decorrelated tails: the dry sound in the middle, the space all around (domain sounds)."""
    l = hall(x, seconds, mix, predelay, damp, seed)
    r = hall(x, seconds, mix, predelay * 1.3, damp, seed + 101)
    m = max(len(l), len(r))
    return np.stack([np.pad(l, (0, m - len(l))), np.pad(r, (0, m - len(r)))], axis=1)


def pan(x, p):
    """Mono → stereo, p in [-1 (left), 1 (right)], equal power."""
    a = (p + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


def width(st, amount=1.0):
    """Widen/narrow a stereo signal (mid/side)."""
    m = (st[:, 0] + st[:, 1]) / 2
    s = (st[:, 0] - st[:, 1]) / 2 * amount
    return np.stack([m + s, m - s], axis=1)


# ------------------------------------------------------------------ assembly
def place(n, *parts):
    """Mix layers into n samples: parts are (signal, start_seconds, gain); stereo if any part is."""
    stereo = any(p[0].ndim == 2 for p in parts)
    out = np.zeros((n, 2)) if stereo else np.zeros(n)
    for sig, start, g in parts:
        if stereo and sig.ndim == 1:
            sig = np.stack([sig, sig], axis=1)
        o = int(start * SR)
        if o >= n:
            continue
        seg = sig[: n - o].copy()
        # a layer that stops while still sounding would click or sound clipped: every layer fades out over its last
        # sixth (up to 0.3 s)
        k = min(int(0.3 * SR), len(seg) // 6)
        if k > 1:
            ramp = np.linspace(1, 0, k)
            seg[-k:] *= ramp[:, None] if seg.ndim == 2 else ramp
        out[o:o + len(seg)] += seg * g
    return out


def finish(x, dur, fout=0.3, fin=0.002):
    """Exact length, DC/rumble below 22 Hz removed, fades, peak -1 dBFS (audio/master.py sets the final loudness)."""
    n = int(dur * SR)
    if x.ndim == 2:
        x = np.stack([fit(highpass(x[:, c], 22, 2), n) for c in range(2)], axis=1)
        for c in range(2):
            x[:, c] = fade(x[:, c], fin, fout)
    else:
        x = fade(fit(highpass(x, 22, 2), n), fin, fout)
    return x / (np.max(np.abs(x)) + 1e-12) * 10 ** (-1 / 20)


def loop_seam(x, xfade=0.4):
    """Seamless loop (mono or stereo): the tail crossfaded into the head, equal power."""
    k = int(xfade * SR)
    w = np.sin(np.linspace(0, np.pi / 2, k)) ** 2
    if x.ndim == 2:
        w = w[:, None]
    head, body, tail = x[:k], x[k:-k], x[-k:]
    return np.concatenate([tail * (1 - w) + head * w, body])
