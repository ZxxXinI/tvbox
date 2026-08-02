# TVBox 多平台直播解析服务

本服务运行在局域网电脑上，统一为 TVBox 提供斗鱼、虎牙、哔哩哔哩、抖音和快手的分类浏览、房间列表、签名解析与短时播放地址缓存。TVBox 仍直接请求平台 CDN 视频流，电脑服务不转发视频。

完整服务器部署说明见 [DEPLOYMENT.md](DEPLOYMENT.md)。如果服务器主机没有 Python，使用文档中的 Docker 方案；当前版本不能直接执行 `node server.py`，因为 Node.js 只负责抖音 `a_bogus` 签名，HTTP 服务和五个平台适配器仍由 Python 运行。

平台直播浏览流程统一为“平台 → 一级分类 → 二级分类 → 直播间 → 播放器”。不同平台的签名、Cookie、反盗链和画质选择均封装在服务端适配器中，Android 不需要增加平台专用 UI。

## 运行要求

- Python 3.11 或更高版本
- Node.js LTS（斗鱼动态签名与抖音 `a_bogus` 签名由 Node 执行）
- 已安装 `requirements.txt` 中的 Python 依赖（当前仅 `requests`）

主机没有 Python 时不需要在主机安装 Python，可使用 Node.js LTS 基础镜像，在 Docker 容器内部运行 Python 虚拟环境。服务器主机只需安装 Docker。

## 启动

```powershell
cd platform_live_server
python -m pip install -r requirements.txt
python server.py --host 0.0.0.0 --port 8868
```

Docker 运行方式和 systemd 常驻配置请参阅 [DEPLOYMENT.md](DEPLOYMENT.md)。

TVBox 和服务端必须网络可达。构建或设置服务地址时使用服务端局域网、VPN 或 HTTPS 地址，例如：

```text
TVBOX_PLATFORM_LIVE_SERVICE_URL=http://20.205.10.127:8868
```

不要填写 `localhost` 或 `127.0.0.1`。Windows 防火墙如提示是否允许 Python 访问网络，请仅勾选“专用网络”。

## 可选平台配置

默认不要求 Cookie。只有遇到平台风控、登录限制或播放接口拒绝时，才在启动服务的电脑上配置：

```text
TVBOX_PLATFORM_LIVE_BILIBILI_COOKIE
TVBOX_PLATFORM_LIVE_DOUYIN_COOKIE
TVBOX_PLATFORM_LIVE_KUAISHOU_COOKIE
TVBOX_PLATFORM_LIVE_KUAISHOU_KWW
TVBOX_PLATFORM_LIVE_HUYA_COOKIE
```

Cookie 只保存在电脑服务端进程内，不返回到 API 响应、日志或 TVBox 设置。快手未显式配置 `KWW` 时，会从 Cookie 中的 `kwfv1` 自动生成 `Kww` 请求头；抖音未配置 Cookie 时使用参考项目的 `ttwid` 兜底值。

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /health` | 服务状态与版本 |
| `GET /v1/live/catalog` | 兼容旧版的斗鱼测试频道目录 |
| `GET /v1/live/sites` | 五个平台列表 |
| `GET /v1/live/categories?site=douyu` | 指定平台的一级、二级分类树 |
| `GET /v1/live/rooms?site=douyu&categoryId=183&page=1` | 指定二级分类的直播间分页 |
| `GET /v1/live/resolve?site=douyu&roomId=36252` | 返回最高画质、请求头和多条候选线路 |

`resolve` 响应继续包含旧版的 `quality`、`protocol`、`url`、`headers` 字段，同时提供 `streams` 数组。`streams` 按平台优先级排列，Android 可在当前线路失败时继续使用下一条线路。缓存键为 `(site, room_id)`，因此不同平台的相同房间号不会互相覆盖。

## 测试

```powershell
python -m unittest discover -s platform_live_server -p 'test_*.py' -v
```

服务端只解析和缓存临时地址，视频流由 Android 设备直接请求平台 CDN。请以普通用户运行服务，不要在未加认证与 HTTPS 的情况下开放公网端口。
