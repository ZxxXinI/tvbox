# TVBox AI Dev Log

## Timeline

### 2026-07-01 09:36 - 发布 v1.2.8

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级到 `1.2.8`，版本码升级到 `10208`。
  - 将自定义视频接口、手机播放手势和首页滚动体验整理为 v1.2.8 发布记录。
  - 准备 release APK 与 `update.json`，用于 GitHub Release 和 OTA 更新。

### 2026-07-01 09:11 - 手机双击暂停修正

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 手机播放页双击区域从左右两段改为左/中/右三段。
  - 双击左侧快退 10 秒，中间播放/暂停，右侧快进 10 秒。
  - 保留单击显示/隐藏控制栏和左右滑动拖动进度。

### 2026-07-01 09:02 - 自定义视频接口配置

- Branch doc: `devLog/settings.md`
- Summary:
  - 设置页“视频接口”保留内置线路不变，并支持扫码添加 MacCms 自定义接口。
  - 手机页面填写接口名称和 MacCms 地址后，电视端自动保存并追加到线路列表。
  - 新增接口会作为当前首页资源使用，首页、搜索、详情和 AI 找片都会走同一套线路选择。

### 2026-07-01 08:23 - 手机播放手势完善

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 手机播放页支持单击屏幕显示或隐藏底部控制栏。
  - 双击屏幕左半区快退 10 秒，双击右半区快进 10 秒。
  - 左右滑动屏幕按影片时长拖动进度，并显示半透明进度提示。
  - 保留长按屏幕临时 2 倍速播放，松手后恢复原倍速。

### 2026-06-30 21:57 - 手机播放与首页滚动交互

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 手机播放页支持长按画面临时 2 倍速播放，松手后恢复原倍速。
  - 首页影片网格向下滑动后隐藏 `TVBox` 标题和影视分类。
  - 首页影片网格回到顶部后恢复标题和影视分类。

### 2026-06-28 20:56 - 发布 v1.2.7

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级到 `1.2.7`，版本码升级到 `10207`。
  - 将设置页大模型配置、手机扫码填写模型/API Key、设置页焦点样式归档为正式版本。
  - 准备 release APK 与 `update.json`，用于 GitHub Release 和 OTA 更新。

### 2026-06-28 19:10 - AI 手机扫码配置

- Branch doc: `devLog/settings.md`
- Summary:
  - 模型名称和 API Key 不再作为电视端输入框展示，改为可聚焦按钮。
  - 点击模型或 API Key 按钮后，电视弹出二维码和局域网地址。
  - 手机扫码打开本地配置页，填写模型名称和 API Key 后自动同步到电视设置。

### 2026-06-28 18:49 - 设置页大模型配置

- Branch doc: `devLog/settings.md`
- Summary:
  - 设置页新增大模型配置区，支持 Agnes、DeepSeek、SiliconFlow、Qwen。
  - 支持配置模型名称和 API Key；API 地址不在设置页展示。
  - AI 找片仅在用户填写 API Key 后使用设置页配置，否则继续使用 APK 内置 AI 配置。
  - 设置页主要按钮统一为空底描边、聚焦绿色填充的电视焦点样式。

### 2026-06-28 17:35 - 覆盖发布 v1.2.6

- Branch doc: `devLog/release.md`
- Summary:
  - 用户要求本次更新仍作为 `v1.2.6` 发布，不新增版本号。
  - 重新构建 release APK，并同步 `README.md` 的 `update.json` 示例 SHA-256 与大小。
  - 后续通过覆盖 GitHub Release 的 `TVBox-v1.2.6.apk` 和 `update.json` 完成同版本发布。

### 2026-06-28 17:22 - 设置首页资源与 AI 按钮焦点优化

- Branch docs: `devLog/settings.md`, `devLog/ai-recommend-focus.md`
- Summary:
  - 设置页新增“首页资源 / 首页渲染数据”下拉选择，可在量子、如意、360 等资源站之间切换首页默认数据。
  - 首页数据源选择会保存到本地设置，重启后继续使用上次选择。
  - AI 找片页顶部按钮和快捷推荐词按钮改为电视焦点样式：未选中为空底描边，选中时绿色填充并显示白色高亮边框。

### 2026-06-28 17:01 - 发布 v1.2.6

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级到 `1.2.6`，版本码升级到 `10206`。
  - 将 AI 找片、语音入口、快捷推荐词、换一批和语音权限修复整理为 v1.2.6 发布记录。
  - 准备 release APK 与 `update.json`，用于 GitHub Release 和 OTA 更新。

### 2026-06-28 16:40 - AI 语音识别空结果修复

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - ADB 日志显示系统把 `RECOGNIZE_SPEECH` 交给小米语音 Activity，但该 Activity 很快返回空结果。
  - AI 找片语音入口改为应用内 `SpeechRecognizer`，减少对外部语音页面结果返回的依赖。
  - 新增麦克风权限声明和运行时授权；识别时显示“正在听”状态，失败时显示明确错误。

### 2026-06-28 16:26 - AI 找片交互优化

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - AI 找片页进入后默认聚焦“语音找片”，减少电视遥控器操作步数。
  - 新增快捷推荐词，用户可直接选择常见找片需求并自动提交。
  - 新增“换一批”，会把当前推荐片名作为排除名单重新请求模型。
  - 语音取消、未识别到内容或设备不支持时，在 AI 找片页面给出提示。

### 2026-06-27 20:19 - AI 推荐延迟资源匹配

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - AI 推荐页不再生成结果后立即逐条搜索资源站，改为先展示模型 JSON 内容。
  - 用户点击推荐卡片时才搜索当前影视数据源；无资源时停留在 AI 找片页提示“暂无该视频资源”。
  - 从 AI 推荐进入详情页后，遥控器返回会回到 AI 找片页。
  - 首页顶部入口移除“刷新(1)”，快捷键调整为历史(1)、搜索(2)、AI找片(3)、直播(4)、设置(5)。

### 2026-06-27 19:05 - AI 推荐卡片尺寸调整

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - AI 推荐页卡片宽度从 `260.dp` 调整为与首页一致的 `148.dp`。
  - 电视横屏下推荐页从偏大的两列卡片恢复为更接近首页的多列展示。

### 2026-06-26 22:08 - AI 找片实验入口

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - 首页新增“AI找片(6)”入口，支持文字输入和系统语音输入。
  - 新增 Agnes Chat Completions 接入，按“角色 + 任务 + 上下文 + 要求 + 输出格式”组织提示词。
  - AI 返回推荐 JSON 后，应用会用 `searchKeyword` 搜索当前影视数据源并生成可点击推荐卡片。
  - API Key 通过本地 `TVBOX_AI_API_KEY` 配置注入，不写入仓库。

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
- AI Recommend: `devLog/ai-recommend.md`
- AI Recommend Focus: `devLog/ai-recommend-focus.md`
- Settings: `devLog/settings.md`
- OTA Update: `devLog/ota-update.md`
- Release: `devLog/release.md`
- Home / Player UI: `devLog/home-player-ui.md`
