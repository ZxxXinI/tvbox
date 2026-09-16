# 内容过滤 - 2026-09-17

## 2026-09-17 07:24 - 恢复伦理与电影解说过滤

### 文件变更

- `app/src/main/java/com/tvbox/app/domain/ContentFilter.kt`：将错误的单字母 `x` 规则恢复为“伦理”和“电影解说”，供分类、影片、豆瓣热播和观看历史共用。
- `app/src/main/java/com/tvbox/app/data/MacCmsDtos.kt`：根据本次响应的分类树屏蔽子分类，并过滤只带分类 ID 而没有类别名称的影片。
- `app/src/main/java/com/tvbox/app/domain/AiRecommendation.kt`：AI 返回结果在客户端按片名、类型和搜索词再次过滤，不能只依赖提示词。
- `app/src/test/java/com/tvbox/app/domain/ContentFilterTest.kt`、`AiRecommendationParserTest.kt`、`PlaybackParserTest.kt`：覆盖两类屏蔽内容、英文 X 误伤、AI 漏拦截、分类树继承过滤，并更新扩充线路后的断言。

### Bug Record

- Time: 2026-09-17 07:24
- Symptoms: 伦理片、电影解说仍可能出现在分类、片单或 AI 推荐中；含字母 `x` 的正常英文片名可能被误过滤。
- Attempted fix: 追溯到历史提交将过滤词从“伦理、电影解说”误改为 `x`，恢复规则并增加分类树与推荐结果兜底。
- Temporary solution: 无。

### 验证

- `:app:testDebugUnitTest`：passed，61 项测试通过。
- `:app:assembleRelease`：passed，Release 的 vital lint、R8 与打包通过。
- 未执行设备功能测试。

### Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/content-filter.md`
