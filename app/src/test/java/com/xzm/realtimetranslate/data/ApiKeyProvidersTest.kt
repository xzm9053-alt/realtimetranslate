package com.xzm.realtimetranslate.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 这些是手写的第三方服务地址，所以单测的价值在于接住「数据悄悄坏掉」的那几种方式：
 * 打错的网址、复制粘贴出来的重复行、指到网站首页而不是申请页，以及付费的那家挤到
 * 免费的前面去。
 */
class ApiKeyProvidersTest {

    private val providers = ApiKeyProviders.all

    @Test
    fun `每一行都写得出名字和费用，地址是 https`() {
        assertTrue("方块点开会是空的", providers.isNotEmpty())
        providers.forEach { p ->
            assertNotEquals("$p 没有名字字符串", 0, p.nameRes)
            assertNotEquals("$p 没有费用说明字符串", 0, p.costRes)
            assertTrue("${p.consoleUrl} 必须是 https", p.consoleUrl.startsWith("https://"))
        }
    }

    @Test
    fun `每家都直接指到申请页，不是网站首页`() {
        // 这个模块的整个价值就是省掉「进去以后自己找」这一步。指到域名根就等于把
        // 找路这件事还给了用户，而这一行看起来和一条能用的深链一模一样。
        providers.forEach { p ->
            val path = p.consoleUrl.removePrefix("https://").substringAfter('/', "")
            assertTrue("${p.consoleUrl} 只指到网站首页，用户还得自己找", path.isNotBlank())
        }
    }

    @Test
    fun `没有两行指向同一个地方`() {
        // 重复在界面上看不出来——两行长得不一样但点出去到同一页，说明其中一行本来
        // 想写的是别处。
        val urls = providers.map { it.consoleUrl }
        assertEquals("有重复的申请页: $urls", urls.size, urls.distinct().size)
    }

    @Test
    fun `付费的那家排在最后，免费的先被看到`() {
        val last = providers.last()
        assertTrue(
            "唯一要花钱的服务商应该沉在底部，实际是 ${last.consoleUrl}",
            last.consoleUrl.contains("deepseek.com"),
        )
    }
}
