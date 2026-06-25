# TVBox AI Dev Log

## Timeline

### 2026-06-25 08:22 - 发布 v1.2.5

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级到 `1.2.5`，版本码升级到 `10205`。
  - 将播放管家阶段性能力、统计维护和 OTA 权限前置整理为 v1.2.5 发布记录。
  - 准备 release APK 与 `update.json`，用于 GitHub Release 和 OTA 更新。

### 2026-06-25 08:07 - OTA 安装权限前置

- Branch doc: `devLog/ota-update.md`
- Summary:
  - 首次启动应用时引导一次“安装未知应用”权限，用户可拒绝。
  - 检测到新版本后，点击更新会先获取安装权限，允许后再继续下载。
  - 已下载 APK 安装前仍保留权限检查，允许后继续调起系统安装器。

### 2026-06-24 19:24 - 播放管家统计维护

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 设置页新增“清空统计”按钮和确认弹窗。
  - 线路健康记录读取、记录成功、记录失败/卡顿时会被动清理 30 天前数据。
  - 线路健康记录继续保留最多 300 条，避免长期堆积。

### 2026-06-24 18:57 - 播放管家 seek 误判修复

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 手动快进/快退或拖动进度后进入 seek cooling。
  - seek 后直到下一次 READY 前不触发 5 秒缓冲换源，也不计入频繁/累计卡顿。
  - 增加 Media3 `DISCONTINUITY_REASON_SEEK` 监听，覆盖手机进度条拖动。

### 2026-06-24 09:12 - 播放管家第五阶段

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 线路健康记录新增成功、失败、卡顿次数统计。
  - 新增播放尝试去重，避免同一次播放反复进入 READY 导致成功次数虚高。
  - 设置页展示整体线路质量统计；详情页不新增次数展示。
  - 播放管家择线评分轻度参考长期成功/失败/卡顿表现。

### 2026-06-24 08:53 - 播放管家第四阶段

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 新增 `PlaybackBufferMonitor`，把卡顿识别从播放器 UI 中抽离为可测试规则。
  - 连续缓冲超过 5 秒、60 秒内频繁短缓冲、60 秒内累计缓冲过久都会记为 `SlowBuffer`。
  - 首屏短加载、快进快退后的短暂缓冲、暂停状态不会被误判为线路不稳定。

### 2026-06-19 18:42 - 播放管家第三阶段

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 新增播放前智能择线：进入播放器前根据线路成功、失败、缓冲记录计算更稳定的线路。
  - 详情页“推荐”标签复用播放管家评分，推荐显示和实际播放选择保持一致。
  - 自动换线关闭时继续尊重用户原始选择，不做播放前自动改线。

### 2026-06-19 18:18 - 播放管家第二阶段

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 设置页新增“播放管家自动换线”开关，默认开启。
  - 播放器新增“手动换线”按钮，用户可主动切换同集下一条线路。
  - 详情页播放线路显示“推荐 / 近期失败 / 较慢”状态。
  - 自动换线关闭时，播放失败只提示，不再自动切换。

### 2026-06-19 10:13 - 播放管家第一阶段

- Branch doc: `devLog/playback-agent.md`
- Summary:
  - 新增规则型播放管家 Agent 底座。
  - 播放失败或缓冲超过 15 秒时，自动记录线路健康状态并切换到同一集的下一条可用线路。
  - 播放成功后记录成功状态，避免线路被长期误判为不可用。
  - 增加播放管家提示文案和单元测试。

## Navigation

- Playback Agent: `devLog/playback-agent.md`
- OTA Update: `devLog/ota-update.md`
- Release: `devLog/release.md`
