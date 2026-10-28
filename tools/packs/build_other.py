"""Non-poetry collections."""
import csv
import os
import re

from common import (SRC, MAX_KIDS, card, group, chunk, even_chunks, balanced_pack,
                    make_collection)
import manual


# ---------------------------------------------------------------- elements
PERIODS = [(1, 1, 2), (2, 3, 10), (3, 11, 18), (4, 19, 36), (5, 37, 54)]


def z_ranges(zs):
    """[55, 56, 72, 73] -> '55–56、72–73'"""
    runs, start, prev = [], zs[0], zs[0]
    for z in zs[1:] + [None]:
        if z is not None and z == prev + 1:
            prev = z
            continue
        runs.append("%d" % start if start == prev else "%d–%d" % (start, prev))
        if z is not None:
            start = prev = z
    return "、".join(runs)


def build_elements():
    el = {z: (s, zh, en) for z, s, zh, en in manual.ELEMENTS}
    assert sorted(el) == list(range(1, 119))

    def c(z):
        s, zh, en = el[z]
        return card("%d 号" % z, "%s %s %s" % (s, zh, en))

    def locus(title, zs):
        cards = [c(z) for z in zs]
        if len(cards) <= MAX_KIDS:
            return group(title, cards)
        subs = []
        for part in even_chunks(zs, MAX_KIDS):
            subs.append(group(z_ranges(part) + " 号", [c(z) for z in part]))
        return group(title, subs)

    loci = [locus("第 %d 周期" % p, list(range(a, b + 1))) for p, a, b in PERIODS]
    loci.append(locus("第 6 周期", [55, 56] + list(range(72, 87))))
    loci.append(locus("镧系", list(range(57, 72))))
    loci.append(locus("第 7 周期", [87, 88] + list(range(104, 119))))
    loci.append(locus("锕系", list(range(89, 104))))
    return make_collection(
        "elements", "science", "元素周期表 118", "Periodic Table",
        "位置 = 周期（镧系、锕系单列）· 子位置 = 每组 ≤8 · 卡 = 原子序数 → 符号 中文名 英文名",
        "Locus = period (lanthanides/actinides separate) · card = atomic number → symbol, names",
        "IUPAC 元素名；中文名依全国科学技术名词审定委员会（2017 年 113/115/117/118 号定名）",
        "事实数据，无版权", [loci])


def simple_loci(data):
    return [group(t, [card(f, b) for f, b in cards]) for t, cards in data]


def build_solar():
    return make_collection(
        "solar", "science", "太阳系", "Solar System",
        "位置 = 太阳 · 八大行星 · 矮行星 · 卡 = 天体 → 一个事实",
        "Locus = Sun, planets, dwarf planets · card = body → fact",
        "NASA Solar System Exploration 公开资料（数字取保守近似值）",
        "事实数据，无版权", [simple_loci(manual.SOLAR)])


def build_jieqi():
    return make_collection(
        "jieqi", "histgeo", "二十四节气", "24 Solar Terms",
        "位置 = 春夏秋冬 · 卡 = 节气 → 公历日期 · 含义",
        "Locus = season · card = term → Gregorian date · meaning",
        "GB/T 33661-2017《农历的编算和颁行》节气名；日期为近年公历常见日（前后 1 天浮动）",
        "事实数据，无版权", [simple_loci(manual.JIEQI)])


def build_dynasty():
    loci = []
    fronts = set()
    for period, dyns in manual.DYNASTY:
        subs = []
        for d, events in dyns:
            cards = []
            for y, ev in events:
                assert y not in fronts, y
                fronts.add(y)
                cards.append(card(y, "%s · %s" % (d, ev)))
            subs.append(group(d, cards))
        loci.append(group(period, subs))
    return make_collection(
        "dynasty2", "histgeo", "中国朝代（扩充）", "Chinese Dynasties (extended)",
        "位置 = 时期 · 子位置 = 朝代 · 卡 = 年份 → 朝代 · 一件事",
        "Locus = era · sub-locus = dynasty · card = year → dynasty · event",
        "通行中学历史教材年代（夏商周采用夏商周断代工程年表）",
        "事实数据，自写", [loci])


def build_provinces():
    return make_collection(
        "provinces", "histgeo", "中国省级行政区", "Chinese Provinces",
        "位置 = 八大区域 · 卡 = 名称 → 简称 · 省会/首府",
        "Locus = region · card = name → abbreviation · capital",
        "中华人民共和国行政区划（民政部公开信息）",
        "事实数据，无版权", [simple_loci(manual.PROVINCES)])


def build_capitals():
    vols, sz, se = [], [], []
    n = 0
    for vzh, ven, loci_data in manual.CAPITALS:
        loci = []
        for title, subs in loci_data:
            if len(subs) == 1 and subs[0][0] is None:
                kids = [card(c, cap) for c, cap, _ in subs[0][1]]
            else:
                kids = [group(st, [card(c, cap) for c, cap, _ in rows]) for st, rows in subs]
            loci.append(group(title, kids))
            n += sum(len(rows) for _, rows in subs)
        vols.append(loci)
        sz.append(vzh)
        se.append(ven)
    assert n == 195, n
    entry = make_collection(
        "capitals", "histgeo", "世界各国首都", "World Capitals",
        "位置 = 地区 · 子位置 = 每组 ≤8 国 · 卡 = 国家 → 首都",
        "Locus = region · sub-locus = ≤8 countries · card = country → capital",
        "193 个联合国会员国 + 梵蒂冈 + 巴勒斯坦；中文译名依外交部通行译名，与 Wikidata（CC0）首都条目核对",
        "事实数据；Wikidata CC0", vols, sz, se)
    return entry


def ordinal(n):
    if 10 <= n % 100 <= 20:
        suf = "th"
    else:
        suf = {1: "st", 2: "nd", 3: "rd"}.get(n % 10, "th")
    return "%d%s" % (n, suf)


def build_uspres():
    pres = manual.USPRES
    assert len(pres) == 47
    loci = []
    for i, part in enumerate(chunk(list(enumerate(pres, 1)), 8)):
        first, last = part[0][0], part[-1][0]
        y0 = re.search(r"\d{4}", part[0][1]).group(0)
        y1 = re.findall(r"\d{4}", part[-1][1])[-1] if last != 47 else "today"
        loci.append(group("%s–%s" % (y0, y1),
                          [card(ordinal(k), v) for k, v in part]))
    return make_collection(
        "uspres", "histgeo", "美国总统（英文）", "US Presidents",
        "位置 = 每 8 任一段 · 卡 = 第 n 任 → 姓名 · 任期",
        "Locus = 8 presidencies · card = ordinal → name · term",
        "White House / US public record", "事实数据，无版权", [loci])


# ---------------------------------------------------------------- words1000
POS = re.compile(r"^(?:[a-z]+\.\s*)+")
BRACKETS = re.compile(r"\[[^\]]*\]|\([^)]*\)|（[^）]*）|<[^>]*>")
OVERRIDES = manual.WORD_GLOSS


def first_sense(trans, max_len=20):
    lines = [l.strip() for l in trans.replace("\\r", "").split("\\n") if l.strip()]
    for line in lines:
        if line.startswith("[") or line.startswith("abbr"):
            continue
        body = POS.sub("", line)
        body = BRACKETS.sub("", body).strip()
        senses = [s.strip() for s in re.split(r"[,，;；]", body) if s.strip()]
        senses = [s.replace("...", "…") for s in senses if re.search(r"[一-鿿]", s)]
        senses = list(dict.fromkeys(senses))
        if not senses:
            continue
        out = senses[0][:max_len]
        for s in senses[1:]:
            if len(out) + 1 + len(s) > max_len:
                break
            out += "；" + s
            if len(out) >= 8:
                break
        return out
    return None


def build_words():
    rows = []
    with open(os.path.join(SRC, "ecdict.csv"), encoding="utf-8") as f:
        for x in csv.DictReader(f):
            try:
                frq = int(x["frq"])
            except ValueError:
                continue
            if frq <= 0:
                continue
            rows.append((frq, x["word"], x["translation"]))
    rows.sort()
    words, seen, skipped = [], set(), []
    for frq, w, t in rows:
        if len(words) >= 1000:
            break
        if not re.fullmatch(r"[a-z]+", w) and w != "I":
            skipped.append(w)
            continue
        tl = t.strip()
        if tl.startswith("abbr") or not tl:
            skipped.append(w)
            continue
        if w in seen:
            continue
        sense = OVERRIDES.get(w) or first_sense(t)
        if not sense:
            skipped.append(w)
            continue
        seen.add(w)
        words.append((frq, w, sense))
    assert len(words) == 1000
    cards = [card(w, s) for _, w, s in words]
    loci = []
    for i, part in enumerate(chunk(cards, 8)):
        a = i * 8 + 1
        loci.append(group("%d–%d" % (a, a + len(part) - 1), part))
    vols = chunk(loci, 15)  # 15 loci x 8 = 120 cards
    entry = make_collection(
        "words1000", "language", "英语高频 1000 词", "English Top 1000",
        "位置 = 每 8 词一组（按词频）· 卡 = 英文 → 中文释义",
        "Locus = 8 words by frequency · card = word → Chinese gloss",
        "ECDICT（skywind3000/ECDICT）COCA 词频 frq 前 1000（略去专有名词/缩写），释义取首义",
        "MIT（ECDICT）", vols)
    entry["_skipped"] = skipped
    entry["_maxrank"] = words[-1][0]
    return entry


# ---------------------------------------------------------------- pi
def pi_digits_machin(n):
    """n digits after the decimal point via Machin's formula in integer arithmetic."""
    guard = 10
    scale = 10 ** (n + guard)

    def arctan_inv(x):
        total = term = scale // x
        x2 = x * x
        k = 1
        sign = -1
        while term:
            term //= x2
            total += sign * (term // (2 * k + 1))
            sign = -sign
            k += 1
        return total

    pi = 4 * (4 * arctan_inv(5) - arctan_inv(239))
    s = str(pi)
    assert s[0] == "3"
    return s[1:n + 1]


def pi_digits_mpmath(n):
    import mpmath
    mpmath.mp.dps = n + 20
    s = mpmath.nstr(mpmath.pi, n + 15, strip_zeros=False)
    assert s.startswith("3.")
    return s[2:2 + n]


KNOWN_PI_50 = "14159265358979323846264338327950288419716939937510"


def build_pi():
    a = pi_digits_machin(200)
    b = pi_digits_mpmath(200)
    assert a == b, "pi methods disagree"
    assert a.startswith(KNOWN_PI_50)
    cards = [card("π 第 %d–%d 位" % (i + 1, i + 4), a[i:i + 4]) for i in range(0, 200, 4)]
    loci = []
    for j, part in enumerate(chunk(cards, 8)):
        start = j * 32 + 1
        end = min(start + 31, 200)
        loci.append(group("第 %d–%d 位" % (start, end), part))
    return make_collection(
        "pi", "numbers", "圆周率前 200 位", "Pi · 200 digits",
        "位置 = 每 32 位一段 · 卡 = 第 n–n+3 位 → 4 个数字",
        "Locus = 32 digits · card = positions → 4 digits",
        "Machin 公式整数运算计算，并与 mpmath 交叉验证（小数点后 200 位）",
        "数学常数，无版权", [loci])
