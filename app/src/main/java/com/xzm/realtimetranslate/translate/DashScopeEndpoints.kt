package com.xzm.realtimetranslate.translate

/**
 * DashScope realtime ASR endpoint selection.
 *
 * Bailian moved the newer Qwen-Audio models to per-workspace hosts
 * (`{WorkspaceId}.cn-beijing.maas.aliyuncs.com`). Fun-ASR and Paraformer still
 * answer on the legacy host, so a blank Workspace ID keeps the old endpoint and
 * nothing regresses for existing users.
 */
object DashScopeEndpoints {

    private const val LEGACY = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/"

    fun realtime(workspaceId: String): String {
        val id = workspaceId.trim()
        return if (id.isBlank()) LEGACY
        else "wss://$id.cn-beijing.maas.aliyuncs.com/api-ws/v1/inference"
    }

    /** Qwen-Audio only answers on the workspace host, so it needs a Workspace ID. */
    fun requiresWorkspace(model: String): Boolean = model.trim().startsWith("qwen-audio")

    /**
     * OpenAI-Realtime compatible socket used by Qwen3-ASR. The model travels in the
     * query string rather than a run-task envelope, per the OpenAI Realtime spec.
     */
    fun openAiRealtime(model: String, workspaceId: String = ""): String {
        val id = workspaceId.trim()
        val host = if (id.isBlank()) "dashscope.aliyuncs.com" else "$id.cn-beijing.maas.aliyuncs.com"
        val name = java.net.URLEncoder.encode(
            model.trim().ifBlank { OpenAiRealtimeProtocol.DEFAULT_MODEL },
            "UTF-8",
        )
        return "wss://$host/api-ws/v1/realtime?model=$name"
    }
}
