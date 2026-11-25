package com.tvbox.app.data

import kotlinx.coroutines.CoroutineScope

internal fun videoApiConfigServer(
    scope: CoroutineScope,
    onSubmit: suspend (String, String) -> PhoneFormSubmission,
    onExpired: suspend () -> Unit = {},
    hostProvider: () -> String = ::phoneFormLocalAddress,
    ttlMillis: Long = PHONE_FORM_TTL_MILLIS,
): PhoneFormServer = PhoneFormServer(
    parentScope = scope,
    submitPath = "/save",
    renderForm = { token, values, error ->
        configPage(token, values["lineName"].orEmpty(), values["apiUrl"].orEmpty(), error)
    },
    savedPage = ::savedPage,
    onSubmit = { values -> onSubmit(values["lineName"].orEmpty().trim(), values["apiUrl"].orEmpty().trim()) },
    onExpired = onExpired,
    hostProvider = hostProvider,
    ttlMillis = ttlMillis,
)

private fun configPage(
    token: String,
    lineName: String = "",
    apiUrl: String = "",
    error: String? = null,
): String {
    return """
        <!doctype html>
        <html lang="zh-CN">
        <head>
          <meta charset="utf-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>TVBox 视频接口配置</title>
          <style>
            body { margin: 0; font-family: sans-serif; background: #10141f; color: #f5f7fb; }
            main { max-width: 540px; margin: 0 auto; padding: 28px 18px; }
            h1 { font-size: 26px; margin: 0 0 10px; }
            p { color: #b8c0cf; line-height: 1.6; }
            label { display: block; margin: 18px 0 8px; font-weight: 700; }
            input { width: 100%; box-sizing: border-box; border: 1px solid #657084; border-radius: 10px; padding: 14px; font-size: 16px; background: #171d2a; color: #fff; }
            button { width: 100%; margin-top: 22px; border: 0; border-radius: 10px; padding: 15px; font-size: 18px; font-weight: 700; color: #051b17; background: #28d0b0; }
            .panel { background: #1a2130; border: 1px solid #2f3a4f; border-radius: 14px; padding: 18px; }
            .hint { font-size: 14px; color: #8f9bb0; }
            .error { color: #ffb4ab; font-weight: 700; }
          </style>
        </head>
        <body>
          <main>
            <h1>TVBox 视频接口配置</h1>
            <div class="panel">
              <p>添加 MacCms 视频接口后，会同步到电视设置页的视频接口列表。</p>
              <p class="hint">示例：https://example.com/api.php/provide/vod</p>
              ${error?.let { "<p class=\"error\">${it.phoneFormEscape()}</p>" }.orEmpty()}
              <form method="post" action="/save">
                <input type="hidden" name="token" value="$token" />
                <label for="lineName">接口名称</label>
                <input id="lineName" name="lineName" value="${lineName.phoneFormEscape()}" autocomplete="off" required />
                <label for="apiUrl">MacCms 接口地址</label>
                <input id="apiUrl" name="apiUrl" value="${apiUrl.phoneFormEscape()}" inputmode="url" autocomplete="off" required />
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
            <p>可以回到电视，在设置页选择这个视频接口。</p>
          </main>
        </body>
        </html>
    """.trimIndent()
}
