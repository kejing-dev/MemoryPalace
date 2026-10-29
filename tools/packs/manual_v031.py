"""Hand-written data for the v0.3.1 collections.

Only selection lists and stable textbook text live here. No measured number (year, height,
length, area, magnitude...) is typed in this file; those all come from the data sources.
Exact defined constants of the SI and textbook formulas are the only digits, see README.
"""

# ---------------------------------------------------------------- animals (selection only)
# Scientific names; all ranks / Chinese names are taken from Wikidata at build time.
ANIMALS = [
    ("哺乳动物", [
        "Panthera leo", "Panthera tigris", "Panthera pardus", "Acinonyx jubatus", "Felis catus",
        "Canis lupus", "Vulpes vulpes", "Ailuropoda melanoleuca", "Ursus arctos", "Ursus maritimus",
        "Ailurus fulgens", "Procyon lotor", "Lutra lutra", "Meles meles", "Odobenus rosmarus", "Phoca vitulina",
        "Pan troglodytes", "Gorilla gorilla", "Pongo pygmaeus", "Rhinopithecus roxellana", "Macaca mulatta",
        "Elephas maximus", "Loxodonta africana", "Equus quagga", "Equus asinus", "Ceratotherium simum",
        "Tapirus indicus", "Hippopotamus amphibius", "Giraffa camelopardalis", "Camelus dromedarius",
        "Camelus bactrianus", "Sus scrofa", "Ovis aries", "Capra hircus", "Elaphurus davidianus",
        "Rangifer tarandus", "Pantholops hodgsonii", "Balaenoptera musculus", "Physeter macrocephalus",
        "Orcinus orca", "Tursiops truncatus", "Lipotes vexillifer", "Mus musculus", "Rattus norvegicus",
        "Sciurus vulgaris", "Castor canadensis", "Cavia porcellus", "Oryctolagus cuniculus",
        "Erinaceus europaeus", "Talpa europaea", "Phascolarctos cinereus", "Vombatus ursinus",
        "Ornithorhynchus anatinus", "Manis pentadactyla", "Myrmecophaga tridactyla", "Desmodus rotundus",
    ]),
    ("鸟类", [
        "Aptenodytes forsteri", "Struthio camelus", "Aquila chrysaetos", "Haliaeetus leucocephalus",
        "Bubo bubo", "Pavo cristatus", "Gallus gallus", "Anas platyrhynchos", "Cygnus olor", "Grus japonensis",
        "Ciconia boyciana", "Phoenicopterus roseus", "Columba livia", "Passer montanus", "Hirundo rustica",
        "Pica pica", "Corvus corax", "Dendrocopos major", "Cuculus canorus", "Psittacus erithacus",
        "Nipponia nippon", "Falco peregrinus", "Ramphastos toco", "Dromaius novaehollandiae",
    ]),
    ("爬行动物", [
        "Crocodylus niloticus", "Alligator sinensis", "Chelonia mydas", "Ophiophagus hannah",
        "Varanus komodoensis", "Gekko gecko", "Iguana iguana", "Python bivittatus",
    ]),
    ("两栖动物", [
        "Andrias davidianus", "Pelophylax nigromaculatus", "Bufo gargarizans", "Ambystoma mexicanum",
    ]),
    ("鱼类", [
        "Carcharodon carcharias", "Rhincodon typus", "Mobula birostris", "Carassius auratus", "Cyprinus carpio",
        "Salmo salar", "Thunnus thynnus", "Acipenser sinensis", "Danio rerio", "Latimeria chalumnae",
        "Amphiprion ocellaris", "Hippocampus kuda",
    ]),
    ("无脊椎动物", [
        "Apis mellifera", "Bombyx mori", "Danaus plexippus", "Musca domestica", "Aedes aegypti",
        "Coccinella septempunctata", "Locusta migratoria", "Mantis religiosa", "Drosophila melanogaster",
        "Mesobuthus martensii", "Octopus vulgaris", "Sepia officinalis", "Nautilus pompilius",
        "Lumbricus terrestris", "Aurelia aurita", "Asterias rubens", "Eriocheir sinensis", "Homarus americanus",
    ]),
]

# Wikidata zh labels that are wrong for the taxon (checked against 中国动物志 / 中文教材名):
# Cyprinus carpio is labelled with a cultivar (德国镜鲤); Psittacidae with Ara's family name.
TAXON_ZH_FIX = {"Cyprinus carpio": "鲤", "Psittacidae": "鹦鹉科", "Actinopteri": "辐鳍鱼纲"}

# ---------------------------------------------------------------- human body (textbook text)
BODY = [
    ("运动系统", [
        ("骨", "支撑身体、保护内脏，红骨髓造血"),
        ("关节", "骨与骨之间的活动连接"),
        ("骨骼肌", "收缩牵动骨产生运动"),
        ("肌腱", "把肌肉连接到骨上"),
        ("韧带", "连接骨与骨，稳固关节"),
        ("颅骨", "保护脑"),
        ("脊柱", "支撑躯干、保护脊髓"),
        ("胸廓", "保护心、肺，参与呼吸运动"),
    ]),
    ("循环系统", [
        ("心脏", "收缩泵血，推动血液循环"),
        ("动脉", "把血液从心脏运往全身"),
        ("静脉", "把血液从全身运回心脏"),
        ("毛细血管", "血液与组织细胞进行物质交换"),
        ("红细胞", "运输氧气（血红蛋白）"),
        ("白细胞", "吞噬病菌，参与免疫防御"),
        ("血小板", "促进止血和凝血"),
        ("血浆", "运载血细胞、养料和代谢废物"),
    ]),
    ("呼吸系统", [
        ("鼻腔", "温暖、湿润、清洁吸入的空气"),
        ("咽", "空气与食物的共同通道"),
        ("喉", "呼吸通道，内有声带可发声"),
        ("气管和支气管", "输送气体，纤毛清除异物"),
        ("肺", "气体交换的主要器官"),
        ("肺泡", "与毛细血管之间交换氧和二氧化碳"),
        ("膈", "收缩舒张带动吸气与呼气"),
    ]),
    ("消化系统", [
        ("口腔", "咀嚼食物，唾液初步消化淀粉"),
        ("食道", "把食物送入胃"),
        ("胃", "暂存食物，初步消化蛋白质"),
        ("小肠", "消化和吸收营养的主要场所"),
        ("大肠", "吸收水分，形成粪便"),
        ("肝", "分泌胆汁，代谢与解毒"),
        ("胆囊", "储存并浓缩胆汁"),
        ("胰", "分泌胰液消化食物，胰岛分泌激素"),
    ]),
    ("泌尿系统", [
        ("肾", "过滤血液，形成尿液"),
        ("肾单位", "肾结构和功能的基本单位"),
        ("输尿管", "把尿液从肾输送到膀胱"),
        ("膀胱", "暂时储存尿液"),
        ("尿道", "把尿液排出体外"),
    ]),
    ("神经系统", [
        ("大脑", "感觉、运动、语言、思维的高级中枢"),
        ("小脑", "协调运动，维持身体平衡"),
        ("脑干", "调节呼吸、心跳等基本生命活动"),
        ("脊髓", "传导神经冲动，完成简单反射"),
        ("神经元", "神经系统结构和功能的基本单位"),
        ("周围神经", "联系中枢与全身各器官"),
    ]),
    ("内分泌系统", [
        ("垂体", "分泌生长激素等，调控其他内分泌腺"),
        ("甲状腺", "分泌甲状腺激素，促进新陈代谢"),
        ("肾上腺", "分泌肾上腺素等，应对紧急状态"),
        ("胰岛", "分泌胰岛素和胰高血糖素调节血糖"),
        ("性腺", "分泌性激素，促进第二性征发育"),
        ("下丘脑", "联系神经调节与激素调节"),
    ]),
    ("免疫系统", [
        ("皮肤和黏膜", "第一道防线，阻挡病原体"),
        ("吞噬细胞", "第二道防线，吞噬消灭病原体"),
        ("淋巴细胞", "第三道防线，产生特异性免疫"),
        ("抗体", "与特定抗原结合，使其失去致病性"),
        ("脾", "过滤血液，参与免疫"),
        ("淋巴结", "过滤淋巴，产生免疫应答"),
        ("胸腺", "T 淋巴细胞发育成熟的场所"),
    ]),
    ("生殖系统", [
        ("睾丸", "产生精子，分泌雄性激素"),
        ("卵巢", "产生卵细胞，分泌雌性激素"),
        ("输卵管", "输送卵细胞，受精的场所"),
        ("子宫", "胚胎和胎儿发育的场所"),
        ("胎盘", "胎儿与母体交换物质"),
    ]),
    ("感觉器官", [
        ("角膜", "透明，让光线进入眼球"),
        ("晶状体", "折射光线，调节焦距"),
        ("视网膜", "含感光细胞，形成物像"),
        ("鼓膜", "接受声波产生振动"),
        ("耳蜗", "含听觉感受器"),
        ("半规管和前庭", "感受头部位置变化，维持平衡"),
        ("味蕾", "感受酸甜苦咸鲜等味觉"),
        ("嗅黏膜", "感受气味"),
    ]),
    ("皮肤", [
        ("表皮", "保护身体，防止病菌侵入"),
        ("真皮", "含血管、神经，使皮肤有弹性"),
        ("汗腺", "分泌汗液，排泄和调节体温"),
        ("皮脂腺", "分泌皮脂，滋润皮肤和毛发"),
        ("黑色素细胞", "产生黑色素，防紫外线"),
    ]),
]

# ---------------------------------------------------------------- SI (textbook definitions, BIPM SI Brochure 9th ed.)
# Exact defining constants and prefix powers are part of the SI definition itself (README: allowed hand data).
SI_BASE = [
    ("长度", "米 m"), ("质量", "千克 kg"), ("时间", "秒 s"), ("电流", "安培 A"),
    ("热力学温度", "开尔文 K"), ("物质的量", "摩尔 mol"), ("发光强度", "坎德拉 cd"),
]
SI_DERIVED = [
    ("导出单位 · 力学", [
        ("力的单位", "牛顿 N = kg·m/s²"), ("压强的单位", "帕斯卡 Pa = N/m²"), ("能量、功的单位", "焦耳 J = N·m"),
        ("功率的单位", "瓦特 W = J/s"), ("频率的单位", "赫兹 Hz = 1/s"), ("平面角的单位", "弧度 rad = m/m"),
        ("立体角的单位", "球面度 sr = m²/m²"),
    ]),
    ("导出单位 · 电磁", [
        ("电荷的单位", "库仑 C = A·s"), ("电压的单位", "伏特 V = W/A"), ("电阻的单位", "欧姆 Ω = V/A"),
        ("电导的单位", "西门子 S = A/V"), ("电容的单位", "法拉 F = C/V"), ("磁通量的单位", "韦伯 Wb = V·s"),
        ("磁感应强度的单位", "特斯拉 T = Wb/m²"), ("电感的单位", "亨利 H = Wb/A"),
    ]),
    ("导出单位 · 其他", [
        ("摄氏温度的单位", "摄氏度 ℃（t/℃ = T/K − 273.15）"), ("光通量的单位", "流明 lm = cd·sr"),
        ("照度的单位", "勒克斯 lx = lm/m²"), ("放射性活度的单位", "贝克勒尔 Bq = 1/s"),
        ("吸收剂量的单位", "戈瑞 Gy = J/kg"), ("剂量当量的单位", "希沃特 Sv = J/kg"),
        ("催化活性的单位", "开特 kat = mol/s"),
    ]),
]
SI_PREFIX = [
    ("词头 · 大", [("k", "千 = 10³"), ("M", "兆 = 10⁶"), ("G", "吉 = 10⁹"), ("T", "太 = 10¹²"),
                 ("P", "拍 = 10¹⁵"), ("E", "艾 = 10¹⁸"), ("h", "百 = 10²"), ("da", "十 = 10¹")]),
    ("词头 · 小", [("m", "毫 = 10⁻³"), ("μ", "微 = 10⁻⁶"), ("n", "纳 = 10⁻⁹"), ("p", "皮 = 10⁻¹²"),
                 ("f", "飞 = 10⁻¹⁵"), ("a", "阿 = 10⁻¹⁸"), ("c", "厘 = 10⁻²"), ("d", "分 = 10⁻¹")]),
]
SI_CONSTANTS = [
    ("铯 133 超精细跃迁频率 ΔνCs", "9 192 631 770 Hz（定义秒）"),
    ("真空中光速 c", "299 792 458 m/s（定义米）"),
    ("普朗克常数 h", "6.626 070 15×10⁻³⁴ J·s（定义千克）"),
    ("元电荷 e", "1.602 176 634×10⁻¹⁹ C（定义安培）"),
    ("玻尔兹曼常数 k", "1.380 649×10⁻²³ J/K（定义开尔文）"),
    ("阿伏伽德罗常数 NA", "6.022 140 76×10²³ mol⁻¹（定义摩尔）"),
    ("发光效能 Kcd", "683 lm/W（定义坎德拉）"),
]
# Units converted with Wikidata P2370 (conversion to SI unit): (QID, Chinese name, symbol)
SI_CONVERSIONS = [
    ("Q218593", "英寸", "in"), ("Q3710", "英尺", "ft"), ("Q482798", "码", "yd"), ("Q253276", "英里", "mi"),
    ("Q93318", "海里", "nmi"), ("Q2963205", "市尺", "尺"), ("Q100995", "磅", "lb"), ("Q48013", "盎司", "oz"),
    ("Q261247", "克拉", "ct"), ("Q191118", "吨", "t"), ("Q81292", "英亩", "acre"), ("Q35852", "公顷", "ha"),
    ("Q836651", "市亩", "亩"), ("Q11582", "升", "L"), ("Q103510", "巴", "bar"), ("Q6859652", "毫米汞柱", "mmHg"),
    ("Q83327", "电子伏特", "eV"), ("Q1811", "天文单位", "au"), ("Q531", "光年", "ly"), ("Q12129", "秒差距", "pc"),
    ("Q28390", "角度 1°", "°"),
]

# ---------------------------------------------------------------- math formulas (textbook)
MATH = [
    ("代数", [
        ("完全平方公式", "(a ± b)² = a² ± 2ab + b²"), ("平方差公式", "a² − b² = (a + b)(a − b)"),
        ("立方和公式", "a³ + b³ = (a + b)(a² − ab + b²)"), ("立方差公式", "a³ − b³ = (a − b)(a² + ab + b²)"),
        ("一元二次方程求根公式", "x = [−b ± √(b² − 4ac)] / 2a"), ("判别式", "Δ = b² − 4ac"),
        ("韦达定理", "x₁ + x₂ = −b/a，x₁x₂ = c/a"), ("同底数幂相乘", "aᵐ · aⁿ = aᵐ⁺ⁿ"),
    ]),
    ("指数与对数", [
        ("幂的乘方", "(aᵐ)ⁿ = aᵐⁿ"), ("负指数", "a⁻ⁿ = 1/aⁿ（a ≠ 0）"), ("对数定义", "aˣ = N ⇔ x = logₐN"),
        ("积的对数", "logₐ(MN) = logₐM + logₐN"), ("商的对数", "logₐ(M/N) = logₐM − logₐN"),
        ("幂的对数", "logₐ(Mⁿ) = n·logₐM"), ("换底公式", "logₐb = lg b / lg a"),
    ]),
    ("平面图形", [
        ("三角形面积", "S = ½ × 底 × 高"), ("海伦公式", "S = √[p(p−a)(p−b)(p−c)]，p 为半周长"),
        ("梯形面积", "S = ½ (上底 + 下底) × 高"), ("平行四边形面积", "S = 底 × 高"),
        ("圆的周长", "C = 2πr"), ("圆的面积", "S = πr²"), ("扇形面积", "S = ½ l r = (n/360) πr²"),
        ("勾股定理", "a² + b² = c²"),
    ]),
    ("立体图形", [
        ("长方体体积", "V = 长 × 宽 × 高"), ("圆柱体积", "V = πr²h"), ("圆锥体积", "V = ⅓ πr²h"),
        ("球的体积", "V = ⁴⁄₃ πr³"), ("球的表面积", "S = 4πr²"), ("圆柱侧面积", "S = 2πrh"),
        ("棱锥体积", "V = ⅓ Sh"), ("欧拉多面体公式", "V − E + F = 2"),
    ]),
    ("三角函数", [
        ("平方关系", "sin²α + cos²α = 1"), ("商数关系", "tan α = sin α / cos α"),
        ("两角和的正弦", "sin(α+β) = sinα cosβ + cosα sinβ"), ("两角和的余弦", "cos(α+β) = cosα cosβ − sinα sinβ"),
        ("二倍角正弦", "sin 2α = 2 sinα cosα"), ("二倍角余弦", "cos 2α = cos²α − sin²α"),
        ("正弦定理", "a/sinA = b/sinB = c/sinC = 2R"), ("余弦定理", "c² = a² + b² − 2ab cosC"),
    ]),
    ("数列与组合", [
        ("等差数列通项", "aₙ = a₁ + (n−1)d"), ("等差数列求和", "Sₙ = n(a₁ + aₙ)/2"),
        ("等比数列通项", "aₙ = a₁qⁿ⁻¹"), ("等比数列求和", "Sₙ = a₁(1 − qⁿ)/(1 − q)，q ≠ 1"),
        ("排列数", "Aₙᵐ = n!/(n−m)!"), ("组合数", "Cₙᵐ = n!/[m!(n−m)!]"),
        ("二项式定理通项", "Tₖ₊₁ = Cₙᵏ aⁿ⁻ᵏ bᵏ"), ("1 + 2 + … + n", "n(n+1)/2"),
    ]),
    ("解析几何", [
        ("两点间距离", "d = √[(x₂−x₁)² + (y₂−y₁)²]"), ("直线斜率", "k = (y₂ − y₁)/(x₂ − x₁)"),
        ("点到直线距离", "d = |Ax₀ + By₀ + C| / √(A² + B²)"), ("圆的标准方程", "(x−a)² + (y−b)² = r²"),
        ("椭圆标准方程", "x²/a² + y²/b² = 1（a > b > 0）"), ("双曲线标准方程", "x²/a² − y²/b² = 1"),
        ("抛物线标准方程", "y² = 2px"), ("中点坐标", "((x₁+x₂)/2, (y₁+y₂)/2)"),
    ]),
    ("微积分与概率", [
        ("(xⁿ)′", "n xⁿ⁻¹"), ("(sin x)′", "cos x"), ("(eˣ)′", "eˣ"), ("(ln x)′", "1/x"),
        ("乘积求导", "(uv)′ = u′v + uv′"), ("牛顿–莱布尼茨公式", "∫ₐᵇ f(x)dx = F(b) − F(a)"),
        ("古典概型", "P(A) = A 包含的基本事件数 / 基本事件总数"), ("条件概率", "P(B|A) = P(AB) / P(A)"),
    ]),
]

# ---------------------------------------------------------------- 省级行政区别称 (common textbook 别称; text only)
PROVINCE_ALIAS = {
    "北京": "燕京", "天津": "津门、津沽", "河北": "燕赵", "山西": "三晋", "黑龙江": "龙江",
    "上海": "申城", "安徽": "江淮", "福建": "八闽", "江西": "赣鄱大地", "山东": "齐鲁", "河南": "中原、中州",
    "湖北": "荆楚", "湖南": "潇湘、三湘", "广东": "岭南、南粤", "广西": "八桂", "海南": "琼州",
    "重庆": "山城、巴渝", "四川": "天府之国、巴蜀", "云南": "彩云之南", "西藏": "雪域高原", "陕西": "三秦",
    "甘肃": "陇原", "宁夏": "塞上江南", "新疆": "西域", "香港": "香江", "澳门": "濠江、濠镜", "台湾": "宝岛",
}

# ---------------------------------------------------------------- 七十二候 (《逸周书·时训解》通行文本, public domain)
JIEQI72 = [
    ("立春", "东风解冻", "蛰虫始振", "鱼陟负冰"), ("雨水", "獭祭鱼", "候雁北", "草木萌动"),
    ("惊蛰", "桃始华", "仓庚鸣", "鹰化为鸠"), ("春分", "玄鸟至", "雷乃发声", "始电"),
    ("清明", "桐始华", "田鼠化为鴽", "虹始见"), ("谷雨", "萍始生", "鸣鸠拂其羽", "戴胜降于桑"),
    ("立夏", "蝼蝈鸣", "蚯蚓出", "王瓜生"), ("小满", "苦菜秀", "靡草死", "麦秋至"),
    ("芒种", "螳螂生", "鵙始鸣", "反舌无声"), ("夏至", "鹿角解", "蜩始鸣", "半夏生"),
    ("小暑", "温风至", "蟋蟀居壁", "鹰始挚"), ("大暑", "腐草为萤", "土润溽暑", "大雨时行"),
    ("立秋", "凉风至", "白露降", "寒蝉鸣"), ("处暑", "鹰乃祭鸟", "天地始肃", "禾乃登"),
    ("白露", "鸿雁来", "玄鸟归", "群鸟养羞"), ("秋分", "雷始收声", "蛰虫坯户", "水始涸"),
    ("寒露", "鸿雁来宾", "雀入大水为蛤", "菊有黄华"), ("霜降", "豺乃祭兽", "草木黄落", "蛰虫咸俯"),
    ("立冬", "水始冰", "地始冻", "雉入大水为蜃"), ("小雪", "虹藏不见", "天气上升地气下降", "闭塞而成冬"),
    ("大雪", "鹖鴠不鸣", "虎始交", "荔挺出"), ("冬至", "蚯蚓结", "麋角解", "水泉动"),
    ("小寒", "雁北乡", "鹊始巢", "雉始雊"), ("大寒", "鸡始乳", "征鸟厉疾", "水泽腹坚"),
]
