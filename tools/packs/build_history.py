"""v0.3.1 history collections from Wikidata (CC0): paintings, nobel, discoveries, events."""
import collections
import re
import sys

from opencc import OpenCC

import verify as V
import wd
from common import MAX_KIDS, card, group, even_chunks, balanced_pack, make_collection

cc = OpenCC("t2s")
CC0 = "CC0（Wikidata）"
RANK_P = "http://wikiba.se/ontology#PreferredRank"


def yfmt(y):
    return "前 %d 年" % -y if y < 0 else "%d 年" % y


def _years(stmts):
    """[(time, rank)] -> (year, raw) using preferred rank, else the single distinct year."""
    pref = [t for t, rk in stmts if rk == RANK_P]
    cand = pref or [t for t, _ in stmts]
    ys = sorted({wd.year_of(t) for t in cand})
    if len(ys) != 1:
        return None, None
    raw = sorted(t for t in cand if wd.year_of(t) == ys[0])[0]
    return ys[0], raw


def by_period(items, bands, title_fmt, key="year"):
    """items sorted; bands [(lo, hi, name)] -> loci with sub-loci of <=8 cards."""
    loci = []
    for lo, hi, name in bands:
        part = [it for it in items if lo <= it[key] <= hi]
        if not part:
            continue
        cards = [it["card"] for it in part]
        if len(cards) <= MAX_KIDS:
            loci.append(group(name, cards))
            continue
        chunks = even_chunks(part, MAX_KIDS)
        subs = []
        for ch in chunks:
            a, z = ch[0][key], ch[-1][key]
            subs.append(group(title_fmt(a, z), [it["card"] for it in ch]))
        for i, ss in enumerate(even_chunks(subs, MAX_KIDS), 1):
            loci.append(group(name if i == 1 else "%s %d" % (name, i), ss))
    return loci


def span(a, z):
    return yfmt(a) if a == z else "%s–%s" % (yfmt(a).replace(" 年", ""), yfmt(z))


def year_filter(items, notes):
    """Second source: keep items whose en.wikipedia article (infobox / lead) states the same year."""
    titles = V.prefetch_en([it["q"] for it in items])
    out = []
    for it in items:
        ok = V.en_year(titles, it["q"], it["year"])
        if ok:
            out.append(it)
        else:
            notes.append("%s %s: en.wikipedia year check %s (%s) -> dropped" % (
                it["q"], it["name"], "no article" if ok is None else "mismatch", it["year"]))
    return out


# ---------------------------------------------------------------- paintings
def build_paintings():
    facts = wd.FactLog("paintings")
    rows = wd.sparql("paintings", """SELECT ?p ?cr ?inc ?irk ?col ?loc ?colp ?sl WHERE {
  ?p wdt:P31 wd:Q3305213 ; wikibase:sitelinks ?sl . FILTER(?sl >= 24)
  OPTIONAL { ?p wdt:P170 ?cr }
  OPTIONAL { ?p p:P571 ?is . ?is ps:P571 ?inc ; wikibase:rank ?irk . FILTER(?irk != wikibase:DeprecatedRank) }
  OPTIONAL { ?p wdt:P195 ?col } OPTIONAL { ?p wdt:P276 ?loc }
  OPTIONAL { ?p wdt:P195/(wdt:P361|wdt:P749) ?colp }
}""")
    info = collections.defaultdict(lambda: {"cr": set(), "inc": set(), "col": set(), "loc": set(), "colp": set(),
                                            "sl": 0})
    for r in rows:
        e = info[wd.qid(r["p"])]
        e["sl"] = int(r["sl"])
        for k in ("cr", "col", "loc", "colp"):
            if k in r:
                e[k].add(wd.qid(r[k]))
        if "inc" in r:
            e["inc"].add((r["inc"], r["irk"]))
    qids = set(info) | {x for e in info.values() for k in ("cr", "col", "loc", "colp") for x in e[k]}
    lb = wd.labels("labels_paintings", qids)
    zh = lambda q: wd.zh_of(lb, q, cc)
    MUSEUM = re.compile(r"(馆|宫|院|中心|画廊|博物|美术|收藏|教堂|大教堂|修道院)$")
    out, notes = [], []
    for q, e in sorted(info.items(), key=lambda kv: -kv[1]["sl"]):
        name = zh(q)
        y, raw = _years(list(e["inc"]))
        crs = sorted(filter(None, (zh(c) for c in e["cr"])))
        places = ([zh(c) for c in sorted(e["col"])] + [zh(c) for c in sorted(e["loc"])]
                  + [zh(c) for c in sorted(e["colp"])])
        places = [p for p in places if p and len(p) >= 4 and MUSEUM.search(p) and "部" not in p]
        if not name or y is None or len(crs) != 1 or not places:
            notes.append("%s %s: dropped (name %s, year %s, creators %s, places %s)" % (
                q, wd.en_of(lb, q), name, sorted(t for t, _ in e["inc"]), crs, places))
            continue
        name = re.sub(r"\s*\(.*?\)$", "", name)
        b = "%s · %s · %s" % (crs[0], yfmt(y), places[0])
        if len(b) > 60:
            b = "%s · %s" % (crs[0], yfmt(y))
        out.append({"q": q, "year": y, "raw": raw, "name": name, "b": b})
    out = year_filter(out, notes)
    seen, uniq = set(), []
    for it in out:  # same title twice (two 《吻》): keep the better-known one (more sitelinks, listed first)
        if it["name"] in seen:
            notes.append("%s %s: duplicate title -> dropped" % (it["q"], it["name"]))
            continue
        seen.add(it["name"])
        uniq.append(it)
    out = uniq[:112]
    out.sort(key=lambda x: (x["year"], x["name"]))
    for it in out:
        it["card"] = card("《%s》" % it["name"], it["b"])
        facts.add("《%s》" % it["name"], it["b"], "paintings", it["q"], [it["raw"]], [it["year"]])
    bands = [(-5000, 1499, "1500 年以前"), (1500, 1599, "16 世纪"), (1600, 1699, "17 世纪"),
             (1700, 1799, "18 世纪"), (1800, 1869, "19 世纪前中期"), (1870, 1899, "19 世纪后期"),
             (1900, 2100, "20 世纪")]
    loci = by_period(out, bands, span)
    facts.save()
    entry = make_collection(
        "paintings", "history", "世界名画", "Famous Paintings",
        "位置 = 时代 · 子位置 = 按年份每组 ≤8 · 卡 = 画作 → 画家 · 年份 · 收藏地",
        "Locus = period · sub-locus = ≤8 by year · card = painting → painter · year · collection",
        "Wikidata（绘画条目按维基语言版本数取前 112 幅；P170 作者、P571 创作时间、P195/P276 收藏地）",
        CC0, balanced_pack(loci))
    entry["_notes"] = notes
    return entry


# ---------------------------------------------------------------- nobel
PRIZES = [("Q38104", "physics", "物理学奖", "Physics"), ("Q44585", "chemistry", "化学奖", "Chemistry"),
          ("Q80061", "medicine", "生理学或医学奖", "Physiology or Medicine")]


def build_nobel():
    facts = wd.FactLog("nobel")
    vals = " ".join("wd:" + p for p, _, _, _ in PRIZES)
    rows = wd.sparql("nobel", """SELECT ?prize ?w ?t WHERE {
  VALUES ?prize { %s } ?w p:P166 ?st . ?st ps:P166 ?prize ; pq:P585 ?t . }""" % vals)
    by = collections.defaultdict(lambda: collections.defaultdict(set))
    raw = {}
    for r in rows:
        y = wd.year_of(r["t"])
        by[wd.qid(r["prize"])][y].add(wd.qid(r["w"]))
        raw[(wd.qid(r["prize"]), y)] = r["t"]
    lb = wd.labels("labels_nobel", {q for d in by.values() for s in d.values() for q in s})
    al = wd.aliases("aliases_nobel", {q for d in by.values() for s in d.values() for q in s})
    api = load_nobel_api()
    vols, sz, se, notes = [], [], [], []
    for pq, key, pzh, pen in PRIZES:
        items = []
        for y in sorted(by[pq]):
            ws = by[pq][y]
            names = []
            for w in sorted(ws, key=lambda w: api.get((key, y), {}).get(w, 9)):
                n = wd.zh_of(lb, w, cc)
                if not n or not re.search(r"[一-鿿]", n):
                    zh_al = [a for a in al.get(w, []) if re.search(r"[一-鿿]", a)]
                    n = cc.convert(zh_al[0]) if zh_al else wd.en_of(lb, w)
                    notes.append("%s %d %s: no zh label -> %s" % (key, y, w, n))
                names.append(n)
            a_set = set(api.get((key, y), {}))
            if a_set and a_set != ws:
                notes.append("%s %d: Wikidata %s vs nobelprize.org %s" % (key, y, sorted(ws), sorted(a_set)))
            b = "、".join(names)
            if len(b) > 60:
                b = "、".join(n.split("·")[-1] for n in names)
            f = "诺贝尔%s %d" % (pzh, y)
            facts.add(f, b, "nobel", pq, [raw[(pq, y)]], [y])
            items.append({"year": y, "card": card(f, b)})
        bands = [(a, a + 19, "%d–%d 年" % (a, a + 19)) for a in range(1901, 2040, 20)]
        loci = by_period(items, bands, lambda a, z: "%d–%d" % (a, z))
        vols.append(loci)
        sz.append(pzh)
        se.append(pen)
    facts.save()
    entry = make_collection(
        "nobel", "history", "诺贝尔科学奖", "Nobel Prizes in Science",
        "卷 = 物理 / 化学 / 生理学或医学 · 位置 = 每 20 年 · 子位置 = ≤8 年 · 卡 = 奖项 年份 → 得主",
        "Volume = prize · locus = 20 years · card = prize + year → laureates",
        "Wikidata（P166 获奖及 P585 时间、中文名）；得主与年份和 nobelprize.org 开放数据（CC0）逐年核对",
        "CC0（Wikidata；Nobel Prize Outreach 开放数据 CC0）", vols, sz, se)
    entry["_notes"] = notes
    return entry


def load_nobel_api():
    """{(category, year): {wikidata qid: portion order}} from api.nobelprize.org v2 (CC0), cached."""
    import json
    import os
    path = os.path.join(wd.CACHE, "nobel_api.json")
    if not os.path.exists(path):
        data = []
        for cat in ("phy", "che", "med"):
            url = ("https://api.nobelprize.org/2.1/nobelPrizes?limit=200&nobelPrizeCategory=%s"
                   "&format=json" % cat)
            data += wd._get(url)["nobelPrizes"]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False)
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    lpath = os.path.join(wd.CACHE, "nobel_api_laureates.json")
    if not os.path.exists(lpath):
        laur, off = [], 0
        while True:
            page = wd._get("https://api.nobelprize.org/2.1/laureates?limit=100&offset=%d&format=json" % off)
            laur += page["laureates"]
            off += 100
            if off >= page["meta"]["count"]:
                break
        with open(lpath, "w", encoding="utf-8") as f:
            json.dump(laur, f, ensure_ascii=False)
    with open(lpath, encoding="utf-8") as f:
        l2q = {l["id"]: l.get("wikidata", {}).get("id") for l in json.load(f)}
    cmap = {"Physics": "physics", "Chemistry": "chemistry", "Physiology or Medicine": "medicine"}
    out = {}
    for p in data:
        key = (cmap[p["category"]["en"]], int(p["awardYear"]))
        d = out.setdefault(key, {})
        for i, l in enumerate(p.get("laureates", [])):
            wq = l2q.get(l["id"])
            if wq:
                d[wq] = i
    return out


# ---------------------------------------------------------------- discoveries
def build_discoveries():
    facts = wd.FactLog("discoveries")
    rows = wd.sparql("discoveries", """SELECT ?x ?kind ?who ?t ?rk ?z WHERE {
  { ?x wdt:P31 wd:Q11344 . BIND("element" AS ?kind) }
  UNION { ?x wdt:P31/wdt:P279* wd:Q2537 ; wikibase:sitelinks ?sl . FILTER(?sl >= 50) BIND("moon" AS ?kind) }
  UNION { VALUES ?x { wd:Q324 wd:Q332 wd:Q339 wd:Q596 wd:Q1096 wd:Q3030 } BIND("planet" AS ?kind) }
  ?x p:P575 ?st . ?st ps:P575 ?t ; wikibase:rank ?rk . FILTER(?rk != wikibase:DeprecatedRank)
  OPTIONAL { ?x wdt:P61 ?who } OPTIONAL { ?x wdt:P1086 ?z }
}""")
    info = collections.defaultdict(lambda: {"who": set(), "t": set(), "kind": None, "z": None})
    for r in rows:
        e = info[wd.qid(r["x"])]
        e["kind"] = r["kind"]
        if "z" in r:
            e["z"] = int(float(r["z"]))
        e["t"].add((r["t"], r["rk"]))
        if "who" in r and re.fullmatch(r"http://www.wikidata.org/entity/Q\d+", r["who"]):
            e["who"].add(wd.qid(r["who"]))
    lb = wd.labels("labels_discoveries", set(info) | {w for e in info.values() for w in e["who"]})
    people = sorted({w for e in info.values() for w in e["who"]})
    life = collections.defaultdict(lambda: [None, None])
    for r in wd.sparql("discoverers_life", """SELECT ?w ?b ?d WHERE { VALUES ?w { %s }
  OPTIONAL { ?w wdt:P569 ?b } OPTIONAL { ?w wdt:P570 ?d } }""" % " ".join("wd:" + w for w in people)):
        L = life[wd.qid(r["w"])]
        if "b" in r and wd.year_of(r["b"]) is not None:
            L[0] = wd.year_of(r["b"])
        if "d" in r and wd.year_of(r["d"]) is not None:
            L[1] = wd.year_of(r["d"])
    pub = pubchem_years()
    out, notes = [], []
    for q, e in info.items():
        name = wd.zh_of(lb, q, cc)
        y, raw = _years(list(e["t"]))
        if e["kind"] == "element" and y is not None:
            py = pub.get(e["z"])
            if py != y:
                notes.append("%s %s: Wikidata year %s != PubChem %s -> dropped" % (q, wd.en_of(lb, q), y, py))
                continue
        alive = []
        for w in sorted(e["who"]):
            b, d = life[w]
            if b is not None and (y is None or not (b + 12 <= y <= (d if d is not None else 9999))):
                notes.append("%s %s: discoverer %s (%s–%s) not active in %s -> removed" % (
                    q, wd.en_of(lb, q), wd.en_of(lb, w), b, d, y))
                continue
            alive.append(w)
        whos = sorted(filter(None, (wd.zh_of(lb, w, cc) for w in alive)))
        if not name or y is None or not whos or y < 1600:
            notes.append("%s %s: dropped (year %s, who %s)" % (q, wd.en_of(lb, q),
                                                               sorted({t for t, _ in e["t"]}), whos))
            continue
        b = "%s · %s" % ("、".join(whos[:2]) + ("等" if len(whos) > 2 else ""), yfmt(y))
        if len(b) > 60:
            b = "%s等 · %s" % (whos[0], yfmt(y))
        out.append({"q": q, "year": y, "raw": raw, "name": name, "kind": e["kind"], "b": b})
    out = [o for o in out if o["kind"] == "element"] + year_filter([o for o in out if o["kind"] != "element"], notes)
    loci = []
    for kind, title in (("element", "化学元素"), ("planet", "行星与矮行星"), ("moon", "卫星")):
        items = sorted((o for o in out if o["kind"] == kind), key=lambda o: (o["year"], o["name"]))
        for it in items:
            f = "发现%s" % it["name"] if kind == "element" else it["name"]
            it["card"] = card(f, it["b"])
            facts.add(f, it["b"], "discoveries", it["q"], [it["raw"]], [it["year"]])
        if kind == "element":
            bands = [(1600, 1799, "元素 · 18 世纪及以前"), (1800, 1849, "元素 · 1800–1849"),
                     (1850, 1899, "元素 · 1850–1899"), (1900, 2100, "元素 · 20 世纪以后")]
            loci += by_period(items, bands, span)
        else:
            loci += by_period(items, [(1600, 2100, title)], span)
    facts.save()
    entry = make_collection(
        "discoveries", "history", "科学发现：元素与天体", "Discoveries: Elements & Moons",
        "位置 = 元素（按时代）/ 行星 / 卫星 · 子位置 = 按年份每组 ≤8 · 卡 = 发现对象 → 发现者 · 年份",
        "Locus = elements by era / planets / moons · card = discovery → discoverer · year",
        "Wikidata（P61 发现者、P575 发现时间）；元素发现年份须与 PubChem 周期表一致，卫星年份与英文维基百科核对，发现时不在世的发现者剔除",
        CC0, balanced_pack(loci))
    entry["_notes"] = notes
    return entry


def pubchem_years():
    """{atomic number: year discovered} from the PubChem periodic table (NIH, public domain)."""
    import json
    import os
    from common import SRC
    with open(os.path.join(SRC, "pubchem", "periodictable.json"), encoding="utf-8") as f:
        t = json.load(f)["Table"]
    cols = t["Column"]["Column"] if "Column" in t else t["Columns"]["Column"]
    zi, yi = cols.index("AtomicNumber"), cols.index("YearDiscovered")
    out = {}
    for r in t["Row"]:
        c = r["Cell"]
        if re.fullmatch(r"\d{4}", c[yi]):
            out[int(c[zi])] = int(c[yi])
    return out


# ---------------------------------------------------------------- world history events
EVENT_CLASSES = {"Q198": "战争", "Q103495": "战争", "Q10931": "革命", "Q178561": "战役", "Q131569": "条约",
                 "Q625298": "条约", "Q2001676": "战役", "Q1261499": "战役", "Q350604": "冲突",
                 "Q13418847": "事件", "Q7944": "地震", "Q12184": "瘟疫"}


def build_events():
    facts = wd.FactLog("events")
    vals = " ".join("wd:" + q for q in EVENT_CLASSES)
    rows = wd.sparql("events", """SELECT ?e ?cls ?s ?srk ?t ?trk ?sl WHERE {
  VALUES ?cls { %s }
  ?e wdt:P31 ?cls ; wikibase:sitelinks ?sl . FILTER(?sl >= 60)
  OPTIONAL { ?e p:P580 ?ss . ?ss ps:P580 ?s ; wikibase:rank ?srk . FILTER(?srk != wikibase:DeprecatedRank) }
  OPTIONAL { ?e p:P585 ?ts . ?ts ps:P585 ?t ; wikibase:rank ?trk . FILTER(?trk != wikibase:DeprecatedRank) }
}""" % vals)
    info = collections.defaultdict(lambda: {"cls": set(), "s": set(), "t": set(), "sl": 0})
    for r in rows:
        e = info[wd.qid(r["e"])]
        e["cls"].add(wd.qid(r["cls"]))
        e["sl"] = int(r["sl"])
        if "s" in r:
            e["s"].add((r["s"], r["srk"]))
        if "t" in r:
            e["t"].add((r["t"], r["trk"]))
    lb = wd.labels("labels_events", set(info))
    out, notes, seen_names = [], [], set()
    for q, e in sorted(info.items(), key=lambda kv: -kv[1]["sl"]):
        name = wd.zh_of(lb, q, cc)
        y, raw = _years(list(e["s"])) if e["s"] else _years(list(e["t"]))
        kind = sorted({EVENT_CLASSES[c] for c in e["cls"]})[0]
        if (not name or y is None or y > 2000 or name in seen_names
                or re.search(r"\d{4}年.*(枪击|爆炸|袭击)|第三次世界大战", name)
                or (kind == "瘟疫" and not re.search("疫|死病", name))):
            notes.append("%s %s: dropped (year %s, name %s)" % (q, wd.en_of(lb, q),
                                                               sorted({t for t, _ in e["s"] | e["t"]}), name))
            continue
        seen_names.add(name)
        out.append({"q": q, "name": name, "year": y, "raw": raw, "b": "%s · %s" % (yfmt(y), kind)})
    out = year_filter(out, notes)[:120]
    out.sort(key=lambda o: (o["year"], o["name"]))
    for it in out:
        it["card"] = card(it["name"], it["b"])
        facts.add(it["name"], it["b"], "events", it["q"], [it["raw"]], [it["year"]])
    bands = [(-10000, 499, "古代"), (500, 1499, "中世纪"), (1500, 1799, "近代早期"), (1800, 1899, "19 世纪"),
             (1900, 1938, "20 世纪前期"), (1939, 1945, "第二次世界大战"), (1946, 2000, "20 世纪后期")]
    loci = by_period(out, bands, span)
    facts.save()
    entry = make_collection(
        "events", "history", "世界历史大事", "World History Events",
        "位置 = 时代 · 子位置 = 按年份每组 ≤8 · 卡 = 事件 → 开始年份 · 类型",
        "Locus = era · sub-locus = ≤8 by year · card = event → start year · type",
        "Wikidata（战争、革命、战役、条约等 2000 年以前的条目按维基语言版本数取前 120；P580 开始时间 / P585 时间）",
        CC0, balanced_pack(loci))
    entry["_notes"] = notes
    return entry
