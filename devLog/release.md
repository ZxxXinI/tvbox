# Release - 2026-06-25

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
