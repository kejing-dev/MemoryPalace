package io.embodit.memorypalace.data

import android.content.Context
import io.embodit.memorypalace.domain.Locus
import io.embodit.memorypalace.domain.Media
import io.embodit.memorypalace.domain.MediaKind
import io.embodit.memorypalace.domain.Node
import io.embodit.memorypalace.domain.NodeKind
import io.embodit.memorypalace.domain.Palace
import io.embodit.memorypalace.domain.Presets
import io.embodit.memorypalace.domain.Quat4
import io.embodit.memorypalace.domain.Srs
import io.embodit.memorypalace.domain.SurfaceKind
import io.embodit.memorypalace.domain.Vec3
import io.embodit.memorypalace.domain.WikiSource
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** A world anchor as the app sees it. [id] is the platform's UUID as a string. */
data class AnchorRecord(val id: String, val position: Vec3, val rotation: Quat4)

sealed interface AnchorOutcome<out T> {
    data class Ok<T>(val value: T) : AnchorOutcome<T>

    data class Failed(val code: Int, val message: String) : AnchorOutcome<Nothing>
}

/** The platform's persistent anchors (`platform.anchor.WorldAnchorGateway`; a fake in tests). */
interface AnchorGateway {
    suspend fun create(position: Vec3, name: String): AnchorOutcome<AnchorRecord>

    /** All anchors of this app in this space; an empty store is `Ok(emptyList())`. */
    suspend fun loadAll(): AnchorOutcome<List<AnchorRecord>>

    suspend fun remove(anchorId: String): AnchorOutcome<Unit>
}

enum class LanguagePref(val tag: String) { SYSTEM("system"), ZH("zh"), EN("en") }

/** Everything the app keeps: palaces (presets + imported), their nodes with SM-2 records, the pinned loci. */
data class Library(val palaces: List<Palace>, val nodes: List<Node>, val loci: List<Locus>, val current: String) {
    val currentPalace: Palace
        get() = palaces.firstOrNull { it.id == current } ?: palaces.first()
}

interface PalaceStore {
    fun load(): Library

    fun save(library: Library)

    fun language(): LanguagePref

    fun saveLanguage(pref: LanguagePref)
}

/**
 * App-private JSON files (design `dataModel.storage`): `palaces.json` (imported palaces, the current
 * one), `nodes.json` (imported nodes, SM-2 records of every card) and `loci.json`. Offline only.
 */
class FilePalaceStore(context: Context) : PalaceStore {
    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("memory_palace", Context.MODE_PRIVATE)

    override fun load(): Library = LibraryCodec.decode(read("palaces.json"), read("nodes.json"), read("loci.json"))

    override fun save(library: Library) {
        val (p, n, l) = LibraryCodec.encode(library)
        write("palaces.json", p)
        write("nodes.json", n)
        write("loci.json", l)
    }

    override fun language(): LanguagePref = LanguagePref.entries.firstOrNull { it.tag == prefs.getString("lang", null) } ?: LanguagePref.SYSTEM

    override fun saveLanguage(pref: LanguagePref) {
        prefs.edit().putString("lang", pref.tag).apply()
    }

    private fun read(name: String): String? = runCatching { File(dir, name).takeIf { it.exists() }?.readText() }.getOrNull()

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}

/**
 * JSON codec of the library; bundled palaces (presets and poetry packs) are rebuilt from code and only
 * their SM-2 records are stored, plus which packs were built. Imported and Wikipedia palaces are stored whole.
 */
object LibraryCodec {
    private val bundledIds: Set<String> = (Presets.palaces + Presets.packs).map { it.id }.toSet()

    fun encode(lib: Library): Triple<String, String, String> {
        val presetIds = bundledIds
        val packs = JSONArray().apply { lib.palaces.filter { Presets.isPack(it.id) }.forEach { put(it.id) } }
        val palaces = JSONObject().put("current", lib.current).put("packs", packs).put("imported", JSONArray().apply { lib.palaces.filter { it.id !in presetIds }.forEach { put(palace(it)) } })
        val nodes =
            JSONObject()
                // v0.3: items added to (or loci renamed in) a bundled palace are stored whole and win over the bundle
                .put("imported", JSONArray().apply { lib.nodes.filter { it.palaceId !in presetIds || changed(it) }.forEach { put(node(it)) } })
                .put("srs", JSONObject().apply { lib.nodes.filter { it.palaceId in presetIds && !changed(it) && it.srs != null && it.srs != Srs() }.forEach { put(it.id, srs(it.srs!!)) } })
        val loci = JSONArray().apply { lib.loci.forEach { put(locus(it)) } }
        return Triple(palaces.toString(), nodes.toString(), loci.toString())
    }

    fun decode(palacesJson: String?, nodesJson: String?, lociJson: String?): Library {
        val p = runCatching { JSONObject(palacesJson ?: "{}") }.getOrElse { JSONObject() }
        val n = runCatching { JSONObject(nodesJson ?: "{}") }.getOrElse { JSONObject() }
        val l = runCatching { JSONArray(lociJson ?: "[]") }.getOrElse { JSONArray() }
        val imported = p.optJSONArray("imported").objects().mapNotNull { runCatching { palace(it) }.getOrNull() }
        val srsMap = n.optJSONObject("srs") ?: JSONObject()
        val presetNodes = Presets.nodes().map { node -> srsMap.optJSONObject(node.id)?.let { node.copy(srs = srs(it)) } ?: node }
        val importedNodes = n.optJSONArray("imported").objects().mapNotNull { runCatching { node(it) }.getOrNull() }
        val built = p.optJSONArray("packs")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().toSet()
        val palaces = Presets.palaces + Presets.packs.filter { it.id in built && it !in Presets.palaces } + imported
        val own = importedNodes.map { it.id }.toSet()
        val ids = (presetNodes + importedNodes).map { it.id }.toSet()
        val loci = l.objects().mapNotNull { runCatching { locus(it) }.getOrNull() }.filter { it.nodeId in ids }
        // v0.2: a first launch opens on Tang Poems (design S02); a saved choice is kept
        val current = p.optString("current").takeIf { c -> palaces.any { it.id == c } } ?: Presets.TANG
        return Library(palaces, presetNodes.filter { it.id !in own } + importedNodes, loci, current)
    }

    private val bundled: Map<String, Node> by lazy { Presets.nodes().associateBy { it.id } }

    /** A bundled node the user changed, or one they added to a bundled palace. */
    private fun changed(n: Node): Boolean = bundled[n.id]?.let { it.copy(srs = n.srs) != n } ?: true

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun palace(x: Palace) =
        JSONObject().put("id", x.id).put("zh", x.nameZh).put("en", x.nameEn).put("color", x.color).put("source", x.source)
            .apply { x.wiki?.let { w -> put("wiki", JSONObject().put("lang", w.lang).put("title", w.title).put("url", w.url).put("revision", w.revision)) } }

    private fun palace(o: JSONObject) =
        Palace(
            o.getString("id"), o.getString("zh"), o.optString("en", o.getString("zh")), o.optInt("color"), o.optString("source"),
            o.optJSONObject("wiki")?.let { w -> WikiSource(w.optString("lang"), w.optString("title"), w.optString("url"), w.optLong("revision")) },
        )

    private fun node(x: Node) =
        JSONObject().put("id", x.id).put("palace", x.palaceId).put("parent", x.parentId ?: JSONObject.NULL).put("order", x.order).put("kind", x.kind.name)
            .put("title", x.title).put("front", x.front).put("back", x.back).apply {
                x.srs?.let { put("srs", srs(it)) }
                if (x.author.isNotEmpty()) put("author", x.author)
                x.media?.let { m -> put("media", JSONObject().put("kind", m.kind.name).put("file", m.file).put("name", m.name).put("beside", m.beside).put("yaw", m.yawDeg.toDouble())) }
            }

    private fun node(o: JSONObject) =
        Node(
            o.getString("id"), o.getString("palace"), if (o.isNull("parent")) null else o.getString("parent"), o.optInt("order"),
            NodeKind.valueOf(o.getString("kind")), o.optString("title"), o.optString("front"), o.optString("back"),
            o.optJSONObject("srs")?.let { srs(it) } ?: if (o.getString("kind") == "CARD") Srs() else null,
            o.optString("author"),
            o.optJSONObject("media")?.let { m -> runCatching { Media(MediaKind.valueOf(m.getString("kind")), m.getString("file"), m.optString("name"), m.optBoolean("beside"), m.optDouble("yaw", 0.0).toFloat()) }.getOrNull() },
        )

    private fun srs(s: Srs) = JSONObject().put("ef", s.ef.toDouble()).put("interval", s.interval).put("reps", s.reps).put("due", s.dueDay ?: JSONObject.NULL).put("lapses", s.lapses)

    private fun srs(o: JSONObject) = Srs(o.optDouble("ef", 2.5).toFloat(), o.optInt("interval"), o.optInt("reps"), if (o.isNull("due") || !o.has("due")) null else o.getInt("due"), o.optInt("lapses"))

    private fun vec(v: Vec3) = JSONArray().put(v.x.toDouble()).put(v.y.toDouble()).put(v.z.toDouble())

    private fun vec(a: JSONArray) = Vec3(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getDouble(2).toFloat())

    private fun locus(x: Locus) =
        JSONObject().put("node", x.nodeId).put("palace", x.palaceId).put("anchor", x.anchorId).put("route", x.route).put("pos", vec(x.position)).put("normal", vec(x.normal)).put("surface", x.surface.name)

    private fun locus(o: JSONObject) =
        Locus(o.getString("node"), o.getString("palace"), o.getString("anchor"), o.getInt("route"), vec(o.getJSONArray("pos")), vec(o.getJSONArray("normal")), SurfaceKind.valueOf(o.optString("surface", "WALL")))
}
