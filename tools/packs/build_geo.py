"""v0.3.1 geography collections, generated from Wikidata (CC0).

peaks, rivers, lakes-deserts, whc-china, currencies.
Every number on a card is computed from the cached SPARQL result (.cache/wd/*.json)
and logged in .cache/facts/<id>.json for validate.py.
"""
import collections
import re
import sys

from opencc import OpenCC

import manual
import verify as V
import wd
import os
from common import SRC, MAX_KIDS, card, group, even_chunks, balanced_pack, make_collection

cc = OpenCC("t2s")
CC0 = "CC0（Wikidata）"
RANK_P = "http://wikiba.se/ontology#PreferredRank"


def log(*a):
    print(*a, file=sys.stderr)


# ---------------------------------------------------------------- shared
def iso_country_names():
    """Wikidata country QID -> short Chinese name used by the capitals collection."""
    isos = {}
    for _, _, loci in manual.CAPITALS:
        for _, subs in loci:
            for _, rows in subs:
                for country, _, iso in rows:
                    isos[iso] = country
    rows = wd.sparql("iso3", """SELECT ?c ?iso WHERE { ?c wdt:P298 ?iso . }""")
    out = {}
    for r in rows:
        if r["iso"] in isos:
            out.setdefault(wd.qid(r["c"]), isos[r["iso"]])
    return out, isos


def pick_value(stmts, tol=0.01):
    """stmts: [(value_str, rank)] for one item. Preferred rank wins; otherwise all
    distinct normal values must agree within tol (relative), and the median is used.
    Returns (value_str, note) or (None, reason)."""
    pref = sorted({v for v, rk in stmts if rk == RANK_P}, key=float)
    vals = pref or sorted({v for v, _ in stmts}, key=float)
    lo, hi = float(vals[0]), float(vals[-1])
    if hi - lo > tol * hi:
        return None, "values disagree: %s" % "/".join(vals)
    return vals[len(vals) // 2], ("preferred" if pref else "normal x%d" % len(vals))


def group_items(stmt_rows, key, val):
    d = collections.defaultdict(list)
    for r in stmt_rows:
        d[wd.qid(r[key])].append((r[val], r.get("rk", "")))
    return d


def sub_split(title, cards):
    """A locus with <=8 cards, or sub-loci of <=8 cards labelled 一/二/…"""
    if len(cards) <= MAX_KIDS:
        return group(title, cards)
    parts = even_chunks(cards, MAX_KIDS)
    if len(parts) > MAX_KIDS:
        raise ValueError("too many cards for one locus: %s" % title)
    return group(title, [group("%s %d" % (title, i), p) for i, p in enumerate(parts, 1)])


def wan(raw):
    """Area display: >= 100000 km² as 'N 万 km²' (rounded to 万), else integer km²."""
    x = float(raw)
    if x >= 100000:
        n = wd.fmt_int(x / 10000)
        return "%d 万 km²" % n, "%d万" % n
    n = wd.fmt_int(raw)
    return "%d km²" % n, str(n)


def en_filter(items, key, tol, dropped):
    """Second source: keep items whose en.wikipedia infobox states the same number (within tol)."""
    titles = V.prefetch_en([it["q"] for it in items])
    out = []
    for it in items:
        ok = V.en_number(titles, it["q"], it[key], tol)
        if not ok and V.zh_number(it["q"], it[key]):
            ok = True
            dropped.append((it["q"], it["name"], "en.wikipedia differs; value attested by zh.wikipedia -> kept"))
        if ok:
            out.append(it)
        else:
            dropped.append((it["q"], it["name"], "en.wikipedia check %s: %s" % ("no article" if ok is None
                                                                                 else "mismatch", it[key])))
    return out


# ---------------------------------------------------------------- peaks
def build_peaks():
    facts = wd.FactLog("peaks")
    names, _ = iso_country_names()
    rows = wd.sparql("peaks", """SELECT ?m ?h ?rk ?prom ?range ?country WHERE {
  ?m wdt:P31/wdt:P279* wd:Q8502 ; p:P2044 ?st . ?st wikibase:rank ?rk FILTER(?rk != wikibase:DeprecatedRank)
  ?st psv:P2044 [wikibase:quantityAmount ?h; wikibase:quantityUnit wd:Q11573] . FILTER(?h >= 6900)
  ?m wdt:P17 ?country .
  OPTIONAL { ?m wdt:P2660 ?prom } OPTIONAL { ?m wdt:P4552 ?range }
}""")
    cont = wd.sparql("continent_highpoints", """SELECT ?c ?p ?h ?rk ?part WHERE {
  VALUES ?c { wd:Q48 wd:Q15 wd:Q46 wd:Q49 wd:Q18 wd:Q51 wd:Q55643 }
  ?c wdt:P610 ?p . ?p p:P2044 ?st . ?st wikibase:rank ?rk FILTER(?rk != wikibase:DeprecatedRank)
  ?st psv:P2044 [wikibase:quantityAmount ?h; wikibase:quantityUnit wd:Q11573] .
  OPTIONAL { ?part wdt:P610 ?p ; wdt:P31/wdt:P279* wd:Q8502 . }
}""")
    info = collections.defaultdict(lambda: {"h": [], "prom": set(), "range": set(), "country": set()})
    for r in rows:
        e = info[wd.qid(r["m"])]
        e["h"].append((r["h"], r["rk"]))
        if "prom" in r:
            e["prom"].add(r["prom"])
        if "range" in r:
            e["range"].add(wd.qid(r["range"]))
        e["country"].add(wd.qid(r["country"]))
    qids = set(info) | {x for e in info.values() for x in e["range"] | e["country"]}
    qids |= {wd.qid(r[k]) for r in cont for k in ("c", "p", "part") if k in r}
    lb = wd.labels("labels_peaks", qids)

    def zh(q):
        return wd.zh_of(lb, q, cc)

    peaks, dropped = [], []
    for q, e in info.items():
        h, note = pick_value(list(set(e["h"])))
        name = zh(q)
        if h is None or not name:
            dropped.append((q, wd.en_of(lb, q), note if h is None else "no zh label"))
            continue
        prom = max((float(p) for p in e["prom"]), default=0)
        peaks.append({"q": q, "name": name, "h": h, "hm": wd.fmt_int(h), "prom": prom,
                      "range": sorted((x for x in (zh(r) for r in e["range"]) if x and not x.endswith("峰")),
                                      key=len),
                      "country": sorted({names[c] for c in e["country"] if c in names})})
    peaks.sort(key=lambda p: -float(p["h"]))
    peaks = en_filter(peaks, "h", 0.005, dropped)

    def back(p):
        parts = ["%d m" % p["hm"]]
        if p["range"]:
            parts.append(p["range"][0])
        cs = [c for c in p["country"] if c]
        if cs:
            parts.append("/".join(cs[:3]))
        return " · ".join(parts)

    def mk(p, front=None):
        f = front or p["name"]
        b = back(p)
        facts.add(f, b, "peaks", p["q"], [p["h"]], [p["hm"]])
        return card(f, b)

    e8 = [p for p in peaks if float(p["h"]) >= 8000 and p["prom"] >= 500]
    assert len(e8) == 14, [p["name"] for p in e8]
    e7 = [p for p in peaks if 7000 <= float(p["h"]) < 8000 and p["prom"] >= 1500]

    # continents
    cname = {"Q48": "亚洲", "Q15": "非洲", "Q46": "欧洲", "Q49": "北美洲", "Q18": "南美洲",
             "Q51": "南极洲", "Q55643": "大洋洲"}
    cst = collections.defaultdict(list)
    cpart = {}
    for r in cont:
        k = (wd.qid(r["c"]), wd.qid(r["p"]))
        cst[k].append((r["h"], r["rk"]))
        if "part" in r:
            cpart.setdefault(k, set()).add(wd.qid(r["part"]))
    ccards = []
    for (c, p), st in sorted(cst.items(), key=lambda kv: -float(pick_value(kv[1])[0] or 0)):
        h, note = pick_value(list(set(st)))
        assert h, (c, p, note)
        name = zh(p)
        if not name:  # e.g. Kibo (no zh label) -> part of Kilimanjaro
            parts = [zh(x) for x in sorted(cpart.get((c, p), ()))]
            parts = [x for x in parts if x and "列表" not in x]
            name = parts[0] if len(parts) == 1 else None
        assert name, (c, p)
        f = "%s最高峰" % cname[c]
        b = "%s · %d m" % (name, wd.fmt_int(h))
        facts.add(f, b, "continent_highpoints", p, [h], [wd.fmt_int(h)])
        ccards.append(card(f, b))

    loci = []
    loci.append(group("七大洲最高峰", ccards))
    for i, part in enumerate(even_chunks(e8, MAX_KIDS), 1):
        loci.append(group("8000 米级 %s" % "一二"[i - 1], [mk(p) for p in part]))
    # 7000ers: one locus per <=8, by height
    for part in even_chunks(e7, MAX_KIDS):
        loci.append(group("%d–%d m" % (part[-1]["hm"], part[0]["hm"]), [mk(p) for p in part]))
    vols = balanced_pack(loci)
    facts.save()
    entry = make_collection(
        "peaks", "geo", "世界高峰", "World's Highest Peaks",
        "位置 = 七大洲最高峰 / 8000 米级 / 7000 米级（按高度）· 卡 = 山峰 → 海拔 · 山脉 · 国家",
        "Locus = continental summits / 8000ers / 7000ers by height · card = peak → elevation · range · country",
        "Wikidata（P2044 海拔，取 preferred 值；7000 米级取地形突出度 P2660 ≥ 1500 m 的独立高峰），海拔四舍五入到米",
        CC0, vols)
    entry["_dropped"] = dropped
    return entry


# ---------------------------------------------------------------- rivers
CONTINENTS = [("Q48", "亚洲", "Asia"), ("Q46", "欧洲", "Europe"), ("Q15", "非洲", "Africa"),
              ("Q49", "北美洲", "North America"), ("Q18", "南美洲", "South America"),
              ("Q55643", "大洋洲", "Oceania"), ("Q538", "大洋洲", "Oceania"), ("Q3960", "大洋洲", "Oceania")]
CONT_ORDER = ["亚洲", "欧洲", "非洲", "北美洲", "南美洲", "大洋洲"]


def build_rivers():
    facts = wd.FactLog("rivers")
    names, _ = iso_country_names()
    rows = wd.sparql("rivers", """SELECT ?r ?len ?rk ?mouth ?country ?sl WHERE {
  ?r wdt:P31/wdt:P279* wd:Q4022 ; p:P2043 ?st . ?st wikibase:rank ?rk FILTER(?rk != wikibase:DeprecatedRank)
  ?st psv:P2043 [wikibase:quantityAmount ?len; wikibase:quantityUnit wd:Q828224] . FILTER(?len >= 1000)
  ?r wikibase:sitelinks ?sl . FILTER(?sl >= 30)
  OPTIONAL { ?r wdt:P403 ?mouth } OPTIONAL { ?r wdt:P17 ?country }
}""")
    info = collections.defaultdict(lambda: {"len": [], "mouth": set(), "country": set(), "sl": 0})
    for r in rows:
        e = info[wd.qid(r["r"])]
        e["len"].append((r["len"], r["rk"]))
        e["sl"] = int(r["sl"])
        if "mouth" in r:
            e["mouth"].add(wd.qid(r["mouth"]))
        if "country" in r:
            e["country"].add(wd.qid(r["country"]))
    qids = set(info) | {m for e in info.values() for m in e["mouth"]}
    lb = wd.labels("labels_rivers", qids)
    rs, dropped = [], []
    for q, e in info.items():
        v, note = pick_value(list(set(e["len"])), tol=0.03)
        name = wd.zh_of(lb, q, cc)
        if v is None or not name:
            dropped.append((q, wd.en_of(lb, q), note if v is None else "no zh"))
            continue
        mouths = sorted(filter(None, (wd.zh_of(lb, m, cc) for m in e["mouth"])))
        cs = sorted({names[c] for c in e["country"] if c in names})
        rs.append({"q": q, "name": name, "len": v, "km": wd.fmt_int(v), "mouth": mouths, "cs": cs})
    rs.sort(key=lambda r: -float(r["len"]))
    rs = en_filter(rs[:130], "len", 0.02, dropped)[:96]
    cards = []
    for r in rs:
        parts = ["%d km" % r["km"]]
        if r["cs"]:
            parts.append("/".join(r["cs"][:2]) + ("等" if len(r["cs"]) > 2 else ""))
        if len(r["mouth"]) == 1:
            parts.append("注入" + r["mouth"][0])
        b = " · ".join(parts)
        facts.add(r["name"], b, "rivers", r["q"], [r["len"]], [r["km"]])
        cards.append(card(r["name"], b))
    loci = []
    for i, part in enumerate(even_chunks(cards, MAX_KIDS)):
        a, z = rs[i * len(part)]["km"] if False else None, None
        loci.append(part)
    out, k = [], 0
    for part in loci:
        hi, lo = rs[k]["km"], rs[k + len(part) - 1]["km"]
        k += len(part)
        out.append(group("%d–%d km" % (lo, hi), part))
    vols = balanced_pack(out)
    facts.save()
    entry = make_collection(
        "rivers", "geo", "世界大河", "Great Rivers of the World",
        "位置 = 按长度每 8 条一段 · 卡 = 河流 → 长度 · 流经国家 · 注入",
        "Locus = 8 rivers by length · card = river → length · countries · mouth",
        "Wikidata（P2043 长度，preferred 值优先；多值相差 >3% 的河流不收；≥30 种语言条目的河流），长度四舍五入到 km",
        CC0, vols)
    entry["_dropped"] = dropped
    return entry


# ---------------------------------------------------------------- lakes & deserts
def _areas(name, cls, minimum, minsl):
    rows = wd.sparql(name, """SELECT ?r ?a ?rk ?sl ?cls ?country ?gone WHERE {
  ?r wdt:P31/wdt:P279* wd:%s ; p:P2046 ?st . ?st wikibase:rank ?rk FILTER(?rk != wikibase:DeprecatedRank)
  ?st psv:P2046 [wikibase:quantityAmount ?a; wikibase:quantityUnit wd:Q712226] . FILTER(?a >= %d)
  ?r wikibase:sitelinks ?sl . FILTER(?sl >= %d)
  OPTIONAL { ?r wdt:P31 ?cls } OPTIONAL { ?r wdt:P17 ?country }
  OPTIONAL { ?r wdt:P576|wdt:P582 ?gone }
}""" % (cls, minimum, minsl))
    info = collections.defaultdict(lambda: {"a": [], "cls": set(), "country": set(), "gone": False})
    for r in rows:
        e = info[wd.qid(r["r"])]
        e["a"].append((r["a"], r["rk"]))
        if "cls" in r:
            e["cls"].add(wd.qid(r["cls"]))
        if "country" in r:
            e["country"].add(wd.qid(r["country"]))
        if "gone" in r:
            e["gone"] = True
    return info


def build_lakes_deserts():
    facts = wd.FactLog("lakes-deserts")
    names, _ = iso_country_names()
    lakes = _areas("lakes", "Q23397", 3000, 25)
    deserts = _areas("deserts", "Q8514", 50000, 20)
    qids = set(lakes) | set(deserts)
    for e in list(lakes.values()) + list(deserts.values()):
        qids |= e["cls"] | e["country"]
    lb = wd.labels("labels_lakes", qids)
    dropped = []

    def rows_of(info, src, kind):
        out = []
        for q, e in info.items():
            en_cls = " ".join((wd.en_of(lb, c) or "") for c in e["cls"]).lower()
            if e["gone"] or re.search(r"former|paleo|pluvial|proglacial|subglacial|dry lake|reservoir|salt flat|salar|"
                                      r"salt pan|lake system|group of lakes|hydrological|semi-desert|peninsula", en_cls):
                dropped.append((q, wd.en_of(lb, q), "class: " + en_cls[:60]))
                continue
            v, note = pick_value(list(set(e["a"])), tol=0.05)
            name = wd.zh_of(lb, q, cc)
            if v is None or not name:
                dropped.append((q, wd.en_of(lb, q), note if v is None else "no zh"))
                continue
            cs = [names.get(c) for c in sorted(e["country"])]
            cs = [c for c in cs if c]
            out.append({"q": q, "name": name, "a": v, "cs": cs, "src": src})
        out.sort(key=lambda r: -float(r["a"]))
        return out

    def mk(r):
        disp, shown = wan(r["a"])
        b = disp
        if r["cs"]:
            b += " · " + "/".join(r["cs"][:3]) + ("等" if len(r["cs"]) > 3 else "")
        facts.add(r["name"], b, r["src"], r["q"], [r["a"]], [shown])
        return card(r["name"], b)

    L = en_filter(rows_of(lakes, "lakes", "lake")[:56], "a", 0.03, dropped)[:40]
    D = en_filter(rows_of(deserts, "deserts", "desert")[:40], "a", 0.05, dropped)[:32]
    loci = []
    for i, part in enumerate(even_chunks(L, MAX_KIDS), 1):
        loci.append(group("湖泊 %s" % "一二三四五六七八"[i - 1], [mk(r) for r in part]))
    for i, part in enumerate(even_chunks(D, MAX_KIDS), 1):
        loci.append(group("沙漠 %s" % "一二三四五六七八"[i - 1], [mk(r) for r in part]))
    vols = balanced_pack(loci)
    facts.save()
    entry = make_collection(
        "lakes-deserts", "geo", "世界湖泊与沙漠", "Lakes & Deserts",
        "位置 = 湖泊 / 沙漠（按面积从大到小，每组 ≤8）· 卡 = 名称 → 面积 · 国家",
        "Locus = lakes / deserts by area (≤8 each) · card = name → area · countries",
        "Wikidata（P2046 面积；古湖、水库、盐沼、湖群、半荒漠不收），≥10 万 km² 四舍五入到万 km²，否则到 km²",
        CC0, vols)
    entry["_dropped"] = dropped
    return entry


# ---------------------------------------------------------------- China world heritage
CRIT_CULT = {"(i)", "(ii)", "(iii)", "(iv)", "(v)", "(vi)"}
CRIT_NAT = {"(vii)", "(viii)", "(ix)", "(x)"}


def province_short(label):
    for _, rows in manual.PROVINCES:
        for name, _ in rows:
            if label.startswith(name):
                return name
    return None


def cn_provinces():
    """{qid: (short zh name, en label)} for the 34 provincial-level divisions."""
    rows = wd.sparql("cn_provinces", """SELECT ?p ?zh ?en ?area ?rk WHERE {
 { wd:Q148 wdt:P150 ?p } UNION { VALUES ?p { wd:Q8646 wd:Q14773 wd:Q865 } }
 OPTIONAL { ?p rdfs:label ?zh FILTER(LANG(?zh)="zh-cn") } OPTIONAL { ?p rdfs:label ?en FILTER(LANG(?en)="en") }
 OPTIONAL { ?p p:P2046 ?st . ?st wikibase:rank ?rk FILTER(?rk != wikibase:DeprecatedRank) ?st psv:P2046 [wikibase:quantityAmount ?area; wikibase:quantityUnit wd:Q712226] }
}""")
    out = {}
    for r in rows:
        q = wd.qid(r["p"])
        zh = "台湾" if q == "Q865" else province_short(r["zh"])
        assert zh, r
        out[q] = (zh, r["en"])
    assert len(out) == 34, len(out)
    return out, rows


def unesco_china():
    """UNESCO World Heritage List XML (verification / gap source): {id: row dict}."""
    import xml.etree.ElementTree as ET
    root = ET.parse(os.path.join(SRC, "unesco", "whc.xml")).getroot()
    out = {}
    for row in root:
        d = {c.tag: (c.text or "") for c in row}
        if "cn" in d.get("iso_code", "").split(","):
            out[d["id_number"]] = d
    return out


# Official Chinese names for sites whose Wikidata item has no Chinese label at all
# (names only; checked against the UNESCO English name and the Chinese list of 中国世界遗产).
WHC_ZH_NAME = {"1414": "新疆天山", "779": "峨眉山—乐山大佛", "1559": "梵净山",
               # Wikidata's item for 778 is the later geopark, not the 1996 cultural-landscape site
               "778": "庐山国家公园"}
# Serial sites whose Wikidata component items are incomplete or carry wrong P131 values
# (checked against the UNESCO component list of each site; text only, no numbers).
WHC_PROVINCES = {
    "1443": ["北京", "天津", "河北", "山东", "河南", "安徽", "江苏", "浙江"],  # 大运河
    "1248": ["云南", "贵州", "重庆", "广西"],  # 中国南方喀斯特 (2007 + 2014)
    "1335": ["贵州", "福建", "湖南", "广东", "江西", "浙江"],  # 中国丹霞
    "1442": ["河南", "陕西", "甘肃", "新疆"],  # 丝绸之路 (China part)
    "1334": ["浙江"], "1559": ["贵州"], "1606": ["江苏"], "1765": ["江西"],
}


def build_whc():
    facts = wd.FactLog("whc-china")
    un = unesco_china()
    provs, _ = cn_provinces()
    ids = " ".join('"%s"' % k for k in sorted(un))
    rows = wd.sparql("whc_china", """SELECT ?s ?main ?id ?start ?crit ?prov ?sl WHERE {
  VALUES ?main { %s }
  ?s wdt:P757 ?id . FILTER(REGEX(?id, CONCAT("^", ?main, "([a-z]+)?(-[0-9]+)?$")))
  ?s wikibase:sitelinks ?sl .
  OPTIONAL { ?s p:P1435 ?h . ?h ps:P1435 wd:Q9259 . OPTIONAL { ?h pq:P580 ?start } }
  OPTIONAL { ?s wdt:P2614 ?crit }
  OPTIONAL { ?s wdt:P131* ?prov . VALUES ?prov { %s } }
}""" % (ids, " ".join("wd:" + q for q in provs)))
    by = collections.defaultdict(lambda: collections.defaultdict(lambda: {
        "ids": set(), "start": set(), "crit": set(), "prov": set(), "sl": 0}))
    for r in rows:
        e = by[r["main"]][wd.qid(r["s"])]
        e["ids"].add(r["id"])
        e["sl"] = int(r["sl"])
        if "start" in r:
            e["start"].add(r["start"])
        if "crit" in r:
            e["crit"].add(wd.qid(r["crit"]))
        if "prov" in r:
            e["prov"].add(wd.qid(r["prov"]))
    qids = {q for items in by.values() for q in items} | {c for items in by.values()
                                                          for e in items.values() for c in e["crit"]}
    lb = wd.labels("labels_whc", qids)
    en2zh = {en: zh for zh, en in provs.values()}
    sites, notes = [], []
    for main, u in sorted(un.items(), key=lambda kv: int(kv[0])):
        items = by.get(main, {})
        # the item carrying the site id itself (not a component "-001"), most sitelinks first
        heads = sorted((q for q, e in items.items() if any(re.fullmatch(main + r"[a-z]*", i) for i in e["ids"])),
                       key=lambda q: (not items[q]["start"], -items[q]["sl"]))
        head = heads[0] if heads else None
        name = wd.zh_of(lb, head, cc) if head else None
        if main in WHC_ZH_NAME:
            notes.append("%s: Wikidata zh label %s -> official name %s" % (main, name, WHC_ZH_NAME[main]))
            name = WHC_ZH_NAME[main]
        assert name, (main, u["site"])
        # province: component items + UNESCO location text; the site item's own P131 only as fallback
        comp = {provs[p][0] for q, e in items.items() if q != head for p in e["prov"]}
        text = {zh for en, zh in en2zh.items()
                if re.search(r"\b%s\b" % re.escape(en.split(" ")[0]), u["location"])}
        own = {provs[p][0] for p in items[head]["prov"]} if head else set()
        if main in WHC_PROVINCES:
            pv = set(WHC_PROVINCES[main])
            notes.append("%s %s: provinces hand-checked %s (Wikidata: own %s, components %s)" % (
                main, name, WHC_PROVINCES[main], sorted(own), sorted(comp)))
        else:
            pv = (comp | text) or own
        assert pv, (main, name)
        wy = sorted({wd.year_of(x) for x in items[head]["start"]}) if head else []
        uy = int(u["date_inscribed"])
        start_raw = sorted(items[head]["start"])[0] if wy else None
        if not wy:
            year, src = uy, "unesco"
            notes.append("%s %s: no inscription date in Wikidata -> UNESCO %d" % (main, name, uy))
        elif wy[0] != uy:
            year, src = uy, "unesco"
            notes.append("%s %s: Wikidata year %s != UNESCO %d -> UNESCO" % (main, name, wy, uy))
        else:
            year, src = wy[0], "wikidata"
        crit = {re.sub(r"^.*?(\([ivx]+\)).*$", r"\1", wd.en_of(lb, c) or "") for e in [items.get(head, {"crit": ()})]
                for c in e["crit"]}
        kind_wd = ("混合" if crit & CRIT_CULT and crit & CRIT_NAT else
                   "文化" if crit & CRIT_CULT else "自然" if crit & CRIT_NAT else None)
        kind_un = {"Cultural": "文化", "Natural": "自然", "Mixed": "混合"}[u["category"]]
        if kind_wd != kind_un:
            notes.append("%s %s: type Wikidata %s vs UNESCO %s -> UNESCO" % (main, name, kind_wd, kind_un))
        sites.append({"q": head, "id": main, "name": name, "year": year, "src": src, "start_raw": start_raw,
                      "kind": kind_un, "provs": sorted(pv, key=lambda z: [n for _, rows_ in manual.PROVINCES
                                                                            for n, _ in rows_].index(z))})
    sites.sort(key=lambda s: (s["year"], int(s["id"])))
    cards = []
    for s in sites:
        provs_ = s["provs"]
        p = "、".join(provs_[:3]) + ("等" if len(provs_) > 3 else "") if provs_ else "多省"
        b = "%s · %d 年 · %s遗产" % (p, s["year"], s["kind"])
        if s["src"] == "wikidata":
            facts.add(s["name"], b, "whc_china", s["q"], [s["start_raw"]], [s["year"]])
        else:
            facts.add(s["name"], b, "unesco", s["id"], [str(s["year"])], [s["year"]])
        cards.append((s["year"], card(s["name"], b)))
    bands = [(1987, 1997), (1998, 2007), (2008, 2016), (2017, 2100)]
    loci = []
    for a, z in bands:
        cs = [c for y, c in cards if a <= y <= z]
        ys = [y for y, c in cards if a <= y <= z]
        for k, part in enumerate(even_chunks(cs, MAX_KIDS)):
            yy = ys[sum(len(x) for x in even_chunks(cs, MAX_KIDS)[:k]):][:len(part)]
            loci.append(group("%d–%d 年列入" % (yy[0], yy[-1]) if yy[0] != yy[-1] else "%d 年列入" % yy[0], part))
    facts.save()
    entry = make_collection(
        "whc-china", "geo", "中国世界遗产", "World Heritage in China",
        "位置 = 列入年份段（每组 ≤8）· 卡 = 遗产 → 省份 · 列入年份 · 类型",
        "Locus = inscription years (≤8 each) · card = site → province · year inscribed · type",
        "Wikidata（P757 遗产编号、P1435 起始时间、P131 所在地、中文名）；名录、年份、类型按 UNESCO 世界遗产名录核对与补正",
        "CC0（Wikidata）；名录事实数据（UNESCO）", balanced_pack(loci))
    entry["_notes"] = notes
    return entry


# ---------------------------------------------------------------- currencies
def iso4217():
    """SIX ISO 4217 list one: {normalised country name: set(alpha codes)}."""
    import xml.etree.ElementTree as ET
    root = ET.parse(os.path.join(SRC, "iso4217", "list-one.xml")).getroot()
    out = collections.defaultdict(set)
    for e in root.iter("CcyNtry"):
        c, code = e.findtext("CtryNm"), e.findtext("Ccy")
        if c and code and not e.findtext("CcyNm", "").startswith(("SDR", "ADB", "Bond", "European", "Gold",
                                                                    "Palladium", "Platinum", "Silver", "WIR",
                                                                    "UIC", "US Dollar (Next day)", "Codes",
                                                                    "No universal")):
            out[_norm(c)].add(code)
    return out


def _norm(name):
    import unicodedata
    n = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().upper()
    n = re.sub(r"\((THE)\)", "", n)
    n = re.sub(r"[^A-Z ]", " ", n)
    n = re.sub(r"\b(THE|OF|AND)\b", " ", n)
    return " ".join(n.split())


# ISO 4217 entity names that do not match the Wikidata English label/aliases
SIX_NAME = {"BOL": "BOLIVIA PLURINATIONAL STATE", "COD": "CONGO DEMOCRATIC REPUBLIC", "COG": "CONGO",
            "FSM": "MICRONESIA FEDERATED STATES", "IRN": "IRAN ISLAMIC REPUBLIC", "KOR": "KOREA REPUBLIC",
            "PRK": "KOREA DEMOCRATIC PEOPLES REPUBLIC", "LAO": "LAO PEOPLES DEMOCRATIC REPUBLIC", "CHN": "CHINA",
            "MDA": "MOLDOVA REPUBLIC", "TZA": "TANZANIA UNITED REPUBLIC", "VEN": "VENEZUELA BOLIVARIAN REPUBLIC",
            "VAT": "HOLY SEE", "PSE": "PALESTINE STATE", "GBR": "UNITED KINGDOM GREAT BRITAIN NORTHERN IRELAND",
            "USA": "UNITED STATES AMERICA", "SYR": "SYRIAN ARAB REPUBLIC", "RUS": "RUSSIAN FEDERATION",
            "NLD": "NETHERLANDS", "CZE": "CZECHIA", "TUR": "TURKIYE", "SWZ": "ESWATINI",
            "CIV": "COTE D IVOIRE", "CPV": "CABO VERDE", "MKD": "NORTH MACEDONIA", "TLS": "TIMOR LESTE",
            "GMB": "GAMBIA", "BHS": "BAHAMAS", "COM": "COMOROS", "DOM": "DOMINICAN REPUBLIC",
            "PHL": "PHILIPPINES", "SDN": "SUDAN", "NER": "NIGER", "ARE": "UNITED ARAB EMIRATES",
            "CAF": "CENTRAL AFRICAN REPUBLIC", "MHL": "MARSHALL ISLANDS", "BRN": "BRUNEI DARUSSALAM",
            "VNM": "VIET NAM", "STP": "SAO TOME PRINCIPE", "KNA": "SAINT KITTS NEVIS",
            "VCT": "SAINT VINCENT GRENADINES", "LCA": "SAINT LUCIA", "TTO": "TRINIDAD TOBAGO",
            "ATG": "ANTIGUA BARBUDA", "BIH": "BOSNIA HERZEGOVINA", "SLB": "SOLOMON ISLANDS",
            "PNG": "PAPUA NEW GUINEA", "GNQ": "EQUATORIAL GUINEA", "GNB": "GUINEA BISSAU"}


def build_currencies():
    names, isos = iso_country_names()
    rows = wd.sparql("currencies", """SELECT ?iso ?en ?cur ?code ?rk ?end WHERE {
  ?c wdt:P298 ?iso ; p:P38 ?st . ?st ps:P38 ?cur ; wikibase:rank ?rk . FILTER(?rk != wikibase:DeprecatedRank)
  OPTIONAL { ?c rdfs:label ?en FILTER(LANG(?en) = "en") }
  OPTIONAL { ?st pq:P582 ?end } OPTIONAL { ?cur wdt:P498 ?code } }""")
    six = iso4217()
    by = collections.defaultdict(lambda: collections.defaultdict(set))
    en = {}
    for r in rows:
        if r["iso"] in isos and "end" not in r:
            by[r["iso"]][wd.qid(r["cur"])].add(r.get("code"))
            en[r["iso"]] = r.get("en", "")
    lb = wd.labels("labels_currencies", {c for v in by.values() for c in v})
    result, notes = {}, []
    for iso in isos:
        key = SIX_NAME.get(iso) or _norm(en.get(iso, ""))
        codes = six.get(key)
        if not codes:
            # not an ISO 4217 entity (Palestine): keep Wikidata's single current currency if unambiguous
            cur = [(q, sorted(c for c in cs if c and re.fullmatch("[A-Z]{3}", c))) for q, cs in by.get(iso, {}).items()]
            cur = [(q, c[0]) for q, c in cur if c]
            assert cur, iso
            notes.append("%s %s: no ISO 4217 entity; kept Wikidata %s" % (iso, isos[iso], [c for _, c in cur]))
            result[iso] = sorted((wd.zh_of(lb, q, cc), c, q) for q, c in cur)
            continue
        out = []
        for q, cs in by.get(iso, {}).items():
            hit = sorted(c for c in cs if c and c in codes)
            if hit:
                out.append((wd.zh_of(lb, q, cc), hit[0], q))
        wd_codes = {c for cs in by.get(iso, {}).values() for c in cs if c}
        if not out:
            notes.append("%s %s: no Wikidata currency matches ISO 4217 %s (Wikidata %s) -> dropped" % (
                iso, isos[iso], sorted(codes), sorted(wd_codes)))
            continue
        extra = wd_codes - codes
        if extra or codes - {c for _, c, _ in out}:
            notes.append("%s %s: kept %s; Wikidata-only %s; ISO-only %s" % (
                iso, isos[iso], [c for _, c, _ in out], sorted(extra), sorted(codes - {c for _, c, _ in out})))
        assert all(n for n, _, _ in out), (iso, out)
        result[iso] = sorted(out, key=lambda x: x[1])
    return result, notes


def build_currencies_collection():
    result, notes = build_currencies()
    vols, sz, se = [], [], []
    n = 0
    for vzh, ven, loci_data in manual.CAPITALS:
        loci = []
        for title, subs in loci_data:
            def cards_of(rows):
                out = []
                for country, _, iso in rows:
                    cur = result.get(iso)
                    if not cur:
                        continue
                    b = "；".join("%s %s" % (nm, code) for nm, code, _ in cur[:3])
                    out.append(card(country, b))
                return out
            if len(subs) == 1 and subs[0][0] is None:
                kids = cards_of(subs[0][1])
            else:
                kids = [group(st, cards_of(rows)) for st, rows in subs]
                kids = [k for k in kids if k["k"]]
            n += sum(1 for _ in kids)
            loci.append(group(title, kids))
        vols.append(loci)
        sz.append(vzh)
        se.append(ven)
    entry = make_collection(
        "currencies", "geo", "各国货币", "World Currencies",
        "位置 = 地区（同“世界各国首都”）· 子位置 = 每组 ≤8 国 · 卡 = 国家 → 货币 ISO 4217 代码",
        "Locus = region (as World Capitals) · card = country → currency + ISO 4217 code",
        "Wikidata（P38 现行货币、P498 ISO 4217 代码），与 ISO 4217 官方列表（SIX）核对",
        CC0, vols, sz, se)
    entry["_notes"] = notes
    return entry
