# AI 找片 - 2026-06-26

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
