# 手机扫码输入搜索

## 2026-11-20 10:05 - 扫码配置共用一次性会话

- `app/src/main/java/com/tvbox/app/data/PhoneFormServer.kt` 提供共用的局域网会话。打开表单、提交和重新生成二维码均使用当前会话，5 分钟到期或配置保存后关闭监听。
- `PhoneSearchServer.kt`、`AiConfigServer.kt` 和 `VideoApiConfigServer.kt` 分别提供原有的搜索、模型和 MacCms 表单。配置校验失败允许更正，已提交的 API Key 不回显。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 绑定当前弹窗的会话，在主线程检查会话后保存设置。关闭弹窗、离开设置或切后台时回收会话。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt` 和 `SettingsScreen.kt` 在配置成功时收起二维码，过期时显示重新生成入口。
- `app/src/test/java/com/tvbox/app/data/PhoneConfigServerTest.kt` 在本机回环接口验证授权、一次保存、纠错、过期及关闭。继续运行原手机扫码搜索回归测试。

### 验证

- 2026-11-20 10:06 完成全量 131 项单元测试，含本轮 7 项回归。Release 构建、Release 关键 Lint、APK 签名和 ZIP 对齐校验通过。
- 签名测试包为 `app/build/outputs/tvbox-test-apks/TVBox-v1.3.14-live-search-qr-test.apk`（5099969 字节），版本 1.3.14（10314），SHA-256 为 `3ad813056b59b5ced3acb2d3cb105b754ee1743d56d16dc22157e191db4bd8b8`。
- 手机与盒子跨设备扫码、全屏效果和遥控器焦点待实机验收。

## 2026-11-15 19:35 - 搜索页菜单键快捷入口（未发布）

- `app/src/main/java/com/tvbox/app/ui/SearchMenuShortcut.kt`：新增可独立测试的菜单按下／松开状态规则，只有在搜索页完成的一次有效按键才打开扫码；长按去重、取消、后台重置和跨页面松开均不误触发。
- `app/src/main/java/com/tvbox/app/MainActivity.kt`：只拦截搜索页菜单键序列，不依赖某个搜索控件是否有焦点；其它页面仍交由原有处理，播放页菜单键继续切倍速。
- `app/src/main/java/com/tvbox/app/ui/SearchScreen.kt`：按钮明确显示“手机扫码输入 · 菜单键”，保留点击入口；不占用数字或方向按键。
- `app/src/test/java/com/tvbox/app/ui/SearchMenuShortcutTest.kt`：覆盖单次触发、长按、窗口已打开、跨页、取消、后台重置及丢失旧松开事件后的恢复等 7 项规则。
- 时间：2026-11-15 19:35；原需求：扫码入口缺少遥控器直达方式。方案：搜索页菜单快捷键；临时方案：无。
- 不修改手机网页、会话服务、有效期或成功后直接搜索逻辑；不请求麦克风。验证与设备限制见 `docs/validation/2026-11-15-player-settings-shortcut.md`。
- 2026-11-15 19:45：菜单规则 7 项及其它相关用例共 53 项通过，Release 构建与签名校验通过；静态复核补充新按键起始时清理丢失旧松开事件的状态，避免吞掉播放页第一次菜单操作。实机按键和焦点仍待用户确认。

## 2026-11-15 15:04 - 首次实现（未发布）

### 文件、原因与用途

- `app/src/main/java/com/tvbox/app/data/PhoneSearchServer.kt`：新增局域网输入服务、手机输入对话框式网页、中文表单解析及提交回调；不依赖云端或新增库，不接入语音服务。
- `app/src/main/java/com/tvbox/app/ui/PhoneSearchDialog.kt`：电视端二维码、有效期、取消、失效及重试提示；复用现有二维码生成能力。
- `app/src/main/java/com/tvbox/app/ui/SearchScreen.kt`：扫码入口、页面／后台服务清理、提交后首个结果焦点及无结果时搜索入口焦点；保留正常搜索与返回定位。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：管理会话代次、开始／关闭／过期状态，主线程校验当前页面并复用原有搜索；成功响应发送后关闭二维码。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`：将现有二维码图片生成函数开放为模块内复用，原 AI／视频接口扫码流程不变。
- `app/src/test/java/com/tvbox/app/data/PhoneSearchServerTest.kt`：仅用本机回环 HTTP 验证输入窗口、中文提交、空值／超长／非法请求、凭证、并发去重、过期和连接清理。

### 行为与安全边界

- 用户明确不需要语音功能，手机网页只有说明、片名输入框和提交按钮；提交后电视直接开始搜索，手机显示已发送。
- 服务绑定一个实际局域网 IPv4 地址和临时端口；没有可用地址时明确提示，不生成回环地址二维码。Wi-Fi 与有线网络需可互通，访客隔离不在客户端绕过范围。
- GET 和 POST 均校验随机凭证；会话 5 分钟有效且只允许一次成功接收，输入 1–100 字，正文上限 4096 字节、连接读取超时 5 秒、最多 8 个并发连接。
- 输入空白或格式错误不消耗正常会话；重复或并发提交只触发一次回调。页面不暴露配置、文件接口、跨域访问权限或外部资源，响应禁止缓存及嵌入。
- 取消、离开搜索页、后台 ON_STOP 及 ViewModel 销毁均停止服务并关闭连接；代次校验阻止旧请求或迟到启动结果影响新窗口。
- 时间：2026-11-15 15:04；原需求为遥控器文字输入不便。临时方案：无，新增扫码通道并保留原搜索输入方式。

### 验证及限制

- 手机服务 9 项本地 HTTP 测试通过，覆盖并发去重、关闭、过期及发送后手机断线；连同片头及既有播放回归共 43 项测试通过。2026-11-15 15:16 最终 Release 构建、v2 签名及 zipalign 校验通过，完整信息见验收记录。
- 离线构建发现原有依赖缓存缺失，已按原版本补齐并登记项目外路径；工程没有 Release 单元测试任务，改用原有 Debug 单元测试任务，不改变构建配置。
- 不操作 ADB，不运行手机浏览器或电视实机测试；可用性、扫码器兼容和真实遥控器体验待用户验收。
- 保持 `10313 / 1.3.13`，不修改 OTA 清单，不提交、推送或正式发布。

### 实现期间发现并修复的边界

- 2026-11-15 15:07：静态复核发现网络请求等待主线程期间可能越过截止时间；增加主线程受理前的实际会话有效性复核，临时方案：无。
- 2026-11-15 15:07：已受理的请求若在手机断线后写响应失败，初版可能保留电视扫码窗口；改为完成响应尝试并关闭连接后，仍结束已受理的电视流程，新增断线回归用例，临时方案：无。
- 2026-11-15 15:09：额外全量 Lint 检出既有 `data/UpdateDownloadService.kt:98` 的通知权限错误；该服务及 Manifest 与本轮开始时一致，不修改或掩盖该问题，单独记录，待后续 OTA 维护处理。

### 导航

- 主日志：`devLog/README.md`；播放模块：`devLog/playback-experience.md`；验收记录：`docs/validation/2026-11-15-intro-phone-search.md`。
