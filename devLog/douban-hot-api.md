# 豆瓣热门接口说明 - 2026-08-07 09:50

## 首页实现 - 2026-08-07 13:35

### File Changes

- `app/src/main/java/com/tvbox/app/data/DoubanHotRepository.kt`：新增豆瓣热播请求、响应映射与带有效期的缓存，实现低频分页读取和失败缓存。
- `app/src/main/java/com/tvbox/app/domain/DoubanHot.kt`：新增热播领域模型与片名精确/包含匹配规则，豆瓣条目不复用 MacCMS 影片 ID。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：首页默认切换到热播；点击卡片时才用当前选择的数据源搜索，资源未命中时留在首页提示。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`、`app/src/main/java/com/tvbox/app/ui/components/Common.kt`：新增热播分类、评分海报卡片、焦点样式、加载更多和可见错误提示。
- `app/src/main/java/com/tvbox/app/MainActivity.kt`：注入 SharedPreferences 热播缓存，使重新打开应用后可复用短期榜单数据。

### Reason / Purpose

- 豆瓣只负责热门榜单信息；MacCMS 线路才负责实际详情和播放地址。延迟查询避免首页一次性请求所有条目的资源站搜索接口。
- 接口/海报失败时回退到当前线路最近更新，保证没有网络波动时首页仍能进入正常观影流程。

### Bug Record

- Time: 2026-08-07 13:35
- Symptoms: 无。
- Attempted fix: 不适用；本次为功能开发。
- Temporary solution: 无。

### Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/douban-hot-api.md`

## 文档说明 - 2026-08-07 09:50

### File Changes

- `docs/douban-hot-api.md`：新增 Android TVBox 的豆瓣热门剧集/综艺接口使用说明，包含请求头、分类、分页、Kotlin 映射、图片防盗链、缓存与失败处理。
- `devLog/README.md`：新增本次文档变更的时间线入口。

## Bug Record

- Time: 2026-08-07 09:50
- Symptoms: 无。
- Attempted fix: 不适用；本次为跨项目接口使用文档。
- Temporary solution: 无。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/douban-hot-api.md`
