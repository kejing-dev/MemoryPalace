"""v0.3.1 nature collections: constellations (Wikidata + Yale Bright Star Catalogue),
animals (Wikidata taxonomy), human body (hand-written textbook facts, see manual_v031.py)."""
import collections
import gzip
import os
import re
import sys

from opencc import OpenCC

import manual_v031 as M
import wd
from common import SRC, MAX_KIDS, card, group, even_chunks, balanced_pack, make_collection

cc = OpenCC("t2s")
CC0 = "CC0（Wikidata）"


# ---------------------------------------------------------------- constellations
GREEK = {"Alp": "α", "Bet": "β", "Gam": "γ", "Del": "δ", "Eps": "ε", "Zet": "ζ", "Eta": "η", "The": "θ",
         "Iot": "ι", "Kap": "κ", "Lam": "λ", "Mu": "μ", "Nu": "ν", "Xi": "ξ", "Omi": "ο", "Pi": "π",
         "Rho": "ρ", "Sig": "σ", "Tau": "τ", "Ups": "υ", "Phi": "φ", "Chi": "χ", "Psi": "ψ", "Ome": "ω"}


def bsc5():
    """Yale Bright Star Catalogue 5 (NASA ADC, public domain). Rows with a constellation."""
    out = []
    with gzip.open(os.path.join(SRC, "bsc5", "bsc5.dat.gz"), "rt", encoding="latin-1") as f:
        for line in f:
            line = line.rstrip("\n").ljust(197)
            hr = line[0:4].strip()
            name = line[4:14]
            con = name[7:10].strip()
            vmag = line[102:107].strip()
            if not con or not vmag or not line[75:77].strip():
                continue
            ra_h = int(line[75:77]) + int(line[77:79]) / 60 + float(line[79:83]) / 3600
            dec = int(line[84:86]) + int(line[86:88]) / 60 + int(line[88:90]) / 3600
            if line[83] == "-":
                dec = -dec
            bayer = name[3:6].strip()
            sup = name[6].strip()
            out.append({"hr": int(hr), "con": con, "v": vmag, "ra": ra_h, "dec": dec,
                        "bayer": GREEK.get(bayer), "sup": sup, "flam": name[0:3].strip(), "line": line[:110]})
    return out


def sep_arcsec(a, b):
    import math
    ra1, ra2 = math.radians(a["ra"] * 15), math.radians(b["ra"] * 15)
    d1, d2 = math.radians(a["dec"]), math.radians(b["dec"])
    c = math.sin(d1) * math.sin(d2) + math.cos(d1) * math.cos(d2) * math.cos(ra1 - ra2)
    return math.degrees(math.acos(max(-1.0, min(1.0, c)))) * 3600


def combined_mag(vs):
    """Combined V magnitude of components, 2 decimals (string)."""
    import math
    m = -2.5 * math.log10(sum(10 ** (-0.4 * float(v)) for v in vs))
    return "%.2f" % (m + 0.0)


SKY = [  # (title, en, test)
    ("北天拱极星座", "N circumpolar", lambda ra, dec: dec >= 60),
    ("南天拱极星座", "S circumpolar", lambda ra, dec: dec <= -50),
    ("春季星空", "spring", lambda ra, dec: 9 <= ra < 15),
    ("夏季星空", "summer", lambda ra, dec: 15 <= ra < 21),
    ("秋季星空", "autumn", lambda ra, dec: ra >= 21 or ra < 3),
    ("冬季星空", "winter", lambda ra, dec: 3 <= ra < 9),
]


def build_constellations():
    facts = wd.FactLog("constellations")
    rows = wd.sparql("constellations", """SELECT ?c ?abbr WHERE {
  ?c wdt:P31/wdt:P279* wd:Q8928 ; wdt:P1813 ?abbr . FILTER(STRLEN(?abbr) = 3) }""")
    abbr = {}
    for r in rows:
        if re.fullmatch(r"[A-Z][A-Za-z]{2}", r["abbr"]):
            abbr.setdefault(r["abbr"], wd.qid(r["c"]))
    assert len(abbr) == 88, (len(abbr), sorted(abbr))
    stars = bsc5()
    # BSC lists the components of close multiples separately (α1/α2 Cru, β Mon A/B/C): combine all
    # entries sharing a Bayer letter (or Flamsteed number) within a constellation: m = -2.5 log10 Σ 10^(-0.4 m_i)
    systems = collections.defaultdict(list)
    for s in stars:
        if s["con"] in abbr:
            key = (s["con"], s["bayer"] or s["flam"] or "HR%d" % s["hr"], "" if s["bayer"] or s["flam"] else s["hr"])
            systems[key].append(s)
    ranked = collections.defaultdict(list)
    for (con, _, _), comps in systems.items():
        comps.sort(key=lambda c: float(c["v"]))
        # only physical close pairs (< 60 arcsec from the primary) are combined; optical pairs such as
        # γ1/γ2 Nor stay separate entries
        close = [c for c in comps if sep_arcsec(comps[0], c) < 60]
        for c in comps:
            if c not in close:
                ranked[con].append((c["v"], [c]))
        ranked[con].append((combined_mag([c["v"] for c in close]) if len(close) > 1 else close[0]["v"], close))
    for con in ranked:
        ranked[con].sort(key=lambda x: x[0])
    assert set(ranked) == set(abbr), set(abbr) - set(ranked)
    hrs = " ".join('"HR %d"' % c[0]["hr"] for con in ranked for _, c in ranked[con][:2])
    starq = wd.sparql("constellation_stars", """SELECT ?s ?code WHERE { VALUES ?code { %s } ?s wdt:P528 ?code . }""" % hrs)
    hr2q = {}
    for r in starq:
        hr2q.setdefault(int(r["code"][3:]), set()).add(wd.qid(r["s"]))
    lb = wd.labels("labels_constellations", set(abbr.values()) | {q for v in hr2q.values() for q in v})
    notes = []
    groups = collections.defaultdict(list)

    def star_text(a, comps):
        s = comps[0]
        qs = sorted(hr2q.get(s["hr"], ()))
        sname = wd.zh_of(lb, qs[0], cc) if len(qs) == 1 else None
        if sname and not re.search(r"[一-鿿]", sname):
            sname = None
        if s["bayer"]:
            desig = s["bayer"] + ("" if len(comps) > 1 else (s["sup"] or ""))
        else:
            desig = s["flam"] or "HR %d" % s["hr"]
        desig = "%s %s" % (desig, a)
        if not sname:
            notes.append("%s: no Chinese star label for HR %d (%s) -> designation only" % (a, s["hr"], desig))
        return "%s（%s）" % (sname, desig) if sname else desig

    for a, q in abbr.items():
        cname = wd.zh_of(lb, q, cc)
        assert cname and cname.endswith("座"), (a, cname)
        (m1, c1), (m2, c2) = ranked[a][0], ranked[a][1]
        star = star_text(a, c1)
        if abs(float(m1) - float(m2)) < 0.015:  # equally bright within BSC precision: name both
            star += " / " + star_text(a, c2)
            notes.append("%s: tie %s %s / %s %s" % (a, c1[0]["line"][4:14], m1, c2[0]["line"][4:14], m2))
        if len(c1) > 1:
            notes.append("%s: combined %d BSC components %s -> %s" % (a, len(c1), [c["v"] for c in c1], m1))
        b = "%s · 最亮星 %s · %s 等" % (a, star, m1)
        if len(b) > 60:
            b = "%s · %s · %s 等" % (a, star, m1)
        s = c1[0]
        facts.add(cname, b, "bsc5", "HR %d" % s["hr"], [c["v"] for c in c1], [m1])
        for title, _, test in SKY:
            if test(s["ra"], s["dec"]):
                groups[title].append((s["ra"], cname, card(cname, b)))
                break
    loci = []
    for title, _, _ in SKY:
        cs = [c for _, _, c in sorted(groups[title], key=lambda x: (x[0] - 9) % 24)]
        parts = even_chunks(cs, MAX_KIDS)
        if len(parts) == 1:
            loci.append(group(title, cs))
        else:
            loci.append(group(title, [group("%s %d" % (title, i), p) for i, p in enumerate(parts, 1)]))
    facts.save()
    entry = make_collection(
        "constellations", "nature", "全天 88 星座", "The 88 Constellations",
        "位置 = 北/南天拱极区与四季星空（按最亮星赤经，北半球晚间）· 子位置 = 每组 ≤8 · 卡 = 星座 → 缩写 · 最亮星 · 视星等",
        "Locus = circumpolar / seasonal sky (by RA of brightest star) · card = constellation → IAU abbr · brightest star · V mag",
        "星座与 IAU 缩写、中文星名：Wikidata；最亮星与 V 星等：耶鲁亮星表第 5 版（BSC5，NASA ADC）",
        "CC0（Wikidata）；BSC5 公有领域（NASA）", balanced_pack(loci))
    entry["_notes"] = notes
    return entry


# ---------------------------------------------------------------- animals
RANK = {"Q37517": "class", "Q36602": "order", "Q35409": "family"}


def load_gbif(names):
    """GBIF Backbone species/match (second source for the class/order/family check), cached."""
    import json
    import urllib.parse
    path = os.path.join(wd.CACHE, "gbif_match.json")
    data = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    todo = [n for n in names if n not in data]
    for n in todo:
        print("  gbif", n, file=sys.stderr)
        data[n] = wd._get("https://api.gbif.org/v1/species/match?strict=true&name=" + urllib.parse.quote(n))
    if todo:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
    return {n: {"class": d.get("class"), "order": d.get("order"), "family": d.get("family")}
            for n, d in data.items()}


def build_animals():
    facts = wd.FactLog("animals")
    names = [n for _, ns in M.ANIMALS for n in ns]
    assert len(names) == len(set(names))
    vals = " ".join('"%s"' % n for n in names)
    rows = wd.sparql("animals", """SELECT ?t ?name ?anc ?rank ?ancname WHERE {
  VALUES ?name { %s }
  ?t wdt:P225 ?name ; wdt:P105 ?r0 . FILTER(?r0 IN (wd:Q7432, wd:Q68947))
  ?t wdt:P171* ?anc . ?anc wdt:P105 ?rank . FILTER(?rank IN (wd:Q37517, wd:Q36602, wd:Q35409))
  ?anc wdt:P225 ?ancname .
}""" % vals)
    tax = collections.defaultdict(lambda: collections.defaultdict(set))
    item = collections.defaultdict(set)
    for r in rows:
        item[r["name"]].add(wd.qid(r["t"]))
        tax[r["name"]][RANK[wd.qid(r["rank"])]].add((wd.qid(r["anc"]), r["ancname"]))
    lb = wd.labels("labels_animals", {q for v in item.values() for q in v} |
                   {q for v in tax.values() for s in v.values() for q, _ in s})
    gbif = load_gbif(names)
    amb = sorted({q for v in tax.values() for s in v.values() if len(s) > 1 for q, _ in s})
    anc = set()
    if amb:
        vv = " ".join("wd:" + q for q in amb)
        for r in wd.sparql("animals_ambiguous_ranks", """SELECT ?a ?b WHERE { VALUES ?a { %s } VALUES ?b { %s }
  ?a wdt:P171+ ?b . }""" % (vv, vv)):
            anc.add((wd.qid(r["a"]), wd.qid(r["b"])))
    al = wd.aliases("aliases_animals", {q for v in item.values() for q in v} |
                    {q for v in tax.values() for s in v.values() for q, _ in s})
    SUF = {"class": "纲", "order": "目", "family": "科"}

    def ok(a, rk):
        return a.endswith(SUF[rk]) and not re.search("[总亚下]" + SUF[rk] + "$", a)

    def rank_name(q, rk):
        """Chinese rank name ending in 纲/目/科 (not 总纲/亚纲…): label, else a zh alias (t2s)."""
        lab = wd.zh_of(lb, q, cc)
        if lab and ok(lab, rk):
            return lab
        for a in [lb.get(q, {}).get(l) for l in wd.ZH_PREF] + al.get(q, []):
            if not a:
                continue
            a = cc.convert(a)
            if ok(a, rk) and re.fullmatch(r"[一-鿿]+", a):
                return a
        return None

    def common_name(q):
        lab = wd.zh_of(lb, q, cc)
        if lab and re.search(r"[一-鿿]", lab):
            return lab
        for a in al.get(q, []):
            if re.fullmatch(r"[一-鿿]+", a):
                return cc.convert(a)
        return None
    out, notes = [], []
    for group_title, ns in M.ANIMALS:
        for n in ns:
            t = tax.get(n)
            if not t or len(item[n]) != 1:
                notes.append("%s: no unique Wikidata taxon (%s) -> dropped" % (n, sorted(item.get(n, ()))))
                continue
            q = next(iter(item[n]))
            g = gbif.get(n, {})
            chosen = {}
            for rk in ("class", "order", "family"):
                cands = sorted(t.get(rk, ()))
                if len(cands) > 1 and g.get(rk):
                    cands = [c for c in cands if c[1] == g[rk]] or cands
                if len(cands) > 1:  # e.g. Teleostei/Actinopteri both ranked class: keep the higher taxon
                    top = [c for c in cands if all(o == c or (o[0], c[0]) in anc for o in cands)]
                    if len(top) == 1:
                        notes.append("%s: %s %s -> higher taxon %s" % (n, rk, [c[1] for c in cands], top[0][1]))
                        cands = top
                if not cands and rk == "class":
                    notes.append("%s: no class in Wikidata tree -> card shows order · family" % n)
                    continue
                if len(cands) != 1:
                    chosen = None
                    notes.append("%s: %s ambiguous/missing %s -> dropped" % (n, rk, cands))
                    break
                chosen[rk] = cands[0]
            if not chosen:
                continue
            zh = {rk: M.TAXON_ZH_FIX.get(lat) or rank_name(cq, rk) for rk, (cq, lat) in chosen.items()}
            name = M.TAXON_ZH_FIX.get(n) or common_name(q)
            for k in [k for k in (n, *[lat for _, lat in chosen.values()]) if k in M.TAXON_ZH_FIX]:
                notes.append("label fix %s -> %s" % (k, M.TAXON_ZH_FIX[k]))
            if not name or not all(zh.values()):
                notes.append("%s: missing Chinese label (%s %s) -> dropped" % (n, name, zh))
                continue
            mism = [rk for rk in chosen if g.get(rk) and g[rk] != chosen[rk][1]]
            if "order" in mism or "family" in mism:
                notes.append("%s: GBIF %s differs from Wikidata %s -> dropped" % (
                    n, {k: g.get(k) for k in mism}, {k: chosen[k][1] for k in mism}))
                continue
            out.append({"sci": n, "q": q, "name": name, "zh": zh, "lat": {k: v[1] for k, v in chosen.items()},
                        "gbif_mism": mism, "group": group_title})
    # loci: by class (Chinese class name); sub-loci: by order, packed to <=8 cards
    by_class = collections.OrderedDict()
    for a in out:
        by_class.setdefault(a["zh"].get("class") or a["group"], []).append(a)
    loci = []
    for cls, animals in by_class.items():
        by_order = collections.OrderedDict()
        for a in animals:
            by_order.setdefault(a["zh"]["order"], []).append(a)
        subs = []
        cur, cur_t = [], []
        for order, ans in by_order.items():  # merge small orders into one sub-locus of <= 8
            for part in even_chunks(ans, MAX_KIDS):
                if cur and len(cur) + len(part) > MAX_KIDS:
                    subs.append(("、".join(cur_t), cur))
                    cur, cur_t = [], []
                cur += part
                if order not in cur_t:
                    cur_t.append(order)
        if cur:
            subs.append(("、".join(cur_t), cur))
        kids = []
        cnt = collections.Counter(t for t, _ in subs)
        seen = collections.Counter()
        for title, ans in subs:
            if cnt[title] > 1:
                seen[title] += 1
                title = "%s %s" % (title, "一二三四五六七八"[seen[title] - 1])
            cards = []
            for a in ans:
                b = " · ".join(a["zh"][k] for k in ("class", "order", "family") if k in a["zh"])
                cards.append(card(a["name"], b))
                facts.add(a["name"], b, "animals", a["q"], [a["lat"][k] for k in ("class", "order", "family")
                                                            if k in a["lat"]], [])
            kids.append(group(title if len(title) <= 16 else title.split("、")[0] + "等", cards))
        for i, part in enumerate(even_chunks(kids, MAX_KIDS), 1):
            loci.append(group(cls if i == 1 else "%s %d" % (cls, i), part))
    facts.save()
    entry = make_collection(
        "animals", "nature", "动物分类", "Animal Classification",
        "位置 = 纲 · 子位置 = 目（小目合并，每组 ≤8）· 卡 = 动物 → 纲 · 目 · 科",
        "Locus = class · sub-locus = order(s) · card = animal → class · order · family",
        "Wikidata 分类（P171 上级分类、P105 分类等级、中文名）；纲目科与 GBIF Backbone 逐条核对",
        CC0, balanced_pack(loci))
    entry["_notes"] = notes
    entry["_animals"] = out
    return entry


# ---------------------------------------------------------------- human body (hand-written)
def build_body():
    loci = []
    for system, organs in M.BODY:
        kids = [card(f, b) for f, b in organs]
        loci.append(group(system, kids))
    return make_collection(
        "body", "nature", "人体系统与器官", "Human Body Systems",
        "位置 = 系统 · 卡 = 器官/结构 → 主要功能",
        "Locus = organ system · card = organ / structure → main function",
        "手写：中学生物 / 人体解剖生理学教材通行表述（不含数字）",
        "事实数据，自写", balanced_pack(loci))
