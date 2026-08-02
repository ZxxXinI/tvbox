# TVBox 多平台直播解析服务部署说明

本文用于把 `platform_live_server` 部署到真正的 Linux 服务器或 Windows 服务器。部署完成后，TVBox 只通过 HTTP API 获取分类、直播间和临时播放地址；视频流仍由 TVBox 直接连接各平台 CDN，服务器不会转发视频流。

## 重要结论：当前版本不能只用 Node.js 启动

当前服务的 HTTP 主程序和五个平台适配器都是 Python：

- `server.py` 是 Python HTTP 服务。
- `bilibili_resolver.py`、`huya_resolver.py`、`douyin_resolver.py`、`kuaishou_resolver.py` 和 `douyu_resolver.py` 都由 Python 加载。
- Node.js 只负责执行 `douyin_sign.js`，不能替代整个服务端。

因此，下面这些命令是错误的，服务器 AI 不要执行：

```bash
node server.py
node server.js
```

如果服务器主机没有 Python，推荐使用“Node.js 基础镜像 + 容器内 Python”的 Docker 方案。这样主机不需要安装 Python，服务仍按当前已经验证过的 Python 实现运行；只有在明确要求完全 Node.js、禁止容器内 Python 时，才需要另行重写五个平台适配器和全部测试。

## 一、服务组成

运行时必须上传下面这些文件到同一个目录，例如 `/opt/tvbox-platform-live`：

```text
platform_live_server/
├─ server.py
├─ platform_common.py
├─ douyu_resolver.py
├─ huya_resolver.py
├─ bilibili_resolver.py
├─ douyin_resolver.py
├─ douyin_sign.js
├─ kuaishou_resolver.py
├─ catalog.json
├─ requirements.txt
└─ （可选）test_douyu_resolver.py、test_platform_resolvers.py
```

其中：

- `server.py`：统一 HTTP 服务和平台注册表。
- `*_resolver.py`：斗鱼、虎牙、哔哩哔哩、抖音、快手适配器。
- `platform_common.py`：统一分类、房间、画质和播放线路模型。
- `douyin_sign.js`：抖音 `a_bogus` 签名助手。
- `catalog.json`：兼容旧版斗鱼频道目录接口，不能删除。
- `requirements.txt`：Python 依赖。

`reference/` 目录、Android 源码、APK、截图和测试日志都不是服务器运行时依赖，不需要上传。服务器不需要安装 Dart、Flutter、QuickJS 或 Android SDK。

## 二、运行要求

推荐使用 Linux（Ubuntu 22.04/24.04、Debian 12 等）：

- Python 3.11 或更高版本。
- Node.js LTS，抖音签名助手需要 Node.js。
- 能够访问斗鱼、虎牙、哔哩哔哩、抖音、快手的 HTTPS 接口。
- 至少开放一个 TCP 端口，默认是 `8868`。
- 服务器和 TVBox 设备之间必须网络可达。

Python 依赖目前只有 `requests`，具体版本约束在 `requirements.txt` 中。Node.js 只使用标准库，不需要安装 npm 包。

## 三、主机没有 Python 时：Docker 部署（推荐）

Docker 方案使用 Node.js LTS 作为基础镜像，并在镜像内部创建 Python 虚拟环境。服务器主机只需要 Docker，不需要安装 Python 或 npm 依赖。

在 `platform_live_server` 目录创建 `Dockerfile`：

```dockerfile
FROM node:20-bookworm-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt ./
RUN python3 -m venv /opt/venv \
    && /opt/venv/bin/pip install --no-cache-dir --upgrade pip \
    && /opt/venv/bin/pip install --no-cache-dir -r requirements.txt

COPY server.py platform_common.py douyu_resolver.py huya_resolver.py bilibili_resolver.py douyin_resolver.py douyin_sign.js kuaishou_resolver.py catalog.json ./
RUN node --check douyin_sign.js

EXPOSE 8868

CMD ["/opt/venv/bin/python", "server.py", "--host", "0.0.0.0", "--port", "8868", "--cache-seconds", "30", "--timeout", "10"]
```

构建镜像：

```bash
cd /opt/tvbox-platform-live
docker build -t tvbox-platform-live:4 .
```

运行容器：

```bash
docker rm -f tvbox-platform-live 2>/dev/null || true
docker run -d \
  --name tvbox-platform-live \
  --restart unless-stopped \
  --env-file /etc/tvbox-platform-live.env \
  -p 8868:8868 \
  tvbox-platform-live:4
```

检查容器：

```bash
docker ps --filter name=tvbox-platform-live
docker logs --tail 100 tvbox-platform-live
curl http://127.0.0.1:8868/health
```

Cookie 文件仍建议设置为 `root:root` 和 `600`。更新代码后重新执行 `docker build`，然后重新创建容器；不要只重启旧容器，否则旧镜像仍在运行。

## 四、Linux 直接部署

### 1. 安装系统依赖

以 Ubuntu/Debian 为例：

```bash
sudo apt update
sudo apt install -y python3 python3-venv python3-pip nodejs npm curl

python3 --version
node --version
```

Python 应显示 `3.11` 或更高版本，Node.js 建议使用当前 LTS 版本。如果系统自带 Node.js 版本过旧，请先通过发行版官方源或 NodeSource 安装 LTS，不要在项目目录中下载二进制文件。

### 2. 创建专用用户和目录

```bash
sudo useradd --system --home /opt/tvbox-platform-live --shell /usr/sbin/nologin tvbox-live
sudo mkdir -p /opt/tvbox-platform-live
sudo chown -R tvbox-live:tvbox-live /opt/tvbox-platform-live
```

把本说明第一节列出的运行时文件上传到 `/opt/tvbox-platform-live`，然后执行：

```bash
cd /opt/tvbox-platform-live
sudo -u tvbox-live python3 -m venv .venv
sudo -u tvbox-live .venv/bin/python -m pip install --upgrade pip
sudo -u tvbox-live .venv/bin/pip install -r requirements.txt

sudo -u tvbox-live node --check douyin_sign.js
```

如果服务器上保留了测试文件，可以运行：

```bash
sudo -u tvbox-live .venv/bin/python -m unittest discover -s . -p 'test_*.py' -v
```

### 3. 配置可选 Cookie

默认不需要 Cookie。只有平台出现风控、登录限制或播放接口拒绝时，才配置对应变量。创建 `/etc/tvbox-platform-live.env`：

```text
# 可选：完整 Cookie，只保存在服务器，不返回给 TVBox
TVBOX_PLATFORM_LIVE_BILIBILI_COOKIE=
TVBOX_PLATFORM_LIVE_DOUYIN_COOKIE=
TVBOX_PLATFORM_LIVE_KUAISHOU_COOKIE=
TVBOX_PLATFORM_LIVE_KUAISHOU_KWW=
TVBOX_PLATFORM_LIVE_HUYA_COOKIE=

# 可选：Node.js 不在 PATH 时填写绝对路径
# TVBOX_PLATFORM_LIVE_NODE=/usr/bin/node
```

设置文件权限：

```bash
sudo chown root:root /etc/tvbox-platform-live.env
sudo chmod 600 /etc/tvbox-platform-live.env
```

说明：

- B站 Cookie 用于遇到 WBI、429 或登录限制时提高成功率。
- 抖音未配置 Cookie 时，服务会使用内置 `ttwid` 兜底。
- 快手未显式配置 `TVBOX_PLATFORM_LIVE_KUAISHOU_KWW` 时，会从 Cookie 的 `kwfv1` 自动生成 `Kww` 请求头。
- Cookie 不要写进源码、Git、README、systemd 日志或聊天记录。

### 4. 配置 systemd 常驻服务

创建 `/etc/systemd/system/tvbox-platform-live.service`：

```ini
[Unit]
Description=TVBox Multi-Platform Live Resolver
After=network-online.target
Wants=network-online.target

[Service]
Type=simple
User=tvbox-live
Group=tvbox-live
WorkingDirectory=/opt/tvbox-platform-live
EnvironmentFile=-/etc/tvbox-platform-live.env
ExecStart=/opt/tvbox-platform-live/.venv/bin/python /opt/tvbox-platform-live/server.py --host 0.0.0.0 --port 8868 --cache-seconds 30 --timeout 10
Restart=always
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true

[Install]
WantedBy=multi-user.target
```

启用并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now tvbox-platform-live
sudo systemctl status tvbox-platform-live --no-pager
```

查看日志：

```bash
sudo journalctl -u tvbox-platform-live -f
```

服务监听地址默认是 `0.0.0.0:8868`。不要把 Python 进程以 root 用户运行。

### 5. 配置防火墙

如果 TVBox 和服务器在同一个局域网，只允许局域网网段访问。例如局域网是 `192.168.0.0/24`：

```bash
sudo ufw allow from 192.168.0.0/24 to any port 8868 proto tcp
sudo ufw status
```

如果使用云服务器，请在云安全组中只允许 TVBox 所在网段或 VPN 网段访问 `8868/tcp`。当前接口没有身份认证，不要直接把 `8868` 暴露到公网。

跨公网使用时，优先采用 Tailscale、WireGuard、ZeroTier 等组网方式，让 TVBox 通过私有网络访问服务器。若必须使用公网，应在 Nginx/Caddy 后提供 HTTPS，并额外限制来源 IP；当前 Android 客户端没有配置服务端 Token 的功能，不能直接套用需要自定义请求头的认证方案。

## 五、Windows 服务器部署

在 PowerShell 中执行：

```powershell
New-Item -ItemType Directory -Force C:\tvbox-platform-live | Out-Null
Set-Location C:\tvbox-platform-live

py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\pip.exe install -r requirements.txt

node --check douyin_sign.js
.\.venv\Scripts\python.exe server.py --host 0.0.0.0 --port 8868
```

首次启动时，在 Windows 防火墙提示中只允许“专用网络”。如果需要开机自启，可以使用任务计划程序或 NSSM，工作目录必须设置为 `C:\tvbox-platform-live`，启动程序使用：

```text
C:\tvbox-platform-live\.venv\Scripts\python.exe
```

参数：

```text
C:\tvbox-platform-live\server.py --host 0.0.0.0 --port 8868 --cache-seconds 30 --timeout 10
```

Cookie 环境变量应配置在服务进程的环境中，不要直接写到公开批处理文件中。

## 六、部署后检查

在服务器本机执行：

```bash
curl http://127.0.0.1:8868/health
curl http://127.0.0.1:8868/v1/live/sites
```

正常结果应包含：

```json
{"status":"ok","service":"tvbox-platform-live","version":4}
```

`/v1/live/sites` 应返回五个平台：`douyu`、`huya`、`bilibili`、`douyin`、`kuaishou`。

从 TVBox 所在网络再检查服务器地址，例如服务器地址为 `192.168.1.20`：

```bash
curl http://192.168.1.20:8868/health
curl http://192.168.1.20:8868/v1/live/sites
```

然后依次检查接口：

```text
GET /v1/live/categories?site=douyu
GET /v1/live/categories?site=huya
GET /v1/live/categories?site=bilibili
GET /v1/live/categories?site=douyin
GET /v1/live/categories?site=kuaishou
GET /v1/live/rooms?site=douyu&categoryId=<二级分类ID>&page=1
GET /v1/live/resolve?site=douyu&roomId=<当前开播房间号>
```

最后一个接口必须使用当前正在直播的房间。平台房间下播、风控或接口临时拒绝时，服务会返回 400 和中文错误信息，这是平台状态，不代表进程停止。

## 七、TVBox 端配置

`TVBOX_PLATFORM_LIVE_SERVICE_URL` 是 Android 构建时注入的地址，不是服务端环境变量。服务器地址变化后，需要重新构建 APK：

### Windows PowerShell

```powershell
$env:TVBOX_PLATFORM_LIVE_SERVICE_URL = "http://192.168.1.20:8868"
.\gradlew.bat assembleDebug
```

### Linux/macOS

```bash
TVBOX_PLATFORM_LIVE_SERVICE_URL=http://192.168.1.20:8868 ./gradlew assembleDebug
```

不要把 `localhost`、`127.0.0.1` 或服务器内部地址写进 APK；TVBox 设备必须能直接访问该地址。如果使用 HTTPS，地址示例为：

```text
https://live.example.com
```

## 八、接口和运行行为

- `/v1/live/sites`：返回五个平台。
- `/v1/live/categories?site=...`：返回一级分类和二级分类。
- `/v1/live/rooms?...`：返回直播间分页。
- `/v1/live/resolve?...`：返回最高画质、请求头、首选 URL 和备用 `streams`。
- `/v1/live/catalog`：保留给旧版斗鱼频道调用，使用 `catalog.json`。
- 成功解析地址默认缓存 30 秒，可通过 `--cache-seconds` 调整。
- 单个平台接口失败不会修改其他平台的适配器状态。
- 服务只负责解析和短时缓存，视频由 TVBox 直接访问平台 CDN。
- 解析返回的 URL 是临时地址，不能长期保存或写入公开缓存。

## 九、常见问题

### 1. TVBox 显示“测试服务未配置”

构建 APK 时没有设置 `TVBOX_PLATFORM_LIVE_SERVICE_URL`，重新按第六节构建即可。修改服务器环境变量不会自动修改已经安装的 APK。

### 2. `ModuleNotFoundError: requests`

确认使用了项目虚拟环境中的 Python：

```bash
/opt/tvbox-platform-live/.venv/bin/pip install -r requirements.txt
/opt/tvbox-platform-live/.venv/bin/python server.py
```

### 3. 抖音报签名助手启动失败

检查 Node.js：

```bash
node --version
node --check /opt/tvbox-platform-live/douyin_sign.js
```

如果 Node 不在服务的 PATH 中，在 `/etc/tvbox-platform-live.env` 设置：

```text
TVBOX_PLATFORM_LIVE_NODE=/usr/bin/node
```

然后执行：

```bash
sudo systemctl restart tvbox-platform-live
```

### 4. 接口超时或 TVBox 无法连接

依次检查：

1. `systemctl status tvbox-platform-live`。
2. `curl http://127.0.0.1:8868/health`。
3. 服务器防火墙和云安全组的 `8868/tcp`。
4. TVBox 与服务器是否在同一网络或 VPN。
5. TVBox APK 中写入的地址是否为服务器可达地址。

### 5. 某个平台返回“房间未开播”或 400

先确认房间仍在直播，再从 `/v1/live/rooms` 重新获取房间号。平台列表可能包含刚刚下播的房间；遇到风控时再配置对应 Cookie。其他平台仍可独立使用。

### 6. 端口已被占用

```bash
sudo ss -lntp | grep 8868
```

停止占用端口的旧服务，或改用其他端口并同步修改 systemd、TVBox 构建地址和防火墙规则。

## 十、交给服务器 AI 的最小任务描述

可以把下面这段直接交给服务器上的 AI：

```text
请在这台服务器部署 TVBox 多平台直播解析服务。当前版本的 HTTP 服务是 Python，Node.js 只用于抖音签名，不能执行 `node server.py`。如果主机没有 Python，优先使用 Docker，在 Node.js 基础镜像内运行 Python；不要把五个平台适配器改成临时的半成品 Node.js 实现。

1. 将 platform_live_server 目录中的 server.py、platform_common.py、douyu_resolver.py、huya_resolver.py、bilibili_resolver.py、douyin_resolver.py、douyin_sign.js、kuaishou_resolver.py、catalog.json、requirements.txt 上传到固定目录。
2. 如果允许主机安装 Python，安装 Python 3.11+、Node.js LTS，创建独立 Python venv，并执行 pip install -r requirements.txt；如果不允许安装 Python，按本文 Dockerfile 构建并运行容器。
3. 运行 node --check douyin_sign.js 和 Python 单元测试；Docker 部署至少检查镜像构建时的 Node 语法检查，并在容器内运行测试。
4. 直接部署时使用专用非 root 用户启动：python server.py --host 0.0.0.0 --port 8868 --cache-seconds 30 --timeout 10；Docker 部署时使用 `--restart unless-stopped`。
5. 直接部署配置 systemd 开机自启和自动重启；Docker 部署保留容器自动重启策略。
6. 只允许 TVBox 所在局域网/VPN 网段访问 8868，不要直接暴露公网。
7. 验证 /health、/v1/live/sites，以及五个平台的 categories、rooms 接口。
8. 输出服务器局域网/VPN 地址、端口、systemd 或 Docker 状态和 TVBox 应使用的 TVBOX_PLATFORM_LIVE_SERVICE_URL。
9. 不要把任何 Cookie 写入 Git、日志或公开文件；默认先不配置 Cookie，只有平台风控时再从安全环境变量注入。
```
