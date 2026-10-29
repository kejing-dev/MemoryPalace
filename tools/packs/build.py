"""Generate all Memory Palace content packs into app/src/main/assets/packs/.

Run with the cache venv:  .cache/venv/Scripts/python.exe -I build.py
(sources are expected in .cache/src, see fetch.sh)
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # -I drops script dir? keep explicit

from common import OUT, write_json  # noqa: E402
import build_poetry as P  # noqa: E402
import build_other as O  # noqa: E402
import build_geo as G  # noqa: E402
import build_nature as N  # noqa: E402
import build_history as H  # noqa: E402
import build_common as C  # noqa: E402
import manual_v031 as MV  # noqa: E402
import verify as V  # noqa: E402

CATEGORY_ORDER = ["poetry", "language", "geo", "history", "nature", "common", "numbers"]
# v0.3.1 re-categorisation of the v0.3.0 collections (index only; volume files unchanged)
RECATEGORISE = {"jieqi": "common", "dynasty2": "history", "provinces": "geo", "capitals": "geo",
                "uspres": "history", "elements": "nature", "solar": "nature"}
ORDER = ["tang300", "song300", "sanzijing", "qianziwen", "lunyu",
         "words1000",
         "provinces", "capitals", "peaks", "rivers", "lakes-deserts", "whc-china", "currencies",
         "dynasty2", "uspres", "events", "paintings", "discoveries", "nobel",
         "elements", "solar", "constellations", "animals", "body",
         "jieqi", "jieqi72", "provinces-alias", "si-units", "math",
         "pi", "cards52", "digits100"]
NEW_V031 = ["peaks", "rivers", "lakes-deserts", "whc-china", "currencies", "events", "paintings",
            "discoveries", "nobel", "constellations", "animals", "body", "jieqi72", "provinces-alias",
            "si-units", "math"]


def generated(cid, category, zh, en, szh, sen):
    return {"id": cid, "category": category, "zh": zh, "en": en,
            "structureZh": szh, "structureEn": sen,
            "source": "App 运行时随机生成", "license": "无",
            "generated": True, "cards": 52 if cid == "cards52" else 25, "volumes": []}


ALIAS_REPORT, HOU_REPORT, ANIMAL_REPORT = [], [], []


def build_provinces_alias():
    kept, report = V.verify_aliases(MV.PROVINCE_ALIAS)
    ALIAS_REPORT.extend(report)
    return C.build_provinces_alias(kept)


def build_jieqi72():
    rep = V.verify_72hou(MV.JIEQI72)
    HOU_REPORT.extend(rep)
    bad = [r for r in rep if not r[2]]
    assert not bad, bad
    return C.build_jieqi72()


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in glob.glob(os.path.join(OUT, "*.json")):
        os.remove(f)
    cols = [
        P.build_tang(), P.build_song(), P.build_sanzijing(), P.build_qianziwen(), P.build_lunyu(),
        O.build_words(),
        O.build_jieqi(), O.build_dynasty(), O.build_provinces(), O.build_capitals(), O.build_uspres(),
        O.build_elements(), O.build_solar(),
        O.build_pi(),
        G.build_peaks(), G.build_rivers(), G.build_lakes_deserts(), G.build_whc(), G.build_currencies_collection(),
        H.build_events(), H.build_paintings(), H.build_discoveries(), H.build_nobel(),
        N.build_constellations(), N.build_animals(), N.build_body(),
        build_jieqi72(), build_provinces_alias(),
        C.build_si(), C.build_math(),
        generated("cards52", "numbers", "扑克顺序训练", "Card Deck Order",
                  "每次随机洗一副牌 · 位置 = 每 4 张一组 · 卡 = 第 n 张 → 牌面",
                  "Shuffled each time · locus = 4 cards · card = position → card"),
        generated("digits100", "numbers", "随机数字 100 位", "Random 100 Digits",
                  "每次随机生成 · 位置 = 每 20 位一段 · 卡 = 第 n–n+3 位 → 4 个数字",
                  "Generated each time · locus = 20 digits · card = positions → 4 digits"),
    ]
    for c in cols:
        c["category"] = RECATEGORISE.get(c["id"], c["category"])
    assert sorted(c["id"] for c in cols) == sorted(ORDER), set(ORDER) ^ {c["id"] for c in cols}
    cols.sort(key=lambda c: ORDER.index(c["id"]))
    cats = [CATEGORY_ORDER.index(c["category"]) for c in cols]
    assert cats == sorted(cats), [c["category"] for c in cols]
    notes = {}
    for c in cols:
        extra = {k: c.pop(k) for k in list(c) if k.startswith("_")}
        if c["id"] == "animals":
            ANIMAL_REPORT.extend((a["name"], "ok" if not a["gbif_mism"] else "GBIF differs: " + ",".join(a["gbif_mism"]),
                                  a["sci"]) for a in extra["_animals"])
        if "_notes" in extra or "_dropped" in extra:
            notes[c["id"]] = [str(x) for x in extra.get("_notes", []) + extra.get("_dropped", [])]
        print("%-10s %5d cards %2d vols %s" % (c["id"], c["cards"], len(c["volumes"]),
              {k: (v if not isinstance(v, list) else len(v)) for k, v in extra.items()}))
        if "_skipped" in extra:
            print("   skipped words:", " ".join(extra["_skipped"]))
    write_json(os.path.join(OUT, "index.json"), {"version": 1, "collections": cols})
    with open(os.path.join(V.wd.CACHE, "build_notes.json"), "w", encoding="utf-8") as f:
        json.dump(notes, f, ensure_ascii=False, indent=1)
    rep = V.run_samples()
    rep["provinces-alias (别称 vs zh.wikipedia)"] = [(p, "ok" if not bad else "dropped " + "、".join(bad), None)
                                                  for p, good, bad in ALIAS_REPORT]
    rep["animals (all, GBIF Backbone)"] = ANIMAL_REPORT
    rep["jieqi72 (zh.wikipedia + zh.wikisource)"] = [(k, "ok" if ok else "MISMATCH", h) for k, h, ok in HOU_REPORT]
    with open(os.path.join(V.wd.CACHE, "verify.json"), "w", encoding="utf-8") as f:
        json.dump(rep, f, ensure_ascii=False, indent=1)
    print("verification (second source):")
    for k, v in rep.items():
        bad = [x for x in v if x[1] != "ok"]
        print("  %-40s %3d checked, %d not ok" % (k, len(v), len(bad)))
        for x in bad:
            print("      ", x)
    new = [c for c in cols if c["id"] in NEW_V031]
    print("NEW v0.3.1: %d collections, %d cards" % (len(new), sum(c["cards"] for c in new)))
    print("song300 repairs (%d):" % len(P.SONG_REPAIRS), " ".join(P.SONG_REPAIRS))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    main()
