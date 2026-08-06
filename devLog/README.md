# TVBox AI Dev Log

### 2026-08-06 22:45 - 发布 v1.3.2：字体大小与卡片布局适配

- Branch doc: `devLog/settings.md`、`devLog/release.md`
- Summary:
  - 发布全局“正常 / 大 / 超大”字体大小设置，选择结果保存到本机并在应用启动后恢复。
  - 所有页面文字以及首页、搜索、AI 找片、历史、选集、设置、平台直播卡片的密度随档位协调调整。
  - 完成 `testDebugUnitTest`、R8 Release 构建与 APK 签名验证，准备 GitHub 与 Gitee 的 v1.3.2 发布资产。

### 2026-08-06 22:26 - 字体大小与卡片布局设置

- Branch doc: `devLog/settings.md`
- Summary:
  - 设置页新增“字体大小”：正常、大、超大三档，默认正常并持久化到本机。
  - 全局 Material Typography 会随档位立即更新，覆盖首页、搜索、详情、历史、直播、播放器和设置页面。
  - 影视海报、搜索/AI 找片、历史、选集和平台直播卡片同步增大，字号和内容密度保持协调。

### 2026-08-03 08:40 - v1.3.1 带平台直播服务地址覆盖构建

- Branch doc: `devLog/release.md`
- Summary:
  - 使用 `TVBOX_PLATFORM_LIVE_SERVICE_URL=http://20.205.10.127:8868` 重新构建 Release APK。
  - OTA `apkUrl` 增加 `https://gh-proxy.org/` 前缀并更新 APK SHA-256。
  - 覆盖 GitHub v1.3.1 Release 的 APK 和 `update.json` 资产。

### 2026-08-03 08:00 - 发布 v1.3.1

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级为 `1.3.1` / `10301`。
  - 归档默认主题 / 影院主题切换、影院首页图标导航和直播遥控器修复。
  - 准备 Release APK、OTA 清单和 GitHub Release 资产。

### 2026-08-03 07:53 - 恢复默认主题初始首页结构

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 默认主题恢复无左侧栏、无 Hero 的初始首页结构。
  - 首页顶部恢复“历史(1) / 搜索(2) / 推荐(3) / 电视(4) / 直播(5) / 设置(6)”六个遥控器入口。
  - 影院主题继续使用左侧图标栏、Hero 推荐和影院首页布局。
  - Debug 构建和 ADB 设备主题切换验证通过。

### 2026-08-03 07:32 - 增加首页分界线并修正电视图标尺寸

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 左侧栏与首页内容区之间增加灰色 1dp 分界线。
  - 电视字体图标缩小至 22sp，避免字形被裁切。
  - Debug 构建、安装和 ADB 首页截图验证通过。

### 2026-08-03 07:24 - 使用项目字体图标并修正影院首页布局

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 左侧导航改用 `docs/icon/font/iconfont.ttf`，推荐入口映射 `linggan_o` 图标。
  - 左栏背景与首页背景统一。
  - 收紧首页 Hero 内部间距，修复“播放 / 详情”文字被裁切。
  - 本次不调整默认主题语义，保留后续单独处理。

### 2026-08-02 - v1.3.0 多平台直播与服务端部署

- Branch docs: `devLog/platform-live.md`, `devLog/release.md`
- Summary:
  - Android 版本升级为 `1.3.0` / `10300`。
  - README 增加五平台直播、服务器部署和 `TVBOX_PLATFORM_LIVE_SERVICE_URL` 构建说明。
  - 本次只提交源码和 Debug 构建验证，不发布 GitHub Release；`update.json` 暂时保持上一版已发布版本。

### 2026-08-02 13:26 - 多平台直播服务部署说明

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 新增 `platform_live_server/DEPLOYMENT.md`，说明服务器运行时文件、Python/Node.js 依赖、Linux systemd、Windows 启动、无 Python 主机的 Docker 部署、防火墙、Cookie 环境变量、TVBox 构建地址、接口检查和常见问题。
  - 明确服务器只解析临时地址，不代理视频流；`reference/`、Android 源码和 APK 不属于运行时依赖。

## Timeline

### 2026-08-02 - 五平台直播适配完成

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 统一服务端接入斗鱼、虎牙、哔哩哔哩、抖音、快手五个平台，保留 `/v1/live/sites`、`categories`、`rooms`、`resolve` 接口和旧响应字段。
  - 新增 B 站 WBI、虎牙 anti-code/TARS、抖音 a_bogus Node 助手、快手网页状态与 Kww 支持。
  - 缓存键改为 `(site, room_id)`；Cookie 仅从电脑服务端环境变量读取。
  - Python/Node 服务端回归测试 21/21 通过。

### 2026-08-02 11:35 - 一级分类卡片标签与斗鱼最高画质优先

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 一级分类卡片不再显示“一级分类”标签，保留分类名称和二级分类数量。
  - 斗鱼多档画质按 `rate` 降序尝试，优先使用最高画质；同一画质的多 CDN 兜底逻辑保持不变。
  - Python 单测、Android 单测、Debug 构建和 `emulator-5554` 实机播放验证通过。

### 2026-08-02 11:23 - OTA 更新地址切回 GitHub

- Branch doc: `devLog/release.md`
- Summary:
  - 应用更新清单地址切换为 `https://raw.githubusercontent.com/ZxxXinI/tvbox/main/update.json`。
  - README 的 OTA、安装和发布说明统一改为 GitHub，APK 继续使用 GitHub Release 地址。
  - Android 单元测试通过，活动代码和文档中不再保留 Gitee OTA 地址。

### 2026-08-02 11:09 - 直播线路卡顿自动换线

- Branch doc: `devLog/live-source.md`
- Summary:
  - 直播页复用缓冲监控，连续缓冲超过 6 秒、60 秒内累计缓冲超过 12 秒或发生 3 次短缓冲时自动切换线路。
  - 新增播放位置看门狗，播放状态正常但位置连续 4 秒不前进时自动切换线路。
  - 播放错误、播放结束和卡顿换线统一不显示线路失败提示；自动尝试当前频道的下一条线路。
  - 新增看门狗单元测试；Android 单元测试、Debug 构建、APK 安装和 ADB 实机播放验证通过。

### 2026-08-02 11:27 - 斗鱼一级/二级分类正式浏览

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 斗鱼分类接口不再限制“原创IP”，完整返回一级大类和二级分类。
  - 平台直播页面改为“斗鱼 → 一级大类 → 二级分类 → 直播间”的浏览流程。
  - Python 单测、Android 构建和 `emulator-5554` 实机验证通过，已验证“网游竞技 → 英雄联盟”房间列表。

### 2026-08-02 09:53 - 更新 Agnes 默认模型与请求地址确认

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - 默认模型名由 `agnes-2.0-flash` 更新为 `agnes-2.5-flash`。
  - 确认默认请求地址为 `https://apihub.agnes-ai.com/v1/chat/completions`。
  - Debug 构建、单元测试和 `emulator-5554` 安装启动验证通过。

### 2026-08-02 09:41 - 更新本地默认大模型 API Key

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - 更新本地 `TVBOX_AI_API_KEY`，仅用于本机 Debug 构建，不写入版本库或开发日志。
  - Debug 构建、单元测试和 `emulator-5554` 安装验证通过。

### 2026-08-02 09:20 - 首页入口命名与推荐页语音入口隐藏

- Branch doc: `devLog/ai-recommend.md`
- Summary:
  - 首页快捷入口调整为“推荐(3) / 电视(4) / 直播(5) / 设置(6)”，数字快捷键同步对应功能。
  - 推荐页不再展示“语音找片”按钮，语音识别实现仍保留在代码中，便于后续恢复。

### 2026-08-02 09:25 - 入口页面标题同步

- Branch docs: `devLog/ai-recommend.md`, `devLog/platform-live.md`
- Summary:
  - 推荐页顶部标题由“AI 找片”改为“推荐”。
  - 平台直播页顶部标题由“平台直播”改为“直播”。
  - 搜索页顶部标题由“搜索影片”改为“搜索”；历史、设置和电视直播页面检查后保持现状。

### 2026-08-02 09:00 - 平台直播：斗鱼弹幕验证后撤销

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 严格参照本地 `dart_simple_live` 的斗鱼弹幕协议，完成了真机登录、入组、自定义心跳和帧接收验证。
  - 当前测试房间在验证窗口只返回登录和进房等非聊天帧，未取得可展示的真实 `chatmsg`。
  - 按用户要求撤销全部弹幕与临时诊断代码，重新构建并安装稳定播放器 APK；不新增电脑服务配置。

### 2026-08-02 07:35 - 直播源分组、频道合并与线路切换

- Branch doc: `devLog/live-source.md`
- Summary:
  - 旧的无分组直播格式直接忽略，仅解析 `分组名称,#genre#` 之后的频道行。
  - 支持带或不带 `$LR•IPV4•29『线路…』` 元数据的 URL，并在播放前移除元数据。
  - 同一分组内同名频道合并为一个频道，重复 URL 去重；不同分组的同名频道保持独立。
  - 直播列表显示分组标题和逗号前的频道名称；遥控器上/下切换当前频道线路，播放失败自动尝试下一条线路。
  - Android 单元测试、Debug 构建和 ADB 实机播放/线路切换验证通过。

### 2026-08-01 22:40 - 平台直播：原创IP 分类页测试体验

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 斗鱼测试接口的分类结果限制为“原创IP”（斗鱼 ID `183`），避免测试阶段展示大量未验证分类。
  - 平台直播的分类与房间网格固定为每行 5 张卡片；卡片按可用宽度保持 16:9 封面比例。
  - 房间列表向下滚动后自动隐藏标题和副标题，释放空间给直播卡片；返回顶部时自动恢复。
  - Python 单测、Android Debug 单测与构建通过；ADB 实机验证了分类过滤、五列网格、标题收起和房间播放。

### 2026-08-01 21:10 - 直播源切换为 yanghanhanyingshi result.txt

- Branch doc: `devLog/live-source.md`
- Summary:
  - 默认直播源切换为 `https://wget.la/https://raw.githubusercontent.com/yanghanhanyingshi/iptv/main/result.txt`。
  - 直播解析器兼容 `频道,URL$LR…` 格式，忽略 `#genre#` 分组行和线路元数据。
  - 新增三条线路示例的回归测试，确认播放请求只使用纯 URL。

### 2026-08-01 19:29 - 平台直播：斗鱼多 CDN 自动恢复测试

- Branch doc: `devLog/platform-live.md`
- Summary:
  - 参照本地 `reference/dart_simple_live-feat-ohos-1.12.7` 的斗鱼取流方式，服务端改为一次签名后收集同清晰度的全部 CDN 候选地址。
  - Android 端改为当前线路重取地址两次、再切下一 CDN；播放结束、播放错误和持续缓冲均进入同一恢复流程。
  - Python 单元测试、Android 单测与 Debug APK 构建通过；APK 已通过 ADB 安装，实机跨越原先十余秒断流窗口后持续正常播放。

### 2026-07-08 19:58 - 发布 v1.2.10

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级到 `1.2.10`，版本码升级到 `10210`。
  - 发布启动安装权限请求移除、直播手机触摸手势和播放器标题颜色优化。
  - 准备 release APK、`update.json` 和 GitHub Release 说明。

### 2026-07-08 19:44 - 直播手机触摸手势

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 直播播放界面新增手机触摸操作。
  - 单击屏幕显示左侧频道列表。
  - 双击左半屏切换上一个频道，双击右半屏切换下一个频道。

### 2026-07-08 19:35 - 取消启动安装权限请求

- Branch doc: `devLog/ota-update.md`
- Summary:
  - 删除应用首次打开时主动请求“安装未知应用”权限的逻辑。
  - 保留点击更新下载和安装 APK 时的权限检查与引导。
  - 避免用户刚打开应用就被系统权限页打断。

### 2026-07-08 07:43 - 影院黑 + 活力绿 UI 风格

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 参考 Spotify 式沉浸暗色播放器风格，统一主题为近黑背景、深灰卡片和绿色功能焦点。
  - 首页、设置页、AI 找片按钮改为深色胶囊按钮，聚焦时变为绿色。
  - 海报卡片、历史卡片、AI 推荐卡片聚焦时增加深灰高亮和更明显的绿色边框。
  - 详情页选集、播放器提示、直播提示统一使用绿色作为主要状态信号。

### 2026-07-08 08:00 - 发布 v1.2.9

- Branch doc: `devLog/release.md`
- Summary:
  - 应用版本升级到 `1.2.9`，版本码升级到 `10209`。
  - 将“影院黑 + 活力绿焦点”UI 风格整理为 v1.2.9 发布记录。
  - 准备 release APK、`update.json` 和 GitHub Release 说明。

### 2026-07-01 18:09 - 接入 Gitee OTA

- Branch doc: `devLog/release.md`
- Summary:
  - 新增 Gitee 仓库作为国内 OTA 清单入口。
  - 应用更新检测地址切换为 Gitee raw 的 `update.json`。
  - 根目录新增 `update.json`，后续随 `agent` 分支同步到 Gitee。

### 2026-07-01 18:03 - 撤回 S3 发布流程

- Branch doc: `devLog/release.md`
- Summary:
  - S3 存储上传不可用，当前不再维护 S3 发布脚本。
  - 删除 S3 发布脚本，README 恢复为 GitHub Release 上传 APK 和 `update.json` 的流程。
  - 后续可以考虑使用 Gitee 作为 OTA 清单和 APK 下载地址。

### 2026-07-01 10:40 - S3 发布流程

- Branch doc: `devLog/release.md`
- Summary:
  - 新增发布资产脚本，发版时同时准备 GitHub Release 和 S3 下载文件。
  - OTA 仍从 GitHub Release 获取 `update.json`，但 `apkUrl` 指向 S3，减少电视盒子下载 APK 的等待时间。
  - S3 凭据只从本机配置或环境变量读取，不写入仓库。

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
- Platform Live: `devLog/platform-live.md`

### 2026-08-03 06:44 - 首页方案归档、图标导航与主题切换

- Branch doc: `devLog/home-player-ui.md`
- Summary:
  - 将三套首页切换原型归档到 `docs/design/tvbox-ui-directions.html`，补充第一套方案和遥控器快捷键说明。
  - 首页左侧导航改为图标形式；移除首页顶部重复 TV 标识和设置入口，仅保留搜索、历史。
  - 新增“默认主题 / 影院主题”持久化切换，默认主题沿用原有 Material 风格，影院主题使用影院色板。
  - 保留首页数字键 `1`–`6` 快捷键，并完成 `emulator-5554` 安装截图验证。
