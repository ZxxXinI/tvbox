# Home / Player UI - 2026-06-30

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

