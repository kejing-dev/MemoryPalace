"""Shared helpers for the Memory Palace content-pack generators.

Node format (see README.md):
  group = {"t": title, "k": [children], "a": author (optional)}
  card  = {"f": front, "b": back}
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.join(HERE, ".cache")
SRC = os.path.join(CACHE, "src")
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "packs"))

MAX_LOCI = 20
MAX_KIDS = 8
MAX_CARDS = 120
MAX_FRONT = 40
MAX_BACK = 60

CN_NUM = "一二三四五六七八九十"


def cn_num(n):
    if n <= 10:
        return CN_NUM[n - 1]
    if n < 20:
        return "十" + CN_NUM[n - 11]
    t, u = divmod(n, 10)
    return CN_NUM[t - 1] + "十" + (CN_NUM[u - 1] if u else "")


def card(f, b):
    return {"f": f, "b": b}


def group(t, kids, a=None):
    g = {"t": t, "k": kids}
    if a:
        g["a"] = a
    return g


def count_cards(node):
    if "k" in node:
        return sum(count_cards(k) for k in node["k"])
    return 1


def chunk(seq, n):
    return [seq[i:i + n] for i in range(0, len(seq), n)]


def even_chunks(seq, max_n):
    """Split seq into the fewest chunks of size <= max_n, as even as possible."""
    if not seq:
        return []
    k = -(-len(seq) // max_n)
    base, extra = divmod(len(seq), k)
    out, i = [], 0
    for j in range(k):
        size = base + (1 if j < extra else 0)
        out.append(seq[i:i + size])
        i += size
    return out


def pack_loci(loci, max_loci=MAX_LOCI, max_cards=MAX_CARDS):
    """Greedy sequential packing of loci into volumes (a locus is never split)."""
    vols, cur, cur_cards = [], [], 0
    for loc in loci:
        c = count_cards(loc)
        if c > max_cards:
            raise ValueError("locus too big: %s (%d cards)" % (loc["t"], c))
        if cur and (len(cur) >= max_loci or cur_cards + c > max_cards):
            vols.append(cur)
            cur, cur_cards = [], 0
        cur.append(loc)
        cur_cards += c
    if cur:
        vols.append(cur)
    return vols


def balanced_pack(loci, max_loci=MAX_LOCI, max_cards=MAX_CARDS):
    """Contiguous partition into the minimum number of volumes (as found by greedy),
    choosing split points by DP to minimise the squared deviation of volume card
    counts from the mean. Loci are never split."""
    greedy = pack_loci(loci, max_loci, max_cards)
    k = len(greedy)
    if k <= 1:
        return greedy
    sizes = [count_cards(l) for l in loci]
    n = len(sizes)
    pre = [0]
    for x in sizes:
        pre.append(pre[-1] + x)
    mean = pre[-1] / k
    INF = float("inf")
    # best[j][i]: min cost to put first i loci into j volumes
    best = [[INF] * (n + 1) for _ in range(k + 1)]
    back = [[-1] * (n + 1) for _ in range(k + 1)]
    best[0][0] = 0.0
    for j in range(1, k + 1):
        for i in range(1, n + 1):
            for s in range(max(0, i - max_loci), i):
                c = pre[i] - pre[s]
                if c > max_cards or best[j - 1][s] == INF:
                    continue
                v = best[j - 1][s] + (c - mean) ** 2
                if v < best[j][i]:
                    best[j][i] = v
                    back[j][i] = s
    assert best[k][n] < INF
    out, i = [], n
    for j in range(k, 0, -1):
        s = back[j][i]
        out.append(loci[s:i])
        i = s
    out.reverse()
    return out


def write_json(path, obj):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, separators=(",", ":"))


def make_collection(cid, category, zh, en, structure_zh, structure_en, source, license_,
                    volumes_loci, vol_suffix_zh=None, vol_suffix_en=None):
    """volumes_loci: list of lists of loci. Writes volume files, returns index entry."""
    n = len(volumes_loci)
    vols = []
    total = 0
    for i, loci in enumerate(volumes_loci, 1):
        vid = "%s-%d" % (cid, i)
        if n == 1:
            vzh, ven = zh, en
        else:
            vzh = "%s · 卷%s" % (zh, cn_num(i))
            ven = "%s · Vol. %d" % (en, i)
        if vol_suffix_zh:
            vzh += " " + vol_suffix_zh[i - 1]
            ven += " " + vol_suffix_en[i - 1]
        cards = sum(count_cards(l) for l in loci)
        total += cards
        write_json(os.path.join(OUT, vid + ".json"), {"id": vid, "zh": vzh, "en": ven, "tree": loci})
        vols.append({"id": vid, "zh": vzh, "en": ven, "file": vid + ".json", "loci": len(loci), "cards": cards})
    return {"id": cid, "category": category, "zh": zh, "en": en,
            "structureZh": structure_zh, "structureEn": structure_en,
            "source": source, "license": license_, "generated": False,
            "cards": total, "volumes": vols}


PUNCT_STRIP = re.compile(r"[，。？！；、：:,.?!;“”‘’\"'《》〈〉「」『』（）()·\s]")


def strip_punct(s):
    return PUNCT_STRIP.sub("", s)
