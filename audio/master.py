"""Mastering pass: sets every sound to its place in the mix and keeps it from clipping.

Minecraft never plays a sound louder than its file: the "volume" a mod passes above 1.0 only widens how far away it
can be heard. So the loudness inside each .ogg is the mix. This script measures each sound's loudest moment
(momentary loudness, 400 ms, EBU R128 K-weighting), applies the gain that puts it on its target from the table below,
and runs a look-ahead limiter so true peaks stay under -1 dBTP. Loops get the same treatment with the signal wrapped
around, so they stay seamless.

Reference, measured from vanilla 1.21.1: TNT explosion -10 LUFS, wither spawn -5.6, thunder -12.6, level-up -13.5.

usage: .tools/venv/Scripts/python audio/master.py [--dry] [name ...]     (no names = every sound in the table)
"""
import sys, os, glob
import numpy as np, soundfile as sf, pyloudnorm as pyln
from scipy.signal import resample_poly
from scipy.ndimage import minimum_filter1d, uniform_filter1d

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SOUNDS = os.path.join(ROOT, "mod", "src", "main", "resources", "assets", "gojolimitless", "sounds")

# target loudest moment (LUFS, momentary) and whether the sound loops
MIX = {
    # the payoffs: louder than a TNT explosion
    "nuke_explosion": -7.0, "nuke_collide": -8.0, "purple_impact": -8.0, "red_detonate_big": -8.0,
    "domain_collapse": -8.5, "domain_instant": -8.5, "nuke_bloom": -9.0, "nuke_shockwave": -9.0, "blue_collapse": -9.5,
    # hits and launches
    "red_detonate": -9.5, "purple_collide": -10.0, "purple_launch": -10.0, "nuke_converge": -10.0, "domain_open": -10.0,
    "red_fire": -11.0, "blue_throw": -11.0, "nuke_boost": -11.0, "domain_ink": -11.0, "domain_tunnel": -11.5,
    "nuke_throw": -12.0,
    # forming, chants, cues
    "blue_form": -12.0, "purple_form": -12.0, "red_form": -13.0, "blue_charge": -13.0, "blue_incant": -12.0,
    "red_incant": -12.0, "purple_incant": -12.0, "nuke_glint": -12.0, "domain_seal": -13.0, "domain_white": -14.0,
    "infinity_toggle": -15.0, "nuke_aftermath": -15.0,
    # details and loops
    "nuke_sky_hum": -15.0, "red_fly": -16.0, "purple_rush": -16.0, "blue_tear": -16.0, "blue_max_loop": -17.0,
    "purple_hum": -17.0, "purple_charge": -17.0, "red_charge": -17.0, "infinity_stop": -17.0, "blue_loop": -18.0,
    "domain_void": -19.0,
}
LOOPS = {"blue_loop", "blue_max_loop", "purple_hum", "purple_charge", "red_charge", "nuke_sky_hum", "domain_void"}

CEILING_DB = -1.0       # true peak
LOOKAHEAD_MS = 3.0
RELEASE_MS = 80.0
VORBIS_LEVEL = 0.3      # libsndfile compression level 0.3 = Vorbis quality ~7, as the build scripts use


def loudest_moment(x, sr):
    meter = pyln.Meter(sr, block_size=0.4)
    n, hop, best = int(0.4 * sr), int(0.1 * sr), -120.0
    for s in range(0, max(1, len(x) - n + 1), hop):
        try:
            v = meter.integrated_loudness(x[s:s + n])
        except Exception:
            continue
        if np.isfinite(v):
            best = max(best, v)
    return best


def true_peak(x):
    return np.abs(resample_poly(x, 4, 1, axis=0)).max()


def limit(x, sr, loop):
    """Look-ahead peak limiter on the linked channels. Returns (limited, deepest gain reduction in dB)."""
    pad = int(0.5 * sr) if loop else 0
    y = np.concatenate([x[-pad:], x, x[:pad]]) if pad else x
    n = len(y)
    up = np.abs(resample_poly(y, 4, 1, axis=0)).max(axis=1)
    up = np.pad(up, (0, max(0, 4 * n - len(up))))[:4 * n]
    peak = up.reshape(n, 4).max(axis=1)
    c = 10 ** (CEILING_DB / 20)
    need = np.minimum(1.0, c / np.maximum(peak, 1e-9))
    la = max(1, int(LOOKAHEAD_MS * sr / 1000))
    g = minimum_filter1d(need, 2 * la + 1)
    a = np.exp(-1.0 / (RELEASE_MS * sr / 1000))
    h = np.empty_like(g); cur = 1.0
    for i in range(n):                                  # instant attack, smooth release
        gi = g[i]
        cur = gi if gi < cur else gi + (cur - gi) * a
        h[i] = cur
    h = uniform_filter1d(h, la + 1)                     # rounds the attack edge; stays under `need`
    y = y * h[:, None]
    if pad:
        y = y[pad:-pad]; h = h[pad:-pad]
    return y, -20 * np.log10(h.min())


def master(name, dry=False):
    path = os.path.join(SOUNDS, name + ".ogg")
    x, sr = sf.read(path, always_2d=True)
    target, loop = MIX[name], name in LOOPS
    before = loudest_moment(x, sr)
    gain_db = target - before
    y, gr = x, 0.0
    for _ in range(5):                                  # limiting lowers the loudness a little: converge on it
        y, gr = limit(x * 10 ** (gain_db / 20), sr, loop)
        got = loudest_moment(y, sr)
        if abs(target - got) < 0.15:
            break
        gain_db += target - got
    after = loudest_moment(y, sr)
    print(f"{name:18s} {before:6.1f} -> {after:6.1f} LUFS  (gain {gain_db:+5.1f} dB, limiter {gr:4.1f} dB, "
          f"true peak {20 * np.log10(true_peak(y)):5.1f} dBTP){'  loop' if loop else ''}")
    if not dry:
        sf.write(path, y.astype(np.float32), sr, format="OGG", subtype="VORBIS", compression_level=VORBIS_LEVEL)


if __name__ == "__main__":
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    dry = "--dry" in sys.argv
    names = args or sorted(MIX)
    missing = sorted(set(os.path.basename(p)[:-4] for p in glob.glob(os.path.join(SOUNDS, "*.ogg"))) - set(MIX))
    if missing:
        print("not in the mix table (left alone):", ", ".join(missing))
    for n in names:
        master(n, dry)
