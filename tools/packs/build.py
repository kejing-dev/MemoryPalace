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


def generated(cid, category, zh, en, szh, sen):
    return {"id": cid, "category": category, "zh": zh, "en": en,
            "structureZh": szh, "structureEn": sen,
            "source": "App 运行时随机生成", "license": "无",
            "generated": True, "cards": 52 if cid == "cards52" else 25, "volumes": []}


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
        generated("cards52", "numbers", "扑克顺序训练", "Card Deck Order",
                  "每次随机洗一副牌 · 位置 = 每 4 张一组 · 卡 = 第 n 张 → 牌面",
                  "Shuffled each time · locus = 4 cards · card = position → card"),
        generated("digits100", "numbers", "随机数字 100 位", "Random 100 Digits",
                  "每次随机生成 · 位置 = 每 20 位一段 · 卡 = 第 n–n+3 位 → 4 个数字",
                  "Generated each time · locus = 20 digits · card = positions → 4 digits"),
    ]
    for c in cols:
        extra = {k: c.pop(k) for k in list(c) if k.startswith("_")}
        print("%-10s %5d cards %2d vols %s" % (c["id"], c["cards"], len(c["volumes"]),
              {k: (v if not isinstance(v, list) else len(v)) for k, v in extra.items()}))
        if "_skipped" in extra:
            print("   skipped words:", " ".join(extra["_skipped"]))
    write_json(os.path.join(OUT, "index.json"), {"version": 1, "collections": cols})
    print("song300 repairs (%d):" % len(P.SONG_REPAIRS), " ".join(P.SONG_REPAIRS))


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
    main()
