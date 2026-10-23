package io.embodit.memorypalace.domain

/**
 * The three bundled palaces (design `presets`). Card content stays in its own language (design
 * `i18n.note`); only the palace names are bilingual. Ids are stable (`<palace>/<path>`), so the
 * progress of a preset survives an app update.
 */
object Presets {
    const val DYNASTIES = "dynasties"
    const val ELEMENTS = "elements20"
    const val TRAVEL = "travel-en"

    private sealed interface Item

    private data class G(val title: String, val kids: List<Item>) : Item

    private data class C(val front: String, val back: String) : Item

    private fun g(title: String, vararg kids: Item) = G(title, kids.toList())

    private fun c(front: String, back: String) = C(front, back)

    val palaces: List<Palace> =
        listOf(
            Palace(DYNASTIES, "中国朝代", "Chinese Dynasties", 0, "preset:$DYNASTIES"),
            Palace(ELEMENTS, "前 20 号元素", "First 20 Elements", 1, "preset:$ELEMENTS"),
            Palace(TRAVEL, "旅行英语", "Travel Phrases", 2, "preset:$TRAVEL"),
        )

    private val trees: Map<String, List<Item>> =
        mapOf(
            DYNASTIES to
                listOf(
                    g(
                        "先秦",
                        c("约前 2070 年", "夏 · 传说中的第一个王朝"),
                        c("约前 1600 年", "商 · 甲骨文"),
                        g("周", c("前 1046 年", "西周 · 分封与礼乐"), c("前 770 年", "东周 · 春秋战国")),
                    ),
                    g("秦汉", c("前 221 年", "秦 · 统一六国"), c("前 202 年", "西汉 · 丝绸之路"), c("25 年", "东汉 · 造纸术改进")),
                    g("三国两晋南北朝", c("220 年", "三国 · 魏蜀吴"), c("266 年", "晋 · 西晋与东晋"), c("420 年", "南北朝 · 南北对峙")),
                    g("隋唐五代", c("581 年", "隋 · 大运河"), c("618 年", "唐 · 贞观之治"), c("907 年", "五代十国 · 分裂")),
                    g("宋元明清", c("960 年", "宋 · 活字印刷"), c("1271 年", "元 · 定都大都"), c("1368 年", "明 · 郑和下西洋"), c("1644 年", "清 · 入关定都北京")),
                ),
            ELEMENTS to
                listOf(
                    g("第 1 周期", c("1 号", "H 氢 Hydrogen"), c("2 号", "He 氦 Helium")),
                    g(
                        "第 2 周期",
                        c("3 号", "Li 锂 Lithium"), c("4 号", "Be 铍 Beryllium"), c("5 号", "B 硼 Boron"), c("6 号", "C 碳 Carbon"),
                        c("7 号", "N 氮 Nitrogen"), c("8 号", "O 氧 Oxygen"), c("9 号", "F 氟 Fluorine"), c("10 号", "Ne 氖 Neon"),
                    ),
                    g(
                        "第 3 周期",
                        c("11 号", "Na 钠 Sodium"), c("12 号", "Mg 镁 Magnesium"), c("13 号", "Al 铝 Aluminium"), c("14 号", "Si 硅 Silicon"),
                        c("15 号", "P 磷 Phosphorus"), c("16 号", "S 硫 Sulfur"), c("17 号", "Cl 氯 Chlorine"), c("18 号", "Ar 氩 Argon"),
                    ),
                    g("第 4 周期前两个", c("19 号", "K 钾 Potassium"), c("20 号", "Ca 钙 Calcium")),
                ),
            TRAVEL to
                listOf(
                    g("机场", c("办理登机", "Check in"), c("登机口", "Gate"), c("我的行李丢了", "My luggage is lost."), c("靠窗座位", "A window seat, please."), c("转机", "Connecting flight")),
                    g("酒店", c("我想退房", "I'd like to check out."), c("房卡", "Key card"), c("我有预订", "I have a reservation."), c("几点吃早餐？", "What time is breakfast?"), c("能帮我叫辆车吗？", "Could you call me a taxi?")),
                    g("餐厅", c("买单", "Check, please."), c("两位", "A table for two."), c("我对花生过敏", "I'm allergic to peanuts."), c("推荐什么？", "What do you recommend?"), c("打包", "Can I get this to go?")),
                ),
        )

    /** All nodes of every preset palace. */
    fun nodes(): List<Node> = palaces.flatMap { p -> build(p.id, null, p.id, trees[p.id].orEmpty()) }

    private fun build(palaceId: String, parentId: String?, prefix: String, items: List<Item>): List<Node> {
        val out = ArrayList<Node>()
        items.forEachIndexed { i, item ->
            val id = "$prefix/$i"
            when (item) {
                is G -> {
                    out += Node(id, palaceId, parentId, i, NodeKind.GROUP, title = item.title)
                    out += build(palaceId, id, id, item.kids)
                }
                is C -> out += Node(id, palaceId, parentId, i, NodeKind.CARD, front = item.front, back = item.back, srs = Srs())
            }
        }
        return out
    }
}
