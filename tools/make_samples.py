"""Debug sample media for QA (design `verification.debugHooks`: media push): app/src/debug/assets/media/.

- sample.jpg: an asymmetric test picture ("F" and an arrow pointing up, a red corner top-left) so a mirrored
  or upside-down image card shows at once (taste D10: check texture direction first).
- sample.glb: a small asymmetric model (a base slab, a tall post on its left-back, an arrow pointing +X),
  one material per part, written by hand as glTF 2.0 binary; so a turn by the stick is visible.
- sample.fbx: not a model the app takes (S38).

Run: python -I tools/make_samples.py
"""
import json
import os
import struct
import sys

from PIL import Image, ImageDraw, ImageFont

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "debug", "assets", "media")


def picture(path):
    w, h = 1600, 1200  # larger than 1024 so the app's scaling shows (S36 "已缩到 1024 px")
    im = Image.new("RGB", (w, h), (246, 240, 228))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 260, 200], fill=(214, 72, 64))  # top-left corner mark
    try:
        font = ImageFont.truetype("arialbd.ttf", 640)
    except OSError:
        font = ImageFont.load_default()
    d.text((380, 220), "F", fill=(40, 34, 60), font=font)
    # an arrow pointing up on the right
    d.polygon([(1250, 180), (1450, 480), (1320, 480), (1320, 1000), (1180, 1000), (1180, 480), (1050, 480)], fill=(59, 42, 107))
    d.text((60, h - 120), "TOP-LEFT RED · ARROW UP", fill=(40, 34, 60), font=ImageFont.load_default())
    im.save(path, "JPEG", quality=90)


def box(cx, cy, cz, sx, sy, sz):
    """24 vertices (flat normals), 36 indices of an axis-aligned box."""
    pos, nrm, idx = [], [], []
    faces = [
        ((1, 0, 0), [(1, -1, -1), (1, 1, -1), (1, 1, 1), (1, -1, 1)]),
        ((-1, 0, 0), [(-1, -1, 1), (-1, 1, 1), (-1, 1, -1), (-1, -1, -1)]),
        ((0, 1, 0), [(-1, 1, -1), (-1, 1, 1), (1, 1, 1), (1, 1, -1)]),
        ((0, -1, 0), [(-1, -1, 1), (-1, -1, -1), (1, -1, -1), (1, -1, 1)]),
        ((0, 0, 1), [(1, -1, 1), (1, 1, 1), (-1, 1, 1), (-1, -1, 1)]),
        ((0, 0, -1), [(-1, -1, -1), (-1, 1, -1), (1, 1, -1), (1, -1, -1)]),
    ]
    for n, corners in faces:
        base = len(pos)
        for c in corners:
            pos.append((cx + c[0] * sx / 2, cy + c[1] * sy / 2, cz + c[2] * sz / 2))
            nrm.append(n)
        idx += [base, base + 1, base + 2, base, base + 2, base + 3]
    return pos, nrm, idx


def glb(path):
    parts = [
        (box(0, 0.02, 0, 0.30, 0.04, 0.20), (0.85, 0.80, 0.70, 1)),  # slab
        (box(-0.10, 0.17, -0.06, 0.05, 0.26, 0.05), (0.23, 0.16, 0.42, 1)),  # post, left-back
        (box(0.06, 0.07, 0.04, 0.16, 0.05, 0.05), (0.85, 0.30, 0.25, 1)),  # arrow shaft along +X
        (box(0.155, 0.07, 0.04, 0.04, 0.09, 0.09), (0.85, 0.30, 0.25, 1)),  # arrow head
    ]
    blob = bytearray()
    views, accessors, meshes, materials, nodes = [], [], [], [], []
    for i, ((pos, nrm, idx), color) in enumerate(parts):
        def add(data, target, comp, count, typ, mn=None, mx=None):
            while len(blob) % 4:
                blob.append(0)
            off = len(blob)
            blob.extend(data)
            views.append({"buffer": 0, "byteOffset": off, "byteLength": len(data), "target": target})
            a = {"bufferView": len(views) - 1, "componentType": comp, "count": count, "type": typ}
            if mn is not None:
                a["min"], a["max"] = mn, mx
            accessors.append(a)
            return len(accessors) - 1

        p = add(b"".join(struct.pack("<3f", *v) for v in pos), 34962, 5126, len(pos), "VEC3", [min(v[k] for v in pos) for k in range(3)], [max(v[k] for v in pos) for k in range(3)])
        n = add(b"".join(struct.pack("<3f", *v) for v in nrm), 34962, 5126, len(nrm), "VEC3")
        ix = add(b"".join(struct.pack("<H", v) for v in idx), 34963, 5123, len(idx), "SCALAR")
        materials.append({"pbrMetallicRoughness": {"baseColorFactor": list(color), "metallicFactor": 0.0, "roughnessFactor": 0.7}})
        meshes.append({"primitives": [{"attributes": {"POSITION": p, "NORMAL": n}, "indices": ix, "material": i}]})
        nodes.append({"mesh": i, "name": ["slab", "post", "shaft", "head"][i]})
    while len(blob) % 4:
        blob.append(0)
    gltf = {
        "asset": {"version": "2.0", "generator": "memory-palace tools/make_samples.py"},
        "scene": 0,
        "scenes": [{"nodes": list(range(len(nodes)))}],
        "nodes": nodes,
        "meshes": meshes,
        "materials": materials,
        "accessors": accessors,
        "bufferViews": views,
        "buffers": [{"byteLength": len(blob)}],
    }
    js = json.dumps(gltf, separators=(",", ":")).encode()
    js += b" " * ((4 - len(js) % 4) % 4)
    total = 12 + 8 + len(js) + 8 + len(blob)
    with open(path, "wb") as f:
        f.write(struct.pack("<III", 0x46546C67, 2, total))
        f.write(struct.pack("<II", len(js), 0x4E4F534A) + js)
        f.write(struct.pack("<II", len(blob), 0x004E4942) + bytes(blob))


def main():
    os.makedirs(OUT, exist_ok=True)
    picture(os.path.join(OUT, "sample.jpg"))
    glb(os.path.join(OUT, "sample.glb"))
    with open(os.path.join(OUT, "sample.fbx"), "wb") as f:
        f.write(b"Kaydara FBX Binary  \x00\x1a\x00" + b"\x00" * 64)
    for n in sorted(os.listdir(OUT)):
        print(n, os.path.getsize(os.path.join(OUT, n)))


if __name__ == "__main__":
    sys.exit(main())
