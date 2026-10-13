package com.xzm.realtimetranslate.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    private val checker = UpdateChecker()

    @Test
    fun evaluateUpdate_whenRemoteIsHigher_returnsHasUpdate() {
        val remote = RemoteUpdateInfo(versionCode = 5, versionName = "v0.5.0")
        val result = checker.evaluateUpdate(remote, currentVersionCode = 4)
        assertTrue(result is UpdateCheckResult.HasUpdate)
        assertEquals(5, (result as UpdateCheckResult.HasUpdate).info.versionCode)
    }

    @Test
    fun evaluateUpdate_whenRemoteIsEqualOrLower_returnsAlreadyLatest() {
        val remoteEqual = RemoteUpdateInfo(versionCode = 4, versionName = "v0.4.0")
        assertEquals(UpdateCheckResult.AlreadyLatest, checker.evaluateUpdate(remoteEqual, currentVersionCode = 4))

        val remoteLower = RemoteUpdateInfo(versionCode = 3, versionName = "v0.3.0")
        assertEquals(UpdateCheckResult.AlreadyLatest, checker.evaluateUpdate(remoteLower, currentVersionCode = 4))
    }
}
