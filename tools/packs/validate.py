"""Validate app/src/main/assets/packs/ and write chars.txt.

Run: .cache/venv/Scripts/python.exe -I validate.py
Exit code 0 = all checks passed.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from common import OUT, MAX_LOCI, MAX_KIDS, MAX_CARDS, MAX_FRONT, MAX_BACK  # noqa: E402

from build import CATEGORY_ORDER, ORDER, NEW_V031  # noqa: E402
import v031_checks  # noqa: E402

CATEGORIES = set(CATEGORY_ORDER)
POETRY = {"tang300", "song300", "sanzijing", "qianziwen", "lunyu"}
ID_RE = re.compile(r"^[a-z0-9-]+$")
GROUP_TITLE_SOFT = 16


def is_cjk(ch):
    o = ord(ch)
    return (0x3400 <= o <= 0x4DBF or 0x4E00 <= o <= 0x9FFF or 0xF900 <= o <= 0xFAFF
            or 0x20000 <= o <= 0x3134F)


errors, warnings = [], []


def err(msg):
    errors.append(msg)


def walk(node, depth, path, stats, texts):
    """depth 1 = locus."""
    if not isinstance(node, dict):
        err("%s: node is not an object" % path)
        return
    if "k" in node:
        if set(node) - {"t", "k", "a"}:
            err("%s: unexpected keys %s" % (path, sorted(node)))
        t = node.get("t")
        if not isinstance(t, str) or not t.strip():
            err("%s: empty group title" % path)
            t = "?"
        if len(t) > GROUP_TITLE_SOFT:
            stats["long_titles"].append(t)
        texts.append(t)
        if "a" in node:
            if not isinstance(node["a"], str) or not node["a"].strip():
                err("%s: empty author" % path)
            texts.append(node["a"])
        kids = node["k"]
        if not isinstance(kids, list) or not kids:
            err("%s/%s: group without children" % (path, t))
            return
        if len(kids) > MAX_KIDS:
            err("%s/%s: %d children > %d" % (path, t, len(kids), MAX_KIDS))
        if depth >= 3:
            err("%s/%s: group at depth %d (max depth 3 incl. cards)" % (path, t, depth))
        for i, k in enumerate(kids):
            walk(k, depth + 1, "%s/%s" % (path, t), stats, texts)
    elif "f" in node or "b" in node:
        if set(node) != {"f", "b"}:
            err("%s: card keys %s" % (path, sorted(node)))
            return
        f, b = node["f"], node["b"]
        if depth < 2:
            err("%s: card at locus level" % path)
        if depth > 3:
            err("%s: card too deep (%d)" % (path, depth))
        for name, v, lim in (("front", f, MAX_FRONT), ("back", b, MAX_BACK)):
            if not isinstance(v, str) or not v.strip():
                err("%s: empty %s" % (path, name))
            elif len(v) > lim:
                err("%s: %s %d chars > %d: %s" % (path, name, len(v), lim, v))
            elif v != v.strip():
                err("%s: %s has outer whitespace" % (path, name))
        stats["cards"] += 1
        texts.extend([f, b])
    else:
        err("%s: neither group nor card" % path)


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    from opencc import OpenCC
    cc = OpenCC("t2s")

    with open(os.path.join(OUT, "index.json"), "rb") as fb:
        raw = fb.read()
    if raw.startswith(b"\xef\xbb\xbf"):
        err("index.json has BOM")
    index = json.loads(raw.decode("utf-8"))
    if index.get("version") != 1:
        err("index version != 1")
    cols = index["collections"]
    ids = [c["id"] for c in cols]
    if ids != ORDER:
        err("collection order %s != %s" % (ids, ORDER))
    cat_idx = [CATEGORY_ORDER.index(c["category"]) for c in cols if c["category"] in CATEGORIES]
    if cat_idx != sorted(cat_idx):
        err("collections not ordered by category %s" % CATEGORY_ORDER)
    vol_ids, files_seen = set(), set()
    all_chars = set()
    total_cards = 0
    trad_hits = []
    for c in cols:
        for key in ("id", "category", "zh", "en", "structureZh", "structureEn", "source", "license"):
            if not isinstance(c.get(key), str) or not c[key].strip():
                err("%s: missing %s" % (c.get("id"), key))
        if c["category"] not in CATEGORIES:
            err("%s: bad category" % c["id"])
        all_chars.update(c["zh"] + c["structureZh"] + c["source"] + c["license"])
        if c["generated"]:
            if c["volumes"]:
                err("%s: generated collection with volumes" % c["id"])
            continue
        col_cards = 0
        for v in c["volumes"]:
            vid = v["id"]
            if not ID_RE.match(vid):
                err("bad volume id %s" % vid)
            if vid in vol_ids:
                err("duplicate volume id %s" % vid)
            vol_ids.add(vid)
            if v["file"] != vid + ".json":
                err("%s: file name mismatch" % vid)
            files_seen.add(v["file"])
            with open(os.path.join(OUT, v["file"]), "rb") as fb:
                vraw = fb.read()
            if vraw.startswith(b"\xef\xbb\xbf"):
                err("%s has BOM" % v["file"])
            vol = json.loads(vraw.decode("utf-8"))
            if vol.get("id") != vid or vol.get("zh") != v["zh"] or vol.get("en") != v["en"]:
                err("%s: header mismatch with index" % vid)
            tree = vol["tree"]
            if len(tree) > MAX_LOCI:
                err("%s: %d loci > %d" % (vid, len(tree), MAX_LOCI))
            if len(tree) != v["loci"]:
                err("%s: loci count %d != index %d" % (vid, len(tree), v["loci"]))
            stats = {"cards": 0, "long_titles": []}
            texts = [vol["zh"]]
            for loc in tree:
                if "k" not in loc:
                    err("%s: top-level node is not a locus group" % vid)
                walk(loc, 1, vid, stats, texts)
            if stats["cards"] > MAX_CARDS:
                err("%s: %d cards > %d" % (vid, stats["cards"], MAX_CARDS))
            if stats["cards"] != v["cards"]:
                err("%s: card count %d != index %d" % (vid, stats["cards"], v["cards"]))
            if stats["long_titles"]:
                warnings.append("%s: %d group titles > %d chars (e.g. %s)" % (
                    vid, len(stats["long_titles"]), GROUP_TITLE_SOFT, stats["long_titles"][0]))
            col_cards += stats["cards"]
            for t in texts:
                all_chars.update(t)
                if c["id"] in POETRY and cc.convert(t) != t:
                    trad_hits.append((vid, t, cc.convert(t)))
        if col_cards != c["cards"]:
            err("%s: collection cards %d != index %d" % (c["id"], col_cards, c["cards"]))
        total_cards += col_cards
    on_disk = {f for f in os.listdir(OUT) if f.endswith(".json") and f != "index.json"}
    if on_disk != files_seen:
        err("orphan/missing volume files: %s" % sorted(on_disk ^ files_seen))
    for vid, t, t2 in trad_hits:
        err("traditional chars remain in %s: %s -> %s" % (vid, t, t2))
    # v0.3.1: generated numbers vs raw data, elements completeness, per-collection minimums
    new_cards = 0
    for c in cols:
        if c["id"] in NEW_V031:
            new_cards += c["cards"]
            if not (c.get("source") and c.get("license")):
                err("%s: source/license missing" % c["id"])
    if len([c for c in cols if c["id"] in NEW_V031]) < 12:
        err("fewer than 12 new collections")
    if new_cards < 1500:
        err("new cards %d < 1500" % new_cards)
    print("v0.3.1 new collections: %d, new cards: %d" % (len(NEW_V031), new_cards))
    for msg in v031_checks.run(OUT, cols):
        err(msg)

    size = sum(os.path.getsize(os.path.join(OUT, f)) for f in os.listdir(OUT))
    cjk = sorted(ch for ch in all_chars if is_cjk(ch))
    other = sorted(ch for ch in all_chars if not is_cjk(ch) and ord(ch) > 0x7F)
    with open(os.path.join(HERE, "chars.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("".join(cjk) + "\n")
    print("collections: %d, volumes: %d, total cards: %d" % (len(cols), len(vol_ids), total_cards))
    print("assets/packs size: %d bytes (%.2f MB)" % (size, size / 1048576))
    print("distinct CJK chars: %d -> %s" % (len(cjk), os.path.join(HERE, "chars.txt")))
    print("non-ASCII non-CJK symbols used:", "".join(other))
    if size >= 3 * 1048576:
        err("assets/packs exceeds 3 MB")
    for w in warnings:
        print("WARN", w)
    for e in errors:
        print("ERROR", e)
    print("OK" if not errors else "FAILED (%d errors)" % len(errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
