"""Small procedural sound-design toolkit (numpy/scipy). Every SFX in the mod is generated from code here,
so it can be re-tuned and rebuilt. Output: mono 48 kHz OGG Vorbis (Minecraft only positions mono sounds)."""
import numpy as np, subprocess, os
from scipy import signal

SR = 48000
rng = np.random.default_rng(7)


def t_axis(dur):
    return np.arange(int(dur * SR)) / SR


def env_adsr(n, a=0.01, d=0.1, s=0.7, r=0.2, curve=2.0):
    """Attack/decay/sustain/release envelope over n samples (times in seconds)."""
    A, D, R = int(a * SR), int(d * SR), int(r * SR)
    S = max(0, n - A - D - R)
    e = np.concatenate([
        np.linspace(0, 1, max(A, 1)) ** (1 / curve),
        1 - (1 - s) * np.linspace(0, 1, max(D, 1)) ** (1 / curve),
        np.full(S, s),
        s * (1 - np.linspace(0, 1, max(R, 1))) ** curve,
    ])
    return np.pad(e, (0, max(0, n - len(e))))[:n]


def exp_env(n, tau):
    return np.exp(-np.arange(n) / (tau * SR))


def noise(n, color="white"):
    w = rng.standard_normal(n)
    if color == "white":
        return w
    if color == "pink":
        b, a = [0.049922035, -0.095993537, 0.050612699, -0.004408786], [1, -2.494956002, 2.017265875, -0.522189400]
        return signal.lfilter(b, a, w) * 3.5
    if color == "brown":
        x = np.cumsum(w)
        x = signal.lfilter([1, -1], [1, -0.995], x)
        return x / (np.std(x) + 1e-9)
    raise ValueError(color)


def sweep_tone(f0, f1, dur, curve="exp", phase=0.0):
    n = int(dur * SR)
    k = np.linspace(0, 1, n)
    f = f0 * (f1 / f0) ** k if curve == "exp" else f0 + (f1 - f0) * k
    return np.sin(2 * np.pi * np.cumsum(f) / SR + phase)


def tone(freq, dur, phase=0.0):
    return np.sin(2 * np.pi * freq * t_axis(dur) + phase)


def bandpass_sweep(x, f_lo, f_hi, q=2.0, steps=64):
    """Time-varying band-pass: frequency centre goes f_lo -> f_hi (arrays allowed) over the signal."""
    n = len(x)
    out = np.zeros(n)
    centres = np.geomspace(f_lo, f_hi, steps) if np.isscalar(f_lo) else None
    seg = n // steps + 1
    win = np.hanning(2 * seg)
    for i in range(steps):
        c = centres[i]
        bw = c / q
        lo, hi = max(20, c - bw / 2), min(SR / 2 - 100, c + bw / 2)
        sos = signal.butter(2, [lo, hi], btype="band", fs=SR, output="sos")
        s0 = max(0, i * seg - seg // 2)
        s1 = min(n, s0 + 2 * seg)
        y = signal.sosfilt(sos, x[s0:s1])
        out[s0:s1] += y * win[: s1 - s0]
    return out


def lowpass(x, fc, order=4):
    return signal.sosfilt(signal.butter(order, fc, btype="low", fs=SR, output="sos"), x)


def highpass(x, fc, order=4):
    return signal.sosfilt(signal.butter(order, fc, btype="high", fs=SR, output="sos"), x)


def bandpass(x, lo, hi, order=3):
    return signal.sosfilt(signal.butter(order, [lo, hi], btype="band", fs=SR, output="sos"), x)


def saturate(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


def reverb(x, seconds=2.0, mix=0.3, predelay=0.02, damp=6000):
    """Convolution with a synthetic hall impulse (decaying, darkening noise)."""
    n = int(seconds * SR)
    ir = rng.standard_normal(n) * np.exp(-np.arange(n) / (seconds * SR / 6.9))
    ir = lowpass(ir, damp, 2)
    ir = np.concatenate([np.zeros(int(predelay * SR)), ir])
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    wet = signal.fftconvolve(x, ir)[: len(x) + n]
    dry = np.pad(x, (0, len(wet) - len(x)))
    return dry * (1 - mix) + wet * mix


def fit(x, n):
    return x[:n] if len(x) >= n else np.pad(x, (0, n - len(x)))


def mix(n, *parts):
    """parts: (signal, offset_seconds, gain)"""
    out = np.zeros(n)
    for sig, off, g in parts:
        o = int(off * SR)
        if o >= n:
            continue
        seg = sig[: n - o]
        out[o:o + len(seg)] += seg * g
    return out


def normalize(x, peak_db=-1.0):
    p = np.max(np.abs(x)) + 1e-12
    return x / p * (10 ** (peak_db / 20))


def fade(x, fin=0.005, fout=0.05):
    a, b = int(fin * SR), int(fout * SR)
    y = x.copy()
    if a: y[:a] *= np.linspace(0, 1, a)
    if b: y[-b:] *= np.linspace(1, 0, b)
    return y


def make_loop(x, xfade=0.35):
    """Seamless loop: crossfade the tail into the head."""
    k = int(xfade * SR)
    head, body, tail = x[:k], x[k:-k], x[-k:]
    w = np.sin(np.linspace(0, np.pi / 2, k)) ** 2
    joined = tail * (1 - w) + head * w
    return np.concatenate([joined, body])


def write_ogg(x, path, quality=7):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    wav = path[:-4] + ".tmp.wav"
    from scipy.io import wavfile
    wavfile.write(wav, SR, (np.clip(x, -1, 1) * 32767).astype(np.int16))
    subprocess.run(["ffmpeg", "-y", "-v", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", str(quality), path], check=True)
    os.remove(wav)
    print("[sfx] wrote", path, f"{len(x) / SR:.2f}s")
