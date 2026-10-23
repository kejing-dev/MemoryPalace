package io.embodit.memorypalace.domain

import io.embodit.memorypalace.data.LibraryCodec
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** v0.2 poetry packs (design `contentSources.poetryPack`). */
class PoetryPackTest {

    @Test
    fun tangAndSongCounts() {
        val tang = PalaceTree(Presets.TANG, Presets.nodes(Presets.TANG))
        assertEquals(listOf("李白", "杜甫", "王维", "白居易", "诸家"), tang.loci.map { it.title })
        assertEquals(14, tang.loci.sumOf { tang.children(it.id).size })
        assertEquals(34, tang.cards.size)
        assertEquals(3, tang.depth)
        val song = PalaceTree(Presets.SONG, Presets.nodes(Presets.SONG))
        assertEquals(4, song.loci.size)
        assertEquals(6, song.loci.sumOf { song.children(it.id).size })
        assertEquals(25, song.cards.size)
    }

    @Test
    fun duFuSpringViewIsTheAcceptanceCard() {
        val t = PalaceTree(Presets.TANG, Presets.nodes(Presets.TANG))
        val du = t.loci.first { it.title == "杜甫" }
        val poem = t.children(du.id).first { it.title == "《春望》" }
        assertEquals("杜甫", poem.author)
        val first = t.children(poem.id).first()
        assertEquals("国破山河在", first.front)
        assertEquals("城春草木深", first.back)
        // a poem of 诸家 keeps its own author
        val zhujia = t.loci.first { it.title == "诸家" }
        assertEquals(listOf("王之涣", "孟浩然", "王昌龄"), t.children(zhujia.id).map { it.author })
    }

    /** Word for word against the design package (when the factory checkout is next to the project). */
    @Test
    fun textMatchesTheDesignPackage() {
        val f = File("../../spatial-app-factory/apps/memory-palace/design-package.json")
        assumeTrue(f.exists())
        val packs = JSONObject(f.readText()).getJSONObject("contentSources").getJSONObject("poetryPack").getJSONArray("packs")
        var checked = 0
        for (i in 0 until packs.length()) {
            val p = packs.getJSONObject(i)
            val t = PalaceTree(p.getString("id"), Presets.nodes(p.getString("id")))
            val loci = p.getJSONArray("loci")
            assertEquals(loci.length(), t.loci.size)
            for (j in 0 until loci.length()) {
                val l = loci.getJSONObject(j)
                assertEquals(l.getString("t"), t.loci[j].title)
                val poems = l.getJSONArray("kids")
                val nodes = t.children(t.loci[j].id)
                for (k in 0 until poems.length()) {
                    val poem = poems.getJSONObject(k)
                    assertEquals(poem.getString("t"), nodes[k].title)
                    assertEquals(poem.getString("author"), nodes[k].author)
                    val lines = poem.getJSONArray("kids")
                    val cards = t.children(nodes[k].id)
                    assertEquals(lines.length(), cards.size)
                    for (m in 0 until lines.length()) {
                        assertEquals(lines.getJSONObject(m).getString("f"), cards[m].front)
                        assertEquals(lines.getJSONObject(m).getString("b"), cards[m].back)
                        checked++
                    }
                }
            }
        }
        assertEquals(59, checked)
    }

    @Test
    fun firstLaunchOpensTangAndSongIsBuiltOnDemand() {
        val fresh = LibraryCodec.decode(null, null, null)
        assertEquals(Presets.TANG, fresh.current)
        assertTrue(fresh.palaces.any { it.id == Presets.TANG })
        assertFalse(fresh.palaces.any { it.id == Presets.SONG })
        // built: it stays in the library, its SM-2 records with it
        val built = fresh.copy(palaces = fresh.palaces + Presets.packs[1], current = Presets.SONG, nodes = fresh.nodes.map { if (it.id == "pack-song/0/0/0") it.copy(srs = Srs(dueDay = 9)) else it })
        val (p, n, l) = LibraryCodec.encode(built)
        val back = LibraryCodec.decode(p, n, l)
        assertEquals(Presets.SONG, back.current)
        assertTrue(back.palaces.any { it.id == Presets.SONG })
        assertEquals(9, back.nodes.first { it.id == "pack-song/0/0/0" }.srs!!.dueDay)
    }

    @Test
    fun wikiPalaceRoundTrip() {
        val d = WikiBuilder.build(File("src/debug/assets/wiki/zh-长城.html").readText(), "zh", "长城")
        val (palace, nodes) = WikiBuilder.palace(d, d.loci.indices.toSet(), "wiki-x", 3)
        val lib = LibraryCodec.decode(null, null, null).let { it.copy(palaces = it.palaces + palace, nodes = it.nodes + nodes) }
        val (p, n, l) = LibraryCodec.encode(lib)
        val back = LibraryCodec.decode(p, n, l)
        assertEquals(palace, back.palaces.first { it.id == "wiki-x" })
        assertEquals(nodes.size, back.nodes.count { it.palaceId == "wiki-x" })
    }
}
