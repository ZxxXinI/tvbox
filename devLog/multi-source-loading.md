# 多来源影视加载优化 - 2026-08-25

## 目标

将 TVBox 的搜索与详情流程从单来源等待或全量等待，调整为适合电视端网络与性能条件的受控并行和渐进呈现。

## 文件变更

- `plan/影视多来源加载优化方案.md`
  - 记录现状、定制策略、超时/缓存参数、验收标准和后续边界。
- `app/src/main/java/com/tvbox/app/data/MovieRepository.kt`
  - 新增多来源搜索与详情渐进更新流。
  - 搜索和替代播放源匹配均限制为最多 3 条来源并行。
  - 新增 3 秒搜索超时、4 秒替代详情超时、5 分钟列表缓存、30 分钟详情缓存和 2 分钟失败冷却。
  - 保留单一来源的备用网址顺序回退；协程取消会立即传播，不再被视作接口异常。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - 搜索结果根据来源完成情况增量写入 UI 状态。
  - 详情页收到主来源后立即结束首屏加载，后续来源只补充播放线路。
- `app/src/main/java/com/tvbox/app/ui/SearchScreen.kt`
  - 搜索过程中保留已返回卡片，显示已完成线路数和结果数。
  - 修正跨来源结果卡片的 Compose key，使用 `apiLineId + movieId` 避免 ID 冲突。
- `app/src/main/java/com/tvbox/app/ui/DetailScreen.kt`
  - 播放线路区显示后台补线进度。
- `app/src/test/java/com/tvbox/app/data/MovieRepositoryTest.kt`
  - 新增多来源去重、主来源优先、内存缓存、3 路并发上限和详情渐进补线测试。
- `devLog/README.md`
  - 增加本模块的主时间线入口。

## 设计边界

- 首页分类继续只使用当前主来源，避免不同 MacCMS 来源的分类 ID 和分类语义混合。
- 多来源聚合只应用于关键词搜索和详情页补齐播放线路。
- 缓存、冷却和健康评分仅保留在进程内；不持久化播放 URL 或用户敏感数据。
- 当前不增加自建服务端、数据库或新的设置项。

## 验证

- `:app:testDebugUnitTest --tests com.tvbox.app.data.MovieRepositoryTest`：通过。
  - 覆盖去重、主来源优先、列表/分类缓存、最大并发 3 和详情渐进补线。
- `:app:assembleDebug`：通过。
  - Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，43,463,913 字节。
- ADB 设备 `emulator-5554`（`2304FPN6DG`，Android 9）：通过。
  - 用户卸载签名不兼容的旧版后，已安装 Debug `1.3.5 / 10305`。
  - `MainActivity` 正常恢复到前台，首屏未发现新的崩溃日志。
  - 使用关键词 `Avatar` 实测：8/8 条影视线路完成，展示 16 张去重结果卡片。
  - 打开结果详情后，最终聚合出量子、如意、红牛三条播放线路；应用进程保持正常。
- 全量 `:app:testDebugUnitTest`：56 项中 54 项通过；两项既有失败不在本次修改文件中。

## Bug Record

- Time: 2026-08-25 07:13
- Symptoms: 全量测试中的 `DoubanHotRepositoryTest.parsesHotItemsAndFiltersBlockedContent` 和 `PlaybackParserTest.filtersBlockedCategoriesAndMovies` 失败。
- Attempted fix: 核对失败断言位置与本次变更范围；两项均位于豆瓣热播解析和内容过滤既有测试，本次未修改对应代码。
- Temporary solution: 保留为既有回归问题；本次新增的 `MovieRepositoryTest` 单独通过，Debug APK 已成功构建。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/multi-source-loading.md`
- Plan: `plan/影视多来源加载优化方案.md`
