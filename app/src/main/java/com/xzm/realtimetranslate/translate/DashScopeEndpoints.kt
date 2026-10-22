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
}
