# 平台直播（斗鱼）- 2026-08-01

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

