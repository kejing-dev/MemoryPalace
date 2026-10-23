"""Memory Palace icon: the PICO 3D icon layers and the 512 export, drawn from the same shapes as the
adaptive icon (`res/drawable/ic_launcher_foreground.xml` on `@color/ic_launcher_background`).

    python tools/make_icon.py [export.png]

Layer 0: the palace-night indigo tile. Layer 1: a palace eave over a porcelain plaque with a brass pin,
and the amber thread of memory linking three lantern dots. sdf_N = the layer's alpha in white.
Coordinates below are the vector's 108-unit viewport.
"""
import os
import sys
from PIL import Image, ImageDraw

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".."))
RES = os.path.join(ROOT, "app", "src", "main", "res")
S = 1024
K = S / 108.0
INDIGO = (59, 42, 107, 255)
PORCELAIN = (244, 240, 232, 255)
AMBER = (255, 182, 72, 255)
INK = (31, 36, 48, 255)
BRASS = (201, 163, 90, 255)


def quad(p0, p1, p2, n=24):
    """Points of a quadratic Bezier, viewport units."""
    out = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t * t * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t * t * p2[1]
        out.append((x, y))
    return out


def px(pts):
    return [(x * K, y * K) for x, y in pts]


def tile():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(im).rounded_rectangle((40, 40, S - 40, S - 40), radius=220, fill=INDIGO)
    return im


def front():
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    eave = quad((22, 38), (36, 35), (54, 23)) + quad((54, 23), (72, 35), (86, 38))[1:] + [(81, 41), (27, 41)]
    d.polygon(px(eave), fill=PORCELAIN)
    d.rounded_rectangle((32 * K, 45 * K, 76 * K, 70 * K), radius=3 * K, fill=PORCELAIN)
    d.rounded_rectangle((32 * K, 45 * K, 37.5 * K, 70 * K), radius=3 * K, fill=AMBER)
    d.rectangle((35 * K, 45 * K, 37.5 * K, 70 * K), fill=AMBER)
    d.rectangle((43 * K, 54 * K, 67 * K, 57 * K), fill=INK)
    d.rectangle((43 * K, 61 * K, 59 * K, 64 * K), fill=INK)
    d.ellipse(((54 - 3.5) * K, (43.5 - 3.5) * K, (54 + 3.5) * K, (43.5 + 3.5) * K), fill=BRASS)
    thread = quad((30, 82), (42, 74), (54, 80)) + quad((54, 80), (66, 86), (78, 78))[1:]
    d.line(px(thread), fill=AMBER, width=int(2.4 * K), joint="curve")
    for cx, cy in ((30, 82), (54, 80), (78, 78)):
        d.ellipse(((cx - 4.5) * K, (cy - 4.5) * K, (cx + 4.5) * K, (cy + 4.5) * K), fill=AMBER)
    return im


def alpha_white(im):
    w = Image.new("RGBA", im.size, (255, 255, 255, 0))
    w.putalpha(im.split()[3])
    return w


def main():
    l0, l1 = tile(), front()
    draw = os.path.join(RES, "drawable")
    l0.save(os.path.join(draw, "icon_3d_layer_0.png"))
    l1.save(os.path.join(draw, "icon_3d_layer_1.png"))
    alpha_white(l0).save(os.path.join(draw, "icon_3d_sdf_0.png"))
    alpha_white(l1).save(os.path.join(draw, "icon_3d_sdf_1.png"))
    if len(sys.argv) > 1:
        Image.alpha_composite(l0, l1).resize((512, 512), Image.LANCZOS).save(sys.argv[1])
    print("icon layers written")


if __name__ == "__main__":
    main()
