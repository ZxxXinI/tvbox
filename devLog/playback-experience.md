# 播放体验、返回定位与继续观看

## 2026-10-05 20:07 - 用户验收后发布 v1.3.11

- 用户已测试并反馈没有明显问题，授权发布新版本并回复 Issue #3；本轮不重复执行测试或操作 ADB。
- 两项修复纳入 `10311 / 1.3.11`，构建签名正式包并同步 Release 与 OTA 清单；原本地测试记录保留，不与正式版本混淆。
- 构建、签名、大小与 SHA-256 详见 `devLog/release.md`；发布后回复 Issue #3，不自动关闭 Issue。

## 2026-10-05 19:13 - 论坛反馈：点播全屏窗口与画面比例选择

### 文件修改、原因与目的

- `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`、`app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`：新增本机持久化的自适应、裁剪铺满和拉伸铺满三种画面模式，旧数据缺少该项时默认自适应，不迁移或覆盖其他设置。
- `app/src/main/java/com/tvbox/app/ui/PlayerViewport.kt`：仅点播页进入沉浸式播放窗口，隐藏状态栏和导航栏，回到前台重新应用，退出时恢复进入前的可见性与行为；将画面模式映射为 Media3 FIT / ZOOM / FILL。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`：明确播放器窗口匹配可用区域；在 AndroidView 更新阶段实时应用缩放模式，不重建播放器，不影响进度、暂停状态、倍速和播放管家逻辑。
- `app/src/main/java/com/tvbox/app/ui/PlayerControls.kt`：新增“画面”选择面板，显示当前模式、选中标记及裁剪/变形提示；窄窗口操作栏可横向滚动，保留遥控器及触摸操作。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：即时更新画面选择并通过现有设置仓库保存。
- `devLog/README.md`、本日志：关联论坛反馈、实现范围和交付边界。

### 缺陷记录与边界

- 时间：2026-10-05 19:13；现象：论坛用户反馈同盒子播放电影时画面不满屏、四周黑框。
- 已定位的限制：点播仅固定 FIT 缩放策略，缺少铺满选项；没有发现硬编码视频分辨率或固定尺寸播放窗口。四周黑框的具体设备/片源原因尚无截图或日志，不能声称已复现。
- 实现：补齐仅播放页的沉浸式窗口和可记忆的画面模式；自适应完整保留比例，裁剪铺满可能丢失边缘，拉伸铺满可能变形，不强制改变所有影片比例。
- 片源自带黑边不等同于播放器留边，不能保证用这三种模式自动消除；同片源设备实际效果由用户验收。
- 临时方案：无；参考 [Media3 缩放模式](https://developer.android.com/reference/androidx/media3/ui/AspectRatioFrameLayout) 和 [Android 沉浸式播放窗口](https://developer.android.com/develop/ui/views/layout/immersive)。

### 构建交付

- 用户在 Issue #3 修复后追加论坛反馈修复，并要求自行完成全部功能测试；之后仅构建签名 Release 本地包，不再运行单元或设备测试，不连接或操作 ADB。
- 保持版本 `10310 / 1.3.10`；不更新正式 Release 附件、OTA 清单，不提交或推送，不修改普通直播、平台直播及独立兼容工程。
- `:app:assembleRelease` 构建通过；包内版本 `10310 / 1.3.10`，最低 API 28，v2 签名校验通过，使用现有 Release 证书，可覆盖安装保留数据。
- APK：`app/build/outputs/apk/release/TVBox-v1.3.10-issue3-picture-test.apk`；大小 `5001469` 字节，SHA-256 `c3a15736624dd0067e9b8933320ca8ce3fda63d3c7cf79801fd2eb7fb6b904ab`。
- APK 同时包含 Issue #3 与画面模式改动；后续未运行功能或单元测试，未安装设备，论坛设备黑框的实际消除效果仍待用户验收。
- 编码：中文源码/日志使用 UTF-8 BOM 并读回验证；主日志 `devLog/README.md`。

## 2026-10-05 19:07 - Issue #3 自动换线保留进度与缓冲提示恢复

### 文件修改、原因与目的

- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：自动换线必须接收当前播放位置和播放意图，并复用手动换线入口，不再把起播位置置零。
- `app/src/main/java/com/tvbox/app/domain/PlaybackRecovery.kt`：统一换线目标解析、对应集匹配与空地址校验；使用进度锚点避免未就绪备用源的零进度覆盖原续播位置；区分缓冲提示和真正播放错误。
- `app/src/main/java/com/tvbox/app/domain/PlaybackAgent.kt`：自动候选按集名匹配对应集，健康评分使用对应集下标，排除缺集及空地址，避免顺序不同的线路切错集。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`：换线前保存实际进度；播放恢复清除缓冲提示和换线通知；媒体身份与当前状态校验阻止旧线路事件再次触发换线，连续失败时保留尝试过的线路集合。
- `app/src/main/java/com/tvbox/app/domain/PlaybackBufferMonitor.kt`：就绪回调不再触发换线；频繁及累计卡顿在下一次实际缓冲时判断，过期记录清理，保留暂停和拖动保护。
- `app/src/test/java/com/tvbox/app/domain/PlaybackRecoveryTest.kt`、`PlaybackBufferMonitorTest.kt`：覆盖自动换线进度、顺序不同的集数、缺集/空地址、暂停意图、连续换线零进度、快退/归零以及恢复时不换线和提示清理。
- `devLog/README.md`、本日志：记录缺陷、修复范围与验证边界。

### 缺陷记录

- 时间：2026-10-05 19:07；反馈：[GitHub Issue #3](https://github.com/ZxxXinI/tvbox/issues/3)。
- 现象：播放管家自动换线从头播放，已经恢复播放时仍显示缓冲提示。
- 原因：自动换线起播位置硬编码为零；缓冲失败提示被写入持久错误但就绪时不清理；就绪回调也可能根据刚结束的卡顿触发换线。
- 修复：统一换线、进度锚点、按集名匹配、缓冲提示类型区分及当前媒体事件校验。
- 临时方案：无。

### 验证与范围

- 在用户追加“不需要测试”要求前启动的检查已完成：83 项单元测试通过，Debug 构建通过，Android Lint 为 0 error / 54 warning / 3 hint。该结果仅覆盖当时 Issue #3 修复代码，不代表后续论坛画面改动通过测试。
- 本轮仅处理 Issue #3，不修改画面缩放、版本号、OTA 清单或 Release 附件；不安装、不执行设备功能测试，不关闭或评论 Issue。
- Android 4.4 / Android 6 独立工程及主日志中已有无关记录保持不变，未提交或推送。
- 主日志：`devLog/README.md`；模块日志：`devLog/playback-experience.md`。

## 2026-10-04 09:44 - 继续观看向上返回导航

- 文件：`app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`、`app/src/main/java/com/tvbox/app/ui/components/Common.kt`。
- 原因：继续观看卡片获得焦点后，网格自动滚动可能隐藏顶部区域并将导航移出焦点树；横向卡片行未设置明确的向上出口。
- 修复：继续观看焦点组接管上键，先展开顶部导航、滚回网格顶部，再等待导航重新进入布局并请求首个导航按钮焦点。默认主题定位历史入口，影院主题定位搜索入口；按住上键的重复事件及释放事件被正确消费。内容卡片再次获得焦点后恢复原自动隐藏行为。
- 保留左右切换卡片、确认续播、数字快捷键及已有页面返回定位。
- 验证：签名 Release 构建与签名校验通过；2026-10-04 09:46 已覆盖安装到 `192.168.0.8:5555`，返回 Success，保留数据。遵循用户先前要求，未执行设备功能测试，由用户自行验收。
- 安装包：`app/build/outputs/apk/release/TVBox-v1.3.9-home-focus-fix.apk`；版本 `10309 / 1.3.9`；SHA-256 `1b561a3e4932873949322554966534be29fd3625984d4cd1716b517c3ebcca4a`。
- 时间：2026-10-04 09:44；缺陷：从继续观看无法向上离开卡片行；临时方案：无。

## 2026-10-03 21:44 - 电视(4)轻量界面统一

- 修改文件：`app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`。
- 原因：普通电视直播仍使用大面积绿色按钮、实心选中行，与新的点播控制栏风格不一致。
- 实现：手机底部改为渐变遮罩和紧凑操作按钮，频道入口使用白色胶囊；显示当前频道与线路信息。频道面板增加标题和数量，使用半透明深色背景、淡绿色选中底色和清晰的焦点描边，手机提供关闭入口。电视侧栏适当加宽，频道提示与操作提示改为轻量样式。
- 仅调整电视(4)页面及本日志；保持当前工作区中已有播放逻辑与续播修复。
- 验证：签名 Release 构建和签名校验通过；2026-10-03 21:52 已覆盖安装到 `192.168.0.8:5555`，返回 Success，保留数据。按用户要求未执行设备功能测试。
- 安装包：`app/build/outputs/apk/release/TVBox-v1.3.9-tv-ui-test.apk`；版本 `10309 / 1.3.9`；SHA-256 `7b469b6d7463e6611514bfa8d3e97120b3e6bba2ca773f55e317f5fc631a3224`。
- 临时方案：无。

## 2026-10-03 21:35 - 控制栏重绘与续播超时修复

- `app/src/main/java/com/tvbox/app/ui/PlayerControls.kt`：将满宽绿色按钮和粗滑块改为渐变遮罩、细进度条、小圆点、紧凑文字按钮及白色主播放按钮；焦点保留浅绿色描边。
- `app/src/main/java/com/tvbox/app/data/MovieRepository.kt`：新增仅请求原来源的 `getResumeDetail`；备用来源自身超时使用 `withTimeoutOrNull` 隔离，不再取消整组详情/搜索请求；调用方取消仍正常传播。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：主来源成功后立即续播，后台追加备用来源且保留当前播放列表；离开加载页时取消续播任务，防止迟到结果跳转。
- `app/src/test/java/com/tvbox/app/data/MovieRepositoryTest.kt`：新增三项回归，覆盖慢备用来源不阻塞续播、备用来源超时不丢失主来源和真实取消继续生效。
- `tools/playback_acceptance_fixture.py`、`tools/resume_timeout_acceptance.py`：补充 6 秒慢备用源与冷启动续播复现脚本；更新既有脚本的倍速文字兼容。

### 缺陷与验证

- 时间：2026-10-03 21:35；现象：继续观看报 `Timed out waiting for 4000ms`；原因：原续播等待 `getDetailProgressively().lastOrNull()`，备用源的 TimeoutCancellationException 又被作为整组取消重新抛出；修复：原来源优先返回、后台补线和局部超时隔离；临时方案：无。
- 69 项单元测试通过，Lint 与 Release 构建通过。设备续播复测在准备阶段未完成，随后用户明确要求停止测试并自行验收；不将此次设备复测计为通过。
- 已生成并校验 `app/build/outputs/apk/release/TVBox-v1.3.9-ui-resume-fix.apk`，SHA-256：`bcf162ebcedbd89f09bba8186de3554b77be706eafe2da53dcbd262a1574ef08`。
- 已覆盖安装至 `192.168.0.8:5555`，安装返回 Success，保留数据，版本仍为 `10309 / 1.3.9`；未在该设备启动测试。

## 2026-10-03 - 本轮实现

### 修改文件与目的

- `app/src/main/java/com/tvbox/app/domain/PlaybackInterruptionPolicy.kt`：统一后台、音频焦点中断及用户手动恢复规则。
- `app/src/main/java/com/tvbox/app/ui/PlaybackSession.kt`：将规则接入 Android 生命周期和 Media3 音频焦点事件；媒体替换继续尊重暂停状态。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`、`PlayerControls.kt`：加入 TVBox 自有操作栏、选集/线路/倍速面板和进度条，保留数字键、菜单键、连续快进与手机手势。
- `app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`、`PlatformLiveScreen.kt`：接入共享暂停状态及继续播放入口，后台暂停时不触发自动换线/重连。
- `app/src/main/java/com/tvbox/app/ui/KeepScreenOnWhileVisible.kt`：播放页在前台保持亮屏，离开或进入后台后恢复原状态。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`、`SearchScreen.kt`、`DetailScreen.kt`、`ReturnFocus.kt`：保存页面滚动位置和内容焦点，按稳定标识恢复；来源/分类/搜索词主动切换时重置。
- `app/src/main/java/com/tvbox/app/domain/WatchHistory.kt`、`data/HistoryRepository.kt`、`ui/TvBoxViewModel.kt`、`ui/components/Common.kt`：新增最近六部未看完的继续观看，绑定播放快照保存历史，串行写入并保留现有历史格式。
- `app/src/test/java/com/tvbox/app/domain/PlaybackExperienceTest.kt`：新增中断、继续观看过滤和跨线路选集规则测试。
- `app/src/debug/AndroidManifest.xml`、`app/src/debug/java/com/tvbox/app/AcceptanceActivity.kt`：仅 Debug 打包的可控验收入口，使用现有业务页面和仓库。
- `app/build.gradle.kts`：提供 Debug 可选使用现有 Release 证书的构建属性，便于无损覆盖安装；正常构建及版本号不变。
- `tools/playback_acceptance_fixture.py`、`tools/playback_adb_acceptance.py`：本地视频/接口及可重复 ADB 验收工具。

### 缺陷记录

- 时间：2026-10-03；现象：从搜索结果进入详情后，返回直接跳到首页；修复：按入口保留 `detailReturnScreen`；临时方案：无。
- 时间：2026-10-03；现象：原长按临时 2 倍速会通过播放器监听写回全局倍速，松手可能仍为 2 倍速；修复：手势单独保存原倍速，松手按原值恢复；临时方案：无。
- 时间：2026-10-03；现象：设备成功停止测试服务但 `am stopservice` 返回非零退出码，测试产生误报；修复：仅对明确包含 `Service stopped` 的停止服务结果接受该退出码，其他命令仍严格校验。
- 时间：2026-10-03；现象：触摸模式下 Accessibility XML 的 `focused` 与 Compose 实际焦点不同；处理：通过返回后按确认键打开原影片验证实际焦点，而不单独依赖 XML 标志。
- 时间：2026-10-03；现象：操作栏聚焦后返回键可能被控件消费，后续左右键仍用于导航；修复：点播根节点接管面板/操作栏的返回键，关闭后在下一帧恢复画面焦点。
- 时间：2026-10-03；现象：快速切到后台再返回时可能尚未触发 ON_STOP；修复：在 ON_PAUSE 即暂停并保存进度，ON_RESUME 仅恢复前台状态，不自动播放。

### 验证与产物

- 最终结果见 `docs/validation/2026-10-03-playback-experience.md`。
- 66 项单元测试通过、Lint 0 errors、签名 Release 构建通过；ADB 13 项通过、1 项部分验证。
- 2026-10-03 21:08：按用户要求覆盖安装到 `192.168.0.8:5555`，保留数据；停止后续设备操作，由用户自行验收。
- 未升级版本号、未提交/推送或发布；保留原工作区中的无关文件。

### 导航

- 主日志：`devLog/README.md`
- 验收报告：`docs/validation/2026-10-03-playback-experience.md`
