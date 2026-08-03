# Home / Player UI - 2026-06-30

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

