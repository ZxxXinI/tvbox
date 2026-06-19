# 播放管家 Agent - 2026-06-19

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
