# 豆瓣最近热门接口使用说明（Android TVBox）

本文用于 TVBox 的「热门剧集 / 热门综艺」榜单。豆瓣只提供片名、海报、评分和摘要；点击卡片后，应用仍要使用当前 MacCMS 数据源按片名查找可播放资源。

## 接口地址

基础地址固定为：

```text
https://m.douban.com/rexxar/api/v2/subject/recent_hot/tv
```

热门综艺也使用末尾的 `/tv` 路径。剧集或综艺由参数区分，不能改为 `/recent_hot/show`。

| 参数 | 说明 | 示例 |
| --- | --- | --- |
| `start` | 起始偏移，第一页为 `0` | `0`、`20`、`40` |
| `limit` | 单页数量，建议固定 | `20` |
| `category` | 榜单大类 | `tv`、`show` |
| `type` | 具体频道 | 见分类表 |

```text
# 热门剧集综合
https://m.douban.com/rexxar/api/v2/subject/recent_hot/tv?start=0&limit=20&category=tv&type=tv

# 热门综艺综合
https://m.douban.com/rexxar/api/v2/subject/recent_hot/tv?start=0&limit=20&category=show&type=show
```

## 必需请求头

裸请求可能返回 `400` 和 `invalid_request_1284`。当前可用请求头：

```kotlin
private const val DOUBAN_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 9; TVBox) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

fun doubanRequest(url: String): Request = Request.Builder()
    .url(url)
    .header("User-Agent", DOUBAN_USER_AGENT)
    .header("Referer", "https://movie.douban.com/tv/")
    .header("Origin", "https://movie.douban.com")
    .header("Accept", "application/json, text/plain, */*")
    .build()
```

当前接口不要求登录 Cookie。不要使用账号 Cookie、验证码处理或高频重试；这是未公开承诺稳定性的页面接口，应保持低频访问并准备回退数据。

## 分类参数

### 热门剧集

| UI 名称 | `category` | `type` |
| --- | --- | --- |
| 综合 | `tv` | `tv` |
| 国产剧 | `tv` | `tv_domestic` |
| 欧美剧 | `tv` | `tv_american` |
| 日剧 | `tv` | `tv_japanese` |
| 韩剧 | `tv` | `tv_korean` |
| 动画 | `tv` | `tv_animation` |
| 纪录片 | `tv` | `tv_documentary` |

### 热门综艺

| UI 名称 | `category` | `type` |
| --- | --- | --- |
| 综合 | `show` | `show` |
| 国内 | `show` | `show_domestic` |
| 国外 | `show` | `show_foreign` |

客户端只允许上表中的参数组合，不让页面参数直接拼接任意 `category` 或 `type`。

## 分页

每页固定读取 20 条：

```kotlin
val page = requestedPage.coerceAtLeast(1)
val start = (page - 1) * 20
val pageCount = maxOf(1, ceil(total / 20.0).toInt())
```

第 1、2、3 页的 `start` 分别为 `0`、`20`、`40`。

## 响应字段与 Kotlin 映射

典型响应：

```json
{
  "total": 243,
  "items": [{
    "id": "37814458",
    "title": "示例剧集",
    "pic": { "large": "https://img1.doubanio.com/view/photo/m_ratio_poster/public/example.jpg" },
    "rating": { "count": 9732, "value": 8.5 },
    "card_subtitle": "2026 / 中国大陆 / 剧情"
  }]
}
```

```kotlin
@Serializable
data class DoubanHotResponse(
    val total: Int = 0,
    val items: List<DoubanHotItem> = emptyList(),
)

@Serializable
data class DoubanHotItem(
    val id: String = "",
    val title: String = "",
    val pic: DoubanPicture? = null,
    val rating: DoubanRating? = null,
    @SerialName("card_subtitle") val cardSubtitle: String = "",
)

@Serializable
data class DoubanPicture(val large: String = "", val normal: String = "")

@Serializable
data class DoubanRating(val count: Int = 0, val value: Double = 0.0)
```

| 豆瓣字段 | TVBox 字段 | 用途 |
| --- | --- | --- |
| `id` | `doubanId` | 列表稳定 key，不用于播放 |
| `title` | `name` | 点击后搜索 MacCMS |
| `pic.large` | `coverUrl` | 海报 |
| `rating.value` | `score` | 大于 0 时显示“豆瓣 8.5” |
| `rating.count` | `ratingCount` | 可选展示 |
| `card_subtitle` | `subtitle` | 年份、地区、类型摘要 |

公开列表继续使用现有过滤规则：标题或摘要命中 `伦理`、`电影解说` 时不展示。

## 海报加载

豆瓣海报有防盗链：无 Referer 可能返回 `418`，普通应用来源可能返回 `403`。加载 `*.doubanio.com/view/photo/...` 时也要携带豆瓣 Referer：

```kotlin
val imageRequest = ImageRequest.Builder(context)
    .data(item.pic?.large)
    .header("Referer", "https://movie.douban.com/tv/")
    .header("User-Agent", DOUBAN_USER_AGENT)
    .build()
```

上例适用于 Coil；使用 Glide 或自定义 OkHttp 图片加载器时，同样只对 `*.doubanio.com` 图片加这两个请求头。图片失败时显示片名占位海报，不能影响卡片点击和资源搜索。

## 缓存、失败与回退

建议缓存键：

```text
douban-hot:{category}:{type}:start:{start}:limit:20
```

| 场景 | 建议 |
| --- | --- |
| 成功榜单 | 缓存 `20 小时 + 10~20 分钟随机时间` |
| 请求失败 | 只缓存 `5~7 分钟`，避免持续高频重试 |
| 接口超时 | 10 秒超时，并显示重试入口 |
| 首页榜单失败 | 回退到当前首页 MacCMS 源的最近更新 |
| 独立热播页失败 | 显示“豆瓣热播暂时不可用，请稍后重试” |
| 图片失败 | 显示片名占位图，保持点击能力 |

每个榜单页只在首次打开、下拉刷新、切换分类或翻页时请求一次；不要为每张海报重复请求榜单。

## 点击后的资源匹配

豆瓣不提供播放地址。卡片点击流程：

1. 读取当前首页 MacCMS 数据源。
2. 用豆瓣 `title` 调用 MacCMS `wd` 搜索。
3. 规范化片名后优先精确匹配。
4. 找到资源则进入现有详情页，并聚合可用播放线路。
5. 找不到时停留在热播页，提示“暂无该视频资源”。

## 常见问题

| 现象 | 原因与处理 |
| --- | --- |
| `invalid_request_1284` | 检查 `User-Agent`、`Referer`、`Origin`、`Accept`。 |
| 热门综艺失败 | 检查路径仍为 `/recent_hot/tv`，参数为 `category=show&type=show`。 |
| 海报占位 | 给豆瓣图片请求增加 Referer；保留占位图。 |
| 接口暂时不可用 | 使用短失败缓存、首页 MacCMS 回退和用户可见重试；不要尝试验证码绕过。 |
| 点击后没有资源 | 豆瓣只提供榜单，表示当前 MacCMS 源未收录该片。 |

## 使用边界

- 本接口是豆瓣网页使用的接口，不是保证长期稳定的公开 API。
- 不加入登录 Cookie、验证码自动化、Cookie 池或高频轮询。
- 仅用于用户确认有权使用的影视元数据展示；播放地址仍来自用户配置的 MacCMS 资源接口。
