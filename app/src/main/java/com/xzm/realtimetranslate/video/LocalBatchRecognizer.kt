package com.xzm.realtimetranslate.video

import com.k2fsa.sherpa.onnx.OfflineFireRedAsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineParaformerModelConfig
import com.k2fsa.sherpa.onnx.OfflineQwen3AsrModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import com.k2fsa.sherpa.onnx.getFeatureConfig
import com.xzm.realtimetranslate.data.LocalAsrArch
import com.xzm.realtimetranslate.data.LocalAsrModelCatalog
import com.xzm.realtimetranslate.data.LocalAsrModelPreset
import com.xzm.realtimetranslate.data.LocalAsrProbeResult
import com.xzm.realtimetranslate.translate.AsrEngine
import com.xzm.realtimetranslate.util.AppLog as Log
import java.io.File

/**
 * 通用离线整段语音识别器（视频字幕提取与批处理）。
 *
 * 完整支持 SenseVoice、Paraformer、Whisper、Moonshine、Qwen3-ASR、FireRedASR 全部 6 大架构。
 * 若指定模型权重未就绪，自动安全平滑降级（Fallback）回退至内置 SenseVoice。
 */
class LocalBatchRecognizer(
    preset: LocalAsrModelPreset,
    probeResult: LocalAsrProbeResult?,
    fallbackModelDir: File,
    language: String = "auto",
    numThreads: Int = 2,
) : BatchSpeechRecognizer {

    val activePreset: LocalAsrModelPreset = resolveActivePreset(preset, probeResult)
    private val normalizedLang: String = normalizeLanguageForArch(language, activePreset.architecture)

    private val recognizer: OfflineRecognizer = run {
        val modelConfig = buildModelConfig(activePreset, probeResult, fallbackModelDir, normalizedLang, numThreads)
        OfflineRecognizer(
            assetManager = null,
            config = OfflineRecognizerConfig(
                featConfig = getFeatureConfig(sampleRate = VadTimeline.SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
            ),
        )
    }

    override suspend fun recognize(samples: FloatArray): String? {
        if (samples.isEmpty()) return null
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, VadTimeline.SAMPLE_RATE)
            recognizer.decode(stream)
            val raw = recognizer.getResult(stream).text
            cleanResultText(raw, activePreset.architecture)
        } finally {
            stream.release()
        }
    }

    override fun close() {
        runCatching { recognizer.release() }
    }

    companion object {
        private const val TAG = "LocalBatchRecognizer"

        fun resolveActivePreset(
            requestedPreset: LocalAsrModelPreset,
            probeResult: LocalAsrProbeResult?,
        ): LocalAsrModelPreset {
            val isReady = probeResult != null && probeResult.isReady
            return if (!isReady && requestedPreset.id != LocalAsrModelCatalog.DEFAULT_MODEL_ID) {
                Log.w(TAG, "本地模型 ${requestedPreset.id} 未就绪，视频批量提取降级回退内置 SenseVoice")
                LocalAsrModelCatalog.getPresetById(LocalAsrModelCatalog.DEFAULT_MODEL_ID)
            } else {
                requestedPreset
            }
        }

        fun normalizeLanguageForArch(code: String, arch: LocalAsrArch): String {
            return when (arch) {
                LocalAsrArch.WHISPER -> {
                    if (code == "auto" || code.isBlank()) ""
                    else if (code.startsWith("zh")) "zh"
                    else code.split("-").first().lowercase()
                }
                LocalAsrArch.SENSE_VOICE -> AsrEngine.senseVoiceLanguageFor(code)
                else -> code
            }
        }

        fun cleanResultText(raw: String?, arch: LocalAsrArch): String? {
            if (raw == null) return null
            return if (arch == LocalAsrArch.SENSE_VOICE) {
                AsrEngine.cleanSenseVoiceText(raw)
            } else {
                val trimmed = raw.trim()
                if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) null else trimmed
            }
        }

        fun buildModelConfig(
            preset: LocalAsrModelPreset,
            probeResult: LocalAsrProbeResult?,
            fallbackModelDir: File,
            language: String,
            numThreads: Int,
        ): OfflineModelConfig {
            val isFallback = probeResult == null || !probeResult.isReady
            return when (preset.architecture) {
                LocalAsrArch.SENSE_VOICE -> {
                    val modelFile = if (!isFallback && probeResult.files.containsKey("model_file")) {
                        probeResult.files["model_file"]!!.absolutePath
                    } else {
                        File(fallbackModelDir, "model.int8.onnx").absolutePath
                    }
                    val tokensFile = if (!isFallback && probeResult.files.containsKey("tokens_file")) {
                        probeResult.files["tokens_file"]!!.absolutePath
                    } else {
                        File(fallbackModelDir, "tokens.txt").absolutePath
                    }
                    OfflineModelConfig(
                        senseVoice = OfflineSenseVoiceModelConfig(
                            model = modelFile,
                            language = language,
                        ),
                        tokens = tokensFile,
                        numThreads = numThreads,
                    )
                }
                LocalAsrArch.PARAFORMER -> {
                    val files = probeResult!!.files
                    OfflineModelConfig(
                        paraformer = OfflineParaformerModelConfig(
                            model = files["model_file"]!!.absolutePath,
                        ),
                        tokens = files["tokens_file"]!!.absolutePath,
                        numThreads = numThreads,
                    )
                }
                LocalAsrArch.WHISPER -> {
                    val files = probeResult!!.files
                    OfflineModelConfig(
                        whisper = OfflineWhisperModelConfig(
                            encoder = files["encoder_file"]!!.absolutePath,
                            decoder = files["decoder_file"]!!.absolutePath,
                            language = language,
                            task = "transcribe",
                        ),
                        tokens = files["tokens_file"]!!.absolutePath,
                        numThreads = numThreads,
                    )
                }
                LocalAsrArch.MOONSHINE -> {
                    val files = probeResult!!.files
                    OfflineModelConfig(
                        moonshine = OfflineMoonshineModelConfig(
                            preprocessor = files["prep_file"]!!.absolutePath,
                            encoder = files["enc_file"]!!.absolutePath,
                            uncachedDecoder = files["uncached_file"]!!.absolutePath,
                            cachedDecoder = files["cached_file"]!!.absolutePath,
                        ),
                        tokens = files["tokens_file"]!!.absolutePath,
                        numThreads = numThreads,
                    )
                }
                LocalAsrArch.QWEN3_ASR -> {
                    val files = probeResult!!.files
                    OfflineModelConfig(
                        qwen3Asr = OfflineQwen3AsrModelConfig(
                            convFrontend = files["conv_frontend"]!!.absolutePath,
                            encoder = files["encoder"]!!.absolutePath,
                            decoder = files["decoder"]!!.absolutePath,
                            tokenizer = files["tokenizer"]!!.absolutePath,
                        ),
                        tokens = files["tokens_file"]?.absolutePath ?: "",
                        numThreads = numThreads,
                    )
                }
                LocalAsrArch.FIRE_RED_ASR -> {
                    val files = probeResult!!.files
                    OfflineModelConfig(
                        fireRedAsr = OfflineFireRedAsrModelConfig(
                            encoder = files["encoder_file"]!!.absolutePath,
                            decoder = files["decoder_file"]!!.absolutePath,
                        ),
                        tokens = files["tokens_file"]!!.absolutePath,
                        numThreads = numThreads,
                    )
                }
            }
        }
    }
}
