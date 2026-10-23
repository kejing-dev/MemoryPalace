# Memory Palace (记忆宫殿)

MR memory-palace app for PICO OS 6. Kotlin, PICO Spatial SDK 6.1.9 (template 6.1), one Mixed Stage (Full
Space only), package `io.embodit.memorypalace`. Loci are pinned to real walls / tables along a route; each
locus opens in place into a grid of cards and sub-loci when you walk within 1 m; review with SM-2, or walk the
route in order and recall.

Design package (binding): `../spatial-app-factory/apps/memory-palace/design-package.json`, the **v2 nested**
version (states S01 - S26, `nesting`, `spatialLayout`, `panels`, `entities3d`, `palette`). Approved sample:
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
    Presets.kt            the three bundled palaces (stable ids <palace>/<path>)
    Importer.kt           CSV / Anki plain text (:: decks, cloze) / indented TXT, UTF-8 -> GBK, limits, splitting
    Rules.kt              PlaceRules (surfaces, 0.8 - 1.8 m, gap 0.8 m, span 5 m, table edge), Wake (1.0 / 1.2 m, facing, gaze)
    Sessions.kt           Review (forgot comes back once), RecallSteps + Recall (route order, grid order, sub-loci)
  data/Stores.kt          AnchorGateway, Library, FilePalaceStore (palaces.json, nodes.json, loci.json), LibraryCodec
  platform/               PalaceGraph, PalaceLog, HeadTracker, PlaneTracker, WorldAnchorGateway, PickerActivity
  ui/palace/              PalaceUiState (+ designState S01..S26), PalaceEvent, PalaceViewModel (all behaviour), PalaceScreen
  ui/palace/scene/        PalaceScene (ECS: one registered System; wake rule, reticle, panel placement, taps),
                          PanelRig (pool), PlaneColliders, SceneKit (materials, sphere / ring / cube meshes), LocusView, Reticle
  ui/palace/components/   SpatialUI panels: PocketPanel, LibraryPanel (+ import, settings), CardPanels (title, cells,
                          labels, nudge, edit bar), PalaceStage (SpatialView + attachments), Strings
  ui/theme/               MemoryPalaceTheme (PicoTheme + 4 semantic roles + Noto Sans / Noto Sans SC), PalaceColors, PalaceType
app/src/debug/            DebugCommandReceiver + DebugCommands (QA hooks)
app/src/test/             19 JVM tests (PalaceRulesTest)
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
- The emulator room: floor plane at y = 0.40, head at y = 1.65, a wall 2 m ahead (WALL plane z ≈ -4.3 further
  back, the bed in between). `place` pinches on what the head ray hits (looking straight ahead hits the bed:
  "can't pin here"); turn the camera to a wall with `tools/harness/emu.sh drag ... -Button right`.
- The emulator locale is en-US: copy is English unless `ui lang_zh`.
- Debug pinches (`cell`, `title`) measure the distance from the ViewModel's state: the emulator idles at ~1 fps
  and the scene's copy of the state can lag a debug jump by a second.
- Real input works: `tools/harness/emu.sh click 1080 1050` while placing pins a locus on the wall above the bed
  (`TAP surface target=plane:WALL`, `LOCUS placed`); `click 1500 2000` hits the floor (`block=SURFACE`).
  X Y are console-screenshot pixels (2160 wide on this AVD).
- Launcher check: HOME key twice (the first opens "Back to Home", confirm it; the second shows the app row).
- No emulator walking: wake / sleep is exercised by re-pinning loci at a distance (`seed a=k`) or turning and
  moving the emulator camera (harness).

## Debug commands (debug build only)

`adb -s emulator-5566 shell "am broadcast -a io.embodit.memorypalace.DEBUG -p io.embodit.memorypalace --es cmd NAME [--es s TEXT] [--es a NUMBERS]"`
or `tools/dev.sh cmd NAME [TEXT] [NUMBERS]`.

| cmd | args | effect |
|---|---|---|
| `state` | s=S01..S26, S04B | jump to that design state (S04B = import success line) |
| `seed` | [a=k] | pin every locus of the current palace with real anchors around the head (k-th ahead at 0.85 m), all cards due |
| `due` | s=all / none | every card due today / tomorrow |
| `dropAnchors` | a=n | remove the anchors of the last n loci, keep their records: relaunch -> S22 |
| `palace` | s=id | switch palace (dynasties, elements20, travel-en, imported ids) |
| `tap` / `cell` / `title` | a=n / a=i / - | pinch locus n (real distance) / the i-th cell / the title |
| `open` | a=n [s=sub-locus title] | open a locus (and a sub-locus) without walking |
| `ui` | s=control | `start_placing start_review start_recall end undo_place done_placing up rate_forgot rate_blurry rate_remembered undo_rate judge_right judge_wrong ok chip settings close import import_back file_<name> palace_<id> pick error_ok lang_system lang_zh lang_en relocate tear_down confirm_yes confirm_no delete_locus retry_lost replace_lost enter grant` |
| `place` | | pinch where the head ray hits (placing) |
| `samples` | | write grouped.csv, bad.csv, too_deep.csv, anki_nested.txt, outline.txt, gbk.csv into files/import/ |
| `hold` | s=on/off | transient states (flash, nudges, import result) stay |
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

## Log (tag `MemoryPalace`)

`STATE Sxx ...` · `PLANES add` · `SCENE` · `ANCHOR load|create|removed` · `LOCUS placed` · `WAKE open|close` ·
`TAP locus|surface|other` · `UI <event>` · `LEVEL enter|up` · `RATE` · `REVIEW start|done` · `RECALL start|reveal|pinch|judge|done` ·
`NUDGE` · `IMPORT` · `FOLLOW` · `RIG slot` · `PTR panel=` (debug) · `CMD` · `DUMP`.

## Runtime facts (emulator, SDK 6.1.9, 2026-10-22)

- 57 planes arrive within a second on a warm emulator; 29 of them can take a locus (WALL / vertical UNKNOWN /
  TABLE). Floor plane y = 0.40, head y = 1.65.
- `WorldTrackingManager.loadAnchor` and `createAnchor` both answer -6 "provider is not ready" until the app
  has subscribed to anchor updates and for a moment after the Stage opens: `WorldAnchorGateway` retries -6.
- No runtime permission exists for planes / anchors on SDK 6.1 (docs list none): S23 shows only when plane
  tracking fails to start.
- Spatial Ruler's panel-pool rules apply (see `ui/palace/scene/PanelRig.kt`).
