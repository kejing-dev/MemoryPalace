package io.embodit.memorypalace.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The builder on real articles (the debug fixtures in `src/debug/assets/wiki`, fetched 2026-10-22). */
class WikiBuilderTest {
    private fun fixture(name: String): String = File("src/debug/assets/wiki/$name").readText()

    private fun dump(d: WikiDraft) {
        val out = StringBuilder("${d.lang} ${d.title} rev=${d.revision} cards=${d.cards}\nlead: ${d.lead}\n")
        for (l in d.loci) {
            out.append("# ${l.title} (${l.cards.size})\n")
            for (c in l.cards) out.append("  F: ${c.front}\n  B: ${c.back}\n")
        }
        File("build/wiki-${d.lang}-${d.title}.txt").apply { parentFile.mkdirs() }.writeText(out.toString())
    }

    @Test
    fun greatWallInChinese() {
        val d = WikiBuilder.build(fixture("zh-长城.html"), "zh", "长城")
        dump(d)
        assertEquals("概述", d.loci.first().title)
        assertTrue(d.revision > 0)
        assertTrue(d.cards >= WikiBuilder.MIN_CARDS)
        assertTrue(d.cards <= WikiBuilder.MAX_CARDS)
        assertTrue(d.loci.size <= WikiBuilder.MAX_LOCI)
        assertTrue(d.loci.none { it.title in setOf("参见", "参考资料", "外部链接") })
        for (l in d.loci) {
            assertTrue(l.cards.size <= WikiBuilder.CARDS_PER_LOCUS)
            for (c in l.cards) {
                assertTrue(c.front, c.front.contains(WikiBuilder.BLANK))
                assertTrue(c.front, c.front.length <= Limits.FRONT_MAX)
                assertTrue(c.back, c.back.length <= Limits.BACK_MAX)
                assertTrue(c.back.startsWith(c.word))
                assertTrue(c.word.length in 2..10)
            }
        }
        // simplified Chinese (Accept-Language: zh-cn when it was fetched)
        assertTrue(d.lead.contains("长城"))
    }

    @Test
    fun greatWallInEnglish() {
        val d = WikiBuilder.build(fixture("en-Great Wall of China.html"), "en", "Great Wall of China")
        dump(d)
        assertEquals("Overview", d.loci.first().title)
        assertTrue(d.cards >= WikiBuilder.MIN_CARDS)
        assertTrue(d.loci.none { it.title.lowercase() in setOf("see also", "references", "external links", "notes") })
    }

    @Test
    fun disambiguationIsTooShort() {
        val d = WikiBuilder.build(fixture("zh-长城镇.html"), "zh", "长城镇")
        dump(d)
        assertTrue(d.cards < WikiBuilder.MIN_CARDS)
    }

    @Test
    fun searchHitsAndPalace() {
        val hits = WikiBuilder.hits(fixture("zh-search-长城.json"))
        assertEquals("长城", hits.first().title)
        assertTrue(hits.first().description.isNotEmpty())
        val d = WikiBuilder.build(fixture("zh-长城.html"), "zh", "长城")
        val keep = setOf(0, 1)
        val (palace, nodes) = WikiBuilder.palace(d, keep, "wiki-1", 5)
        val t = PalaceTree("wiki-1", nodes)
        assertEquals(2, t.loci.size)
        assertEquals(2, t.depth)
        assertEquals(d.loci[0].cards.size + d.loci[1].cards.size, t.cards.size)
        assertEquals("https://zh.wikipedia.org/wiki/长城", palace.wiki!!.url)
        assertEquals(d.revision, palace.wiki!!.revision)
    }
}
