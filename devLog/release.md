# Release - 2026-06-25

## 2026-08-07 13:45 - 发布 v1.3.4：豆瓣热播首页

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 热播首页功能完成后需要提供可被 OTA 识别的正式版本。
  - Purpose: 将版本更新为 `1.3.4 / 10304`。

- File path: `CHANGELOG.md`、`README.md`、`update.json`
  - Reason: 用户安装、OTA 检测和发布说明必须指向同一个版本化 APK。
  - Purpose: 记录热播功能，并写入 v1.3.4 APK 的下载地址、SHA-256 与文件大小。

- File path: `app/build/outputs/apk/release/TVBox-v1.3.4.apk`
  - Reason: GitHub Release 需要稳定的版本化安装包。
  - Purpose: 保存已签名 v1.3.4 APK，供用户安装和 OTA 下载。

- File path: `devLog/README.md`、`devLog/douban-hot-api.md`、`devLog/release.md`
  - Reason: 需要可追溯地记录接口实现、设备测试与发布结果。
  - Purpose: 登记本次功能、验证边界和完整性数据。

## Bug Record

- Time: 2026-08-07 13:45
- Symptoms: 无。
- Attempted fix: 不适用；本次为功能发布。
- Temporary solution: 无。

## Verification

- `./gradlew.bat testDebugUnitTest assembleRelease`
  - Result: passed.
- Android 9 设备：成功覆盖安装 `v1.3.4 / 10304`，豆瓣热播首屏返回 `243` 部；点击“这一秒过火”通过量子线路进入详情页；返回后仍为热播页；加载更多后显示 `40 / 243` 部。
- `apksigner verify --print-certs app\build\outputs\apk\release\TVBox-v1.3.4.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`。
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.3.4.apk`
  - Size: `4853929`
  - SHA-256: `3875d84023e5b620536f7619ee2989a62f07eddc141ee1bad0e27891c898d74d`

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-08-07 07:50 - 发布 v1.3.3：修复启动闪退

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: v1.3.2 已发布但无法启动，必须提高版本号让已安装用户收到 OTA 修复。
  - Purpose: 将 `versionCode` 更新为 `10303`，将 `versionName` 更新为 `1.3.3`。

- File path: `CHANGELOG.md`
  - Reason: 用户需要明确知道本版本是紧急启动修复。
  - Purpose: 记录字体缩放崩溃修复和回归测试。

- File path: `README.md`
  - Reason: 当前安装、OTA 示例和手动发布命令应对应修复版。
  - Purpose: 更新版本、下载地址和 APK 校验值。

- File path: `update.json`
  - Reason: 旧版应用依赖 OTA 清单识别高版本修复。
  - Purpose: 指向 v1.3.3 Release APK，并提供实际 SHA-256 与文件大小。

- File path: `app/build/outputs/apk/release/TVBox-v1.3.3.apk`
  - Reason: GitHub 与 Gitee Release 需要稳定的版本化安装包。
  - Purpose: 保存已签名 v1.3.3 APK，供用户安装和 OTA 下载。

- File path: `devLog/README.md`
  - Reason: 主时间线需要记录线上版本问题和修复结果。
  - Purpose: 登记根因和设备验证结果。

- File path: `devLog/release.md`
  - Reason: 发布模块需要保留 v1.3.3 的完整性数据。
  - Purpose: 记录紧急修复发布准备、构建和签名验证。

## Bug Record

- Time: 2026-08-07 07:41
- Symptoms: v1.3.2 打开后立即闪退，无法进入首页。
- Attempted fix: 修正默认 `TextUnit.Unspecified` 的字体缩放逻辑，并添加回归测试。
- Temporary solution: 无，v1.3.3 为正式修复版本。

## Verification

- `./gradlew.bat testDebugUnitTest assembleRelease --console=plain --no-daemon`
  - Result: passed.
- Android 设备：安装 `v1.3.3 / 10303` 后，`MainActivity` 前台运行，PID 存在，无新崩溃日志。
- `apksigner verify --print-certs app\build\outputs\apk\release\TVBox-v1.3.3.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`。
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.3.3.apk`
  - Size: `4837545`
  - SHA-256: `5896f3528961df579af6ee7dea95d610b53770196d8007b2a099914053756ff2`
- GitHub 和 Gitee 将在 commit 与 tag 推送后同步 v1.3.3 代码和发布资产。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-08-06 22:45 - 发布 v1.3.2：字体大小与卡片布局适配

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 字体与布局设置完成后需要发布独立 Android 版本，供已有设备通过 OTA 识别更新。
  - Purpose: 将 `versionCode` 更新为 `10302`，将 `versionName` 更新为 `1.3.2`。

- File path: `CHANGELOG.md`
  - Reason: 用户需要知道本版本的可见功能变化。
  - Purpose: 登记字体大小、全局文字缩放和网格卡片适配。

- File path: `README.md`
  - Reason: 安装、OTA 示例和手动发布命令应对应当前正式版本。
  - Purpose: 更新版本、APK 文件名、下载地址、校验值和发布命令。

- File path: `update.json`
  - Reason: 已安装的旧版应用依赖该清单发现 v1.3.2 并校验下载结果。
  - Purpose: 指向 v1.3.2 Release APK，提供实际 SHA-256 与文件大小。

- File path: `app/build/outputs/apk/release/TVBox-v1.3.2.apk`
  - Reason: GitHub 和 Gitee Release 需要明确、稳定的版本化安装包名称。
  - Purpose: 保存 v1.3.2 已签名 Release APK，供上传作为发布资产。

- File path: `devLog/README.md`
  - Reason: 主开发时间线需要可追溯本次功能与发布。
  - Purpose: 登记 v1.3.2 的功能范围与构建验证。

- File path: `devLog/release.md`
  - Reason: 发布模块需要保留版本、APK 完整性数据和验证结果。
  - Purpose: 记录 v1.3.2 的正式发布准备工作。

## Bug Record

- Time: 2026-08-06 22:45
- Symptoms: 无。本次为正常功能版本发布。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat testDebugUnitTest assembleRelease --console=plain --no-daemon`
  - Result: passed.
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\TVBox-v1.3.2.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`。
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.3.2.apk`
  - Size: `4837545`
  - SHA-256: `9d35fa39208ce3bf0620677b7367d8a90964470a51dee48f95fe431715224ec5`
- GitHub 和 Gitee 将在 commit 与 tag 推送后上传 APK、`update.json`。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-08-03 08:40 - v1.3.1 带平台直播服务地址覆盖构建

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: Release APK 需要连接统一平台直播服务。
  - Purpose: 使用构建参数 `TVBOX_PLATFORM_LIVE_SERVICE_URL=http://20.205.10.127:8868` 注入 `BuildConfig.PLATFORM_LIVE_SERVICE_URL`。
- File path: `update.json`
  - Reason: 代理下载地址和带服务配置的 APK 需要同步到 OTA 清单。
  - Purpose: 将 `apkUrl` 改为 `https://gh-proxy.org/` 前缀，并更新 APK SHA-256。
- File path: `README.md`
  - Reason: OTA 示例必须与实际清单和 Release 资产保持一致。
  - Purpose: 更新代理下载地址、校验值和说明。
- File path: `devLog/README.md`
  - Reason: 需要记录同版本覆盖构建的配置和发布动作。
  - Purpose: 在主时间线登记带平台直播地址的 v1.3.1 覆盖构建。
- File path: `devLog/release.md`
  - Reason: 发布记录需要保留本次 APK 配置、校验值和资产更新结果。
  - Purpose: 记录带平台直播服务地址的 v1.3.1 覆盖发布。

## Bug Record

- Time: 2026-08-03 08:40
- Symptoms: 原 v1.3.1 APK 未注入平台直播服务地址，平台直播无法请求统一服务。
- Attempted fix: 使用 `TVBOX_PLATFORM_LIVE_SERVICE_URL=http://20.205.10.127:8868` 重新构建并覆盖 Release 资产。
- Temporary solution: 无。

## Verification

- `.\gradlew.bat testDebugUnitTest assembleRelease --console=plain --no-daemon`
  - Result: passed.
- Release `BuildConfig.PLATFORM_LIVE_SERVICE_URL`：`http://20.205.10.127:8868`。
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\app-release.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`。
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.3.1.apk`
  - Size: `4821161`
  - SHA-256: `29036c8f5f06b5a81c694aa9cb62c2ad6f418acf314a4001581204ea0229d947`
- OTA APK URL: `https://gh-proxy.org/https://github.com/ZxxXinI/tvbox/releases/download/v1.3.1/TVBox-v1.3.1.apk`
- GitHub v1.3.1 Release 资产将覆盖为本次带服务地址的 APK 和 `update.json`。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-08-03 08:00 - 发布 v1.3.1

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 发布默认主题 / 影院主题和直播焦点修复，需要提升 Android 版本号。
  - Purpose: 将 `versionCode` 设置为 `10301`，将 `versionName` 设置为 `1.3.1`。
- File path: `CHANGELOG.md`
  - Reason: 新版本需要记录用户可见的主题、导航和遥控器改动。
  - Purpose: 增加 v1.3.1 更新说明。
- File path: `README.md`
  - Reason: 安装、OTA 和发布示例仍指向 v1.3.0。
  - Purpose: 更新当前版本、安装示例和 v1.3.1 发布命令。
- File path: `update.json`
  - Reason: OTA 需要识别并下载 v1.3.1。
  - Purpose: 更新版本号、Release APK 地址和 v1.3.1 变更说明，构建后写入校验值。
- File path: `devLog/README.md`
  - Reason: 用户要求记录每次发布和源码变更。
  - Purpose: 在主时间线登记 v1.3.1 发布。
- File path: `devLog/release.md`
  - Reason: 发布流程需要保留版本、资产和验证记录。
  - Purpose: 记录 v1.3.1 发布涉及的文件和后续构建结果。

## Bug Record

- Time: 2026-08-03 08:00
- Symptoms: 无新增缺陷；本次为正常版本发布。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest assembleRelease --console=plain --no-daemon`
  - Result: passed.
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\app-release.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`。
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.3.1.apk`
  - Size: `4821161`
  - SHA-256: `d79636cbdbcb1031aae4a6ccf412106744b2700b197cc74c4db2b3dd41a19ca2`
- GitHub Release 将在 commit 和 tag 推送后创建并上传 APK、`update.json`。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-08-02 - v1.3.0 源码版本升级

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 多平台直播接入完成，需要提升 Android 版本号。
  - Purpose: 将 `versionCode` 设置为 `10300`，将 `versionName` 设置为 `1.3.0`。
- File path: `CHANGELOG.md`
  - Reason: 需要记录五平台直播和服务端部署能力。
  - Purpose: 增加 v1.3.0 变更说明。
- File path: `README.md`
  - Reason: 当前 README 缺少平台直播服务部署和构建地址说明。
  - Purpose: 增加五平台直播说明、部署文档链接和 `TVBOX_PLATFORM_LIVE_SERVICE_URL` 构建示例。

## Bug Record

- Time: 2026-08-02
- Symptoms: 无新增缺陷。本次为源码版本升级和部署文档同步。
- Attempted fix: 不适用。
- Temporary solution: 本次暂不更新 `update.json`，避免 OTA 指向尚未发布的 v1.3.0 Release。

## Verification

- 使用 `TVBOX_PLATFORM_LIVE_SERVICE_URL=http://20.205.10.127:8868` 完成 Debug 构建参数验证。
- 未发布 GitHub Release，待后续正式发布时再更新 `update.json` 和 Release 资产。

## 2026-08-02 11:23 - OTA 更新地址切回 GitHub

## File Changes

- File path: `app/src/main/java/com/tvbox/app/data/AppUpdateRepository.kt`
  - Reason: 当前 OTA 更新检测仍然请求 Gitee 地址。
  - Purpose: 将更新清单地址切换为 GitHub raw 的 `main/update.json`。

- File path: `README.md`
  - Reason: OTA 地址、安装来源和发布流程仍包含 Gitee 的当前说明。
  - Purpose: 统一记录 GitHub OTA 清单和 GitHub Release APK 发布方式。

## Bug Record

- Time: 2026-08-02 11:23
- Symptoms: 应用和文档的当前 OTA 入口仍指向 Gitee。
- Attempted fix: 切换应用清单地址和 README 当前发布说明至 GitHub。
- Temporary solution: 无。

## Verification

- `:app:testDebugUnitTest`：通过。
- 活动项目代码、README 和 `update.json` 中的当前 OTA 地址已核对为 GitHub。

## 2026-07-08 19:58 - 发布 v1.2.10

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 需要发布包含启动权限、直播手势和播放器颜色调整的正式版本。
  - Purpose: 将版本号升级到 `1.2.10`，版本码升级到 `10210`。

- File path: `CHANGELOG.md`
  - Reason: 新版本需要记录用户可见更新内容。
  - Purpose: 增加 v1.2.10 更新说明。

- File path: `README.md`
  - Reason: 安装和发布示例需要跟随当前版本。
  - Purpose: 更新 APK 文件名、版本号、校验值和 Release 命令示例。

- File path: `update.json`
  - Reason: OTA 需要识别 v1.2.10 并下载对应 APK。
  - Purpose: 更新版本码、版本名、APK 地址、SHA-256、大小和更新说明。

- File path: `app/build/outputs/apk/release/TVBox-v1.2.10.apk`
  - Reason: GitHub Release 需要上传正式安装包。
  - Purpose: 保存 v1.2.10 release APK，供用户下载安装和 OTA 使用。

- File path: `app/build/outputs/apk/release/release-notes-v1.2.10.md`
  - Reason: GitHub Release 需要清晰的中文版本说明。
  - Purpose: 记录 v1.2.10 发布内容和验证结果。

- File path: `devLog/README.md`
  - Reason: 用户要求每次修改后记录做了什么、为什么做。
  - Purpose: 在主时间线登记 v1.2.10 发布。

- File path: `devLog/release.md`
  - Reason: 本次属于正式发布任务。
  - Purpose: 记录版本升级、发布资产、校验值和验证命令。

## Bug Record

- Time: 2026-07-08 19:58
- Symptoms: 无。本次为正常版本发布。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat testDebugUnitTest assembleRelease --console=plain`
  - Result: passed.
- `Get-FileHash app/build/outputs/apk/release/TVBox-v1.2.10.apk -Algorithm SHA256`
  - Result: `b74370f955f1741040267e3efc17c1a50e6750ddfd70be65325d779751eac2b3`.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`
## 2026-07-08 08:00 - 发布 v1.2.9

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 需要发布包含新 UI 风格的正式版本。
  - Purpose: 将版本号升级到 `1.2.9`，版本码升级到 `10209`。

- File path: `CHANGELOG.md`
  - Reason: 新版本需要记录用户可见更新内容。
  - Purpose: 增加 v1.2.9 更新说明，聚焦影院黑 + 活力绿 UI 风格。

- File path: `README.md`
  - Reason: 安装和发布示例需要跟随当前版本。
  - Purpose: 更新 APK 文件名、版本号、校验值和 Release 命令示例。

- File path: `update.json`
  - Reason: OTA 需要识别 v1.2.9 并下载对应 APK。
  - Purpose: 更新版本码、版本名、APK 地址、SHA-256、大小和更新说明。

- File path: `app/build/outputs/apk/release/TVBox-v1.2.9.apk`
  - Reason: GitHub Release 需要上传正式安装包。
  - Purpose: 保存 v1.2.9 release APK，供用户下载安装和 OTA 使用。

- File path: `app/build/outputs/apk/release/release-notes-v1.2.9.md`
  - Reason: GitHub Release 需要清晰的中文版本说明。
  - Purpose: 记录 v1.2.9 发布内容和验证结果。

- File path: `app/build/outputs/apk/release/update.json`
  - Reason: GitHub Release 附件需要携带对应版本 OTA 清单。
  - Purpose: 与根目录 `update.json` 保持一致，便于手动或备份发布。

- File path: `devLog/README.md`
  - Reason: 用户要求每次修改后记录做了什么、为什么做。
  - Purpose: 在主时间线登记 v1.2.9 发布。

- File path: `devLog/release.md`
  - Reason: 本次属于正式发布任务。
  - Purpose: 记录版本升级、发布资产、校验值和验证命令。

## Bug Record

- Time: 2026-07-08 08:00
- Symptoms: 无。本次为正常版本发布。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat testDebugUnitTest assembleRelease --console=plain`
  - Result: passed.
- `Get-FileHash app/build/outputs/apk/release/TVBox-v1.2.9.apk -Algorithm SHA256`
  - Result: `68f5c2e9b5763b4ab4b245f623932dd45d68e0ff51961c3ddf45548f2e048901`.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`
## 2026-07-01 18:09 - 接入 Gitee OTA

## File Changes

- File path: `app/src/main/java/com/tvbox/app/data/AppUpdateRepository.kt`
  - Reason: GitHub 直连和代理下载在电视盒子上速度不稳定，需要切换到国内可访问的 OTA 清单地址。
  - Purpose: 将应用更新检测地址改为 `https://gitee.com/zhen-xin/tv-box/raw/agent/update.json`。
- File path: `update.json`
  - Reason: OTA 清单需要随 Gitee 仓库同步，避免依赖 GitHub Release 的 `latest/download` 地址。
  - Purpose: 新增根目录更新清单，当前指向 Gitee Release 的 `TVBox-v1.2.8.apk`。
- File path: `README.md`
  - Reason: 发布流程需要说明 GitHub 与 Gitee 双远端、Gitee OTA 清单和 Gitee Release APK。
  - Purpose: 更新安装、OTA 和发布命令说明。
- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 Gitee OTA 接入索引。
- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录 OTA 地址切换原因。
  - Purpose: 记录 Gitee OTA 接入涉及的文件、原因和后续发布方式。

## Bug Record

- Time: 2026-07-01 18:09
- Symptoms: GitHub 下载慢，S3 存储不可用。
- Attempted fix: 改用 Gitee raw 承载 `update.json`，Gitee Release 承载 APK。
- Temporary solution: GitHub Release 继续作为备份发布渠道。

## Verification

- `Get-Content -Raw -Encoding UTF8 update.json | ConvertFrom-Json`
  - Result: passed. Root `update.json` is valid JSON.
- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`
## 2026-07-01 18:32 - 校正 Gitee v1.2.8 下载地址

## File Changes

- File path: `update.json`
  - Reason: Gitee Release tag 已改为 `v1.2.8`，OTA APK 地址需要与正式版本号保持一致。
  - Purpose: 将当前 OTA APK 地址设置为 `/releases/download/v1.2.8/TVBox-v1.2.8.apk`。
- File path: `devLog/release.md`
  - Reason: 发布流程需要记录本次 Gitee 链路验证结果。
  - Purpose: 记录 Gitee raw 清单和 APK 下载地址验证结果。

## Bug Record

- Time: 2026-07-01 18:32
- Symptoms: Gitee Release tag 已从 `1.28` 调整为 `v1.2.8`。
- Attempted fix: 将 `update.json` 中的 `apkUrl` 改回正式版本 tag `v1.2.8`。
- Temporary solution: 后续保持 Gitee Release tag 与应用版本一致。

## Verification

- `Invoke-WebRequest -Uri "https://gitee.com/zhen-xin/tv-box/raw/agent/update.json"`
  - Result: HTTP 200.
- `Invoke-WebRequest -Uri "https://gitee.com/zhen-xin/tv-box/releases/download/v1.2.8/TVBox-v1.2.8.apk" -Method Head`
  - Result: HTTP 200.
- `git ls-remote gitee "refs/tags/*"`
  - Result: `v1.2.8` tag exists on Gitee.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-07-01 18:03 - 撤回 S3 发布流程

## File Changes

- File path: `scripts/publish-release-assets.ps1`
  - Reason: S3 存储端上传不可用，继续维护 S3 上传脚本会增加发布复杂度。
  - Purpose: 删除 S3 发布脚本，避免后续发版误用不可用链路。
- File path: `README.md`
  - Reason: README 当前仍描述 S3 下载 APK 的流程，但实际链路不可用。
  - Purpose: 恢复为 GitHub Release 上传 APK 和 `update.json` 的说明。
- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入撤回 S3 发布流程索引。
- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录方案撤回原因。
  - Purpose: 记录 S3 发布脚本删除、README 恢复和后续 Gitee 方向。

## Bug Record

- Time: 2026-07-01 18:03
- Symptoms: S3 上传不可用，V4 鉴权返回 401，V2 尝试返回服务端 500。
- Attempted fix: 删除当前 S3 发布脚本和 README 中的 S3 发布说明。
- Temporary solution: 继续使用 GitHub Release；后续可切换到 Gitee 承载 OTA 清单和 APK。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-07-01 10:40 - S3 发布流程

## File Changes

- File path: `scripts/publish-release-assets.ps1`
  - Reason: GitHub Release 下载 APK 在电视盒子上速度不稳定，即使代理也可能很慢。
  - Purpose: 新增发布脚本，生成指向 S3 的 `update.json`，并内置 AWS Signature V4 上传能力，支持把 APK 上传到 S3、把 APK 和 `update.json` 上传到 GitHub Release。
- File path: `README.md`
  - Reason: 发布链路从单纯 GitHub 下载改为 GitHub 获取清单、S3 下载 APK。
  - Purpose: 说明 S3 本地配置、`update.json` 示例、DryRun、正式上传命令，以及无需安装 AWS CLI。
- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 S3 发布流程索引。
- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录脚本、文档和验证结果。
  - Purpose: 记录 S3 发布流程涉及的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-07-01 10:40
- Symptoms: 无新增缺陷；本次为发布链路优化。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\publish-release-assets.ps1 -VersionName 1.2.8 -VersionCode 10208 -S3Bucket c68393c9e4fe40e88ec2a07527326176 -DryRun`
  - Result: passed. Generated S3 `apkUrl` and local `update.json`; upload was intentionally skipped.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\publish-release-assets.ps1 -VersionName 1.2.8 -VersionCode 10208`
  - Result: first run failed before upload because Windows PowerShell had not loaded `System.Net.Http`; script now loads the assembly explicitly before creating `HttpClient`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\publish-release-assets.ps1 -VersionName 1.2.8 -VersionCode 10208 -NoAcl`
  - Result: upload reached S3 but the connection failed while streaming content; script now sends a byte array body with explicit `Content-Length`.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-07-01 09:36 - v1.2.8

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 发布新版本需要提高应用版本号，确保 OTA 能识别为可更新版本。
  - Purpose: 将 `versionCode` 升级到 `10208`，将 `versionName` 升级到 `1.2.8`。

- File path: `CHANGELOG.md`
  - Reason: 已完成的自定义视频接口、手机播放手势和首页滚动体验需要归档到正式版本。
  - Purpose: 新增 `v1.2.8 - 2026-07-01` 发布记录，并清空未发布条目。

- File path: `README.md`
  - Reason: README 中安装示例、OTA 示例和发布命令仍指向旧版本。
  - Purpose: 更新示例版本到 `v1.2.8` / `10208`，并同步 release APK 的 SHA-256 与大小。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 v1.2.8 发布索引。

- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录版本、资产和验证结果。
  - Purpose: 记录 v1.2.8 发布涉及的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-07-01 09:36
- Symptoms: 无新增缺陷；本次为版本发布整理。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug assembleRelease --console=plain`
  - Result: passed.
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\TVBox-v1.2.8.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`.
- `git diff --check`
  - Result: passed. Only line-ending warnings were reported.
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.2.8.apk`
  - Size: `4721013`
  - SHA-256: `598bef37d28f16898991395ea2a89e092c6320908f82ca381852d4e1403ab030`
- GitHub Release upload will be performed after commit/tag so release assets match committed source.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-06-28 20:56 - v1.2.7

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 发布新版本需要提高应用版本号，确保 OTA 能识别为可更新版本。
  - Purpose: 将 `versionCode` 升级到 `10207`，将 `versionName` 升级到 `1.2.7`。

- File path: `CHANGELOG.md`
  - Reason: 设置页大模型配置和手机扫码配置能力需要归档到正式版本。
  - Purpose: 新增 `v1.2.7 - 2026-06-28` 发布记录，并清空未发布条目。

- File path: `README.md`
  - Reason: README 中安装示例、OTA 示例和功能说明仍指向旧版本。
  - Purpose: 更新示例版本到 `v1.2.7` / `10207`，并说明手机扫码配置模型名称和 API Key。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 v1.2.7 发布索引。

- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录版本、资产和验证结果。
  - Purpose: 记录 v1.2.7 发布涉及的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-28 20:56
- Symptoms: 无新增缺陷；本次为版本发布整理。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleRelease --console=plain`
  - Result: passed.
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\TVBox-v1.2.7.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`.
- `git diff --check`
  - Result: passed. Only line-ending warnings were reported.
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.2.7.apk`
  - Size: `4671861`
  - SHA-256: `f1fb17d27f90ecd853382d103a004e03a44395d26d6c8e18d0ab6000763fb088`
- GitHub Release upload will be performed after commit/tag so release assets match committed source.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-06-28 17:35 - 覆盖发布 v1.2.6

## File Changes

- File path: `CHANGELOG.md`
  - Reason: 首页资源切换和 AI 焦点优化需要归入当前 v1.2.6 说明。
  - Purpose: 在 `v1.2.6 - 2026-06-28` 版本块补充本次覆盖发布内容。
- File path: `README.md`
  - Reason: 同一个 v1.2.6 APK 重新构建后 SHA-256 与大小发生变化。
  - Purpose: 同步 `update.json` 示例中的 `apkSha256` 和 `apkSize`。
- File path: `devLog/release.md`
  - Reason: 用户要求本次更新仍发布为 v1.2.6，不新增版本号。
  - Purpose: 记录同版本覆盖发布的产物信息和验证结果。

## Verification

- `.\gradlew.bat assembleRelease --console=plain`
  - Result: passed.
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.2.6.apk`
  - Size: `4622709`
  - SHA-256: `406bdea9a3b0a107790945d3bba1d79f81c46171f765068ef7a2c99feb4ac9e8`
- GitHub Release asset upload:
  - Will be performed after commit/tag update so assets match committed source.

## Bug Record

- Time: 2026-06-28 17:35
- Symptoms: 无新增缺陷；本次为同版本覆盖发布。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-06-28 17:01 - v1.2.6

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 发布新版本需要提高应用版本号，确保 OTA 能识别为可更新版本。
  - Purpose: 将 `versionCode` 升级到 `10206`，将 `versionName` 升级到 `1.2.6`。

- File path: `CHANGELOG.md`
  - Reason: 已完成的 AI 找片和语音修复需要归档到正式版本。
  - Purpose: 新增 `v1.2.6 - 2026-06-28` 发布记录，并清空未发布条目。

- File path: `README.md`
  - Reason: README 中安装示例、OTA 示例和发布命令仍指向旧版本。
  - Purpose: 更新示例版本到 `v1.2.6` / `10206`，并在构建后同步 release APK 的 SHA-256 与大小。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 v1.2.6 发布索引。

- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录版本、资产和验证结果。
  - Purpose: 记录 v1.2.6 发布涉及的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-28 17:01
- Symptoms: 无新增缺陷；本次为版本发布整理。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleRelease --console=plain`
  - Result: passed.
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\TVBox-v1.2.6.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`.
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.2.6.apk`
  - Size: `4606325`
  - SHA-256: `3a375705659488a45e40dd20d9abb986b84c457518168351b6f29c5b2e762937`
- GitHub Release upload will be performed after commit/tag so release assets match committed source.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`

## 2026-06-25 08:22 - v1.2.5

## File Changes

- File path: `app/build.gradle.kts`
  - Reason: 发布新版本需要提高应用版本号，确保 OTA 能识别为可更新版本。
  - Purpose: 将 `versionCode` 升级到 `10205`，将 `versionName` 升级到 `1.2.5`。

- File path: `CHANGELOG.md`
  - Reason: 已完成的播放管家和 OTA 权限改动需要归档到正式版本。
  - Purpose: 新增 `v1.2.5 - 2026-06-25` 发布记录，并清空未发布条目。

- File path: `README.md`
  - Reason: README 中安装示例、OTA 示例和发布命令仍指向旧版本。
  - Purpose: 更新示例版本到 `v1.2.5` / `10205`，并同步 release APK 的 SHA-256 与大小。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入 v1.2.5 发布索引。

- File path: `devLog/release.md`
  - Reason: 发布流程是独立维护主题，需要记录版本、资产和验证结果。
  - Purpose: 记录 v1.2.5 发布涉及的文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-25 08:22
- Symptoms: 无新增缺陷；本次为版本发布整理。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleRelease --console=plain`
  - Result: passed.
- `E:\Soft\Tools\AndroidSDK\build-tools\36.1.0\apksigner.bat verify --print-certs app\build\outputs\apk\release\TVBox-v1.2.5.apk`
  - Result: passed. Certificate DN: `CN=TVBox, OU=TVBox, O=TVBox, L=Unknown, ST=Unknown, C=CN`.
- Release asset:
  - APK: `app/build/outputs/apk/release/TVBox-v1.2.5.apk`
  - Size: `4573449`
  - SHA-256: `54a1d50f713d9b1d8dac2383d493c5f2b767dbc90980ae0e6f841d61a9c844ef`

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/release.md`
