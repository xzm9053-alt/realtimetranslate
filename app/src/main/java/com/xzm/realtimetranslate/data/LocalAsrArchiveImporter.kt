package com.xzm.realtimetranslate.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.xzm.realtimetranslate.util.AppLog as Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

sealed interface ModelImportResult {
    data class Success(
        val modelId: String,
        val modelName: String,
        val fileCount: Int,
        val totalBytes: Long,
    ) : ModelImportResult

    data class Failure(
        val reason: String,
    ) : ModelImportResult
}

data class DetectedModelMatch(
    val preset: LocalAsrModelPreset,
    val modelDir: File,
    val files: Map<String, File>,
)

object LocalAsrArchiveImporter {

    private const val TAG = "LocalAsrArchiveImporter"

    /**
     * 将输入流解包到输出目录，自动防御 Zip Slip 路径逃逸。
     * 支持 .zip, .tar.gz, .tar.bz2, .tar 等格式。
     * 返回成功解压出的文件总数。
     */
    fun unpackStream(
        inputStream: InputStream,
        archiveName: String,
        outputDir: File,
        onProgress: (String) -> Unit = {},
    ): Int {
        outputDir.mkdirs()
        val canonicalOut = outputDir.canonicalFile
        val lowerName = archiveName.lowercase()
        val buffered = if (inputStream is BufferedInputStream) inputStream else BufferedInputStream(inputStream)

        var count = 0
        if (lowerName.endsWith(".tar.bz2") || lowerName.endsWith(".tbz2") || lowerName.endsWith(".bz2")) {
            onProgress("正在解压 BZip2 压缩归档...")
            val bzIn = BZip2CompressorInputStream(buffered)
            val tarIn = TarArchiveInputStream(bzIn)
            count = extractTar(tarIn, canonicalOut, onProgress)
        } else if (lowerName.endsWith(".tar.gz") || lowerName.endsWith(".tgz")) {
            onProgress("正在解压 GZip 压缩归档...")
            val gzIn = GzipCompressorInputStream(buffered)
            val tarIn = TarArchiveInputStream(gzIn)
            count = extractTar(tarIn, canonicalOut, onProgress)
        } else if (lowerName.endsWith(".tar")) {
            onProgress("正在解包 Tar 归档...")
            val tarIn = TarArchiveInputStream(buffered)
            count = extractTar(tarIn, canonicalOut, onProgress)
        } else {
            // 默认为 Zip 格式或通过头检测
            onProgress("正在解包 Zip 压缩文件...")
            val zipIn = ZipArchiveInputStream(buffered)
            count = extractZip(zipIn, canonicalOut, onProgress)
        }

        return count
    }

    private fun extractZip(
        zipIn: ZipArchiveInputStream,
        canonicalOut: File,
        onProgress: (String) -> Unit,
    ): Int {
        var count = 0
        var entry: ZipArchiveEntry? = zipIn.nextEntry
        while (entry != null) {
            val name = entry.name
            val target = File(canonicalOut, name).canonicalFile
            if (!isSafeChild(canonicalOut, target)) {
                Log.w(TAG, "Zip Slip skipped: $name")
                entry = zipIn.nextEntry
                continue
            }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out ->
                    zipIn.copyTo(out)
                }
                count++
            }
            entry = zipIn.nextEntry
        }
        return count
    }

    private fun extractTar(
        tarIn: TarArchiveInputStream,
        canonicalOut: File,
        onProgress: (String) -> Unit,
    ): Int {
        var count = 0
        var entry: TarArchiveEntry? = tarIn.nextEntry
        while (entry != null) {
            val name = entry.name
            val target = File(canonicalOut, name).canonicalFile
            if (!isSafeChild(canonicalOut, target)) {
                Log.w(TAG, "Tar Slip skipped: $name")
                entry = tarIn.nextEntry
                continue
            }
            if (entry.isDirectory) {
                target.mkdirs()
            } else {
                target.parentFile?.mkdirs()
                FileOutputStream(target).use { out ->
                    tarIn.copyTo(out)
                }
                count++
            }
            entry = tarIn.nextEntry
        }
        return count
    }

    private fun isSafeChild(parent: File, child: File): Boolean {
        val parentPath = parent.canonicalPath
        val childPath = child.canonicalPath
        return childPath == parentPath || childPath.startsWith(parentPath + File.separator)
    }

    /**
     * 自动遍历解包目录，检测与哪个 LocalAsrModelPreset 架构和权重指纹匹配。
     */
    fun detectMatchingPreset(
        extractedDir: File,
        preferredModelId: String? = null,
    ): DetectedModelMatch? {
        if (!extractedDir.isDirectory) return null

        val candidateDirs = mutableListOf<File>()
        candidateDirs.add(extractedDir)

        // 收集子目录 (最多 3 层)
        fun collectSubdirs(dir: File, depth: Int) {
            if (depth > 3) return
            val subs = dir.listFiles { f -> f.isDirectory } ?: return
            for (sub in subs) {
                candidateDirs.add(sub)
                collectSubdirs(sub, depth + 1)
            }
        }
        collectSubdirs(extractedDir, 1)

        // 优先匹配用户当前选择的模型
        if (!preferredModelId.isNullOrBlank()) {
            val preferredPreset = LocalAsrModelCatalog.PRESETS.firstOrNull { it.id == preferredModelId }
            if (preferredPreset != null) {
                for (dir in candidateDirs) {
                    val files = LocalAsrModelCatalog.checkFilesForArch(dir, preferredPreset.architecture)
                    if (files != null) {
                        return DetectedModelMatch(preferredPreset, dir, files)
                    }
                }
            }
        }

        // 全量遍历 8 大模型检测
        for (preset in LocalAsrModelCatalog.PRESETS) {
            for (dir in candidateDirs) {
                val files = LocalAsrModelCatalog.checkFilesForArch(dir, preset.architecture)
                if (files != null) {
                    return DetectedModelMatch(preset, dir, files)
                }
            }
        }

        return null
    }

    /**
     * 将识别成功的模型权重文件部署到 App 内部私有目录 files/models/asr/<modelId>/
     */
    fun deployModelFiles(
        sourceDir: File,
        destinationDir: File,
        preset: LocalAsrModelPreset,
    ): Boolean {
        if (!sourceDir.isDirectory) return false
        val verified = LocalAsrModelCatalog.checkFilesForArch(sourceDir, preset.architecture) ?: return false

        if (destinationDir.exists()) {
            destinationDir.deleteRecursively()
        }
        destinationDir.mkdirs()

        // 拷贝所有匹配的文件与字典
        for (file in verified.values) {
            val target = File(destinationDir, file.name)
            file.copyTo(target, overwrite = true)
        }

        // 额外拷贝目录下的所有 .txt / .json / .yaml 等元数据配置（如有）
        sourceDir.listFiles()?.forEach { extra ->
            if (extra.isFile && (extra.extension in listOf("txt", "json", "yaml", "onnx"))) {
                val target = File(destinationDir, extra.name)
                if (!target.exists()) {
                    extra.copyTo(target, overwrite = true)
                }
            }
        }

        val checkAfter = LocalAsrModelCatalog.checkFilesForArch(destinationDir, preset.architecture)
        return checkAfter != null
    }

    /**
     * 从系统 Uri 一键导入模型压缩包并全自动配置就绪。
     */
    suspend fun importArchive(
        context: Context,
        uri: Uri,
        preferredModelId: String? = null,
        onProgress: (String) -> Unit = {},
    ): ModelImportResult = withContext(Dispatchers.IO) {
        val fileName = queryFileName(context, uri) ?: "archive.zip"
        val tempDir = File(context.cacheDir, "model_import_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            onProgress("正在打开模型压缩包: $fileName")
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ModelImportResult.Failure("无法打开所选文件流，请检查文件权限")

            val count = inputStream.use { stream ->
                unpackStream(stream, fileName, tempDir, onProgress)
            }

            if (count == 0) {
                return@withContext ModelImportResult.Failure("压缩包内无有效文件，解压为空")
            }

            onProgress("正在自动识别模型架构与校验权重...")
            val match = detectMatchingPreset(tempDir, preferredModelId)
                ?: return@withContext ModelImportResult.Failure(
                    "未能识别到有效的 ASR 模型文件！\n请确保压缩包内包含对应架构的 .onnx 权重及 tokens.txt 字典。"
                )

            onProgress("正在部署到应用安全存储 (${match.preset.name})...")
            val targetDir = File(context.filesDir, "models/asr/${match.preset.id}")
            val success = deployModelFiles(match.modelDir, targetDir, match.preset)

            if (!success) {
                return@withContext ModelImportResult.Failure("模型文件复制部署失败，请检查手机可用空间")
            }

            val deployedFiles = targetDir.listFiles() ?: emptyArray()
            val totalBytes = deployedFiles.sumOf { it.length() }

            ModelImportResult.Success(
                modelId = match.preset.id,
                modelName = match.preset.name,
                fileCount = deployedFiles.size,
                totalBytes = totalBytes,
            )
        } catch (e: Exception) {
            Log.e(TAG, "Import archive failed", e)
            ModelImportResult.Failure("导入解压失败: ${e.localizedMessage ?: e.message}")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun queryFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "content") {
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }
}
