"""v0.3.1 'common' collections: SI units, math formulas, province aliases + area, 72 候."""
import collections
import re

from opencc import OpenCC

import manual
import manual_v031 as M
import verify as V
import wd
from build_geo import cn_provinces, pick_value
from common import MAX_KIDS, card, group, even_chunks, balanced_pack, make_collection

cc = OpenCC("t2s")
SI_UNIT = {"Q11573": "m", "Q11570": "kg", "Q25517": "m³", "Q25343": "m²", "Q25269": "J", "Q44395": "Pa",
           "Q33680": "rad", "Q11574": "s"}
SUP = str.maketrans("-0123456789", "⁻⁰¹²³⁴⁵⁶⁷⁸⁹")


def fmt_quantity(raw):
    """Wikidata amount string -> display. Exact short decimals are kept as is; long ones are
    rounded to 6 significant digits; very large / small use ×10ⁿ."""
    from decimal import Decimal
    d = Decimal(raw)
    if d == d.to_integral_value() and abs(d) < Decimal(10) ** 12:
        return str(int(d))
    t = d.normalize()
    exp = t.adjusted()
    digits = len(t.as_tuple().digits)
    if -4 <= exp < 12 and digits <= 10:
        return format(t, "f")
    if -5 <= exp < 12:  # long decimal: 6 significant digits
        q = Decimal(1).scaleb(exp - 5)
        return format(d.quantize(q).normalize(), "f")
    m = (d / Decimal(10) ** exp)
    m = format(m.quantize(Decimal("0.00001")).normalize(), "f")
    return "%s×10%s" % (m, str(exp).translate(SUP))


def build_si():
    facts = wd.FactLog("si-units")
    qids = " ".join("wd:" + q for q, _, _ in M.SI_CONVERSIONS)
    rows = wd.sparql("si_conversions", """SELECT ?u ?v ?si ?rk WHERE { VALUES ?u { %s }
  ?u p:P2370 ?st . ?st wikibase:rank ?rk ; psv:P2370 [wikibase:quantityAmount ?v; wikibase:quantityUnit ?si] .
  FILTER(?rk != wikibase:DeprecatedRank) }""" % qids)
    by = collections.defaultdict(set)
    for r in rows:
        by[wd.qid(r["u"])].add((r["v"], wd.qid(r["si"]), r["rk"]))
    conv = []
    for q, zh, sym in M.SI_CONVERSIONS:
        st = by[q]
        units = {u for _, u, _ in st}
        assert len(units) == 1 and next(iter(units)) in SI_UNIT, (q, st)
        v, note = pick_value([(v, rk) for v, _, rk in st], tol=0)
        assert v, (q, st)
        shown = fmt_quantity(v)
        f = "1 %s（%s）" % (zh, sym) if not zh.startswith("角度") else "1°"
        from decimal import Decimal
        exact = "×" not in shown and Decimal(shown) == Decimal(v)
        b = "%s%s %s" % ("" if exact else "≈ ", shown, SI_UNIT[next(iter(units))])
        facts.add(f, b, "si_conversions", q, [v], [shown])
        conv.append(card(f, b))
    loci = [group("七个基本单位", [card(a, b) for a, b in M.SI_BASE])]
    loci += [group(t, [card(a, b) for a, b in cs]) for t, cs in M.SI_DERIVED]
    loci += [group(t, [card("词头 %s" % a, b) for a, b in cs]) for t, cs in M.SI_PREFIX]
    loci.append(group("七个定义常数", [card(a, b) for a, b in M.SI_CONSTANTS]))
    for i, part in enumerate(even_chunks(conv, MAX_KIDS), 1):
        loci.append(group("单位换算 %s" % "一二三四"[i - 1], part))
    facts.save()
    return make_collection(
        "si-units", "common", "国际单位制与换算", "SI Units & Conversions",
        "位置 = 基本单位 / 导出单位 / 词头 / 定义常数 / 换算 · 卡 = 量或单位 → 名称、定义或 SI 值",
        "Locus = base / derived units, prefixes, defining constants, conversions · card = quantity or unit → SI",
        "SI 定义：BIPM《国际单位制》第 9 版（手写）；换算系数：Wikidata P2370（换算为 SI 单位）",
        "事实数据；Wikidata CC0", balanced_pack(loci))


def build_math():
    loci = [group(t, [card(a, b) for a, b in cs]) for t, cs in M.MATH]
    return make_collection(
        "math", "common", "常用数学公式", "Math Formulas",
        "位置 = 分支 · 卡 = 公式名称 → 公式",
        "Locus = branch · card = formula name → formula",
        "手写：中学数学教材通行公式", "数学公式，无版权", balanced_pack(loci))


def build_provinces_alias(verified_alias):
    """verified_alias: {province: alias text} already checked against a second source."""
    facts = wd.FactLog("provinces-alias")
    provs, rows = cn_provinces()
    areas = collections.defaultdict(list)
    for r in rows:
        if "area" in r:
            areas[provs[wd.qid(r["p"])][0]].append((r["area"], r["rk"], wd.qid(r["p"])))
    loci, notes = [], []
    titles = V.prefetch_en(list(provs))
    for region, items in manual.PROVINCES:
        subs = []
        for name, _ in items:
            cards = []
            if name in verified_alias:
                cards.append(card("%s的别称" % name, verified_alias[name]))
            st = areas.get(name, [])
            v, note = pick_value([(a, rk) for a, rk, _ in st], tol=0.02) if st else (None, "no area")
            if v and not V.en_number(titles, st[0][2], v, 0.03):
                notes.append("%s: area %s not confirmed by en.wikipedia infobox -> dropped" % (name, v))
                v = None
            if v:
                wan = float(v) / 10000
                shown = ("%.1f" % wan) if wan >= 1 else ("%d" % wd.fmt_int(v))
                b = "约 %s 万 km²" % shown if wan >= 1 else "约 %s km²" % shown
                cards.append(card("%s的面积" % name, b))
                facts.add("%s的面积" % name, b, "cn_provinces", st[0][2], [v], [shown])
            else:
                notes.append("%s: area dropped (%s)" % (name, note))
            if cards:
                subs.append(group(name, cards))
        loci.append(group(region, subs))
    facts.save()
    entry = make_collection(
        "provinces-alias", "common", "省级行政区 · 别称与面积", "Provinces · Aliases & Area",
        "位置 = 八大区域 · 子位置 = 省级行政区 · 卡 = 别称 / 面积",
        "Locus = region · sub-locus = province · cards = alias / area",
        "别称：手写通行别称，逐条与中文维基百科条目核对；面积：Wikidata P2046，四舍五入到 0.1 万 km²",
        "事实数据；Wikidata CC0", balanced_pack(loci))
    entry["_notes"] = notes
    return entry


def build_jieqi72():
    seasons = [("春", M.JIEQI72[0:6]), ("夏", M.JIEQI72[6:12]), ("秋", M.JIEQI72[12:18]), ("冬", M.JIEQI72[18:24])]
    loci = []
    for s, terms in seasons:
        subs = []
        for term, a, b, c in terms:
            a, b, c = (cc.convert(x) for x in (a, b, c))  # OpenCC standard forms, e.g. 鴽 -> 𫛪
            subs.append(group(term, [card("%s · 一候" % term, a), card("%s · 二候" % term, b),
                                     card("%s · 三候" % term, c)]))
        loci.append(group("%s季六节气" % s, subs))
    return make_collection(
        "jieqi72", "common", "七十二候", "72 Pentads",
        "位置 = 四季 · 子位置 = 节气 · 卡 = 节气 · 第 n 候 → 物候",
        "Locus = season · sub-locus = solar term · card = term + pentad → phenology",
        "《逸周书·时训解》通行文本（公有领域），逐条与中文维基文库 / 维基百科“七十二候”核对",
        "公有领域", balanced_pack(loci))
