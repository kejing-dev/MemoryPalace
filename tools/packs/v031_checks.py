"""validate.py checks for the v0.3.1 collections.

For every generated number the build logged (.cache/facts/<id>.json):
  1. the card (front, back) exists in that collection's volume files,
  2. the shown value is re-derived from the raw value with the same rounding rule,
  3. the raw value is present for that entity in the cached source data (Wikidata query result,
     BSC5 catalogue, UNESCO list),
  4. after removing the shown values, the card back contains no other digits.
Plus: elements = 118 elements, one per atomic number, arranged by period, symbols = PubChem;
currencies: every code is an ISO 4217 code (or Wikidata's for a non-ISO entity).
"""
import gzip
import json
import os
import re
from decimal import Decimal

import wd
from common import SRC

FACTS = ["peaks", "rivers", "lakes-deserts", "whc-china", "events", "paintings", "discoveries", "nobel",
         "constellations", "animals", "provinces-alias", "si-units"]


def walk_cards(tree):
    for n in tree:
        if "k" in n:
            yield from walk_cards(n["k"])
        else:
            yield n["f"], n["b"]


def cards_of(out, col):
    cards = set()
    for v in col["volumes"]:
        with open(os.path.join(out, v["file"]), encoding="utf-8") as f:
            cards |= set(walk_cards(json.load(f)["tree"]))
    return cards


def expected_shown(cid, row):
    raw = row["raw"]
    if cid in ("peaks", "rivers"):
        return [str(wd.fmt_int(raw[0]))]
    if cid == "lakes-deserts":
        x = Decimal(raw[0])
        return ["%d万" % wd.fmt_int(x / 10000)] if x >= 100000 else [str(wd.fmt_int(raw[0]))]
    if cid in ("events", "paintings", "discoveries", "nobel", "whc-china"):
        if row["src"] == "unesco":
            return [raw[0]]
        return [str(wd.year_of(raw[0]))]
    if cid == "constellations":
        from build_nature import combined_mag
        return [raw[0] if len(raw) == 1 else combined_mag(raw)]
    if cid == "si-units":
        from build_common import fmt_quantity
        return [fmt_quantity(raw[0])]
    if cid == "provinces-alias":
        w = Decimal(raw[0]) / 10000
        return ["%.1f" % w] if w >= 1 else [str(wd.fmt_int(raw[0]))]
    if cid == "animals":
        return []
    raise KeyError(cid)


def display_tokens(cid, shown):
    """How a shown value appears on the card back."""
    out = []
    for s in shown:
        if cid == "lakes-deserts" and s.endswith("万"):
            out.append(s[:-1] + " 万")
        elif cid in ("events", "paintings", "discoveries", "nobel", "whc-china") and s.startswith("-"):
            out.append("前 " + s[1:])
        else:
            out.append(s)
    return out


_cache = {}


def raw_present(row):
    src, q, raw = row["src"], row["qid"], row["raw"]
    if src == "bsc5":
        if "bsc5" not in _cache:
            with gzip.open(os.path.join(SRC, "bsc5", "bsc5.dat.gz"), "rt", encoding="latin-1") as f:
                _cache["bsc5"] = {int(l[0:4]): l for l in f}
        hr = int(q.split()[-1])
        line = _cache["bsc5"].get(hr, "")
        return raw[0] == line[102:107].strip()
    if src == "unesco":
        import build_geo
        u = build_geo.unesco_china().get(q)
        return u is not None and u["date_inscribed"] == raw[0]
    path = os.path.join(wd.WD_CACHE, src + ".json")
    if src not in _cache:
        with open(path, encoding="utf-8") as f:
            _cache[src] = json.load(f)
    rows = _cache[src]
    uri = "http://www.wikidata.org/entity/" + q
    hits = [r for r in rows if uri in r.values()]
    vals = {v for r in hits for v in r.values()}
    return bool(hits) and all(x in vals for x in raw)


def check_elements(out, msgs):
    with open(os.path.join(SRC, "pubchem", "periodictable.json"), encoding="utf-8") as f:
        t = json.load(f)["Table"]
    cols = t["Columns"]["Column"]
    zi, si = cols.index("AtomicNumber"), cols.index("Symbol")
    sym = {int(r["Cell"][zi]): r["Cell"][si] for r in t["Row"]}
    with open(os.path.join(out, "elements-1.json"), encoding="utf-8") as f:
        tree = json.load(f)["tree"]
    seen = {}
    period_of = {}
    for loc in tree:
        for fr, b in walk_cards([loc]):
            z = int(re.match(r"(\d+) 号", fr).group(1))
            if z in seen:
                msgs.append("elements: %d twice" % z)
            seen[z] = b.split()[0]
            period_of[z] = loc["t"]
    if sorted(seen) != list(range(1, 119)):
        msgs.append("elements: atomic numbers are not exactly 1..118")
    for z, s in seen.items():
        if sym.get(z) != s:
            msgs.append("elements: %d symbol %s != PubChem %s" % (z, s, sym.get(z)))
    # period of each element from its atomic number (noble gas boundaries)
    bounds = [2, 10, 18, 36, 54, 86, 118]
    for z, title in period_of.items():
        p = next(i for i, b in enumerate(bounds, 1) if z <= b)
        want = ("镧系" if 57 <= z <= 71 else "锕系" if 89 <= z <= 103 else "第 %d 周期" % p)
        if title != want:
            msgs.append("elements: %d in locus %s, expected %s" % (z, title, want))


def check_currencies(out, col, msgs):
    import xml.etree.ElementTree as ET
    root = ET.parse(os.path.join(SRC, "iso4217", "list-one.xml")).getroot()
    codes = {e.findtext("Ccy") for e in root.iter("CcyNtry")}
    for f, b in cards_of(out, col):
        for code in re.findall(r"\b[A-Z]{3}\b", b):
            if code not in codes and f != "巴勒斯坦":
                msgs.append("currencies: %s code %s not in ISO 4217" % (f, code))
        if not re.search(r"\b[A-Z]{3}\b", b):
            msgs.append("currencies: %s has no ISO code: %s" % (f, b))


def run(out, cols):
    msgs = []
    by_id = {c["id"]: c for c in cols}
    for cid in FACTS:
        col = by_id[cid]
        cards = cards_of(out, col)
        with open(os.path.join(wd.FACTS_DIR, cid + ".json"), encoding="utf-8") as f:
            rows = json.load(f)
        logged = {(r["f"], r["b"]) for r in rows}
        for r in rows:
            if (r["f"], r["b"]) not in cards:
                msgs.append("%s: logged card not in pack: %s → %s" % (cid, r["f"], r["b"]))
            exp = expected_shown(cid, r)
            if exp and exp != r["shown"]:
                msgs.append("%s: %s shown %s != recomputed %s from raw %s" % (cid, r["f"], r["shown"], exp, r["raw"]))
            if not raw_present(r):
                msgs.append("%s: %s raw %s not found in source cache %s/%s" % (cid, r["f"], r["raw"], r["src"], r["qid"]))
            rest = r["f"] if cid == "nobel" else r["b"]  # nobel: the year is on the front
            for tok in display_tokens(cid, r["shown"]):
                if tok not in rest:
                    msgs.append("%s: %s card %r lacks shown value %s" % (cid, r["f"], rest, tok))
                rest = rest.replace(tok, "", 1)
            if cid == "constellations":  # star designations (γ2 Nor, 46 LMi) carry digits
                rest = rest.rsplit(" · ", 1)[-1]
            rest = re.sub(r"\d+号", "", rest)  # probe names: 旅行者2号
            if re.search(r"\d", rest):
                msgs.append("%s: %s back has unlogged digits: %r" % (cid, r["f"], r["b"]))
        # every card of these collections that shows a digit must be logged
        for f, b in cards:
            if re.search(r"\d", b) and (f, b) not in logged and cid not in ("si-units",):
                msgs.append("%s: card with a number but no fact log: %s → %s" % (cid, f, b))
    from build import NEW_V031
    for cid in NEW_V031:
        fronts = {}
        for f, b in cards_of(out, by_id[cid]):
            if f in fronts:
                msgs.append("%s: same front twice with different backs: %s" % (cid, f))
            fronts[f] = b
    check_elements(out, msgs)
    check_currencies(out, by_id["currencies"], msgs)
    return msgs
