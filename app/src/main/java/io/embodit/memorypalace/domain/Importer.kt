package io.embodit.memorypalace.domain

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

/** Why a file could not be imported (design `import.result`). */
enum class ImportError { EMPTY, NO_TWO_COLUMNS, TOO_DEEP, ENCODING }

sealed interface ImportOutcome {
    data class Ok(val palaces: List<Palace>, val nodes: List<Node>, val loci: Int, val cards: Int, val depth: Int) : ImportOutcome

    data class Failed(val error: ImportError) : ImportOutcome
}

/**
 * Palace import (design `import`): CSV with two columns (flat) or three (`group,front,back`, the group
 * split on `/`, at most 2 group levels); an Anki "plain text notes" export, whose deck name splits on
 * `::` into palace › locus › sub-locus (HTML stripped, `{{c1::…}}` restored); or an outline TXT where
 * every 2 spaces / 1 tab of indent is a level and `front | back` or `front<Tab>back` is a card.
 * UTF-8 first, GBK when that fails. Pure Kotlin: [parse] runs in the JVM tests.
 */
object Importer {
    const val MAX_LINES = 500

    private sealed interface T

    private class TG(val title: String, val kids: MutableList<T> = ArrayList()) : T

    private class TC(val front: String, val back: String) : T

    fun decode(bytes: ByteArray): String? {
        val body = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        for (cs in listOf(Charsets.UTF_8, runCatching { Charset.forName("GBK") }.getOrNull())) {
            if (cs == null) continue
            try {
                val dec = cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                return dec.decode(ByteBuffer.wrap(body)).toString()
            } catch (_: CharacterCodingException) {
            }
        }
        return null
    }

    /**
     * [fileName] picks the format and names the palace; [idSeed] makes node ids unique per import;
     * [color] is the palace colour index.
     */
    fun parse(fileName: String, bytes: ByteArray, idSeed: String, color: Int): ImportOutcome {
        val text = decode(bytes) ?: return ImportOutcome.Failed(ImportError.ENCODING)
        val lines = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').take(MAX_LINES + 8)
        if (lines.none { it.isNotBlank() && !it.startsWith("#") }) return ImportOutcome.Failed(ImportError.EMPTY)
        val base = fileName.substringAfterLast('/').substringBeforeLast('.').ifBlank { "Palace" }
        val lower = fileName.lowercase()
        val parsed: Pair<String, List<T>> =
            try {
                when {
                    lower.endsWith(".csv") -> base to csv(lines)
                    lines.any { it.startsWith("#separator") || it.startsWith("#deck column") || it.startsWith("#html") } -> anki(lines, base)
                    lines.filter { it.isNotBlank() }.all { !it[0].isWhitespace() && it.contains('\t') && !it.contains(" | ") } -> anki(lines, base)
                    else -> base to outline(lines)
                }
            } catch (e: ParseFail) {
                return ImportOutcome.Failed(e.error)
            }
        val (name, roots) = parsed
        val loci = normalize(roots)
        val cardCount = loci.sumOf { count(it) }
        if (cardCount == 0) return ImportOutcome.Failed(if (roots.isEmpty()) ImportError.EMPTY else ImportError.NO_TWO_COLUMNS)
        // split by whole loci into "Name 1/2" … (design `import.split`)
        val chunks = ArrayList<MutableList<T>>()
        var cur = ArrayList<T>()
        var curCards = 0
        for (l in loci) {
            val n = count(l)
            if (cur.isNotEmpty() && (cur.size >= Limits.LOCI_PER_PALACE || curCards + n > Limits.CARDS_PER_PALACE)) {
                chunks += cur
                cur = ArrayList()
                curCards = 0
            }
            cur += l
            curCards += n
        }
        if (cur.isNotEmpty()) chunks += cur
        val palaces = ArrayList<Palace>()
        val nodes = ArrayList<Node>()
        var depth = 0
        chunks.forEachIndexed { i, chunk ->
            val pid = if (chunks.size == 1) idSeed else "$idSeed-${i + 1}"
            val pname = if (chunks.size == 1) name else "$name ${i + 1}/${chunks.size}"
            palaces += Palace(pid, pname, pname, color, "import:$fileName")
            chunk.forEachIndexed { j, t -> nodes += emit(pid, null, "$pid/$j", j, t, 1) { depth = maxOf(depth, it) } }
        }
        return ImportOutcome.Ok(palaces, nodes, loci.size, cardCount, depth)
    }

    private class ParseFail(val error: ImportError) : Exception()

    // ---------------------------------------------------------------------------- CSV

    private val HEADERS = setOf("front", "back", "正面", "背面", "group", "分组", "question", "answer", "问题", "答案")

    private fun csv(lines: List<String>): List<T> {
        val rows = csvRows(lines.joinToString("\n")).filter { r -> r.any { it.isNotBlank() } }.take(MAX_LINES)
        if (rows.isEmpty()) throw ParseFail(ImportError.EMPTY)
        val body = if (rows.first().any { it.trim().lowercase() in HEADERS }) rows.drop(1) else rows
        val cols = body.maxOfOrNull { r -> r.count { it.isNotBlank() } } ?: 0
        if (cols < 2) throw ParseFail(ImportError.NO_TWO_COLUMNS)
        val roots = ArrayList<T>()
        if (cols == 2 || body.all { it.size < 3 }) {
            for (r in body) {
                val f = r.getOrNull(0)?.trim().orEmpty()
                val b = r.getOrNull(1)?.trim().orEmpty()
                if (f.isNotEmpty() && b.isNotEmpty()) roots += card(f, b)
            }
            return flat(roots)
        }
        for (r in body) {
            val path = r.getOrNull(0)?.split('/')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            val f = r.getOrNull(1)?.trim().orEmpty()
            val b = r.getOrNull(2)?.trim().orEmpty()
            if (f.isEmpty() || b.isEmpty()) continue
            if (path.size > 2) throw ParseFail(ImportError.TOO_DEEP)
            insert(roots, path, card(f, b))
        }
        return roots
    }

    /** RFC 4180-ish: quotes, doubled quotes, commas and newlines inside quotes; `;` when there is no comma. */
    private fun csvRows(text: String): List<List<String>> {
        val sep = if (!text.contains(',') && text.contains(';')) ';' else ','
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        cell.append('"')
                        i++
                    } else quoted = false
                } else cell.append(ch)
            } else {
                when (ch) {
                    '"' -> quoted = true
                    sep -> {
                        row += cell.toString()
                        cell.clear()
                    }
                    '\n' -> {
                        row += cell.toString()
                        cell.clear()
                        rows += row
                        row = ArrayList()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row += cell.toString()
            rows += row
        }
        return rows
    }

    // ---------------------------------------------------------------------------- Anki

    private fun anki(lines: List<String>, fallbackName: String): Pair<String, List<T>> {
        var sep = '\t'
        var deckCol = -1
        val data = ArrayList<String>()
        for (l in lines) {
            if (l.startsWith("#")) {
                val (k, v) = l.drop(1).split(':', limit = 2).let { it[0].trim() to it.getOrElse(1) { "" }.trim() }
                when (k) {
                    "separator" -> sep = when (v.lowercase()) { "tab" -> '\t'; "comma" -> ','; "semicolon" -> ';'; "pipe" -> '|'; "space" -> ' '; else -> v.firstOrNull() ?: '\t' }
                    "deck column" -> deckCol = (v.toIntOrNull() ?: 0) - 1
                }
                continue
            }
            if (l.isNotBlank()) data += l
        }
        val roots = ArrayList<T>()
        var palaceName: String? = null
        var anyDeck = false
        for (l in data.take(MAX_LINES)) {
            val cols = l.split(sep).map { unquote(it) }
            val deck = if (deckCol >= 0) cols.getOrNull(deckCol) else null
            val fields = cols.filterIndexed { i, _ -> i != deckCol }
            var f = clean(fields.getOrNull(0).orEmpty())
            var b = clean(fields.getOrNull(1).orEmpty())
            if (CLOZE.containsMatchIn(f)) {
                val full = CLOZE.replace(f) { it.groupValues[1] }
                if (b.isEmpty()) b = full
                f = CLOZE.replace(f, "[…]")
            }
            if (f.isEmpty() || b.isEmpty()) continue
            val path = deck?.split("::")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
            if (path.isNotEmpty()) {
                anyDeck = true
                if (palaceName == null) palaceName = path[0]
            }
            val groups = path.drop(1)
            if (groups.size > 2) throw ParseFail(ImportError.TOO_DEEP)
            insert(roots, groups, card(f, b))
        }
        if (roots.isEmpty()) throw ParseFail(if (data.isEmpty()) ImportError.EMPTY else ImportError.NO_TWO_COLUMNS)
        val flatOnly = roots.all { it is TC }
        return (palaceName ?: fallbackName) to if (flatOnly || !anyDeck) flat(roots.filterIsInstance<TC>().ifEmpty { null } ?: roots) else roots
    }

    private val CLOZE = Regex("\\{\\{c\\d+::(.*?)(?:::[^}]*)?\\}\\}")
    private val TAG = Regex("<[^>]+>")

    private fun clean(s: String): String =
        TAG.replace(s.replace("<br>", " ").replace("<br/>", " ").replace("<br />", " "), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
            .trim()

    private fun unquote(s: String): String {
        val t = s.trim()
        return if (t.length >= 2 && t.startsWith('"') && t.endsWith('"')) t.substring(1, t.length - 1).replace("\"\"", "\"") else t
    }

    // ---------------------------------------------------------------------------- outline

    private fun outline(lines: List<String>): List<T> {
        val roots = ArrayList<T>()
        val stack = ArrayList<TG>() // stack[i] = open group at level i
        var sawCard = false
        for (raw in lines.take(MAX_LINES)) {
            if (raw.isBlank() || raw.startsWith("#")) continue
            var level = 0
            var i = 0
            var spaces = 0
            while (i < raw.length && (raw[i] == ' ' || raw[i] == '\t')) {
                if (raw[i] == '\t') level++ else if (++spaces == 2) {
                    level++
                    spaces = 0
                }
                i++
            }
            val body = raw.substring(i).trim().removePrefix("- ").trim()
            if (body.isEmpty()) continue
            val parts = if (body.contains(" | ")) body.split(" | ", limit = 2) else if (body.contains('\t')) body.split('\t', limit = 2) else listOf(body)
            while (stack.size > level) stack.removeAt(stack.size - 1)
            val parent = stack.lastOrNull()
            if (level > stack.size) throw ParseFail(ImportError.TOO_DEEP)
            if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                if (level > 2) throw ParseFail(ImportError.TOO_DEEP)
                val c = card(parts[0].trim(), parts[1].trim())
                if (parent == null) roots += c else parent.kids += c
                sawCard = true
            } else {
                if (level > 1) throw ParseFail(ImportError.TOO_DEEP)
                val g = TG(body.take(Limits.FRONT_MAX))
                if (parent == null) roots += g else parent.kids += g
                stack += g
            }
        }
        if (!sawCard) throw ParseFail(ImportError.NO_TWO_COLUMNS)
        return roots
    }

    // ---------------------------------------------------------------------------- tree shaping

    private fun card(f: String, b: String) = TC(f.take(Limits.FRONT_MAX), b.take(Limits.BACK_MAX))

    private fun insert(roots: MutableList<T>, path: List<String>, c: TC) {
        var list = roots
        for (name in path) {
            val g = list.firstOrNull { it is TG && it.title == name } as TG? ?: TG(name.take(Limits.FRONT_MAX)).also { list += it }
            list = g.kids
        }
        list += c
    }

    /** Flat decks (design `nesting.flatDecks`): ≤ 20 cards = one locus each; more = every 6 into a locus named by its first front. */
    private fun flat(cards: List<T>): List<T> {
        if (cards.size <= Limits.LOCI_PER_PALACE) return cards
        return cards.chunked(Limits.FLAT_GROUP).map { chunk -> TG(((chunk.first() as? TC)?.front ?: "…").take(Limits.FRONT_MAX), chunk.toMutableList()) }
    }

    /** ≤ 8 children per node: an over-full sub-locus splits into siblings, an over-full locus into several loci. */
    private fun normalize(roots: List<T>): List<T> {
        val out = ArrayList<T>()
        for (r in roots) {
            if (r !is TG) {
                out += r
                continue
            }
            val kids = ArrayList<T>()
            for (k in r.kids) {
                if (k is TG && k.kids.size > Limits.CHILDREN_PER_NODE) {
                    val parts = k.kids.chunked(Limits.CHILDREN_PER_NODE)
                    parts.forEachIndexed { i, p -> kids += TG("${k.title} ${i + 1}", p.toMutableList()) }
                } else if (k is TG && k.kids.isEmpty()) continue else kids += k
            }
            if (kids.isEmpty()) continue
            if (kids.size <= Limits.CHILDREN_PER_NODE) out += TG(r.title, kids)
            else kids.chunked(Limits.CHILDREN_PER_NODE).forEachIndexed { i, p -> out += TG("${r.title} ${i + 1}", p.toMutableList()) }
        }
        return out
    }

    private fun count(t: T): Int = if (t is TC) 1 else (t as TG).kids.sumOf { count(it) }

    private fun emit(pid: String, parent: String?, id: String, order: Int, t: T, level: Int, depth: (Int) -> Unit): List<Node> {
        depth(level)
        return when (t) {
            is TC -> listOf(Node(id, pid, parent, order, NodeKind.CARD, front = t.front, back = t.back, srs = Srs()))
            is TG -> listOf(Node(id, pid, parent, order, NodeKind.GROUP, title = t.title)) + t.kids.flatMapIndexed { i, k -> emit(pid, id, "$id/$i", i, k, level + 1, depth) }
        }
    }
}
