# 直播源切换与 result.txt 解析 - 2026-08-01

## 目标

将电视直播默认数据源切换为 `yanghanhanyingshi/iptv` 生成的 `result.txt`，并兼容其分组和线路标记格式。

## 实现记录

## 本次完善（2026-08-02）

- `LiveParser.kt` 只接受有 `分组名称,#genre#` 上下文的频道行，旧的无分组格式直接丢弃。
- 频道 URL 支持带或不带 `$LR•IPV4•29『线路…』` 元数据；播放前截断第一个 `$`，没有元数据时直接使用完整 URL。
- 同一分组内按逗号前的频道名称合并多条线路并按 URL 去重；不同分组的同名频道保持独立。
- `LiveChannel` 保存分组、逗号前频道名称和 `LiveChannelLine` 线路列表。
- 直播页显示分组标题和逗号前的频道名称；遥控器上/下循环切换当前频道线路，播放失败时自动向后尝试下一条线路。
- 回归测试覆盖无分组旧格式、带/不带元数据、同名合并、重复 URL 去重及跨分组同名频道。
- ADB 设备 `192.168.0.7:5555` 已安装 Debug APK，并验证播放器解码与上/下线路切换。

## 卡顿线路自动换线（2026-08-02 11:09）

- 文件：`app/src/main/java/com/tvbox/app/domain/LivePlaybackWatchdog.kt`
  - 监控播放位置是否连续 4 秒不前进；暂停或切换媒体后重置计时，只对同一次停滞报告一次。
- 文件：`app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`
  - 复用 `PlaybackBufferMonitor`，直播专用阈值为连续缓冲 6 秒、60 秒内 3 次缓冲、累计缓冲 12 秒。
  - 接入播放位置看门狗；播放错误、播放结束、缓冲过久和播放停滞统一推进当前频道下一条线路。
  - 自动换线不设置错误提示状态，线路失败时直接切换；所有线路尝试完毕也不显示线路失败文本。
- 文件：`app/src/test/java/com/tvbox/app/domain/LivePlaybackWatchdogTest.kt`
  - 覆盖正常推进、4 秒无进度、暂停重置和切换媒体重置。

### 验证

- `:app:testDebugUnitTest`：通过。
- `:app:assembleDebug`：通过。
- APK 已安装到 ADB 设备 `192.168.0.7:5555`。
- 实机前几条线路播放失败时自动推进线路，没有显示失败提示；后续线路进入 H.264/AAC 解码并持续渲染。

- 文件：`app/src/main/java/com/tvbox/app/data/LiveRepository.kt`
  - 默认地址改为 `https://wget.la/https://raw.githubusercontent.com/yanghanhanyingshi/iptv/main/result.txt`。
- 文件：`app/src/main/java/com/tvbox/app/domain/LiveParser.kt`
  - `#genre#` 行建立分组上下文，不作为频道处理；没有分组上下文的旧格式频道行直接忽略。
  - 频道行在第一个 `$` 处截断，移除 `$LR•IPV4•29『线路…』` 等线路元数据；无元数据时直接使用 URL。
  - 同组同名频道合并为一个频道并按 URL 去重，保留线路出现顺序。
- 文件：`app/src/test/java/com/tvbox/app/domain/PlaybackParserTest.kt`
  - 增加无分组旧格式、带/不带元数据、同名合并、重复 URL 去重和跨分组同名频道的回归测试。

## 缺陷记录

- 时间：2026-08-01 21:10
- 症状：原解析器将线路标记当作 URL 的一部分，导致 ExoPlayer 请求带有 `$LR…` 后缀的无效地址。
- 修复位置：`LiveParser.kt` 的直播行解析逻辑。
- 临时方案：无。

## 验证标准

- `央视卫视,#genre#` 被忽略。
- 三条示例线路解析为三条 `LiveChannel`。
- 播放 URL 不包含 `$LR…` 元数据。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/live-source.md`
