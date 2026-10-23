package io.embodit.memorypalace.domain

/** One search hit (design S29). */
data class WikiHit(val title: String, val description: String)

/** A cloze card made from one sentence: [front] has the linked word blanked, [back] is the word and the sentence. */
data class WikiCard(val front: String, val back: String, val word: String)

data class WikiLocus(val title: String, val cards: List<WikiCard>)

/** What an article turns into before the user builds it (design S30). */
data class WikiDraft(val lang: String, val title: String, val revision: Long, val lead: String, val loci: List<WikiLocus>) {
    val cards: Int
        get() = loci.sumOf { it.cards.size }
}

/**
 * Wikipedia article -> palace (design `contentSources.wikipedia.builder`): the lead is the first locus
 * ("Overview"), then one locus per top-level section in article order, at most 8; reference-type sections
 * are skipped. Each locus takes the first sentences that hold an internal link and blanks the first linked
 * word of 2 - 10 characters (3 - 30 in English): front = the sentence with "＿＿", back = the word and the
 * sentence. ≤ 6 cards a locus, ≤ 40 a palace; sections with nothing usable are left out. Two levels only
 * (locus -> card). Works on Parsoid HTML (`/w/rest.php/v1/page/{title}/html`), pure Kotlin for the JVM tests.
 */
object WikiBuilder {
    const val MAX_LOCI = 8
    const val CARDS_PER_LOCUS = 6
    const val MAX_CARDS = 40
    const val MIN_CARDS = 5

    /** English sentences are longer than the design's 40 / 60 Chinese characters: a 0.16 m card holds about this much. */
    const val EN_FRONT_MAX = 90
    const val EN_BACK_MAX = 120

    private val SKIP =
        setOf(
            "参见", "参考文献", "参考资料", "参考来源", "外部链接", "注释", "注解", "延伸阅读", "相关条目", "脚注", "来源", "引用",
            "see also", "references", "external links", "notes", "further reading", "footnotes", "sources", "bibliography", "citations",
        )

    private const val LINK_OPEN = '\u0001'
    private const val LINK_CLOSE = '\u0002'
    const val BLANK = "＿＿"

    /** The inside of a tag whose quoted attributes may hold ">" (Parsoid's data-mw JSON does). */
    private const val ATTRS = """(?:[^>"']|"[^"]*"|'[^']*')*"""

    fun build(html: String, lang: String, title: String): WikiDraft {
        val zh = lang == "zh"
        val revision = Regex("revision/(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val body = clean(html.substringAfter("<body", html))
        val heads = Regex("<h2\\b$ATTRS>(.*?)</h2>", RegexOption.DOT_MATCHES_ALL).findAll(body).toList()
        val sections = ArrayList<Pair<String, String>>()
        sections += (if (zh) "概述" else "Overview") to body.substring(0, heads.firstOrNull()?.range?.first ?: body.length)
        heads.forEachIndexed { i, m ->
            val end = heads.getOrNull(i + 1)?.range?.first ?: body.length
            sections += text(m.groupValues[1]).trim() to body.substring(m.range.last + 1, end)
        }
        val loci = ArrayList<WikiLocus>()
        var total = 0
        var lead = ""
        for ((index, section) in sections.withIndex()) {
            val (name, content) = section
            if (name.isBlank() || name.lowercase() in SKIP) continue
            if (loci.size >= MAX_LOCI || total >= MAX_CARDS) break
            val sentences = paragraphs(content).flatMap { sentences(it, zh) }
            if (index == 0) lead = sentences.firstOrNull()?.let { plain(it) }.orEmpty()
            val cards = ArrayList<WikiCard>()
            val fronts = HashSet<String>()
            for (s in sentences) {
                if (cards.size >= CARDS_PER_LOCUS || total + cards.size >= MAX_CARDS) break
                val c = card(s, zh) ?: continue
                if (fronts.add(c.front)) cards += c
            }
            if (cards.isEmpty()) continue
            loci += WikiLocus(name.take(Limits.FRONT_MAX), cards)
            total += cards.size
        }
        return WikiDraft(lang, title, revision, lead.take(if (zh) 80 else 200), loci)
    }

    /** Drops what never makes a card: styles, references, tables, figures, info boxes, math. */
    private fun clean(html: String): String {
        var s = html
        for (tag in listOf("style", "script", "sup", "table", "figure", "math", "math-element")) {
            s = s.replace(Regex("<$tag\\b$ATTRS>.*?</$tag>", RegexOption.DOT_MATCHES_ALL), "")
        }
        return s
    }

    private fun paragraphs(section: String): List<String> =
        Regex("<p\\b$ATTRS>(.*?)</p>", RegexOption.DOT_MATCHES_ALL).findAll(section).map { m ->
            // internal links become marked spans, every other tag goes
            val linked =
                Regex("(<a\\b$ATTRS>)(.*?)</a>", RegexOption.DOT_MATCHES_ALL).replace(m.groupValues[1]) { a ->
                    if (a.groupValues[1].contains("rel=\"mw:WikiLink\"")) "$LINK_OPEN${text(a.groupValues[2])}$LINK_CLOSE" else text(a.groupValues[2])
                }
            text(linked).replace(Regex("\\s+"), " ").trim()
        }.filter { it.isNotEmpty() }.toList()

    private fun text(html: String): String =
        html.replace(Regex("<$ATTRS>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
            .replace(Regex("\\[\\d+]"), "")

    private fun sentences(p: String, zh: Boolean): List<String> =
        if (zh) Regex("[^。！？]+(?:[。！？][”」』）]*)?").findAll(p).map { it.value.trim() }.filter { it.length > 4 }.toList()
        else p.split(Regex("(?<=[.!?])\\s+(?=[A-Z0-9\"“(])")).map { it.trim() }.filter { it.length > 12 }

    private fun plain(s: String): String = s.replace(LINK_OPEN.toString(), "").replace(LINK_CLOSE.toString(), "")

    private fun card(sentence: String, zh: Boolean): WikiCard? {
        val links = Regex("$LINK_OPEN([^$LINK_CLOSE]*)$LINK_CLOSE").findAll(sentence).toList()
        val pick =
            links.firstOrNull { m ->
                val w = m.groupValues[1].trim()
                val n = w.length
                (if (zh) n in 2..10 else n in 3..30) && !w.all { it.isDigit() || it.isWhitespace() }
            } ?: return null
        val word = pick.groupValues[1].trim()
        val blanked = plain(sentence.substring(0, pick.range.first) + BLANK + sentence.substring(pick.range.last + 1))
        val full = plain(sentence)
        val frontMax = if (zh) Limits.FRONT_MAX else EN_FRONT_MAX
        val backMax = if (zh) Limits.BACK_MAX else EN_BACK_MAX
        val front = window(blanked, frontMax, zh) ?: return null
        val rest = backMax - word.length - 3
        val back = "$word · " + if (full.length <= rest) full else window(full.replaceFirst(word, BLANK), rest, zh)?.replace(BLANK, word) ?: full.take(rest - 1) + "…"
        return WikiCard(front, back.take(backMax), word)
    }

    /** The sentence, or the clauses around the blank when it is longer than [max] ("…" where it was cut). */
    private fun window(s: String, max: Int, zh: Boolean): String? {
        if (s.length <= max) return s
        val sep = if (zh) Regex("(?<=[，、；：])") else Regex("(?<=[,;:])\\s+")
        val parts = s.split(sep).filter { it.isNotEmpty() }
        val at = parts.indexOfFirst { it.contains(BLANK) }
        if (at < 0) return null
        var from = at
        var to = at
        var len = parts[at].length
        while (true) {
            val grow = when {
                to + 1 < parts.size && len + parts[to + 1].length + 1 <= max - 2 -> { to++; parts[to].length }
                from > 0 && len + parts[from - 1].length + 1 <= max - 2 -> { from--; parts[from].length }
                else -> 0
            }
            if (grow == 0) break
            len += grow + 1
        }
        val core = parts.subList(from, to + 1).joinToString(if (zh) "" else " ")
        if (core.length > max - 2) {
            // one clause alone is too long: a window of characters around the blank
            val i = core.indexOf(BLANK)
            val half = (max - 2 - BLANK.length) / 2
            val a = (i - half).coerceAtLeast(0)
            val b = (a + max - 2).coerceAtMost(core.length)
            return (if (a > 0) "…" else "") + core.substring(a, b) + (if (b < core.length) "…" else "")
        }
        return (if (from > 0) "…" else "") + core + (if (to < parts.size - 1) "…" else "")
    }

    /** The palace the user builds from [draft] with the loci left ticked (design T16: new id, fresh SM-2). */
    fun palace(draft: WikiDraft, keep: Set<Int>, id: String, color: Int): Pair<Palace, List<Node>> {
        val url = "https://${draft.lang}.wikipedia.org/wiki/" + draft.title.replace(' ', '_')
        val palace = Palace(id, draft.title, draft.title, color, "wiki:${draft.lang}:${draft.title}", WikiSource(draft.lang, draft.title, url, draft.revision))
        val nodes = ArrayList<Node>()
        draft.loci.withIndex().filter { it.index in keep }.forEachIndexed { order, (_, l) ->
            val lid = "$id/$order"
            nodes += Node(lid, id, null, order, NodeKind.GROUP, title = l.title)
            l.cards.forEachIndexed { j, c -> nodes += Node("$lid/$j", id, lid, j, NodeKind.CARD, front = c.front, back = c.back, srs = Srs()) }
        }
        return palace to nodes
    }

    /** Search JSON of `/w/rest.php/v1/search/title` -> hits. */
    fun hits(json: String): List<WikiHit> {
        val pages = runCatching { org.json.JSONObject(json).optJSONArray("pages") }.getOrNull() ?: return emptyList()
        return (0 until pages.length()).mapNotNull { i ->
            val o = pages.optJSONObject(i) ?: return@mapNotNull null
            WikiHit(o.optString("title"), if (o.isNull("description")) "" else o.optString("description"))
        }
    }
}
