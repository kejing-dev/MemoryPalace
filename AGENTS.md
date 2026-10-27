# Memory Palace (记忆宫殿)

MR memory-palace app for PICO OS 6. Kotlin, PICO Spatial SDK 6.1.9 (template 6.1), one Mixed Stage (Full
Space only), package `io.embodit.memorypalace`. Loci are pinned to real walls / tables along a route; each
locus opens in place into a grid of cards and sub-loci when you walk within 1 m; review with SM-2, or walk the
route in order and recall.

v0.2 (2026-10-22, device feedback "the content is not enough"): content sources. Offline poetry packs (Tang Poems,
the default first palace, and Song Ci; public-domain originals, palace = volume, locus = poet, sub-locus = poem,
card = couplet) and a palace built from a Wikipedia article (outside mainland China; one locus per section,
fill-in cards, CC BY-SA 4.0 credit on every card's back). The only network use; built palaces stay offline.

v0.2.2 (2026-10-23, device feedback "use spatial anchors, no plane detection"): plane
tracking is no longer started. A locus is a free spatial anchor 1.2 m along the pinch direction (the gaze, or
the ray through the far backstop shell), its height kept 0.8 m below to 0.3 m above the eyes; the plaque stands
upright facing back to where it was placed from (`PlaceRules.free` / `checkFree`: gap 0.8 m, span 5 m, room
limit). S01 now only lasts until the anchors are loaded. Anchor `UPDATED` events move their locus
(`PalaceEvent.AnchorMoved`). Content is unchanged from v0.2.1: more content (user images / 3D models / typed
text, a bigger default library) is the v0.3 design; a content draft lies in
`../spatial-app-factory/apps/memory-palace/build/v0.2.2/content-draft/`.

Design package (binding): `../spatial-app-factory/apps/memory-palace/design-package.v0.2.json` (design-package.json is the v0.3 draft since 2026-10-23), **v0.2** (states
S01 - S31, `nesting`, `contentSources`, `spatialLayout`, `panels`, `entities3d`, `palette`). Approved sample:
`../spatial-app-factory/apps/memory-palace/sample.html`. Skeleton D, built from Furniture Fit (`../furniture-fit`),
itself ported from Spatial Ruler (`../spatial-ruler`): panel pool, Stage root, plane colliders, world anchors,
head tracker, theme, fonts.

## Structure

```
app/src/main/java/io/embodit/memorypalace/
  Main.kt                 DefaultStage { MemoryPalaceTheme { PalaceScreen() } }
  domain/                 pure Kotlin, JVM-tested
    Vec3.kt Geometry.kt   vectors, HeadPose, PanelFacing, PlaneSurface (+ two-sided raycast), flatDistance
    Palace.kt             Palace, Node (GROUP / CARD), Srs, Locus, PalaceTree, Limits, Days (4 a.m.), Sm2
    Presets.kt            bundled palaces + poetry packs (generated word for word from design `contentSources.poetryPack`)
    WikiBuilder.kt        Parsoid HTML -> draft palace (lead + sections -> loci, cloze cards, limits), search JSON, palace
    Importer.kt           CSV / Anki plain text (:: decks, cloze) / indented TXT, UTF-8 -> GBK, limits, splitting
    Rules.kt              PlaceRules (v0.2.2 free spots: 1.2 m ahead, gap 0.8 m, span 5 m; old surface rules kept, unused), Wake (1.0 / 1.2 m, facing, gaze)
    Sessions.kt           Review (forgot comes back once), RecallSteps + Recall (route order, grid order, sub-loci)
  data/Stores.kt          AnchorGateway, Library, FilePalaceStore (palaces.json, nodes.json, loci.json), LibraryCodec
  platform/               PalaceGraph, PalaceLog, HeadTracker, PlaneTracker, WorldAnchorGateway, PickerActivity,
                          WikiClient (REST search / page HTML, 6 s timeout, zh-cn, debug fixtures and wikiFail)
  ui/palace/              PalaceUiState (+ designState S01..S26), PalaceEvent, PalaceViewModel (all behaviour), PalaceScreen
  ui/palace/scene/        PalaceScene (ECS: one registered System; wake rule, reticle, panel placement, taps),
                          PanelRig (pool), PlaneColliders, SceneKit (materials, sphere / ring / cube meshes), LocusView, Reticle
  ui/palace/components/   SpatialUI panels: PocketPanel, LibraryPanel (+ import, settings), SourcePanels (S27 - S31:
                          new palace, poetry packs, Wikipedia search / preview / unreachable), CardPanels (title, cells,
                          labels, nudge, edit bar), PalaceStage (SpatialView + attachments), Strings
  ui/theme/               MemoryPalaceTheme (PicoTheme + 4 semantic roles + Noto Sans / Noto Sans SC), PalaceColors, PalaceType
app/src/debug/            DebugCommandReceiver + DebugCommands (QA hooks)
app/src/debug/assets/wiki Wikipedia fixtures (长城 zh / en, 长城镇 = too short): CC BY-SA 4.0, fetched 2026-10-22
app/src/test/             28 JVM tests (PalaceRulesTest, PoetryPackTest, WikiBuilderTest)
tools/                    dev.sh, cap.sh, qa_shot.sh, qa_tour.sh, contact.py, make_icon.py, subset_cjk_font.py,
                          harness/ (emulator input: camera turn, pinch; from Spatial Ruler)
```

One direction of data: panel controls and scene pinches -> `PalaceViewModel.onEvent` -> `PalaceUiState` ->
`PalaceScene.render`. Per-frame work (wake distance, recall "near another locus", reticle, panel placement,
lazy follow, slide / flip / breathing / shake) is in `PalaceScene.onFrame` only; it reports changes as events
(`LocusWoke`, `NearOther`, `AimChanged`) and writes Compose state only on change (`panelShown`, six distance
labels, the decimetres to the next locus). Head pose never goes through Compose.

## UI rules

- All 2D UI is SpatialUI inside `PicoTheme`. No Material / Material3 (gate: `../spatial-app-factory/bin/material-grep.txt`).
- Every panel is a pre-built Stage attachment panel (AlertDialog / SnackbarHost do not work in a Stage): the
  confirm strip lives inside the pocket panel.
- Cards are porcelain faces (`palette.plaque`), panels are glass. Wall loci face out of the wall
  (`PanelRig.Slot.orient`), table loci lean back 30° and turn to the viewer.
- Copy in `res/values` / `res/values-zh`; read through `rs()` (`LocalPalaceResources`) so the language switches live.
  After changing zh copy or the presets run `python tools/subset_cjk_font.py`.

## Build, run (Git Bash; device commands are pinned to emulator-5566 / AVD PICO_6_1_Factory4)

```bash
tools/dev.sh build && tools/dev.sh install && tools/dev.sh launch
tools/dev.sh log 'STATE|WAKE|RATE|RECALL|LOCUS' 40
tools/dev.sh crash
./gradlew.bat testDebugUnitTest      # with JAVA_HOME = the primer JDK 21 (see tools/dev.sh)
tools/cap.sh NAME                     # console screenshot -> .scratch/shots/NAME.png
tools/qa_shot.sh S13                  # one design state -> .scratch/shots/S13.png
tools/qa_tour.sh                      # all states + .scratch/shots/tour.png + tour.txt (reached state per shot)
```

QA tips (emulator, 2026-10-22):
- `state <id>` re-pins the current palace around the head with real anchors (a ring of 2.2 m; the locus a
  state needs stands 0.85 m straight ahead, facing you). It takes 2 - 4 s; `qa_shot.sh` waits for
  `CMD state done <id>`. The wake rule then opens the locus by itself (`WAKE open`), exactly as walking up would.
- Panels lag 3 - 10 s behind a change on the emulator: `qa_shot.sh` waits 10 s.
- The emulator room: head at y = 1.65. v0.2.2: `place` pins 1.2 m along the head ray wherever it looks; a second
  locus needs ≥ 0.8 m, so pinch well to the side (or turn the camera with `tools/harness/emu.sh drag ... -Button right`).
- The emulator locale is en-US: copy is English unless `ui lang_zh`.
- Debug pinches (`cell`, `title`) measure the distance from the ViewModel's state: the emulator idles at ~1 fps
  and the scene's copy of the state can lag a debug jump by a second.
- Real input works (v0.2.2): `tools/harness/emu.sh click 1080 1050` while placing pins a locus 1.2 m ahead
  (`TAP room target=backstop:nz`, `LOCUS placed`); the same pinch again is `block=TOO_CLOSE`; `click 2130 1150`
  is far enough to the right for the next one. force-stop + launch: both come back (`ANCHOR load missing=0`).
  X Y are console-screenshot pixels (2160 wide on this AVD).
- Launcher check: HOME key twice (the first opens "Back to Home", confirm it; the second shows the app row).
- No emulator walking: wake / sleep is exercised by re-pinning loci at a distance (`seed a=k`) or turning and
  moving the emulator camera (harness).

## Debug commands (debug build only)

`adb -s emulator-5566 shell "am broadcast -a io.embodit.memorypalace.DEBUG -p io.embodit.memorypalace --es cmd NAME [--es s TEXT] [--es a NUMBERS]"`
or `tools/dev.sh cmd NAME [TEXT] [NUMBERS]`.

| cmd | args | effect |
|---|---|---|
| `state` | s=S01..S31, S04B, S15W, S28B, S30B | jump to that design state (S04B import success · S15W a Wikipedia card's back · S28B Tang Poems' button -> home · S30B a too-short article) |
| `seed` | [a=k] | pin every locus of the current palace with real anchors around the head (k-th ahead at 0.85 m), all cards due |
| `due` | s=all / none | every card due today / tomorrow |
| `dropAnchors` | a=n | remove the anchors of the last n loci, keep their records: relaunch -> S22 |
| `palace` | s=id | switch palace (pack-tang, pack-song, dynasties, elements20, travel-en, imported / wiki ids) |
| `tap` / `cell` / `title` | a=n / a=i / - | pinch locus n (real distance) / the i-th cell / the title |
| `open` | a=n [s=sub-locus title] | open a locus (and a sub-locus) without walking |
| `ui` | s=control | `start_placing start_review start_recall end undo_place done_placing up rate_forgot rate_blurry rate_remembered undo_rate judge_right judge_wrong ok chip settings close import import_back file_<name> palace_<id> pick error_ok lang_system lang_zh lang_en relocate tear_down confirm_yes confirm_no delete_locus retry_lost replace_lost enter grant`; v0.2: `new_palace back source_poetry source_wiki source_file pack_build_<id> wiki_lang_zh wiki_lang_en wiki_search_<words> wiki_pick_<n> wiki_toggle_<n> wiki_build wiki_retry go_poetry` |
| `place` | | pinch where the head ray hits (placing) |
| `samples` | | write grouped.csv, bad.csv, too_deep.csv, anki_nested.txt, outline.txt, gbk.csv into files/import/ |
| `hold` | s=on/off | transient states (flash, nudges, import result) stay |
| `wikiFixture` | s=on/off | Wikipedia answers from `assets/wiki` (search 长城, articles 长城 / 长城镇 / Great Wall of China): QA without network. The `state` jumps S15W / S29 / S30 / S30B turn it on |
| `wikiFail` | s=on/off | every Wikipedia request fails: S31 |
| `dump` | | `DUMP {json}` (designState, open locus and cells, nudge, loci with distances); also files/dump.json |
| `pose` `panels` `planes` `bounds` `park` | | read back head pose, panel slots, planes, control bounds; re-park the pocket panel |

Import files can also be pushed: `adb -s emulator-5566 push x.csv /sdcard/Android/data/io.embodit.memorypalace/files/import/`.

## QA screenshot list (design-package `states[]`)

S01 scanning · S02 home empty (中国朝代 0/5) · S03 library · S04 import list (S04B: success line) · S05 import
error (bad.csv) · S06 placing (1 pinned, preview of ②) · S07 cannot pin (reason line; with the camera on the
floor the reticle is coral ✕) · S08 pinned ③ (flash) · S09 home due + walk hint · S10 review, nothing near (line to
the next) · S11 too far ("0.x m closer", the dot shakes) · S12 single-card locus awake · S13 ② 秦汉 open (title +
3 cards + thread) · S14 inside ① › 周 · S15 card turned over (3 rating keys) · S16 round done · S17 recall at ②
("Where is this?") · S18 recall grid, first card open with ✓ / ✕ · S19 recall wrong order ("Go to ② first") ·
S20 recall result · S21 home done · S22 loci lost · S23 no spatial access · S24 settings · S25 confirm take-down ·
S26 placing, ② picked (delete bar).
v0.2: S02 is Tang Poems 0 / 5 · S14 / S15 Tang Poems › 杜甫 › 《春望》, 国破山河在 -> 城春草木深 with 《春望》· 杜甫 on the back ·
S15W a Wikipedia card's back with the CC BY-SA credit · S27 new palace (poetry / Wikipedia / file) · S28 poetry packs
(S28B: Tang Poems -> its home) · S29 search 长城 (fixture) · S30 preview of 长城 (7 loci · 40 cards) · S30B 长城镇,
too short, button disabled · S31 this network can't reach Wikipedia.
Live Wikipedia (no fixture): `cmd wikiFixture off`, `ui source_wiki`, `ui wiki_lang_zh`, `ui wiki_search_长城`,
`ui wiki_pick_0`, `ui wiki_build` (verified 2026-10-22 from emulator-5566: each request < 1 s).

## Log (tag `MemoryPalace`)

`STATE Sxx ...` · `PLANES add` · `SCENE` · `ANCHOR load|create|removed` · `LOCUS placed` · `WAKE open|close` ·
`TAP locus|surface|other` · `UI <event>` · `LEVEL enter|up` · `RATE` · `REVIEW start|done` · `RECALL start|reveal|pinch|judge|done` ·
`NUDGE` · `IMPORT` · `PACK built` · `WIKI GET|search|draft|built|unreachable|fixture` · `FOLLOW` · `RIG slot` · `PTR panel=` (debug) · `CMD` · `DUMP`.

## Runtime facts (emulator, SDK 6.1.9, 2026-10-22)

- (before v0.2.2) 57 planes arrived within a second on a warm emulator; planes are not tracked any more.
- `WorldTrackingManager.loadAnchor` and `createAnchor` both answer -6 "provider is not ready" until the app
  has subscribed to anchor updates and for a moment after the Stage opens: `WorldAnchorGateway` retries -6.
- Wikipedia (v0.2): `Accept-Language: zh-cn` on the REST HTML gives `content-language: zh-Hans-CN` (Simplified);
  the revision id is in the root `about` attribute. Parsoid puts JSON with ">" inside `data-mw` attributes:
  tags are matched quote-aware (`WikiBuilder.ATTRS`). The User-Agent has no contact yet (Wikimedia asks for one;
  to be filled in before release).
- No runtime permission exists for planes / anchors on SDK 6.1 (docs list none): S23 shows only when anchor
  loading keeps answering -5 after the retries.
- Spatial Ruler's panel-pool rules apply (see `ui/palace/scene/PanelRig.kt`).
