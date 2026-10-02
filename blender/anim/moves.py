"""Gojo's casting animations for the Minecraft player model (format v2: bends, pivots, whole body, hand signs).

Body language (from the anime and manga): relaxed and economical — weight on one leg, the off hand in a pocket,
small precise gestures; the power is in the technique, not in wind-ups. Signs (canon): Blue and Red with two fingers
raised or pointed; the merge of Blue and Red with index and little finger extended; Hollow Purple fired with a
pinch (index to thumb) and the middle finger extended. Timings match the gameplay entities (ticks × 3 = frames).

    python3 blender/anim/moves.py                                   export everything
    tools/blender -b -P blender/anim/moves.py -- --preview blue_tap,red_charge --skin <png> --out <dir>
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig2
from rig2 import Anim, R

T = 3   # frames per game tick (60 fps)


# ------------------------------------------------------------------ building blocks
def stance(lean=0.0):
    """Gojo at ease: weight on the left leg, left hand in the pocket, right arm loose."""
    return dict(
        left_arm=dict(rot=R(10, 0, -7), bend=(0, -38)),
        right_arm=dict(rot=R(2, 0, 4), bend=(0, -6)),
        right_leg=dict(rot=R(-4, 0, 4), bend=(0, 6)),
        left_leg=dict(rot=R(3, 0, -2), bend=(0, 0)),
        body=dict(rot=R(0, 0, 0)),
        root=dict(rot=R(lean, 0, 0), pos=(0, 0, 0)),
    )


def turn(deg):
    """Twist the torso (− brings the right shoulder forward) and move the shoulders with it."""
    s = math.sin(math.radians(deg)) * 5.0
    return dict(body=dict(rot=R(0, deg, 0)), right_arm_pos=(0, 0, s), left_arm_pos=(0, 0, -s))


def plant(depth=1.0, lean=0.0, fwd=0.0, side=0.0, split=0.0):
    """Weight into the legs: knees flex, hips drop. split > 0 puts the right foot forward (a stride)."""
    k = 16.0 * depth
    return dict(
        right_leg=dict(rot=R(-6 * depth - 14 * split, 0, 6 + 2 * depth), bend=(0, k + 10 * split)),
        left_leg=dict(rot=R(5 * depth + 12 * split, 0, -5 - 2 * depth), bend=(0, k * 0.85 + 4 * split)),
        root=dict(rot=R(lean, 0, 0), pos=(side, -1.0 * depth - 0.5 * split, fwd)),
    )


def merge(*dicts):
    out = {}
    for d in dicts:
        for k, v in d.items():
            if k.endswith("_pos"):
                part = k[:-4]
                out.setdefault(part, {})["pos"] = v
            else:
                out.setdefault(k, {}).update(v)
    return out


def key(a, frame, pose, e="smooth"):
    a.pose(frame, pose, e)


def fade(a, fin, hold_to, fout, end):
    a.weight(0, 0.0).weight(fin, 1.0, "out")
    if hold_to is not None:
        a.weight(hold_to, 1.0).weight(end, 0.0, "smooth")


ANIMS = {}


def anim(fn):
    a = fn()
    ANIMS[a.name] = a
    return fn


# ================================================================== Lapse: Blue
@anim
def blue_tap():
    """A casual two-finger point at the target — the weight rolls onto the front foot as the arm snaps up, the head
    and the off shoulder follow a beat later. 0.75 s."""
    a = Anim("blue_tap", 45, aim=("right_arm",), first_person=("right_arm",))
    fade(a, 5, 30, None, 45)
    key(a, 0, merge(stance(), turn(0)))
    key(a, 5, merge(stance(), turn(4), plant(0.3, lean=1), dict(right_arm=dict(rot=R(-20, 0, 6), bend=(0, -40)))), "in")   # gather
    key(a, 10, merge(stance(), turn(-14), plant(0.7, lean=-3, fwd=0.8, side=-0.4),
                     dict(right_arm=dict(rot=R(-98, 6, 0), bend=(0, -12)))), "snap")
    key(a, 16, merge(stance(), turn(-11), plant(0.5, lean=-1.5, fwd=0.6, side=-0.3),
                     dict(right_arm=dict(rot=R(-91, 4, 0), bend=(0, -10)))), "back")
    key(a, 30, merge(stance(), turn(-10), plant(0.45, lean=-1, fwd=0.5, side=-0.3),
                     dict(right_arm=dict(rot=R(-90, 3, 0), bend=(0, -10)))))
    key(a, 45, merge(stance(), turn(0), plant(0.0), dict(right_arm=dict(rot=R(-30, 0, 0), bend=(0, -20)))))
    a.key(0, head=dict(rot=R(0, 0, 0))).key(12, head=dict(rot=R(3, -6, 0)), e="back").key(45, head=dict(rot=R(0, 0, 0)))
    a.delay("head", 3).delay("left_arm", 2)
    a.hand(0, "right", "relaxed", 0.0).hand(3, "right", "relaxed", 1.0).hand(9, "right", "two_up", 1.0)
    a.hand(32, "right", "two_up", 1.0).hand(42, "right", "relaxed", 0.0)
    return a


@anim
def blue_max():
    """Maximum Output: Blue — stance widened, two fingers on the orb; the arm tracks it procedurally."""
    a = Anim("blue_max", 24, aim=(), first_person=("right_arm",))
    fade(a, 10, None, None, 24)
    base = merge(stance(), dict(right_leg=dict(rot=R(-6, 0, 9), bend=(0, 10)), left_leg=dict(rot=R(6, 0, -8), bend=(0, 8))))
    key(a, 0, merge(stance(), turn(0)))
    key(a, 14, merge(base, turn(-8), dict(right_arm=dict(rot=R(-100, 0, 0), bend=(0, -12)), root=dict(rot=R(-2, 0, 0), pos=(0, -0.6, 0)))), "out")
    key(a, 24, merge(base, turn(-8), dict(right_arm=dict(rot=R(-100, 0, 0), bend=(0, -12)), root=dict(rot=R(-2, 0, 0), pos=(0, -0.6, 0)))))
    a.hand(0, "right", "relaxed", 0.0).hand(4, "right", "relaxed", 1.0).hand(12, "right", "two_up", 1.0)
    return a


@anim
def blue_throw():
    """Maximum Output: Blue released — weight rocks back onto the rear foot, then the whole body steps into the throw
    and the orb is sent; the head and free arm trail through. 1 s."""
    a = Anim("blue_throw", 60, aim=("right_arm",), first_person=("right_arm",))
    fade(a, 3, 40, None, 60)
    key(a, 0, merge(stance(), plant(0.6), turn(-8), dict(right_arm=dict(rot=R(-100, 0, 0), bend=(0, -12)))))
    # wind-up: weight back, arm drawn up and behind, body coiled
    key(a, 10, merge(stance(), turn(16), plant(0.9, lean=6, fwd=-1.6, split=-0.4),
                     dict(right_arm=dict(rot=R(-152, -10, 14), bend=(0, -74)), left_arm=dict(rot=R(-30, 0, -20), bend=(0, -30)))), "out")
    # step in and send it: the torso leads, the arm whips through
    key(a, 16, merge(stance(), turn(-20), plant(1.2, lean=-8, fwd=2.8, split=1.0),
                     dict(right_arm=dict(rot=R(-88, 8, 0), bend=(0, -4)), left_arm=dict(rot=R(14, 0, -14), bend=(0, -24)))), "snap")
    # follow-through, the body carried forward, then recovering
    key(a, 26, merge(stance(), turn(-15), plant(1.0, lean=-5, fwd=2.4, split=0.9),
                     dict(right_arm=dict(rot=R(-68, 12, 0), bend=(0, -8)), left_arm=dict(rot=R(8, 0, -10), bend=(0, -28)))))
    key(a, 42, merge(stance(), turn(-8), plant(0.5, lean=-1, fwd=1.4, split=0.4),
                     dict(right_arm=dict(rot=R(-58, 6, 0), bend=(0, -14)))))
    key(a, 60, merge(stance(), turn(0), plant(0.0)))
    a.key(0, head=dict(rot=R(0, 0, 0))).key(10, head=dict(rot=R(-6, 8, 0)), e="out").key(18, head=dict(rot=R(6, -8, 0)), e="snap")
    a.key(60, head=dict(rot=R(0, 0, 0)))
    a.delay("head", 3).delay("left_arm", 3)
    a.hand(0, "right", "two_up", 1.0).hand(9, "right", "two_up", 1.0).hand(14, "right", "open", 1.0)
    a.hand(40, "right", "open", 1.0).hand(56, "right", "relaxed", 0.0)
    return a


# ================================================================== Reversal: Red
@anim
def red_tap():
    """A flick of the index finger: Red forms at the fingertip (tick 4) and leaves; the recoil kicks the hand up and
    is taken in the knees. 0.75 s."""
    a = Anim("red_tap", 45, aim=("right_arm",), first_person=("right_arm",))
    fade(a, 4, 30, None, 45)
    key(a, 0, merge(stance(), turn(0)))
    key(a, 7, merge(stance(), turn(-10), plant(0.3), dict(right_arm=dict(rot=R(-93, 2, 0), bend=(0, -18)))), "out")
    key(a, 11, merge(stance(), turn(-11), plant(0.4, lean=-1), dict(right_arm=dict(rot=R(-91, 2, 0), bend=(0, -16)))))
    # fire (tick 4 = frame 12): kick, weight rocks back into bent knees
    key(a, 15, merge(stance(), turn(-6), plant(0.9, lean=5, fwd=-1.2),
                     dict(right_arm=dict(rot=R(-106, 6, 0), bend=(0, -24)))), "snap")
    key(a, 22, merge(stance(), turn(-7), plant(0.6, lean=2, fwd=-0.8), dict(right_arm=dict(rot=R(-97, 4, 0), bend=(0, -18)))), "back")
    key(a, 30, merge(stance(), turn(-6), plant(0.4, lean=1, fwd=-0.5), dict(right_arm=dict(rot=R(-95, 4, 0), bend=(0, -18)))))
    key(a, 45, merge(stance(), turn(0), plant(0.0), dict(right_arm=dict(rot=R(-25, 0, 0), bend=(0, -18)))))
    a.key(0, head=dict(rot=R(0, 0, 0))).key(16, head=dict(rot=R(-5, 0, 0)), e="snap").key(45, head=dict(rot=R(0, 0, 0)))
    a.delay("head", 2)
    a.hand(0, "right", "relaxed", 0.0).hand(3, "right", "relaxed", 1.0).hand(7, "right", "point", 1.0)
    a.hand(12, "right", "point", 1.0).hand(15, "right", "open", 1.0).hand(30, "right", "open", 1.0)
    a.hand(40, "right", "relaxed", 0.0)
    return a


def red_sign_pose():
    """The incantation Red: two fingers raised beside the face (the reference clip's opening)."""
    return merge(stance(), turn(-14), plant(0.4), dict(
        right_arm=dict(rot=R(-58, -34, 6), bend=(0, -118)),
        head=dict(rot=R(6, -6, 0)),
    ))


@anim
def red_charge():
    """Held while chanting: Red burns at the tip of the two raised fingers. Holds on the last frame."""
    a = Anim("red_charge", 24, first_person=("right_arm",))
    fade(a, 12, None, None, 24)
    key(a, 0, merge(stance(), turn(0)))
    key(a, 14, red_sign_pose(), "out")
    key(a, 24, red_sign_pose())
    # first person: hold the sign out in front and to the right, where you can see it
    a.key(0, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.key(14, right_arm_fp=dict(rot=R(-92, 16, 0), bend=(0, -62)), e="out")
    a.key(24, right_arm_fp=dict(rot=R(-92, 16, 0), bend=(0, -62)))
    a.hand(0, "right", "relaxed", 0.0).hand(5, "right", "relaxed", 1.0).hand(12, "right", "two_up", 1.0)
    return a


@anim
def red_release():
    """The chanted Red fired: the arm drives forward from the sign, the recoil throws the weight back onto the rear
    foot and down through the knees, and he rides it out. 1 s."""
    a = Anim("red_release", 60, aim=("right_arm",), first_person=("right_arm",))
    fade(a, 2, 40, None, 60)
    key(a, 0, merge(red_sign_pose(), plant(0.4)))
    key(a, 3, merge(red_sign_pose(), plant(0.6, lean=2), dict(right_arm=dict(rot=R(-50, -34, 6), bend=(0, -124)))), "out")   # load
    key(a, 7, merge(stance(), turn(-18), plant(0.9, lean=-5, fwd=1.8, split=0.5),
                    dict(right_arm=dict(rot=R(-94, 4, 0), bend=(0, -6)))), "snap")
    key(a, 14, merge(stance(), turn(-8), plant(1.4, lean=9, fwd=-2.4, split=-0.2),
                     dict(right_arm=dict(rot=R(-110, 8, 0), bend=(0, -22)), left_arm=dict(rot=R(-12, 0, -24), bend=(0, -26)))), "out")
    key(a, 24, merge(stance(), turn(-8), plant(1.0, lean=4, fwd=-1.8), dict(right_arm=dict(rot=R(-98, 5, 0), bend=(0, -16)))), "back")
    key(a, 40, merge(stance(), turn(-6), plant(0.5, lean=1, fwd=-1.0), dict(right_arm=dict(rot=R(-92, 4, 0), bend=(0, -14)))))
    key(a, 60, merge(stance(), turn(0), plant(0.0), dict(right_arm=dict(rot=R(-25, 0, 0), bend=(0, -18)))))
    a.key(0, head=dict(rot=R(6, -6, 0))).key(14, head=dict(rot=R(-8, 0, 0)), e="snap").key(60, head=dict(rot=R(0, 0, 0)))
    a.delay("head", 3).delay("left_arm", 3)
    a.hand(0, "right", "two_up", 1.0).hand(5, "right", "point", 1.0).hand(13, "right", "open", 1.0)
    a.hand(40, "right", "open", 1.0).hand(56, "right", "relaxed", 0.0)
    return a


# ================================================================== Hollow Technique: Purple
def arms_spread():
    return dict(right_arm=dict(rot=R(-14, 0, 80), bend=(0, -16)), left_arm=dict(rot=R(-14, 0, -80), bend=(0, -16)))


def hands_together():
    return dict(right_arm=dict(rot=R(-62, -32, 0), bend=(0, -72)), left_arm=dict(rot=R(-62, 32, 0), bend=(0, -72)))


@anim
def purple_tap():
    """Blue in the left hand, Red in the right, drawn together (the merge sign) — they collide at tick 20 — and the
    imaginary mass fired with the pinch at tick 34. The body sinks as the two infinities are pressed together and
    drives forward on the release. 2.4 s."""
    a = Anim("purple_tap", 144, aim=(), first_person=("right_arm", "left_arm"))
    fade(a, 6, 118, None, 144)
    key(a, 0, merge(stance(), turn(0)))
    key(a, 12, merge(stance(), plant(0.5, lean=2), arms_spread()), "out")
    key(a, 30, merge(stance(), plant(0.6, lean=1), arms_spread(), dict(right_arm=dict(rot=R(-24, 0, 70)), left_arm=dict(rot=R(-24, 0, -70)))))
    # pressing them together: the weight sinks, the hands fight to close
    key(a, 60, merge(stance(), plant(1.1, lean=-3, fwd=0.4), hands_together()), "in")
    key(a, 66, merge(stance(), plant(1.3, lean=4, fwd=-0.6), hands_together(),
                     dict(right_arm=dict(rot=R(-60, -32, 0), bend=(0, -76)), left_arm=dict(rot=R(-60, 32, 0), bend=(0, -76)))), "snap")  # the impact
    key(a, 84, merge(stance(), plant(1.0, lean=3, fwd=-0.5), hands_together(),
                     dict(right_arm=dict(rot=R(-58, -30, 0), bend=(0, -80)), left_arm=dict(rot=R(-58, 30, 0), bend=(0, -80)))))
    key(a, 94, merge(stance(), plant(1.1, lean=5, fwd=-1.0), hands_together(),
                     dict(right_arm=dict(rot=R(-50, -24, 0), bend=(0, -96)), left_arm=dict(rot=R(-54, 26, 0), bend=(0, -88)))), "in")  # draw back
    # fire: the right hand drives forward with the pinch, a stride into it, the left drops back to the pocket
    key(a, 102, merge(stance(), turn(-16), plant(1.2, lean=-7, fwd=2.4, split=0.9),
                      dict(right_arm=dict(rot=R(-94, 4, 0), bend=(0, -6)), left_arm=dict(rot=R(10, 0, -14), bend=(0, -30)))), "snap")
    key(a, 118, merge(stance(), turn(-12), plant(0.8, lean=-3, fwd=1.8, split=0.6),
                      dict(right_arm=dict(rot=R(-90, 4, 0), bend=(0, -8)))))
    key(a, 144, merge(stance(), turn(0), plant(0.0), dict(right_arm=dict(rot=R(-20, 0, 0), bend=(0, -18)))))
    a.key(0, head=dict(rot=R(0, 0, 0))).key(60, head=dict(rot=R(10, 0, 0))).key(68, head=dict(rot=R(4, 0, 0)), e="snap")
    a.key(104, head=dict(rot=R(-4, -8, 0)), e="snap").key(144, head=dict(rot=R(0, 0, 0)))
    a.delay("head", 3)
    fp_spread = dict(right_arm_fp=dict(rot=R(-62, 38, 0), bend=(0, -30)), left_arm_fp=dict(rot=R(-62, -38, 0), bend=(0, -30)))
    fp_close = dict(right_arm_fp=dict(rot=R(-78, -6, 0), bend=(0, -40)), left_arm_fp=dict(rot=R(-78, 6, 0), bend=(0, -40)))
    a.key(0, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)), left_arm_fp=dict(rot=R(10, 0, -7), bend=(0, -38)))
    a.key(12, e="out", **fp_spread)
    a.key(30, **fp_spread)
    a.key(60, e="in", **fp_close)
    a.key(94, right_arm_fp=dict(rot=R(-74, -6, 0), bend=(0, -52)), left_arm_fp=dict(rot=R(-74, 6, 0), bend=(0, -52)))
    a.key(102, e="snap", right_arm_fp=dict(rot=R(-92, 2, 0), bend=(0, -8)), left_arm_fp=dict(rot=R(4, 0, -8), bend=(0, -30)))
    a.key(118, right_arm_fp=dict(rot=R(-90, 2, 0), bend=(0, -8)), left_arm_fp=dict(rot=R(4, 0, -8), bend=(0, -30)))
    a.key(144, right_arm_fp=dict(rot=R(-20, 0, 0), bend=(0, -18)), left_arm_fp=dict(rot=R(10, 0, -7), bend=(0, -38)))
    for side in ("right", "left"):
        a.hand(0, side, "relaxed", 0.0).hand(6, side, "relaxed", 1.0).hand(14, side, "two_up", 1.0)
        a.hand(40, side, "two_up", 1.0).hand(56, side, "horns", 1.0)
    a.hand(94, "right", "horns", 1.0).hand(100, "right", "pinch", 1.0).hand(122, "right", "pinch", 1.0).hand(140, "right", "relaxed", 0.0)
    a.hand(92, "left", "horns", 1.0).hand(100, "left", "relaxed", 0.0)
    return a


@anim
def purple_sign_1():
    """九綱 Nine Ropes: the right hand raised before the chest, two fingers upright."""
    a = Anim("purple_sign_1", 30, first_person=("right_arm",))
    fade(a, 14, None, None, 30)
    p = merge(stance(), turn(-10), dict(right_arm=dict(rot=R(-60, -28, 0), bend=(0, -96)), head=dict(rot=R(10, 0, 0))))
    key(a, 0, merge(stance(), turn(0)))
    key(a, 18, p, "out")
    key(a, 30, p)
    a.key(0, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.key(18, right_arm_fp=dict(rot=R(-86, -8, 0), bend=(0, -58)), e="out")
    a.key(30, right_arm_fp=dict(rot=R(-86, -8, 0), bend=(0, -58)))
    a.hand(0, "right", "relaxed", 0.0).hand(6, "right", "relaxed", 1.0).hand(16, "right", "two_up", 1.0)
    return a


@anim
def purple_sign_2():
    """偏光 Polarized Light: both hands up either side of the head — Blue's side and Red's side."""
    a = Anim("purple_sign_2", 30, first_person=("right_arm", "left_arm"))
    fade(a, 4, None, None, 30)
    legs = dict(right_leg=dict(rot=R(-5, 0, 7), bend=(0, 8)), left_leg=dict(rot=R(5, 0, -6), bend=(0, 6)))
    p = merge(stance(), legs, dict(right_arm=dict(rot=R(-40, 0, 46), bend=(0, -102)),
                                   left_arm=dict(rot=R(-40, 0, -46), bend=(0, -102)),
                                   body=dict(rot=R(0, 0, 0)), head=dict(rot=R(-4, 0, 0))))
    key(a, 0, merge(stance(), turn(-10), dict(right_arm=dict(rot=R(-60, -28, 0), bend=(0, -96)))))
    key(a, 18, p, "out")
    key(a, 30, p)
    a.key(0, right_arm_fp=dict(rot=R(-86, -8, 0), bend=(0, -58)), left_arm_fp=dict(rot=R(10, 0, -7), bend=(0, -38)))
    a.key(18, right_arm_fp=dict(rot=R(-80, 22, 0), bend=(0, -70)), left_arm_fp=dict(rot=R(-80, -22, 0), bend=(0, -70)), e="out")
    a.key(30, right_arm_fp=dict(rot=R(-80, 22, 0), bend=(0, -70)), left_arm_fp=dict(rot=R(-80, -22, 0), bend=(0, -70)))
    a.hand(0, "right", "two_up", 1.0).hand(12, "right", "horns", 1.0)
    a.hand(0, "left", "relaxed", 0.0).hand(6, "left", "relaxed", 1.0).hand(14, "left", "horns", 1.0)
    return a


@anim
def purple_sign_3():
    """烏と声明 Crow and Declaration: the hands meet before the face, fingers crossed over each other."""
    a = Anim("purple_sign_3", 30, first_person=("right_arm", "left_arm"))
    fade(a, 2, None, None, 30)
    legs = dict(right_leg=dict(rot=R(-5, 0, 7), bend=(0, 8)), left_leg=dict(rot=R(5, 0, -6), bend=(0, 6)))
    p = merge(stance(), legs, dict(right_arm=dict(rot=R(-66, -30, 0), bend=(0, -96)),
                                   left_arm=dict(rot=R(-62, 28, 0), bend=(0, -100)),
                                   head=dict(rot=R(12, 0, 0)), root=dict(rot=R(-2, 0, 0))))
    key(a, 0, merge(stance(), legs, dict(right_arm=dict(rot=R(-40, 0, 46), bend=(0, -102)), left_arm=dict(rot=R(-40, 0, -46), bend=(0, -102)))))
    key(a, 16, p, "out")
    key(a, 30, p)
    a.key(0, right_arm_fp=dict(rot=R(-80, 22, 0), bend=(0, -70)), left_arm_fp=dict(rot=R(-80, -22, 0), bend=(0, -70)))
    a.key(16, right_arm_fp=dict(rot=R(-84, 14, 0), bend=(0, -52)), left_arm_fp=dict(rot=R(-84, -12, 0), bend=(0, -56)), e="out")
    a.key(30, right_arm_fp=dict(rot=R(-84, 14, 0), bend=(0, -52)), left_arm_fp=dict(rot=R(-84, -12, 0), bend=(0, -56)))
    a.hand(0, "right", "horns", 1.0).hand(12, "right", "seal", 1.0)
    a.hand(0, "left", "horns", 1.0).hand(12, "left", "seal", 1.0)
    return a


@anim
def purple_sign_4():
    """表裏の間 Between Front and Back: the pinch thrust forward, the left hand bracing the right forearm."""
    a = Anim("purple_sign_4", 30, aim=("right_arm",), first_person=("right_arm", "left_arm"))
    fade(a, 2, None, None, 30)
    legs = dict(right_leg=dict(rot=R(-12, 0, 8), bend=(0, 16)), left_leg=dict(rot=R(10, 0, -7), bend=(0, 10)))
    p = merge(stance(), legs, turn(-16), dict(right_arm=dict(rot=R(-90, 0, 0), bend=(0, -10)),
                                              left_arm=dict(rot=R(-66, 36, 0), bend=(0, -60)),
                                              root=dict(rot=R(-5, 0, 0), pos=(0, -0.8, 1))))
    key(a, 0, merge(stance(), legs, dict(right_arm=dict(rot=R(-66, -30, 0), bend=(0, -96)), left_arm=dict(rot=R(-62, 28, 0), bend=(0, -100)))))
    key(a, 14, p, "back")
    key(a, 30, p)
    a.hand(0, "right", "seal", 1.0).hand(10, "right", "pinch", 1.0)
    a.hand(0, "left", "seal", 1.0).hand(10, "left", "grip", 1.0)
    return a


@anim
def purple_200_release():
    """The 200% Purple: Blue and Red swing in and collide between the hands (tick 16); the mass is held and it fights
    back — he sinks into a deep stance and is shoved back — then (tick 44) he steps into it and sends it with
    everything he has, and the recoil nearly takes him off his feet. 3.8 s."""
    a = Anim("purple_200_release", 228, aim=("right_arm",), first_person=("right_arm", "left_arm"))
    fade(a, 2, 190, None, 228)
    sign4 = merge(stance(), plant(1.0, lean=-5, fwd=1, split=0.6), turn(-16),
                  dict(right_arm=dict(rot=R(-90, 0, 0), bend=(0, -10)), left_arm=dict(rot=R(-66, 36, 0), bend=(0, -60))))
    hold = merge(stance(), plant(1.6, lean=3, fwd=-0.5, split=0.4), turn(-6),
                 dict(right_arm=dict(rot=R(-80, -16, 0), bend=(0, -44)), left_arm=dict(rot=R(-80, 18, 0), bend=(0, -44))))
    key(a, 0, sign4)
    key(a, 30, merge(hold, dict(right_arm=dict(rot=R(-74, -26, 0), bend=(0, -60)), left_arm=dict(rot=R(-74, 28, 0), bend=(0, -60)))))
    key(a, 48, merge(hold, plant(2.0, lean=9, fwd=-2.2, split=0.3)), "snap")                 # the collision shoves him back
    key(a, 70, merge(hold, plant(1.7, lean=5, fwd=-1.6, split=0.3)), "back")
    key(a, 100, merge(hold, plant(1.9, lean=7, fwd=-2.0, split=0.3)))                           # the mass swelling, pushing
    key(a, 122, merge(hold, plant(2.2, lean=-2, fwd=-1.0, split=0.5),
                      dict(right_arm=dict(rot=R(-70, -12, 0), bend=(0, -70)), left_arm=dict(rot=R(-74, 14, 0), bend=(0, -58)))), "in")  # draw back
    # send it: step through, the whole body behind the right arm
    key(a, 132, merge(stance(), turn(-22), plant(1.8, lean=-12, fwd=4.0, split=1.2),
                      dict(right_arm=dict(rot=R(-92, 2, 0), bend=(0, -2)), left_arm=dict(rot=R(-10, -10, -34), bend=(0, -30)))), "snap")
    # recoil: thrown back onto the rear leg, arms loose
    key(a, 146, merge(stance(), turn(-10), plant(1.6, lean=10, fwd=0.5, split=0.4),
                      dict(right_arm=dict(rot=R(-104, 10, 8), bend=(0, -18)), left_arm=dict(rot=R(-20, 0, -40), bend=(0, -24)))), "out")
    key(a, 176, merge(stance(), turn(-12), plant(1.0, lean=2, fwd=1.4, split=0.5),
                      dict(right_arm=dict(rot=R(-90, 4, 0), bend=(0, -10)), left_arm=dict(rot=R(0, 0, -14), bend=(0, -28)))), "back")
    key(a, 228, merge(stance(), turn(0), plant(0.0), dict(right_arm=dict(rot=R(-20, 0, 0), bend=(0, -18)))))
    a.key(0, head=dict(rot=R(0, 0, 0))).key(48, head=dict(rot=R(-8, 0, 0)), e="snap").key(120, head=dict(rot=R(12, 0, 0)))
    a.key(134, head=dict(rot=R(0, -6, 0)), e="snap").key(148, head=dict(rot=R(-14, 0, 0)), e="out").key(228, head=dict(rot=R(0, 0, 0)))
    a.delay("head", 3).delay("left_arm", 3)
    a.hand(0, "right", "pinch", 1.0).hand(20, "right", "horns", 1.0).hand(124, "right", "horns", 1.0)
    a.hand(130, "right", "open", 1.0).hand(196, "right", "open", 1.0).hand(222, "right", "relaxed", 0.0)
    a.hand(0, "left", "grip", 1.0).hand(20, "left", "horns", 1.0).hand(124, "left", "horns", 1.0)
    a.hand(132, "left", "open", 1.0).hand(160, "left", "relaxed", 0.0)
    return a


# ================================================================== Remote Hollow Purple (NukeEntity timeline)
# The reference clip's body beats (Avour's edit of ch. 235), tick for tick:
#   0–2.5 s   the sign raised in front of the chest, head lowered, a faint smile; Red at the fingertips
#   3.0–4.0 s the sign brought up beside the eye, Red burning next to it
#   4.5–5.5 s the arm whipped straight up; Red leaves; he watches it go
#   6.0–7.0 s the two fingers back in front of his face, eyes to camera (the Six Eyes blaze)
#   13–14 s   crouched, then the leap          · 14.5–16 s airborne, the Purple incantation signed in the air
#   19–20 s   calm, facing the bloom           · 21–22.5 s a floating silhouette, one knee drawn up
def sign_up(height):
    """The two-finger sign held up in front: height 0 = chest, 1 = beside the right eye."""
    return dict(right_arm=dict(rot=R(-36 - 30 * height, -20 - 12 * height, 2 + 2 * height), bend=(0, -84 - 38 * height)))


@anim
def nuke_red():
    """T0 → T_THROW (84): Red at the fingertips of the raised sign while its incantation is chanted (T12/30/48); with
    every word the sign rises, until Red burns beside the right eye (T60). A slight forward hunch, head lowered."""
    L = 84 * T
    a = Anim("nuke_red", L, first_person=("right_arm",))
    fade(a, 18, None, None, L)
    hunch = dict(body=dict(rot=R(6, 0, 0)), root=dict(rot=R(-3, 0, 0)))
    head_down = dict(head=dict(rot=R(14, -10, 2)))
    key(a, 0, merge(stance(), turn(0)))
    key(a, 10 * T, merge(stance(), turn(-10), plant(0.25), hunch, head_down, sign_up(0.0)), "out")
    key(a, 30 * T, merge(stance(), turn(-12), plant(0.3), hunch, head_down, sign_up(0.35)), "out")
    key(a, 48 * T, merge(stance(), turn(-14), plant(0.35), hunch, dict(head=dict(rot=R(10, -12, 4))), sign_up(0.7)), "out")
    top = merge(stance(), turn(-16), plant(0.4), hunch, dict(head=dict(rot=R(8, -14, 6))), sign_up(1.0))
    key(a, 60 * T, top, "out")
    key(a, 78 * T, merge(top, dict(right_arm=dict(rot=R(-68, -33, 5), bend=(0, -124)))))
    key(a, L, merge(top, plant(0.55), dict(right_arm=dict(rot=R(-62, -32, 6), bend=(0, -126)))), "in")
    a.delay("head", 4).delay("body", 2)
    a.key(0, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.key(10 * T, right_arm_fp=dict(rot=R(-84, 14, 0), bend=(0, -60)), e="out")
    a.key(60 * T, right_arm_fp=dict(rot=R(-100, 12, 0), bend=(0, -70)), e="out")
    a.key(L, right_arm_fp=dict(rot=R(-100, 12, 0), bend=(0, -72)))
    a.hand(0, "right", "relaxed", 0.0).hand(8, "right", "relaxed", 1.0).hand(20, "right", "two_up", 1.0)
    return a


@anim
def nuke_throw():
    """T_THROW (84) → T_LEAP (262): Red hurled straight up — a dip to load, up onto the toes as the arm whips overhead;
    he watches it go, then brings the two fingers back in front of his face for the Six Eyes (T110–140), lowers them,
    watches the sky while Blue is chanted, and crouches for the leap."""
    L = (262 - 84) * T
    a = Anim("nuke_throw", L, first_person=("right_arm",))
    fade(a, 1, None, None, L)
    start = merge(stance(), turn(-16), plant(0.55), dict(right_arm=dict(rot=R(-62, -32, 6), bend=(0, -126)), head=dict(rot=R(8, -14, 6))))
    up = merge(stance(), turn(-4), plant(-0.15, lean=8), dict(right_arm=dict(rot=R(-176, -4, 6), bend=(0, -4)), head=dict(rot=R(-10, 0, 0)),
                                                             root=dict(rot=R(8, 0, 0), pos=(0, 0.8, -0.6))))
    face = merge(stance(), turn(-12), plant(0.3), dict(right_arm=dict(rot=R(-48, -18, 4), bend=(0, -100)), head=dict(rot=R(6, -4, 0)),
                                                      body=dict(rot=R(4, 0, 0))))
    watch = merge(stance(), plant(0.15), dict(head=dict(rot=R(-6, 0, 0))))
    crouch = merge(stance(), plant(1.9, lean=-10, fwd=0.6), dict(right_arm=dict(rot=R(30, 0, 12), bend=(0, -20)),
                                                                   left_arm=dict(rot=R(30, 0, -12), bend=(0, -20)),
                                                                   body=dict(rot=R(14, 0, 0)), head=dict(rot=R(-20, 0, 0))))
    key(a, 0, start)
    key(a, 5, merge(start, plant(1.2, lean=-4), dict(right_arm=dict(rot=R(-46, -30, 8), bend=(0, -130)))), "out")   # load
    key(a, 12, up, "snap")
    key(a, 20, merge(up, dict(right_arm=dict(rot=R(-170, -4, 6), bend=(0, -8)))), "back")
    key(a, 50, merge(up, dict(root=dict(rot=R(5, 0, 0), pos=(0, 0.3, -0.3)))))
    key(a, 78, face, "out")                                     # T110: the sign back in front of the face
    key(a, 168, merge(face, dict(head=dict(rot=R(4, -3, 0)))))  # T140
    key(a, 200, merge(watch, dict(right_arm=dict(rot=R(-12, 0, 6), bend=(0, -22)))), "smooth")
    key(a, 420, merge(watch, dict(right_arm=dict(rot=R(-4, 0, 4), bend=(0, -10)), body=dict(rot=R(-2, 0, 0)))))
    key(a, 480, merge(watch, dict(head=dict(rot=R(-12, 0, 0)))))
    key(a, 518, crouch, "in")                                   # T257: down into the crouch, arms swung back
    key(a, L, merge(crouch, plant(2.1, lean=-12, fwd=0.7)))
    a.delay("head", 3).delay("left_arm", 2)
    a.key(0, right_arm_fp=dict(rot=R(-100, 12, 0), bend=(0, -72)))
    a.key(12, right_arm_fp=dict(rot=R(-170, 6, 0), bend=(0, -6)), e="snap")
    a.key(50, right_arm_fp=dict(rot=R(-160, 6, 0), bend=(0, -8)))
    a.key(78, right_arm_fp=dict(rot=R(-92, 14, 0), bend=(0, -66)), e="out")
    a.key(168, right_arm_fp=dict(rot=R(-92, 14, 0), bend=(0, -66)))
    a.key(200, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.hand(0, "right", "two_up", 1.0).hand(9, "right", "two_up", 1.0).hand(12, "right", "open", 1.0)
    a.hand(50, "right", "open", 1.0).hand(70, "right", "two_up", 1.0).hand(168, "right", "two_up", 1.0)
    a.hand(196, "right", "relaxed", 1.0).hand(230, "right", "relaxed", 0.0)
    a.hand(500, "right", "relaxed", 0.0).hand(518, "right", "fist", 0.8).hand(500, "left", "relaxed", 0.0).hand(518, "left", "fist", 0.8)
    return a


@anim
def nuke_air():
    """T_LEAP (262) → T_BLOOM (396): the leap (Blue's pull carries him), knees tucked, then floating behind the target;
    the Purple incantation signed in the air (T_PURPLE_1..4 = 290/308/326/344)."""
    L = (396 - 262) * T
    a = Anim("nuke_air", L, first_person=("right_arm", "left_arm"))
    fade(a, 1, None, None, L)
    push = merge(dict(right_leg=dict(rot=R(10, 0, 3), bend=(0, 2)), left_leg=dict(rot=R(16, 0, -3), bend=(0, 4)),
                      right_arm=dict(rot=R(-150, 0, 18), bend=(0, -10)), left_arm=dict(rot=R(-146, 0, -18), bend=(0, -10)),
                      body=dict(rot=R(-4, 0, 0)), root=dict(rot=R(-10, 0, 0), pos=(0, 1.0, 0.6)), head=dict(rot=R(-24, 0, 0))))
    tuck = merge(dict(right_leg=dict(rot=R(-58, 0, 6), bend=(0, 88)), left_leg=dict(rot=R(-36, 0, -6), bend=(0, 96)),
                      right_arm=dict(rot=R(-36, 0, 36), bend=(0, -30)), left_arm=dict(rot=R(-32, 0, -40), bend=(0, -30)),
                      body=dict(rot=R(10, 0, 0)), root=dict(rot=R(-4, 0, 0)), head=dict(rot=R(-8, 0, 0))))
    dangle = dict(right_leg=dict(rot=R(-12, 0, 5), bend=(0, 30)), left_leg=dict(rot=R(14, 0, -4), bend=(0, 44)))
    float_ = merge(dangle, dict(right_arm=dict(rot=R(-10, 0, 16), bend=(0, -18)), left_arm=dict(rot=R(-8, 0, -18), bend=(0, -20)),
                                body=dict(rot=R(2, 0, 0)), root=dict(rot=R(4, 0, 0), pos=(0, 0, 0)), head=dict(rot=R(6, 0, 0))))
    key(a, 0, push)
    key(a, 14, tuck, "out")
    key(a, 60, merge(tuck, dict(right_leg=dict(rot=R(-40, 0, 6), bend=(0, 70)), left_leg=dict(rot=R(-18, 0, -6), bend=(0, 76)))))
    key(a, 30 * T, float_)                                     # T292: arrived at the hover point
    s1 = (290 - 262) * T; s2 = (308 - 262) * T; s3 = (326 - 262) * T; s4 = (344 - 262) * T; col = (356 - 262) * T
    sign1 = merge(float_, dict(right_arm=dict(rot=R(-60, -28, 0), bend=(0, -96))))
    sign2 = merge(float_, dict(right_arm=dict(rot=R(-40, 0, 46), bend=(0, -102)), left_arm=dict(rot=R(-40, 0, -46), bend=(0, -102))))
    sign3 = merge(float_, dict(right_arm=dict(rot=R(-66, -30, 0), bend=(0, -96)), left_arm=dict(rot=R(-62, 28, 0), bend=(0, -100))))
    sign4 = merge(float_, dict(right_arm=dict(rot=R(-92, 4, 0), bend=(0, -10)), left_arm=dict(rot=R(-66, 36, 0), bend=(0, -60)),
                               root=dict(rot=R(-4, 0, 0))))
    key(a, s1 + 10, merge(sign1, dict(right_leg=dict(rot=R(-20, 0, 6), bend=(0, 44)))), "out")
    key(a, s2 + 8, sign2, "out")
    key(a, s3 + 8, sign3, "out")
    key(a, s4 + 8, sign4, "back")
    key(a, col, merge(sign4, dict(right_arm=dict(rot=R(-96, 4, 0), bend=(0, -4)))))
    key(a, L, merge(sign4, dict(root=dict(rot=R(2, 0, 0)))))
    a.delay("head", 3).delay("left_leg", 3)
    a.hand(0, "right", "open", 1.0).hand(14, "right", "relaxed", 1.0).hand(s1, "right", "relaxed", 1.0).hand(s1 + 8, "right", "two_up", 1.0)
    a.hand(s2, "right", "two_up", 1.0).hand(s2 + 8, "right", "horns", 1.0)
    a.hand(s3, "right", "horns", 1.0).hand(s3 + 8, "right", "seal", 1.0)
    a.hand(s4, "right", "seal", 1.0).hand(s4 + 8, "right", "pinch", 1.0)
    a.hand(0, "left", "open", 1.0).hand(14, "left", "relaxed", 1.0).hand(s2, "left", "relaxed", 1.0).hand(s2 + 8, "left", "horns", 1.0)
    a.hand(s3, "left", "horns", 1.0).hand(s3 + 8, "left", "seal", 1.0)
    a.hand(s4, "left", "seal", 1.0).hand(s4 + 8, "left", "grip", 1.0)
    return a


@anim
def nuke_brace():
    """T_BLOOM (396) → T_END (540): caught inside his own erasure. Calm: the pinch lowers, the mass shoves him back a
    little, arms drift loose; then the floating silhouette of the wide shot — one knee drawn up."""
    L = (540 - 396) * T
    a = Anim("nuke_brace", L, first_person=())
    fade(a, 2, None, None, L)
    dangle = dict(right_leg=dict(rot=R(-18, 0, 6), bend=(0, 34)), left_leg=dict(rot=R(10, 0, -6), bend=(0, 48)))
    p0 = merge(dangle, dict(right_arm=dict(rot=R(-96, 4, 0), bend=(0, -4)), left_arm=dict(rot=R(-66, 36, 0), bend=(0, -60)),
                            root=dict(rot=R(-2, 0, 0))))
    shoved = merge(dangle, dict(right_arm=dict(rot=R(-34, 0, 30), bend=(0, -24)), left_arm=dict(rot=R(-30, 0, -32), bend=(0, -26)),
                                body=dict(rot=R(-3, 0, 0)), root=dict(rot=R(7, 0, 0), pos=(0, 0.5, -1.5)), head=dict(rot=R(-4, 0, 0))))
    knee = merge(dict(right_leg=dict(rot=R(-62, 0, 8), bend=(0, 80)), left_leg=dict(rot=R(16, 0, -6), bend=(0, 36)),
                      right_arm=dict(rot=R(-24, 0, 34), bend=(0, -30)), left_arm=dict(rot=R(-10, 0, -40), bend=(0, -20)),
                      body=dict(rot=R(4, 0, 0)), root=dict(rot=R(4, 0, 0), pos=(0, 0.3, -1.0)), head=dict(rot=R(-2, 0, 0))))
    key(a, 0, p0)
    key(a, 14, shoved, "out")
    key(a, 70, merge(shoved, dict(root=dict(rot=R(5, 0, 0), pos=(0, 0.4, -1.2)))))
    key(a, 110, knee, "smooth")                                 # T433: the silhouette of the wide shot
    key(a, 250, merge(knee, dict(right_leg=dict(rot=R(-56, 0, 8), bend=(0, 74)))))
    key(a, L, merge(dangle, dict(right_arm=dict(rot=R(-12, 0, 20), bend=(0, -16)), left_arm=dict(rot=R(-12, 0, -20), bend=(0, -16)),
                                 root=dict(rot=R(4, 0, 0)), head=dict(rot=R(8, 0, 0)))))
    a.delay("head", 4).delay("left_arm", 3).delay("left_leg", 4)
    a.hand(0, "right", "pinch", 1.0).hand(10, "right", "open", 1.0).hand(60, "right", "open", 1.0).hand(100, "right", "relaxed", 0.0)
    a.hand(0, "left", "grip", 1.0).hand(10, "left", "open", 1.0).hand(60, "left", "open", 1.0).hand(100, "left", "relaxed", 0.0)
    return a


# ================================================================== Domain Expansion: Unlimited Void (DomainEntity)
def seal_pose(pull=0.0):
    """S1E7's seal: index and middle fingers crossed, raised beside the right cheek; the left hand hooked into the
    high collar, pulling it down (pull 0..1)."""
    return merge(stance(), turn(-6), plant(0.2), sign_up(0.8), dict(
        left_arm=dict(rot=R(-46 + 6 * pull, 40, 0), bend=(0, -126 + 8 * pull)),
        head=dict(rot=R(8 + 3 * pull, -8, 4)),
        body=dict(rot=R(2, 0, 0)),
    ))


@anim
def domain_seal():
    """The full domain (D_WHITE 44, D_INK 58, D_TUNNEL 66, D_FLASH 104, D_OPEN 106): the Six Eyes, then the seal
    raised beside his face while the other hand pulls the collar down; held through the white, the ink and the
    tunnel; released once the void is open."""
    L = 130 * T
    a = Anim("domain_seal", L, first_person=("right_arm", "left_arm"))
    a.weight(0, 0.0).weight(12, 1.0, "out").weight(106 * T, 1.0).weight(L, 0.0, "smooth")
    key(a, 0, merge(stance(), turn(0)))
    key(a, 6 * T, merge(stance(), turn(-3), dict(right_arm=dict(rot=R(-30, -14, 4), bend=(0, -70)),
                                                left_arm=dict(rot=R(-24, 20, 0), bend=(0, -80)))), "in")
    key(a, 12 * T, seal_pose(0.0), "out")
    key(a, 30 * T, seal_pose(1.0), "smooth")
    key(a, 104 * T, merge(seal_pose(1.0), dict(head=dict(rot=R(4, -6, 2)))))
    key(a, L, merge(stance(), turn(0)), "smooth")
    a.delay("head", 3).delay("left_arm", 4)
    a.key(0, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)), left_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.key(12 * T, right_arm_fp=dict(rot=R(-96, 10, 0), bend=(0, -74)), left_arm_fp=dict(rot=R(-70, -30, 0), bend=(0, -90)), e="out")
    a.key(104 * T, right_arm_fp=dict(rot=R(-96, 10, 0), bend=(0, -74)), left_arm_fp=dict(rot=R(-70, -30, 0), bend=(0, -90)))
    a.key(L, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)), left_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.hand(0, "right", "relaxed", 0.0).hand(4 * T, "right", "relaxed", 1.0).hand(10 * T, "right", "seal", 1.0)
    a.hand(106 * T, "right", "seal", 1.0).hand(122 * T, "right", "relaxed", 0.0)
    a.hand(0, "left", "relaxed", 0.0).hand(4 * T, "left", "relaxed", 1.0).hand(11 * T, "left", "grip", 1.0)
    a.hand(106 * T, "left", "grip", 1.0).hand(122 * T, "left", "relaxed", 0.0)
    return a


@anim
def domain_instant():
    """The 0.2-second domain (I_WHITE 8, I_INK 11, I_VOID 15, I_WIPE 19, I_END 31): the seal snaps up, the collar
    is hooked, and it is over before anyone could see it."""
    L = 31 * T
    a = Anim("domain_instant", L, first_person=("right_arm", "left_arm"))
    a.weight(0, 0.0).weight(5, 1.0, "out").weight(22 * T, 1.0).weight(L, 0.0, "smooth")
    key(a, 0, merge(stance(), turn(0)))
    key(a, 3 * T, seal_pose(0.0), "snap")
    key(a, 6 * T, seal_pose(1.0), "out")
    key(a, 20 * T, seal_pose(1.0))
    key(a, L, merge(stance(), turn(0)), "smooth")
    a.delay("left_arm", 2)
    a.key(0, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)), left_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.key(3 * T, right_arm_fp=dict(rot=R(-96, 10, 0), bend=(0, -74)), left_arm_fp=dict(rot=R(-70, -30, 0), bend=(0, -90)), e="snap")
    a.key(20 * T, right_arm_fp=dict(rot=R(-96, 10, 0), bend=(0, -74)), left_arm_fp=dict(rot=R(-70, -30, 0), bend=(0, -90)))
    a.key(L, right_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)), left_arm_fp=dict(rot=R(0, 0, 0), bend=(0, 0)))
    a.hand(0, "right", "relaxed", 1.0).hand(2 * T, "right", "seal", 1.0).hand(22 * T, "right", "seal", 1.0).hand(29 * T, "right", "relaxed", 0.0)
    a.hand(0, "left", "relaxed", 1.0).hand(3 * T, "left", "grip", 1.0).hand(22 * T, "left", "grip", 1.0).hand(29 * T, "left", "relaxed", 0.0)
    return a


if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if "--preview" in argv:
        names = argv[argv.index("--preview") + 1].split(",")
        import preview
        frames = None
        if "--frames" in argv:
            frames = [int(x) for x in argv[argv.index("--frames") + 1].split(",")]
        preview.run([ANIMS[n] for n in names], argv, frames=frames)
    else:
        for a in ANIMS.values():
            print("wrote", a.export())
