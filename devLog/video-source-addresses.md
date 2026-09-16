# 影视线路地址整理 - 2026-08-19

## 2026-09-15 21:38 - 整理线路接入 TVBox

## 文件变更

- 文件路径：`app/src/main/java/com/tvbox/app/data/MacCmsApi.kt`
  - 原因：整理文档中的公开 MacCMS 线路尚未全部进入内置接口列表。
  - 目的：接入文档列出的 32 个视频来源；量子、如意、360、红牛、非凡等重复来源沿用已有 ID，360 与非凡的替代地址并入 `baseUrls` 回退列表。
- 文件路径：`app/src/main/java/com/tvbox/app/data/LiveRepository.kt`
  - 原因：文档记录的 YanG M3U 直播地址尚未被应用使用。
  - 目的：在现有直播源加载失败或返回空频道时，自动尝试 `https://tv.iill.top/m3u/Gather`。
- 文件路径：`app/src/main/java/com/tvbox/app/domain/LiveParser.kt`
  - 原因：现有解析器只支持 TVBox 逗号文本格式，不能读取标准 M3U。
  - 目的：识别 `#EXTINF`、`tvg-name` 和 `group-title`，按频道合并重复播放线路。

## Bug Record

- Time: 2026-09-15 21:38
- Symptoms: 无新增缺陷；本次为线路扩充。原有直播解析器无法读取 M3U，是接入备用直播源前发现的兼容性缺口。
- Attempted fix: 增加格式自动识别和独立 M3U 解析分支；直播仓库按地址顺序回退，并保留协程取消传播。
- Temporary solution: 无。

## 验证

- Release 编译与 R8 打包通过。
- 按用户要求未向各上游线路发起连通性或内容测试。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/video-source-addresses.md`
- Main reference: `影视线路地址整理.md`

## 目标

- 归档 ZIP0 视频卡片快速加载所采用或可推断的性能优化方法。
- 将 MoonTV 视频源页面公开的影视采集接口和直播源整理进统一文档。
- 保留页面原文地址与此前实测地址之间的差异，避免静默覆盖。

## 文件变更

- 文件路径：`影视线路地址整理.md`
  - 原 `ZIP0-十条影视线路地址.md` 更名为更通用的名称。
  - 新增 MoonTV 页面公开的 32 个影视采集源和 1 个直播源。
  - 新增 `cache_time: 7200`、来源重合关系及 360/非凡接口差异说明。
  - 新增首屏骨架、并发加载、渐进渲染、后端短缓存、请求合并、CDN、懒加载、去重和健康状态等优化记录。
- 文件路径：`devLog/README.md`
  - 增加本次文档整理的主时间线入口。
- 文件路径：`devLog/video-source-addresses.md`
  - 建立影视线路地址模块的详细变更记录和导航。

## 数据来源

- ZIP0：<https://zip0.com/category/movie>
- MoonTV 视频源：<https://blog.xiqi.site/archives/moontv-shi-pin-yuan>
- MoonTV 页面发布日期：2025-11-08。
- 本次读取日期：2026-08-19。

## 关键记录

- MoonTV 页面代码块包含 32 个 `api_site` 条目，10 个与 ZIP0 来源相同或等价，新增 22 个来源。
- MoonTV 的如意资源键为 `rycj`，ZIP0 的键为 `ruyi`，二者指向同一采集 API。
- MoonTV 记录的 360 API 为 `https://360zy.com/api.php/provide/vod`；ZIP0 对应来源此前实测为 `https://360zyzz.com/api.php/provide/vod`，两个版本均保留。
- MoonTV 还公开 YanG 直播源 `https://tv.iill.top/m3u/Gather` 及 EPG `https://epg.112114.xyz/pp.xml`。
- MoonTV 示例缓存时间为 7200 秒；ZIP0 的具体数据缓存层和 TTL 未公开，只记录实测证据与合理推断。

## 验证

- 已从浏览器页面代码块直接解析 JSON，确认 32 个影视源、1 个直播源和 `cache_time: 7200`。
- 已核对主文档包含 32 行 MoonTV 影视源、10 行 ZIP0 线路和性能优化章节。
- 所有修改的中文 Markdown 文件均需保存为 UTF-8 with BOM，并在修改后读取验证。

## Bug Record

- Time: 2026-08-19 08:00
- Symptoms: 无功能缺陷；补丁工具最初无法匹配带 BOM 文件的首行。
- Attempted fix: 临时移除 BOM 后应用内容补丁，完成后统一恢复 UTF-8 with BOM。
- Temporary solution: 无；最终编码验证作为固定收尾步骤。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/video-source-addresses.md`
- Main reference: `影视线路地址整理.md`
