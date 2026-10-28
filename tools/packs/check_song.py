"""Diff 宋词三百首.json against the full 全宋词 (chinese-poetry ci.song.*.json).

For each ci in the 300 list, finds the best-matching text by the same author and prints
character-level differences for human review. Not part of the build.
"""
import difflib
import glob
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, ".cache", "src")


def norm(s):
    return re.sub(r"[^㐀-鿿\U00020000-\U0003134F]", "", s)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    full = {}
    for f in glob.glob(os.path.join(SRC, "ci", "ci.song.*.json")):
        for p in json.load(open(f, encoding="utf-8")):
            full.setdefault(p["author"], []).append(norm("".join(p["paragraphs"])))
    s300 = json.load(open(os.path.join(SRC, "宋词_宋词三百首.json"), encoding="utf-8"))
    ndiff = 0
    for p in s300:
        a = norm("".join(p["paragraphs"]))
        cands = full.get(p["author"], [])
        best, ratio = None, 0
        for c in cands:
            r = difflib.SequenceMatcher(None, a, c, autojunk=False).quick_ratio()
            if r > ratio - 0.05:
                r = difflib.SequenceMatcher(None, a, c, autojunk=False).ratio()
                if r > ratio:
                    best, ratio = c, r
        if best is None or ratio < 0.6:
            print("NO MATCH", p["author"], p["rhythmic"], round(ratio, 2))
            continue
        if best == a:
            continue
        ndiff += 1
        sm = difflib.SequenceMatcher(None, a, best, autojunk=False)
        ops = []
        for tag, i1, i2, j1, j2 in sm.get_opcodes():
            if tag != "equal":
                ctx = a[max(0, i1 - 4):i2 + 4]
                ops.append("%s[%s→%s]@%s" % (tag[0], a[i1:i2], best[j1:j2], ctx))
        print("%s %s (%.2f): %s" % (p["author"], p["rhythmic"], ratio, "  ".join(ops[:8])))
    print("poems with differences:", ndiff)


if __name__ == "__main__":
    main()
