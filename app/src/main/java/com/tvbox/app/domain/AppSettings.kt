package com.tvbox.app.domain

import kotlinx.serialization.Serializable

data class AppSettings(
    val homeApiLineId: String = "liangzi",
    val customVideoApiLines: List<CustomVideoApiLine> = emptyList(),
    val aiProviderId: String = AiProviders.default.id,
    val aiModelName: String = AiProviders.default.defaultModel,
    val aiApiKey: String = "",
    val checkUpdatesOnStartup: Boolean = true,
    val playbackAgentAutoSwitchEnabled: Boolean = true,
)

@Serializable
data class CustomVideoApiLine(
    val id: String,
    val name: String,
    val baseUrl: String,
) {
    fun toApiLine(): ApiLine {
        return ApiLine(
            id = id,
            name = name,
            baseUrls = listOf(baseUrl),
        )
    }
}

fun List<CustomVideoApiLine>.toApiLines(): List<ApiLine> {
    return map { it.toApiLine() }
}

data class AiProvider(
    val id: String,
    val name: String,
    val apiBaseUrl: String,
    val defaultModel: String,
) {
    val chatCompletionsUrl: String
        get() {
            val normalized = apiBaseUrl.trim().trimEnd('/')
            return if (normalized.endsWith("/chat/completions")) {
                normalized
            } else {
                "$normalized/chat/completions"
            }
        }
}

object AiProviders {
    val all = listOf(
        AiProvider(
            id = "agnes",
            name = "Agnes",
            apiBaseUrl = "https://apihub.agnes-ai.com/v1/chat/completions",
            defaultModel = "agnes-2.0-flash",
        ),
        AiProvider(
            id = "deepseek",
            name = "DeepSeek",
            apiBaseUrl = "https://api.deepseek.com",
            defaultModel = "deepseek-v4-flash",
        ),
        AiProvider(
            id = "siliconflow",
            name = "SiliconFlow",
            apiBaseUrl = "https://api.siliconflow.cn/v1",
            defaultModel = "Qwen/Qwen2.5-7B-Instruct",
        ),
        AiProvider(
            id = "qwen",
            name = "Qwen",
            apiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
            defaultModel = "qwen-plus",
        ),
    )

    val default: AiProvider = all.first()

    fun find(id: String): AiProvider = all.firstOrNull { it.id == id } ?: default
}
