# TVBox AI Dev Log

## Timeline

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
