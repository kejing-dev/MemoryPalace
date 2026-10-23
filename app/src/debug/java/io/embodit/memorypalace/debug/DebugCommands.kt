package io.embodit.memorypalace.debug

import android.content.Context
import android.os.Handler
import android.os.Looper
import io.embodit.memorypalace.data.LanguagePref
import io.embodit.memorypalace.domain.BlockReason
import io.embodit.memorypalace.domain.HeadPose
import io.embodit.memorypalace.domain.P2
import io.embodit.memorypalace.domain.PalaceTree
import io.embodit.memorypalace.domain.Presets
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Sm2
import io.embodit.memorypalace.domain.Spot
import io.embodit.memorypalace.domain.SurfaceKind
import io.embodit.memorypalace.domain.Vec3
import io.embodit.memorypalace.platform.PalaceGraph
import io.embodit.memorypalace.platform.PalaceLog
import io.embodit.memorypalace.platform.PickerActivity
import io.embodit.memorypalace.ui.palace.ConfirmKind
import io.embodit.memorypalace.ui.palace.Nudge
import io.embodit.memorypalace.ui.palace.NudgeKind
import io.embodit.memorypalace.ui.palace.PalaceEvent
import io.embodit.memorypalace.ui.palace.PalaceViewModel
import io.embodit.memorypalace.ui.palace.Phase
import io.embodit.memorypalace.ui.palace.Popup
import io.embodit.memorypalace.ui.palace.debug.Probe
import io.embodit.memorypalace.ui.palace.scene.PalaceScene
import java.io.File
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import org.json.JSONArray
import org.json.JSONObject

/**
 * Test hooks of the debug build (design `verification.debugHooks`). One log line (tag `MemoryPalace`)
 * answers every command; `state` ends with `CMD state done <id> reached=<designState>`.
 *
 * Designed states (QA screenshots)
 * - `state` s=S01..S26        jump to that state (re-pins the palace around the head where it needs loci)
 *
 * Driving
 * - `seed` [a=k]              pin every locus of the current palace with real anchors on a 2.2 m ring around
 *                             the head; with k the k-th locus (1-based) stands 0.85 m straight ahead
 * - `due` s=all|none          every card due today / tomorrow
 * - `dropAnchors` a=n         remove the anchors of the last n loci (records stay): after a restart they are lost (S22)
 * - `palace` s=<id>           switch palace (dynasties, elements20, travel-en, ...)
 * - `tap` a=n                 pinch locus n (real distance) · `cell` a=i (pinch the i-th cell) · `title`
 * - `open` a=n [s=sub-locus title]   open locus n (and a sub-locus) without walking
 * - `ui` s=<control>          press a control by name (list in AGENTS.md)
 * - `place`                   pinch on the head-ray hit, like a real pinch on the surface looked at
 * - `samples`                 write the sample import files into files/import/
 * - `hold` s=on|off           transient states (flash, nudges, import result) stay
 * - `wikiFail` s=on|off       every Wikipedia request fails (S31)
 * - `wikiFixture` s=on|off    Wikipedia answers from assets/wiki (长城, 长城镇, Great Wall of China): QA offline
 *
 * Reading back
 * - `dump` (also files/dump.json) · `pose` · `panels` · `planes` · `bounds`
 */
internal object DebugCommands {
    private const val NO_HOST = "CMD ignored: no Stage"
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, cmd: String, s: String?, a: String?) {
        val host = Probe.host
        if (host == null) {
            PalaceLog.w(NO_HOST)
            return
        }
        val vm = host.viewModel()
        val scene = host.scene()
        val nums = a?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: emptyList()
        when (cmd) {
            "state" -> state(context, vm, scene, (s ?: "S02").uppercase(Locale.US))
            "seed" -> seed(vm, scene, nums.firstOrNull()?.toInt()?.minus(1)) { vm.debugDue(true) }
            "due" -> vm.debugDue(s != "none")
            "dropAnchors" -> vm.debugDropAnchors(nums.firstOrNull()?.toInt() ?: 1)
            "palace" -> vm.debugPalace(s ?: Presets.DYNASTIES)
            "tap" -> locusId(vm, nums.firstOrNull()?.toInt() ?: 1)?.let { id -> tapLocus(vm, scene, id) }
            "cell" -> vm.state.value.open?.cells?.getOrNull((nums.firstOrNull()?.toInt() ?: 1) - 1)?.let { cellTap(vm, scene, it.nodeId) }
            "title" -> titleTap(vm, scene)
            "open" -> {
                val id = locusId(vm, nums.firstOrNull()?.toInt() ?: 1)
                val sub = s?.let { t -> id?.let { PalaceTree(vm.library.current, vm.library.nodes).children(it).firstOrNull { c -> c.title == t }?.id } }
                vm.debugOpen(id, sub)
            }
            "ui" -> ui(vm, s ?: "")
            "place" -> {
                val (spot, block) = scene.debugAim()
                vm.onEvent(PalaceEvent.PlaceTapped(spot, block))
            }
            "samples" -> PalaceLog.i("CMD samples ${samples(context)}")
            "hold" -> vm.holdTransients = s != "off"
            "wikiFail" -> PalaceGraph.wiki(context).failAll = s != "off"
            "wikiFixture" -> PalaceGraph.wiki(context).fixtures = s != "off"
            "dump" -> dump(context, vm, scene)
            "pose" -> PalaceLog.i(scene.poseLine())
            "panels" -> scene.panelLines().forEach { PalaceLog.i(it) }
            "planes" -> scene.planeLines().forEach { PalaceLog.i(it) }
            "bounds" -> bounds()
            "park" -> scene.parkPanels()
            else -> PalaceLog.w("CMD unknown '$cmd'")
        }
    }

    private fun locusId(vm: PalaceViewModel, n: Int): String? = vm.state.value.loci.getOrNull(n - 1)?.nodeId

    private fun tapLocus(vm: PalaceViewModel, scene: PalaceScene, id: String) {
        val l = vm.state.value.loci.firstOrNull { it.nodeId == id } ?: return
        val head = scene.headPose()?.position ?: return
        vm.onEvent(PalaceEvent.LocusTapped(id, io.embodit.memorypalace.domain.flatDistance(l.position, head)))
    }

    /**
     * A pinch on a cell / the title with the distance measured from the ViewModel's open locus (the
     * scene's copy of the state can lag a frame or more behind a debug jump: the emulator idles at ~1 fps).
     */
    private fun openDistance(vm: PalaceViewModel, scene: PalaceScene): Float {
        val open = vm.state.value.open ?: return Float.MAX_VALUE
        val l = vm.state.value.loci.firstOrNull { it.nodeId == open.locusId } ?: return Float.MAX_VALUE
        val head = scene.headPose()?.position ?: return Float.MAX_VALUE
        return io.embodit.memorypalace.domain.flatDistance(l.position, head)
    }

    private fun cellTap(vm: PalaceViewModel, scene: PalaceScene, id: String) = vm.onEvent(PalaceEvent.CellTapped(id, openDistance(vm, scene)))

    private fun titleTap(vm: PalaceViewModel, scene: PalaceScene) = vm.onEvent(PalaceEvent.TitleTapped(openDistance(vm, scene)))

    private fun ui(vm: PalaceViewModel, name: String) {
        val e: PalaceEvent? =
            when {
                name == "start_placing" -> PalaceEvent.StartPlacing
                name == "start_review" -> PalaceEvent.StartReview
                name == "start_recall" -> PalaceEvent.StartRecall
                name == "end" -> PalaceEvent.EndSession
                name == "undo_place" -> PalaceEvent.UndoPlace
                name == "done_placing" -> PalaceEvent.DonePlacing
                name == "up" -> PalaceEvent.UpLevel
                name == "rate_forgot" -> PalaceEvent.Rate(Sm2.FORGOT)
                name == "rate_blurry" -> PalaceEvent.Rate(Sm2.BLURRY)
                name == "rate_remembered" -> PalaceEvent.Rate(Sm2.REMEMBERED)
                name == "undo_rate" -> PalaceEvent.UndoRate
                name == "judge_right" -> PalaceEvent.Judge(true)
                name == "judge_wrong" -> PalaceEvent.Judge(false)
                name == "ok" -> PalaceEvent.SummaryOk
                name == "chip" -> PalaceEvent.ChipClicked
                name == "settings" -> PalaceEvent.SettingsClicked
                name == "close" -> PalaceEvent.PopupClosed
                name == "import" -> PalaceEvent.ImportClicked
                name == "import_back" -> PalaceEvent.ImportBack
                name.startsWith("file_") -> PalaceEvent.ImportFile(name.removePrefix("file_"))
                name.startsWith("palace_") -> PalaceEvent.PalacePicked(name.removePrefix("palace_"))
                name == "pick" -> PalaceEvent.PickFromFiles
                name == "error_ok" -> PalaceEvent.ImportErrorOk
                name == "lang_system" -> PalaceEvent.LanguagePicked(LanguagePref.SYSTEM)
                name == "lang_zh" -> PalaceEvent.LanguagePicked(LanguagePref.ZH)
                name == "lang_en" -> PalaceEvent.LanguagePicked(LanguagePref.EN)
                name == "relocate" -> PalaceEvent.Relocate
                name == "tear_down" -> PalaceEvent.TearDown
                name == "confirm_yes" -> PalaceEvent.ConfirmYes
                name == "confirm_no" -> PalaceEvent.ConfirmNo
                name == "delete_locus" -> PalaceEvent.DeleteLocus
                name == "retry_lost" -> PalaceEvent.RetryLost
                name == "replace_lost" -> PalaceEvent.ReplaceLost
                name == "enter" -> PalaceEvent.EnterAnyway
                name == "grant" -> PalaceEvent.GrantPermission
                // v0.2 content sources
                name == "new_palace" -> PalaceEvent.NewPalace
                name == "back" -> PalaceEvent.PopupBack
                name == "source_poetry" -> PalaceEvent.SourcePoetry
                name == "source_wiki" -> PalaceEvent.SourceWiki
                name == "source_file" -> PalaceEvent.SourceFile
                name.startsWith("pack_build_") -> PalaceEvent.BuildPack(name.removePrefix("pack_build_"))
                name.startsWith("wiki_search_") -> PalaceEvent.WikiSearch(name.removePrefix("wiki_search_").replace('_', ' '))
                name.startsWith("wiki_lang_") -> PalaceEvent.WikiLang(name.removePrefix("wiki_lang_"))
                name.startsWith("wiki_pick_") ->
                    vm.state.value.wiki?.hits?.getOrNull(name.removePrefix("wiki_pick_").toIntOrNull() ?: 0)?.let { PalaceEvent.WikiPick(it.title, it.description) }
                name.startsWith("wiki_toggle_") -> PalaceEvent.WikiToggle(name.removePrefix("wiki_toggle_").toIntOrNull() ?: 0)
                name == "wiki_build" -> PalaceEvent.WikiBuild
                name == "wiki_retry" -> PalaceEvent.WikiRetry
                name == "go_poetry" -> PalaceEvent.GoPoetry
                else -> null
            }
        if (e == null) PalaceLog.w("CMD ui unknown '$name'") else vm.onEvent(e)
    }

    // -------------------------------------------------------------------------------- seeding

    /**
     * Spots for [n] loci around the head: [front] (0-based) 0.85 m straight ahead, the rest on a 2.2 m
     * ring away from it (all facing the head, as if on walls); without [front] the ring goes all round.
     */
    fun spots(scene: PalaceScene, n: Int, front: Int?): List<Spot> {
        val head = scene.headPose() ?: HeadPose(Vec3(0f, 1.6f, 0f), Quat4.IDENTITY)
        val floor = scene.floor()
        val y = (head.position.y - 0.1f).coerceIn(floor + 0.95f, floor + 1.7f)
        val f = head.level()
        val left = P2(f.z, -f.x)
        fun dir(deg: Double): P2 {
            val r = Math.toRadians(deg)
            return (f * cos(r).toFloat() + left * sin(r).toFloat()).normalized()
        }
        val others = (0 until n).filter { it != front }
        val out = arrayOfNulls<Spot>(n)
        others.forEachIndexed { i, idx ->
            val deg = if (front == null) 30.0 + i * 360.0 / n else 70.0 + (if (others.size == 1) 110.0 else i * 220.0 / (others.size - 1))
            val d = dir(deg)
            val at = Vec3(head.position.x + d.x * RING_M, y, head.position.z + d.z * RING_M)
            out[idx] = Spot(at, Vec3(-d.x, 0f, -d.z), SurfaceKind.WALL)
        }
        if (front != null && front in 0 until n) out[front] = Spot(Vec3(head.position.x + f.x * FRONT_M, y, head.position.z + f.z * FRONT_M), Vec3(-f.x, 0f, -f.z), SurfaceKind.WALL)
        return out.filterNotNull()
    }

    private fun seed(vm: PalaceViewModel, scene: PalaceScene, front: Int?, count: Int? = null, then: () -> Unit) {
        val n = PalaceTree(vm.library.current, vm.library.nodes).loci.size
        val all = spots(scene, n, front)
        vm.debugSeed(if (count != null) all.take(count) else all, then)
    }

    private fun later(ms: Long, block: () -> Unit) {
        main.postDelayed(block, ms)
    }

    private fun done(vm: PalaceViewModel, id: String) {
        later(400) { PalaceLog.i("CMD state done $id reached=${vm.state.value.designState}") }
    }

    private fun state(context: Context, vm: PalaceViewModel, scene: PalaceScene, id: String) {
        vm.debugReset()
        vm.holdTransients = true
        vm.debugPalace(Presets.DYNASTIES)
        val tree = { PalaceTree(vm.library.current, vm.library.nodes) }
        when (id) {
            "S01" -> vm.debugSeed(emptyList()) {
                vm.debugPhase(Phase.SCANNING)
                done(vm, id)
            }
            "S23" -> vm.debugSeed(emptyList()) {
                vm.debugPhase(Phase.NO_PERMISSION)
                done(vm, id)
            }
            "S02" -> {
                // v0.2: a first launch opens on Tang Poems
                vm.debugPalace(Presets.TANG)
                vm.debugSeed(emptyList()) { done(vm, id) }
            }
            "S03" -> seed(vm, scene, null) {
                vm.debugDue(true)
                vm.debugPopup(Popup.LIBRARY)
                done(vm, id)
            }
            "S04", "S04B" -> {
                samples(context)
                vm.onEvent(PalaceEvent.ImportClicked)
                if (id == "S04B") vm.onEvent(PalaceEvent.ImportFile("grouped.csv"))
                done(vm, id)
            }
            "S05" -> {
                samples(context)
                vm.onEvent(PalaceEvent.ImportClicked)
                vm.onEvent(PalaceEvent.ImportFile("bad.csv"))
                done(vm, id)
            }
            "S06", "S07" -> seed(vm, scene, null, count = 1) {
                vm.onEvent(PalaceEvent.StartPlacing)
                if (id == "S07") vm.debugBlock(BlockReason.SURFACE)
                done(vm, id)
            }
            "S08" -> seed(vm, scene, null, count = 2) {
                vm.onEvent(PalaceEvent.StartPlacing)
                val n = tree().loci.size
                val third = spots(scene, n, 2)[2]
                vm.debugPlace(third)
                later(2500) { done(vm, id) }
            }
            "S09" -> seed(vm, scene, null) {
                vm.debugDue(true)
                vm.onEvent(PalaceEvent.DonePlacing)
                done(vm, id)
            }
            "S10", "S11" -> seed(vm, scene, null) {
                vm.debugDue(true)
                vm.onEvent(PalaceEvent.StartReview)
                if (id == "S11") later(600) { locusId(vm, 1)?.let { tapLocus(vm, scene, it) } }
                later(1200) { done(vm, id) }
            }
            "S12" -> {
                vm.debugImportText("单卡测试.csv", SINGLE_CSV)
                seed(vm, scene, 0) {
                    vm.debugDue(true)
                    vm.onEvent(PalaceEvent.StartReview)
                    later(1500) { done(vm, id) }
                }
            }
            "S13" -> seed(vm, scene, 1) {
                vm.debugDue(true)
                vm.onEvent(PalaceEvent.StartReview)
                later(1200) {
                    vm.debugOpen(locusId(vm, 2))
                    // the scene learns about the open locus on the next render: pinch after it
                    later(800) {
                        done(vm, id)
                    }
                }
            }
            // v0.2 acceptance: Tang Poems, walk up to 杜甫, into 《春望》, 国破山河在 -> 城春草木深
            "S14", "S15" -> {
                vm.debugPalace(Presets.TANG)
                seed(vm, scene, 1) {
                    vm.debugDue(true)
                    vm.onEvent(PalaceEvent.StartReview)
                    later(1200) {
                        vm.debugOpen(locusId(vm, 2))
                        later(800) {
                            vm.state.value.open?.cells?.firstOrNull { it.group }?.let { cellTap(vm, scene, it.nodeId) }
                            if (id == "S15") later(800) { vm.state.value.open?.cells?.firstOrNull { it.lit }?.let { cellTap(vm, scene, it.nodeId) } }
                            later(if (id == "S15") 1600 else 600) { done(vm, id) }
                        }
                    }
                }
            }
            // a card of a Wikipedia palace (built from the 长城 fixture): the CC BY-SA credit on its back
            "S15W" -> {
                vm.debugRemoveWiki()
                val wiki = PalaceGraph.wiki(context)
                wiki.fixtures = true
                wiki.failAll = false
                vm.onEvent(PalaceEvent.SourceWiki)
                vm.onEvent(PalaceEvent.WikiLang("zh"))
                vm.onEvent(PalaceEvent.WikiPick("长城", "中国古代的一系列边界防御工事"))
                later(2500) {
                    vm.onEvent(PalaceEvent.WikiBuild)
                    seed(vm, scene, 0) {
                        vm.debugDue(true)
                        vm.onEvent(PalaceEvent.StartReview)
                        later(1200) {
                            vm.debugOpen(locusId(vm, 1))
                            later(800) {
                                vm.state.value.open?.cells?.firstOrNull { it.lit }?.let { cellTap(vm, scene, it.nodeId) }
                                later(800) { done(vm, id) }
                            }
                        }
                    }
                }
            }
            "S27" -> {
                vm.debugPopup(Popup.NEW_PALACE)
                done(vm, id)
            }
            "S28", "S28B" -> {
                vm.debugPalace(Presets.TANG)
                vm.debugSeed(emptyList()) {
                    vm.onEvent(PalaceEvent.NewPalace)
                    vm.onEvent(PalaceEvent.SourcePoetry)
                    // S28B: Tang Poems' button (already in the library) leads to its home, 0 / 5 loci
                    if (id == "S28B") vm.onEvent(PalaceEvent.BuildPack(Presets.TANG))
                    done(vm, id)
                }
            }
            "S29", "S30", "S30B" -> {
                val wiki = PalaceGraph.wiki(context)
                wiki.fixtures = true
                wiki.failAll = false
                vm.onEvent(PalaceEvent.SourceWiki)
                vm.onEvent(PalaceEvent.WikiLang("zh"))
                vm.onEvent(PalaceEvent.WikiSearch("长城"))
                when (id) {
                    "S30" -> later(800) { vm.onEvent(PalaceEvent.WikiPick("长城", "中国古代的一系列边界防御工事")) }
                    "S30B" -> later(800) { vm.onEvent(PalaceEvent.WikiPick("长城镇", "")) }
                }
                later(2500) { done(vm, id) }
            }
            "S31" -> {
                val wiki = PalaceGraph.wiki(context)
                wiki.failAll = true
                vm.onEvent(PalaceEvent.SourceWiki)
                later(1500) {
                    wiki.failAll = false
                    done(vm, id)
                }
            }
            "S16" -> seed(vm, scene, null) {
                vm.debugDue(false)
                // a small round: the three cards of ② due today
                val ids = tree().cardsUnder(tree().loci[1].id).map { it.id }.toSet()
                vm.debugDueOnly(ids)
                vm.onEvent(PalaceEvent.StartReview)
                vm.debugRateAll()
                later(PalaceViewModel.SUMMARY_DELAY_MS + 800) { done(vm, id) }
            }
            "S17", "S18" -> seed(vm, scene, 1) {
                vm.debugDue(true)
                vm.onEvent(PalaceEvent.StartRecall)
                vm.debugRecall(1)
                later(1200) {
                    vm.debugOpen(locusId(vm, 2))
                    if (id == "S18") {
                        later(800) {
                            titleTap(vm, scene)
                            later(800) {
                                vm.state.value.open?.cells?.firstOrNull()?.let { cellTap(vm, scene, it.nodeId) }
                                done(vm, id)
                            }
                        }
                    } else done(vm, id)
                }
            }
            "S19" -> seed(vm, scene, 4) {
                vm.debugDue(true)
                vm.onEvent(PalaceEvent.StartRecall)
                vm.debugRecall(1)
                later(1500) {
                    if (vm.state.value.nudge == null) {
                        locusId(vm, 5)?.let { vm.onEvent(PalaceEvent.NearOther(it)) }
                    }
                    done(vm, id)
                }
            }
            "S20" -> seed(vm, scene, null) {
                vm.onEvent(PalaceEvent.StartRecall)
                vm.debugRecallResult(15, 17, listOf(3, 4), 2)
                done(vm, id)
            }
            "S21" -> seed(vm, scene, null) {
                vm.debugDue(false)
                done(vm, id)
            }
            "S22" -> seed(vm, scene, null) {
                vm.debugDue(true)
                vm.debugMarkLost(2)
                done(vm, id)
            }
            "S24" -> seed(vm, scene, null) {
                vm.debugDue(true)
                vm.debugPopup(Popup.SETTINGS)
                done(vm, id)
            }
            "S25" -> seed(vm, scene, null) {
                vm.debugDue(true)
                vm.debugConfirm(ConfirmKind.TEAR_DOWN)
                done(vm, id)
            }
            "S26" -> seed(vm, scene, 1, count = 3) {
                vm.onEvent(PalaceEvent.StartPlacing)
                vm.debugEdit(locusId(vm, 2))
                done(vm, id)
            }
            else -> PalaceLog.w("CMD state unknown $id")
        }
    }

    // -------------------------------------------------------------------------------- import samples

    private const val SINGLE_CSV = "正面,背面\n1 号,H 氢\n2 号,He 氦\n3 号,Li 锂\n4 号,Be 铍\n5 号,B 硼\n"

    private val SAMPLES =
        mapOf(
            "grouped.csv" to "分组,正面,背面\n先秦/周,前 1046 年,西周\n先秦/周,前 770 年,东周\n先秦,约前 2070 年,夏\n秦汉,前 221 年,秦\n秦汉,前 202 年,西汉\n秦汉,25 年,东汉\n隋唐,581 年,隋\n隋唐,618 年,唐\n",
            "bad.csv" to "只有一列\n第二行\n第三行\n",
            "too_deep.csv" to "分组,正面,背面\na/b/c,正面,背面\n",
            "anki_nested.txt" to "#separator:tab\n#html:true\n#deck column:3\n前 221 年\t秦 · 统一六国\t中国朝代::秦汉\n25 年\t东汉\t中国朝代::秦汉\n前 770 年\t东周\t中国朝代::先秦::周\n{{c1::氢}}是 1 号元素\t\t中国朝代::杂项\n",
            "outline.txt" to "开场\n  问好 | 大家好，我是…\n  今天讲什么 | 记忆宫殿\n主体\n  方法\n    位置 | 选熟悉的房间\n    路线 | 按顺序走\n  例子 | 中国朝代\n结尾 | 谢谢大家\n",
        )

    fun samples(context: Context): String {
        val dir = PickerActivity.importDir(context)
        for ((name, text) in SAMPLES) File(dir, name).writeText(text)
        // the GBK fallback: an Excel-style Chinese CSV
        File(dir, "gbk.csv").writeBytes("正面,背面\n苹果,apple\n香蕉,banana\n".toByteArray(charset("GBK")))
        return "${SAMPLES.size + 1} files in ${dir.absolutePath}"
    }

    // -------------------------------------------------------------------------------- read back

    private fun dump(context: Context, vm: PalaceViewModel, scene: PalaceScene) {
        val s = vm.state.value
        val head = scene.headPose()?.position
        val o =
            JSONObject()
                .put("designState", s.designState)
                .put("phase", s.phase.name)
                .put("mode", s.mode.name)
                .put("home", s.home.name)
                .put("palace", s.palaceId)
                .put("placed", s.placedCount)
                .put("dueToday", s.dueToday)
                .put("lost", s.lostLoci)
                .put("open", s.open?.let { JSONObject().put("locus", it.locusId).put("title", it.title).put("inside", it.inside).put("hidden", it.hidden).put("cells", JSONArray(it.cells.map { c -> "${c.index}:${c.text}${if (c.lit) "*" else ""}${if (c.flipped) "^" else ""}" })) })
                .put("nudge", s.nudge?.let { "${it.kind}:${it.text}" })
                .put("review", s.review?.let { "${it.done}/${it.total}" })
                .put("recall", s.recall?.let { "${it.number}/${it.lociTotal} revealed=${it.revealed}" })
                .put("today", vm.todayIndex)
                .put(
                    "loci",
                    JSONArray(
                        s.loci.map { l ->
                            JSONObject().put("n", l.number).put("id", l.nodeId).put("title", l.title).put("found", l.found).put("due", l.due).put("tone", l.tone.name)
                                .put("d", head?.let { String.format(Locale.US, "%.2f", io.embodit.memorypalace.domain.flatDistance(l.position, it)) })
                        }
                    ),
                )
        val text = o.toString()
        runCatching { File(context.filesDir, "dump.json").writeText(text) }
        PalaceLog.i("DUMP $text")
    }

    private fun bounds() {
        for (panel in listOf("pocket", "library", "settings", "title", "edit") + (0 until 8).map { "cell$it" }) {
            val size = Probe.sizeOf(panel) ?: continue
            for ((name, b) in Probe.controlsOf(panel)) PalaceLog.i(String.format(Locale.US, "BOUNDS panel=%s size=%dx%d name=%s c=(%.0f, %.0f) wh=(%.0f, %.0f)", panel, size.first, size.second, name, b.cx, b.cy, b.w, b.h))
        }
    }

    private const val RING_M = 2.2f
    private const val FRONT_M = 0.85f
}
