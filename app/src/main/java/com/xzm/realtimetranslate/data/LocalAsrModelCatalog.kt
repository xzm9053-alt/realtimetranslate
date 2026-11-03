package com.xzm.realtimetranslate.data

import android.content.Context
import java.io.File

/**
 * 本地开源语音识别 (ASR) 模型架构分类
 */
enum class LocalAsrArch {
    SENSE_VOICE,
    PARAFORMER,
    WHISPER,
    MOONSHINE,
    QWEN3_ASR,
    FIRE_RED_ASR,
}

/**
 * 本地开源语音识别模型预设信息
 */
data class LocalAsrModelPreset(
    val id: String,
    val name: String,
    val description: String,
    val domesticDownloadUrl: String,
    val mirrorUrl: String,
    val sizeMb: Int,
    val supportedLangs: List<String>,
    val architecture: LocalAsrArch,
    /** 是否支持 350~400ms 高频局部草稿；自回归大模型（Whisper、Qwen3-ASR 等）为 false 以防 CPU 积压与发热 */
    val supportsDraft: Boolean = true,
)

/**
 * 权重探测结果
 */
data class LocalAsrProbeResult(
    val isReady: Boolean,
    val modelDir: File?,
    val files: Map<String, File>,
)

/**
 * 本地开源语音识别模型编目与权重探针中心（对齐电脑版生态）
 */
object LocalAsrModelCatalog {

    const val DEFAULT_MODEL_ID = "sensevoice-small"

    val PRESETS: List<LocalAsrModelPreset> = listOf(
        LocalAsrModelPreset(
            id = "sensevoice-small",
            name = "⚡ SenseVoice INT8 (内置免下载 · 中/英/日/韩/粤)",
            description = "阿里轻量端侧语音识别标杆，毫秒级流式打字机，自带声音事件与情绪检测，默认内置免配置。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/iic/SenseVoiceSmall",
            mirrorUrl = "https://hf-mirror.com/FunAudioLLM/SenseVoiceSmall",
            sizeMb = 239,
            supportedLangs = listOf("zh", "en", "ja", "ko", "yue", "auto"),
            architecture = LocalAsrArch.SENSE_VOICE,
            supportsDraft = true,
        ),
        LocalAsrModelPreset(
            id = "paraformer-zh",
            name = "⭐ Paraformer 中文 (阿里工业级 · 会议演讲极稳 · 约220MB)",
            description = "阿里达摩院非自回归普通话工业级识别标杆，中文断句标点极稳，长句同传不漏字。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/pengzhendong/sherpa-onnx-paraformer-zh",
            mirrorUrl = "https://hf-mirror.com/k2-fsa/sherpa-onnx-paraformer-zh-2024-03-09",
            sizeMb = 220,
            supportedLangs = listOf("zh"),
            architecture = LocalAsrArch.PARAFORMER,
            supportsDraft = true,
        ),
        LocalAsrModelPreset(
            id = "whisper-tiny",
            name = "🌐 Whisper Tiny (OpenAI 99语种全能 · 极速版 · 约75MB)",
            description = "OpenAI Whisper 极速轻量版，支持全球 99 种主流语言转写与识别，超低 CPU 与内存占用。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/pengzhendong/sherpa-onnx-whisper-tiny",
            mirrorUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-tiny",
            sizeMb = 75,
            supportedLangs = listOf("all"),
            architecture = LocalAsrArch.WHISPER,
            supportsDraft = false,
        ),
        LocalAsrModelPreset(
            id = "whisper-small",
            name = "🌐 Whisper Small (OpenAI 99语种全能 · 高精版 · 约460MB)",
            description = "OpenAI Whisper 高精版，全球 99 种语言识别质量卓越，尤其擅长小语种及杂音复杂环境。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/pengzhendong/sherpa-onnx-whisper-small",
            mirrorUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-small",
            sizeMb = 460,
            supportedLangs = listOf("all"),
            architecture = LocalAsrArch.WHISPER,
            supportsDraft = false,
        ),
        LocalAsrModelPreset(
            id = "whisper-large-v3-turbo",
            name = "👑 Whisper Large v3 Turbo (OpenAI 旗舰极速版 · 约650MB)",
            description = "OpenAI 旗舰级语音识别，4层快速解码器，在保持接近 Large v3 极高准确率的同时大幅提升推理速度，多语种泛化最佳。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/pengzhendong/sherpa-onnx-whisper-large-v3-turbo",
            mirrorUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-whisper-large-v3-turbo",
            sizeMb = 650,
            supportedLangs = listOf("all"),
            architecture = LocalAsrArch.WHISPER,
            supportsDraft = false,
        ),
        LocalAsrModelPreset(
            id = "moonshine-tiny",
            name = "🚀 Moonshine Tiny (端侧边缘极速版 · 延迟极低 · 约60MB)",
            description = "Useful Sensors 端侧专用高效率语音识别架构，针对边缘实时优化，延迟仅为 Whisper 几分之一。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/AI-ModelScope/sherpa-onnx-moonshine-tiny-en-int8",
            mirrorUrl = "https://hf-mirror.com/csukuangfj/sherpa-onnx-moonshine-tiny-en-int8",
            sizeMb = 60,
            supportedLangs = listOf("en"),
            architecture = LocalAsrArch.MOONSHINE,
            supportsDraft = true,
        ),
        LocalAsrModelPreset(
            id = "qwen3-asr-0.6b",
            name = "⭐ Qwen3-ASR 0.6B (阿里千问 · 30语种+22方言 · 约600MB)",
            description = "阿里通义千问新一代 Speech-LLM 架构轻量版，30 种语言与 22 种中文方言，抗噪及复杂场景出色。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/Qwen/Qwen3-ASR-0.6B",
            mirrorUrl = "https://hf-mirror.com/Qwen/Qwen3-ASR-0.6B",
            sizeMb = 600,
            supportedLangs = listOf("zh", "en", "ja", "ko", "yue", "all"),
            architecture = LocalAsrArch.QWEN3_ASR,
            supportsDraft = false,
        ),
        LocalAsrModelPreset(
            id = "fire-red-asr",
            name = "🔥 FireRedASR (小红书 · 中文超低字错率 CER 0.57% · 约1.1GB)",
            description = "小红书工业级中文识别标杆，公开基准字错率 CER 低至 0.57%，普通话与方言转写精度极高。",
            domesticDownloadUrl = "https://www.modelscope.cn/models/FireRedTeam/FireRedASR",
            mirrorUrl = "https://hf-mirror.com/FireRedTeam/FireRedASR",
            sizeMb = 1100,
            supportedLangs = listOf("zh", "en"),
            architecture = LocalAsrArch.FIRE_RED_ASR,
            supportsDraft = false,
        ),
    )

    fun getPresetById(modelId: String): LocalAsrModelPreset {
        return PRESETS.firstOrNull { it.id == modelId } ?: PRESETS.first()
    }

    /**
     * 探测指定模型是否就绪。
     * 检索位置包含应用内部 filesDir、外部 externalFilesDir（便于用户连电脑直接拷入）。
     * 具备自动深度检索一层子目录的容错能力（防止解压多出一层 wrapper 目录）。
     */
    fun probe(
        context: Context,
        modelId: String,
        customSearchRoots: List<File>? = null,
    ): LocalAsrProbeResult {
        val preset = getPresetById(modelId)
        val candidates = mutableListOf<File>()

        // 1. 自定义搜索根（用于测试或特殊路径）
        customSearchRoots?.forEach { root ->
            candidates.add(File(root, modelId))
            candidates.add(root)
        }

        // 2. 外部私有目录（用户连 USB 电脑在 /Android/data/com.xzm.realtimetranslate/files/models/asr/ 下直接粘贴即可）
        runCatching {
            context.getExternalFilesDir(null)?.let { ext ->
                candidates.add(File(ext, "models/asr/$modelId"))
                candidates.add(File(ext, "models/$modelId"))
            }
        }

        // 3. 内部私有目录
        val filesDir = context.filesDir
        candidates.add(File(filesDir, "models/asr/$modelId"))
        candidates.add(File(filesDir, "models/$modelId"))

        // 4. SenseVoice 内置兼容目录
        if (modelId == "sensevoice-small") {
            candidates.add(File(filesDir, "models/sensevoice"))
            candidates.add(File(filesDir, "models/SenseVoiceSmall"))
            candidates.add(File(filesDir, "models"))
        }

        for (dir in candidates) {
            if (!dir.isDirectory) continue
            val directFiles = checkFilesForArch(dir, preset.architecture)
            if (directFiles != null) {
                return LocalAsrProbeResult(isReady = true, modelDir = dir, files = directFiles)
            }

            // 容错：检查用户解压可能多出的一层子目录
            val subdirs = dir.listFiles { f -> f.isDirectory } ?: emptyArray()
            for (sub in subdirs) {
                val subFiles = checkFilesForArch(sub, preset.architecture)
                if (subFiles != null) {
                    return LocalAsrProbeResult(isReady = true, modelDir = sub, files = subFiles)
                }
            }
        }

        return LocalAsrProbeResult(isReady = false, modelDir = null, files = emptyMap())
    }

    /**
     * 根据架构检测目录内的模型权重和字典文件
     */
    fun checkFilesForArch(dir: File, arch: LocalAsrArch): Map<String, File>? {
        if (!dir.isDirectory) return null
        val allFiles = dir.listFiles() ?: return null

        when (arch) {
            LocalAsrArch.SENSE_VOICE, LocalAsrArch.PARAFORMER -> {
                val modelFile = allFiles.firstOrNull { it.name == "model.int8.onnx" }
                    ?: allFiles.firstOrNull { it.name == "model.onnx" }
                    ?: allFiles.firstOrNull { it.name.endsWith(".onnx") && !it.name.contains("vad") }
                val tokensFile = allFiles.firstOrNull { it.name == "tokens.txt" }
                    ?: allFiles.firstOrNull { it.name.endsWith(".txt") && it.name.contains("tokens") }
                if (modelFile != null && tokensFile != null) {
                    return mapOf("model_file" to modelFile, "tokens_file" to tokensFile)
                }
            }
            LocalAsrArch.WHISPER -> {
                val encFiles = allFiles.filter { it.name.contains("encoder", ignoreCase = true) && it.name.endsWith(".onnx") }
                val decFiles = allFiles.filter { it.name.contains("decoder", ignoreCase = true) && it.name.endsWith(".onnx") }
                val tokensFile = allFiles.firstOrNull { it.name.contains("tokens", ignoreCase = true) && it.name.endsWith(".txt") }
                    ?: allFiles.firstOrNull { it.name == "tokens.txt" }

                if (encFiles.isNotEmpty() && decFiles.isNotEmpty() && tokensFile != null) {
                    val enc = encFiles.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: encFiles.first()
                    val dec = decFiles.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: decFiles.first()
                    return mapOf(
                        "encoder_file" to enc,
                        "decoder_file" to dec,
                        "tokens_file" to tokensFile,
                    )
                }
            }
            LocalAsrArch.MOONSHINE -> {
                val prep = allFiles.firstOrNull { it.name.startsWith("preprocess", ignoreCase = true) && it.name.endsWith(".onnx") }
                val enc = allFiles.firstOrNull { it.name.startsWith("encode", ignoreCase = true) && it.name.endsWith(".onnx") }
                val uncached = allFiles.firstOrNull { it.name.contains("uncached_decode", ignoreCase = true) && it.name.endsWith(".onnx") }
                val cached = allFiles.firstOrNull { it.name.contains("cached_decode", ignoreCase = true) && it.name.endsWith(".onnx") }
                val tokens = allFiles.firstOrNull { it.name == "tokens.txt" }

                if (prep != null && enc != null && uncached != null && cached != null && tokens != null) {
                    return mapOf(
                        "prep_file" to prep,
                        "enc_file" to enc,
                        "uncached_file" to uncached,
                        "cached_file" to cached,
                        "tokens_file" to tokens,
                    )
                }
            }
            LocalAsrArch.QWEN3_ASR -> {
                val conv = allFiles.firstOrNull { it.name.contains("conv_frontend", ignoreCase = true) && it.name.endsWith(".onnx") }
                val encFiles = allFiles.filter { it.name.contains("encoder", ignoreCase = true) && it.name.endsWith(".onnx") }
                val decFiles = allFiles.filter { it.name.contains("decoder", ignoreCase = true) && it.name.endsWith(".onnx") }
                val tok = allFiles.firstOrNull { it.name.contains("tokenizer", ignoreCase = true) && it.name.endsWith(".json") }
                    ?: allFiles.firstOrNull { it.name.contains("tokens", ignoreCase = true) && it.name.endsWith(".txt") }
                    ?: allFiles.firstOrNull { it.name.contains("bpe", ignoreCase = true) && it.name.endsWith(".model") }

                if (conv != null && encFiles.isNotEmpty() && decFiles.isNotEmpty() && tok != null) {
                    val enc = encFiles.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: encFiles.first()
                    val dec = decFiles.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: decFiles.first()
                    return mapOf(
                        "conv_frontend" to conv,
                        "encoder" to enc,
                        "decoder" to dec,
                        "tokenizer" to tok,
                    )
                }
            }
            LocalAsrArch.FIRE_RED_ASR -> {
                val encFiles = allFiles.filter { it.name.contains("encoder", ignoreCase = true) && it.name.endsWith(".onnx") }
                val decFiles = allFiles.filter { it.name.contains("decoder", ignoreCase = true) && it.name.endsWith(".onnx") }
                val tokensFile = allFiles.firstOrNull { it.name.contains("tokens", ignoreCase = true) && it.name.endsWith(".txt") }
                    ?: allFiles.firstOrNull { it.name == "tokens.txt" }

                if (encFiles.isNotEmpty() && decFiles.isNotEmpty() && tokensFile != null) {
                    val enc = encFiles.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: encFiles.first()
                    val dec = decFiles.firstOrNull { it.name.contains("int8", ignoreCase = true) } ?: decFiles.first()
                    return mapOf(
                        "encoder_file" to enc,
                        "decoder_file" to dec,
                        "tokens_file" to tokensFile,
                    )
                }
            }
        }
        return null
    }
}
