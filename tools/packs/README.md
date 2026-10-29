# Content packs (Memory Palace v0.3.1)

Offline content library generated into `app/src/main/assets/packs/` (index + one JSON per volume).
The existing in-app presets (唐诗名篇, 宋词名篇, 中国朝代 v0.1, 前 20 号元素, 旅行英语) stay in Kotlin
and are not generated here.

## Reproduce

```
bash tools/packs/fetch.sh    # sources -> .cache/src (incl. UNESCO, ISO 4217, BSC5, PubChem), venv -> .cache/venv
tools/packs/.cache/venv/Scripts/python.exe -I tools/packs/build.py
tools/packs/.cache/venv/Scripts/python.exe -I tools/packs/validate.py   # exit 0 = pass, writes chars.txt
```

Optional review tools (not part of the build): `check_capitals.py` (Wikidata cross-check),
`check_song.py` (宋词三百首 vs full 全宋词 diff).

Files: `common.py` (limits, packing, writer), `build_poetry.py`, `build_other.py`,
`manual.py` (hand-checked data), `build.py` (entry, writes `index.json`, prints the second-source report),
`validate.py`. v0.3.1 adds `wd.py` (Wikidata / Wikipedia access, cache, fact log), `build_geo.py`,
`build_history.py`, `build_nature.py`, `build_common.py`, `manual_v031.py` (selection lists and textbook
text only), `verify.py` (second-source checks) and `v031_checks.py` (validate.py checks for the new packs).
`.cache/` is git-ignored: `.cache/wd/` (every SPARQL / API answer), `.cache/wiki/` (Wikipedia wikitext, used
only for comparison, never copied into a pack), `.cache/facts/<id>.json` (raw value behind every number on a
card), `.cache/verify.json` and `.cache/build_notes.json` (every drop / correction with its reason).
The build only goes to the network for queries not cached yet (delete a file in `.cache/wd/` to refresh it).
All Wikimedia requests send the User-Agent
`MemoryPalaceContentBuilder/0.3.1 (https://github.com/kejing-dev/MemoryPalace; packs build script)`, wait
≥ 2 s between uncached requests and back off on 429 / 5xx.

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
- v0.3.0 used Wikidata only for the capitals check; v0.3.1 uses it as the main source, see below.


## v0.3.1: 16 new encyclopedia / general-knowledge collections (validate.py: OK)

Categories are now `poetry, language, geo, history, nature, common, numbers`; `index.json` is ordered by
that list. Re-categorised (index only, all v0.3.0 volume files byte-identical): jieqi→common,
dynasty2→history, provinces→geo, capitals→geo, uspres→history, elements→nature, solar→nature.

| id | category | cards | vols | source | license |
|---|---|---|---|---|---|
| peaks | geo | 72 | 1 | Wikidata P2044 (preferred value), P2660 ≥ 1500 m for 7000ers, P4552, P17; continents' P610 | CC0 |
| rivers | geo | 96 | 1 | Wikidata P2043, P403, P17 (rivers ≥ 1000 km with ≥ 30 sitelinks) | CC0 |
| lakes-deserts | geo | 59 | 1 | Wikidata P2046, P17 (no former lakes, reservoirs, salt flats, lake systems) | CC0 |
| whc-china | geo | 61 | 1 | Wikidata P757 / P1435 / P131 / labels; list, year, type checked against the UNESCO list XML | CC0; UNESCO facts |
| currencies | geo | 195 | 3 | Wikidata P38 + P498, kept only if listed for the country in ISO 4217 (SIX) | CC0 |
| events | history | 120 | 1 | Wikidata wars / revolutions / battles / treaties … ≤ 2000, top 120 by sitelinks, P580 / P585 | CC0 |
| paintings | history | 112 | 1 | Wikidata paintings, top 112 by sitelinks, P170 / P571 / P195 / P276 | CC0 |
| discoveries | history | 201 | 2 | Wikidata P61 / P575 (elements, planets, moons); element years must equal PubChem | CC0; PubChem public domain |
| nobel | history | 355 | 3 | Wikidata P166 + P585; laureates per year checked with api.nobelprize.org | CC0 (both) |
| constellations | nature | 88 | 1 | Wikidata (88 IAU constellations, abbreviations, Chinese star names via HR number); brightest star and V mag: Yale Bright Star Catalogue 5 | CC0; BSC5 public domain (NASA ADC) |
| animals | nature | 107 | 1 | Wikidata P171 / P105 and Chinese names; class / order / family checked with GBIF Backbone | CC0 |
| body | nature | 73 | 1 | hand-written textbook functions (no numbers) | facts |
| jieqi72 | common | 72 | 1 | 《逸周书·时训解》 (public domain), checked against zh.wikipedia 七十二候 and zh.wikisource 逸周书 卷六 | public domain |
| provinces-alias | common | 53 | 1 | 别称 hand-written, kept only if the term occurs in the province's zh.wikipedia article; area Wikidata P2046 | facts; CC0 |
| si-units | common | 73 | 1 | SI definitions (BIPM SI Brochure, 9th ed., hand); conversion factors Wikidata P2370 | facts; CC0 |
| math | common | 63 | 1 | hand-written textbook formulas | — |

New: **16 collections, 1800 cards, 24 volume files**. Totals now: 32 collections, 79 volume files,
7291 static cards, `assets/packs` = 482 KB.

### Data rules as implemented

- Every year, height, length, area, magnitude and conversion factor on a card is computed by the build from
  the cached source data and logged in `.cache/facts/<id>.json`. `validate.py` (`v031_checks.py`) re-reads
  the source cache (SPARQL JSON, BSC5 catalogue line, UNESCO XML), re-derives the shown value with the same
  rounding rule, checks the card exists in the pack, and fails if any other digit is on the back. A
  mutation test (Everest 8849 → 8850 in a copy) is caught. It also checks 12+ new collections, ≥ 1500 new
  cards, category order, elements (1–118 once each, symbols = PubChem, correct period locus) and that every
  currency code is an ISO 4217 code.
- Rounding: heights to the metre, river lengths to the km, areas to the km² (≥ 100 000 km² to the 万 km²),
  province areas to 0.1 万 km², SI factors with more than 10 digits to 6 significant digits marked `≈`.
  BCE years are shown as `前 N 年`.
- Several Wikidata values for one item: the preferred-rank value wins; otherwise all values must agree within
  1 % (rivers 3 %, lakes 5 %) or the item is dropped.
- WDQS serialises BCE dates in astronomical numbering (`-0215` = 216 BC); `wd.year_of` converts this. Found by
  the Cannae check (first showed 前 215 年).
- Chinese labels: zh-cn → zh-hans → zh-sg → zh → traditional variants, then OpenCC t2s. Items without a
  Chinese label are dropped; one Nobel laureate (Gary Ruvkun, medicine 2024) has none and is shown in English.
- Hand-written text (each collection's `source` says so), all in `manual_v031.py`: the animal selection
  (scientific names only), BODY, MATH, SI base / derived units / prefixes / the 7 defining constants (exact
  SI definitions: the only digits typed by hand), PROVINCE_ALIAS, JIEQI72; plus in `build_geo.py` 4 official
  Chinese names of heritage sites whose Wikidata item has no or a wrong Chinese label (新疆天山,
  峨眉山—乐山大佛, 梵净山, 庐山国家公园) and the provinces of 8 serial sites where Wikidata P131 is incomplete or
  wrong (e.g. 大运河 only 河南; 丝绸之路 listed 浙江 and 福建); and 3 taxon label fixes (Cyprinus carpio
  labelled 德国镜鲤 → 鲤, Psittacidae labelled 金刚鹦鹉科 → 鹦鹉科, Actinopteri labelled 辐鳍鱼总纲 → 辐鳍鱼纲).

### Verification (second source)

Full per-card report: `.cache/verify.json`; every drop with its reason: `.cache/build_notes.json`.
Items that fail the second-source check are dropped at build time, so the re-check of the final packs is 100 %.

| collection | second source | checked | result / action |
|---|---|---|---|
| peaks | en.wikipedia infobox / lead (zh.wikipedia if en differs) | 72 / 72 | 2 candidates dropped, 1 kept on zhwiki; Kibo (Africa's summit) has no enwiki page: 5895 m matches the Kilimanjaro article (manual) |
| rivers | same | 96 / 96 | 15 dropped (e.g. Nile: Wikidata 6650 km, enwiki now 7088 km and neither wiki quotes 6650 → dropped), 5 kept on zhwiki |
| lakes-deserts | same | 59 / 59 | 24 dropped (Lake Victoria 68 100 vs 59 947 km², Gobi, Arabian Desert …), 3 kept on zhwiki |
| whc-china | UNESCO World Heritage List XML | 61 / 61 | Wikidata lacked 3 years (长城, 庐山, 丽江) and had 2 wrong (九寨沟 1990 → 1992, 景迈山 2013 → 2023): UNESCO value used; type always UNESCO's |
| currencies | ISO 4217 list one (SIX, published 2026-09-17) | 195 / 195 | only codes in both kept (removes Wikidata noise: 9 currencies for Zimbabwe → ZWG, Poland PLZ → PLN, Czech "203", Dutch Caribbean currencies for the Netherlands); Palestine (no ISO entity) keeps ILS / EGP / JOD |
| events | en.wikipedia year | 120 / 120 | 1 dropped (新石器革命) |
| paintings | en.wikipedia year (ranges like 1484–1486 accepted) | 112 / 112 | 13 dropped (Rokeby Venus 1644 vs 1647–51, Young Sick Bacchus 1595 vs 1593 …); 3 duplicate titles dropped (second 《吻》, 《维纳斯的诞生》 …) |
| discoveries | PubChem YearDiscovered (elements); en.wikipedia (planets, moons); discoverer's life span | 201 / 201 | 32 elements dropped, year ≠ PubChem (boron 1892, magnesium 1755, platinum 1557 …); 14 discoverers removed who were not adult / alive in that year (Wöhler for Ti 1791 and Be 1798, Berzelius for Zr 1789 …) |
| nobel | api.nobelprize.org (laureate Wikidata ids) | 355 / 355 | identical except physics 2023 and medicine 2025, where Wikidata uses a duplicate item for one laureate (same name) |
| constellations | en.wikipedia constellation infobox "brightest star" | 88 / 88 | first run 6 mismatches → fixed: BSC lists close double components separately, so α Cru, α Cen, γ Vel, γ Vol, β Mon, α CVn are combined (< 60″, m = −2.5 log Σ 10^(−0.4 mᵢ)); ties within 0.015 mag show both stars (And, CrA, Mon) |
| animals | GBIF Backbone species/match | 117 candidates | 10 dropped where order or family differ (whales: Wikidata Artiodactyla vs GBIF Cetacea, Talpa, emu, tuna, zebrafish, clownfish …); 10 kept where only the class name differs (GBIF uses Crocodylia / Squamata / Testudines / Elasmobranchii as classes) |
| provinces-alias | zh.wikipedia province article (别称); en.wikipedia (area) | 27 别称 / 33 areas | 7 别称 not found → dropped (津沽, 申城, 三湘, 南粤, 雪域高原, 陇原, 塞上江南); 4 areas dropped (Hong Kong: Wikidata values disagree; Jiangsu, Qinghai, Ningxia: enwiki differs) |
| si-units | identities: 12 in = 1 ft, 3 ft = 1 yd, 1760 yd = 1 mi, 16 oz = 1 lb, 4840 yd² = 1 acre, ly = c · 365.25 d, pc = 648000/π au, 市尺 = 1/3 m, 市亩 = 2000/3 m² | 9 | all exact |
| jieqi72 | zh.wikipedia 七十二候 + zh.wikisource 逸周书 卷六 | 72 / 72 | 1 fix: 小暑二候 蟋蟀居宇 → 蟋蟀居壁 (逸周书); 鴽 is written in its OpenCC form 𫛪 |
| elements (v0.3.0) | PubChem periodic table | 118 / 118 | all symbols match, 1–118 once each, every element in its period locus (lanthanides / actinides separate) |
| body, math | — | — | hand-written standard textbook statements, reviewed; no numbers |

### Not done / dropped

- Chinese emperors / 年号 timeline: Wikidata's era-name and reign data is incomplete and inconsistent; not
  sourced reliably, so not built.
- Inventions incl. 四大发明: Wikidata P61 / P575 for inventions is unusable (汽车 → 南怀仁 1884, 电子计算机 →
  Babbage 1945, 黑火药 → Berthold Schwarz 900). Replaced by `discoveries` (elements, planets, moons), whose
  data can be cross-checked. 四大发明 mostly have no single inventor or year.
- Country highest points: Wikidata P610 on countries is contradictory (India: Kangchenjunga preferred, K2
  normal; Australia: Mawson Peak preferred), so only the continents' highest points are used.
- Flag colours: no reliable machine-readable source.
- Scientists' contributions: free text, no structured CC0 source; Nobel laureates instead.
- Events are "top by number of Wikipedia language editions", which is war-heavy by nature of the data;
  events after 2000 are excluded (recent / ongoing conflicts).
- New rare characters for the font subset: 䴕 (䴕形目, ext-A) and 𫛪 (田鼠化为𫛪, ext-E). New non-CJK symbols
  include superscript / subscript digits and letters (² ³ ⁻ ⁿ ₀ ₁ ₂ ₐ ₖ ₙ ˣ ᵐ ᵇ ᵏ), Greek letters (Δ Ω α–π),
  √ ∫ ≈ ≠ ≤ ≥ ⇔ ℃ ⅓ ½ ⁄ ±.
