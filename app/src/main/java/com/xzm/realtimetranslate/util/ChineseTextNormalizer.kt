package com.xzm.realtimetranslate.util

/**
 * High-performance, zero-dependency Simplified Chinese normalizer.
 *
 * LLMs (DeepSeek, Qwen, Gemini, Claude) frequently mix in Traditional Chinese
 * characters or Japanese Kanji (e.g. 體, 點, 說, 會, 氣, 實, 國, 後, 裡, 著, 麼, 軟, 訊)
 * when translating anime, gaming, classical or Japanese source material.
 *
 * This normalizer maps high-frequency Traditional Chinese and Japanese Kanji
 * characters directly to their standard Simplified Chinese counterparts.
 */
object ChineseTextNormalizer {

    private val CHAR_MAP: CharArray = CharArray(65536) { it.toChar() }.apply {
        // Explicit source=target pairs eliminate any index offset errors.
        val mappingRules = listOf(
            // Basic Pronouns, Function Words & Frequent Verbs
            "這" to '这', "們" to '们', "來" to '来', "時" to '时', "為" to '为', "爲" to '为',
            "會" to '会', "說" to '说', "話" to '话', "著" to '着', "麼" to '么', "點" to '点',
            "個" to '个', "後" to '后', "裡" to '里', "裏" to '里', "從" to '从', "與" to '与',
            "對" to '对', "讓" to '让', "給" to '给', "開" to '开', "關" to '关', "發" to '发',
            "現" to '现', "動" to '动', "變" to '变', "過" to '过', "進" to '进', "出" to '出',
            "頭" to '头', "見" to '见', "問" to '问', "當" to '当', "應" to '应', "該" to '该',
            "樣" to '样', "種" to '种', "類" to '类', "別" to '别', "無" to '无', "法" to '法',
            "長" to '长', "間" to '间', "邊" to '边', "遠" to '远', "處" to '处', "總" to '总',
            "結" to '结', "果" to '果', "成" to '成', "帶" to '带', "條" to '条', "件" to '件', "並" to '并',

            // Computing, Tech, IT & Devices
            "軟" to '软', "體" to '体', "訊" to '讯', "網" to '网', "絡" to '络', "數" to '数',
            "據" to '据', "設" to '设', "置" to '置', "備" to '备', "電" to '电', "腦" to '脑',
            "機" to '机', "經" to '经', "圖" to '图', "畫" to '画', "檔" to '档', "視" to '视',
            "頻" to '频', "聲" to '声', "音" to '音', "語" to '语', "言" to '言', "譯" to '译',
            "碼" to '码', "鍵" to '键', "盤" to '盘', "顯" to '显', "示" to '示', "測" to '测',
            "試" to '试', "處" to '处', "理" to '理', "認" to '认', "證" to '证', "標" to '标',
            "題" to '题', "選" to '选', "項" to '项', "單" to '单', "雙" to '双', "線" to '线',
            "錄" to '录', "專" to '专', "業" to '业', "庫" to '库', "載" to '载', "輸" to '输',
            "連" to '连', "接" to '接', "斷" to '断', "狀" to '状', "態" to '态', "錯" to '错',
            "誤" to '误', "警" to '警', "告" to '告', "功" to '功', "敗" to '败', "閉" to '闭',
            "彈" to '弹', "浮" to '浮', "窗" to '窗', "資" to '资', "源" to '源', "內" to '内',
            "容" to '容', "量" to '量', "速" to '速', "度" to '度', "延" to '延', "遲" to '迟',
            "模" to '模', "型" to '型', "參" to '参', "格" to '格', "式" to '式', "字" to '字',
            "顏" to '颜', "色" to '色', "透" to '透', "明" to '明', "陰" to '阴', "影" to '影',
            "框" to '框', "圓" to '圆', "角" to '角', "距" to '距', "離" to '离', "寬" to '宽',
            "高" to '高', "區" to '区', "域" to '域', "範" to '范', "圍" to '围', "坐" to '坐',
            "標" to '标', "頂" to '顶', "底" to '底', "側" to '侧', "清" to '清', "單" to '单',
            "查" to '查', "看" to '看', "搜" to '搜', "索" to '索', "輸" to '输', "入" to '入',
            "送" to '送', "返" to '返', "回" to '回', "確" to '确', "消" to '消', "首" to '首',
            "頁" to '页', "重" to '重', "新" to '新', "更" to '更', "定" to '定', "配" to '配',

            // High Frequency Classical & Anime / Game / Japanese Kanji
            "気" to '气', "実" to '实', "氣" to '气', "實" to '实', "國" to '国', "學" to '学',
            "書" to '书', "愛" to '爱', "樂" to '乐', "歡" to '欢', "難" to '难', "熱" to '热',
            "車" to '车', "門" to '门', "問" to '问', "題" to '题', "錢" to '钱', "買" to '买',
            "賣" to '卖', "價" to '价', "員" to '员', "師" to '师', "隊" to '队', "軍" to '军',
            "敵" to '敌', "戰" to '战', "鬥" to '斗', "勝" to '胜', "負" to '负', "殺" to '杀',
            "傷" to '伤', "害" to '害', "死" to '死', "生" to '生', "命" to '命', "體" to '体',
            "力" to '力', "魔" to '魔', "法" to '法', "技" to '技', "能" to '能', "術" to '术',
            "寶" to '宝', "物" to '物', "金" to '金', "銀" to '银', "鐵" to '铁', "鋼" to '钢',
            "劍" to '剑', "刀" to '刀', "槍" to '枪', "弓" to '弓', "箭" to '箭', "甲" to '甲',
            "盾" to '盾', "裝" to '装', "階" to '阶', "級" to '级', "經" to '经', "驗" to '验',
            "值" to '值', "升" to '升', "降" to '降', "強" to '强', "弱" to '弱', "速" to '速',
            "防" to '防', "禦" to '御', "護" to '护', "擊" to '击', "攻" to '攻', "閃" to '闪',
            "避" to '避', "暴" to '暴', "擊" to '击', "復" to '复', "甦" to '苏', "治" to '治',
            "癒" to '愈', "藥" to '药', "草" to '草', "毒" to '毒', "詛" to '诅', "咒" to '咒',
            "封" to '封', "印" to '印', "解" to '解', "除" to '除', "屬" to '属', "性" to '性',
            "水" to '水', "火" to '火', "風" to '风', "雷" to '雷', "土" to '土', "光" to '光',
            "暗" to '暗', "聖" to '圣', "神" to '神', "龍" to '龙', "獸" to '兽', "妖" to '妖',
            "精" to '精', "靈" to '灵', "魂" to '魂', "鬼" to '鬼', "怪" to '怪', "異" to '异',
            "界" to '界', "國" to '国', "王" to '王', "城" to '城', "堡" to '堡', "村" to '村',
            "莊" to '庄', "鎮" to '镇', "市" to '市', "街" to '街', "道" to '道', "路" to '路',
            "橋" to '桥', "門" to '门', "塔" to '塔', "殿" to '殿', "堂" to '堂', "宮" to '宫',

            // Common General Characters
            "萬" to '万', "億" to '亿', "兩" to '两', "雙" to '双', "幾" to '几', "點" to '点',
            "半" to '半', "許" to '许', "多" to '多', "少" to '少', "極" to '极', "甚" to '甚',
            "最" to '最', "更" to '更', "較" to '较', "略" to '略', "微" to '微', "太" to '太',
            "過" to '过', "分" to '分', "別" to '别', "各" to '各', "同" to '同', "樣" to '样',
            "全" to '全', "部" to '部', "整" to '整', "齊" to '齐', "總" to '总', "共" to '共',
            "僅" to '仅', "單" to '单', "獨" to '独', "隻" to '只', "唯" to '唯', "復" to '复',
            "雜" to '杂', "簡" to '简', "難" to '难', "易" to '易', "真" to '真', "假" to '假',
            "虛" to '虚', "實" to '实', "空" to '空', "滿" to '满', "新" to '新', "舊" to '旧',
            "好" to '好', "壞" to '坏', "優" to '优', "劣" to '劣', "美" to '美', "醜" to '丑',
            "愛" to '爱', "恨" to '恨', "喜" to '喜', "怒" to '怒', "哀" to '哀', "樂" to '乐',
            "悲" to '悲', "傷" to '伤', "痛" to '痛', "苦" to '苦', "驚" to '惊', "恐" to '恐',
            "懼" to '惧', "怕" to '怕', "憂" to '忧', "愁" to '愁', "悶" to '闷', "煩" to '烦',
            "惱" to '恼', "急" to '急', "慌" to '慌', "忙" to '忙', "閑" to '闲', "靜" to '静',
            "動" to '动', "亂" to '乱', "吵" to '吵', "鬧" to '闹', "熱" to '热', "冷" to '冷',
            "涼" to '凉', "溫" to '温', "暖" to '暖', "寒" to '寒', "凍" to '冻', "冰" to '冰',
            "雪" to '雪', "霜" to '霜', "露" to '露', "霧" to '雾', "雲" to '云', "雨" to '雨',
            "電" to '电', "雷" to '雷', "風" to '风', "晴" to '晴', "陰" to '阴', "天" to '天',
            "地" to '地', "日" to '日', "月" to '月', "星" to '星', "光" to '光', "明" to '明',
            "暗" to '暗', "黑" to '黑', "白" to '白', "紅" to '红', "綠" to '绿', "藍" to '蓝',
            "黃" to '黄', "紫" to '紫', "灰" to '灰', "褐" to '褐', "金" to '金', "銀" to '银',
            "銅" to '铜', "鐵" to '铁', "錫" to '锡', "鉛" to '铅', "石" to '石', "木" to '木',
            "水" to '水', "火" to '火', "土" to '土', "沙" to '沙', "泥" to '泥', "塵" to '尘',
            "風" to '风', "氣" to '气', "空" to '空', "天" to '天', "海" to '海', "洋" to '洋',
            "江" to '江', "河" to '河', "湖" to '湖', "溪" to '溪', "泉" to '泉', "潭" to '潭',
            "池" to '池', "井" to '井', "坑" to '坑', "洞" to '洞', "山" to '山', "嶺" to '岭',
            "峰" to '峰', "崖" to '崖', "谷" to '谷', "原" to '原', "野" to '野', "田" to '田',
            "園" to '园', "林" to '林', "森" to '森', "樹" to '树', "木" to '木', "枝" to '枝',
            "葉" to '叶', "花" to '花', "草" to '草', "果" to '果', "實" to '实', "根" to '根',
            "種" to '种', "子" to '子', "苗" to '苗', "芽" to '芽', "藤" to '藤', "藻" to '藻',
            "鳥" to '鸟', "獸" to '兽', "蟲" to '虫', "魚" to '鱼', "蝦" to '虾', "蟹" to '蟹',
            "貝" to '贝', "螺" to '螺', "蠶" to '蚕', "繭" to '茧', "絲" to '丝', "線" to '线',
            "網" to '网', "羅" to '罗', "綱" to '纲', "領" to '领', "頭" to '头', "首" to '首',
            "項" to '项', "頸" to '颈', "肩" to '肩', "背" to '背', "腰" to '腰', "腹" to '腹',
            "胸" to '胸', "臂" to '臂', "手" to '手', "指" to '指', "掌" to '掌', "拳" to '拳',
            "腿" to '腿', "膝" to '膝', "腳" to '脚', "足" to '足', "踝" to '踝', "趾" to '趾',
            "身" to '身', "體" to '体', "膚" to '肤', "皮" to '皮', "肉" to '肉', "骨" to '骨',
            "髓" to '髓', "血" to '血', "脈" to '脉', "心" to '心', "肝" to '肝', "肺" to '肺',
            "胃" to '胃', "腸" to '肠', "膽" to '胆', "腎" to '肾', "腦" to '脑', "筋" to '筋',
            "眼" to '眼', "目" to '目', "耳" to '耳', "鼻" to '鼻', "口" to '口', "嘴" to '嘴',
            "齒" to '齿', "牙" to '牙', "舌" to '舌', "唇" to '唇', "喉" to '喉', "嚨" to '咙',
            "髮" to '发', "毛" to '毛', "鬚" to '须', "眉" to '眉', "睫" to '睫', "容" to '容',
            "貌" to '貌', "顏" to '颜', "色" to '色', "態" to '态', "聲" to '声', "音" to '音',
            "氣" to '气', "味" to '味', "香" to '香', "臭" to '臭', "甜" to '甜', "苦" to '苦',
            "酸" to '酸', "辣" to '辣', "鹹" to '咸', "淡" to '淡', "濃" to '浓', "厚" to '厚',
            "薄" to '薄', "重" to '重', "輕" to '轻', "軟" to '软', "硬" to '硬', "堅" to '坚',
            "柔" to '柔', "剛" to '刚', "韌" to '韧', "脆" to '脆', "鈍" to '钝', "銳" to '锐',
            "利" to '利', "害" to '害', "益" to '益', "損" to '损', "得" to '得', "失" to '失',
            "成" to '成', "敗" to '败', "興" to '兴', "衰" to '衰', "榮" to '荣', "辱" to '辱',
            "貴" to '贵', "賤" to '贱', "富" to '富', "貧" to '贫', "窮" to '穷', "達" to '达',
            "顯" to '显', "隱" to '隐', "彰" to '彰', "微" to '微', "著" to '著', "藏" to '藏',
            "露" to '露', "含" to '含', "蓄" to '蓄', "發" to '发', "斂" to '敛', "收" to '收',
            "放" to '放', "縱" to '纵', "橫" to '横', "直" to '直', "曲" to '曲', "彎" to '弯',
            "平" to '平', "險" to '险', "陡" to '陡', "斜" to '斜', "側" to '侧', "正" to '正',
            "偏" to '偏', "歪" to '歪', "準" to '准', "差" to '差', "錯" to '错', "誤" to '误',
            "真" to '真', "確" to '确', "審" to '审', "查" to '查', "驗" to '验', "證" to '证',
            "明" to '明', "白" to '白', "清" to '清', "楚" to '楚', "模" to '模', "糊" to '糊',
            "隱" to '隐', "約" to '约', "暗" to '暗', "淡" to '淡', "明" to '明', "亮" to '亮',
            "燦" to '灿', "爛" to '烂', "輝" to '辉', "煌" to '煌', "光" to '光', "彩" to '彩',
            "榮" to '荣', "耀" to '耀', "華" to '华', "麗" to '丽', "樸" to '朴', "素" to '素',
            "簡" to '简', "潔" to '洁', "整" to '整', "齊" to '齐', "雜" to '杂', "亂" to '乱',
            "順" to '顺', "逆" to '逆', "從" to '从', "違" to '违', "合" to '合', "分" to '分',
            "聚" to '聚', "散" to '散', "集" to '集', "合" to '合', "離" to '离', "別" to '别',
            "聯" to '联', "絡" to '络', "牽" to '牵', "連" to '连', "牽" to '牵', "扯" to '扯',
            "關" to '关', "係" to '系', "牽" to '牵', "制" to '制', "約" to '约', "束" to '束',
            "規" to '规', "則" to '则', "律" to '律', "法" to '法', "度" to '度', "量" to '量',
            "衡" to '衡', "權" to '权', "威" to '威', "力" to '力', "勢" to '势', "能" to '能',
            "效" to '效', "果" to '果', "驗" to '验', "證" to '证', "功" to '功', "勞" to '劳',
            "績" to '绩', "成" to '成', "業" to '业', "事" to '事', "跡" to '迹', "史" to '史',
            "志" to '志', "記" to '记', "傳" to '传', "話" to '话', "語" to '语', "言" to '言',
            "詞" to '词', "句" to '句', "篇" to '篇', "章" to '章', "節" to '节', "段" to '段',
            "行" to '行', "列" to '列', "排" to '排', "次" to '次', "第" to '第', "號" to '号',
            "名" to '名', "稱" to '称', "號" to '号', "題" to '题', "字" to '字', "符" to '符',
            "標" to '标', "點" to '点', "符" to '符', "圖" to '图', "表" to '表', "冊" to '册',
            "卷" to '卷', "宗" to '宗', "檔" to '档', "案" to '案', "件" to '件', "資" to '资',
            "料" to '料', "訊" to '讯', "息" to '息', "報" to '报', "告" to '告', "文" to '文',
            "書" to '书', "信" to '信', "函" to '函', "啟" to '启', "發" to '发', "送" to '送',
            "達" to '达', "收" to '收', "受" to '受', "領" to '领', "納" to '纳', "存" to '存',
            "儲" to '储', "備" to '备', "查" to '查', "閱" to '阅', "覽" to '览', "觀" to '观',
            "讀" to '读', "寫" to '写', "抄" to '抄', "錄" to '录', "謄" to '誊', "印" to '印',
            "刷" to '刷', "刻" to '刻', "刊" to '刊', "發" to '发', "布" to '布', "宣" to '宣',
            "播" to '播', "揚" to '扬', "頌" to '颂', "讚" to '赞', "歌" to '歌', "詠" to '咏',
            "歎" to '叹', "嘆" to '叹', "慨" to '慨', "怨" to '怨', "尤" to '尤', "責" to '责',
            "怪" to '怪', "咎" to '咎', "罰" to '罚', "懲" to '惩', "戒" to '戒', "飭" to '饬',
            "賞" to '赏', "賜" to '赐', "賚" to '赉', "予" to '予', "給" to '给', "頒" to '颁',
            "贈" to '赠', "奉" to '奉', "獻" to '献', "供" to '供', "進" to '进', "貢" to '贡',
            "納" to '纳', "賦" to '赋', "稅" to '税', "租" to '租', "借" to '借', "貸" to '贷',
            "還" to '还', "償" to '偿', "賠" to '赔', "帳" to '帐', "債" to '债', "產" to '产',
            "財" to '财', "貨" to '货', "物" to '物', "資" to '资', "金" to '金', "銀" to '银',
            "幣" to '币', "券" to '券', "價" to '价', "值" to '值', "量" to '量', "度" to '度',
            "衡" to '衡', "規" to '规', "範" to '范', "模" to '模', "式" to '式', "程" to '程',
            "序" to '序', "次" to '次', "等" to '等', "級" to '级', "品" to '品', "格" to '格',
            "質" to '质', "素" to '素', "德" to '德', "行" to '行', "操" to '操', "守" to '守',
            "節" to '节', "義" to '义', "忠" to '忠', "誠" to '诚', "信" to '信', "實" to '实',
            "篤" to '笃', "敬" to '敬', "恭" to '恭', "謙" to '谦', "遜" to '逊', "和" to '和',
            "順" to '顺', "柔" to '柔', "惠" to '惠', "慈" to '慈', "愛" to '爱', "仁" to '仁',
            "義" to '义', "禮" to '礼', "智" to '智', "信" to '信', "勇" to '勇', "毅" to '毅',
            "果" to '果', "敢" to '敢', "斷" to '断', "決" to '决', "志" to '志', "願" to '愿',
            "向" to '向', "往" to '往', "趨" to '趋', "勢" to '势', "機" to '机', "緣" to '缘',
            "運" to '运', "數" to '数', "命" to '命', "途" to '途', "道" to '道', "理" to '理',
            "由" to '由', "因" to '因', "果" to '果', "源" to '源', "本" to '本', "末" to '末',
            "始" to '始', "終" to '终', "先" to '先', "後" to '后', "早" to '早', "遲" to '迟',
            "緩" to '缓', "急" to '急', "緊" to '紧', "迫" to '迫', "催" to '催', "促" to '促'
        )
        for ((trad, simp) in mappingRules) {
            for (ch in trad) {
                this[ch.code] = simp
            }
        }
    }

    /**
     * Converts any Traditional Chinese or Japanese Kanji characters in [text]
     * to standard Simplified Chinese characters. O(N) array lookup.
     */
    fun toSimplified(text: String): String {
        if (text.isEmpty()) return text
        val sb = StringBuilder(text.length)
        for (i in 0 until text.length) {
            val c = text[i]
            val code = c.code
            if (code in 0..65535) {
                sb.append(CHAR_MAP[code])
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    /**
     * True if the specified language code or display label represents Simplified Chinese.
     */
    fun isSimplifiedTarget(targetLangOrLabel: String): Boolean {
        val s = targetLangOrLabel.trim().lowercase()
        return s == "zh-hans" || s == "zh-cn" || s == "zh" || s == "zh_cn" ||
            s == "chinese" || s.contains("simplified") || s.contains("简体")
    }
}
