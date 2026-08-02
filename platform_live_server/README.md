# TVBox 斗鱼平台直播测试服务

本服务运行在局域网电脑上，只负责斗鱼房间签名、播放地址解析和短时缓存；TVBox 仍直接请求斗鱼 CDN 视频流。服务会优先返回 HLS（m3u8）以获得更稳定的长时间播放，FLV 仅作为 HLS 不可用时的兜底。

平台直播浏览按照“斗鱼 → 一级大类 → 二级分类 → 直播间”的层级工作；分类接口会同时返回一级 `parentCategories` 和完整二级 `categories`。

## 运行要求

- Python 3.11 或更高版本
- Node.js LTS（斗鱼动态签名脚本由 Node 执行）
- 已安装 `requirements.txt` 中的 Python 依赖（仅 `requests`；Node.js 由服务直接调用）

## 启动

```powershell
cd platform_live_server
python -m pip install -r requirements.txt
python server.py --host 0.0.0.0 --port 8866
```

电视和电脑必须在同一局域网。TVBox 构建使用电脑的局域网地址，例如 `http://192.168.0.5:8866`；不要填写 `localhost` 或 `127.0.0.1`。

Windows 防火墙如提示是否允许 Python 访问网络，请仅勾选“专用网络”。

## 接口

| 接口 | 说明 |
| --- | --- |
| `GET /health` | 服务状态 |
| `GET /v1/live/catalog` | 测试频道目录 |
| `GET /v1/live/sites` | 平台列表 |
| `GET /v1/live/categories?site=douyu` | 斗鱼一级大类和二级分类；返回 `parentCategories` 与 `categories` |
| `GET /v1/live/rooms?site=douyu&categoryId=xxx&page=1` | 指定二级分类的直播间分页 |
| `GET /v1/live/resolve?site=douyu&roomId=36252` | 返回临时直链和播放器所需请求头 |

成功解析的地址只缓存 30 秒。斗鱼的播放直链和签名会过期，因此 TVBox 在切台或重试时会重新解析。

## 测试频道

编辑 `catalog.json` 添加或修改房间。每个频道需要唯一 `id`、显示名 `name`、固定 `site: "douyu"` 和数字 `roomId`。斗鱼未开播时，TVBox 会显示未开播提示。

## 安全边界

斗鱼的签名规则要求执行服务端下发的动态 JavaScript。本服务仅用于你自己的局域网测试：请以普通用户运行、不要保存个人 Cookie 或密钥，也不要在未加认证与 HTTPS 的情况下开放公网端口。
