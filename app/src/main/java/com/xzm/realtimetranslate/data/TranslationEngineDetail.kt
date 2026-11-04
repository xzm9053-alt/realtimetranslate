package com.xzm.realtimetranslate.data

/**
 * 引擎大分类：开源模型生态 vs API付费商业服务
 */
enum class EngineCategory(val label: String, val icon: String) {
    OPEN_SOURCE("开源模型", "🤖"),
    API_PAID("API 付费", "☁️"),
}

/**
 * 翻译引擎详细介绍与生态元数据
 */
data class TranslationEngineDetail(
    val type: TranslationEngineType,
    val category: EngineCategory,
    val iconEmoji: String,
    val brandColorLong: Long,
    val categoryBadge: String,
    val developer: String,
    val highlight: String,
    val costDesc: String,
    val suitableScenarios: String,
    val recommendedModels: List<String>,
    val detailText: String,
)

/**
 * 语音识别引擎详细介绍与元数据
 */
data class AsrEngineDetail(
    val type: AsrEngineType,
    val category: EngineCategory,
    val iconEmoji: String,
    val brandColorLong: Long,
    val categoryBadge: String,
    val highlight: String,
    val costDesc: String,
    val suitableScenarios: String,
    val detailText: String,
)

/**
 * 开源翻译模型预设（直接列在开源模型一栏中的模型项）
 */
data class OpenSourceTranslationPreset(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val brandColorLong: Long,
    val categoryBadge: String,
    val developer: String,
    val highlight: String,
    val costDesc: String,
    val suitableScenarios: String,
    val modelScopeUrl: String,
    val hfMirrorUrl: String,
    val engineType: TranslationEngineType,
    val defaultModelName: String = "",
    val detailText: String,
)

object TranslationEngineCatalog {

    /**
     * 开源翻译模型矩阵（用户点开「开源模型」一栏直接完整列出）
     */
    val OPEN_SOURCE_TRANSLATION_PRESETS: List<OpenSourceTranslationPreset> = listOf(
        OpenSourceTranslationPreset(
            id = "sakura-14b",
            name = "🌸 SakuraLLM (日漫汉化霸主)",
            iconEmoji = "🌸",
            brandColorLong = 0xFFEC4899, // 粉色
            categoryBadge = "💻 电脑私有化 (局域网) · 日漫生肉",
            developer = "Sakura 社区 (基于 Qwen2 深度微调)",
            highlight = "ACG生肉轻小说/动漫/游戏首选 · 精准还原人称代词",
            costDesc = "开源免费 · 需电脑运行 · 手机局域网零花费",
            suitableScenarios = "日漫追番、生肉 Galgame、轻小说汉化、日语字幕精翻",
            modelScopeUrl = "https://www.modelscope.cn/models/sakura-editor/Sakura-14B-Qwen2beta-v0.9.2-GGUF",
            hfMirrorUrl = "https://hf-mirror.com/sakura-editor/Sakura-14B-Qwen2beta-v0.9.2-GGUF",
            engineType = TranslationEngineType.OPENAI_COMPAT,
            defaultModelName = "sakura:14b",
            detailText = "【💡 运行方式：电脑运行 · 手机局域网直连】日译中 ACGN（动画、轻小说、视觉小说、日语字幕）领域的绝对霸主。数十亿参数大模型手机无法承载，需在电脑（推荐使用 Ollama / LM Studio 等）本地运行，手机填入电脑 Wi-Fi IP 即可免费调用电脑算力！",
        ),
        OpenSourceTranslationPreset(
            id = "seed-x-7b",
            name = "🔥 Seed-X-7B (字节跳动精翻)",
            iconEmoji = "🔥",
            brandColorLong = 0xFFF59E0B, // 琥珀金
            categoryBadge = "💻 电脑私有化 (局域网) · 地道成语",
            developer = "字节跳动 (ByteDance)",
            highlight = "成语诗歌网络热词神级对齐 · 7B超越百亿大模型",
            costDesc = "开源免费 · 需电脑运行 · 手机局域网零花费",
            suitableScenarios = "视频字幕打轴、中英地道口语、网络热词与短视频文案",
            modelScopeUrl = "https://www.modelscope.cn/models/ByteDance/Seed-X-7B",
            hfMirrorUrl = "https://hf-mirror.com/ByteDance/Seed-X-7B",
            engineType = TranslationEngineType.OPENAI_COMPAT,
            defaultModelName = "seed-x:7b",
            detailText = "【💡 运行方式：电脑运行 · 手机局域网直连】字节跳动专为高水准翻译训练的开源专精模型，成语诗歌与热词深度优化。数十亿大模型需在电脑运行（如 Ollama），手机通过 Wi-Fi 局域网直连使用。",
        ),
        OpenSourceTranslationPreset(
            id = "qwen2.5-7b",
            name = "⭐ Qwen2.5-7B (通义千问开源旗舰)",
            iconEmoji = "⭐",
            brandColorLong = 0xFF3B82F6, // 科技蓝
            categoryBadge = "💻 电脑私有化 (局域网) · 通用旗舰",
            developer = "阿里通义千问",
            highlight = "综合翻译最强开源基座 · 8G显存推荐",
            costDesc = "开源免费 · 需电脑运行 · 手机局域网零花费",
            suitableScenarios = "长篇上下文理解、技术文档、论文润色、亚洲多语言对齐",
            modelScopeUrl = "https://www.modelscope.cn/models/qwen/Qwen2.5-7B-Instruct/summary",
            hfMirrorUrl = "https://hf-mirror.com/Qwen/Qwen2.5-7B-Instruct-GGUF",
            engineType = TranslationEngineType.OPENAI_COMPAT,
            defaultModelName = "qwen2.5:7b",
            detailText = "【💡 运行方式：电脑运行 · 手机局域网直连】中英互译与亚洲语言综合最强开源基座。数十亿参数需电脑本地显卡或 CPU 运行（如 Ollama），手机连入同一局域网免 Key 畅享旗舰翻译。",
        ),
        OpenSourceTranslationPreset(
            id = "nllb-200",
            name = "🌐 Meta NLLB-200 (200语种零幻觉)",
            iconEmoji = "🌐",
            brandColorLong = 0xFF10B981, // 绿色
            categoryBadge = "💻 电脑私有化 (局域网) · 传统NMT",
            developer = "Meta (Facebook AI Research)",
            highlight = "200种全球语言全能互译 · 纯CPU毫秒推理",
            costDesc = "开源免费 · 电脑CPU极速运行 · 零幻觉",
            suitableScenarios = "全球生僻语言、排版标签严格保留、低延迟批量转写",
            modelScopeUrl = "https://www.modelscope.cn/models/AI-ModelScope/nllb-200-distilled-600M",
            hfMirrorUrl = "https://hf-mirror.com/facebook/nllb-200-distilled-600M",
            engineType = TranslationEngineType.OPENAI_COMPAT,
            defaultModelName = "nllb-200",
            detailText = "【💡 运行方式：电脑运行 · 手机局域网直连】Meta 200 种多语言神经机翻大一统模型。纯 CPU 毫秒推理，输出确定性极高，零大模型幻觉，需在电脑部署服务，手机局域网连接。",
        ),
        OpenSourceTranslationPreset(
            id = "microsoft",
            name = "🌐 微软翻译 (免密通道)",
            iconEmoji = "🌐",
            brandColorLong = 0xFF06B6D4, // 青蓝
            categoryBadge = "免密直连 · 0门槛",
            developer = "微软 (Microsoft Edge API)",
            highlight = "免密钥 · 零门槛即开即用 · 毫秒响应",
            costDesc = "完全免费 · 免 Key · 国内直连",
            suitableScenarios = "开箱即用、追求超低网络延迟、对长篇润色无特殊要求的快翻",
            modelScopeUrl = "",
            hfMirrorUrl = "",
            engineType = TranslationEngineType.MICROSOFT,
            detailText = "经典工业级神经机器翻译（NMT）通道，免注册 API Key，国内极速直连。响应时间通常仅需 200~400ms，绝不产生大模型幻觉，非常适合刚装机想要立即体验翻译的用户。",
        ),
        OpenSourceTranslationPreset(
            id = "google_free",
            name = "🌈 谷歌翻译 (免密通道)",
            iconEmoji = "🌈",
            brandColorLong = 0xFFF59E0B, // 琥珀金
            categoryBadge = "免密通道 · 多语种",
            developer = "Google 网页翻译接口",
            highlight = "免密钥 · 广谱语种覆盖 · 确定性高",
            costDesc = "完全免费 · 免 Key",
            suitableScenarios = "全球生僻小语种互译、词条短句快速对照",
            modelScopeUrl = "",
            hfMirrorUrl = "",
            engineType = TranslationEngineType.GOOGLE_FREE,
            detailText = "Google 传统神经机器翻译免密钥通道，覆盖全球上百种主流与小语种。采用确定性 NMT 架构，输出稳定，不消耗 Token 额度，支持作为备用翻译引擎随时切换。",
        ),
        OpenSourceTranslationPreset(
            id = "none",
            name = "🔇 仅识别 (不翻译 · 纯离线)",
            iconEmoji = "🔇",
            brandColorLong = 0xFF64748B, // 板岩灰
            categoryBadge = "纯端侧 · 离线听写",
            developer = "本地 SenseVoice / ASR 直通",
            highlight = "只识别不翻译 · 零网络 · 零延迟 · 零费用",
            costDesc = "完全离线 · 免 API Key · 0 流量消耗",
            suitableScenarios = "纯外语听写学习、会议原声记录、原生字幕实时提取、断网环境",
            modelScopeUrl = "",
            hfMirrorUrl = "",
            engineType = TranslationEngineType.NONE,
            detailText = "原汁原味的原声听写模式。语音识别结果不经过大模型翻译直接原样展现为字幕，不耗费任何网络流量和 Token 费用，非常适合在无网络环境下记录原声或进行外语听力训练。",
        ),
    )

    val DETAILS: Map<TranslationEngineType, TranslationEngineDetail> = mapOf(
        TranslationEngineType.DEEPSEEK to TranslationEngineDetail(
            type = TranslationEngineType.DEEPSEEK,
            category = EngineCategory.API_PAID,
            iconEmoji = "🐳",
            brandColorLong = 0xFF3B82F6, // 科技蓝
            categoryBadge = "商业 API · 按量计费",
            developer = "深度求索 (DeepSeek)",
            highlight = "推理与句式重组极佳 · 性价比天花板",
            costDesc = "付费按量计费（千字几厘钱，超低使用成本）",
            suitableScenarios = "技术文档、学术论文、长篇剧本文案、中英长难句重组与指代消歧",
            recommendedModels = listOf("deepseek-v4-flash (推荐)", "deepseek-v4-pro"),
            detailText = "DeepSeek 在技术文档、代码注释、学术论文及中英长难句重组上表现极佳。凭借极强的逻辑推理能力，它在处理指代消歧与文学化润色时比传统机翻自然得多；App 支持一键开启/关闭 AI 深度思考模式。",
        ),
        TranslationEngineType.ZHIPU to TranslationEngineDetail(
            type = TranslationEngineType.ZHIPU,
            category = EngineCategory.API_PAID,
            iconEmoji = "🔮",
            brandColorLong = 0xFF8B5CF6, // 灵动紫
            categoryBadge = "商业 API · 免费额度",
            developer = "智谱 AI (清华技术背景)",
            highlight = "永久免费额度 · 国内极速直连",
            costDesc = "GLM-4-Flash 永久免费不限量（国内直连免梯）",
            suitableScenarios = "日常视频字幕追番、口语交流、长时同传听写（零费用首选）",
            recommendedModels = listOf("glm-4-flash (免费推荐)", "glm-4-air", "glm-5"),
            detailText = "国内顶尖 AI 研发机构推出的代表作。提供 GLM-4-Flash 永久免费不限量的专属优惠，免翻墙国内毫秒级响应，是日常视频字幕同传与长时间挂机翻译的最佳免费大模型方案。",
        ),
        TranslationEngineType.GEMINI to TranslationEngineDetail(
            type = TranslationEngineType.GEMINI,
            category = EngineCategory.API_PAID,
            iconEmoji = "✨",
            brandColorLong = 0xFF06B6D4, // 青蓝
            categoryBadge = "全球顶尖大模型",
            developer = "Google (谷歌)",
            highlight = "多语种深度对齐 · 盲测综合顶级",
            costDesc = "Google AI Studio 免费配额（需梯/支持海外网络）",
            suitableScenarios = "欧洲多语种、小语种互译、口语双关语与俚语地道转换",
            recommendedModels = listOf("gemini-3.8-flash (主力)", "gemini-3.7-flash", "gemini-3.5-flash-lite"),
            detailText = "Google 旗舰多模态大模型家族。继承了 Google 深厚的多语种语料与知识库积累，在盲测中多语种地道表达甚至超越传统机翻，尤其擅长小语种及跨文化俚语转换；通过官方兼容层接入。",
        ),
        TranslationEngineType.OPENAI_COMPAT to TranslationEngineDetail(
            type = TranslationEngineType.OPENAI_COMPAT,
            category = EngineCategory.API_PAID,
            iconEmoji = "⚡",
            brandColorLong = 0xFFEC4899, // 洋红粉
            categoryBadge = "自定义 API · 兼容协议",
            developer = "通用 OpenAI 兼容协议",
            highlight = "支持自定义 API 地址、Key 与任意中转站 / 商业大模型",
            costDesc = "视所接服务商规则而定（需配置 API Key）",
            suitableScenarios = "使用第三方中转站、OpenRouter、SiliconFlow、Groq 或本地自建端点",
            recommendedModels = listOf(
                "gpt-4o-mini",
                "deepseek-chat",
                "Qwen/Qwen2.5-7B-Instruct",
                "claude-3-5-haiku",
            ),
            detailText = "全能自定义 API 插槽。允许你配置任意提供标准 OpenAI /v1/chat/completions 接口的商业中转站、海外聚合平台（如 OpenRouter、Groq）或云端服务商，自由输入自定义模型名称与 API Key 进行翻译。",
        ),
        TranslationEngineType.MICROSOFT to TranslationEngineDetail(
            type = TranslationEngineType.MICROSOFT,
            category = EngineCategory.OPEN_SOURCE,
            iconEmoji = "🌐",
            brandColorLong = 0xFF10B981, // 清新绿
            categoryBadge = "免密直连 · 0门槛",
            developer = "微软 (Microsoft Edge API)",
            highlight = "免密钥 · 零门槛即开即用 · 毫秒响应",
            costDesc = "完全免费 · 免 Key · 国内直连",
            suitableScenarios = "开箱即用、追求超低网络延迟、对长篇润色无特殊要求的快翻",
            recommendedModels = emptyList(),
            detailText = "经典工业级神经机器翻译（NMT）通道，免注册 API Key，国内极速直连。响应时间通常仅需 200~400ms，绝不产生大模型幻觉，非常适合刚装机想要立即体验翻译的用户。",
        ),
        TranslationEngineType.GOOGLE_FREE to TranslationEngineDetail(
            type = TranslationEngineType.GOOGLE_FREE,
            category = EngineCategory.OPEN_SOURCE,
            iconEmoji = "🌈",
            brandColorLong = 0xFFF59E0B, // 琥珀金
            categoryBadge = "免密通道 · 多语种",
            developer = "Google 网页翻译接口",
            highlight = "免密钥 · 广谱语种覆盖 · 确定性高",
            costDesc = "完全免费 · 免 Key",
            suitableScenarios = "全球生僻小语种互译、词条短句快速对照",
            recommendedModels = emptyList(),
            detailText = "Google 传统神经机器翻译免密钥通道，覆盖全球上百种主流与小语种。采用确定性 NMT 架构，输出稳定，不消耗 Token 额度，支持作为备用翻译引擎随时切换。",
        ),
        TranslationEngineType.NONE to TranslationEngineDetail(
            type = TranslationEngineType.NONE,
            category = EngineCategory.OPEN_SOURCE,
            iconEmoji = "🔇",
            brandColorLong = 0xFF64748B, // 板岩灰
            categoryBadge = "纯端侧 · 离线听写",
            developer = "本地 SenseVoice / ASR 直通",
            highlight = "只识别不翻译 · 零网络 · 零延迟 · 零费用",
            costDesc = "完全离线 · 免 API Key · 0 流量消耗",
            suitableScenarios = "纯外语听写学习、会议原声记录、原生字幕实时提取、断网环境",
            recommendedModels = emptyList(),
            detailText = "原汁原味的原声听写模式。语音识别结果不经过大模型翻译直接原样展现为字幕，不耗费任何网络流量和 Token 费用，非常适合在无网络环境下记录原声或进行外语听力训练。",
        ),
    )

    val ASR_DETAILS: Map<AsrEngineType, AsrEngineDetail> = mapOf(
        AsrEngineType.LOCAL_SENSEVOICE to AsrEngineDetail(
            type = AsrEngineType.LOCAL_SENSEVOICE,
            category = EngineCategory.OPEN_SOURCE,
            iconEmoji = "💻",
            brandColorLong = 0xFF10B981, // 绿色
            categoryBadge = "开源生态 · 纯离线",
            highlight = "基于 sherpa-onnx · 毫秒打字机 · 支持8大顶尖开源模型",
            costDesc = "完全免费 · 离线不出机 · 0 流量",
            suitableScenarios = "日常视频同传、隐私保护、无网环境、低延迟口语打字机",
            detailText = "纯 C++ 编译的端侧推理引擎，默认内置阿里 SenseVoice INT8 模型（239MB），同时支持扩展加载 Paraformer、Whisper Turbo、Moonshine、Qwen3-ASR 等 8 款业界顶尖开源模型，具备模型未就绪自动平滑降级保护。",
        ),
        AsrEngineType.GENERIC_OPENAI to AsrEngineDetail(
            type = AsrEngineType.GENERIC_OPENAI,
            category = EngineCategory.API_PAID,
            iconEmoji = "🌐",
            brandColorLong = 0xFF8B5CF6, // 紫色
            categoryBadge = "自定义 API · 音频转录",
            highlight = "POST /v1/audio/transcriptions 标准云端转写接口",
            costDesc = "视服务商计费规则而定（需配置 API Key）",
            suitableScenarios = "接入 OpenAI 官方 Whisper API、Groq 极速转录或第三方转录服务",
            detailText = "标准 OpenAI 音频转录接口协议，每次切句时将音频打包为标准 WAV 格式并调用云端 API 发起转录，支持灵活配置任意商业或第三方转录服务商。",
        ),
        AsrEngineType.ALIYUN_FUN_ASR to AsrEngineDetail(
            type = AsrEngineType.ALIYUN_FUN_ASR,
            category = EngineCategory.API_PAID,
            iconEmoji = "⚡",
            brandColorLong = 0xFF3B82F6, // 蓝色
            categoryBadge = "商业云端 · 实时流式",
            highlight = "WebSocket 实时流式传输 · 阿里百炼官方出品",
            costDesc = "阿里云百炼商业按量计费（新用户赠送免费额度）",
            suitableScenarios = "正式会议长句同传、专业演讲、高抗噪要求环境",
            detailText = "阿里云百炼工业级流式语音识别服务，全双工 WebSocket 长连接握手，毫秒级流式吐词定稿，提供包括 fun-asr-realtime、paraformer-realtime-v2、qwen3-asr-flash 在内的完整模型生态支持。",
        ),
    )

    fun getDetail(type: TranslationEngineType): TranslationEngineDetail {
        return DETAILS[type] ?: DETAILS.getValue(TranslationEngineType.DEEPSEEK)
    }

    fun getAsrDetail(type: AsrEngineType): AsrEngineDetail {
        return ASR_DETAILS[type] ?: ASR_DETAILS.getValue(AsrEngineType.LOCAL_SENSEVOICE)
    }

    fun getTranslationEnginesByCategory(category: EngineCategory): List<TranslationEngineType> {
        return TranslationEngineType.entries.filter { getDetail(it).category == category }
    }

    fun getAsrEnginesByCategory(category: EngineCategory): List<AsrEngineType> {
        return AsrEngineType.entries.filter { getAsrDetail(it).category == category }
    }
}
