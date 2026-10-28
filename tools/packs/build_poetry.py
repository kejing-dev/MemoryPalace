"""Poetry collections: tang300, song300, sanzijing, qianziwen, lunyu."""
import json
import os
import re

from opencc import OpenCC

from common import (SRC, MAX_KIDS, card, group, count_cards, chunk, even_chunks,
                    balanced_pack, make_collection, strip_punct, cn_num)
import manual

CC = OpenCC("t2s")

# Variant / source-typo normalisation applied after OpenCC t2s.
# Each entry is justified in README.md.
VARIANTS = {
    "茍": "苟",   # 三字经 source uses 茍 (ji4) for 苟 (gou3)
    "崐": "昆",   # 千字文 玉出昆冈
    "臯": "皋",   # 千字文 林皋幸即
    "竝": "并",   # 千字文 并皆佳妙
    "碁": "棋",   # 三字经 能赋棋
    "爲": "为",
    "裏": "里",
    "鬥": "斗",
}
PHRASE_FIX = {
    "祥训诂": "详训诂",   # 三字经 source typo
    "三易祥": "三易详",   # 三字经 source typo
    "周撤东": "周辙东",   # 三字经 source typo
}

CLAUSE_SEP = "，,：:"
SENT_END = "。？！；?!;"


def s2(text):
    t = CC.convert(text)
    for a, b in VARIANTS.items():
        t = t.replace(a, b)
    for a, b in PHRASE_FIX.items():
        t = t.replace(a, b)
    return t


ANNOT = re.compile(r"\s*[（(][^）)]*([）)]|$)")


def clean_line(line):
    line = ANNOT.sub("", line)          # drop "(一作：…)" notes, incl. truncated ones
    line = re.sub(r"[《》〈〉“”‘’\"']", "", line)
    line = line.replace("、", "")
    return line.strip()


def sentences(paragraphs):
    """-> list of sentences, each a list of clauses (punctuation removed)."""
    text = clean_line("".join(paragraphs))
    out, cur, buf = [], [], ""
    for ch in text:
        if ch in CLAUSE_SEP:
            if buf:
                cur.append(buf)
            buf = ""
        elif ch in SENT_END:
            if buf:
                cur.append(buf)
            buf = ""
            if cur:
                out.append(cur)
            cur = []
        elif ch.isspace():
            continue
        else:
            buf += ch
    if buf:
        cur.append(buf)
    if cur:
        out.append(cur)
    return out


def pair_cards(paragraphs):
    sents = sentences(paragraphs)
    # merge single-clause sentences into the following sentence (or previous if last)
    merged = []
    carry = []
    for s in sents:
        s = carry + s
        carry = []
        if len(s) == 1:
            carry = s
            continue
        merged.append(s)
    if carry:
        if merged:
            merged[-1] = merged[-1] + carry
        else:
            merged.append(carry)
    cards = []
    for s in merged:
        if len(s) == 1:
            raise ValueError("cannot pair single clause: %s" % s)
        i = 0
        n = len(s)
        while i < n:
            if n - i == 3:
                cards.append(card(s[i], s[i + 1] + " " + s[i + 2]))
                i += 3
            else:
                cards.append(card(s[i], s[i + 1]))
                i += 2
    for c in cards:
        for k in ("f", "b"):
            if not strip_punct(c[k]):
                raise ValueError("empty clause in %r" % c)
    return cards


def poem_subloci(title, author, cards):
    """A poem becomes one sub-locus, or several parts (≤8 cards each) if long."""
    parts = even_chunks(cards, MAX_KIDS)
    if len(parts) == 1:
        return [group("《%s》" % title, parts[0], a=author)]
    return [group("《%s》%s" % (title, cn_num(i)), p, a=author) for i, p in enumerate(parts, 1)]


def author_loci(order, by_author):
    """Poet loci; a poet with >8 sub-loci spreads over '李白 1', '李白 2'…"""
    loci = []
    for a in order:
        subs = by_author[a]
        if len(subs) <= MAX_KIDS:
            loci.append(group(a, subs))
        else:
            for i, part in enumerate(even_chunks(subs, MAX_KIDS), 1):
                loci.append(group("%s %d" % (a, i), part))
    return loci


def load(name):
    with open(os.path.join(SRC, name), encoding="utf-8") as f:
        return json.load(f)


# Fixes to the 蒙学 唐诗三百首 file found by diffing it against 全唐诗/唐诗三百首.json
# (tools/packs/README.md lists them).
TANG_TITLE_FIX = {"同從弟銷南齋玩月憶山陰": "同從弟南齋玩月憶山陰崔少府"}
TANG_PARAGRAPH_SLICE = {"長信怨": (4, 6)}       # file merges all 5 长信秋词; 300 has only 其三
TANG_TEXT_FROM_FULL = {"贈衛八處士": "贈衛八處士"}  # 蒙学 file stops after 4 of 12 couplets


def build_tang():
    d = load("蒙学_tangshisanbaishou.json")
    full = {p["title"]: p for p in load("全唐诗_唐诗三百首.json")}
    order, by_author = [], {}
    npoems = 0
    for sec in d["content"]:
        for p in sec["content"]:
            author = s2(p["author"])
            chapter = p["chapter"]
            paragraphs = p["paragraphs"]
            if chapter in TANG_PARAGRAPH_SLICE:
                i, j = TANG_PARAGRAPH_SLICE[chapter]
                paragraphs = paragraphs[i:j]
            if chapter in TANG_TEXT_FROM_FULL:
                paragraphs = [x.replace("荅", "答") for x in full[TANG_TEXT_FROM_FULL[chapter]]["paragraphs"]]
            title = s2(TANG_TITLE_FIX.get(chapter, chapter)).replace("・", "·")
            cards = pair_cards([s2(x) for x in paragraphs])
            npoems += 1
            if author not in by_author:
                by_author[author] = []
                order.append(author)
            by_author[author].extend(poem_subloci(title, author, cards))
    loci = author_loci(order, by_author)
    vols = balanced_pack(loci)
    entry = make_collection(
        "tang300", "poetry", "唐诗三百首", "300 Tang Poems",
        "位置 = 诗人 · 子位置 = 一首 · 卡 = 一联",
        "Locus = poet · sub-locus = poem · card = couplet",
        "chinese-poetry 蒙学/唐诗三百首（蘅塘退士编，OpenCC 转简体）",
        "原文公有领域；数据集 MIT", vols)
    entry["_poems"] = npoems
    return entry


# --- 宋词三百首 repair -------------------------------------------------------
# The chinese-poetry 宋词三百首.json lost every character outside its original
# code page (e.g. 紞如三鼓 -> 如三鼓, 鹈鴂 -> 鹈, author 赵令畤 -> 赵令).  The full
# 全宋词 files of the same repo keep them, so for each ci we align against the
# best match by the same author and re-insert the missing characters only
# (replacements/deletions are left as the 三百首 reading).
SONG_AUTHOR_FIX = {"赵令": "赵令畤", "张": "张鎡", "韩": "韩疁"}
SONG_RHYTHMIC_FIX = {("周邦彦", "大"): "大酺"}   # 酺 dropped in the source title
SONG_SKIP_INSERT = {("李清照", "声声慢", "忺")}   # use the 通行本 reading 堪 instead
SONG_PHRASE_FIX = {
    "如今有谁摘": "如今有谁堪摘",   # 李清照《声声慢》
    "把洒黄昏": "把酒黄昏",         # 李清照《醉花阴》 typo in both files
    "过了青明时节": "过了清明时节",  # 辛弃疾《念奴娇》 typo in both files
}
CJK_RE = re.compile(r"[㐀-鿿𠀀-𱍏]")
SONG_REPAIRS = []


def load_full_ci():
    import glob
    full = {}
    for f in sorted(glob.glob(os.path.join(SRC, "ci", "ci.song.*.json"))):
        for p in load(os.path.relpath(f, SRC)):
            full.setdefault(p["author"], []).append("".join(p["paragraphs"]))
    return full


def repair_ci(author, rhythmic, text, full):
    import difflib
    cands = full.get(author, []) + (full.get("张", []) if author == "张鎡" else [])
    a_idx = [i for i, ch in enumerate(text) if CJK_RE.match(ch)]
    a = "".join(text[i] for i in a_idx)
    best, best_r = None, 0.0
    for c in cands:
        c_idx = [i for i, ch in enumerate(c) if CJK_RE.match(ch)]
        cn = "".join(c[i] for i in c_idx)
        sm = difflib.SequenceMatcher(None, a, cn, autojunk=False)
        if sm.quick_ratio() <= best_r:
            continue
        r = sm.ratio()
        if r > best_r:
            best, best_r = (c, c_idx, cn), r
    if not best or best_r < 0.9:
        return text
    c, c_idx, cn = best
    inserts = []  # (position in text, chars)
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(None, a, cn, autojunk=False).get_opcodes():
        if tag != "insert":
            continue
        chars = cn[j1:j2]
        if any((author_s, rhythmic, chars) in SONG_SKIP_INSERT for author_s in (CC.convert(author),)):
            continue
        # does the inserted run start a clause in the full text?
        pos_full = c_idx[j1]
        starts_clause = pos_full == 0 or not CJK_RE.match(c[pos_full - 1])
        if starts_clause or i1 == 0:
            at = a_idx[i1] if i1 < len(a_idx) else len(text)
        else:
            at = a_idx[i1 - 1] + 1
        inserts.append((at, chars))
    for at, chars in sorted(inserts, reverse=True):
        text = text[:at] + chars + text[at:]
        SONG_REPAIRS.append("%s《%s》+%s" % (CC.convert(author), rhythmic, chars))
    return text


def build_song():
    d = load("宋词_宋词三百首.json")
    full = load_full_ci()
    order, raw = [], {}
    for p in d:
        author_raw = SONG_AUTHOR_FIX.get(p["author"], p["author"])
        text = repair_ci(author_raw, p["rhythmic"], "".join(p["paragraphs"]), full)
        text = s2(text)
        for x, y in SONG_PHRASE_FIX.items():
            text = text.replace(x, y)
        author = s2(author_raw)
        rhythmic = SONG_RHYTHMIC_FIX.get((p["author"], p["rhythmic"]), p["rhythmic"])
        title = s2(rhythmic).replace("・", "·")
        cards = pair_cards([text])
        if author not in raw:
            raw[author] = []
            order.append(author)
        raw[author].append((title, cards))
    by_author = {}
    for a in order:
        titles = [t for t, _ in raw[a]]
        subs = []
        for t, cards in raw[a]:
            if titles.count(t) > 1:
                t = "%s·%s" % (t, cards[0]["f"][:6])
            subs.extend(poem_subloci(t, a, cards))
        by_author[a] = subs
    loci = author_loci(order, by_author)
    vols = balanced_pack(loci)
    entry = make_collection(
        "song300", "poetry", "宋词三百首", "300 Song Ci",
        "位置 = 词人 · 子位置 = 一首 · 卡 = 上句→下句",
        "Locus = ci poet · sub-locus = ci · card = clause → next clause",
        "chinese-poetry 宋词/宋词三百首（上彊村民编；OpenCC t2s 规范残留繁体）",
        "原文公有领域；数据集 MIT", vols)
    entry["_poems"] = len(d)
    return entry


def numbered_loci(cards, per=MAX_KIDS):
    return [group("第 %d 段" % i, c) for i, c in enumerate(chunk(cards, per), 1)]


def build_sanzijing():
    d = load("蒙学_sanzijing-traditional.json")
    cards = pair_cards([s2(x) for x in d["paragraphs"]])
    for c in cards:
        assert len(c["f"]) == 3 and len(c["b"]) == 3, c
    loci = numbered_loci(cards)
    vols = balanced_pack(loci)
    return make_collection(
        "sanzijing", "poetry", "三字经", "Three Character Classic",
        "位置 = 每 8 卡一段 · 卡 = 上句 → 下句",
        "Locus = every 8 cards · card = line → next line",
        "chinese-poetry 蒙学/三字经（传统版，OpenCC 转简体，校正 4 处源文错字）",
        "原文公有领域；数据集 MIT", vols)


def build_qianziwen():
    d = load("蒙学_qianziwen.json")
    lines = [s2(x) for x in d["paragraphs"]]
    assert len(lines) == 250 and all(len(x) == 4 for x in lines)
    cards = [card(lines[i], lines[i + 1]) for i in range(0, 250, 2)]
    loci = numbered_loci(cards)
    vols = balanced_pack(loci)
    return make_collection(
        "qianziwen", "poetry", "千字文", "Thousand Character Classic",
        "位置 = 每 8 卡一段 · 卡 = 上句 → 下句",
        "Locus = every 8 cards · card = line → next line",
        "chinese-poetry 蒙学/千字文（OpenCC 转简体，异体字规范 3 处）",
        "原文公有领域；数据集 MIT", vols)


def build_lunyu():
    d = load("论语_lunyu.json")
    chapters = {c["chapter"].replace("篇", ""): strip_punct(s2("".join(c["paragraphs"]))) for c in d}
    order = [c["chapter"].replace("篇", "") for c in d]
    loci = []
    for pian in order:
        qs = manual.LUNYU.get(pian)
        if not qs:
            continue
        cards = []
        for f, b in qs:
            joined = strip_punct(f + b)
            if joined not in chapters[pian]:
                raise ValueError("lunyu quote not found in %s: %s / %s" % (pian, f, b))
            cards.append(card(f, b))
        loci.append(group(pian, cards))
    unknown = set(manual.LUNYU) - set(order)
    assert not unknown, unknown
    return make_collection(
        "lunyu", "poetry", "论语名句", "The Analects",
        "位置 = 篇 · 卡 = 名句上半 → 下半",
        "Locus = book chapter · card = first half → second half",
        "《论语》原文；逐句与 chinese-poetry 论语/lunyu.json 核对",
        "原文公有领域；数据集 MIT", [loci])
