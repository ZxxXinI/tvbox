package com.tvbox.app.data

import com.tvbox.app.domain.CustomAiConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.json.Json
import java.util.UUID

internal fun customAiConfigServer(
    scope: CoroutineScope,
    saved: CustomAiConfig?,
    onSubmit: suspend (CustomAiConfig) -> PhoneFormSubmission,
    onExpired: suspend () -> Unit = {},
    hostProvider: () -> String = ::phoneFormLocalAddress,
): PhoneFormServer {
    val nonce = UUID.randomUUID().toString()
    return PhoneFormServer(
        parentScope = scope,
        submitPath = "/save",
        renderForm = { token, values, error ->
            val apiBaseUrl = values["apiBaseUrl"] ?: saved?.apiBaseUrl.orEmpty()
            customConfigPage(
                token = token,
                nonce = nonce,
                apiBaseUrl = apiBaseUrl,
                modelId = values["modelId"] ?: saved?.modelId.orEmpty(),
                hasSavedKey = saved?.apiKey?.isAiApiKey() == true && saved.apiBaseUrl == apiBaseUrl,
                error = error,
            )
        },
        savedPage = ::aiConfigSavedPage,
        onSubmit = { values ->
            val endpoints = AiApiEndpoints.parse(values["apiBaseUrl"].orEmpty())
            val apiKey = endpoints?.let { resolveApiKey(values, saved, it) }
            val modelId = values["modelId"].orEmpty().trim()
            when {
                endpoints == null -> PhoneFormSubmission.Invalid(INVALID_ADDRESS)
                apiKey == null -> PhoneFormSubmission.Invalid(INVALID_KEY)
                !modelId.isAiModelId() -> PhoneFormSubmission.Invalid("请选择模型，或填写有效的模型 ID。")
                else -> onSubmit(CustomAiConfig(endpoints.baseUrl, modelId, apiKey))
            }
        },
        onExpired = onExpired,
        hostProvider = hostProvider,
        dataRoute = PhoneFormDataRoute("/models", nonce) { values ->
            val endpoints = AiApiEndpoints.parse(values["apiBaseUrl"].orEmpty())
            val apiKey = endpoints?.let { resolveApiKey(values, saved, it) }
            val result = when {
                endpoints == null -> AiModelListResult(error = INVALID_ADDRESS)
                apiKey == null -> AiModelListResult(error = INVALID_KEY)
                else -> fetchAiModels(endpoints, apiKey)
            }
            Json.encodeToString(AiModelListResult.serializer(), result)
        },
    )
}

private fun resolveApiKey(values: Map<String, String>, saved: CustomAiConfig?, endpoints: AiApiEndpoints): String? {
    val typed = values["apiKey"].orEmpty().trim()
    val apiKey = typed.ifEmpty { saved?.takeIf { it.apiBaseUrl == endpoints.baseUrl }?.apiKey.orEmpty() }
    return apiKey.takeIf { it.isAiApiKey() }
}

private const val INVALID_ADDRESS = "请填写有效的 HTTP 或 HTTPS API 地址。"
private const val INVALID_KEY = "请填写此地址的有效 API Key。"

private fun customConfigPage(
    token: String,
    nonce: String,
    apiBaseUrl: String,
    modelId: String,
    hasSavedKey: Boolean,
    error: String?,
): String = """
    <!doctype html>
    <html lang="zh-CN"><head><meta charset="utf-8">
    <meta name="viewport" content="width=device-width,initial-scale=1">
    <title>配置大模型 · TVBox</title>
    <style>
      *{box-sizing:border-box} [hidden]{display:none!important}
      body{margin:0;background:#0d100e;color:#f5f6f5;font:16px system-ui,sans-serif}
      main{max-width:460px;margin:18px auto;padding:22px;border:1px solid #333d36;border-radius:20px;background:#141816}
      h1{margin:0 0 22px;padding:0 0 18px;border-bottom:1px solid #2d3730;font-size:26px}
      form{display:flex;flex-direction:column;gap:18px;min-height:540px}
      label,legend{font-weight:650} .field{display:grid;gap:8px}
      input:not([type=radio]){min-width:0;width:100%;height:48px;padding:0 12px;border:1px solid #35423a;
        border-radius:10px;background:#1b221e;color:inherit;font:inherit}
      .key{position:relative} .key input{padding-right:64px}
      .key button{position:absolute;right:5px;top:5px;min-height:38px;padding:0 8px;border:0;background:transparent;color:#afbbb3;font-size:14px}
      button{min-height:46px;padding:8px 12px;border:1px solid transparent;border-radius:10px;
        background:#1ed760;color:#071a0d;font:inherit;font-weight:700}
      button:disabled{background:#232a25;color:#89968d} .secondary{background:transparent;color:#61ee96;border-color:#3e5746}
      :is(input,button):focus-visible,.option:focus-within{outline:2px solid #61ee96;outline-offset:2px}
      .actions{display:flex;gap:10px}.actions button:first-child{flex:1}
      fieldset{min-width:0;margin:0;padding:0;border:0} legend{padding:0 0 8px}
      .choices{display:grid;gap:6px;max-height:236px;overflow:auto;padding:3px}
      .option{display:flex;gap:10px;align-items:center;padding:10px;border:1px solid #35423a;border-radius:9px;background:#1b221e;overflow-wrap:anywhere}
      .option:has(input:checked){background:#173225;border-color:#1ed760}
      input[type=radio]{flex:none;accent-color:#1ed760}
      .hint,.placeholder{color:#afbbb3;font-size:14px}.placeholder{margin:0;padding:14px;background:#1b221e;border-radius:10px}
      .error{margin:0;padding:12px;border:1px solid #644837;border-radius:10px;background:#30251e;color:#ffd7bd}
      footer{margin-top:auto;padding-top:16px;border-top:1px solid #2d3730}footer button{width:100%}
      @media(max-width:480px){main{margin:12px}}
    </style></head><body><main>
    <h1>配置大模型</h1>
    <form id="config" method="post" action="/save" autocomplete="off">
      <input type="hidden" name="token" value="$token">
      <div class="field">
        <label for="apiBaseUrl">API 地址</label>
        <input id="apiBaseUrl" name="apiBaseUrl" type="url" required maxlength="1024"
          placeholder="https://api.example.com/v1" value="${apiBaseUrl.phoneFormEscape()}" autocapitalize="off" spellcheck="false">
      </div>
      <div class="field">
        <label for="apiKey">API Key</label>
        <div class="key"><input id="apiKey" name="apiKey" type="password" maxlength="1024"
          placeholder="${if (hasSavedKey) "已配置，留空使用原 Key" else "请输入 API Key"}"
          autocomplete="new-password" autocapitalize="off" spellcheck="false">
          <button id="showKey" type="button" hidden>显示</button>
        </div>
      </div>
      <div id="actions" class="actions" hidden>
        <button id="fetchModels" type="button">获取模型</button>
        <button id="manualEntry" type="button" class="secondary">手动填写</button>
      </div>
      <p id="status" class="error" role="status" ${if (error == null) "hidden" else ""}>${error.orEmpty().phoneFormEscape()}</p>
      <fieldset id="modelChoices" hidden><legend>选择模型</legend><div id="models" class="choices"></div></fieldset>
      <p class="placeholder" id="noModel" hidden>尚未选择模型</p>
      <div class="field" id="modelField">
        <label for="modelId">模型 ID</label>
        <input id="modelId" name="modelId" required value="${modelId.phoneFormEscape()}" maxlength="256"
          autocapitalize="off" spellcheck="false">
        <span class="hint">填写服务商提供的模型 ID</span>
      </div>
      <footer><button id="save" type="submit">保存到电视</button></footer>
    </form>
    </main><script nonce="$nonce">
    const form = document.getElementById('config');
    const address = form.elements.apiBaseUrl;
    const key = form.elements.apiKey;
    const model = form.elements.modelId;
    const get = id => document.getElementById(id);
    const status = get('status');
    const fetchButton = get('fetchModels');
    const manualButton = get('manualEntry');
    const list = get('models');
    const hasSavedKey = $hasSavedKey;
    const savedAddress = address.value;
    let mode = model.value || !status.hidden ? 'manual' : 'empty';
    let loading = false;
    let fetchFailed = false;

    function hasCredentials() {
      return address.value.trim() && address.validity.valid &&
        (key.value.trim() || hasSavedKey && address.value.trim() === savedAddress);
    }

    function render() {
      get('modelField').hidden = mode !== 'manual';
      model.type = mode === 'manual' ? 'text' : 'hidden';
      model.required = mode === 'manual';
      get('noModel').hidden = mode !== 'empty';
      get('modelChoices').hidden = mode !== 'list';
      fetchButton.disabled = loading || !hasCredentials();
      fetchButton.textContent = loading ? '正在获取…' : fetchFailed ? '重新获取' : '获取模型';
      fetchButton.classList.toggle('secondary', fetchFailed && !loading);
      manualButton.hidden = fetchFailed;
      manualButton.disabled = loading;
      get('save').disabled = loading || !hasCredentials() || !model.value.trim();
    }

    get('showKey').addEventListener('click', event => {
      const reveal = key.type === 'password';
      key.type = reveal ? 'text' : 'password';
      event.currentTarget.textContent = reveal ? '隐藏' : '显示';
    });
    get('showKey').hidden = false;
    get('actions').hidden = false;

    function credentialsChanged() {
      status.hidden = true;
      fetchFailed = false;
      if (mode === 'list') {
        mode = 'empty';
        model.value = '';
        list.replaceChildren();
      }
      render();
    }
    address.addEventListener('input', credentialsChanged);
    key.addEventListener('input', credentialsChanged);
    model.addEventListener('input', render);
    manualButton.addEventListener('click', () => {
      mode = 'manual';
      render();
      model.focus();
    });

    function showModels(ids) {
      const previous = model.value;
      model.value = ids.includes(previous) ? previous : '';
      list.replaceChildren();
      for (const id of ids) {
        const radio = document.createElement('input');
        radio.type = 'radio';
        radio.name = 'modelChoice';
        radio.checked = id === model.value;
        radio.addEventListener('change', () => { model.value = id; render(); });
        const label = document.createElement('label');
        label.className = 'option';
        label.append(radio, document.createTextNode(id));
        list.append(label);
      }
      mode = 'list';
    }

    fetchButton.addEventListener('click', async () => {
      if (loading || !hasCredentials()) return;
      const body = new URLSearchParams({token: form.elements.token.value, apiBaseUrl: address.value, apiKey: key.value});
      loading = true;
      status.hidden = true;
      address.readOnly = key.readOnly = model.readOnly = true;
      render();
      let error;
      try {
        const response = await fetch('/models', {method: 'POST', body});
        if (response.status === 403) {
          error = '二维码已失效，请在电视上重新打开。';
        } else if (response.ok) {
          const result = await response.json();
          if (result.models?.length) {
            showModels(result.models);
            fetchFailed = false;
          } else {
            error = result.error || '服务未返回可选模型。';
          }
        } else {
          error = '获取模型失败，请重试。';
        }
      } catch {
        error = '无法连接电视，请重试。';
      }
      if (error) {
        status.textContent = error;
        status.hidden = false;
        mode = 'manual';
        fetchFailed = true;
      }
      loading = false;
      address.readOnly = key.readOnly = model.readOnly = false;
      render();
    });
    render();
    </script></body></html>
""".trimIndent()
