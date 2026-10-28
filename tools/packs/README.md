# Content packs (Memory Palace v0.3.0)

Offline content library generated into `app/src/main/assets/packs/` (index + one JSON per volume).
The existing in-app presets (唐诗名篇, 宋词名篇, 中国朝代 v0.1, 前 20 号元素, 旅行英语) stay in Kotlin
and are not generated here.

## Reproduce

```
bash tools/packs/fetch.sh                              # sources -> .cache/src, venv -> .cache/venv
tools/packs/.cache/venv/Scripts/python.exe -I tools/packs/build.py
tools/packs/.cache/venv/Scripts/python.exe -I tools/packs/validate.py   # exit 0 = pass, writes chars.txt
```

Optional review tools (not part of the build): `check_capitals.py` (Wikidata cross-check),
`check_song.py` (宋词三百首 vs full 全宋词 diff).

Files: `common.py` (limits, packing, writer), `build_poetry.py`, `build_other.py`,
`manual.py` (hand-checked data), `build.py` (entry, writes `index.json`), `validate.py`.
`.cache/` is git-ignored.

## Result (validate.py: OK)

| id | category | cards | volumes | source | license |
|---|---|---|---|---|---|
| tang300 | poetry | 1588 | 15 | chinese-poetry `蒙学/tangshisanbaishou.json` (蘅塘退士 320 首), OpenCC t2s | text public domain; dataset MIT |
| song300 | poetry | 1969 | 19 | chinese-poetry `宋词/宋词三百首.json` (280 首), repaired from `宋词/ci.song.*.json` | public domain; MIT |
| sanzijing | poetry | 190 | 2 | chinese-poetry `蒙学/sanzijing-traditional.json`, t2s | public domain; MIT |
| qianziwen | poetry | 125 | 2 | chinese-poetry `蒙学/qianziwen.json`, t2s | public domain; MIT |
| lunyu | poetry | 72 | 1 | hand-picked; every quote asserted verbatim against `论语/lunyu.json` at build time | public domain; MIT |
| words1000 | language | 1000 | 9 | ECDICT `ecdict.csv`, column `frq` (COCA rank) | MIT |
| jieqi | histgeo | 24 | 1 | hand-written (GB/T 33661-2017 names, common Gregorian dates "前后") | facts |
| dynasty2 | histgeo | 49 | 1 | hand-written, textbook dates (夏商周断代工程 for 夏/商/西周) | facts |
| provinces | histgeo | 34 | 1 | hand-written (v0.2.2 draft wording + 港澳台) | facts |
| capitals | histgeo | 195 | 3 | hand-written MFA-style names, cross-checked with Wikidata (CC0) | facts / CC0 |
| uspres | histgeo | 47 | 1 | hand-written | facts |
| elements | science | 118 | 1 | hand-written; 104–118 per 2017 全国科学技术名词审定委员会 | facts |
| solar | science | 30 | 1 | hand-written from NASA public facts, rounded | facts |
| pi | numbers | 50 | 1 | computed (Machin formula, integer arithmetic) and asserted equal to mpmath | — |
| cards52 / digits100 | numbers | 52 / 25 | 0 | `generated: true`, index entry only | — |

Totals: 58 volume files, 5491 cards (static), `assets/packs` = 337 KB.
`chars.txt` = 3599 distinct CJK characters. It includes ext-A/B/E/F and late-URO code points the font
subsetter must keep (otherwise tofu): 㧑䝙䯄鿏鿔鿫鿬鿭𡒄𣨼𦈌𪭢𫐆𫓧𫓩𫛸𫟷𫟼𬘘𬨎𬬭𬬻𬭊𬭛𬭳𬭶
(elements 104–118, plus OpenCC-standard simplified forms such as 𪭢 摐, 𡒄 壈, 𬘘 紞, 𬨎 輶, 𫛸 鶗).

## Structure decisions

- Volume ids `<collection>-<n>`; single-volume collections still use `-1`. Volume names `· 卷一` / `· Vol. 1`
  (capitals add the region: 亚洲·大洋洲 / 欧洲 / 非洲·美洲).
- Packing never splits a locus; tang/song use a DP split that keeps volume card counts close to the mean.
- Poems: locus = poet (`李白 1`, `李白 2`… when a poet has >8 sub-loci); sub-locus = `《题》` with `"a"`;
  a poem longer than 8 cards is split into `《琵琶行》一/二/…` parts (needed for the ≤8 children rule).
- Couplet pairing: clauses split on `，：` inside sentences ended by `。？！；`. A 1-clause sentence is merged
  into the next one; a sentence with an odd clause count ends in a 3-clause card whose back joins two clauses
  with a space (e.g. `将进酒`: 君不见 → 黄河之水天上来 奔流到海不复回). Punctuation removed; `、` dropped.
- Song titles: the dataset has only 词牌 (no 题). When a poet has the same 词牌 twice, the title gets the
  first 6 chars of the first line, e.g. `《高阳台·修竹凝妆》`.
- Spec estimated ~1100 tang / ~1200 song cards; the real counts are higher because long 古诗/慢词 are kept
  complete (长恨歌, 琵琶行, 莺啼序 …). Nothing was truncated.

## Source corrections (all in code, see build_poetry.py)

- Tang (蒙学 file, diffed against `全唐诗/唐诗三百首.json`): 《赠卫八处士》 was truncated after 4 of 12
  couplets → full text taken from the 全唐诗 file; 《长信怨》 entry contained all five 长信秋词 → only 其三
  (奉帚平明…) kept; title typo 同从弟**销**南斋… → 同从弟南斋玩月忆山阴崔少府. Inline variant notes
  `(一作：…)`, including ones broken across lines, are stripped. The 蒙学 file uses the popular readings
  (床前明月光, 黄鹤楼 昔人已乘黄鹤去 …), which were kept over 全唐诗 variants.
- Song: the 三百首 file silently dropped every rare character (`紞如三鼓` → `如三鼓`, `鹈鴂` → `鹈`,
  author 赵令畤 → 赵令, 张鎡 → 张, 词牌 大酺 → 大). `repair_ci` aligns each ci with the same author's text in
  the full 全宋词 files and re-inserts only the missing characters (61 insertions in ~55 ci). Plus 3 typos
  present in both files: 把洒黄昏 → 把酒黄昏 (李清照), 青明时节 → 清明时节 (辛弃疾), and 如今有谁摘 →
  如今有谁堪摘 (通行本; the full file's 忺 was not used). Readings such as 沈沈/萧瑟 stay as in the source.
- 三字经: 茍 → 苟 (×2), 祥训诂 → 详训诂, 三易祥 → 三易详, 周撤东 → 周辙东, 能赋碁 → 能赋棋.
- 千字文: variant normalisation 崐 → 昆, 臯 → 皋, 竝 → 并 (贞絜, 壹体, 寔 kept as in source).
- After OpenCC t2s every poetry string is re-converted by validate.py and must be unchanged.

## Hand-data notes

- words1000: first 1000 ranks with `frq > 0`, lowercase a–z only (plus `I`); skipped `n't American Mr North
  TV PM OK Mrs` (proper nouns/abbreviations; lowest kept rank is 1113 because ECDICT has gaps). Back = first
  ECDICT line's senses, POS prefix and `[计]`-style tags removed, ≤20 chars. ECDICT orders senses by POS
  alphabetically, so the literal first sense is often a rare noun (give → 弹性, still → 蒸馏室,
  southern → 男风). 296 words use a hand-written dominant everyday gloss instead (`manual.WORD_GLOSS`).
  QA comparing against ECDICT will see these differ by design.
- capitals: `check_capitals.py` → 170 exact matches with Wikidata zh-cn labels; the other 25 are naming
  style (中华人民共和国 vs 中国, 华盛顿哥伦比亚特区 vs 华盛顿, MFA 摩加迪沙/莫罗尼/贝尔莫潘/布里奇敦 vs
  Wikidata 摩加迪休/莫洛尼/贝尔墨邦/布里奇顿) and were reviewed by hand. One real change found: Equatorial
  Guinea's capital moved to Ciudad de la Paz on 2026-01-03 (Wikidata) → `拉巴斯城（和平城）· 2026 年迁自马拉博`.
  Multi-capital / disputed cases are spelled out on the card (玻利维亚, 南非, 斯里兰卡, 科特迪瓦, 贝宁, 布隆迪,
  埃斯瓦蒂尼, 瑙鲁, 以色列 "地位有争议", 巴勒斯坦).
- elements: IUPAC English spellings (Aluminium, Caesium, Sulfur). Period 6/7 loci hold 55–56+72–86 and
  87–88+104–118; 镧系/锕系 are their own loci.
- jieqi dates are the usual recent-year dates and vary by ±1 day ("前后").
- uspres: Cleveland is 22nd and 24th, Trump 45th and 47th (`2025–`). Locus titles are year spans.

## Not done / caveats

- Song: only the 280 ci present in the dataset (not the full 300 of every edition); no 词题.
- 题/字 variants in the 蒙学 唐诗 and 宋词 files beyond the corrections above were not hand-verified line
  by line; the cross-diffs (`.cache/tang_diff.txt`, `.cache/song_diff.txt`) list every remaining
  difference between the two chinese-poetry sources for anyone doing QA.
- Wikidata was used only for the capitals check (the first two SPARQL attempts timed out / were rate-limited;
  the retrying script succeeded).
