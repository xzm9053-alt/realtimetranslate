package com.xzm.realtimetranslate.ui

import android.content.Intent
import com.xzm.realtimetranslate.ui.main.isVideoShare
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoShareIntentTest {

    @Test
    fun `ACTION_SEND 带 video 通配类型算视频分享`() {
        assertTrue(isVideoShare(Intent.ACTION_SEND, "video/*"))
    }

    @Test
    fun `ACTION_SEND 带具体视频类型也算`() {
        assertTrue(isVideoShare(Intent.ACTION_SEND, "video/mp4"))
        assertTrue(isVideoShare(Intent.ACTION_SEND, "video/x-matroska"))
    }

    @Test
    fun `非 ACTION_SEND 不算`() {
        assertFalse(isVideoShare(Intent.ACTION_VIEW, "video/*"))
        assertFalse(isVideoShare(Intent.ACTION_SEND_MULTIPLE, "video/*"))
        assertFalse(isVideoShare(null, "video/*"))
    }

    @Test
    fun `非视频类型不算`() {
        assertFalse(isVideoShare(Intent.ACTION_SEND, "text/plain"))
        assertFalse(isVideoShare(Intent.ACTION_SEND, "image/*"))
        assertFalse(isVideoShare(Intent.ACTION_SEND, "application/pdf"))
        assertFalse(isVideoShare(Intent.ACTION_SEND, null))
    }
}
