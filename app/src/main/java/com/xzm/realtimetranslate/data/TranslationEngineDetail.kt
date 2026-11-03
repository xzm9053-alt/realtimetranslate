package com.xzm.realtimetranslate.data

/**
 * 翻译引擎详细介绍与生态元数据
 */
data class TranslationEngineDetail(
    val type: TranslationEngineType,
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
    val iconEmoji: String,
    val brandColorLong: Long,
    val categoryBadge: String,
    val highlight: String,
    val costDesc: String,
    val suitableScenarios: String,
    val detailText: String,
)

object TranslationEngineCatalog {

    val DETAILS: Map<TranslationEngineType, TranslationEngineDetail> = mapOf(
        TranslationEngineType.DEEPSEEK to TranslationEngineDetail(
            type = TranslationEngineType.DEEPSEEK,
            iconEmoji = "🐳",
            brandColorLong = 0xFF3B82F6, // 科技蓝
            categoryBadge = "开源旗舰大模型",
            developer = "深度求索 (DeepSeek)",
            highlight = "推理与句式重组极佳 · 性价比天花板",
            costDesc = "付费按量计费（千字几厘钱，超低使用成本）",
            suitableScenarios = "技术文档、学术论文、长篇剧本文案、中英长难句重组与指代消歧",
            recommendedModels = listOf("deepseek-v4-flash (推荐)", "deepseek-v4-pro"),
            detailText = "DeepSeek 在技术文档、代码注释、学术论文及中英长难句重组上表现极佳。凭借极强的逻辑推理能力，它在处理指代消歧与文学化润色时比传统机翻自然得多；App 支持一键开启/关闭 AI 深度思考模式。",
        ),
        TranslationEngineType.ZHIPU to TranslationEngineDetail(
            type = TranslationEngineType.ZHIPU,
            iconEmoji = "🔮",
            brandColorLong = 0xFF8B5CF6, // 灵动紫
            categoryBadge = "商业专精大模型",
            developer = "智谱 AI (清华技术背景)",
            highlight = "永久免费额度 · 国内极速直连",
            costDesc = "GLM-4-Flash 永久免费不限量（国内直连免梯）",
            suitableScenarios = "日常视频字幕追番、口语交流、长时同传听写（零费用首选）",
            recommendedModels = listOf("glm-4-flash (免费推荐)", "glm-4-air", "glm-5"),
            detailText = "国内顶尖 AI 研发机构推出的代表作。提供 GLM-4-Flash 永久免费不限量的专属优惠，免翻墙国内毫秒级响应，是日常视频字幕同传与长时间挂机翻译的最佳免费大模型方案。",
        ),
        TranslationEngineType.GEMINI to TranslationEngineDetail(
            type = TranslationEngineType.GEMINI,
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
        TranslationEngineType.MICROSOFT to TranslationEngineDetail(
            type = TranslationEngineType.MICROSOFT,
            iconEmoji = "🌐",
            brandColorLong = 0xFF10B981, // 清新绿
            categoryBadge = "传统神经机翻 (NMT)",
            developer = "微软 (Microsoft Edge API)",
            highlight = "免密钥 · 零门槛即开即用 · 毫秒响应",
            costDesc = "完全免费 · 免 Key · 国内直连",
            suitableScenarios = "开箱即用、追求超低网络延迟、对长篇润色无特殊要求的快翻",
            recommendedModels = emptyList(),
            detailText = "经典工业级神经机器翻译（NMT）通道，免注册 API Key，国内极速直连。响应时间通常仅需 200~400ms，绝不产生大模型幻觉，非常适合刚装机想要立即体验翻译的用户。",
        ),
        TranslationEngineType.GOOGLE_FREE to TranslationEngineDetail(
            type = TranslationEngineType.GOOGLE_FREE,
            iconEmoji = "🌈",
            brandColorLong = 0xFFF59E0B, // 琥珀金
            categoryBadge = "传统神经机翻 (NMT)",
            developer = "Google 网页翻译接口",
            highlight = "免密钥 · 广谱语种覆盖 · 确定性高",
            costDesc = "完全免费 · 免 Key",
            suitableScenarios = "全球生僻小语种互译、词条短句快速对照",
            recommendedModels = emptyList(),
            detailText = "Google 传统神经机器翻译免密钥通道，覆盖全球上百种主流与小语种。采用确定性 NMT 架构，输出稳定，不消耗 Token 额度，支持作为备用翻译引擎随时切换。",
        ),
        TranslationEngineType.OPENAI_COMPAT to TranslationEngineDetail(
            type = TranslationEngineType.OPENAI_COMPAT,
            iconEmoji = "⚡",
            brandColorLong = 0xFFEC4899, // 洋红粉
            categoryBadge = "自建 / 开源 / 第三方",
            developer = "通用 OpenAI 兼容协议",
            highlight = "支持本地 Ollama / 硅基流动 / 任意兼容端点",
            costDesc = "视所接服务商而定（可接免费或本地离线部署）",
            suitableScenarios = "私有化部署、高阶极客、日漫专精 SakuraLLM、字节 Seed-X、混元翻译",
            recommendedModels = listOf(
                "🌸 SakuraLLM (日漫汉化霸主)",
                "🔥 Seed-X-7B (字节精翻/成语诗歌)",
                "⭐ Qwen2.5-7B (开源综合第一)",
                "🌐 NLLB-200 (Meta 200语种零幻觉)",
            ),
            detailText = "全能开放协议插槽。支持接入任何标准 OpenAI 格式端点（如硅基流动、OpenRouter、Groq 或本地局域网电脑运行的 Ollama / LM Studio）。结合当前开源主流，可挂载日漫专精的 SakuraLLM、字节 Seed-X 专精模型或 Meta NLLB-200。",
        ),
        TranslationEngineType.NONE to TranslationEngineDetail(
            type = TranslationEngineType.NONE,
            iconEmoji = "🔇",
            brandColorLong = 0xFF64748B, // 板岩灰
            categoryBadge = "纯端侧本地模式",
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
            iconEmoji = "💻",
            brandColorLong = 0xFF10B981, // 绿色
            categoryBadge = "纯端侧离线 (0流量)",
            highlight = "基于 sherpa-onnx · 毫秒打字机 · 支持8大顶尖开源模型",
            costDesc = "完全免费 · 离线不出机 · 0 流量",
            suitableScenarios = "日常视频同传、隐私保护、无网环境、低延迟口语打字机",
            detailText = "纯 C++ 编译的端侧推理引擎，默认内置阿里 SenseVoice INT8 模型（239MB），同时支持扩展加载 Paraformer、Whisper Turbo、Moonshine、Qwen3-ASR 等 8 款业界顶尖开源模型，具备模型未就绪自动平滑降级保护。",
        ),
        AsrEngineType.ALIYUN_FUN_ASR to AsrEngineDetail(
            type = AsrEngineType.ALIYUN_FUN_ASR,
            iconEmoji = "⚡",
            brandColorLong = 0xFF3B82F6, // 蓝色
            categoryBadge = "工业级云端全双工",
            highlight = "WebSocket 实时流式传输 · 阿里百炼官方出品",
            costDesc = "阿里云百炼官方提供新用户首年赠送额度",
            suitableScenarios = "正式会议长句同传、专业演讲、高抗噪要求环境",
            detailText = "阿里云百炼工业级流式语音识别服务，全双工 WebSocket 长连接握手，毫秒级流式吐词定稿，提供包括 fun-asr-realtime、paraformer-realtime-v2、qwen3-asr-flash 在内的完整模型生态支持。",
        ),
        AsrEngineType.GENERIC_OPENAI to AsrEngineDetail(
            type = AsrEngineType.GENERIC_OPENAI,
            iconEmoji = "🌐",
            brandColorLong = 0xFF8B5CF6, // 紫色
            categoryBadge = "标准通用转录 (HTTP)",
            highlight = "POST /v1/audio/transcriptions 标准接口",
            costDesc = "视服务商而定（硅基流动提供免费 SenseVoice 转写）",
            suitableScenarios = "硅基流动免费云端识别、自建 Faster-Whisper 服务器、Groq",
            detailText = "标准 OpenAI 音频转录接口协议，每次切句时将音频打包为标准 WAV 格式并发起转录，支持灵活配置自建或第三方语音转录服务。",
        ),
    )

    fun getDetail(type: TranslationEngineType): TranslationEngineDetail {
        return DETAILS[type] ?: DETAILS.getValue(TranslationEngineType.DEEPSEEK)
    }

    fun getAsrDetail(type: AsrEngineType): AsrEngineDetail {
        return ASR_DETAILS[type] ?: ASR_DETAILS.getValue(AsrEngineType.LOCAL_SENSEVOICE)
    }
}
