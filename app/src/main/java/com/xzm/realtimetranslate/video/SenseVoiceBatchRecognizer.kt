package com.xzm.realtimetranslate.video

import com.xzm.realtimetranslate.data.LocalAsrModelCatalog
import java.io.File

/**
 * 整段识别用的 SenseVoice 封装（视频字幕）。
 * 委托至通用的 [LocalBatchRecognizer] 实现。
 */
class SenseVoiceBatchRecognizer(
    modelDir: File,
    language: String = "auto",
    numThreads: Int = 2,
) : BatchSpeechRecognizer by LocalBatchRecognizer(
    preset = LocalAsrModelCatalog.getPresetById(LocalAsrModelCatalog.DEFAULT_MODEL_ID),
    probeResult = null,
    fallbackModelDir = modelDir,
    language = language,
    numThreads = numThreads,
)
