# 播放管家 Agent - 2026-06-19

## 2026-06-19 18:18 - 第二阶段：可控与可见

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`
  - Reason: 播放管家自动换线需要用户可控开关。
  - Purpose: 增加 `playbackAgentAutoSwitchEnabled` 设置项，默认开启。

- File path: `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`
  - Reason: 新增设置项需要持久化。
  - Purpose: 使用 SharedPreferences 保存“播放管家自动换线”开关。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`
  - Reason: 设置页需要暴露播放管家的可控入口。
  - Purpose: 新增“播放管家 / 自动换线”设置项，关闭后提示仍可手动换线。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 自动换线和手动换线需要不同执行规则。
  - Purpose: 自动触发时尊重设置开关；手动换线不受开关影响；继续复用播放管家决策器。

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: 用户需要在播放器内主动切换线路，并理解自动换线是否关闭。
  - Purpose: 新增“手动换线”按钮；自动换线关闭时播放失败只提示，不自动切线。

- File path: `app/src/main/java/com/tvbox/app/ui/DetailScreen.kt`
  - Reason: 播放管家的线路判断需要在详情页可见。
  - Purpose: 在线路标签中显示“推荐 / 近期失败 / 较慢”状态。

- File path: `app/src/main/java/com/tvbox/app/domain/PlaybackAgent.kt`
  - Reason: 详情页需要复用近期失败/卡顿判断。
  - Purpose: 暴露播放健康冷却时间和 `recentIssueType`，供 UI 显示状态。

- File path: `app/src/test/java/com/tvbox/app/domain/PlaybackAgentTest.kt`
  - Reason: 新增可见状态依赖近期问题类型判断。
  - Purpose: 增加近期失败和较慢状态判断测试。

- File path: `CHANGELOG.md`
  - Reason: 需要记录本次未发布功能，便于后续发布版本整理。
  - Purpose: 增加播放管家第二阶段说明。

## Bug Record

- Time: 2026-06-19 18:18
- Symptoms: 无新增缺陷；实现过程中未发现编译或测试失败。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/playback-agent.md`

## 2026-06-19 10:13 - 播放管家第一阶段

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/PlaybackAgent.kt`
  - Reason: 原播放失败切线逻辑只在播放器 UI 内临时判断，缺少可复用的规则决策层。
  - Purpose: 新增规则型播放管家 Agent，负责根据本轮已失败线路、历史健康记录和同一集可用地址选择下一条线路。

- File path: `app/src/main/java/com/tvbox/app/data/PlaybackHealthRepository.kt`
  - Reason: 播放管家需要记住线路近期失败、缓冲和成功状态，不能只依赖当前播放器内存状态。
  - Purpose: 使用 SharedPreferences 持久化线路健康记录，供下一次播放时跳过近期不稳定线路。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: ViewModel 是播放器状态、线路切换和数据仓库之间的协调层。
  - Purpose: 加载播放健康快照，记录失败/缓冲/成功事件，并调用播放管家 Agent 执行同集线路切换。

- File path: `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`
  - Reason: ExoPlayer 的错误、缓冲和就绪事件需要喂给播放管家。
  - Purpose: 播放失败时触发 Agent 切线；缓冲超过 15 秒时自动切线；播放成功时记录线路恢复；底部显示“播放管家”提示。

- File path: `app/src/main/java/com/tvbox/app/MainActivity.kt`
  - Reason: 新增播放健康仓库后需要注入到 ViewModel。
  - Purpose: 创建 `SharedPlaybackHealthRepository` 并传入 `TvBoxViewModel`。

- File path: `app/src/test/java/com/tvbox/app/domain/PlaybackAgentTest.kt`
  - Reason: 播放管家决策会直接影响播放体验，需要锁住核心规则。
  - Purpose: 覆盖跳过本轮失败线路、避开近期失败线路、成功恢复健康状态、无可用线路不切换等规则。

- File path: `CHANGELOG.md`
  - Reason: 需要记录本次未发布功能，便于后续发布版本整理。
  - Purpose: 增加播放管家第一阶段和播放提示优化说明。

- File path: `devLog/README.md`
  - Reason: 用户要求每次完成后将开发记录放入 `devLog` 文件夹。
  - Purpose: 作为主时间线，索引每次 AI 辅助开发记录。

- File path: `devLog/playback-agent.md`
  - Reason: 播放管家是独立模块，需要分支文档记录详细变更。
  - Purpose: 记录播放管家相关文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-19 10:13
- Symptoms: 无新增缺陷；实现过程中未发现编译或测试失败。
- Attempted fix: 不适用。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.
- `git diff --check`
  - Result: passed, only existing Windows line-ending warnings.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/playback-agent.md`
## 2026-06-19 18:42 - 第三阶段：播放前智能择线

## File Changes

- File path: `app/src/main/java/com/tvbox/app/domain/PlaybackAgent.kt`
  - Reason: 第二阶段只在播放失败后切线，进入播放前仍可能先尝试近期失败线路。
  - Purpose: 新增 `selectBestSource` 与线路健康评分，按近期成功、近期失败/缓冲和当前选择计算更稳定的播放源。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 播放入口需要在进入播放器前调用播放管家，而不是等 ExoPlayer 报错后才处理。
  - Purpose: 详情加载和打开播放器时使用智能择线；自动换线关闭时继续尊重用户原始线路。

- File path: `app/src/main/java/com/tvbox/app/ui/DetailScreen.kt`
  - Reason: 详情页“推荐”标签需要和实际播放前择线结果一致。
  - Purpose: 线路标签复用 `PlaybackAgent.selectBestSource`，避免 UI 推荐和真实播放选择分叉。

- File path: `app/src/test/java/com/tvbox/app/domain/PlaybackAgentTest.kt`
  - Reason: 播放前择线会影响默认播放线路，需要用单元测试锁住核心规则。
  - Purpose: 覆盖无健康记录时尊重当前选择、优先近期成功线路、避开近期失败线路。

- File path: `CHANGELOG.md`
  - Reason: 需要记录未发布功能，便于后续发版整理。
  - Purpose: 增加播放管家第三阶段说明。

- File path: `devLog/README.md`
  - Reason: 用户要求开发记录放在 `devLog` 文件夹下。
  - Purpose: 在主时间线加入第三阶段索引。

- File path: `devLog/playback-agent.md`
  - Reason: 播放管家是独立模块，需要记录分阶段设计和变更明细。
  - Purpose: 记录第三阶段文件、原因、目的和验证结果。

## Bug Record

- Time: 2026-06-19 18:42
- Symptoms: 无新增缺陷；实现过程中发现评分函数需要复用实例冷却时间，避免测试或后续配置时排序规则不一致。
- Attempted fix: 将评分函数显式接收 `cooldownMs`，由 `PlaybackAgent` 实例传入。
- Temporary solution: 不适用。

## Verification

- `.\gradlew.bat testDebugUnitTest --console=plain`
  - Result: passed.
- `.\gradlew.bat assembleDebug --console=plain`
  - Result: passed.

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/playback-agent.md`
