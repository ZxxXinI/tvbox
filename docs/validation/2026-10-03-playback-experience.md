# 播放体验验收报告（2026-10-03）

## 环境和范围

- ADB：`emulator-5554`；Android 9 / API 28，1600×900，设备系统报告手机特性。
- 使用注入的遥控器按键和实际触摸测试；未使用物理电视遥控器。
- 测试前设备未安装 `com.tvbox.app`，不存在需要保留的旧应用数据。
- 受控测试：仅 Debug 的 AcceptanceActivity 调用本轮正式业务页面；本地 FFmpeg 生成 120 秒视频，提供两条 MacCms 接口、三集视频及两类直播接口。
- 音频中断：使用临时前台服务辅助 APK 获取/释放音频焦点；真实来电未验证（当前模拟器控制端口不可用）。
- 正常 Release 已覆盖安装到 `emulator-5554`；随后按用户要求覆盖安装到 `192.168.0.8:5555`（API 36，型号 24117RK2CC），返回 `Success`，更新时间 2026-10-03 21:08:23。新设备仅安装，由用户自行测试。

## 最终验证结果

- 66 项 JVM 单元测试全部通过；Debug Lint：0 errors、46 warnings、3 hints；签名 Release 构建通过。
- ADB 功能验收：13 项通过、1 项部分验证。有效结果分布于 `evidence-final-v4`（01–09）、`evidence-final-v5`（10、12）及 `evidence-final-v8`（13、14、11）。
- 通过：确认键操作栏、面板分层返回、10 秒快进、三次间隔重复快进 14→44 秒、数字切集、菜单倍速、暂停换线保留集数/进度、后台手动恢复、音频焦点中断、继续观看和重启保留、首页及搜索返回原焦点、两类直播后台恢复、历史页返回、触摸展开/滑动进度/长按后倍速恢复、影院主题入口。
- 部分验证：暂停播放页的窗口 `KEEP_SCREEN_ON` 存在，退出后释放；原锁屏设置已恢复。ROM 电源服务的有效超时为 2147483647ms，无法确认短超时自动熄屏。
- 真实接口：正常 Release 首页获取豆瓣热播 204 部及 MacCms 分类；电视源加载 74 个频道。CCTV1 在 8 秒观察窗口中仍在缓冲，未确认真实线路稳定播放。该结果与本地可控媒体的播放器验证分开记录。
- 本轮仅生成本地测试包，未升级版本、未提交、未推送或发布。

## 安装包

- 文件：`app/build/outputs/apk/release/TVBox-v1.3.9-playback-test.apk`。
- 包名/版本：`com.tvbox.app`，`10309 / 1.3.9`，最低 API 28。
- 大小：4985001 字节。
- SHA-256：`99adf700ac854c6e587468f0ce7adf6e6bc2d6225974df524a0a9d2e7a381819`。
- 签名证书 SHA-256：`7244ed4db1ee7488c98d1df3c80b2ccacf72e7983aca3f6341c6268ae1dd8b09`。
- Manifest 已确认不包含 Debug 验收入口。
- 测试前确认历史均来自 acceptance 接口，验收后已清理这些测试记录；辅助音频应用、测试端口映射和设备端重复按键辅助文件均已移除，本地接口服务已停止。

## 可重复验收

1. 生成测试视频并运行 `tools/playback_acceptance_fixture.py --video build/acceptance/video.mp4`。
2. `adb -s emulator-5554 reverse tcp:8766 tcp:8766`。
3. 构建 Debug：`gradlew.bat :app:assembleDebug -PTVBOX_SIGN_DEBUG_WITH_RELEASE=true`，并覆盖安装；该属性需要本机现有 Release 签名配置。
4. 使用 `tools/build_acceptance_helpers.ps1` 构建并安装音频焦点/重复按键辅助程序；运行 `tools/playback_adb_acceptance.py --serial emulator-5554`。
5. 最终安装正常配置的 Release 包，停止服务并移除本轮辅助应用和端口映射。

## 证据位置

- 各次验收保存在 `build/acceptance/evidence*`，包含结果 JSON、XML 和截图；保留初次误报与修正后的结果。
- 未验证项目与外部接口实测结果分别记录，不把本地可控接口结果视为线上来源验证。
