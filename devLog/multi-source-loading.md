# 多来源影视加载优化 - 2026-08-25

## 2026-11-27 11:16 - 首页数据源测速与继续观看

- `HomeCatalog.kt` 和 `MovieRepository.kt` 用实际首页首屏测量接口耗时。请求绕过缓存，最多 6 个并行，每个限 4 秒，区分可用、超时、请求失败和空片单。
- 可用结果按耗时排序。关闭弹窗取消未完成的请求，首页首选源及现有故障回退由原有加载流程管理。
- 单元测试覆盖缓存旁路、状态、并发上限、取消及推荐顺序。模拟器用两条受控接口验证首页和设置的推荐焦点。
- [返回主日志](README.md)

## 2026-11-20 10:05 - 区分搜索无匹配与视频源请求失败

- `app/src/main/java/com/tvbox/app/data/MovieRepository.kt` 分开统计响应正常和请求失败的来源。成功返回空列表也计作正常响应，主动重试会重新查询处于冷却状态的来源。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 和 `SearchScreen.kt` 分别显示全失败、无匹配和部分失败。有结果时继续显示片单及失败线路数。
- `app/src/test/java/com/tvbox/app/data/MovieRepositoryTest.kt` 和 `app/src/test/java/com/tvbox/app/ui/TvBoxBrowseRecoveryTest.kt` 用受控来源覆盖计数、重试和部分结果保留。

## 2026-11-20 09:25 - 首页视频源自动切换

用户反馈首选源量子失效时，首页直接显示请求超时。首页影片目录现在从当前源开始，按视频源列表顺序循环尝试，每个源的首屏请求最多等待 4 秒。单个来源超时、请求异常或没有可显示影片时继续尝试，全部试完才显示失败和重试入口。豆瓣热播接口失败、没有热播内容，或热播可用但当前源分类无法加载时，也会尝试影片目录。

- `app/src/main/java/com/tvbox/app/data/HomeCatalog.kt` 处理有序候选、单源超时和取消，并将原来的分类 13 限定在量子。
- `app/src/main/java/com/tvbox/app/data/MovieRepository.kt` 提供首页目录请求。重试首屏时重新请求此前返回空列表的源。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 在同一个首页任务内处理豆瓣、目录及分类，把实际来源、影片和分类一并应用。分类和翻页使用实际来源，详情重试使用影片自身的来源。
- `app/src/main/java/com/tvbox/app/ui/SettingsScreen.kt` 读取已保存的首选源。自动切换只更新本次会话的实际来源。
- `app/src/test/java/com/tvbox/app/data/MovieRepositoryTest.kt` 用受控响应覆盖首选正常、故障切换与翻页、超时和空响应、循环、重试及取消。

### 验证

- 2026-11-20 09:28 完成全量 118 项单元测试，含 5 项首页切源回归。Release 构建和 Release 关键 Lint 均通过。
- 签名测试包为 `TVBox-v1.3.14-home-source-fallback-test.apk`（5099969 字节），版本为 1.3.14（10314）。APK 签名和 ZIP 对齐校验通过，SHA-256 为 `e99082961e24b43a6e401e266276fa34cb03ddae1d0bc36de630de24216d2af5`。
- 电视盒子与真实视频源的故障场景待验收。

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
