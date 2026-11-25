# 平台直播（斗鱼）- 2026-08-01

## 2026-11-20 11:23 - 影院主题直播浏览界面

- 将 `DefaultPlatformLiveScreen.kt` 扩展至共用的 `app/src/main/java/com/tvbox/app/ui/PlatformLiveBrowserScreen.kt`。影院主题宽屏采用左侧平台及收藏栏，右侧显示分类标题、搜索和二级分类标签。窄屏的平台入口改为横排，默认主题保留原横排结构。
- `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt` 让两种主题复用房间列表、搜索、分页与收藏。影院主题宽屏三列，默认主题宽屏四列。收藏星标位于右下角，播放器返回后按房间定位。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 统一平台和分类的选择与恢复，直播页显示主题内的平台导航，回到首页后恢复影院主导航。
- `app/src/main/java/com/tvbox/app/ui/theme/Theme.kt` 移除旧多层直播列表的列数设置。列数由房间区域实际宽度和主题决定。
- `app/src/test/java/com/tvbox/app/ui/TvBoxBrowseRecoveryTest.kt` 验证两种主题的首屏、播放返回、收藏返回、分类切换和重返直播，同时验证影院主导航的显示范围。

### 验证

- 2026-11-20 11:23 完成全量 133 项单元测试、Release 构建及 Release 关键 Lint。APK 签名、ZIP 对齐和版本 1.3.14（10314）校验通过。
- 签名包为 `app/build/outputs/tvbox-test-apks/TVBox-v1.3.14-cinema-live-theme-test.apk`，SHA-256 为 `666686b99c7f236f14ba54d2a79ae723f1a09fb19e5c10acb07d7e9e9729b38b`。
- 盒子上的排版和遥控器焦点待实机验收。

## 2026-11-20 11:06 - 默认主题直播浏览界面

- `app/src/main/java/com/tvbox/app/ui/DefaultPlatformLiveScreen.kt` 落地默认主题方案。顶部平台使用横向排列，一级分类用下拉，二级分类用可横向滚动的标签。收藏在标题右侧，搜索与分类标题在宽屏同行。
- `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt` 复用房间卡片、查询、收藏、分页和播放器返回定位。默认主题宽屏四列，较窄时调整为三列或两列。标题最多两行，星标位于右下角。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 首次进入选中首个平台，并打开该平台首个有二级分类的分组。在同一次应用运行中重新进入，或从收藏返回时恢复之前的平台和分类。平台或分类切换立即显示加载状态，取消或晚到的响应不会覆盖当前选择。
- `app/src/test/java/com/tvbox/app/ui/TvBoxBrowseRecoveryTest.kt` 验证进入直播、跳过空一级分类、切换平台及分类、收藏返回和再次进入。

### 验证

- 2026-11-20 11:07 完成全量 133 项单元测试、Release 构建和 Release 关键 Lint。APK 签名、ZIP 对齐及版本 1.3.14（10314）均已校验。
- 签名包为 `app/build/outputs/tvbox-test-apks/TVBox-v1.3.14-default-live-theme-test.apk`，SHA-256 为 `de6d90db733e8c94edaced9dc081e2cdc56cd009c316d31e2ec45f4d99515c5a`。
- 新界面的排版和遥控器焦点待实机验收。

## 2026-11-20 10:24 - 二级分类房间搜索

- `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt` 在二级分类房间列表上方添加「用户名或标题」、搜索和清除。支持输入法搜索键，查询进度和无匹配状态区分已查页面与完整分类。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 匹配主播名和标题，忽略英文大小写及关键词首尾空白。复用当前平台分类的房间分页，每批最多请求后续 5 页。搜索错误保留已有结果，清除后回到已加载的全部房间。
- 搜索结果打开播放器后，上一个和下一个直播间沿用匹配结果，返回房间列表保留查询。退出二级分类会清空查询。
- `app/src/test/java/com/tvbox/app/ui/TvBoxBrowseRecoveryTest.kt` 用 8 页假数据验证主播名在第 3 页命中、直播标题在第 8 页命中、单批页数及清除搜索。

### 验证

- 2026-11-20 10:24 完成 Release 构建与签名、ZIP 对齐和版本校验。使用同签名覆盖安装至 `emulator-5554`，安装包与本地 SHA-256 一致。
- 签名包为 `app/build/outputs/tvbox-test-apks/TVBox-v1.3.14-live-room-search-test.apk`，SHA-256 为 `d3400f3a2f50d845b64ad36a6b1405573e58f9a86a78d8f729f0a15ba37e38cb`。
- 全量 132 项单元测试和 Release 关键 Lint 通过。受控测试覆盖跨页匹配。
- 模拟器使用斗鱼「网游竞技 → 英雄联盟」真实分类。主播名片段 `MQ` 和标题片段 `BLG` 均命中，找不到房间时显示空结果，清除后恢复房间列表。带关键词返回后进入「热门游戏」，搜索条件清空。遥控器方向键可从首个房间聚焦搜索框。模拟器所用分类的测试数据只有一页。
- 截图见 `app/build/outputs/room-search-qa/match.png`、`title-match.png`、`no-match.png`、`cleared.png` 和 `other-category.png`。

## 2026-11-20 10:05 - 平台直播全屏与房间焦点

- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt` 和 `TvBoxViewModel.kt` 在平台直播播放器中隐藏影院主题导航，返回浏览页面后恢复导航。
- `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt` 复用 `PlayerViewport.kt` 的系统栏控制。首次进入房间列表时聚焦初始房间，从播放页返回时聚焦当前房间，翻页追加保持当前焦点。
- `app/src/test/java/com/tvbox/app/ui/TvBoxBrowseRecoveryTest.kt` 覆盖影院主题下浏览与播放的导航栏状态。系统栏和遥控器焦点待实机验收。

## 2026-11-20 09:46 - 房间列表翻页失败可原位重试

- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt` 区分直播房间首屏错误与追加页错误。追加失败保留当前分类、最后成功的页码和已有房间，重试只请求失败页。
- `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt` 追加错误显示在底部原控件。加载和失败期间保留该控件的焦点，成功追加时不重新定位房间。
- `app/src/test/java/com/tvbox/app/ui/TvBoxBrowseRecoveryTest.kt` 模拟第二页失败和重试，验证请求顺序为第 1 页、第 2 页、第 2 页。真机焦点待验收。

## 2026-09-17 06:52 - 直播播放亮屏与房间收藏

### 文件变更

- `app/src/main/java/com/tvbox/app/ui/KeepScreenOnWhileVisible.kt`、`LiveScreen.kt`、`PlatformLiveScreen.kt`：直播播放页可见时保持屏幕亮起，离开后恢复原状态；收藏卡片和房间星星具备独立触控与遥控器焦点。
- `app/src/main/java/com/tvbox/app/data/PlatformLiveFavoritesRepository.kt`：用设备私有 SharedPreferences 保存收藏房间快照，以平台和房间 ID 去重，不保存过期播放地址。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`、`app/src/main/java/com/tvbox/app/MainActivity.kt`：接入收藏仓库、收藏列表状态、播放来源返回路径和本地刷新。
- `app/src/test/java/com/tvbox/app/data/PlatformLiveFavoritesRepositoryTest.kt`：验证跨平台同房间号不冲突、再次点击取消收藏、恢复元数据不误用旧人气。
- `README.md`：更新用户可见功能说明。

### Bug Record

- Time: 2026-09-17 06:52
- Symptoms: 手机观看直播时系统自动锁屏生效；平台直播房间无法在本机收藏并集中查看。
- Attempted fix: 播放页通过 Compose 视图的 keepScreenOn 生命周期保持亮屏；新增本机收藏存储与 UI/导航接线，平台列表失败时保留收藏入口。
- Temporary solution: 无。

### 验证

- `:app:compileDebugKotlin` 与 `:app:assembleDebug`：通过。
- `PlatformLiveFavoritesRepositoryTest` 与 `LivePlaybackWatchdogTest` 定向测试：通过。
- `:app:testDebugUnitTest`：59 项中 3 项失败，位于影视/豆瓣解析测试；本次未修改这些测试或相关解析代码，但未核对历史基线。
- 未进行手机/电视实机功能测试；本次不构建或发布新版 Release。

### Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/platform-live.md`

## 2026-08-02 - v1.3.0 服务端部署与构建配置

### 文件变更

- `platform_live_server/README.md`：补充 Docker 部署入口和 Node.js/Python 运行边界。
- `README.md`：补充五平台直播说明、服务端部署文档链接和 `TVBOX_PLATFORM_LIVE_SERVICE_URL` 构建命令。
- `app/build.gradle.kts`：将 Android 版本升级为 `1.3.0` / `10300`。

### 验证

- 使用 `TVBOX_PLATFORM_LIVE_SERVICE_URL=http://20.205.10.127:8868` 进行 Debug 构建。
- 未将服务地址写入源码、Cookie 或服务器配置文件。

## 2026-08-02 13:31 - 无 Python 主机的部署路径说明

### 文件变更

- `platform_live_server/DEPLOYMENT.md`：补充当前服务不能由 Node.js 单独启动的架构说明，并新增 Node.js 基础镜像内置 Python 的 Docker 部署方案。

### 目的

- 避免服务器 AI 错误执行 `node server.py` 或临时重写适配器。
- 在服务器主机没有 Python 时，通过 Docker 保持已验证的 Python 五平台解析实现不变。

## 2026-08-02 - 多平台通用 UI 兜底文案

### 文件变更

- `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`：移除房间列表和二级分类卡片中写死的“斗鱼”兜底文案，改为当前平台名称或通用“直播/直播分类”。

### 缺陷记录

- 时间：2026-08-02
- 症状：虎牙、哔哩哔哩、抖音或快手返回空父分类名称时，通用直播页面可能显示“斗鱼直播/斗鱼分类”。
- 修复位置：`PlatformLiveScreen.kt` 的房间列表副标题和二级分类卡片。
- 临时方案：无，已改为按当前平台动态兜底。

# 多平台直播适配 - 2026-08-02

## 统一服务端适配

### 文件变更

- `platform_live_server/platform_common.py`：新增统一分类树、房间分页、播放候选和解析异常数据模型。
- `platform_live_server/bilibili_resolver.py`：接入 B 站 `getList`、`getRoomList`、WBI 签名和最高 `qn` 多 CDN 播放地址。
- `platform_live_server/huya_resolver.py`：接入虎牙固定大分类、`bussLive`、`cache.php`、`HNF_GLOBAL_INIT`、anti-code 与最小 TUP3/TARS token 解析。
- `platform_live_server/douyin_resolver.py`：接入抖音 `categoryData`、分区房间列表、稳定 `webRid`、API/网页回退和最高等级 FLV/HLS 地址。
- `platform_live_server/douyin_sign.js`：从参考项目封装 `kABogus` 为 Node.js 标准输入输出助手。
- `platform_live_server/kuaishou_resolver.py`：接入快手固定大分类、分类分页、`__INITIAL_STATE__`、`playUrls` 质量分组和 `kwfv1`/`Kww`。
- `platform_live_server/server.py`：注册五个平台，保留原有接口和旧响应字段，缓存键改为 `(site, room_id)`，读取五个平台可选 Cookie 环境变量。
- `platform_live_server/test_platform_resolvers.py`：新增跨平台字段映射、签名、TARS、质量选择和缓存隔离回归测试。

### 验证

- `python -m unittest discover -s platform_live_server -p 'test_*.py' -v`：21/21 通过。
- `node --check platform_live_server/douyin_sign.js`：通过。
- 抖音签名助手实际调用：成功返回 `a_bogus`。
- 联网服务端集成检查：五个平台分类树、房间分页均成功；斗鱼、虎牙、B 站、抖音各验证到可播放房间并返回最高画质/多线路，快手分类与网页状态解析正常但测试窗口返回“请求过快/未开播”房间。
- Android `emulator-5554`：Debug APK 安装成功；“直播”入口显示斗鱼、虎牙、哔哩哔哩、抖音、快手五个平台卡片，B 站一级分类和二级分类页可正常加载。

### 缺陷记录

- 时间：2026-08-02
- 症状：B 站子分类缺少父分类参数；快手网页状态正则遇到嵌套对象时截断；虎牙 TARS struct 结束标记被误当字段。
- 修复位置：B 站分类 ID、快手/虎牙括号平衡解析、TARS 解码器。
- 临时方案：无，已由单元测试覆盖。

## 2026-08-02 11:35 - 一级分类卡片标签与最高画质优先

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`
  - Reason: 一级分类卡片不需要重复显示固定的“一级分类”说明。
  - Purpose: 让文本卡片按需显示标签，一级分类卡片仅显示分类名称和二级分类数量；平台卡片等其他标签继续保留。

- File path: `platform_live_server/douyu_resolver.py`
  - Reason: 斗鱼返回的多档画质顺序不应作为默认选择依据。
  - Purpose: 按 `rate` 从高到低尝试播放地址，优先最高画质，失败时仍按原有流程切换 CDN/降级画质。

- File path: `platform_live_server/test_douyu_resolver.py`
  - Reason: 需要锁定最高画质优先的取流行为。
  - Purpose: 新增多档 `rate` 场景测试，确认最高档位最先请求。

## Verification

- `python -m unittest discover -s platform_live_server -p 'test_*.py' -v`
  - Result: 8/8 passed.
- `$env:TVBOX_PLATFORM_LIVE_SERVICE_URL='http://192.168.0.5:8868'; .\\gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: BUILD SUCCESSFUL.
- `adb -s emulator-5554 install -r app\\build\\outputs\\apk\\debug\\app-debug.apk`
  - Result: installed successfully.
- `emulator-5554` UI/playback smoke test
  - Result: 一级分类页卡片未显示“一级分类”；进入“网游竞技 → 英雄联盟”后成功进入直播间列表，播放器浮层显示 `原画2K60 / FLV / hw-h5`，确认优先使用最高画质。

## 2026-08-02 11:27 - 斗鱼一级/二级分类正式浏览

## File Changes

- File path: `platform_live_server/douyu_resolver.py`
  - Reason: 斗鱼接口同时提供 `cate1Info` 一级大类和 `cate2Info` 二级分类，需要完整传递两层数据。
  - Purpose: 新增分类树解析，保留父分类与子分类的关联信息。

- File path: `platform_live_server/server.py`
  - Reason: 原测试逻辑只返回二级分类 `183`（原创IP）。
  - Purpose: 移除测试白名单，分类接口返回 `parentCategories` 和完整 `categories`。

- File path: `app/src/main/java/com/tvbox/app/domain/Models.kt`
  - Reason: Android 端需要表达一级大分类。
  - Purpose: 新增 `PlatformLiveParentCategory` 数据模型。

- File path: `app/src/main/java/com/tvbox/app/data/PlatformLiveRepository.kt`
  - Reason: 客户端需要一次读取分类树并兼容旧服务返回。
  - Purpose: 新增 `PlatformLiveCategoryTree`，解析父分类和二级分类；旧响应缺少父分类时从子分类元数据回退生成。

- File path: `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - Reason: 原页面只有平台、二级分类、房间三层状态。
  - Purpose: 增加一级分类状态、选择动作、返回路径和分类树加载逻辑。

- File path: `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`
  - Reason: 用户需要先选择大分类，再选择二级分类。
  - Purpose: 增加一级分类页面，形成“平台 → 一级大类 → 二级分类 → 房间”完整浏览流程。

## Verification

- `python -m unittest discover -s . -p 'test_*.py' -v`
  - Result: 7/7 passed.
- `./gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `emulator-5554` UI smoke test
  - Result: 已验证斗鱼一级分类显示“网游竞技、单机热游、手游休闲、娱乐天地”等；进入“网游竞技”后显示二级分类“英雄联盟、热门游戏、三角洲行动”等；进入“英雄联盟”后成功显示直播间列表。
- Back navigation
  - Result: 房间 → 二级分类 → 一级大类返回路径通过。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/platform-live.md`

## 目标

## 2026-08-02 09:25 - 平台直播页标题同步

## File Changes

- File path: `app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`
  - Reason: 首页入口已改名为“直播”，平台直播页顶部仍显示旧名称。
  - Purpose: 将平台直播浏览页顶部标题统一为“直播”。

## Verification

- `./gradlew.bat testDebugUnitTest assembleDebug --console=plain`
  - Result: passed.
- `adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk`
  - Result: installed successfully.
- Source/UI review
  - Result: 平台直播浏览页标题已改为“直播”；当前 APK 未配置独立服务地址，因此平台页会先显示配置提示，标题需在服务可用时显示。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/platform-live.md`

为首页“平台直播”测试入口接入斗鱼服务，并解决单条临时 FLV 地址在短时间内中断后无法持续播放的问题。

本次以本地 `reference/dart_simple_live-feat-ohos-1.12.7` 为直接实现参考：保留同清晰度的全部 CDN，当前线路失败时先重取地址，再切换下一线路。

## 实现记录

### 服务端：候选 CDN 地址集

- 文件：`platform_live_server/douyu_resolver.py`
  - 房间签名只计算一次，复用于后续全部 `getH5Play` 请求。
  - 先获取清晰度和 CDN 清单，再按清晰度逐个取得 CDN 真实地址。
  - 去重候选 URL，并将 `scdn*` CDN 排到最后。
  - 移除将 `.flv` 字符串替换为 `.m3u8` 的推断，以及会额外打开直播连接的预读验证。
- 文件：`platform_live_server/server.py`
  - 解析接口新增 `streams` 数组，每项包含 CDN、协议和地址；保留首选 `url` 字段以兼容旧调用方。
- 文件：`platform_live_server/test_douyu_resolver.py`
  - 覆盖 CDN 排序/去重和单次签名复用到所有候选线路。

### Android：恢复与切线

- 文件：`app/src/main/java/com/tvbox/app/domain/Models.kt`
  - 新增 `PlatformLiveStreamCandidate`，解析结果持有候选集和当前候选索引。
- 文件：`app/src/main/java/com/tvbox/app/data/PlatformLiveRepository.kt`
  - 读取服务端 `streams`；旧服务仅返回 `url` 时回退为单候选，保证兼容。
- 文件：`app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`
  - 对同一候选线路最多重取地址两次，第二次前延迟 1 秒。
  - 两次仍失败才切换下一 CDN；所有候选耗尽后提示失败。
- 文件：`app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`
  - 将播放器错误、播放结束、持续缓冲统一交由恢复流程处理。
  - 浮层显示清晰度、协议、当前 CDN 和候选线路序号。

### 平台与分类浏览测试体验

- 文件：`platform_live_server/server.py`
  - 测试阶段只向 TVBox 返回斗鱼“原创IP”分类（斗鱼二级分类 ID 为 `183`），避免在测试入口中暴露未逐项验证的完整分类列表。
- 文件：`app/src/main/java/com/tvbox/app/ui/PlatformLiveScreen.kt`
  - 平台、分类和房间列表统一固定为每行 5 张卡片；图片卡片根据网格宽度保持 `16:9` 封面比例。
  - 房间网格一旦离开顶部，就自动隐藏当前分类标题与副标题；回到顶部后恢复，扩大可浏览区域。

### 斗鱼弹幕验证（已撤销）

- 参考文件：`reference/dart_simple_live-feat-ohos-1.12.7/simple_live_core/lib/src/danmaku/douyu_danmaku.dart`。
  - 验证过参考实现使用的 `wss://danmuproxy.douyu.com:8506`、`loginreq`、`joingroup`、`gid=-9999` 和 45 秒 `mrkl` 心跳流程。
  - Android 真机已成功登录房间、持续接收斗鱼二进制事件帧，且移除通用 WebSocket Ping 后不再发生 30 秒断开。
- 结果：测试窗口内仅收到 `loginres`、`uenter` 等非聊天事件，没有获得可在屏幕上验证的 `chatmsg`。
- 处理：按用户要求删除弹幕客户端、消息模型、播放器叠加层、开关入口、单元测试和临时日志；当前 APK 不包含弹幕功能，也没有新增电脑服务端口或依赖。

## 缺陷记录

- 时间：2026-08-01 19:17
- 症状：斗鱼首选 FLV 线路在约十余秒后可能关闭；原实现只返回一条地址，客户端无法切换同一清晰度的其他 CDN。
- 修复位置：`platform_live_server/douyu_resolver.py`、`TvBoxViewModel.kt`、`PlatformLiveScreen.kt`。
- 处理方案：服务端返回多 CDN 候选，Android 按“重取两次 → 下一 CDN”的顺序恢复。

## 验证结果

- Python：`python -m unittest -v test_douyu_resolver.py`，7/7 通过。
- Android：`testDebugUnitTest assembleDebug --rerun-tasks` 构建成功，44 个任务完成。
- ADB：设备 `192.168.0.7:5555` 覆盖安装 Debug APK 成功。
- 真实解析：斗鱼房间 `36252` 返回两条 CDN 候选，首选为 `hw-h5`，`scdnculinsy` 位于其后。
- 实机：进入首页“平台直播”后，已跨越此前十余秒的故障窗口并持续正常播放；用户已确认当前实机无问题。
- 分类浏览：斗鱼分类页仅显示“原创IP”一张自动聚焦的卡片；原创IP 房间页首行实测为 5 张卡片。
- 滚动行为：遥控器下移到第二行后，界面标题“原创IP / 娱乐天地”已自动隐藏，网格扩展显示空间。
- 播放回归：从原创IP房间进入播放器后，斗鱼视频正常渲染播放。
- 弹幕撤销回归：Android 单元测试与 Debug APK 构建通过；ADB 覆盖安装后可再次进入原创IP播放页，弹幕入口已不存在。

本次实机观察期间首选 CDN 没有再次主动中断，因此没有在该次会话中触发实际的第二线路切换；恢复状态机已通过源码、单元测试和 Android 构建验证，后续会在 CDN 再次波动时按事件日志继续确认。

## 运行说明

- 测试服务仍在本机 `http://192.168.0.5:8866` 运行，以保持当前设备播放不中断。
- 服务只解析和返回临时地址，视频流仍由 Android 设备直接请求斗鱼 CDN。
- 斗鱼动态签名运行依赖本机 Node.js；当前测试环境已具备该条件。

## Navigation

- Master doc: `devLog/README.md`
- Branch doc: `devLog/platform-live.md`

