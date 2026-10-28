"""Cross-check manual.CAPITALS against Wikidata (CC0).

Fetches every sovereign state's ISO 3166-1 alpha-3 code, capital(s) and zh-cn /
zh-hans labels, caches the result in .cache/src/wikidata_capitals.json and prints every
mismatch for human review. Not part of the build; run when editing capitals.
"""
import json
import os
import sys
import urllib.parse
import urllib.error
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import manual  # noqa: E402
from common import SRC  # noqa: E402

QUERY = """
SELECT ?iso ?cl ?cll ?capl ?capll WHERE {
  VALUES ?iso { %s }
  ?country wdt:P298 ?iso .
  OPTIONAL { ?country rdfs:label ?cl . FILTER(LANG(?cl) IN ("zh-cn", "zh-hans", "zh")) BIND(LANG(?cl) AS ?cll) }
  OPTIONAL { ?country wdt:P36 ?cap .
             OPTIONAL { ?cap rdfs:label ?capl . FILTER(LANG(?capl) IN ("zh-cn", "zh-hans", "zh")) BIND(LANG(?capl) AS ?capll) } }
}
"""


def fetch():
    path = os.path.join(SRC, "wikidata_capitals.json")
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    isos = " ".join('"%s"' % iso for _, _, loci in manual.CAPITALS for _, subs in loci
                    for _, rows in subs for _, _, iso in rows)
    url = "https://query.wikidata.org/sparql?format=json&query=" + urllib.parse.quote(QUERY % isos)
    req = urllib.request.Request(url, headers={"User-Agent": "memory-palace-packs/0.3 (content check)"})
    import time
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                data = json.load(r)
            break
        except urllib.error.HTTPError as e:
            if e.code not in (429, 502, 503, 504) or attempt == 5:
                raise
            wait = int(e.headers.get("Retry-After") or 30)
            print("HTTP %d, retrying in %ds" % (e.code, wait))
            time.sleep(min(wait, 90))
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    return data


def main():
    sys.stdout.reconfigure(encoding="utf-8")
    data = fetch()
    pref = {"zh-cn": 0, "zh-hans": 1, "zh": 2}
    wd = {}
    for row in data["results"]["bindings"]:
        iso = row["iso"]["value"]
        e = wd.setdefault(iso, {"cl": {}, "caps": set(), "caps_all": set()})
        if "cl" in row:
            e["cl"][row["cll"]["value"]] = row["cl"]["value"]
        if "capl" in row:
            e["caps_all"].add(row["capl"]["value"])
            if row["capll"]["value"] in ("zh-cn", "zh-hans"):
                e["caps"].add(row["capl"]["value"])
    for e in wd.values():
        e["country"] = e["cl"][min(e["cl"], key=pref.get)] if e["cl"] else "?"
        if not e["caps"]:
            e["caps"] = e["caps_all"]
        e["en"] = set()
    n_ok = n_diff = 0
    for _, _, loci in manual.CAPITALS:
        for _, subs in loci:
            for _, rows in subs:
                for country, cap, iso in rows:
                    w = wd.get(iso)
                    if not w:
                        print("NO WIKIDATA", iso, country)
                        n_diff += 1
                        continue
                    main_cap = cap.split(" · ")[0].split("（")[0]
                    if main_cap in w["caps"] and country == w["country"]:
                        n_ok += 1
                    else:
                        n_diff += 1
                        print("%s  ours: %s → %s | wikidata: %s → %s (%s)" % (
                            iso, country, cap, w["country"], "/".join(sorted(w["caps"])),
                            "/".join(sorted(w["en"]))))
    print("exact matches: %d, differences to review: %d" % (n_ok, n_diff))


if __name__ == "__main__":
    main()
