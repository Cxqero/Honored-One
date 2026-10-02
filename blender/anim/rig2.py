"""Casting animations for the Minecraft player model, v2: bends, pivot offsets, whole-body moves and hand signs.

Authoring is key-to-key tweening with per-segment easing (like an After Effects timeline), which gives exact
control over anticipation, snaps, overshoot and holds. Angles are written in degrees in Minecraft model convention
(pitch, yaw, roll; for an arm, pitch -90 = pointing forward, -180 = straight up; roll + swings the right arm out
to the side... see blender/lib/mcchar.py). Bends: (axis, angle) degrees — for arms axis 0 and a negative angle bends
the forearm forward (elbow), for legs a positive angle bends the knee.

Parts: head, body (torso), right_arm, left_arm, right_leg, left_leg, root (whole body: rot degrees about x/y/z,
pos pixels with x = the character's left, y up, z forward).

Export: mod/src/main/resources/assets/gojolimitless/animations/player/<name>.json (format v2, baked at FPS).
Preview: tools/blender -b -P blender/anim/moves.py -- --preview <name,...> --skin <png> --out <dir>
"""
import math, json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "mod", "src", "main", "resources", "assets", "gojolimitless", "animations", "player")
FPS = 60
PARTS = ["head", "body", "right_arm", "left_arm", "right_leg", "left_leg", "root"]


# ------------------------------------------------------------------ easing
def ease(kind, x):
    x = max(0.0, min(1.0, x))
    if kind == "linear":
        return x
    if kind == "in":                     # slow start (wind-up)
        return x * x * x
    if kind == "out":                    # fast start, soft landing (a snap into a pose)
        return 1 - (1 - x) ** 3
    if kind == "snap":                   # very fast start
        return 1 - (1 - x) ** 5
    if kind == "back":                   # overshoot then settle
        c1, c3 = 1.70158, 2.70158
        return 1 + c3 * (x - 1) ** 3 + c1 * (x - 1) ** 2
    if kind == "hold":
        return 0.0 if x < 1 else 1.0
    # "smooth": ease in-out
    return x * x * (3 - 2 * x)


# ------------------------------------------------------------------ animation
class Anim:
    def __init__(self, name, length, aim=(), first_person=("right_arm",), fps=FPS):
        self.name, self.length, self.fps = name, length, fps
        self.aim = list(aim)
        self.first_person = list(first_person)
        self.keys = {p: {"rot": [], "pos": [], "bend": []} for p in PARTS}
        self.wkeys = []
        self.hand_keys = {"right": [], "left": []}
        self.notes = []

    # key(frame, ease, part=dict(rot=(p,y,r), pos=(x,y,z), bend=(axis, angle)), ...)
    def key(self, frame, e="smooth", **parts):
        for pn, d in parts.items():
            pn = pn.replace("_fp", "@fp") if pn.endswith("_fp") else pn
            if pn not in self.keys:
                self.keys[pn] = {"rot": [], "pos": [], "bend": []}
            for ch in ("rot", "pos", "bend"):
                if ch in d:
                    self.keys[pn][ch].append((frame, tuple(d[ch]), e))
        return self

    def pose(self, frame, pose, e="smooth"):
        """Key a whole pose dict {part: {rot/pos/bend}}."""
        return self.key(frame, e, **pose)

    def delay(self, part, frames, channels=("rot", "pos", "bend")):
        """Overlapping action: this part arrives `frames` later than the keys say (the first key stays put)."""
        for ch in channels:
            ks = self.keys[part][ch]
            self.keys[part][ch] = [ks[0]] + [(min(self.length, f + frames), v, e) for f, v, e in ks[1:]] if ks else ks
        return self

    def weight(self, frame, w, e="smooth"):
        self.wkeys.append((frame, w, e))
        return self

    def hand(self, frame, side, shape, w=1.0):
        self.hand_keys[side].append((frame, shape, w))
        return self

    def _channel(self, keys, f):
        if not keys:
            return None
        ks = sorted(keys, key=lambda k: k[0])
        if f <= ks[0][0]:
            return ks[0][1]
        for a, b in zip(ks, ks[1:]):
            if f <= b[0]:
                x = (f - a[0]) / max(1e-6, b[0] - a[0])
                k = ease(b[2], x)
                return tuple(av + (bv - av) * k for av, bv in zip(a[1], b[1]))
        return ks[-1][1]

    def sample(self, f, fp=False):
        """Pose at frame f in the mcchar / game units (radians, pixels). fp: use first-person override tracks."""
        out = {}
        for pn in PARTS:
            d = {}
            src = pn + "@fp" if fp and pn + "@fp" in self.keys else pn
            r = self._channel(self.keys[src]["rot"], f)
            if r is not None:
                d["rot"] = tuple(math.radians(v) for v in r)
            p = self._channel(self.keys[src]["pos"], f)
            if p is not None:
                d["pos"] = p
            b = self._channel(self.keys[src]["bend"], f)
            if b is not None:
                d["bend"] = (math.radians(b[0]), math.radians(b[1]))
            if d:
                out[pn] = d
        out["hands"] = {}
        for side in ("right", "left"):
            ks = sorted(self.hand_keys[side], key=lambda k: k[0])
            if not ks:
                continue
            if f <= ks[0][0]:
                out["hands"][side] = (ks[0][1], ks[0][1], 0.0, ks[0][2])
                continue
            res = (ks[-1][1], ks[-1][1], 0.0, ks[-1][2])
            for a, b in zip(ks, ks[1:]):
                if f < b[0]:
                    k = (f - a[0]) / max(1e-3, b[0] - a[0])
                    k = k * k * (3 - 2 * k)
                    res = (a[1], b[1], k, a[2] + (b[2] - a[2]) * k)
                    break
            out["hands"][side] = res
        return out

    def weight_at(self, f):
        if not self.wkeys:
            return 1.0
        return self._channel([(a, (w,), e) for a, w, e in self.wkeys], f)[0]

    # ---------------- export
    def export(self, out_dir=OUT):
        n = self.length + 1
        tracks = {}
        for pn in self.keys:
            t = {}
            for ch in ("rot", "pos", "bend"):
                if not self.keys[pn][ch]:
                    continue
                vals = []
                for f in range(n):
                    v = self._channel(self.keys[pn][ch], f)
                    if ch == "rot":
                        v = tuple(math.radians(x) for x in v)
                    elif ch == "bend":
                        v = (math.radians(v[0]), math.radians(v[1]))
                    vals.append([round(x, 5) for x in v])
                t[ch] = vals
            if t:
                tracks[pn] = t
        data = {
            "version": 2, "fps": self.fps, "frames": n,
            "weight": [round(self.weight_at(f), 4) for f in range(n)],
            "aim": self.aim, "first_person": self.first_person,
            "tracks": tracks,
            "hands": {s: [[k[0], k[1], k[2]] for k in sorted(v, key=lambda k: k[0])] for s, v in self.hand_keys.items() if v},
        }
        os.makedirs(out_dir, exist_ok=True)
        path = os.path.join(out_dir, self.name + ".json")
        with open(path, "w") as f:
            json.dump(data, f, separators=(",", ":"))
        return path


def R(p=0.0, y=0.0, r=0.0):
    return (p, y, r)
