package com.tvbox.app.data

import kotlinx.coroutines.CoroutineScope

data class PhoneSearchSession(val url: String)

/** 仅在搜索对话框打开时提供影片关键词输入。 */
class PhoneSearchServer(
    parentScope: CoroutineScope,
    onSubmit: suspend (String) -> String?,
    onDelivered: suspend () -> Unit,
    onExpired: suspend () -> Unit = {},
    hostProvider: () -> String = ::phoneFormLocalAddress,
    ttlMillis: Long = PHONE_FORM_TTL_MILLIS,
) {
    private val server = PhoneFormServer(
        parentScope = parentScope,
        submitPath = "/search",
        renderForm = { token, _, error -> page(token, error) },
        savedPage = {
            phoneFormDocument(
                "<main class=panel><h1>已发送</h1><p>请在电视上查看搜索结果。</p>" +
                    "<p>再次搜索请在电视上重新打开二维码。</p></main>",
            )
        },
        onSubmit = { values ->
            val query = values["query"].orEmpty().trim()
            if (query.isBlank() || query.codePointCount(0, query.length) > 100) {
                PhoneFormSubmission.Invalid("请输入 1–100 个字的影片名称。")
            } else {
                onSubmit(query)?.let { PhoneFormSubmission.Inactive(it) } ?: PhoneFormSubmission.Accepted
            }
        },
        onDelivered = onDelivered,
        onExpired = onExpired,
        closeAfterDelivery = false,
        hostProvider = hostProvider,
        ttlMillis = ttlMillis,
    )

    val isSessionActive: Boolean get() = server.isSessionActive

    fun start(): PhoneSearchSession = PhoneSearchSession(server.start())

    fun stop() = server.stop()

    private fun page(token: String, error: String?): String = phoneFormDocument("""
        <main class="panel" role="dialog" aria-labelledby="title"><h1 id="title">在电视上搜索</h1>
        <p>请输入要在电视上搜索的影片名称。</p>${error?.let { "<p class=error>${it.phoneFormEscape()}</p>" }.orEmpty()}
        <form method="post" action="/search"><input type="hidden" name="token" value="$token">
        <label for="query">影片名称</label><input id="query" name="query" placeholder="例如：西游记" maxlength="100" required autofocus autocomplete="off">
        <button type="submit">在电视上搜索</button></form><p>请保持电视停留在扫码窗口。二维码 5 分钟有效。</p></main>
    """.trimIndent())
}
