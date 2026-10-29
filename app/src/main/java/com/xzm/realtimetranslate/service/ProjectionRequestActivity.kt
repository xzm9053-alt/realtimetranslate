package com.xzm.realtimetranslate.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.xzm.realtimetranslate.data.AudioSourceMode
import com.xzm.realtimetranslate.data.ScreenOcrMode
import com.xzm.realtimetranslate.util.AppLog as Log

/**
 * 完全透明的跳板 Activity：
 * 仅用于向系统申请 MediaProjection 录屏授权。
 * 授权完成或取消后立即 finish() 退出，绝不携带主应用界面，
 * 保证用户在使用外部 App（看视频、玩游戏、看网页）时点击悬浮球不会跳转到主软件界面。
 */
class ProjectionRequestActivity : ComponentActivity() {

    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val mode = intent.getStringExtra(EXTRA_MODE)
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            when (mode) {
                MODE_OCR -> {
                    val region = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_REGION, Rect::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_REGION)
                    } ?: Rect(0, 0, 1, 1)

                    ScreenTextSessionService.start(
                        this,
                        region = region,
                        resultCode = result.resultCode,
                        data = result.data!!,
                        mode = ScreenOcrMode.REGION,
                    )
                }
                MODE_VOICE -> {
                    val audioSourceStr = intent.getStringExtra(EXTRA_AUDIO_SOURCE) ?: AudioSourceMode.MEDIA.name
                    val audioSource = runCatching { AudioSourceMode.valueOf(audioSourceStr) }
                        .getOrDefault(AudioSourceMode.MEDIA)

                    SubtitleSessionService.start(
                        this,
                        audioSource = audioSource,
                        resultCode = result.resultCode,
                        data = result.data!!,
                    )
                }
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mpm == null) {
            Log.e(TAG, "MediaProjectionManager is null")
            finish()
            return
        }
        try {
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch screen capture intent", t)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        private const val TAG = "ProjectionRequestActivity"
        const val EXTRA_MODE = "mode"
        const val EXTRA_REGION = "region"
        const val EXTRA_AUDIO_SOURCE = "audio_source"
        const val MODE_OCR = "ocr"
        const val MODE_VOICE = "voice"

        fun requestOcr(context: Context, region: Rect) {
            val intent = Intent(context, ProjectionRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_MODE, MODE_OCR)
                putExtra(EXTRA_REGION, region)
            }
            context.startActivity(intent)
        }

        fun requestVoice(context: Context, audioSource: AudioSourceMode) {
            val intent = Intent(context, ProjectionRequestActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                putExtra(EXTRA_MODE, MODE_VOICE)
                putExtra(EXTRA_AUDIO_SOURCE, audioSource.name)
            }
            context.startActivity(intent)
        }
    }
}
