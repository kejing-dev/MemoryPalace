package io.embodit.memorypalace.domain

/**
 * The bundled palaces (design `presets`, v0.2 `contentSources.poetryPack`). Card content stays in its
 * own language (design `i18n.note`); only the palace names are bilingual. Ids are stable
 * (`<palace>/<path>`), so the progress of a preset survives an app update.
 *
 * The poetry packs are public-domain original text only (no modern notes or translations), copied word
 * for word from the design package's `packs` (generated, not typed): palace = a volume, locus = a poet,
 * sub-locus = one poem (title + author), card = one couplet, front the first line, back the next.
 * Tang Poems is the default first palace; Song Ci joins the library when it is built (S28).
 */
object Presets {
    const val TANG = "pack-tang"
    const val SONG = "pack-song"
    const val DYNASTIES = "dynasties"
    const val ELEMENTS = "elements20"
    const val TRAVEL = "travel-en"

    private sealed interface Item

    private data class G(val title: String, val kids: List<Item>, val author: String = "") : Item

    private data class C(val front: String, val back: String) : Item

    private fun g(title: String, vararg kids: Item) = G(title, kids.toList())

    private fun c(front: String, back: String) = C(front, back)

    private fun poem(title: String, author: String, vararg lines: Item) = G(title, lines.toList(), author)

    /** The poetry packs (S28), the library's 「诗词」 group. */
    val packs: List<Palace> =
        listOf(
            Palace(TANG, "唐诗名篇", "Tang Poems", 0, "pack:$TANG"),
            Palace(SONG, "宋词名篇", "Song Ci", 4, "pack:$SONG"),
        )

    /** In every library; [SONG] is added when the user builds it. */
    val palaces: List<Palace> =
        listOf(
            packs[0],
            Palace(DYNASTIES, "中国朝代", "Chinese Dynasties", 0, "preset:$DYNASTIES"),
            Palace(ELEMENTS, "前 20 号元素", "First 20 Elements", 1, "preset:$ELEMENTS"),
            Palace(TRAVEL, "旅行英语", "Travel Phrases", 2, "preset:$TRAVEL"),
        )

    private val trees: Map<String, List<Item>> =
        mapOf(
            TANG to
                listOf(
                    g(
                        "李白",
                        poem("《静夜思》", "李白", c("床前明月光", "疑是地上霜"), c("举头望明月", "低头思故乡")),
                        poem("《望庐山瀑布》", "李白", c("日照香炉生紫烟", "遥看瀑布挂前川"), c("飞流直下三千尺", "疑是银河落九天")),
                        poem("《早发白帝城》", "李白", c("朝辞白帝彩云间", "千里江陵一日还"), c("两岸猿声啼不住", "轻舟已过万重山")),
                    ),
                    g(
                        "杜甫",
                        poem("《春望》", "杜甫", c("国破山河在", "城春草木深"), c("感时花溅泪", "恨别鸟惊心"), c("烽火连三月", "家书抵万金"), c("白头搔更短", "浑欲不胜簪")),
                        poem("《绝句》", "杜甫", c("两个黄鹂鸣翠柳", "一行白鹭上青天"), c("窗含西岭千秋雪", "门泊东吴万里船")),
                        poem("《春夜喜雨》", "杜甫", c("好雨知时节", "当春乃发生"), c("随风潜入夜", "润物细无声"), c("野径云俱黑", "江船火独明"), c("晓看红湿处", "花重锦官城")),
                    ),
                    g(
                        "王维",
                        poem("《鹿柴》", "王维", c("空山不见人", "但闻人语响"), c("返景入深林", "复照青苔上")),
                        poem("《相思》", "王维", c("红豆生南国", "春来发几枝"), c("愿君多采撷", "此物最相思")),
                        poem("《九月九日忆山东兄弟》", "王维", c("独在异乡为异客", "每逢佳节倍思亲"), c("遥知兄弟登高处", "遍插茱萸少一人")),
                    ),
                    g(
                        "白居易",
                        poem("《赋得古原草送别》", "白居易", c("离离原上草", "一岁一枯荣"), c("野火烧不尽", "春风吹又生"), c("远芳侵古道", "晴翠接荒城"), c("又送王孙去", "萋萋满别情")),
                        poem("《暮江吟》", "白居易", c("一道残阳铺水中", "半江瑟瑟半江红"), c("可怜九月初三夜", "露似真珠月似弓")),
                    ),
                    g(
                        "诸家",
                        poem("《登鹳雀楼》", "王之涣", c("白日依山尽", "黄河入海流"), c("欲穷千里目", "更上一层楼")),
                        poem("《春晓》", "孟浩然", c("春眠不觉晓", "处处闻啼鸟"), c("夜来风雨声", "花落知多少")),
                        poem("《出塞》", "王昌龄", c("秦时明月汉时关", "万里长征人未还"), c("但使龙城飞将在", "不教胡马度阴山")),
                    ),
                ),
            SONG to
                listOf(
                    g(
                        "苏轼",
                        poem("《水调歌头·明月几时有》", "苏轼", c("明月几时有", "把酒问青天"), c("不知天上宫阙", "今夕是何年"), c("我欲乘风归去", "又恐琼楼玉宇，高处不胜寒"), c("起舞弄清影", "何似在人间"), c("转朱阁，低绮户", "照无眠"), c("不应有恨", "何事长向别时圆"), c("人有悲欢离合", "月有阴晴圆缺，此事古难全"), c("但愿人长久", "千里共婵娟")),
                    ),
                    g(
                        "李清照",
                        poem("《如梦令·常记溪亭日暮》", "李清照", c("常记溪亭日暮", "沉醉不知归路"), c("兴尽晚回舟", "误入藕花深处"), c("争渡，争渡", "惊起一滩鸥鹭")),
                        poem("《如梦令·昨夜雨疏风骤》", "李清照", c("昨夜雨疏风骤", "浓睡不消残酒"), c("试问卷帘人", "却道海棠依旧"), c("知否，知否", "应是绿肥红瘦")),
                    ),
                    g(
                        "辛弃疾",
                        poem("《西江月·夜行黄沙道中》", "辛弃疾", c("明月别枝惊鹊", "清风半夜鸣蝉"), c("稻花香里说丰年", "听取蛙声一片"), c("七八个星天外", "两三点雨山前"), c("旧时茅店社林边", "路转溪桥忽见")),
                        poem("《丑奴儿·书博山道中壁》", "辛弃疾", c("少年不识愁滋味", "爱上层楼"), c("爱上层楼", "为赋新词强说愁"), c("而今识尽愁滋味", "欲说还休"), c("欲说还休", "却道天凉好个秋")),
                    ),
                    g(
                        "晏殊",
                        poem("《浣溪沙·一曲新词酒一杯》", "晏殊", c("一曲新词酒一杯", "去年天气旧亭台"), c("夕阳西下几时回", "无可奈何花落去"), c("似曾相识燕归来", "小园香径独徘徊")),
                    ),
                ),
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

    /** All nodes of every bundled palace, the packs included. */
    fun nodes(): List<Node> = (palaces + packs.drop(1)).flatMap { p -> nodes(p.id) }

    fun nodes(palaceId: String): List<Node> = build(palaceId, null, palaceId, trees[palaceId].orEmpty())

    fun isPack(palaceId: String): Boolean = packs.any { it.id == palaceId }

    private fun build(palaceId: String, parentId: String?, prefix: String, items: List<Item>): List<Node> {
        val out = ArrayList<Node>()
        items.forEachIndexed { i, item ->
            val id = "$prefix/$i"
            when (item) {
                is G -> {
                    out += Node(id, palaceId, parentId, i, NodeKind.GROUP, title = item.title, author = item.author)
                    out += build(palaceId, id, id, item.kids)
                }
                is C -> out += Node(id, palaceId, parentId, i, NodeKind.CARD, front = item.front, back = item.back, srs = Srs())
            }
        }
        return out
    }
}
