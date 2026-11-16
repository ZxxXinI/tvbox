# 播放体验、返回定位与继续观看

## 2026-11-16 20:30 - 倍速按钮直接循环与切集标注（未发布）

### 文件、原因与用途

- `ui/PlaybackSpeed.kt`：提取菜单键原有循环算法并统一文字格式；正常档位按 1、1.25、1.5、2、0.75 回到 1，2 倍速显示 `2.0x`，近似值识别与原未知值回退保持。
- `ui/PlayerScreen.kt`：底栏按钮和菜单松开事件调用同一个循环动作，使用即时 ViewModel 状态，避免快速操作读取尚未重组的旧值；只调整速度、状态和原有短提示，不换媒体、不自动续播。
- `ui/PlayerControls.kt`：倍速按钮改为直接执行循环，显示统一格式并保持按钮焦点；删除速度面板分支及重复列表，切集按钮显示“上一集(1)”和“下一集(3)”。
- `ui/PlayerNavigation.kt`、`app/src/test/java/com/tvbox/app/ui/PlayerNavigationTest.kt`：移除临时 Speeds 面板枚举和旧返回分支，验证它不再存在；非持久化状态无需迁移。
- `app/src/test/java/com/tvbox/app/ui/PlaybackSpeedTest.kt`：新增完整循环、显示文本、近似／异常值和连续多次循环 4 项测试。
- `README.md`、`CHANGELOG.md`、主日志及验收记录：更新无弹窗倍速操作与按键标注，不回改此前历史条目。

### 缺陷与边界

- 时间：2026-11-16 20:30；反馈：点击底部倍速会打开不需要的弹窗，切集按钮没有直观标注数字快捷键。
- 修复：按钮／菜单共用即时循环行为，速度标签顺序为 `1x → 1.25x → 1.5x → 2.0x → 0.75x → 1x`，无倍速面板入口或旧标题。临时方案：无。
- 既有频道式播放设置、返回层级、1/3 切集、10 秒连续快进及手机扫码保留；本轮不进行设备或 GUI 测试，验证见 `docs/validation/2026-11-16-speed-cycle.md`。
- 最终 59 项相关测试、Release 构建与 v2 签名、zipalign 校验通过；APK 中静态确认新切集标注，旧速度弹窗源码已移除。版本不变，未安装设备、提交或发布。

## 2026-11-16 20:12 - 参照频道列表重绘播放设置（未发布）

### 文件、原因与用途

- 参考 `ui/LiveScreen.kt` 的电视频道面板及频道行：电视侧栏固定 260dp，背景 `0xEF101514`，分隔线白色 8%，列表间隔 6dp，行圆角 10dp、最小高度 48dp、内边距 12×10dp，绿色焦点 `0xFF7AE2BA`；仅作为参考，未修改该文件。
- `ui/PlaybackSettingsRow.kt`：新增同风格的扁平可点击列表行，名称及当前值分两层左对齐，当前项轻绿色底、焦点绿色 1dp 描边、长内容省略，并显示进入子项的提示箭头；不使用易混淆为数字快捷键的频道编号。
- `ui/PlayerControls.kt`：播放设置采用同款底色、标题／副标题、分隔线及列表间距，列表区域单独滚动，保留原设置项焦点、返回层级及顶右贴边的全高布局，不增加关闭按钮。
- `ui/PlayerNavigation.kt`、`app/src/test/java/com/tvbox/app/ui/PlayerNavigationTest.kt`：宽度统一为 260dp（窗口更窄时不超出可用宽度），更新宽度测试，保留四项功能和返回映射测试。
- `README.md`、`CHANGELOG.md`、主日志和验收记录：注明本次样式参照、测试限制及本地产物，不覆盖前一轮历史记录。

### 缺陷及边界记录

- 时间：2026-11-16 20:12；用户反馈上一轮设置仍不好看，要求参考电视(4)频道样式与宽度。
- 修复：不再将多个操作栏按钮堆在侧栏中，而是使用频道风格的扁平列表；标题与当前值层级分明。临时方案：无。
- 不修改电视(4)的左侧频道面板或播放能力；片头的输入框、继承按钮仍保持移除，已有默认与本片设置不清空。
- 本轮不进行设备／界面测试，不把静态颜色、宽度及测试通过描述为实际画面验收；最终包及手工清单见 `docs/validation/2026-11-16-player-channel-style.md`。
- 最终 55 项相关测试、Release 构建及 v2 签名、zipalign 校验通过；本地包 `TVBox-v1.3.13-player-settings-style-test.apk`，未安装设备、提交、推送或发布。

## 2026-11-16 20:00 - 片头操作与侧栏布局简化（未发布）

### 文件、原因与用途

- `ui/IntroSkipDialog.kt`：按用户要求删除自定义秒数输入框和“跟随全局”按钮，改用只读当前值、原预设及 ±5 秒调整；不再唤起输入法，保存与取消仍保留。
- `ui/PlayerControls.kt`：播放设置取消外边距与关闭按钮，改为右上贴边、全高的方角侧栏，内容保留安全区和内边距；片头摘要仅显示实际秒数，不再显示继承文字，长标签省略显示。
- `ui/PlayerNavigation.kt`、`app/src/test/java/com/tvbox/app/ui/PlayerNavigationTest.kt`：侧栏宽度按屏宽调整至 220–280dp，小屏不超过可用宽度；新增电视、小屏及极窄边界测试，原返回层级不变。
- `README.md`、`CHANGELOG.md`、主日志和验收记录：同步现有操作入口，保留之前版本与实现记录。

### 缺陷、设置与验证边界

- 时间：2026-11-16 20:00。反馈：片头数字输入和继承按钮冗余；播放设置过宽、与右侧和顶部有空隙，并有多余关闭按钮。
- 修复：删除上述 UI，保留既有 0–600 秒范围；影片原先没有覆盖时，未调整直接保存仍保留默认继承，实际调整才写入本片覆盖，不清空已有数据。
- 首项方向上改为边界取消，不再指向已删除的关闭按钮；返回键仍逐级退出，小屏和长列表仍可滚动。临时方案：无。
- 单元测试和签名包信息见 `docs/validation/2026-11-16-player-ui.md`；未执行 ADB、手机／电视 UI 测试，不把布局参数检查描述为实际画面验收。
- 最终 55 项相关测试、Release 构建、v2 签名和 zipalign 校验通过，本地包 `TVBox-v1.3.13-player-ui-test.apk`，版本仍为 1.3.13；未提交、推送或发布。

## 2026-11-15 19:35 - 播放设置面板收纳（未发布）

### 文件、原因与用途

- `ui/PlayerNavigation.kt`：集中定义播放面板及父级关系，画面、片头、选集与线路返回设置，倍速保持底栏独立入口；没有删除原有播放能力。
- `ui/PlayerControls.kt`：底栏从多项操作精简为播放／暂停、上／下一集、倍速和设置；新增右侧简洁面板与当前值提示，子项操作或取消后回到设置列表。
- `ui/PlayerScreen.kt`：遥控器 Back 和系统返回统一使用父级关系，逐级返回；触摸隐藏控制栏时清除隐藏面板，避免下次返回误入残留层级。
- `app/src/test/java/com/tvbox/app/ui/PlayerNavigationTest.kt`：验证四项功能仍存在、父级返回关系及独立倍速入口。
- `README.md`、`CHANGELOG.md`、主日志和验收记录：注明未发布的设置入口、返回行为及校验边界。

### 行为及缺陷记录

- 时间：2026-11-15 19:35。原表现：操作栏按钮较多，用户希望保留功能但减少常驻入口。
- 实现：画面、片头、选集和线路收进当前播放设置，不跳转应用全局设置；下层选中或取消回到父级并恢复原项目焦点，关闭设置回到底栏“设置”，初次打开底栏仍默认播放／暂停。
- 设置列表上下键显式导航，边界不跳到背后的控制栏；小屏／大字号可滚动，面板出现及滚动后按帧等待焦点节点就绪。
- 静态复核同步修复触摸关闭控制栏后仍保留隐藏子面板的旧边界；临时方案：无。
- 数字 1/3、菜单倍速、10 秒连续快进、续播与换线逻辑保持不变。本轮不进行设备或界面测试，焦点实际体验待用户验收。
- 最终测试、构建和签名结果见 `docs/validation/2026-11-15-player-settings-shortcut.md`。
- 2026-11-15 19:45：相关单元测试共 53 项通过（含 3 项播放面板导航用例），Release 构建及 v2 签名、zipalign 校验通过；保持原版本，未执行设备测试或发布。

## 2026-11-15 15:04 - 自动跳过片头（未发布）

### 文件、原因与用途

- `domain/IntroSkip.kt`：集中处理全局／本片覆盖、范围限制及每次开播的一次性跳转规则；时长未知等待，视频太短或不可跳转时忽略。
- `domain/AppSettings.kt`、`data/AppSettingsRepository.kt`：新增全局秒数和按详情来源 ID＋影片 ID 保存的覆盖项，默认关闭；0 为本片显式关闭，移除覆盖项表示继承，不改变历史格式。
- `ui/IntroSkipDialog.kt`、`ui/TvBoxApp.kt`、`ui/PlayerControls.kt`：设置页默认片头入口及播放器本片入口，提供预设、自定义 0–600 秒和 ±5 秒操作，修改只影响下次从头播放／切集。
- `ui/TvBoxViewModel.kt`、`ui/PlayerScreen.kt`：区分从头开播与续播／换线，接入播放器自动连播、数字 1/3 和选集按钮；主动跳转使用原缓冲保护，保存目标进度，后台／中断时不自动跳转或续播。
- `app/src/test/java/com/tvbox/app/domain/IntroSkipTest.kt`：覆盖继承与关闭、序列化、边界、历史零进度也不跳转、一次性处理、手动退回和下一集新设置快照。
- `CHANGELOG.md`、`README.md`：注明未发布改动，不将本地测试包描述为已发布版本。

### 缺陷与边界记录

- 时间：2026-11-15 15:04。
- 原需求：每集开头只能手动快进，缺少统一的片头跳过设置。
- 实现：由一次性策略在当前视频就绪后执行；不只修改 `openPlayer` 初始进度，补齐 Media3 自动切集路径。
- 安全边界：原媒体身份检查先于跳转取消，避免旧线路事件干扰新媒体；历史进度和换线暂停状态保持优先。
- 临时方案：无；本地单元测试结果及签名包信息见 `docs/validation/2026-11-15-intro-phone-search.md`。未执行设备功能测试。
- 2026-11-15 15:13：补充开播请求的一次性消费后，编译发现媒体身份辅助函数原为文件私有；改为模块内可见，沿用原键定义。消费后列表重建不再重新授权同一集跳片头；临时方案：无。
- 2026-11-15 15:16：最终相关单元测试 43 项通过，Release 构建、v2 签名及 zipalign 校验通过；本地测试 APK 为 `TVBox-v1.3.13-intro-phone-search-test.apk`。额外全量 Lint 的既有 OTA 通知权限错误单独记录，不记为通过。

### 导航

- 主日志：`devLog/README.md`；扫码模块：`devLog/phone-search.md`。

## 2026-10-08 12:01 - 设备模式正式发布 v1.3.12

- 用户确认没有问题，授权发布 `10312 / 1.3.12`，设备模式、短剧横屏与现有 OTA 修复同时纳入。
- 默认电视模式，本机保存；手机用户通过设置切换手机模式，系统识别不覆盖用户选择。
- 本轮不重复功能测试，发布构建、签名和 OTA 校验信息见 `devLog/release.md`；原测试包记录保留。

## 2026-10-08 11:38 - 设备模式默认电视与短剧播放方向

### 缺陷与修复

- 现象：电视盒子看竖屏短剧时，整个播放页面也进入竖屏。
- 原因：点播仅依赖系统 UI_MODE_TYPE_TELEVISION，普通直播还判断 Leanback，两套自动检测不统一；盒子报告普通设备类型时会进入手机转屏逻辑。
- 用户选择的方案：设置提供电视模式/手机模式，默认电视模式并在本机保存，手动选择优先。
- 修复：电视模式进入点播或电视(4)明确请求横屏；手机模式保留点播随影片比例转屏与普通直播手机布局。点播退出回到应用横屏，普通直播退出恢复进入前方向。
- 自动识别保留为设置页参考信息，统一读取 UI 类型、Leanback 和旧电视特征；不根据参考结果修改用户选择。
- 临时方案：无。

### 文件与目的

- `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`：增加 DeviceMode，缺少或无效存储值默认 Television。
- `app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`：读写 device_mode 本机设置，不迁移或删除用户其他数据。
- `app/src/main/java/com/tvbox/app/ui/DeviceModeDetection.kt`：合并自动识别条件，仅向设置页提供参考。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`：设置页顶部增加设备模式选择、行为说明和系统识别参考，支持遥控器与触摸。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：立即更新选择，串行保存设置，避免快速切换后的写入顺序覆盖最后选择。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`：视频尺寸回调只记录比例，方向由设备模式和当前视频共同决定，避免回调捕获旧判断；保留进度、缩放、数字键、菜单键及连续快进。
- `app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`：使用同一设备模式判断布局与方向，电视锁横屏，手机采用原有自适应布局和操作栏；触摸处理及控制栏/频道列表隐藏计时随模式重新应用。
- `devLog/README.md`、本日志：记录缺陷、实现和测试包交付；此前 OTA 改动保持在当前工作区，不修改独立兼容工程。

### 验证与交付

- 用户自行进行功能验收；本轮仅编译构建、校验签名及包内版本，不操作设备，不运行单元测试。
- 临时构建版本为 `10310 / 1.3.10`，用于继续测试线上 1.3.11 的 OTA；产物生成后恢复源码版本为 1.3.11。
- 画面缩放仍沿用用户选择，自适应模式下竖屏影片完整显示；不强制裁剪或拉伸。
- 不在 MainActivity 新增全局转屏处理，不修改平台直播或独立 Android 4.4 / Android 6 工程，不更新正式 Release 或线上清单，不提交推送。
- `:app:assembleRelease` 构建通过；APK `app/build/outputs/apk/release/TVBox-v1.3.10-device-mode-test.apk`，`5034437` 字节；SHA-256 `dcde59d8ddbd3a1cf308ddc6269dfd68a4727897c7a0d8de1c61c519b2f38502`。
- 包内版本 `10310 / 1.3.10`，最低 API 28；v2 签名校验通过，沿用正式签名证书。产物生成后源码版本已恢复 `10311 / 1.3.11`。
- 实际短剧方向、手机模式和设置重启保留由用户验收；没有安装到设备或执行功能测试。中文源码/日志 UTF-8 BOM 读回验证通过；主日志：`devLog/README.md`。

## 2026-10-05 20:07 - 用户验收后发布 v1.3.11

- 用户已测试并反馈没有明显问题，授权发布新版本并回复 Issue #3；本轮不重复执行测试或操作 ADB。
- 两项修复纳入 `10311 / 1.3.11`，构建签名正式包并同步 Release 与 OTA 清单；原本地测试记录保留，不与正式版本混淆。
- 构建、签名、大小与 SHA-256 详见 `devLog/release.md`；发布后回复 Issue #3，不自动关闭 Issue。

## 2026-10-05 19:13 - 论坛反馈：点播全屏窗口与画面比例选择

### 文件修改、原因与目的

- `app/src/main/java/com/tvbox/app/domain/AppSettings.kt`、`app/src/main/java/com/tvbox/app/data/AppSettingsRepository.kt`：新增本机持久化的自适应、裁剪铺满和拉伸铺满三种画面模式，旧数据缺少该项时默认自适应，不迁移或覆盖其他设置。
- `app/src/main/java/com/tvbox/app/ui/PlayerViewport.kt`：仅点播页进入沉浸式播放窗口，隐藏状态栏和导航栏，回到前台重新应用，退出时恢复进入前的可见性与行为；将画面模式映射为 Media3 FIT / ZOOM / FILL。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`：明确播放器窗口匹配可用区域；在 AndroidView 更新阶段实时应用缩放模式，不重建播放器，不影响进度、暂停状态、倍速和播放管家逻辑。
- `app/src/main/java/com/tvbox/app/ui/PlayerControls.kt`：新增“画面”选择面板，显示当前模式、选中标记及裁剪/变形提示；窄窗口操作栏可横向滚动，保留遥控器及触摸操作。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：即时更新画面选择并通过现有设置仓库保存。
- `devLog/README.md`、本日志：关联论坛反馈、实现范围和交付边界。

### 缺陷记录与边界

- 时间：2026-10-05 19:13；现象：论坛用户反馈同盒子播放电影时画面不满屏、四周黑框。
- 已定位的限制：点播仅固定 FIT 缩放策略，缺少铺满选项；没有发现硬编码视频分辨率或固定尺寸播放窗口。四周黑框的具体设备/片源原因尚无截图或日志，不能声称已复现。
- 实现：补齐仅播放页的沉浸式窗口和可记忆的画面模式；自适应完整保留比例，裁剪铺满可能丢失边缘，拉伸铺满可能变形，不强制改变所有影片比例。
- 片源自带黑边不等同于播放器留边，不能保证用这三种模式自动消除；同片源设备实际效果由用户验收。
- 临时方案：无；参考 [Media3 缩放模式](https://developer.android.com/reference/androidx/media3/ui/AspectRatioFrameLayout) 和 [Android 沉浸式播放窗口](https://developer.android.com/develop/ui/views/layout/immersive)。

### 构建交付

- 用户在 Issue #3 修复后追加论坛反馈修复，并要求自行完成全部功能测试；之后仅构建签名 Release 本地包，不再运行单元或设备测试，不连接或操作 ADB。
- 保持版本 `10310 / 1.3.10`；不更新正式 Release 附件、OTA 清单，不提交或推送，不修改普通直播、平台直播及独立兼容工程。
- `:app:assembleRelease` 构建通过；包内版本 `10310 / 1.3.10`，最低 API 28，v2 签名校验通过，使用现有 Release 证书，可覆盖安装保留数据。
- APK：`app/build/outputs/apk/release/TVBox-v1.3.10-issue3-picture-test.apk`；大小 `5001469` 字节，SHA-256 `c3a15736624dd0067e9b8933320ca8ce3fda63d3c7cf79801fd2eb7fb6b904ab`。
- APK 同时包含 Issue #3 与画面模式改动；后续未运行功能或单元测试，未安装设备，论坛设备黑框的实际消除效果仍待用户验收。
- 编码：中文源码/日志使用 UTF-8 BOM 并读回验证；主日志 `devLog/README.md`。

## 2026-10-05 19:07 - Issue #3 自动换线保留进度与缓冲提示恢复

### 文件修改、原因与目的

- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：自动换线必须接收当前播放位置和播放意图，并复用手动换线入口，不再把起播位置置零。
- `app/src/main/java/com/tvbox/app/domain/PlaybackRecovery.kt`：统一换线目标解析、对应集匹配与空地址校验；使用进度锚点避免未就绪备用源的零进度覆盖原续播位置；区分缓冲提示和真正播放错误。
- `app/src/main/java/com/tvbox/app/domain/PlaybackAgent.kt`：自动候选按集名匹配对应集，健康评分使用对应集下标，排除缺集及空地址，避免顺序不同的线路切错集。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`：换线前保存实际进度；播放恢复清除缓冲提示和换线通知；媒体身份与当前状态校验阻止旧线路事件再次触发换线，连续失败时保留尝试过的线路集合。
- `app/src/main/java/com/tvbox/app/domain/PlaybackBufferMonitor.kt`：就绪回调不再触发换线；频繁及累计卡顿在下一次实际缓冲时判断，过期记录清理，保留暂停和拖动保护。
- `app/src/test/java/com/tvbox/app/domain/PlaybackRecoveryTest.kt`、`PlaybackBufferMonitorTest.kt`：覆盖自动换线进度、顺序不同的集数、缺集/空地址、暂停意图、连续换线零进度、快退/归零以及恢复时不换线和提示清理。
- `devLog/README.md`、本日志：记录缺陷、修复范围与验证边界。

### 缺陷记录

- 时间：2026-10-05 19:07；反馈：[GitHub Issue #3](https://github.com/ZxxXinI/tvbox/issues/3)。
- 现象：播放管家自动换线从头播放，已经恢复播放时仍显示缓冲提示。
- 原因：自动换线起播位置硬编码为零；缓冲失败提示被写入持久错误但就绪时不清理；就绪回调也可能根据刚结束的卡顿触发换线。
- 修复：统一换线、进度锚点、按集名匹配、缓冲提示类型区分及当前媒体事件校验。
- 临时方案：无。

### 验证与范围

- 在用户追加“不需要测试”要求前启动的检查已完成：83 项单元测试通过，Debug 构建通过，Android Lint 为 0 error / 54 warning / 3 hint。该结果仅覆盖当时 Issue #3 修复代码，不代表后续论坛画面改动通过测试。
- 本轮仅处理 Issue #3，不修改画面缩放、版本号、OTA 清单或 Release 附件；不安装、不执行设备功能测试，不关闭或评论 Issue。
- Android 4.4 / Android 6 独立工程及主日志中已有无关记录保持不变，未提交或推送。
- 主日志：`devLog/README.md`；模块日志：`devLog/playback-experience.md`。

## 2026-10-04 09:44 - 继续观看向上返回导航

- 文件：`app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`、`app/src/main/java/com/tvbox/app/ui/components/Common.kt`。
- 原因：继续观看卡片获得焦点后，网格自动滚动可能隐藏顶部区域并将导航移出焦点树；横向卡片行未设置明确的向上出口。
- 修复：继续观看焦点组接管上键，先展开顶部导航、滚回网格顶部，再等待导航重新进入布局并请求首个导航按钮焦点。默认主题定位历史入口，影院主题定位搜索入口；按住上键的重复事件及释放事件被正确消费。内容卡片再次获得焦点后恢复原自动隐藏行为。
- 保留左右切换卡片、确认续播、数字快捷键及已有页面返回定位。
- 验证：签名 Release 构建与签名校验通过；2026-10-04 09:46 已覆盖安装到 `192.168.0.8:5555`，返回 Success，保留数据。遵循用户先前要求，未执行设备功能测试，由用户自行验收。
- 安装包：`app/build/outputs/apk/release/TVBox-v1.3.9-home-focus-fix.apk`；版本 `10309 / 1.3.9`；SHA-256 `1b561a3e4932873949322554966534be29fd3625984d4cd1716b517c3ebcca4a`。
- 时间：2026-10-04 09:44；缺陷：从继续观看无法向上离开卡片行；临时方案：无。

## 2026-10-03 21:44 - 电视(4)轻量界面统一

- 修改文件：`app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`。
- 原因：普通电视直播仍使用大面积绿色按钮、实心选中行，与新的点播控制栏风格不一致。
- 实现：手机底部改为渐变遮罩和紧凑操作按钮，频道入口使用白色胶囊；显示当前频道与线路信息。频道面板增加标题和数量，使用半透明深色背景、淡绿色选中底色和清晰的焦点描边，手机提供关闭入口。电视侧栏适当加宽，频道提示与操作提示改为轻量样式。
- 仅调整电视(4)页面及本日志；保持当前工作区中已有播放逻辑与续播修复。
- 验证：签名 Release 构建和签名校验通过；2026-10-03 21:52 已覆盖安装到 `192.168.0.8:5555`，返回 Success，保留数据。按用户要求未执行设备功能测试。
- 安装包：`app/build/outputs/apk/release/TVBox-v1.3.9-tv-ui-test.apk`；版本 `10309 / 1.3.9`；SHA-256 `7b469b6d7463e6611514bfa8d3e97120b3e6bba2ca773f55e317f5fc631a3224`。
- 临时方案：无。

## 2026-10-03 21:35 - 控制栏重绘与续播超时修复

- `app/src/main/java/com/tvbox/app/ui/PlayerControls.kt`：将满宽绿色按钮和粗滑块改为渐变遮罩、细进度条、小圆点、紧凑文字按钮及白色主播放按钮；焦点保留浅绿色描边。
- `app/src/main/java/com/tvbox/app/data/MovieRepository.kt`：新增仅请求原来源的 `getResumeDetail`；备用来源自身超时使用 `withTimeoutOrNull` 隔离，不再取消整组详情/搜索请求；调用方取消仍正常传播。
- `app/src/main/java/com/tvbox/app/ui/TvBoxViewModel.kt`：主来源成功后立即续播，后台追加备用来源且保留当前播放列表；离开加载页时取消续播任务，防止迟到结果跳转。
- `app/src/test/java/com/tvbox/app/data/MovieRepositoryTest.kt`：新增三项回归，覆盖慢备用来源不阻塞续播、备用来源超时不丢失主来源和真实取消继续生效。
- `tools/playback_acceptance_fixture.py`、`tools/resume_timeout_acceptance.py`：补充 6 秒慢备用源与冷启动续播复现脚本；更新既有脚本的倍速文字兼容。

### 缺陷与验证

- 时间：2026-10-03 21:35；现象：继续观看报 `Timed out waiting for 4000ms`；原因：原续播等待 `getDetailProgressively().lastOrNull()`，备用源的 TimeoutCancellationException 又被作为整组取消重新抛出；修复：原来源优先返回、后台补线和局部超时隔离；临时方案：无。
- 69 项单元测试通过，Lint 与 Release 构建通过。设备续播复测在准备阶段未完成，随后用户明确要求停止测试并自行验收；不将此次设备复测计为通过。
- 已生成并校验 `app/build/outputs/apk/release/TVBox-v1.3.9-ui-resume-fix.apk`，SHA-256：`bcf162ebcedbd89f09bba8186de3554b77be706eafe2da53dcbd262a1574ef08`。
- 已覆盖安装至 `192.168.0.8:5555`，安装返回 Success，保留数据，版本仍为 `10309 / 1.3.9`；未在该设备启动测试。

## 2026-10-03 - 本轮实现

### 修改文件与目的

- `app/src/main/java/com/tvbox/app/domain/PlaybackInterruptionPolicy.kt`：统一后台、音频焦点中断及用户手动恢复规则。
- `app/src/main/java/com/tvbox/app/ui/PlaybackSession.kt`：将规则接入 Android 生命周期和 Media3 音频焦点事件；媒体替换继续尊重暂停状态。
- `app/src/main/java/com/tvbox/app/ui/PlayerScreen.kt`、`PlayerControls.kt`：加入 TVBox 自有操作栏、选集/线路/倍速面板和进度条，保留数字键、菜单键、连续快进与手机手势。
- `app/src/main/java/com/tvbox/app/ui/LiveScreen.kt`、`PlatformLiveScreen.kt`：接入共享暂停状态及继续播放入口，后台暂停时不触发自动换线/重连。
- `app/src/main/java/com/tvbox/app/ui/KeepScreenOnWhileVisible.kt`：播放页在前台保持亮屏，离开或进入后台后恢复原状态。
- `app/src/main/java/com/tvbox/app/ui/TvBoxApp.kt`、`SearchScreen.kt`、`DetailScreen.kt`、`ReturnFocus.kt`：保存页面滚动位置和内容焦点，按稳定标识恢复；来源/分类/搜索词主动切换时重置。
- `app/src/main/java/com/tvbox/app/domain/WatchHistory.kt`、`data/HistoryRepository.kt`、`ui/TvBoxViewModel.kt`、`ui/components/Common.kt`：新增最近六部未看完的继续观看，绑定播放快照保存历史，串行写入并保留现有历史格式。
- `app/src/test/java/com/tvbox/app/domain/PlaybackExperienceTest.kt`：新增中断、继续观看过滤和跨线路选集规则测试。
- `app/src/debug/AndroidManifest.xml`、`app/src/debug/java/com/tvbox/app/AcceptanceActivity.kt`：仅 Debug 打包的可控验收入口，使用现有业务页面和仓库。
- `app/build.gradle.kts`：提供 Debug 可选使用现有 Release 证书的构建属性，便于无损覆盖安装；正常构建及版本号不变。
- `tools/playback_acceptance_fixture.py`、`tools/playback_adb_acceptance.py`：本地视频/接口及可重复 ADB 验收工具。

### 缺陷记录

- 时间：2026-10-03；现象：从搜索结果进入详情后，返回直接跳到首页；修复：按入口保留 `detailReturnScreen`；临时方案：无。
- 时间：2026-10-03；现象：原长按临时 2 倍速会通过播放器监听写回全局倍速，松手可能仍为 2 倍速；修复：手势单独保存原倍速，松手按原值恢复；临时方案：无。
- 时间：2026-10-03；现象：设备成功停止测试服务但 `am stopservice` 返回非零退出码，测试产生误报；修复：仅对明确包含 `Service stopped` 的停止服务结果接受该退出码，其他命令仍严格校验。
- 时间：2026-10-03；现象：触摸模式下 Accessibility XML 的 `focused` 与 Compose 实际焦点不同；处理：通过返回后按确认键打开原影片验证实际焦点，而不单独依赖 XML 标志。
- 时间：2026-10-03；现象：操作栏聚焦后返回键可能被控件消费，后续左右键仍用于导航；修复：点播根节点接管面板/操作栏的返回键，关闭后在下一帧恢复画面焦点。
- 时间：2026-10-03；现象：快速切到后台再返回时可能尚未触发 ON_STOP；修复：在 ON_PAUSE 即暂停并保存进度，ON_RESUME 仅恢复前台状态，不自动播放。

### 验证与产物

- 最终结果见 `docs/validation/2026-10-03-playback-experience.md`。
- 66 项单元测试通过、Lint 0 errors、签名 Release 构建通过；ADB 13 项通过、1 项部分验证。
- 2026-10-03 21:08：按用户要求覆盖安装到 `192.168.0.8:5555`，保留数据；停止后续设备操作，由用户自行验收。
- 未升级版本号、未提交/推送或发布；保留原工作区中的无关文件。

### 导航

- 主日志：`devLog/README.md`
- 验收报告：`docs/validation/2026-10-03-playback-experience.md`
