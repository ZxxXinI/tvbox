# OTA 下载与安装包保存复查 - 2026-10-08

## 环境与原始故障

- 用户指定设备：`emulator-5554`，Android API 28；应用 `com.tvbox.app`，`10310 / 1.3.10`。
- 原小窗错误：`Unknown URL content://downloads/my_downloads`。
- `content query --uri content://downloads/all_downloads` 返回 `Could not find provider: downloads`，下载组件包查询为空。
- `cmd package resolve-activity --brief -a android.intent.action.CREATE_DOCUMENT -c android.intent.category.OPENABLE -t application/vnd.android.package-archive` 返回 `No activity found`。
- 结论：设备缺少系统下载提供器和文件保存选择器；原失败发生在提交系统下载任务时，还未发起 APK 网络请求。

## 本次定向复查

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 同版本覆盖安装修复测试包 | 通过 | `adb -s emulator-5554 install -r` 返回 Success，保留应用数据 |
| 触发线上 1.3.11 更新 | 通过 | 更新弹窗识别目标 1.3.11，点击立即更新 |
| 系统组件缺失时转应用下载 | 通过 | `TVBoxUpdate` 日志记录系统 Unknown URL 异常和应用下载完成；小窗出现 16% 进度 |
| 下载完整性 | 通过 | APK 大小 5001469 字节，SHA-256 与线上清单一致 |
| 自动打开系统安装器 | 通过 | 系统界面显示“安装来源：TVBox”与安装/取消按钮；选择取消，未覆盖为线上旧版 |
| 无文件选择器时保存到本地 | 通过 | 点击保存安装包，出现 Android 9 存储授权，允许后成功保存到公共下载目录 |
| 保存文件完整性 | 通过 | 导出文件大小与 SHA-256 均与下载原件一致 |
| 下载完成后停止前台服务 | 通过 | `dumpsys activity services com.tvbox.app` 显示无活动服务 |

## 文件与交付

- 下载原件：`/sdcard/Android/data/com.tvbox.app/files/Download/updates/TVBox-10311-1791428855736.apk`。
- 用户可见文件：`/storage/emulated/0/Download/TVBox/TVBox-v1.3.11.apk`。
- 以上两份文件 SHA-256：`4ab85ed43e9d5fe91b0a2b525161dd6eaff1334de590a369899b395916848333`。这是线上现有正式 1.3.11，并非尚未发布的修复版。
- 本地修复测试 APK：`app/build/outputs/apk/release/TVBox-v1.3.10-ota-download-export-test.apk`；5034437 字节；SHA-256 `10c915f4cba48e87ec2dd9edf107846bc81cd399b121cf5e086f1c6c9fcdea86`。
- 修复包沿用正式 Release 证书，签名及包内版本已校验；设备保持修复测试版 `10310 / 1.3.10`。
- 本机证据：`app/build/outputs/ota-validation/2026-10-08/installer-confirmation.xml`、`export-result.xml`、`TVBoxUpdate.log`。

## 验证边界

- 仅按本次用户授权在 5554 复查 OTA 和保存功能，未操作 192.168.0.8，未执行其他功能回归或单元测试。
- 因设备组件缺失，本次未验证系统文件选择器的选择路径、Android 10+ MediaStore 下载目录分支、正常 DownloadManager 分支，以及系统杀进程后的下载恢复。
- 文件管理器发起安装仍需要其获得系统许可；本功能不绕过系统安装授权。
- 源码版本恢复为 `10311 / 1.3.11`，未更改线上清单、发布附件或提交推送。
