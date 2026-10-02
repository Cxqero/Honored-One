"""Domain Expansion: Unlimited Void — sound design (original, in stereo, timed to the domain's phases).
Run: python3 audio/build_domain_sfx.py [name ...]

Stereo on purpose: Minecraft doesn't place stereo sounds in the world, it plays them around the listener. The domain
isn't somewhere in the world, you're inside it.

domain_seal      (0 → 2.3 s)  the hand rises: a cloth sweep, the seal set with a dry tick; the Six Eyes open in a
                              crystalline shimmer that climbs while a low pressure gathers; the world breathes in
domain_white     (→ 1.0 s)    everything drops out: a thin pure ring, a breath of choir, a pressure in the ears, and a
                              sharp inhale into the ink
domain_ink       (→ 2.4 s)    the ink erupts, deep and wet, droplets scattering; then space manifests: neon lines zip
                              into being, faster and faster, crackling, over a chord that swells out of the dark
domain_tunnel    (2.8 s)      the flood of information: an endless rising Shepard glissando that keeps accelerating,
                              rushing air, chattering data, a heartbeat quickening, all cut dead at the flash
domain_open      (→ 6.5 s)    the flash: a vast struck bell over a floor that drops away, crystal overtones, and the
                              reverb opens into infinite space, the void's chord rising under it
domain_void      (8 s loop)   the inside: a deep chord breathing, cosmic wind moving across you, distant stars ringing
                              out, something vast and far away singing once
domain_collapse  (→ 2.4 s)    the void is inhaled, cracks, and shatters like glass; the ink splashes, the world rushes back
domain_instant   (→ 1.9 s)    the 0.2-second domain at its real beats: snap, ring, ink, flash-bell, wipe, silence
"""
import os, sys
import numpy as np
from scipy import signal
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from sfxlib import SR, t_axis, noise, lowpass, highpass, bandpass, saturate, sweep_tone
from build_blue_sfx import OUT

R = np.random.default_rng(1377)


# ================================================================== stereo toolkit
def st(x, pan=0.0):
    """Mono → stereo, constant-power pan (-1 left … +1 right)."""
    a = (np.clip(pan, -1, 1) + 1) * np.pi / 4
    return np.stack([x * np.cos(a), x * np.sin(a)], axis=1)


def zeros(dur):
    return np.zeros((int(round(dur * SR)), 2))


def put(dst, src, at):
    """Adds src (stereo) into dst at time `at` (s), clipped to dst."""
    i = int(round(at * SR))
    if i >= len(dst):
        return dst
    n = min(len(src), len(dst) - i)
    dst[i:i + n] += src[:n]
    return dst


def env(n, pts):
    """Piecewise-linear envelope from (time_s, level) points."""
    t = np.arange(n) / SR
    xs, ys = zip(*pts)
    return np.interp(t, xs, ys)


def ir(seconds, damp, seed, predelay=0.02, diffuse=6.9):
    g = np.random.default_rng(seed)
    n = int(seconds * SR)
    h = g.standard_normal(n) * np.exp(-np.arange(n) / (seconds * SR / diffuse))
    h = lowpass(h, damp, 2)
    h = np.concatenate([np.zeros(int(predelay * SR)), h])
    return h / (np.sqrt(np.sum(h ** 2)) + 1e-9)


def verb(x, seconds=2.5, mix=0.35, damp=7000, predelay=0.02, keep=True):
    """Stereo hall: a decorrelated impulse per channel (width), the dry signal kept in place."""
    L = int(seconds * SR)
    out = np.zeros((len(x) + L + int(predelay * SR) + 8, 2))
    for c in range(2):
        wet = signal.fftconvolve(x[:, c] if x.ndim == 2 else x, ir(seconds, damp, 11 + c * 7, predelay))
        out[:len(wet), c] += wet * mix
        dry = x[:, c] if x.ndim == 2 else x
        out[:len(dry), c] += dry * (1 - mix)
    return out[:len(x)] if not keep else out


def cverb(x, seconds, mix, damp=6000):
    """Circular reverb: the tail wraps around (for seamless loops)."""
    n = len(x)
    out = np.zeros_like(x)
    for c in range(2):
        h = ir(min(seconds, n / SR * 0.95), damp, 23 + c * 5)
        H = np.fft.rfft(np.pad(h, (0, max(0, n - len(h))))[:n])
        wet = np.fft.irfft(np.fft.rfft(x[:, c]) * H, n)
        out[:, c] = x[:, c] * (1 - mix) + wet * mix
    return out


def fin(x, peak_db=-1.0, fade_in=0.004, fade_out=0.08):
    x = x.copy()
    n = len(x)
    a, b = int(fade_in * SR), int(fade_out * SR)
    if a:
        x[:a] *= np.linspace(0, 1, a)[:, None]
    if b:
        x[n - b:] *= np.linspace(1, 0, b)[:, None]
    p = np.max(np.abs(x)) + 1e-9
    return x / p * 10 ** (peak_db / 20)


def glue(x, drive=2.0):
    """Soft saturation that pulls the body up under the transients."""
    return np.tanh(x / (np.max(np.abs(x)) + 1e-9) * drive) / np.tanh(drive)


def write_st(x, name, quality=7):
    import subprocess
    from scipy.io import wavfile
    path = os.path.join(OUT, name + ".ogg")
    wav = path[:-4] + ".tmp.wav"
    wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", wav, "-ac", "2", "-c:a", "libvorbis", "-q:a", str(quality), path], check=True)
    os.remove(wav)
    print(f"[sfx] wrote {name}.ogg {len(x) / SR:.2f}s stereo")


# ================================================================== voices
def phase_of(freq):
    return 2 * np.pi * np.cumsum(freq) / SR


def partials(dur, spec, detune=0.0, seed=0):
    """Additive: (freq, amp, decay_s) partials; each doubled slightly detuned for a living beat."""
    g = np.random.default_rng(seed)
    t = t_axis(dur)
    x = np.zeros(len(t))
    for f, a, tau in spec:
        ph = g.uniform(0, 2 * np.pi)
        e = np.exp(-t / tau)
        x += a * e * np.sin(2 * np.pi * f * t + ph)
        if detune:
            x += a * 0.6 * e * np.sin(2 * np.pi * (f + detune * (0.5 + g.random())) * t + ph + 1.3)
    return x


def fm(dur, fc, ratio, index, idx_decay, amp_decay, attack=0.002):
    t = t_axis(dur)
    I = index * np.exp(-t / idx_decay)
    x = np.sin(2 * np.pi * fc * t + I * np.sin(2 * np.pi * fc * ratio * t))
    return x * np.minimum(1, t / attack) * np.exp(-t / amp_decay)


def zip_line(dur, f0, f1, bright=2.5):
    """A neon line drawing itself: a bright FM chirp with a sparkling tail."""
    t = t_axis(dur)
    f = f0 * (f1 / f0) ** (t / dur) ** 0.6
    mod = np.sin(phase_of(f * 1.51)) * bright * np.exp(-t / (dur * 0.5))
    x = np.sin(phase_of(f) + mod)
    e = np.minimum(1, t / 0.004) * np.exp(-t / (dur * 0.45))
    return x * e


def shepard(dur, rate0, rate1, base=32.0, octaves=8, centre=None, width=1.6):
    """Shepard-Risset glissando: octave-spaced sines rising forever under a fixed spectral bell; rate in oct/s."""
    t = t_axis(dur)
    rate = rate0 + (rate1 - rate0) * (t / dur) ** 1.6
    pos = np.cumsum(rate) / SR
    centre = centre if centre is not None else octaves / 2
    x = np.zeros(len(t))
    for k in range(octaves):
        o = (k + pos) % octaves
        f = base * 2 ** o
        w = np.exp(-0.5 * ((o - centre) / width) ** 2)
        x += w * np.sin(phase_of(f) + k)
    return x


def formant(n, formants, q=6.0, color="pink"):
    """Breathy vowel: noise through parallel resonances (e.g. 'ah': 700/1220/2600)."""
    src = noise(n, color)
    x = np.zeros(n)
    for f, a in formants:
        b, aa = signal.iirpeak(f, q, SR)
        x += a * signal.lfilter(b, aa, src)
    return x


def droplets(dur, count, lo, hi, t0, t1, seed, decay=0.035):
    """Wet droplets: short resonant pings scattered in time and across the stereo field."""
    g = np.random.default_rng(seed)
    out = zeros(dur)
    for _ in range(count):
        at = t0 + (t1 - t0) * g.random() ** 1.7
        f = g.uniform(lo, hi)
        L = int(0.12 * SR)
        tt = np.arange(L) / SR
        ping = np.sin(2 * np.pi * f * tt * (1 + 0.25 * np.exp(-tt / 0.01))) * np.exp(-tt / decay) * g.uniform(0.2, 1.0)
        put(out, st(ping, g.uniform(-0.9, 0.9)), at)
    return out


def glass(dur, count, t0, spread, seed):
    """Shattering glass: bright inharmonic tinkles falling in a burst."""
    g = np.random.default_rng(seed)
    out = zeros(dur)
    for _ in range(count):
        at = t0 + g.exponential(spread)
        base = g.uniform(2200, 7000)
        L = int(0.35 * SR)
        tt = np.arange(L) / SR
        x = sum(np.sin(2 * np.pi * base * r * tt + g.uniform(0, 6)) * a for r, a in ((1, 1), (1.73, 0.55), (2.41, 0.4), (3.3, 0.25)))
        x *= np.exp(-tt / g.uniform(0.04, 0.14)) * g.uniform(0.2, 1)
        put(out, st(x, g.uniform(-1, 1)), at)
    return out


def boom(dur, f0, f1, sweep, decay, drive=2.2):
    t = t_axis(dur)
    f = f1 + (f0 - f1) * np.exp(-t / sweep)
    return saturate(np.sin(phase_of(f)) * np.exp(-t / decay) * np.minimum(1, t / 0.003), drive)


def crackle(n, density, lo, hi, seed):
    g = np.random.default_rng(seed)
    x = np.zeros(n)
    idx = g.random(n) < density / SR
    x[idx] = g.uniform(-1, 1, idx.sum())
    return bandpass(x, lo, hi)


def swell(dur, lo, hi, power=3.0, color="pink"):
    """A reversed-reverb style inhale: filtered noise rising to a hard stop."""
    n = int(dur * SR)
    x = bandpass(noise(n, color), lo, hi) * (np.arange(n) / n) ** power
    return x


# ================================================================== the cues
def domain_seal():
    dur = 2.35
    out = zeros(dur)
    n = len(out)
    t = t_axis(dur)
    # the hand rises: a cloth sweep, left to centre
    cloth = bandpass(noise(int(0.5 * SR), "pink"), 500, 5200) * env(int(0.5 * SR), [(0, 0), (0.12, 1), (0.5, 0)])
    put(out, st(cloth * 0.55, -0.35), 0.0)
    # the seal set: a dry, close tick and a tiny body thump
    tick = highpass(noise(int(0.012 * SR)), 2500) * np.hanning(int(0.012 * SR))
    thump = boom(0.25, 180, 70, 0.03, 0.06, 1.5) * 0.35
    put(out, st(tick * 1.2, 0.1), 0.34)
    put(out, st(thump, 0.0), 0.34)
    # the Six Eyes open: crystalline partials that climb a whole tone, shimmering side to side
    shim_d = dur - 0.4
    ts = t_axis(shim_d)
    climb = 2 ** ((2 / 12) * (ts / shim_d) ** 1.5)
    shim_L = np.zeros(len(ts)); shim_R = np.zeros(len(ts))
    for i, (f, a) in enumerate(((1760, 0.5), (2637, 0.36), (3520, 0.28), (4699, 0.2), (5274, 0.14), (7040, 0.08))):
        trem = 0.6 + 0.4 * np.sin(2 * np.pi * (5.1 + i * 0.73) * ts + i)
        v = np.sin(phase_of(f * climb) + i) * a * trem
        (shim_L if i % 2 == 0 else shim_R)[:] += v
        (shim_R if i % 2 == 0 else shim_L)[:] += v * 0.35
    shim_env = env(len(ts), [(0, 0), (0.25, 0.6), (1.2, 0.9), (shim_d - 0.2, 1.0), (shim_d, 0.0)])
    put(out, np.stack([shim_L, shim_R], axis=1) * shim_env[:, None] * 0.32, 0.38)
    # the pressure gathers: a sub drone with a slow beat, swelling
    sub = (np.sin(2 * np.pi * 43.65 * t) + 0.8 * np.sin(2 * np.pi * 44.3 * t) + 0.3 * np.sin(2 * np.pi * 87.3 * t))
    sub *= env(n, [(0, 0), (0.6, 0.25), (1.9, 0.8), (2.3, 1.0), (dur, 0.0)])
    put(out, st(saturate(sub, 1.4) * 0.55, 0.0), 0.0)
    # the world breathes in, into the white
    put(out, st(swell(0.75, 900, 9000, 3.2) * 0.9, 0.2) + st(swell(0.75, 700, 7000, 3.0) * 0.9, -0.2), dur - 0.75)
    return fin(glue(verb(out, 2.2, 0.3, 8000)[: int(dur * SR)], 1.6), -1.5, 0.004, 0.02)


def domain_white():
    dur = 1.05
    out = zeros(dur)
    n = len(out)
    t = t_axis(dur)
    # a thin pure ring in the silence, trembling, its octave a ghost; slightly apart in each ear
    ring_L = np.sin(2 * np.pi * 2217 * t + 0.6 * np.sin(2 * np.pi * 5.8 * t)) + 0.18 * np.sin(2 * np.pi * 4434 * t)
    ring_R = np.sin(2 * np.pi * 2219.5 * t + 0.6 * np.sin(2 * np.pi * 6.3 * t + 1)) + 0.18 * np.sin(2 * np.pi * 4439 * t)
    ring_env = env(n, [(0, 0), (0.05, 1), (0.8, 0.8), (1.0, 0.2)])
    out += np.stack([ring_L, ring_R], axis=1) * ring_env[:, None] * 0.25
    # a breath of choir far off ('ah'), a fifth apart left and right
    ch = formant(n, [(700, 1.0), (1220, 0.6), (2600, 0.25)], 9) * env(n, [(0, 0), (0.25, 1), (0.85, 0.7), (dur, 0)])
    ch2 = formant(n, [(1050, 1.0), (1830, 0.5), (3100, 0.2)], 9) * env(n, [(0, 0), (0.35, 0.8), (0.85, 0.6), (dur, 0)])
    out += np.stack([ch, ch2], axis=1) * 0.12
    # pressure in the ears
    press = lowpass(noise(n, "brown"), 60) * env(n, [(0, 0.2), (0.5, 1.0), (dur, 0.3)])
    out += st(press * 0.9)
    # the sharp inhale into the ink
    put(out, st(swell(0.3, 1500, 11000, 4.0) * 1.1, 0.0), dur - 0.3)
    return fin(verb(out, 3.0, 0.4, 9000)[:n], -4.0, 0.002, 0.01)


def domain_ink():
    dur = 2.4
    out = zeros(dur)
    n = len(out)
    # the eruption: a deep blow, a wet splash, a viscous glug
    put(out, st(boom(1.4, 120, 30, 0.12, 0.45, 2.6) * 1.0), 0.0)
    splash = bandpass(noise(int(0.7 * SR)), 250, 4500) * env(int(0.7 * SR), [(0, 0), (0.006, 1), (0.08, 0.45), (0.7, 0)])
    put(out, st(splash * 0.8, -0.25) + st(bandpass(noise(int(0.7 * SR)), 300, 5000) * env(int(0.7 * SR), [(0, 0), (0.008, 1), (0.09, 0.4), (0.7, 0)]) * 0.8, 0.25), 0.0)
    glug_n = int(0.6 * SR)
    glug = lowpass(noise(glug_n, "brown"), 500)
    b, a = signal.iirpeak(260, 4, SR)
    glug = signal.lfilter(b, a, glug) * env(glug_n, [(0, 0), (0.05, 1), (0.6, 0)])
    put(out, st(glug * 1.4), 0.02)
    out += droplets(dur, 70, 900, 4200, 0.03, 0.9, 5, 0.03) * 0.35
    # space manifests: neon lines zip into being, ever denser, scattered across the field
    g = np.random.default_rng(42)
    tt = 0.18
    while tt < dur - 0.1:
        k = (tt - 0.18) / (dur - 0.28)
        L = g.uniform(0.05, 0.16)
        f0 = g.uniform(700, 2600) * (1 + k)
        z = zip_line(L, f0, f0 * g.uniform(2.2, 4.5), 2.0 + 2 * k)
        pan = g.uniform(-0.95, 0.95)
        put(out, st(z * (0.10 + 0.14 * k) * g.uniform(0.6, 1.0), pan), tt)
        put(out, st(z * 0.05, -pan), tt + 0.06)                       # a faint echo in the other ear
        tt += 1 / (5 + 42 * k ** 1.4) * g.uniform(0.5, 1.5)
    # electricity under the lines
    cr = crackle(n, 900, 1800, 9000, 9) * env(n, [(0, 0), (0.4, 0.2), (dur - 0.2, 1.0), (dur, 0.6)])
    out += np.stack([cr, np.roll(cr, 331)], axis=1) * 0.18
    # a chord swelling out of the dark (the void's), its filter opening
    t = t_axis(dur)
    chord = sum(a * np.sin(2 * np.pi * f * t + i) for i, (f, a) in enumerate(((55, 1.0), (82.41, 0.7), (110, 0.6), (164.8, 0.4), (246.9, 0.22), (329.6, 0.16))))
    chord = lowpass(chord, 900) * env(n, [(0, 0), (0.5, 0.1), (2.0, 0.8), (dur, 1.0)])
    out += np.stack([chord, np.roll(chord, 97)], axis=1) * 0.3
    # handing into the tunnel: a short riser
    put(out, st(shepard(0.6, 0.8, 2.0, 60, 6, 3.0, 1.3) * env(int(0.6 * SR), [(0, 0), (0.6, 1)]) * 0.18), dur - 0.6)
    return fin(verb(out, 2.4, 0.32, 8500)[:n], -1.0, 0.002, 0.06)


def domain_tunnel():
    dur = 2.8
    out = zeros(dur)
    n = len(out)
    t = t_axis(dur)
    # the endless rise, accelerating
    sh = shepard(dur, 0.5, 3.2, 40, 8, 4.2, 1.5)
    sh2 = shepard(dur, 0.5, 3.2, 40 * 2 ** 0.5, 8, 4.4, 1.5)     # a tritone apart in the other ear
    rise = env(n, [(0, 0), (0.3, 0.5), (dur - 0.05, 1.0), (dur, 1.0)])
    out += np.stack([sh * 0.8 + sh2 * 0.3, sh2 * 0.8 + sh * 0.3], axis=1) * rise[:, None] * 0.16
    # rushing air: a band sweeping up, fluttering faster (doppler of everything flying past)
    air = noise(n, "pink")
    centres = 300 * (6500 / 300) ** ((t / dur) ** 1.3)
    rushed = np.zeros(n)
    step = 1024
    for i in range(0, n, step):
        c = centres[min(i + step // 2, n - 1)]
        lo, hi = max(40, c / 1.8), min(SR / 2 - 100, c * 1.8)
        seg = bandpass(air[max(0, i - 512): i + step], lo, hi)
        rushed[i:i + step] = seg[-len(rushed[i:i + step]):]
    flutter = 0.7 + 0.3 * np.sin(2 * np.pi * np.cumsum(4 + 30 * (t / dur) ** 2) / SR)
    rushed *= flutter * env(n, [(0, 0.3), (0.2, 0.7), (dur, 1.0)])
    out += np.stack([rushed, np.roll(rushed, 211)], axis=1) * 0.55
    # the flood of information: chattering data, bit-crushed, flicking between the ears
    g = np.random.default_rng(99)
    tt = 0.02
    while tt < dur - 0.02:
        k = tt / dur
        L = g.uniform(0.006, 0.03)
        f = 220 * g.choice([2, 3, 4, 5, 6, 8, 10, 12, 16]) * (1 + k)
        blip = np.sign(np.sin(2 * np.pi * f * t_axis(L))) * np.hanning(int(L * SR) or 1)
        blip = np.round(blip * 6) / 6
        put(out, st(blip * g.uniform(0.04, 0.1) * (0.6 + 0.8 * k), g.uniform(-1, 1)), tt)
        tt += 1 / (25 + 140 * k ** 1.5) * g.uniform(0.4, 1.6)
    # a heartbeat quickening
    beat_t, rate = 0.1, 1.6
    while beat_t < dur - 0.08:
        hb = boom(0.22, 90, 45, 0.02, 0.07, 1.8)
        put(out, st(hb * 0.55), beat_t)
        put(out, st(hb * 0.3), beat_t + 0.16 / rate * 1.6)
        rate += 0.9
        beat_t += 1 / rate
    # the last moment: everything swells, then silence (the flash is the next sound)
    out *= env(n, [(0, 1), (dur - 0.25, 1.0), (dur - 0.02, 1.35), (dur, 0)])[:, None]
    return fin(verb(out, 1.4, 0.2, 9000)[:n], -1.0, 0.03, 0.012)


def domain_open():
    dur = 6.5
    out = zeros(dur)
    n = len(out)
    t = t_axis(dur)
    # the vast bell: inharmonic bell modes on a low strike, each a pair beating slowly, spread wide
    f0 = 98.0
    modes = ((0.5, 0.55, 5.5), (1.0, 1.0, 4.8), (1.19, 0.5, 3.2), (1.5, 0.42, 3.6), (2.0, 0.6, 3.0), (2.52, 0.32, 2.2),
             (3.0, 0.28, 2.0), (4.07, 0.2, 1.4), (5.2, 0.14, 1.0), (6.8, 0.08, 0.7))
    bl = partials(dur, [(f0 * r, a, d) for r, a, d in modes], detune=0.35, seed=3)
    br = partials(dur, [(f0 * r * 1.0015, a, d) for r, a, d in modes], detune=0.35, seed=4)
    strike = highpass(noise(int(0.03 * SR)), 1200) * np.hanning(int(0.03 * SR))
    out += np.stack([bl, br], axis=1) * 0.33
    put(out, st(strike * 0.8), 0)
    # crystal overtones, ringing out on both sides
    cr = partials(dur, [(1318.5, 0.5, 2.2), (1975.5, 0.35, 1.8), (2637, 0.28, 1.5), (3951, 0.18, 1.1), (5274, 0.1, 0.8)], 0.8, 7)
    cr2 = partials(dur, [(1661.2, 0.4, 2.0), (2489, 0.3, 1.6), (3322, 0.2, 1.2), (4978, 0.12, 0.9)], 0.8, 8)
    out += np.stack([cr, cr2], axis=1) * 0.16
    # the floor drops away
    put(out, st(boom(3.5, 72, 21, 0.8, 1.6, 2.0) * 0.9), 0)
    # the void's chord rising under the tail
    chord = sum(a * np.sin(2 * np.pi * f * t + i) for i, (f, a) in enumerate(((55, 1.0), (82.41, 0.7), (110, 0.55), (123.5, 0.3), (164.8, 0.35))))
    chord = lowpass(chord, 700) * env(n, [(0, 0), (1.2, 0.1), (4.0, 0.6), (dur, 0.7)])
    out += np.stack([chord, np.roll(chord, 173)], axis=1) * 0.3
    # a shimmer of air in the huge space
    air = highpass(noise(n, "pink"), 3000) * env(n, [(0, 0), (0.1, 0.6), (2.5, 0.25), (dur, 0.05)])
    out += np.stack([air, np.roll(air, 4001)], axis=1) * 0.06
    return fin(verb(out, 5.0, 0.45, 7000)[:n], -0.8, 0.0015, 0.8)


def domain_void():
    L = 8.0
    n = int(L * SR)
    t = t_axis(L)
    out = np.zeros((n, 2))
    # every component repeats a whole number of times in 8 s (the loop closes)
    q = lambda f: round(f * L) / L
    breath = 0.72 + 0.28 * np.sin(2 * np.pi * t / L)
    chord = [(55, 1.0), (55.25, 0.7), (82.5, 0.55), (110, 0.45), (123.75, 0.25), (165, 0.3), (220.125, 0.12), (247.5, 0.08)]
    dl = sum(a * np.sin(2 * np.pi * q(f) * t + i) for i, (f, a) in enumerate(chord))
    dr = sum(a * np.sin(2 * np.pi * q(f * 1.0005) * t + i + 0.8) for i, (f, a) in enumerate(chord))
    # a slow filter breathing on the drone: blend a dark and a bright voicing
    dark = 0.5 + 0.5 * np.sin(2 * np.pi * 2 * t / L)
    out += np.stack([dl, dr], axis=1) * breath[:, None] * 0.3
    bright = sum(a * np.sin(2 * np.pi * q(f) * t) for f, a in ((440, 0.12), (495, 0.08), (660, 0.06), (990, 0.035)))
    out += np.stack([bright, np.roll(bright, 211)], axis=1) * (1 - dark)[:, None] * 0.35
    # cosmic wind, circular (FFT-shaped), drifting across the stereo field
    spec = np.fft.rfft(R.standard_normal(n))
    fr = np.fft.rfftfreq(n, 1 / SR)
    shape = 1 / np.maximum(fr, 20) ** 0.9 * np.exp(-((np.log2(np.maximum(fr, 1)) - np.log2(600)) / 1.6) ** 2)
    wind = np.fft.irfft(spec * shape, n)
    wind /= np.max(np.abs(wind)) + 1e-9
    pan = np.sin(2 * np.pi * t / L + 0.4)
    out += np.stack([wind * (0.6 - 0.4 * pan), wind * (0.6 + 0.4 * pan)], axis=1) * 0.28
    # distant stars ringing out (their tails fold back over the loop's start)
    long = np.zeros((2 * n, 2))
    g = np.random.default_rng(12)
    notes = [1760, 1975.5, 2217.5, 2637, 2960, 3520]
    for at in (0.6, 1.9, 2.7, 4.1, 5.3, 6.6, 7.4):
        f = g.choice(notes)
        d = 2.8
        s = partials(d, [(f, 1.0, 1.1), (f * 2.01, 0.25, 0.6), (f * 3.02, 0.1, 0.35)], 0.4, int(at * 10))
        put(long, st(s * 0.07, g.uniform(-0.9, 0.9)), at)
    # something vast and far away sings, once
    wn = int(2.2 * SR)
    wt = np.arange(wn) / SR
    wf = 175 - 40 * (wt / 2.2) ** 1.5 + 4 * np.sin(2 * np.pi * 4.5 * wt)
    whale = np.sin(phase_of(wf)) + 0.5 * np.sin(phase_of(wf * 2)) + 0.25 * np.sin(phase_of(wf * 3))
    b, a = signal.iirpeak(700, 3, SR)
    whale = signal.lfilter(b, a, whale) * env(wn, [(0, 0), (0.4, 1), (1.6, 0.8), (2.2, 0)])
    put(long, st(whale * 0.09, -0.4), 3.2)
    out += long[:n] + long[n:]
    out = cverb(out, 4.5, 0.45, 5500)
    p = np.max(np.abs(out)) + 1e-9
    return out / p * 10 ** (-9 / 20)


def domain_collapse():
    dur = 2.4
    out = zeros(dur)
    n = len(out)
    # the void is inhaled...
    put(out, st(swell(0.36, 400, 9000, 2.6) * 1.0, -0.3) + st(swell(0.36, 500, 8000, 2.8) * 1.0, 0.3), 0.0)
    rev = partials(0.36, [(98 * r, a, 0.4) for r, a in ((1, 1), (1.5, 0.5), (2, 0.5), (3, 0.3))], 0.3, 21)[::-1]
    put(out, st(rev * 0.35), 0.0)
    # ...cracks, and shatters like glass as the white ink wipes it away
    crack = highpass(noise(int(0.02 * SR)), 1500) * np.hanning(int(0.02 * SR))
    put(out, st(crack * 1.3, 0.1), 0.36)
    out += glass(dur, 120, 0.36, 0.12, 31) * 0.3
    put(out, st(boom(0.9, 140, 40, 0.05, 0.25, 2.0) * 0.8), 0.36)
    splash = bandpass(noise(int(0.5 * SR)), 600, 6000) * env(int(0.5 * SR), [(0, 0), (0.005, 1), (0.1, 0.35), (0.5, 0)])
    put(out, st(splash * 0.6, -0.2) + st(np.roll(splash, 150) * 0.5, 0.25), 0.37)
    # the world rushes back
    back = bandpass(noise(int(1.6 * SR), "pink"), 150, 3000) * env(int(1.6 * SR), [(0, 0), (0.25, 1), (1.6, 0)])
    put(out, np.stack([back, np.roll(back, 503)], axis=1) * 0.45, 0.6)
    return fin(glue(verb(out, 2.6, 0.35, 8000)[:n], 2.2), -1.0, 0.003, 0.3)


def domain_instant():
    """The 0.2-second domain, at its real beats: I_WHITE 0.40 s, I_INK 0.55, I_VOID 0.75, I_WIPE 0.95, I_END 1.55."""
    dur = 1.9
    out = zeros(dur)
    # the snap of the seal
    snap = highpass(noise(int(0.012 * SR)), 2600) * np.hanning(int(0.012 * SR))
    put(out, st(snap * 1.2, 0.15), 0.0)
    put(out, st(boom(0.2, 200, 80, 0.02, 0.05, 1.5) * 0.3), 0.0)
    # the white: a flash of the ring
    w = domain_white()[: int(0.16 * SR)] * np.linspace(1, 0, int(0.16 * SR))[:, None]
    put(out, w * 0.8, 0.40)
    # the ink bursts, the lines flicker
    ink = domain_ink()[: int(0.5 * SR)] * np.linspace(1, 0.2, int(0.5 * SR))[:, None]
    put(out, ink * 0.9, 0.55)
    # the void for an instant: the bell, cut short
    bell = domain_open()[: int(0.5 * SR)] * np.linspace(1, 0, int(0.5 * SR))[:, None] ** 0.5
    put(out, bell * 0.8, 0.75)
    # the white ink wipe, then quiet
    put(out, domain_collapse()[int(0.3 * SR): int(1.0 * SR)] * 0.7, 0.9)
    return fin(glue(verb(out, 1.8, 0.25, 8000)[: len(out)], 2.6), -1.0, 0.002, 0.25)


CUES = (domain_seal, domain_white, domain_ink, domain_tunnel, domain_open, domain_void, domain_collapse, domain_instant)

if __name__ == "__main__":
    want = set(sys.argv[1:])
    for fn in CUES:
        if want and fn.__name__ not in want:
            continue
        write_st(fn(), fn.__name__)
