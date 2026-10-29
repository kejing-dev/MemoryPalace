"""Render every res/drawable/ic_*.xml (VectorDrawable) into one contact sheet, for the icon self-check (profile D23).

    python tools/render_icons.py [out.png] [drawable dir]

Pure matplotlib: a small SVG path parser (M L H V C S Q T A Z, absolute and relative; arcs become cubic Béziers),
fillType evenOdd / nonZero, strokes, and <group> translate / scale. Each icon is drawn white on dark (as on the
panels) at 96 px and at the real on-panel size (22 dp ~ 33 px), with its file name.
"""
import math
import os
import re
import sys
import xml.etree.ElementTree as ET

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import PathPatch
from matplotlib.path import Path

A = "{http://schemas.android.com/apk/res/android}"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
RES = os.path.join(ROOT, "app", "src", "main", "res", "drawable")
if len(sys.argv) > 2:
    RES = sys.argv[2]


def tokens(d):
    for m in re.finditer(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?", d):
        yield m.group(0)


def arc_to_beziers(x1, y1, rx, ry, phi, fa, fs, x2, y2):
    if rx == 0 or ry == 0:
        return [(x1, y1, x2, y2, x2, y2)]
    phi = math.radians(phi)
    c, s = math.cos(phi), math.sin(phi)
    dx, dy = (x1 - x2) / 2, (y1 - y2) / 2
    x1p, y1p = c * dx + s * dy, -s * dx + c * dy
    rx, ry = abs(rx), abs(ry)
    lam = x1p ** 2 / rx ** 2 + y1p ** 2 / ry ** 2
    if lam > 1:
        rx, ry = rx * math.sqrt(lam), ry * math.sqrt(lam)
    num = rx ** 2 * ry ** 2 - rx ** 2 * y1p ** 2 - ry ** 2 * x1p ** 2
    den = rx ** 2 * y1p ** 2 + ry ** 2 * x1p ** 2
    k = math.sqrt(max(0, num / den)) if den else 0
    if fa == fs:
        k = -k
    cxp, cyp = k * rx * y1p / ry, -k * ry * x1p / rx
    cx, cy = c * cxp - s * cyp + (x1 + x2) / 2, s * cxp + c * cyp + (y1 + y2) / 2

    def ang(ux, uy, vx, vy):
        a = math.atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        return a

    t1 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dt = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not fs and dt > 0:
        dt -= 2 * math.pi
    elif fs and dt < 0:
        dt += 2 * math.pi
    n = max(1, int(math.ceil(abs(dt) / (math.pi / 2))))
    d = dt / n
    out = []
    for i in range(n):
        a1, a2 = t1 + i * d, t1 + (i + 1) * d
        al = 4 / 3 * math.tan((a2 - a1) / 4)

        def pt(a):
            return cx + rx * math.cos(a) * c - ry * math.sin(a) * s, cy + rx * math.cos(a) * s + ry * math.sin(a) * c

        def dv(a):
            return -rx * math.sin(a) * c - ry * math.cos(a) * s, -rx * math.sin(a) * s + ry * math.cos(a) * c

        p1, p2 = pt(a1), pt(a2)
        d1, d2 = dv(a1), dv(a2)
        out.append((p1[0] + al * d1[0], p1[1] + al * d1[1], p2[0] - al * d2[0], p2[1] - al * d2[1], p2[0], p2[1]))
    return out


def parse(d):
    verts, codes = [], []
    t = list(tokens(d))
    i, cmd = 0, None
    x = y = sx = sy = 0.0
    lc = lq = None  # last control points for S / T

    def num():
        nonlocal i
        v = float(t[i])
        i += 1
        return v

    while i < len(t):
        if re.fullmatch(r"[A-Za-z]", t[i]):
            cmd = t[i]
            i += 1
        rel = cmd.islower()
        C = cmd.upper()
        ox, oy = (x, y) if rel else (0.0, 0.0)
        if C == "Z":
            codes.append(Path.CLOSEPOLY)
            verts.append((sx, sy))
            x, y = sx, sy
            lc = lq = None
            continue
        if C == "M":
            x, y = ox + num(), oy + num()
            sx, sy = x, y
            verts.append((x, y))
            codes.append(Path.MOVETO)
            cmd = "l" if rel else "L"
            lc = lq = None
        elif C in "LHV":
            if C == "L":
                x, y = ox + num(), oy + num()
            elif C == "H":
                x = ox + num() if rel else num()
            else:
                y = oy + num() if rel else num()
            verts.append((x, y))
            codes.append(Path.LINETO)
            lc = lq = None
        elif C in "CS":
            if C == "C":
                c1 = (ox + num(), oy + num())
            else:
                c1 = (2 * x - lc[0], 2 * y - lc[1]) if lc else (x, y)
            c2 = (ox + num(), oy + num())
            x, y = ox + num(), oy + num()
            verts += [c1, c2, (x, y)]
            codes += [Path.CURVE4] * 3
            lc, lq = c2, None
        elif C in "QT":
            if C == "Q":
                q = (ox + num(), oy + num())
            else:
                q = (2 * x - lq[0], 2 * y - lq[1]) if lq else (x, y)
            x, y = ox + num(), oy + num()
            verts += [q, (x, y)]
            codes += [Path.CURVE3] * 2
            lq, lc = q, None
        elif C == "A":
            rx, ry, phi, fa, fs = num(), num(), num(), num(), num()
            nx, ny = ox + num(), oy + num()
            for b in arc_to_beziers(x, y, rx, ry, phi, int(fa), int(fs), nx, ny):
                verts += [(b[0], b[1]), (b[2], b[3]), (b[4], b[5])]
                codes += [Path.CURVE4] * 3
            x, y = nx, ny
            lc = lq = None
    return verts, codes


def color(v):
    if not v or v.startswith("@") or v.startswith("?"):
        return (1, 1, 1, 1)
    v = v.lstrip("#")
    if len(v) == 6:
        v = "FF" + v
    a, r, g, b = (int(v[k:k + 2], 16) / 255 for k in (0, 2, 4, 6))
    return (r, g, b, a)


def draw(ax, path_file):
    root = ET.parse(path_file).getroot()
    vw, vh = float(root.get(A + "viewportWidth")), float(root.get(A + "viewportHeight"))

    def walk(node, tx):
        for ch in node:
            if ch.tag == "group":
                g = (float(ch.get(A + "translateX", 0)), float(ch.get(A + "translateY", 0)), float(ch.get(A + "scaleX", 1)), float(ch.get(A + "scaleY", 1)))
                walk(ch, lambda p, g=g, tx=tx: tx((p[0] * g[2] + g[0], p[1] * g[3] + g[1])))
            elif ch.tag == "path":
                v, c = parse(ch.get(A + "pathData"))
                if not v:
                    continue
                v = [tx(p) for p in v]
                p = Path(v, c)
                fill = ch.get(A + "fillColor")
                stroke = ch.get(A + "strokeColor")
                fa = float(ch.get(A + "fillAlpha", 1))
                if fill and fill not in ("#00000000",):
                    col = color(fill)
                    # matplotlib fills even-odd for every path; nonZero holes drawn the same way here
                    ax.add_patch(PathPatch(p, facecolor=col[:3], alpha=col[3] * fa, edgecolor="none"))
                if stroke:
                    w = float(ch.get(A + "strokeWidth", 1))
                    col = color(stroke)
                    ax.add_patch(PathPatch(p, facecolor="none", edgecolor=col[:3], alpha=col[3], linewidth=w * ax.figure.dpi_px_per_unit / 1.0,
                                           capstyle=("round" if ch.get(A + "strokeLineCap") == "round" else "butt"), joinstyle=("round" if ch.get(A + "strokeLineJoin") == "round" else "miter")))

    walk(root, lambda p: p)
    ax.set_xlim(0, vw)
    ax.set_ylim(vh, 0)
    ax.set_aspect("equal")
    ax.axis("off")
    return vw


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, ".scratch", "icons", "sheet.png")
    files = sorted(f for f in os.listdir(RES) if f.startswith("ic_") and f.endswith(".xml") and f != "ic_launcher_foreground.xml")
    cols = 6
    rows = math.ceil(len(files) / cols)
    fig = plt.figure(figsize=(cols * 1.8, rows * 1.6), dpi=100, facecolor="#2b2b2e")
    for k, f in enumerate(files):
        r, c = divmod(k, cols)
        for big in (True, False):
            size = 0.9 if big else 0.33
            left = (c * 1.8 + (0.1 if big else 1.15)) / (cols * 1.8)
            bottom = 1 - (r * 1.6 + 0.15 + size) / (rows * 1.6)
            ax = fig.add_axes([left, bottom, size / (cols * 1.8), size / (rows * 1.6)])
            ax.set_facecolor("#2b2b2e")
            fig.dpi_px_per_unit = 1
            vw = float(ET.parse(os.path.join(RES, f)).getroot().get(A + "viewportWidth"))
            ax.figure.dpi_px_per_unit = size * 100 / vw * 0.72
            draw(ax, os.path.join(RES, f))
        fig.text((c * 1.8 + 0.9) / (cols * 1.8), 1 - (r * 1.6 + 1.35) / (rows * 1.6), f[:-4], color="white", ha="center", fontsize=8)
    os.makedirs(os.path.dirname(out), exist_ok=True)
    fig.savefig(out, facecolor=fig.get_facecolor())
    print(out, len(files), "icons")


if __name__ == "__main__":
    main()
