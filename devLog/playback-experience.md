# 播放体验、返回定位与继续观看

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
