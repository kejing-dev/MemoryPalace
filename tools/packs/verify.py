"""Second-source checks for the v0.3.1 collections (run by build.py; results -> .cache/verify.json).

- zh.wikipedia: province 别称 (each alias must occur in the province's article) and the 72 候 text.
- en.wikipedia infoboxes: >= 10 % samples of peaks, rivers, lakes/deserts, constellations, paintings,
  events, moons, province areas (number / year must occur in the article's infobox).
- Built-in identities for SI conversions (1 mi = 1760 yd, 1 ly = c x Julian year, ...).
Other collections are checked inside their builders against full second sources
(UNESCO list, ISO 4217, GBIF, nobelprize.org, PubChem).
Wikipedia text is used for comparison only and is not copied into any pack.
"""
import collections
import json
import os
import random
import re
import sys
import urllib.parse
from decimal import Decimal

from opencc import OpenCC

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import wd  # noqa: E402

cc = OpenCC("t2s")
WIKI_CACHE = os.path.join(wd.CACHE, "wiki")


def wiki_text(lang, title, site="wikipedia"):
    os.makedirs(WIKI_CACHE, exist_ok=True)
    tag = lang if site == "wikipedia" else "%s_%s" % (lang, site)
    path = os.path.join(WIKI_CACHE, "%s_%s.txt" % (tag, re.sub(r'[\\/:*?"<>| ]', "_", title)))
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return f.read()
    url = ("https://%s.%s.org/w/api.php?action=query&prop=revisions&rvprop=content&rvslots=main"
           "&format=json&formatversion=2&redirects=1&titles=%s" % (lang, site, urllib.parse.quote(title)))
    data = wd._get(url)
    pages = data["query"]["pages"]
    text = pages[0]["revisions"][0]["slots"]["main"]["content"] if pages and "revisions" in pages[0] else ""
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    return text


def enwiki_titles(qids):
    out = {}
    ids = sorted(set(qids))
    import hashlib
    key = hashlib.sha1(" ".join(ids).encode()).hexdigest()[:12]
    ent = wd.entities("sitelinks_%s" % key, ids, props="sitelinks")
    for q, e in ent.items():
        sl = e.get("sitelinks", {}).get("enwiki")
        if sl:
            out[q] = sl["title"]
    return out


def infobox(text):
    """The leading template block (infobox) of an article, or the first 6000 chars."""
    m = re.search(r"\{\{\s*Infobox", text, re.I)
    if not m:
        return text[:6000]
    depth, i = 0, m.start()
    while i < len(text) - 1:
        if text.startswith("{{", i):
            depth += 1
            i += 2
            continue
        if text.startswith("}}", i):
            depth -= 1
            i += 2
            if depth == 0:
                return text[m.start():i]
            continue
        i += 1
    return text[m.start():m.start() + 8000]


def numbers_in(text):
    return [Decimal(x.replace(",", "")) for x in re.findall(r"\d[\d,]*(?:\.\d+)?", text)]


def sample(rows, frac=0.12, minimum=10, seed=31):
    rows = list(rows)
    k = min(len(rows), max(minimum, -(-len(rows) * frac // 1)))
    random.Random(seed).shuffle(rows)
    return rows[:int(k)]


def check_numbers(cid, facts, scale=None, tol=0.01, field_re=None):
    """facts rows with qid -> enwiki infobox must contain a number within tol of raw value."""
    rows = [r for r in facts if re.fullmatch(r"Q\d+", r["qid"])]
    smp = sample(rows)
    titles = enwiki_titles([r["qid"] for r in smp])
    res = []
    for r in smp:
        t = titles.get(r["qid"])
        if not t:
            res.append((r["f"], "no enwiki", None))
            continue
        box = infobox(wiki_text("en", t))
        raw = Decimal(r["raw"][0]) if re.fullmatch(r"-?[\d.]+", r["raw"][0]) else Decimal(wd.year_of(r["raw"][0]))
        nums = numbers_in(box)
        if scale:
            nums += [n * scale for n in nums]
        ok = any(abs(n - raw) <= abs(raw) * Decimal(str(tol)) + Decimal("0.5") * (tol == 0) for n in nums)
        res.append((r["f"], "ok" if ok else "MISMATCH", "%s vs enwiki:%s" % (r["raw"][0], t)))
    return res


def check_years(facts):
    rows = [r for r in facts if re.fullmatch(r"Q\d+", r["qid"])]
    smp = sample(rows)
    titles = enwiki_titles([r["qid"] for r in smp])
    res = []
    for r in smp:
        t = titles.get(r["qid"])
        if not t:
            res.append((r["f"], "no enwiki", None))
            continue
        text = wiki_text("en", t)
        y = abs(int(r["shown"][0]))
        ok = re.search(r"(?<!\d)%d(?!\d)" % y, infobox(text)) or re.search(r"(?<!\d)%d(?!\d)" % y, text[:4000])
        res.append((r["f"], "ok" if ok else "MISMATCH", "%s vs enwiki:%s" % (y, t)))
    return res


def load_facts(cid):
    with open(os.path.join(wd.FACTS_DIR, cid + ".json"), encoding="utf-8") as f:
        return json.load(f)


# ---------------------------------------------------------------- zh checks used as build filters
PROVINCE_ARTICLE = {"北京": "北京市", "天津": "天津市", "上海": "上海市", "重庆": "重庆市", "香港": "香港",
                    "澳门": "澳门", "台湾": "台湾", "内蒙古": "内蒙古自治区", "广西": "广西壮族自治区",
                    "西藏": "西藏自治区", "宁夏": "宁夏回族自治区", "新疆": "新疆维吾尔自治区"}


def verify_aliases(alias):
    """Keep only alias terms found in the province's zh.wikipedia article. -> (kept, report)"""
    kept, report = {}, []
    for prov, text in alias.items():
        title = PROVINCE_ARTICLE.get(prov, prov + "省")
        art = strip_links(cc.convert(wiki_text("zh", title)))
        terms = text.split("、")
        good = [t for t in terms if t in art]
        bad = [t for t in terms if t not in art]
        if good:
            kept[prov] = "、".join(good)
        report.append((prov, good, bad))
    return kept, report


def strip_links(text):
    text = re.sub(r"\[\[(?:[^\]|]*\|)?([^\]]*)\]\]", lambda m: m.group(1), text)
    return re.sub(r"<ref[^>]*>.*?</ref>|<ref[^>]*/>", "", text, flags=re.S)


def verify_72hou(rows):
    """Each 候 must occur in zh.wikipedia 七十二候 (月令七十二候集解 table) or in zh.wikisource
    逸周书 卷六 (时训解)."""
    a1 = re.sub(r"[，。；、\s]", "", strip_links(cc.convert(wiki_text("zh", "七十二候"))))
    a2 = re.sub(r"[，。；、\s]", "", cc.convert(wiki_text("zh", "逸周書/卷六", site="wikisource")))
    out = []
    for term, *hou in rows:
        for i, h in enumerate(hou, 1):
            h = cc.convert(h)
            out.append(("%s%d" % (term, i), h, h in a1 or h in a2))
    return out


# ---------------------------------------------------------------- SI identities
def si_identities():
    c = {r["f"]: Decimal(r["raw"][0]) for r in load_facts("si-units")}
    get = lambda k: next(v for f, v in c.items() if k in f)
    inch, ft, yd, mi = get("英寸"), get("英尺"), get("码"), get("英里")
    lb, oz, acre, au, ly, pc = get("磅"), get("盎司"), get("英亩"), get("天文单位"), get("光年"), get("秒差距")
    pi = Decimal("3.14159265358979323846264338327950288")
    checks = [
        ("1 ft = 12 in", ft == 12 * inch), ("1 yd = 3 ft", yd == 3 * ft), ("1 mi = 1760 yd", mi == 1760 * yd),
        ("1 lb = 16 oz", lb == 16 * oz), ("1 acre = 4840 yd²", acre == 4840 * yd * yd),
        ("1 ly = c × 365.25 d", ly == 299792458 * Decimal("365.25") * 86400),
        ("1 pc = 648000/π au (1e-12)", abs(pc - 648000 / pi * au) / pc < Decimal("1e-12")),
        ("1 市尺 = 1/3 m (1e-15)", abs(get("市尺") - Decimal(1) / 3) < Decimal("1e-15")),
        ("1 市亩 = 2000/3 m² (1e-12)", abs(get("市亩") - Decimal(2000) / 3) < Decimal("1e-12")),
    ]
    return [(n, "ok" if ok else "MISMATCH", None) for n, ok in checks]


def recheck(cid, kind, tol=None, only=None):
    """Re-check every generated card of a collection against en.wikipedia (same rule as the build filter)."""
    rows = [r for r in load_facts(cid) if re.fullmatch(r"Q\d+", r["qid"]) and (only is None or only(r))]
    titles = prefetch_en([r["qid"] for r in rows])
    out = []
    for r in rows:
        if kind == "num":
            ok = en_number(titles, r["qid"], r["raw"][0], tol) or zh_number(r["qid"], r["raw"][0])
        else:
            ok = en_year(titles, r["qid"], int(r["shown"][0]))
        out.append((r["f"], "ok" if ok else ("no enwiki" if ok is None else "MISMATCH"), r["raw"][0]))
    return out


GREEK_NAME = {"α": "alpha", "β": "beta", "γ": "gamma", "δ": "delta", "ε": "epsilon", "ζ": "zeta", "η": "eta",
              "θ": "theta", "ι": "iota", "κ": "kappa", "λ": "lambda", "μ": "mu", "ν": "nu", "ξ": "xi",
              "ο": "omicron", "π": "pi", "ρ": "rho", "σ": "sigma", "τ": "tau", "υ": "upsilon", "φ": "phi",
              "χ": "chi", "ψ": "psi", "ω": "omega"}


def check_constellations():
    """Brightest star (BSC5) vs the 'brightest' field of each constellation's en.wikipedia infobox."""
    rows = wd.sparql("constellations", "")  # cached by the build
    abbr2q = {}
    for r in rows:
        if re.fullmatch(r"[A-Z][A-Za-z]{2}", r["abbr"]):
            abbr2q.setdefault(r["abbr"], wd.qid(r["c"]))
    facts = load_facts("constellations")
    hr2q = collections.defaultdict(set)
    for x in wd.sparql("constellation_stars", ""):
        hr2q[x["code"]].add(wd.qid(x["s"]))
    lbl = wd.labels("labels_constellations", [])
    titles = prefetch_en(list(abbr2q.values()))
    out = []
    for r in facts:
        m = re.match(r"([A-Z][A-Za-z]{2}) · 最亮星 .*?（?([α-ω])?([0-9]*) ?([A-Z][A-Za-z]{2})?）? · ", r["b"])
        abbr = r["b"][:3]
        g = re.search(r"([α-ω])(\d?) %s" % abbr, r["b"])
        t = titles.get(abbr2q[abbr])
        text = wiki_text("en", t) if t else ""
        line = " ".join(l for l in text.split("\n") if re.match(r"\s*\|\s*brightest", l, re.I))
        line = line.lower()
        ok = None
        if line:
            letters = re.findall(r"([α-ω])\d? %s" % abbr, r["b"])
            ok = any(l in line or GREEK_NAME[l] in line for l in letters)
            for q in hr2q.get(r["qid"], ()):
                en = (lbl.get(q, {}).get("en") or "").lower()
                ok = ok or (bool(en) and en in line)
        out.append((r["f"], "ok" if ok else ("no infobox" if ok is None else "MISMATCH"), r["b"]))
    return out


def run_samples():
    rep = {}
    rep["peaks (all, en.wikipedia)"] = recheck("peaks", "num", 0.005)
    rep["rivers (all, en.wikipedia)"] = recheck("rivers", "num", 0.02)
    rep["lakes-deserts (all, en.wikipedia)"] = recheck("lakes-deserts", "num", 0.05)
    rep["paintings (all, en.wikipedia)"] = recheck("paintings", "year")
    rep["events (all, en.wikipedia)"] = recheck("events", "year")
    rep["discoveries planets/moons (all, en.wikipedia)"] = recheck(
        "discoveries", "year", only=lambda r: not r["f"].startswith("发现"))
    rep["provinces-alias area (all, en.wikipedia)"] = recheck("provinces-alias", "num", 0.03)
    rep["si-units (identities)"] = si_identities()
    rep["constellations (all, en.wikipedia infobox)"] = check_constellations()
    return rep


# ---------------------------------------------------------------- per-item en.wikipedia checks (build filters)
CONVERT = {"mi": ("km", Decimal("1.609344")), "km": ("km", Decimal(1)), "ft": ("m", Decimal("0.3048")),
           "m": ("m", Decimal(1)), "sqmi": ("km2", Decimal("2.589988110336")),
           "sq mi": ("km2", Decimal("2.589988110336")), "km2": ("km2", Decimal(1)), "sqkm": ("km2", Decimal(1)),
           "ha": ("km2", Decimal("0.01")), "acre": ("km2", Decimal("0.0040468564224"))}


def prefetch_en(qids):
    """Fetch enwiki wikitext for many items, 50 titles per request; returns {qid: title}."""
    titles = enwiki_titles(qids)
    todo = []
    for q, t in titles.items():
        path = os.path.join(WIKI_CACHE, "en_%s.txt" % re.sub(r'[\/:*?"<>| ]', "_", t))
        if not os.path.exists(path):
            todo.append(t)
    os.makedirs(WIKI_CACHE, exist_ok=True)
    for i in range(0, len(todo), 50):
        batch = todo[i:i + 50]
        url = ("https://en.wikipedia.org/w/api.php?action=query&prop=revisions&rvprop=content&rvslots=main"
               "&format=json&formatversion=2&titles=%s" % urllib.parse.quote("|".join(batch)))
        data = wd._get(url)
        norm = {n["to"]: n["from"] for n in data["query"].get("normalized", [])}
        for pg in data["query"]["pages"]:
            t = norm.get(pg["title"], pg["title"])
            text = pg["revisions"][0]["slots"]["main"]["content"] if "revisions" in pg else ""
            m = re.match(r"#REDIRECT\s*\[\[([^\]]+)\]\]", text, re.I)
            if m:
                text = wiki_text("en", m.group(1))
            with open(os.path.join(WIKI_CACHE, "en_%s.txt" % re.sub(r'[\/:*?"<>| ]', "_", t)), "w",
                      encoding="utf-8") as f:
                f.write(text)
    return titles


def _converted(box):
    out = []
    for m in re.finditer(r"\{\{\s*(?:convert|cvt)\s*\|\s*([\d,.]+)\s*\|(?:\s*[-–to]+\s*\|\s*[\d,.]+\s*\|)?\s*([a-z0-9 ]+?)\s*[|}]",
                         box, re.I):
        try:
            v = Decimal(m.group(1).replace(",", ""))
        except Exception:
            continue
        u = m.group(2).strip().lower()
        if u in CONVERT:
            out.append(v * CONVERT[u][1])
    return out


def en_number(titles, qid, raw, tol, scale=None):
    """True/False if the enwiki infobox has / lacks a number within tol of raw; None if no article."""
    t = titles.get(qid)
    if not t:
        return None
    text = wiki_text("en", t)
    box = infobox(text) + " " + re.sub(r"<ref[^>]*>.*?</ref>", "", text[:8000], flags=re.S)
    raw = Decimal(raw)
    nums = numbers_in(box) + _converted(box)
    if scale:
        nums += [n * scale for n in nums]
    if any(abs(n - raw) <= abs(raw) * Decimal(str(tol)) for n in nums):
        return True
    # otherwise the exact value must be quoted somewhere in the article with a unit
    # (contested figures, e.g. the Nile's 6,650 km, are discussed in the body text)
    if raw == raw.to_integral_value() and raw >= 100:
        n = int(raw)
        pat = r"(?<![\d.,])(%d|%s)(?![\d])\s*(?:\||&nbsp;|\s)?\s*(?:km|m\b|sq|square|mi)" % (n, "{:,}".format(n))
        if re.search(pat, re.sub(r"<ref[^>]*>.*?</ref>", "", text, flags=re.S)):
            return True
    return False


def en_year(titles, qid, year):
    t = titles.get(qid)
    if not t:
        return None
    text = wiki_text("en", t)
    hay = infobox(text) + "\n" + text[:5000]
    y = abs(year)
    if re.search(r"(?<!\d)%d(?!\d)" % y, hay):
        return True
    for a, b in re.findall(r"(?<!\d)(\d{3,4})\s*(?:–|-|&ndash;|to)\s*(\d{2,4})(?!\d)", hay):
        a, b = int(a), int(b)
        if b < 100:
            b = a // 100 * 100 + b
        if a <= y <= b:
            return True
    return False


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    for k, v in run_samples().items():
        bad = [x for x in v if x[1] != "ok"]
        print(k, len(v), "checked,", len(bad), "not ok")
        for x in bad:
            print("   ", x)


def zh_number(qid, raw):
    """Fallback second source: the zh.wikipedia article quotes the exact value with a unit."""
    ids = [qid]
    ent = wd.entities("sitelinks_zh_%s" % qid, ids, props="sitelinks")
    sl = ent.get(qid, {}).get("sitelinks", {}).get("zhwiki")
    if not sl:
        return None
    text = cc.convert(re.sub(r"<ref[^>]*>.*?</ref>", "", wiki_text("zh", sl["title"]), flags=re.S))
    raw = Decimal(raw)
    if raw != raw.to_integral_value():
        return False
    n = int(raw)
    forms = [str(n), "{:,}".format(n)]
    if n >= 10000 and n % 100 == 0:
        forms.append(("%g" % (n / 10000)) + "万")
    pat = r"(?<![\d.,])(%s)(?![\d])\s*(?:\||&nbsp;|\s)?\s*(?:公里|千米|平方公里|平方千米|米|km|m\b)" % "|".join(
        re.escape(f) for f in forms)
    return bool(re.search(pat, text))
