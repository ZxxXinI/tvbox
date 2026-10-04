# OTA 更新 - 2026-06-25

## 2026-10-04 11:33 - 合入正式 v1.3.10

- 应用户要求保留版本号，将下述后台更新能力合入正式 1.3.10，重新上传现有 Release 的 APK 和配套 OTA 清单。
- 签名 Release 重新构建通过；产物 SHA-256 与此前已安装的测试包一致，未重复安装或执行设备功能测试。
- 同版本已安装用户不会再次收到 OTA 提示，需手动下载覆盖安装；发布详情见 `devLog/release.md`，主日志见 `devLog/README.md`。

## 2026-10-04 10:59 - 系统后台下载、进度卡片与自动安装入口

### 文件修改

- `app/src/main/java/com/tvbox/app/data/AppUpdateRepository.kt`：使用 Android DownloadManager，保存任务 ID、版本及文件位置；重新进入应用继续观察同一任务，校验大小与 SHA-256 后才报告完成。UI 观察被取消时不移除系统下载任务。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：立即更新后关闭对话框、保持下载状态，启动时恢复任务；检查更新不再重置正在下载或已完成的状态，维护一次性自动安装事件。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`：独立于页面显示应用内小型进度卡片，支持失败重试和完成后手动安装；无需系统悬浮窗权限。
- `app/src/main/java/com/tvbox/app/MainActivity.kt`：仅在 RESUMED 状态自动打开安装界面；安装阶段检查未知来源权限，保留授权返回后的待安装路径，避免每次返回前台重复弹出。
- `app/src/main/res/xml/update_file_paths.xml`：允许 FileProvider 共享应用专属下载目录下的 APK。

### 缺陷记录

- 时间：2026-10-04 10:59。
- 现象：更新弹窗关闭后看不到下载状态，下载完成没有自动进入安装流程。
- 修复：系统管理下载，独立进度卡片显示状态；完成事件在前台触发系统安装，后台完成等待回到应用。安装仍需用户在系统界面确认。
- 临时方案：无。

### 验证与产物

- 签名 Release 构建和签名校验通过；按用户要求未执行设备功能测试。
- 已覆盖安装到 `192.168.0.8:5555`，返回 Success，保留数据，版本为 `10310 / 1.3.10`。
- APK：`app/build/outputs/apk/release/TVBox-v1.3.10-background-update-test.apk`；SHA-256 `cde557c130b3ea1b2045a0ea89f421bc3ef71756584232b5059127bee7701f87`。
- 本地测试包未发布；此功能用于后续应用内更新。
- 主日志：`devLog/README.md`。

## 2026-07-08 19:35 - 取消启动安装权限请求

## File Changes

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: 用户希望删除应用打开后立即获取安装权限的行为，避免首次进入应用被系统权限页打断。
  - Purpose: 删除 `onCreate` 中的首次启动权限请求，移除 `FirstLaunch` 权限动作和对应 SharedPreferences 标记；保留点击更新下载、安装 APK 时的权限检查与引导。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录做了什么、为什么做。
  - Purpose: 在主时间线登记本次 OTA 权限行为调整。

- File path: `devLog/ota-update.md`
  - Reason: 本次属于 OTA 安装权限流程调整。
  - Purpose: 记录修改文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-07-08 19:35
- Symptoms: 应用打开后会主动跳转安装未知应用权限页，打断用户进入应用。
- Attempted fix: 删除首次启动权限请求和 `FirstLaunch` 权限动作，仅在更新下载或安装时请求权限。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat compileDebugKotlin --console=plain`
  - Result: passed.
- `./gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/ota-update.md`
## 2026-06-25 08:07 - 安装权限前置

## File Changes

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: 电视盒子在 OTA 下载完成后才提示安装未知应用权限，容易导致安装阶段失败或用户不知道如何继续。
  - Purpose: 首次启动引导一次安装权限；点击更新时先检查权限，允许后继续下载；已下载 APK 安装前继续做权限兜底。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 更新弹窗原来直接调用 ViewModel 下载，无法在 Activity 层先处理系统权限。
  - Purpose: 增加 `onStartUpdateDownload` 回调，让“立即更新”先经过 Activity 权限预检。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布功能，便于后续发版整理。
  - Purpose: 增加 OTA 安装权限前置说明。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 OTA 安装权限前置索引。

- File path: `devLog/ota-update.md`
  - Reason: OTA 更新是独立模块，需要单独记录权限、下载和安装流程变更。
  - Purpose: 记录本次 OTA 权限行为的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-25 08:07
- Symptoms: 电视盒子检测到更新并下载 APK 后，安装阶段才提示安装权限问题，导致更新流程中断。
- Attempted fix: 首次启动引导安装未知应用权限；更新下载前和 APK 安装前统一检查权限，授权返回后继续原动作。
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
- Branch doc: `devLog/ota-update.md`
