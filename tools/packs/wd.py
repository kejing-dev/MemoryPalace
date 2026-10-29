"""Wikidata access with on-disk cache (.cache/wd/<name>.json).

Every request sends a User-Agent with a contact address and waits between
uncached requests (Wikimedia rate limits). Re-running the build uses the cache only;
delete .cache/wd/<name>.json to refresh one query.
"""
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

from common import CACHE

UA = ("MemoryPalaceContentBuilder/0.3.1 "
      "(https://github.com/kejing-dev/MemoryPalace; packs build script)")
WD_CACHE = os.path.join(CACHE, "wd")
ENDPOINT = "https://query.wikidata.org/sparql"
_last = [0.0]
PAUSE = 2.0


def _get(url, data=None):
    for attempt in range(8):
        wait = PAUSE - (time.time() - _last[0])
        if wait > 0:
            time.sleep(wait)
        req = urllib.request.Request(url, data=data, headers={
            "User-Agent": UA, "Accept": "application/sparql-results+json, application/json"})
        if data is not None:
            req.add_header("Content-Type", "application/x-www-form-urlencoded")
        try:
            with urllib.request.urlopen(req, timeout=180) as r:
                body = r.read()
            _last[0] = time.time()
            try:
                return json.loads(body.decode("utf-8"))
            except ValueError:
                if attempt == 7:
                    raise
                print("  truncated/invalid JSON, retry", file=sys.stderr)
                time.sleep(30)
                continue
        except urllib.error.HTTPError as e:
            _last[0] = time.time()
            if e.code not in (429, 500, 502, 503, 504) or attempt == 7:
                raise
            ra = e.headers.get("Retry-After")
            pause = int(ra) if ra and ra.isdigit() else 20 * (attempt + 1)
            print("  HTTP %d, retry in %ds" % (e.code, pause), file=sys.stderr)
            time.sleep(min(pause, 120))
        except (urllib.error.URLError, TimeoutError) as e:
            _last[0] = time.time()
            if attempt == 7:
                raise
            print("  %s, retry" % e, file=sys.stderr)
            time.sleep(20)


def _cached(name, fn):
    os.makedirs(WD_CACHE, exist_ok=True)
    path = os.path.join(WD_CACHE, name + ".json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    print("  fetching %s ..." % name, file=sys.stderr)
    data = fn()
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return data


def sparql(name, query):
    """Run a SPARQL query (POST), return list of {var: value-string} rows."""
    def fn():
        body = urllib.parse.urlencode({"query": query, "format": "json"}).encode()
        res = _get(ENDPOINT, body)
        return [{k: v["value"] for k, v in b.items()} for b in res["results"]["bindings"]]
    return _cached(name, fn)


def entities(name, qids, props="labels|claims|sitelinks"):
    """wbgetentities in batches of 50; returns {qid: entity}."""
    def fn():
        out = {}
        ids = list(dict.fromkeys(qids))
        for i in range(0, len(ids), 50):
            url = ("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=%s&ids=%s"
                   % (urllib.parse.quote(props), "|".join(ids[i:i + 50])))
            out.update(_get(url)["entities"])
        return out
    return _cached(name, fn)


def qid(uri):
    return uri.rsplit("/", 1)[-1]


ZH_PREF = ("zh-cn", "zh-hans", "zh-sg", "zh", "zh-hant", "zh-tw", "zh-hk")


def zh_label(labels, cc=None):
    """Best simplified-Chinese label from a {lang: label} dict (t2s-converted); None if absent."""
    for lang in ZH_PREF:
        if lang in labels:
            v = labels[lang]
            if isinstance(v, dict):
                v = v["value"]
            v = re.sub("[​-‏﻿]", "", v).strip()
            return cc.convert(v) if cc else v
    return None


def year_of(t):
    """SPARQL xsd:dateTime -> calendar year, negative for BCE.

    WDQS serialises dates with astronomical year numbering (year 0 = 1 BCE), so
    '-0215-...' is 216 BCE -> returns -216."""
    m = re.match(r"^([+-]?)(\d+)-", t)
    if not m:
        return None
    y = int(m.group(2))
    return -(y + 1) if m.group(1) == "-" else y


LABEL_LANGS = "zh-cn|zh-hans|zh-sg|zh|zh-hant|zh-tw|zh-hk|en"


def labels(name, qids):
    """{qid: {lang: label}} for zh variants + en, cached in .cache/wd/<name>.json (incremental)."""
    os.makedirs(WD_CACHE, exist_ok=True)
    path = os.path.join(WD_CACHE, name + ".json")
    raw = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            raw = json.load(f)
    ids = sorted(q for q in set(qids) - set(raw) if re.fullmatch(r"Q[0-9]+", q))
    if ids:
        print("  fetching %d labels for %s ..." % (len(ids), name), file=sys.stderr)
    for i in range(0, len(ids), 50):
        url = ("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
               "&props=labels&languages=%s&ids=%s" % (urllib.parse.quote(LABEL_LANGS),
                                                      "|".join(ids[i:i + 50])))
        for q, e in _get(url)["entities"].items():
            raw[q] = {k: v["value"] for k, v in e.get("labels", {}).items()}
    if ids:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(raw, f, ensure_ascii=False)
    return raw


def zh_of(lbls, q, cc):
    """Simplified Chinese label of q (zh-cn preferred, traditional variants converted)."""
    return zh_label(lbls.get(q, {}), cc)


def en_of(lbls, q):
    return lbls.get(q, {}).get("en")


# ---------------------------------------------------------------- fact log
# Builders record, for every card that shows a generated number, which cache file and
# entity the raw value came from. validate.py re-reads the cache and checks the card.
FACTS_DIR = os.path.join(CACHE, "facts")


class FactLog:
    def __init__(self, cid):
        self.cid = cid
        self.rows = []

    def add(self, f, b, src, qid_, raw, shown):
        """raw: list of raw value strings exactly as in the cache; shown: list of numbers
        (ints/strings) printed on the card back, in the same order."""
        self.rows.append({"f": f, "b": b, "src": src, "qid": qid_,
                          "raw": [str(x) for x in raw], "shown": [str(x) for x in shown]})

    def save(self):
        os.makedirs(FACTS_DIR, exist_ok=True)
        with open(os.path.join(FACTS_DIR, self.cid + ".json"), "w", encoding="utf-8") as fh:
            json.dump(self.rows, fh, ensure_ascii=False, indent=0)


def fmt_int(x):
    """Round a raw numeric string to an int (half up) for display."""
    from decimal import Decimal, ROUND_HALF_UP
    return int(Decimal(str(x)).quantize(Decimal(1), rounding=ROUND_HALF_UP))


def aliases(name, qids):
    """{qid: [zh aliases (any zh variant)]}, cached incrementally like labels()."""
    os.makedirs(WD_CACHE, exist_ok=True)
    path = os.path.join(WD_CACHE, name + ".json")
    raw = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            raw = json.load(f)
    ids = sorted(q for q in set(qids) - set(raw) if re.fullmatch(r"Q[0-9]+", q))
    for i in range(0, len(ids), 50):
        url = ("https://www.wikidata.org/w/api.php?action=wbgetentities&format=json"
               "&props=aliases&languages=%s&ids=%s" % (urllib.parse.quote(LABEL_LANGS),
                                                       "|".join(ids[i:i + 50])))
        for q, e in _get(url)["entities"].items():
            raw[q] = [a["value"] for lang in ZH_PREF for a in e.get("aliases", {}).get(lang, [])]
    if ids:
        with open(path, "w", encoding="utf-8") as f:
            json.dump(raw, f, ensure_ascii=False)
    return raw
