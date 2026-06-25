# OTA 更新 - 2026-06-25

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
