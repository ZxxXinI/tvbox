# Settings - 2026-06-28

## 2026-08-07 07:50 - 修复字体大小导致的启动闪退

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/theme/Theme.kt`
  - Reason: 默认 Typography 的 `letterSpacing` 是 `TextUnit.Unspecified`，不能直接参与乘法运算。
  - Purpose: 仅缩放已指定的字体单位，未指定字体大小或字距保持原样，避免 Compose 在首帧布局时崩溃。

- File path: `app/src/test/java/com/tvbox/app/ui/theme/ThemeTypographyTest.kt`
  - Reason: v1.3.2 的崩溃属于纯 UI 配置回归，需要自动化锁定。
  - Purpose: 验证未指定字距不会参与缩放，已指定字距和字体大小会按比例缩放。

## Bug Record

- Time: 2026-08-07 07:41
- Symptoms: v1.3.2 在 Android 设备启动后立即闪退，Logcat 显示 `IllegalArgumentException: Cannot perform operation for Unspecified type`。
- Attempted fix: 为 `TextUnit` 缩放添加 `isSpecified` 判断，避免对 `TextUnit.Unspecified` 进行乘法。
- Temporary solution: 无，已通过正式 Release APK 修复。

## Verification

- `./gradlew.bat testDebugUnitTest assembleRelease --console=plain --no-daemon`
  - Result: passed.
- Android 设备：安装 `v1.3.3 / 10303` 后，`MainActivity` 处于前台，应用进程存在，干净 Logcat 无 `FATAL EXCEPTION`。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/settings.md`

## 2026-08-06 22:26 - 字体大小与卡片布局设置

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`
  - Reason: 字体大小需要有默认值、显示名称和可持久化的稳定标识。
  - Purpose: 新增 `TvFontScale`，提供正常、大、超大三档，默认正常。

- File path: `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`
  - Reason: 用户选择的字体大小需要在应用重启后保留。
  - Purpose: 使用 SharedPreferences 读取和保存字体大小；旧配置或未知值安全回退到正常。

- File path: `app/src/main/java/com/tvbox/app/ui/theme/Theme.kt`
  - Reason: 所有页面都应使用同一套字体大小规则，不能逐页维护字号。
  - Purpose: 按字体档位缩放全局 Material Typography，并提供影视、历史、选集、设置和平台直播卡片的统一尺寸令牌。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 设置页选择后需要立即更新界面并异步保存。
  - Purpose: 新增 `updateFontScale`，更新状态和本机设置。

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: 应用主题层需要读取当前字体档位。
  - Purpose: 将 `fontScale` 传入 `TVBoxTheme`，使全部页面立即重组并应用字号。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 用户需要在设置页选择字体大小，首页、历史和设置网格也要同步适配。
  - Purpose: 在外观设置中新增三档字体大小按钮，并让首页影视、历史和设置网格读取统一尺寸。

- File path: `app/src/main/java/com/tvbox/app/ui/SearchScreen.kt`
  - Reason: 搜索结果必须和首页卡片大小保持一致。
  - Purpose: 搜索海报网格读取统一影视卡片尺寸。

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: AI 找片结果应同步字体和卡片密度。
  - Purpose: AI 推荐网格读取统一影视卡片尺寸。

- File path: `app/src/main/java/com/tvbox/app/ui/DetailScreen.kt`
  - Reason: 大字号下选集按钮需要增加宽度，避免内容拥挤。
  - Purpose: 选集网格读取统一选集尺寸。

- File path: `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`
  - Reason: 平台直播卡片也应跟随显示大小变化。
  - Purpose: 根据档位从 5、4、3 列显示直播卡片。

- File path: `app/src/test/java/com/tvbox/app/domain/TvFontScaleTest.kt`
  - Reason: 字体档位的配置兼容性需要可回归验证。
  - Purpose: 验证三档存储值、未知值回退和缩放比例递增。

## Bug Record

- Time: 2026-08-06 22:26
- Symptoms: 无。本次为设置和布局增强。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat compileDebugKotlin --console=plain`
  - Result: passed.
- `./gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/settings.md`
## 2026-07-01 09:02 - 自定义视频接口配置

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`
  - Reason: 用户希望在设置页添加自己的 MacCms 视频 API 接口，并且内置接口保持不变。
  - Purpose: 新增 `CustomVideoApiLine` 和 `customVideoApiLines` 设置项，用于保存用户自定义接口。

- File path: `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`
  - Reason: 自定义视频接口需要跨启动保存。
  - Purpose: 使用 SharedPreferences + JSON 持久化用户添加的接口列表，读取失败时回退为空列表。

- File path: `app/src/main/java/com/tvbox/app/data/MovieRepository.kt`
  - Reason: 首页、搜索、详情和 AI 找片需要能查询用户新增的 MacCms 接口。
  - Purpose: 保留内置 `ApiLines.defaults`，运行时追加设置页读取到的自定义接口。

- File path: `app/src/main/java/com/tvbox/app/data/VideoApiConfigServer.kt`
  - Reason: 电视端输入长 URL 不方便，需要像大模型配置一样用手机填写。
  - Purpose: 新增临时 HTTP 配置服务，弹窗打开时启动，手机提交接口名称和地址后同步回电视。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 设置页二维码弹窗需要管理服务生命周期，并在保存后更新当前可选视频接口。
  - Purpose: 新增视频接口配置弹窗状态、扫码服务启动/停止、自定义接口保存和线路刷新逻辑。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 设置页需要展示视频接口管理入口。
  - Purpose: 将“首页资源”升级为“视频接口”，保留线路下拉选择并新增“添加接口”二维码弹窗。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录做了什么。
  - Purpose: 在主时间线加入自定义视频接口配置索引。

- File path: `devLog/settings.md`
  - Reason: 本次改动属于设置页和数据源管理。
  - Purpose: 记录文件、原因、目的和验证结果。

## Verification

- `.\gradlew.bat compileDebugKotlin --console=plain`
  - Result: passed.
- `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed. Only line-ending warnings were reported.

## Bug Record

- Time: 2026-07-01 09:02
- Symptoms: 无新增缺陷；本次为设置页功能增强。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/settings.md`

## 2026-06-28 19:10 - AI 手机扫码配置

## File Changes

- File path: `gradle/libs.versions.toml`
  - Reason: 电视端需要离线生成手机配置二维码。
  - Purpose: 新增 ZXing core 依赖版本和库声明。
- File path: `app/build.gradle.kts`
  - Reason: 二维码生成需要引入 ZXing core。
  - Purpose: 添加 `implementation(libs.zxing.core)`。
- File path: `app/src/main/java/com/tvbox/app/data/AiConfigServer.kt`
  - Reason: 手机需要在同一局域网内提交模型名称和 API Key 到电视。
  - Purpose: 新增临时 HTTP 配置服务，弹窗打开时启动，关闭时停止；通过一次性 token 接收表单提交。
- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 设置页二维码弹窗需要启动/停止配置服务，并接收手机端提交结果。
  - Purpose: 新增 AI 配置弹窗状态、服务生命周期管理和手机提交后的设置保存逻辑。
- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 模型名称和 API Key 不适合用电视遥控器长文本输入。
  - Purpose: 将模型/API Key 输入框改为按钮；点击后弹出左侧二维码、右侧说明和局域网地址的配置弹窗。
- File path: `CHANGELOG.md`
  - Reason: 未发布记录需要反映扫码配置交互。
  - Purpose: 更新大模型配置描述为手机扫码填写。
- File path: `README.md`
  - Reason: 用户需要知道如何在下载后的 APK 中配置自己的大模型 Key。
  - Purpose: 说明手机扫码填写模型名称和 API Key 的流程，以及未填写 Key 时继续使用 APK 内置配置。

## Verification

- `./gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `./gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed.
- ADB screenshot
  - Result: skipped because connected device reported `offline`.

## Bug Record

- Time: 2026-06-28 19:10
- Symptoms: 遥控器输入模型名称和 API Key 不方便，尤其是长 API Key。
- Attempted fix: 电视端改为二维码弹窗，手机端网页输入并提交回电视本地服务。
- Temporary solution: 不适用。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/settings.md`

## 2026-06-28 18:49 - 大模型配置与设置按钮焦点

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`
  - Reason: 设置页需要保存用户选择的大模型、模型名称和 API Key。
  - Purpose: 新增 `AiProvider`、`AiProviders`、`aiProviderId`、`aiModelName`、`aiApiKey`，内置 Agnes、DeepSeek、SiliconFlow、Qwen 的接口地址和默认模型。
- File path: `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`
  - Reason: 大模型配置需要跨启动保存。
  - Purpose: 使用 SharedPreferences 持久化 AI provider、模型名和 API Key。
- File path: `app/src/main/java/com/tvbox/app/data/AiRecommendationRepository.kt`
  - Reason: AI 找片需要在用户填写 API Key 后切换到用户选择的大模型接口。
  - Purpose: 新增 `AiRequestConfig`；当设置页 API Key 为空时继续使用 APK 内置配置，填写后才使用用户配置。
- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 设置页输入需要写入状态并传给 AI 请求层。
  - Purpose: 新增更新 AI provider、模型名和 API Key 的方法，并在提交 AI 推荐时按“有 Key 才覆盖”的规则生成请求配置。
- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 设置页需要显示大模型选择、模型输入和 API Key 输入，并统一按钮焦点样式。
  - Purpose: 新增大模型设置行和 `SettingsActionButton`；设置页主要按钮改为空底描边、聚焦绿色填充。
- File path: `CHANGELOG.md`
  - Reason: 未发布功能需要进入更新记录。
  - Purpose: 记录大模型配置、AI 配置覆盖规则和设置页按钮焦点优化。
- File path: `README.md`
  - Reason: 用户需要知道别人下载 APK 后如何配置自己的 API Key。
  - Purpose: 说明 APK 默认使用打包配置，设置页填写 API Key 后才覆盖默认 AI 配置。

## Verification

- `./gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `./gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed.
- ADB screenshot
  - Result: skipped because connected device reported `offline`.

## Bug Record

- Time: 2026-06-28 18:49
- Symptoms: 设置页按钮焦点样式和首页不一致；公开 APK 用户无法在应用内配置自己的大模型 API Key。
- Attempted fix: 设置页新增大模型配置区，并统一按钮为空底描边、聚焦绿色填充。
- Temporary solution: 不适用。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/settings.md`

## 2026-06-28 17:22 - 首页资源切换

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`
  - Reason: 用户希望在设置页选择首页渲染数据使用的资源站。
  - Purpose: 新增 `homeApiLineId` 设置项，默认保持 `liangzi`。
- File path: `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`
  - Reason: 首页资源选择需要跨启动保存。
  - Purpose: 使用 SharedPreferences 持久化 `home_api_line_id`。
- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 首页加载、搜索和 AI 找片需要跟随设置中选择的数据源。
  - Purpose: 启动时读取 `homeApiLineId`，切换资源后保存设置、清空首页状态并重新加载首页。
- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 设置页需要暴露资源选择入口。
  - Purpose: 新增“首页资源 / 首页渲染数据”设置项，并通过下拉菜单展示量子、如意、360 等线路。

## Verification

- `./gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `./gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- ADB debug install and screenshot
  - Result: settings page rendered the new “首页资源 / 首页渲染数据” section with current source “量子”.

## Bug Record

- Time: 2026-06-28 17:22
- Symptoms: 无新增缺陷；本次为设置页功能补充。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/settings.md`
