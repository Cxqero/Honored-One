"""Calibration poses: render in Blender and export for the game, to compare conventions side by side.
    python3 blender/anim/calib.py                   (export JSON)
    tools/blender -b -P blender/anim/calib.py -- --preview --skin <png> --out <dir>
"""
import os, sys, math
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import rig2
from rig2 import Anim, R

ANIMS = []

a = Anim("calib_bend", 30, first_person=("right_arm",))
a.weight(0, 0).weight(10, 1)
a.key(0, right_arm=dict(rot=R(0, 0, 0), bend=(0, 0)))
a.key(12, right_arm=dict(rot=R(-90, 0, 0), bend=(0, -90)), left_arm=dict(rot=R(0, 0, 0), bend=(0, 45)))
a.key(12, right_leg=dict(rot=R(-30, 0, 0), bend=(0, 50)))
a.hand(0, "right", "relaxed", 1.0)
ANIMS.append(a)

a = Anim("calib_root", 30, first_person=())
a.weight(0, 0).weight(10, 1)
a.key(0, root=dict(rot=R(0, 0, 0), pos=(0, 0, 0)))
a.key(12, root=dict(rot=R(25, 0, 0), pos=(8, 4, 8)))
ANIMS.append(a)

a = Anim("calib_rooty", 30, first_person=())
a.weight(0, 0).weight(10, 1)
a.key(0, root=dict(rot=R(0, 0, 0))).key(12, root=dict(rot=R(0, 35, 0)))
ANIMS.append(a)

a = Anim("calib_rootz", 30, first_person=())
a.weight(0, 0).weight(10, 1)
a.key(0, root=dict(rot=R(0, 0, 0))).key(12, root=dict(rot=R(0, 0, 25)))
ANIMS.append(a)

a = Anim("calib_hands", 30, first_person=("right_arm", "left_arm"))
a.weight(0, 0).weight(10, 1)
a.key(0, right_arm=dict(rot=R(0, 0, 0)), left_arm=dict(rot=R(0, 0, 0)))
a.key(12, right_arm=dict(rot=R(-60, -20, 0), bend=(0, -95)), left_arm=dict(rot=R(-60, 20, 0), bend=(0, -95)))
a.hand(0, "right", "two_up", 1.0).hand(0, "left", "seal", 1.0)
ANIMS.append(a)

if __name__ == "__main__":
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    if "--preview" in argv:
        import preview
        preview.run(ANIMS, argv, frames=[29])
    else:
        for an in ANIMS:
            print("wrote", an.export())
