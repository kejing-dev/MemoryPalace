package io.embodit.memorypalace.domain

import io.embodit.memorypalace.data.Library
import io.embodit.memorypalace.data.LibraryCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalaceRulesTest {

    // ---------------------------------------------------------------- presets / tree

    @Test
    fun dynastiesPresetIsFiveLociSeventeenCardsThreeLevels() {
        val t = PalaceTree(Presets.DYNASTIES, Presets.nodes())
        assertEquals(5, t.loci.size)
        assertEquals(17, t.cards.size)
        assertEquals(3, t.depth)
        val zhou = t.children(t.loci[0].id).first { !it.isCard }
        assertEquals("周", zhou.title)
        assertEquals(listOf("先秦", "周", "前 770 年"), t.pathTo(t.children(zhou.id)[1].id).map { it.label })
    }

    @Test
    fun otherPresetsMatchTheDesignCounts() {
        val e = PalaceTree(Presets.ELEMENTS, Presets.nodes())
        assertEquals(4, e.loci.size)
        assertEquals(20, e.cards.size)
        val tr = PalaceTree(Presets.TRAVEL, Presets.nodes())
        assertEquals(3, tr.loci.size)
        assertEquals(15, tr.cards.size)
        for (t in listOf(e, tr)) for (n in t.nodes) assertTrue(t.children(n.id).size <= Limits.CHILDREN_PER_NODE)
    }

    // ---------------------------------------------------------------- SM-2

    @Test
    fun sm2IntervalsAndLapses() {
        var s = Srs()
        s = Sm2.rate(s, Sm2.REMEMBERED, 100)
        assertEquals(101, s.dueDay)
        s = Sm2.rate(s, Sm2.REMEMBERED, 101)
        assertEquals(107, s.dueDay)
        s = Sm2.rate(s, Sm2.BLURRY, 107)
        assertTrue(s.interval >= 6)
        val lapse = Sm2.rate(s, Sm2.FORGOT, 120)
        assertEquals(0, lapse.reps)
        assertEquals(121, lapse.dueDay)
        assertTrue(lapse.ef >= 1.3f)
        assertTrue(Srs().isDue(0))
    }

    @Test
    fun dayStartsAtFourInTheMorning() {
        val day = 86_400_000L
        val base = 20_000L * day
        assertEquals(Days.of(base + 4 * 3_600_000L, 0), Days.of(base + 23 * 3_600_000L, 0))
        assertEquals(Days.of(base + 3 * 3_600_000L, 0) + 1, Days.of(base + 5 * 3_600_000L, 0))
    }

    // ---------------------------------------------------------------- import

    private fun ok(name: String, text: String): ImportOutcome.Ok = Importer.parse(name, text.toByteArray(), "t", 0) as ImportOutcome.Ok

    private fun fail(name: String, text: String): ImportError = (Importer.parse(name, text.toByteArray(), "t", 0) as ImportOutcome.Failed).error

    @Test
    fun groupedCsvBuildsNestedLoci() {
        val r = ok("dyn.csv", "分组,正面,背面\n先秦/周,前 1046 年,西周\n先秦,约前 2070 年,夏\n秦汉,前 221 年,秦\n")
        assertEquals(2, r.loci)
        assertEquals(3, r.cards)
        assertEquals(3, r.depth)
        val t = PalaceTree("t", r.nodes)
        assertEquals(listOf("先秦", "秦汉"), t.loci.map { it.title })
    }

    @Test
    fun flatCsvIsOneLocusPerCardUpToTwenty() {
        val r = ok("flat.csv", (1..5).joinToString("\n") { "q$it,a$it" })
        assertEquals(5, r.loci)
        assertTrue(PalaceTree("t", r.nodes).loci.all { it.isCard })
        val big = ok("big.csv", (1..30).joinToString("\n") { "q$it,a$it" })
        assertEquals(5, big.loci) // every 6 cards into one locus
        assertEquals("q1", PalaceTree("t", big.nodes).loci[0].title)
    }

    @Test
    fun importErrors() {
        assertEquals(ImportError.NO_TWO_COLUMNS, fail("bad.csv", "只有一列\n第二行\n"))
        assertEquals(ImportError.TOO_DEEP, fail("deep.csv", "g,f,b\na/b/c,x,y\n"))
        assertEquals(ImportError.EMPTY, fail("empty.csv", "\n\n"))
    }

    @Test
    fun quotedCsvCells() {
        val r = ok("q.csv", "\"a, b\",\"say \"\"hi\"\"\"\n")
        val c = PalaceTree("t", r.nodes).cards.single()
        assertEquals("a, b", c.front)
        assertEquals("say \"hi\"", c.back)
    }

    @Test
    fun gbkFallback() {
        val bytes = "正面,背面\n苹果,apple\n香蕉,banana\n".toByteArray(charset("GBK"))
        val r = Importer.parse("gbk.csv", bytes, "t", 0) as ImportOutcome.Ok
        assertEquals("苹果", PalaceTree("t", r.nodes).cards.first().front)
    }

    @Test
    fun ankiDeckNamesNestAndClozeIsRestored() {
        val text = "#separator:tab\n#html:true\n#deck column:3\n前 221 年\t<b>秦</b>\t中国朝代::秦汉\n前 770 年\t东周\t中国朝代::先秦::周\n{{c1::氢}}是 1 号\t\t中国朝代::杂项\n"
        val r = ok("x.txt", text)
        assertEquals("中国朝代", r.palaces.single().nameZh)
        val t = PalaceTree(r.palaces.single().id, r.nodes)
        assertEquals(listOf("秦汉", "先秦", "杂项"), t.loci.map { it.title })
        assertEquals("秦", t.cardsUnder(t.loci[0].id).single().back)
        val cloze = t.cardsUnder(t.loci[2].id).single()
        assertEquals("[…]是 1 号", cloze.front)
        assertEquals("氢是 1 号", cloze.back)
        assertEquals(3, r.depth)
        assertEquals(ImportError.TOO_DEEP, fail("y.txt", "#deck column:3\nf\tb\tA::B::C::D\n"))
    }

    @Test
    fun outlineTxtByIndent() {
        val r = ok("talk.txt", "开场\n  问好 | 大家好\n主体\n  方法\n    位置 | 熟悉的房间\n  例子 | 朝代\n结尾 | 谢谢\n")
        val t = PalaceTree("t", r.nodes)
        assertEquals(listOf("开场", "主体", "结尾"), t.loci.map { it.label })
        assertEquals(3, r.depth)
        assertEquals(ImportError.TOO_DEEP, fail("deep.txt", "a\n  b\n    c\n      d | e\n"))
    }

    @Test
    fun overFullPalacesSplitByWholeLoci() {
        val text = (1..25).joinToString("\n") { "g$it,f$it,b$it" }
        val r = ok("many.csv", "分组,正面,背面\n$text")
        assertEquals(2, r.palaces.size)
        assertEquals("many 1/2", r.palaces[0].nameZh)
        assertEquals(20, PalaceTree(r.palaces[0].id, r.nodes).loci.size)
    }

    // ---------------------------------------------------------------- placing

    private val wall = PlaneSurface("w", "WALL", listOf(Vec3(-2f, 0f, -2f), Vec3(2f, 0f, -2f), Vec3(2f, 3f, -2f), Vec3(-2f, 3f, -2f)))
    private val floorPlane = PlaneSurface("f", "FLOOR", listOf(Vec3(-2f, 0f, -2f), Vec3(2f, 0f, -2f), Vec3(2f, 0f, 2f), Vec3(-2f, 0f, 2f)))

    @Test
    fun placingRules() {
        val n = Vec3(0f, 0f, 1f)
        val ok = PlaceRules.spot(Vec3(0f, 1.3f, -2f), n, wall)
        assertNull(PlaceRules.check(ok, wall, 0f, emptyList(), 0))
        assertEquals(BlockReason.TOO_LOW, PlaceRules.check(PlaceRules.spot(Vec3(0f, 0.6f, -2f), n, wall), wall, 0f, emptyList(), 0))
        assertEquals(BlockReason.TOO_HIGH, PlaceRules.check(PlaceRules.spot(Vec3(0f, 1.9f, -2f), n, wall), wall, 0f, emptyList(), 0))
        assertEquals(BlockReason.SURFACE, PlaceRules.check(PlaceRules.spot(Vec3(0f, 0f, 0f), Vec3.UP, floorPlane), floorPlane, 0f, emptyList(), 0))
        assertEquals(BlockReason.TOO_CLOSE, PlaceRules.check(ok, wall, 0f, listOf(Vec3(0.5f, 1.3f, -1.985f)), 0))
        assertEquals(BlockReason.TOO_FAR_FROM_ROUTE, PlaceRules.check(ok, wall, 0f, listOf(Vec3(0f, 1.3f, 3.5f)), 0))
        assertEquals(BlockReason.LIMIT, PlaceRules.check(ok, wall, 0f, emptyList(), 300))
    }

    @Test
    fun raycastHitsTheWallAhead() {
        val hit = raycast(listOf(wall, floorPlane), Vec3(0f, 1.6f, 0f), Vec3(0f, 0f, -1f))!!
        assertEquals("w", hit.plane.id)
        assertEquals(2f, hit.distance, 1e-4f)
        assertTrue(hit.normal.z > 0f)
    }

    // ---------------------------------------------------------------- wake

    private fun locus(id: String, x: Float, z: Float, normal: Vec3 = Vec3(0f, 0f, 1f)) = Locus(id, "p", "a", 0, Vec3(x, 1.3f, z), normal, SurfaceKind.WALL)

    private fun head(x: Float, z: Float) = HeadPose(Vec3(x, 1.6f, z), Quat4.IDENTITY)

    @Test
    fun wakeHysteresisAndFacing() {
        val a = locus("a", 0f, -2f)
        assertEquals("a", Wake.pick(listOf(a), setOf("a"), null, head(0f, -1.1f)))
        assertNull(Wake.pick(listOf(a), setOf("a"), null, head(0f, -0.9f)))
        // open stays open up to 1.2 m, closes beyond
        assertEquals("a", Wake.pick(listOf(a), setOf("a"), "a", head(0f, -0.85f)))
        assertNull(Wake.pick(listOf(a), setOf("a"), "a", head(0f, -0.7f)))
        // behind the wall: no wake
        assertNull(Wake.pick(listOf(a), setOf("a"), null, head(0f, -2.5f)))
        // not a candidate: no wake
        assertNull(Wake.pick(listOf(a), emptySet(), null, head(0f, -1.1f)))
    }

    @Test
    fun wakePrefersTheOneLookedAtThenTheNearest() {
        val a = locus("a", -0.5f, -1.5f)
        val b = locus("b", 0.4f, -1.5f)
        assertEquals("a", Wake.pick(listOf(a, b), setOf("a", "b"), null, head(-0.45f, -0.7f)))
        assertEquals(0.6f, Wake.closer(1.53f), 1e-4f)
    }

    // ---------------------------------------------------------------- sessions

    @Test
    fun reviewForgotComesBackOnce() {
        var r = Review(listOf("x", "y"))
        r = r.rate("x", Sm2.FORGOT)
        assertEquals(setOf("y", "x"), r.pending)
        r = r.rate("y", Sm2.REMEMBERED)
        r = r.rate("x", Sm2.BLURRY)
        assertTrue(r.finished)
        assertEquals(3, r.total)
        assertEquals(1, r.count(Sm2.FORGOT))
        assertEquals(setOf("x"), r.unrate("x").pending)
    }

    @Test
    fun recallFollowsTheRouteAndTheGridOrder() {
        val t = PalaceTree(Presets.DYNASTIES, Presets.nodes())
        val first = t.loci[0].id
        var rc = Recall(t.loci.map { it.id }).reveal(t)
        val kids = t.children(first)
        // ② is not next: refused
        assertFalse(rc.pinch(kids[1].id).second)
        rc = rc.pinch(kids[0].id).first
        assertEquals(kids[0].id, rc.open)
        rc = rc.judge(true)
        rc = rc.pinch(kids[1].id).first.judge(false)
        // the sub-locus 周 is entered, its two cards in order, then the locus is done
        assertEquals(kids[2].id, rc.current()!!.nodeId)
        rc = rc.pinch(kids[2].id).first
        assertEquals(kids[2].id, rc.level(t))
        val zhou = t.children(kids[2].id)
        rc = rc.pinch(zhou[0].id).first.judge(true)
        rc = rc.pinch(zhou[1].id).first.judge(true)
        assertEquals(1, rc.index)
        assertFalse(rc.revealed)
        assertEquals(3, rc.right)
        assertEquals(listOf(kids[1].id), rc.wrong)
        assertEquals(setOf(4), rc.approachedEarly(4).outOfOrder)
    }

    // ---------------------------------------------------------------- persistence

    @Test
    fun libraryRoundTrip() {
        val imported = ok("mine.csv", "a,b\nc,d\n")
        val nodes = Presets.nodes().map { if (it.id == "dynasties/1/0") it.copy(srs = Srs(2.36f, 6, 2, 107, 1)) else it } + imported.nodes
        val loci = listOf(Locus("dynasties/1", Presets.DYNASTIES, "uuid-1", 0, Vec3(1f, 1.3f, -2f), Vec3(0f, 0f, 1f), SurfaceKind.WALL))
        val lib = Library(Presets.palaces + imported.palaces, nodes, loci, imported.palaces.single().id)
        val (p, n, l) = LibraryCodec.encode(lib)
        val back = LibraryCodec.decode(p, n, l)
        assertEquals(lib.current, back.current)
        assertEquals(lib.palaces, back.palaces)
        assertEquals(lib.loci, back.loci)
        assertEquals(Srs(2.36f, 6, 2, 107, 1), back.nodes.first { it.id == "dynasties/1/0" }.srs)
        assertEquals(lib.nodes.size, back.nodes.size)
    }
}
