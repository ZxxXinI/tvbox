# Settings - 2026-06-28

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
