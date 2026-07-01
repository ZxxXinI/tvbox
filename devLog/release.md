# Release - 2026-06-25

## 2026-07-01 10:40 - S3 发布流程

## File Changes

- File path: `scripts/publish-release-assets.ps1`
  - Reason: GitHub Release 下载 APK 在电视盒子上速度不稳定，即使代理也可能很慢。
  - Purpose: 新增发布脚本，生成指向 S3 的 `update.json`，并支持把 APK 上传到 S3、把 APK 和 `update.json` 上传到 GitHub Release。
- File path: `README.md`
  - Reason: 发布链路从单纯 GitHub 下载改为 GitHub 获取清单、S3 下载 APK。
  - Purpose: 说明 S3 本地配置、`update.json` 示例、DryRun 和正式上传命令。
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
