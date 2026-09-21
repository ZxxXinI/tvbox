# Home / Player UI - 2026-06-30

## 2026-09-21 18:22 - 电视(4)手机页面兼容

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`
  - Reason: “电视(4)”普通电视直播页面沿用电视盒子布局，手机端频道面板固定在左侧且频道行不能直接点击。
  - Purpose: 手机竖屏使用底部频道面板，横屏使用自适应左侧面板；增加触摸选台、频道浏览保持和手机操作栏。
  - Purpose: 仅在普通电视直播页面临时放开手机方向，退出页面后恢复进入前的方向；电视设备继续保持横屏和原有遥控器事件处理。
- File path: `devLog/README.md`
  - Reason: 记录本次“电视(4)”页面适配并保持主时间线可导航。
  - Purpose: 登记本次修改的范围和验证结果。
- File path: `devLog/home-player-ui.md`
  - Reason: 本次修改属于普通电视直播页面 UI 和触摸交互。
  - Purpose: 记录实现边界、缺陷和验证结果。

## Bug Record

- Time: 2026-09-21 18:22
- Symptoms: 手机进入“电视(4)”时只能使用固定电视盒子布局，频道触摸选取和常用播放操作不够直接。
- Attempted fix: 在 `LiveScreen.kt` 内增加手机横竖屏布局、点击选台、触摸控制栏和页面级方向管理；保留电视端遥控器路径。
- Temporary solution: 无。

## Verification

- `.\\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`：passed。
- Lint：0 errors；未执行手机或电视设备功能测试。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-09-16 11:57 - Media3 控制器移除与遥控器快捷键恢复

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: Media3 原生控制器的子控件取得焦点后，TVBox 外层监听无法稳定收到数字键和菜单键；控制器可见时左右键也被交给了原生进度条。
  - Purpose: 设置 `useController=false`，让 `PlayerView` 只负责画面；禁止播放器及子控件获取焦点，由 Compose 根容器统一处理遥控器事件。
  - Purpose: 左右键每次移动 10 秒，按键重复事件以 150ms 最小间隔连续执行；数字 1/3、菜单和播放暂停键只在松开时执行一次。
  - Purpose: 菜单键恢复 `0.75x / 1x / 1.25x / 1.5x / 2x` 循环倍速，并用临时提示显示进度、切集、倍速及播放状态。

## Bug Record

- Time: 2026-09-16 11:57
- Symptoms: 更换为 Media3 原生控制器后，数字 1、数字 3 和菜单键不可用，左右键依赖原生进度条焦点。
- Attempted fix: 移除原生控制器及焦点样式代码，把遥控器处理移动到唯一可聚焦的播放页根节点，并区分 KeyDown 重复 seek 与 KeyUp 单次快捷操作。
- Temporary solution: 无。

## Verification

- `./gradlew.bat :app:compileDebugKotlin --console=plain --no-daemon`：passed。
- `./gradlew.bat :app:assembleRelease --console=plain --no-daemon`：passed。
- 按用户要求未启动应用或执行播放功能测试。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-09-15 21:38 - 播放器与搜索页遥控器焦点增强

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: Media3 原生播放、切集和设置按钮聚焦时仅有弱反馈，进度条小圆点也不易判断是否选中。
  - Purpose: 为原生控制按钮增加绿色底色、白色描边、图标反色和放大动画；为进度条增加聚焦背景、白色小圆点和纵向放大，并将遥控器步长固定为 10 秒以支持长按连续 seek。
- File path: `app/src/main/java/com/tvbox/app/ui/SearchScreen.kt`
  - Reason: 搜索按钮在遥控器聚焦时与未聚焦状态接近。
  - Purpose: 为搜索与返回按钮增加绿色选中色、白色焦点环、放大、阴影和粗体文字。

## Bug Record

- Time: 2026-09-15 21:38
- Symptoms: 播放器控制按钮、进度条圆点和搜索按钮的遥控器选中状态不够明显；进度条快进步长不固定。
- Attempted fix: 保留 Media3 原生控制器，在运行时为原生控件增加电视焦点状态，并调用 `DefaultTimeBar.setKeyTimeIncrement(10000)` 固定方向键步长。
- Temporary solution: 无。

## Verification

- `./gradlew.bat :app:assembleRelease --console=plain --no-daemon`：passed。
- 按用户要求未启动应用，也未执行界面和播放功能测试。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-08-16 20:52 - 播放器智能横竖屏比例适配

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 电影、电视剧与短剧的视频实际比例不同，手机端需要针对竖屏短剧自动适配。
  - Purpose: 监听 Media3 `VideoSize`，按真实比例区分横屏和竖屏；`PlayerView` 固定使用 `FIT` 完整显示。非电视设备播放竖屏流时自动转为竖屏，横屏流保持横屏；电视设备维持横向并使用黑边显示竖屏流；退出播放器恢复横屏。

- File path: `devLog/README.md`
  - Reason: 用户要求每次代码修改后记录开发内容。
  - Purpose: 在项目主时间线登记本次播放器比例适配。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次属于播放器显示与移动端交互调整。
  - Purpose: 保存实现原因、边界和验证结果。

## Bug Record

- Time: 2026-08-16 20:41
- Symptoms: `testDebugUnitTest` 的两项内容过滤测试失败。
- Attempted fix: 已定位为当前 `ContentFilter` 将屏蔽关键词改为 `x` 后，与测试期望的“伦理、电影解说”过滤规则不一致。
- Temporary solution: 此改动在本次任务前已由用户保留并提交，不擅自恢复；比例适配代码已通过 Kotlin 编译与 APK 构建。

## Verification

- `./gradlew.bat assembleDebug --console=plain --offline --no-daemon`：passed。
- `./gradlew.bat assembleRelease --console=plain --offline --no-daemon`：passed。
- `./gradlew.bat testDebugUnitTest assembleDebug --console=plain --offline --no-daemon`：比例适配编译通过；两项既有内容过滤测试失败，原因见上方缺陷记录。
- ADB `emulator-5554`：正式签名 APK 覆盖安装成功；横屏电视剧实播，系统配置保持 `land`，画面按比例完整显示且无拉伸或裁切。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-08-16 20:29 - 手机播放亮度与音量手势

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 手机端需要常见播放器的单手亮度和音量调节能力。
  - Purpose: 左半屏纵向滑动调节当前播放页窗口亮度，右半屏纵向滑动调节 `STREAM_MUSIC` 音量；上滑增加、下滑减少，并显示亮度百分比或音量级别。横向手势、单击、双击和长按维持原有行为，Media3 原生控件触摸优先。

- File path: `devLog/README.md`
  - Reason: 用户要求每次代码修改后记录开发内容。
  - Purpose: 在项目主时间线登记本次手机端播放器手势增强。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次属于播放器交互调整。
  - Purpose: 保存实现原因、涉及文件和验证结果。

## Bug Record

- Time: 2026-08-16 20:25
- Symptoms: 首次编译时，系统亮度回退值为浮点数，导致 Kotlin 无法推断亮度读取表达式的数值类型。
- Attempted fix: 将回退值改为整型系统亮度级别，保留最终换算为 0 到 1 的窗口亮度。
- Temporary solution: 无。

## Verification

- `./gradlew.bat testDebugUnitTest assembleDebug --console=plain --offline --no-daemon`：passed。
- `./gradlew.bat assembleRelease --console=plain --offline --no-daemon`：passed。
- ADB `emulator-5554`：正式签名 APK 覆盖安装成功；播放页左侧上滑后窗口属性 `sbrt=0.9037037`，返回详情页后该属性消失并恢复系统亮度；右侧上滑后 `STREAM_MUSIC` 从 0 调至 8/15，`dumpsys audio` 确认调用来源为 `com.tvbox.app`。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-08-03 07:53 - 默认主题恢复初始首页结构

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 默认主题不能只切换颜色，应恢复最初无侧栏的首页结构。
  - Purpose: 默认主题隐藏影院左栏和 Hero；影院主题保留现有影院布局。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Common.kt`
  - Reason: 默认主题需要初始版顶部快捷入口。
  - Purpose: 默认主题显示历史(1)、搜索(2)、推荐(3)、电视(4)、直播(5)、设置(6)，并恢复旧版按钮样式。

## Bug Record

- Time: 2026-08-03 07:53
- Symptoms: 默认主题此前只改变颜色，仍显示影院主题的左栏和 Hero 结构。
- Attempted fix: 按 `TvTheme` 分支恢复初始首页结构与顶部快捷入口。
- Temporary solution: 无。

## Verification

- `compileDebugKotlin --console=plain --offline --no-daemon`：passed。
- `assembleDebug --console=plain --offline --no-daemon`：passed。
- ADB：`emulator-5554` 已验证影院主题保留左栏，切换默认主题后显示初始六按钮顶部栏。

## 2026-08-03 07:32 - 首页分界线和电视图标尺寸修正

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 左侧栏与首页内容区之间需要明确的视觉分界。
  - Purpose: 增加 1dp 灰色竖向分界线。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Common.kt`
  - Reason: 项目电视图标字形在原尺寸下显示不完整。
  - Purpose: 将电视图标单独缩小到 22sp，其他图标保持原尺寸。

## Bug Record

- Time: 2026-08-03 07:32
- Symptoms: 左栏与内容区没有边界线；电视图标字形超出容器。
- Attempted fix: 增加灰色分界线，缩小电视图标。
- Temporary solution: 无。

## Verification

- `assembleDebug --console=plain --offline --no-daemon`：passed。
- ADB：`emulator-5554` 安装成功；已确认分界线可见，电视图标完整显示。

## 2026-08-03 07:24 - 项目图标和首页布局修正

## File Changes

- File path: `app/src/main/res/font/tvbox_iconfont.ttf`
  - Reason: `docs/icon/font` 提供了与项目匹配的字体图标代码。
  - Purpose: 在 Android 中加载 `home`、`tv`、`live`、`linggan_o`、`setting` 五个字形。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Common.kt`
  - Reason: 左侧栏应使用项目图标，且背景应与首页一致。
  - Purpose: 用字体图标替换自绘图标，并将导航栏背景改为首页背景色。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: Hero 底部“播放 / 详情”按钮文字显示不完整。
  - Purpose: 压缩 Hero 文案区垂直间距，保证按钮完整显示。

## Bug Record

- Time: 2026-08-03 07:24
- Symptoms: 左栏图标与项目资源不一致；左栏背景比首页深色层级不同；Hero 按钮文字被容器裁切。
- Attempted fix: 接入项目字体图标、统一背景色、收紧 Hero 布局。
- Temporary solution: 默认主题语义按用户要求暂不调整。

## Verification

- `compileDebugKotlin --console=plain --offline --no-daemon`：passed。
- `assembleDebug --console=plain --offline --no-daemon`：passed。
- ADB：`emulator-5554` 安装成功；已确认推荐为灯泡图标，播放 / 详情文字完整显示。

## 2026-07-08 19:44 - 直播手机触摸手势

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`
  - Reason: 用户希望手机端直播界面支持直观触摸切台和显示频道列表。
  - Purpose: 在直播播放器外层增加触摸手势：单击显示左侧频道列表；双击左半屏切换上一个频道；双击右半屏切换下一个频道。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录做了什么、为什么做。
  - Purpose: 在主时间线登记直播手机触摸手势。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次属于播放器和直播 UI 交互增强。
  - Purpose: 记录修改文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-07-08 19:44
- Symptoms: 无。本次为直播手机触摸交互新增。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat compileDebugKotlin --console=plain`
  - Result: passed.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-08-16 19:57 - Media3 原生播放器控制

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 自定义底部栏与 Media3 控制器重复，并会阻碍用户使用原生播放控件。
  - Purpose: 改为由 `PlayerView` 提供播放/暂停、时间轴、上/下一集和设置入口；保留手势和播放管家状态提示，并在控制器显示时将触摸交给原生控件、单击空白画面隐藏控制器后恢复手势。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: Media3 原生上/下一集需要使用播放列表，并将切换结果同步回应用状态。
  - Purpose: 增加原生切集同步和倍速状态同步，使详情页选集焦点、历史记录、播放管家和切线后的倍速保持一致。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录改动。
  - Purpose: 在主时间线加入原生播放器控制改造索引。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次属于播放器 UI 与交互基础设施调整。
  - Purpose: 记录实现原因、文件、测试过程和焦点问题修正。

## Bug Record

- Time: 2026-08-16 19:57
- Symptoms: 播放页同时显示自定义操作栏和 Media3 控制器；自定义触摸层还会影响原生控件的点击。
- Attempted fix: 移除自定义操作栏，将剧集组装为 Media3 播放列表；控制器显示时移除手势触摸监听，隐藏后恢复手势监听。
- Temporary solution: 无。

## Verification

- `./gradlew.bat compileDebugKotlin --console=plain --offline --no-daemon`：passed。
- `./gradlew.bat testDebugUnitTest assembleRelease --console=plain --offline --no-daemon`：passed。
- ADB `emulator-5554`：安装 release 包后，验证原生控制器、原生倍速菜单、原生下一集和返回详情页后第 02 集焦点同步。
- ADB `192.168.0.7:5555`：中途 release 包曾覆盖安装成功；最终包安装时设备状态为 `offline`，未重复尝试安装，等待设备重新连接。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`
## 2026-07-08 07:43 - 影院黑 + 活力绿 UI 风格

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/theme/Theme.kt`
  - Reason: 用户希望参考 `.codex/skills/awesome-design-md-main` 中适合 TVBox 的 UI 风格，并采用前次讨论的 Spotify 式暗色播放器方向。
  - Purpose: 将全局主题收敛为近黑背景、深灰层级、Spotify Green 风格主焦点色和柔和错误/提示色。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Focus.kt`
  - Reason: 电视遥控焦点需要在暗色背景上更清楚。
  - Purpose: 统一焦点边框为绿色，略微增强聚焦放大比例。

- File path: `app/src/main/java/com/tvbox/app/ui/components/Common.kt`
  - Reason: 首页头部按钮、分类和通用卡片是 TVBox 最高频视觉区域。
  - Purpose: 将头部按钮和分类改为深色胶囊按钮；海报卡片和历史卡片聚焦时使用深灰高亮。

- File path: `app/src/main/java/com/tvbox/app/ui/AiRecommendScreen.kt`
  - Reason: AI 找片页面需要与首页按钮和海报网格保持一致。
  - Purpose: AI 操作按钮改为胶囊按钮；AI 推荐卡片聚焦时高亮。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 设置页按钮此前与首页视觉不完全一致。
  - Purpose: 设置页操作按钮改为同样的深色胶囊和绿色焦点。

- File path: `app/src/main/java/com/tvbox/app/ui/DetailScreen.kt`
  - Reason: 选集按钮在遥控器移动时需要更明显的聚焦反馈。
  - Purpose: 选集按钮聚焦或选中时统一使用绿色主色。

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 播放器提示需要与新的功能焦点色一致。
  - Purpose: 倍速、手势和播放管家提示改用绿色状态文字。

- File path: `app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`
  - Reason: 直播频道列表和提示层需要融入整体播放器风格。
  - Purpose: 左侧频道列表使用近黑半透明面板，直播提示使用绿色状态文字。

- File path: `devLog/README.md`
  - Reason: 用户要求每次修改后记录做了什么、为什么做。
  - Purpose: 在主时间线登记本次 UI 风格统一。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次属于首页、播放器和通用 UI 风格调整。
  - Purpose: 记录涉及文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-07-08 07:43
- Symptoms: 无。本次为视觉风格统一。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `./gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`
## 2026-07-01 09:11 - 手机双击暂停修正

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 手机播放页之前双击只按左右两半处理，用户无法通过屏幕手势暂停播放。
  - Purpose: 将双击区域改成左/中/右三段；左侧快退 10 秒，中间播放/暂停，右侧快进 10 秒，并显示半透明提示。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录做了什么。
  - Purpose: 在主时间线加入手机双击暂停修正索引。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次属于播放器手势缺陷修正。
  - Purpose: 记录问题症状、修正方式和验证结果。

## Bug Record

- Time: 2026-07-01 09:11
- Symptoms: 手机播放时双击屏幕只能快退或快进，无法通过手势暂停播放。
- Attempted fix: 双击区域从左右二分改为左/中/右三分，中间区域执行播放/暂停切换。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat compileDebugKotlin --console=plain`
  - Result: passed.
- `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed. Only line-ending warnings were reported.

## 2026-08-03 06:44 - 第一套首页方案对齐

## File Changes

- `docs/design/tvbox-ui-directions.html`：归档可切换的三套首页设计原型。
- `docs/design/README.md`：说明第一套“影院 · 媒体库”方向与数字键快捷键。
- `app/src/main/java/com/tvbox/app/ui/components/Common.kt`：使用自绘 TV 导航图标；顶部仅保留首页摘要、搜索和历史，左栏不再重复搜索和历史。
- `app/src/main/java/com/tvbox/app/ui/theme/Theme.kt`：建立默认 / 影院两套动态色板。
- `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`：增加 `TvTheme` 枚举与 `AppSettings.theme`。
- `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`：读写主题存储键。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：增加 `updateTheme`，即时更新并保存设置。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`：增加主题选择控件，并保留首页数字键 1–6 快捷键。
- `app/src/main/java/com/tvbox/app/MainActivity.kt`：将当前主题传入 `TVBoxTheme`。

## Bug Record

- Time: 2026-08-03 06:44
- Symptoms: 设计稿位置不稳定；左栏文字符号与第一套图标方案不一致；首页出现重复入口；影院主题缺少设置切换入口。
- Attempted fix: 设计稿归档、左栏 Canvas 图标化、顶部入口精简、动态主题和设置持久化。
- Temporary solution: 无。

## Verification

- `compileDebugKotlin --console=plain --offline --no-daemon`：passed。
- `assembleDebug --console=plain --offline --no-daemon`：passed。
- ADB：`emulator-5554`（`1600x900`）安装成功；已验证首页、设置页、影院主题首页，以及数字键 `1` 打开历史、数字键 `6` 打开设置。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-07-01 08:23 - 手机播放手势完善

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 用户希望手机播放时具备更完整的常见视频手势。
  - Purpose: 在播放器触摸层增加单击、双击、横向滑动和长按的统一识别逻辑；单击切换底部控制栏，双击左右半区快退/快进 10 秒，横向滑动按当前影片时长计算目标进度并在松手时 seek。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录做了什么。
  - Purpose: 在主时间线加入手机播放手势完善索引。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次改动属于播放器 UI 手势增强。
  - Purpose: 记录文件、原因、目的、验证结果和 ADB 测试阻塞原因。

## Bug Record

- Time: 2026-07-01 08:23
- Symptoms: ADB 覆盖安装失败，设备返回 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，原因是手机上现有 `com.tvbox.app` 签名和本地构建包签名不一致。
- Attempted fix: 先尝试安装 debug APK，再构建并安装 release APK；release APK 仍被系统拒绝。
- Temporary solution: 不擅自卸载手机旧包，等待用户确认是否允许清除本地数据后再继续实机安装测试。

## Verification

- `.\gradlew.bat compileDebugKotlin --console=plain`
  - Result: passed.
- `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleRelease --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed. Only line-ending warnings were reported.
- ADB:
  - Device: `192.168.0.7:5555`
  - Install blocked: existing package signature mismatch.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

## 2026-06-30 21:57 - 手机播放与首页滚动交互

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 用户希望手机播放时可以像常见视频 App 一样长按屏幕 2 倍速播放。
  - Purpose: 在 `PlayerView` 上监听触摸长按，长按达到系统长按时间后临时设置播放器为 `2x`，松手或取消触摸后恢复用户原来的倍速。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 用户希望首页向下滑动时隐藏 `TVBox` 标题和影视分类，回到顶部后再显示。
  - Purpose: 将首页影片网格滚动状态提升到 `HomeScreen`，根据 `firstVisibleItemIndex` 和 `firstVisibleItemScrollOffset` 控制顶部内容显示。

- File path: `devLog/README.md`
  - Reason: 用户要求每次开发后记录做了什么。
  - Purpose: 在主时间线加入本次手机播放和首页滚动交互索引。

- File path: `devLog/home-player-ui.md`
  - Reason: 本次改动横跨首页和播放器 UI，需要独立分支文档记录。
  - Purpose: 记录文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-30 21:57
- Symptoms: 无新增缺陷；本次为交互增强。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed. Only line-ending warnings were reported.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/home-player-ui.md`

