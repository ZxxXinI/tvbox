# AI 找片 - 2026-06-26

## 2026-06-28 16:40 - 语音识别空结果修复

## File Changes

- File path: `app/src/main/AndroidManifest.xml`
  - Reason: 应用内语音识别需要麦克风权限，并且 Android 11+ 需要声明可查询的语音识别服务。
  - Purpose: 声明 `android.permission.RECORD_AUDIO` 和 `android.speech.RecognitionService` 查询能力，支持运行时申请录音权限和查找系统语音服务。

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: ADB 日志显示外部系统语音 Activity 打开后很快返回空结果，导致 AI 找片页提示“没有听清”。
  - Purpose: 将语音找片从 `ActivityResult` 外部语音页改为应用内 `SpeechRecognizer`；处理 TVBox 麦克风权限、系统语音服务麦克风权限、识别结果和错误码。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 语音识别过程需要区别“正在听”和“识别失败”。
  - Purpose: 新增 `aiVoiceListening` 状态和 `startAiVoiceListening()`，识别结束或失败时清理监听状态。

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: 监听期间需要给用户明确反馈，并避免重复点击输入控件。
  - Purpose: 语音识别中显示“正在听，请说出找片需求”，同时禁用输入、找片、换一批等按钮。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布 bug 修复，便于后续发版整理。
  - Purpose: 增加语音找片空结果修复说明。

- File path: `README.md`
  - Reason: AI 找片语音方案从系统语音页调整为应用内语音识别。
  - Purpose: 更新功能描述为“应用内语音识别”。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入本次语音识别空结果修复索引。

- File path: `devLog/ai-recommend.md`
  - Reason: AI 找片是独立模块，需要记录语音识别修复过程。
  - Purpose: 记录本次问题症状、文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-28 16:40
- Symptoms: 点击“语音找片”后，系统小米语音 Activity 会打开但很快返回空结果；改为应用内识别后发现小米语音服务自身 `RECORD_AUDIO` 为拒绝，返回权限错误。
- Attempted fix: 改为应用内 `SpeechRecognizer`；新增麦克风权限申请和语音服务查询声明；系统语音服务缺少麦克风权限时打开其设置页；识别中显示“正在听”，失败时根据错误码显示提示。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.
- ADB install and device smoke test
  - Result: installed successfully. Logs confirmed `com.tvbox.app` already had `RECORD_AUDIO`; Xiaomi speech service initially denied `RECORD_AUDIO`, then entered `正在听` after granting `com.xiaomi.mibrain.speech` microphone permission.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/ai-recommend.md`

## 2026-06-28 16:26 - 语音入口、快捷推荐词与换一批

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: AI 找片第一版需要用户手动移动到语音按钮或输入完整需求，电视遥控器操作不够直接。
  - Purpose: 进入页面默认聚焦“语音找片”，新增快捷推荐词横排按钮，并在输入区增加“换一批”入口。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 快捷词和换一批需要复用 AI 推荐请求，但换一批还要避免重复当前结果。
  - Purpose: 支持传入快捷词直接提交；“换一批”会把当前推荐片名作为排除名单拼入请求，重新生成一批推荐。

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: 系统语音识别取消、空结果或设备不支持时，原逻辑缺少页面反馈。
  - Purpose: 识别成功后直接提交识别文本；识别失败、取消或不支持语音时，在 AI 找片页展示明确提示。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布功能调整，便于后续发版整理。
  - Purpose: 增加 AI 找片语音入口、快捷推荐词和换一批说明。

- File path: `README.md`
  - Reason: 项目功能说明需要与当前 AI 找片能力一致。
  - Purpose: 更新 AI 找片功能描述，说明支持文字、语音、快捷推荐词和换一批。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入本次 AI 找片交互优化索引。

- File path: `devLog/ai-recommend.md`
  - Reason: AI 找片是独立模块，需要记录语音与推荐交互优化。
  - Purpose: 记录本次文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-28 16:26
- Symptoms: 语音取消或未识别到文字时没有页面提示；用户想要同类结果时只能重新输入需求。
- Attempted fix: 增加 AI 页面消息入口；语音回调失败时写入页面提示；新增快捷词和带排除名单的“换一批”请求。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/ai-recommend.md`

## 2026-06-27 20:19 - 延迟资源匹配与首页快捷键调整

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: AI 推荐生成后立即搜索每个推荐资源会拖慢页面，且用户未必会点击所有卡片。
  - Purpose: 推荐结果只保存模型 JSON；点击卡片时再搜索资源，搜不到时停留在 AI 找片页提示“暂无该视频资源”；从 AI 进入详情后返回 AI 页。

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: 推荐卡片展示阶段不再知道是否可播放，需要从“资源状态卡片”改为“AI 推荐卡片”。
  - Purpose: 图片区域使用片名占位，点击卡片时显示查找中提示，未命中资源时展示错误提示。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 首页快捷键需要按新顺序映射。
  - Purpose: 数字 1/2/3/4/5 分别打开历史、搜索、AI 找片、直播、设置。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Common.kt`
  - Reason: 首页顶部不再需要刷新按钮。
  - Purpose: 移除 `刷新(1)`，按钮改为 `历史(1)`、`搜索(2)`、`AI找片(3)`、`直播(4)`、`设置(5)`。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布功能调整，便于后续发版整理。
  - Purpose: 增加 AI 延迟资源匹配和首页快捷键调整说明。

- File path: `README.md`
  - Reason: 首页快捷键文档需要与实际行为一致。
  - Purpose: 更新首页快捷键表格，移除刷新并加入数字 5 设置。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入本次 AI 推荐延迟资源匹配索引。

- File path: `devLog/ai-recommend.md`
  - Reason: AI 找片是独立模块，需要记录交互流程调整。
  - Purpose: 记录本次延迟搜索、返回路径和快捷键调整的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-27 20:19
- Symptoms: AI 推荐生成后会提前逐条搜索资源站，造成不必要等待；从 AI 推荐进入详情后返回路径也需要保留 AI 页面上下文。
- Attempted fix: 将资源搜索改为点击卡片时执行；新增详情返回来源；首页快捷键同步调整。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/ai-recommend.md`

## 2026-06-27 19:05 - 推荐卡片尺寸调整

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: AI 推荐页卡片过大，当前横屏每行只能显示两个卡片，和首页影片网格密度不一致。
  - Purpose: 将推荐页网格 `minSize` 从 `260.dp` 调整为首页同款 `148.dp`，让卡片宽度和首页保持一致。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布 UI 优化，便于后续发版整理。
  - Purpose: 增加 AI 推荐页卡片尺寸优化说明。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入本次 AI 推荐卡片尺寸调整索引。

- File path: `devLog/ai-recommend.md`
  - Reason: AI 找片是独立模块，需要记录页面布局调整。
  - Purpose: 记录本次卡片尺寸调整的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-27 19:05
- Symptoms: AI 推荐页卡片太大，横屏每行只显示两个卡片。
- Attempted fix: 复用首页影片网格的 `148.dp` 自适应宽度，让推荐卡片密度与首页一致。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/ai-recommend.md`

## 2026-06-26 22:08 - 实验入口

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 测试阶段需要直接使用 API Key，但不能把密钥写入源码或仓库。
  - Purpose: 新增 `TVBOX_AI_API_KEY` 配置读取，并注入到 `BuildConfig.AI_API_KEY`。

- File path: `app/src/main/java/com/tvbox/app/domain/AiRecommendation.kt`
  - Reason: 大模型输出必须转成稳定结构，才能生成 TV 推荐页面。
  - Purpose: 定义 AI 推荐结果模型，并解析纯 JSON 或 fenced JSON 内容。

- File path: `app/src/main/java/com/tvbox/app/data/AiRecommendationRepository.kt`
  - Reason: 应用需要调用 Agnes Chat Completions 接口生成找片推荐。
  - Purpose: 新增 AI 推荐仓库，按“角色 + 任务 + 上下文 + 要求 + 输出格式”组织提示词，并解析 assistant content。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: AI 推荐结果需要和现有影视数据源匹配，匹配成功后才能进入详情页。
  - Purpose: 新增 AI 找片状态、提交逻辑和可播放资源匹配逻辑。

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: 用户需要一个独立页面查看 AI 生成的推荐卡片。
  - Purpose: 新增 AI 找片页面，支持输入、语音、加载、错误和可点击推荐卡片。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 首页需要进入 AI 找片页面，并支持遥控器数字快捷键。
  - Purpose: 注册 `AiRecommend` 页面，新增数字 6 快捷键。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Common.kt`
  - Reason: 首页顶部需要有明确入口。
  - Purpose: 新增 `AI找片(6)` 按钮。

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: 用户希望按键后可以语音交流，第一版可先复用系统语音识别。
  - Purpose: 新增系统语音识别 Intent，识别成功后填入 AI 查询并自动提交。

- File path: `app/src/test/java/com/tvbox/app/domain/AiRecommendationParserTest.kt`
  - Reason: 大模型输出格式可能有 JSON 代码块或字段差异。
  - Purpose: 覆盖纯 JSON、fenced JSON、`title` 字段回退和 `searchKeyword` 回退。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布功能，便于后续发版整理。
  - Purpose: 增加 AI 找片实验功能说明。

- File path: `README.md`
  - Reason: 新功能需要说明快捷键和本地 Key 配置方式。
  - Purpose: 增加 AI 找片功能介绍、数字 6 快捷键和 `TVBOX_AI_API_KEY` 配置说明。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 AI 找片实验入口索引。

- File path: `devLog/ai-recommend.md`
  - Reason: AI 找片是独立模块，需要记录接口、提示词、页面和匹配逻辑。
  - Purpose: 记录本次 AI 找片文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-26 22:08
- Symptoms: 无新增缺陷；本次为 AI 找片实验功能。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain --rerun-tasks`
  - Result: passed after adding local `TVBOX_AI_API_KEY`, ensuring `BuildConfig` was regenerated for local testing.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.
- Local config check:
  - Result: `TVBOX_AI_API_KEY` was added to ignored local `local.properties` for device testing; the secret is not committed.
- Live API smoke test:
  - Result: first request timed out after 30 seconds; second request reached the API but returned `HTTP 500 upstream_error`. App-side request wiring is in place, but provider availability needs retesting.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/ai-recommend.md`
