package com.xzm.realtimetranslate.data

import androidx.annotation.StringRes
import com.xzm.realtimetranslate.R

/**
 * 一家能申请 API Key 的服务商。
 *
 * 只回答「去哪申请」一个问题，所以没有引擎、没有 BaseUrl、没有模型名——那些属于引擎
 * 自己的默认值和 [com.xzm.realtimetranslate.translate.ModelPresets]。这个类型早先还兼着
 * 「一键把引擎配好」，那个动作已经砍掉（模块只管申请 Key），数据也跟着瘦回它真正需要的样子。
 */
data class ApiKeyProvider(
    @StringRes val nameRes: Int,
    /** 一行费用说明：免费还是付费、免费到什么程度。 */
    @StringRes val costRes: Int,
    /** 申请页本身，不是网站首页。用系统浏览器打开。 */
    val consoleUrl: String,
)

/**
 * 申请 API Key 的四个去处，能白用的排在前面。
 *
 * 顺序本身就是信息：这个方块的读者是「还没决定要不要花钱」的人，所以智谱、硅基流动、
 * Google AI Studio 三家在前，只有 DeepSeek 是纯付费的，放最后并写明要先充值。
 *
 * 四个地址都对官方文档核实过。本项目对网址的规矩是**先核实再写进来**——猜错一个，用户
 * 拿到的是一次 404，而不是一个能用的 Key。
 */
object ApiKeyProviders {
    val all: List<ApiKeyProvider> = listOf(
        ApiKeyProvider(
            nameRes = R.string.apikey_zhipu,
            costRes = R.string.apikey_zhipu_cost,
            consoleUrl = "https://open.bigmodel.cn/usercenter/apikeys",
        ),
        ApiKeyProvider(
            nameRes = R.string.apikey_siliconflow,
            costRes = R.string.apikey_siliconflow_cost,
            consoleUrl = "https://cloud.siliconflow.cn/account/ak",
        ),
        ApiKeyProvider(
            nameRes = R.string.apikey_gemini,
            costRes = R.string.apikey_gemini_cost,
            consoleUrl = "https://aistudio.google.com/apikey",
        ),
        ApiKeyProvider(
            nameRes = R.string.apikey_deepseek,
            costRes = R.string.apikey_deepseek_cost,
            consoleUrl = "https://platform.deepseek.com/api_keys",
        ),
    )
}
