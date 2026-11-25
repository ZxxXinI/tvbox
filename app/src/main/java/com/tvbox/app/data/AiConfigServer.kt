package com.tvbox.app.data

import com.tvbox.app.domain.AiProvider
import kotlinx.coroutines.CoroutineScope

internal fun aiConfigServer(
    scope: CoroutineScope,
    provider: AiProvider,
    modelName: String,
    onSubmit: suspend (String, String) -> PhoneFormSubmission,
    onExpired: suspend () -> Unit = {},
    hostProvider: () -> String = ::phoneFormLocalAddress,
    ttlMillis: Long = PHONE_FORM_TTL_MILLIS,
): PhoneFormServer = PhoneFormServer(
    parentScope = scope,
    submitPath = "/save",
    renderForm = { token, values, error ->
        configPage(token, provider, values["modelName"] ?: modelName, error)
    },
    savedPage = ::savedPage,
    onSubmit = { values ->
        val submittedModel = values["modelName"].orEmpty().trim()
        val submittedKey = values["apiKey"].orEmpty().trim()
        if (submittedModel.isBlank() || submittedKey.isBlank()) {
            PhoneFormSubmission.Invalid("请填写模型名称和 API Key。")
        } else {
            onSubmit(submittedModel, submittedKey)
        }
    },
    onExpired = onExpired,
    hostProvider = hostProvider,
    ttlMillis = ttlMillis,
)

private fun configPage(
    token: String,
    provider: AiProvider,
    modelName: String,
    error: String? = null,
): String {
    val safeModelName = modelName.phoneFormEscape()
    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>TVBox AI 配置</title>
          <style>
            body { margin: 0; font-family: sans-serif; background: #10141f; color: #f5f7fb; }
            main { max-width: 520px; margin: 0 auto; padding: 28px 18px; }
            h1 { font-size: 26px; margin: 0 0 10px; }
            p { color: #b8c0cf; line-height: 1.6; }
            label { display: block; margin: 18px 0 8px; font-weight: 700; }
            input { width: 100%; box-sizing: border-box; border: 1px solid #657084; border-radius: 10px; padding: 14px; font-size: 16px; background: #171d2a; color: #fff; }
            button { width: 100%; margin-top: 22px; border: 0; border-radius: 10px; padding: 15px; font-size: 18px; font-weight: 700; color: #051b17; background: #28d0b0; }
            .panel { background: #1a2130; border: 1px solid #2f3a4f; border-radius: 14px; padding: 18px; }
            .error { color: #ffb4ab; font-weight: 700; }
          </style>
        </head>
        <body>
          <main>
            <h1>TVBox AI 配置</h1>
            <div class="panel">
              <p>当前大模型：<strong>${provider.name.phoneFormEscape()}</strong></p>
              <p>填写后会自动同步到电视。API Key 只保存在电视本机。</p>
              ${error?.let { "<p class=\"error\">${it.phoneFormEscape()}</p>" }.orEmpty()}
              <form method="post" action="/save">
                <input type="hidden" name="token" value="$token" />
                <label for="modelName">模型名称</label>
                <input id="modelName" name="modelName" value="$safeModelName" autocomplete="off" required />
                <label for="apiKey">API Key</label>
                <input id="apiKey" name="apiKey" type="password" autocomplete="off" required />
                <button type="submit">确认同步到电视</button>
              </form>
            </div>
          </main>
        </body>
        </html>
    """.trimIndent()
}

private fun savedPage(): String {
    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>已保存</title>
          <style>
            body { margin: 0; font-family: sans-serif; background: #10141f; color: #f5f7fb; }
            main { max-width: 520px; margin: 0 auto; padding: 34px 18px; }
            h1 { color: #28d0b0; }
            p { color: #b8c0cf; line-height: 1.6; }
          </style>
        </head>
        <body>
          <main>
            <h1>已同步到电视</h1>
            <p>可以回到电视继续使用 AI 找片。</p>
          </main>
        </body>
        </html>
    """.trimIndent()
}
