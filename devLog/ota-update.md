# OTA 更新 - 2026-06-25

## 2026-10-08 12:01 - OTA 修复正式纳入 v1.3.12

- 用户验收并授权发布新版本，安装授权续接、系统下载缺失回退与保存 APK 功能纳入 `10312 / 1.3.12`。
- 同步正式签名 APK、Release 和 main 更新清单；旧版无法拉起授权/下载组件时，可从 Release 页面手动覆盖安装。
- 本轮只执行发布构建和产物/远端校验，不重复设备功能测试；验证与发布证据见 `devLog/release.md`。

## 2026-10-08 11:10 - 系统组件缺失回退下载与保存安装包

### 缺陷、原因与修改

- 用户报告 5554 下载失败，并要求支持保存 APK 后手动安装；只读检查发现 `Unknown URL content://downloads/my_downloads`，确认系统缺少 DownloadProvider；保存文件选择器也不存在。
- `app/src/main/java/com/tvbox/app/data/AppUpdateRepository.kt`：系统下载提交/查询不可用时转应用下载，持久化所用通道与用户选择的手动安装模式，恢复时保留已下载 APK；手动保存不会再次自动弹安装或授权界面。
- `app/src/main/java/com/tvbox/app/data/UpdateDownloadService.kt`：新增非导出的 dataSync 前台服务作为备用通道，显示下载通知、独立于 UI 观察者运行；分块写入临时文件，大小及 SHA-256 校验通过后才改为完整 APK，结束或失败停止服务。任务中断后重启可重新下载，未实现 HTTP 分段续传。
- `app/src/main/java/com/tvbox/app/data/UpdateApkFiles.kt`：共用 APK 大小及 SHA-256 校验，下载与导出仅使用完整可信文件。
- `app/src/main/java/com/tvbox/app/data/UpdateApkExporter.kt`：优先保存到用户通过系统选择的文档位置；选择器不可用时保存到 `Download/TVBox`，Android 10+ 使用 MediaStore，Android 9 请求存储权限；不覆盖已有文件，复制校验并清理本次创建的失败文件。
- `app/src/main/AndroidManifest.xml`：声明前台服务及 dataSync 权限；新增仅 API 28 及以下使用的外部存储写入权限，用于公共目录导出，不用于私有下载。
- `app/src/main/java/com/tvbox/app/MainActivity.kt`：增加系统保存文件选择器与旧版存储权限回调，保留旋转/重建时的待保存路径。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`、`ui/TvBoxApp.kt`：新增保存状态、结果说明及小窗/更新弹窗保存按钮，保存过程阻止重复点击和自动安装争抢。
- 临时方案：无。系统未允许文件管理器安装时，手动安装仍会受到系统限制。

### 构建与定向复查

- 构建签名 Release 测试包为 `10310 / 1.3.10`，与用户正在测试的设备版本一致；源码版本在产物生成后恢复 1.3.11。
- APK：`app/build/outputs/apk/release/TVBox-v1.3.10-ota-download-export-test.apk`，5034437 字节；SHA-256 `10c915f4cba48e87ec2dd9edf107846bc81cd399b121cf5e086f1c6c9fcdea86`。
- 用户本轮指定 5554 查看下载问题，定向复查完成：覆盖安装 Success、备用下载成功、大小/SHA 校验一致、自动显示系统安装确认页、取消安装后成功保存到公共下载目录。
- 导出位置：`/storage/emulated/0/Download/TVBox/TVBox-v1.3.11.apk`；文件为线上现有 1.3.11，SHA-256 `4ab85ed43e9d5fe91b0a2b525161dd6eaff1334de590a369899b395916848333`。没有确认安装这个线上包，设备保留修复测试版。
- 不执行其他功能回归或单元测试；未验证系统选择器和 Android 10+ 导出分支，未操作 192.168.0.8。完整证据与边界见 `docs/validation/2026-10-08-ota-download-export.md`。
- 主日志：`devLog/README.md`；未提交、推送或发布。

## 2026-10-08 10:28 - 降低本地测试版本以触发现有 OTA

- 原因：此前生成 `10311 / 1.3.11` 包，线上最新也是 10311，无法实际触发 OTA 检测；用户要求生成 1.3.10 自行验收，再决定生成 1.3.11 修复版。
- 修改：临时调整 `app/build.gradle.kts` 为 `10310 / 1.3.10`，其余代码保持最新 OTA 授权修复，不回退到旧发布源码。构建产物生成后恢复源码版本至 `10311 / 1.3.11`。
- 测试路径：安装本轮本地 APK → 检测并下载线上 1.3.11 → 检查授权引导、授权返回续接和系统安装界面。线上现有 1.3.11 不含本次 OTA 修复，确认安装会替换掉本地修复测试包；可先验证到安装确认页。
- 安装限制：同包名低 versionCode 不能正常覆盖更高版本；若卸载后重装，会丢失本机数据。可在低版本/未安装应用的设备上测试；另一方案是独立包名的 OTA 测试应用，与正式版并存，本轮未生成该替代包。
- 仅执行构建及产物版本/签名校验，用户自行测试；不操作 ADB，不变更线上更新清单、不发布、不提交或推送。
- `:app:assembleRelease` 构建通过；本地 APK：`app/build/outputs/apk/release/TVBox-v1.3.10-ota-permission-test.apk`。
- 包内版本已核对为 `10310 / 1.3.10`；v2 签名通过，沿用正式签名证书；大小 `5017853` 字节，SHA-256 `e514658be77b3198d3d45de2e399585ae1c9ef995ffac22908813c0a0ef12819`。
- 读取 GitHub main 的线上清单确认目标为 `10311 / 1.3.11`，APK SHA-256 仍为 `4ab85ed43e9d5fe91b0a2b525161dd6eaff1334de590a369899b395916848333`，没有本次未发布的 OTA 授权修复。
- 产物复制完成后源码版本已恢复为 `10311 / 1.3.11`；等待用户验收后再生成对应修复版。中文日志 UTF-8 BOM 读回验证通过；主日志：`devLog/README.md`。

## 2026-10-08 10:17 - 安装权限待处理状态与自动续接

### 缺陷记录

- 现象：小窗下载完成未显示安装界面，点击安装时提示无法打开安装权限设置。
- 原因：`installUpdateApk` 在检查文件、权限和启动安装器之前就持久化 `installPrompted=true`；跳转失败也会消费自动安装机会。原权限跳转仅尝试应用专属授权页和安全页，失败异常被忽略。
- 已确认 Manifest 和正式 APK 包含 `REQUEST_INSTALL_PACKAGES`；系统授权仍需用户允许，不能通过添加声明自行授予权限。
- 修复时间：2026-10-08 10:17；临时方案：无。

### 文件、原因与目的

- `app/src/main/java/com/tvbox/app/MainActivity.kt`：先消费一次 UI 事件，仅在系统安装器成功启动后记录已触发；权限页增加应用专属/通用未知来源、应用详情、安全、应用管理和系统设置回退，记录每个入口的异常。安装器尝试 ACTION_VIEW 与 ACTION_INSTALL_PACKAGE，携带 APK URI 读取授权及 ClipData，并捕获文件共享异常。
- `app/src/main/java/com/tvbox/app/data/AppUpdateRepository.kt`：下载任务新增等待安装授权标记，旧数据默认兼容；等待授权不重复自动打开设置，授权状态和安装器启动状态分开保存；失效安装包可清除任务引用重新下载，不批量删除文件。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：管理等待授权、安装失败和授权后续接；任务状态串行写入，防止晚到的“等待授权”覆盖已成功启动的状态。下载恢复读取授权等待状态，保留已校验 APK。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`：小窗和弹窗显示等待授权及重试安装入口，小窗持续展示失败原因，不仅依赖短暂 Toast；等待授权时额外提供“系统设置”入口，方便专属页面没有授权选项的设备手动查找。
- `devLog/README.md`、本日志：记录缺陷、修复范围、构建产物及验收边界。

### 状态与交付边界

- 下载完成且前台：已授权则打开安装器；未授权则引导设置并保留任务。
- 设置返回：仅在 Activity 恢复前台且重新检查通过时安装；即使厂商设置页未返回标准回调，也由前台状态观察补齐。
- 未授权返回：显示等待授权，不反复弹设置；手动授权后回到应用可续接。安装器取消后保留手动安装入口，不反复自动打开安装器。
- 设置或安装器启动失败：记录 `TVBoxUpdate` 日志，保留 APK 和重试入口。系统未开放安装权限时仍需厂商支持的授权方式，不宣称绕过系统限制。
- 按用户既定分工不执行单元/设备功能测试，不操作 ADB；仅编译构建及校验签名、版本、产物信息。
- 保持 `10311 / 1.3.11`，不更新 GitHub Release 或 OTA 清单；同版本本地包需手动覆盖安装，修复用于安装该包后的更新流程。
- `:app:assembleRelease` 构建通过（包含构建自带的 vital lint）；未运行测试或连接设备。
- 本地 APK：`app/build/outputs/apk/release/TVBox-v1.3.11-ota-permission-test.apk`；`5017853` 字节；SHA-256 `a3b72606f342660993a93bce65bf0a8215f7d0a277636603911c65b3f7e4f2d5`。
- v2 签名校验通过，证书 SHA-256 `7244ed4db1ee7488c98d1df3c80b2ccacf72e7983aca3f6341c6268ae1dd8b09`；包内版本 `10311 / 1.3.11`，最低 API 28，包含 `REQUEST_INSTALL_PACKAGES`。
- 中文源码和日志均使用 UTF-8 BOM 并读回验证。主日志：`devLog/README.md`。

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
