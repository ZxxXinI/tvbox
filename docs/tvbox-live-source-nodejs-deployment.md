# TVBox 直播源缓存同步服务部署说明（Node.js）

## 1. 目的

当前 TVBox 直接请求下面的地址：

    https://wget.la/https://raw.githubusercontent.com/yanghanhanyingshi/iptv/main/result.txt

这个地址偶尔响应时间较长，可能导致电视端加载直播源超时。推荐在自己的服务器上部署一个 Node.js 同步服务：

1. 服务器每 6 小时从原地址获取一次 result.txt。
2. 获取成功并通过格式校验后，原子替换服务器上的缓存文件。
3. 获取失败时保留上一次成功的文件。
4. TVBox 只请求自己服务器上的静态缓存文件，不再实时等待 wget.la。

最终 TVBox 请求地址示例：

    https://tv.example.com/tvbox/result.txt

本方案不要求服务器安装 Python，只需要 Node.js 18.17 或更高版本、npm，以及一个 HTTPS 域名。

## 2. 给服务器 AI 的部署要求

可以把下面的内容直接交给服务器上的 AI：

    请在这台 Linux 服务器上部署一个 TVBox 直播源缓存同步服务，禁止使用 Python，使用 Node.js 18.17+ 和 npm。

    原始直播源地址：
    https://wget.la/https://raw.githubusercontent.com/yanghanhanyingshi/iptv/main/result.txt

    要求：
    1. 创建 Node.js 项目，包含 sync.mjs 和 server.mjs。
    2. sync.mjs 每次执行时请求原始地址，超时时间 120 秒，失败最多重试 3 次。
    3. 下载内容必须校验：
       - 必须包含 #genre#；
       - 至少包含 3 条带 http:// 或 https:// 的频道线路；
       - 内容不是明显的 HTML 错误页面。
    4. 校验失败或请求失败时，不能覆盖旧的 result.txt。
    5. 写入时先写临时文件，再使用原子 rename 替换正式文件，避免 TVBox 读到半截内容。
    6. 使用 systemd timer 每 6 小时执行一次同步，服务器启动后 2 分钟先执行一次。
    7. 使用 Node.js HTTP 服务提供：
       - GET /tvbox/result.txt：返回缓存直播源；
       - GET /healthz：返回服务状态；
       - 其他路径返回 404。
    8. Node 服务只监听 127.0.0.1:8787，再使用 Caddy 或 Nginx 反向代理到 HTTPS 域名。
    9. 最终访问地址必须是：
       https://你的域名/tvbox/result.txt
    10. 完成后输出：
        - 同步脚本路径；
        - Node 服务状态；
        - systemd timer 状态；
        - HTTPS 访问地址；
        - curl 验证结果。

    不要把原始地址做成每次请求都实时转发的接口，TVBox 必须读取服务器本地缓存文件。

## 3. 推荐目录结构

    /opt/tvbox-source-sync/
    ├── package.json
    ├── sync.mjs
    └── server.mjs

    /var/lib/tvbox-source/
    └── result.txt

    /etc/tvbox-source.env
    /etc/systemd/system/tvbox-source.service
    /etc/systemd/system/tvbox-source-sync.service
    /etc/systemd/system/tvbox-source-sync.timer

建议把脚本放在 /opt，把运行中的缓存文件放在 /var/lib。这样脚本和数据分离，更新脚本时不会误删旧直播源。

## 4. Node.js 项目文件

### 4.1 package.json

    {
      "name": "tvbox-source-sync",
      "version": "1.0.0",
      "private": true,
      "type": "module",
      "engines": {
        "node": ">=18.17"
      },
      "scripts": {
        "sync": "node sync.mjs",
        "serve": "node server.mjs"
      }
    }

这个项目只使用 Node.js 内置模块，不需要安装 axios、express 等额外依赖。保留 npm scripts 是为了方便服务器 AI 和运维人员统一管理。

### 4.2 sync.mjs

    import fs from 'node:fs/promises';
    import path from 'node:path';

    const sourceUrl =
      process.env.SOURCE_URL ||
      'https://wget.la/https://raw.githubusercontent.com/yanghanhanyingshi/iptv/main/result.txt';
    const targetFile =
      process.env.TARGET_FILE || '/var/lib/tvbox-source/result.txt';
    const timeoutMs = Number(process.env.FETCH_TIMEOUT_MS || 120000);
    const maxRetries = Number(process.env.FETCH_RETRIES || 3);
    const minChannels = Number(process.env.MIN_CHANNELS || 3);

    function sleep(ms) {
      return new Promise((resolve) => setTimeout(resolve, ms));
    }

    async function fetchSource() {
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), timeoutMs);

      try {
        const response = await fetch(sourceUrl, {
          signal: controller.signal,
          headers: {
            'User-Agent': 'TVBox-Source-Sync/1.0',
            Accept: 'text/plain,text/*;q=0.9,*/*;q=0.1',
          },
        });

        if (!response.ok) {
          throw new Error('源地址返回 HTTP ' + response.status);
        }

        const bytes = await response.arrayBuffer();
        const content = Buffer.from(bytes)
          .toString('utf8')
          .replace(/^\uFEFF/, '')
          .replace(/\r\n/g, '\n')
          .trimEnd() + '\n';

        return content;
      } finally {
        clearTimeout(timeout);
      }
    }

    function validateSource(content) {
      if (!content.includes('#genre#')) {
        throw new Error('直播源中没有找到 #genre# 分组标记');
      }

      const channelLines = content
        .split('\n')
        .filter((line) => /^[^,\r\n]+,\s*https?:\/\/\S+/i.test(line));

      if (channelLines.length < minChannels) {
        throw new Error(
          '有效频道线路数量过少：' +
            channelLines.length +
            '，要求至少 ' +
            minChannels,
        );
      }

      const firstNonEmptyLine = content
        .split('\n')
        .map((line) => line.trim())
        .find(Boolean);

      if (
        firstNonEmptyLine &&
        /^<!doctype html|<html|<body/i.test(firstNonEmptyLine)
      ) {
        throw new Error('源地址返回的内容疑似 HTML 错误页面');
      }
    }

    async function writeAtomically(content) {
      const targetDirectory = path.dirname(targetFile);
      await fs.mkdir(targetDirectory, { recursive: true });

      const temporaryFile = targetFile + '.tmp-' + process.pid;

      try {
        await fs.writeFile(temporaryFile, content, 'utf8');
        await fs.rename(temporaryFile, targetFile);
      } catch (error) {
        await fs.rm(temporaryFile, { force: true });
        throw error;
      }
    }

    async function syncOnce() {
      const content = await fetchSource();
      validateSource(content);
      await writeAtomically(content);

      const channelCount = content
        .split('\n')
        .filter((line) => /^[^,\r\n]+,\s*https?:\/\/\S+/i.test(line))
        .length;

      console.log(
        new Date().toISOString() +
          ' 同步成功：' +
          channelCount +
          ' 条频道线路，文件：' +
          targetFile,
      );
    }

    async function main() {
      let lastError;

      for (let attempt = 1; attempt <= maxRetries; attempt += 1) {
        try {
          await syncOnce();
          return;
        } catch (error) {
          lastError = error;
          console.error(
            new Date().toISOString() +
              ' 第 ' +
              attempt +
              '/' +
              maxRetries +
              ' 次同步失败：',
            error,
          );

          if (attempt < maxRetries) {
            await sleep(attempt * 5000);
          }
        }
      }

      console.error(
        new Date().toISOString() +
          ' 同步最终失败，保留旧文件：',
        lastError,
      );
      process.exitCode = 1;
    }

    main().catch((error) => {
      console.error('同步程序异常退出：', error);
      process.exitCode = 1;
    });

### 4.3 server.mjs

    import { createReadStream } from 'node:fs';
    import fs from 'node:fs/promises';
    import { createServer } from 'node:http';

    const host = process.env.BIND_HOST || '127.0.0.1';
    const port = Number(process.env.PORT || 8787);
    const targetFile =
      process.env.TARGET_FILE || '/var/lib/tvbox-source/result.txt';

    const server = createServer(async (request, response) => {
      const requestUrl = new URL(
        request.url || '/',
        'http://127.0.0.1',
      );

      if (requestUrl.pathname === '/healthz') {
        response.writeHead(200, {
          'Content-Type': 'application/json; charset=utf-8',
          'Cache-Control': 'no-store',
        });
        response.end(
          JSON.stringify({
            ok: true,
            service: 'tvbox-source-sync',
            time: new Date().toISOString(),
          }),
        );
        return;
      }

      if (requestUrl.pathname !== '/tvbox/result.txt') {
        response.writeHead(404, {
          'Content-Type': 'text/plain; charset=utf-8',
        });
        response.end('Not Found\n');
        return;
      }

      if (request.method !== 'GET' && request.method !== 'HEAD') {
        response.writeHead(405, {
          Allow: 'GET, HEAD',
          'Content-Type': 'text/plain; charset=utf-8',
        });
        response.end('Method Not Allowed\n');
        return;
      }

      try {
        const stat = await fs.stat(targetFile);

        response.writeHead(200, {
          'Content-Type': 'text/plain; charset=utf-8',
          'Cache-Control': 'no-cache, must-revalidate',
          'X-Content-Type-Options': 'nosniff',
          'Content-Length': stat.size,
          'Last-Modified': stat.mtime.toUTCString(),
        });

        if (request.method === 'HEAD') {
          response.end();
          return;
        }

        createReadStream(targetFile).on('error', (error) => {
          console.error('读取缓存文件失败：', error);
          response.destroy(error);
        }).pipe(response);
      } catch (error) {
        console.error('缓存文件不存在或无法读取：', error);
        response.writeHead(503, {
          'Content-Type': 'text/plain; charset=utf-8',
          'Cache-Control': 'no-store',
        });
        response.end('直播源暂时不可用\n');
      }
    });

    server.listen(port, host, () => {
      console.log(
        'TVBox source server listening on http://' + host + ':' + port,
      );
    });

    process.on('SIGTERM', () => {
      server.close(() => process.exit(0));
    });

## 5. 环境变量文件

创建 /etc/tvbox-source.env：

    SOURCE_URL=https://wget.la/https://raw.githubusercontent.com/yanghanhanyingshi/iptv/main/result.txt
    TARGET_FILE=/var/lib/tvbox-source/result.txt
    FETCH_TIMEOUT_MS=120000
    FETCH_RETRIES=3
    MIN_CHANNELS=3
    BIND_HOST=127.0.0.1
    PORT=8787

如果以后更换原始源，只需要修改 SOURCE_URL，不需要修改代码。

## 6. Linux 初始化命令

下面以 Ubuntu 或 Debian 为例。服务器 AI 应先检查 Node.js 版本，如果低于 18.17，需要升级到 Node.js 20 LTS 或更高版本。

    node --version
    npm --version

创建运行用户和目录：

    sudo useradd --system --home /opt/tvbox-source-sync --shell /usr/sbin/nologin tvbox-source
    sudo mkdir -p /opt/tvbox-source-sync
    sudo mkdir -p /var/lib/tvbox-source
    sudo chown -R tvbox-source:tvbox-source /opt/tvbox-source-sync
    sudo chown -R tvbox-source:tvbox-source /var/lib/tvbox-source

如果 tvbox-source 用户已经存在，useradd 命令提示已存在时可以忽略，继续执行目录创建命令。

把 package.json、sync.mjs 和 server.mjs 放入：

    /opt/tvbox-source-sync/

然后执行：

    cd /opt/tvbox-source-sync
    npm install

本项目没有第三方依赖，因此 npm install 主要用于检查 package.json；不需要 Python 和 pip。

## 7. systemd 服务

### 7.1 Node HTTP 服务

创建 /etc/systemd/system/tvbox-source.service：

    [Unit]
    Description=TVBox cached source HTTP server
    After=network-online.target
    Wants=network-online.target

    [Service]
    Type=simple
    User=tvbox-source
    Group=tvbox-source
    WorkingDirectory=/opt/tvbox-source-sync
    EnvironmentFile=/etc/tvbox-source.env
    ExecStart=/usr/bin/node /opt/tvbox-source-sync/server.mjs
    Restart=always
    RestartSec=5
    NoNewPrivileges=true

    [Install]
    WantedBy=multi-user.target

如果 node 不在 /usr/bin/node，使用 command -v node 找到实际路径，并修改 ExecStart。

### 7.2 一次同步服务

创建 /etc/systemd/system/tvbox-source-sync.service：

    [Unit]
    Description=Sync TVBox source from upstream
    Wants=network-online.target
    After=network-online.target

    [Service]
    Type=oneshot
    User=tvbox-source
    Group=tvbox-source
    WorkingDirectory=/opt/tvbox-source-sync
    EnvironmentFile=/etc/tvbox-source.env
    ExecStart=/usr/bin/node /opt/tvbox-source-sync/sync.mjs
    NoNewPrivileges=true

### 7.3 每 6 小时定时器

创建 /etc/systemd/system/tvbox-source-sync.timer：

    [Unit]
    Description=Run TVBox source sync every 6 hours

    [Timer]
    OnBootSec=2min
    OnUnitActiveSec=6h
    Persistent=true
    Unit=tvbox-source-sync.service

    [Install]
    WantedBy=timers.target

加载并启动：

    sudo systemctl daemon-reload
    sudo systemctl enable --now tvbox-source.service
    sudo systemctl enable --now tvbox-source-sync.timer

首次部署后建议立即手动同步一次：

    sudo systemctl start tvbox-source-sync.service
    sudo systemctl status tvbox-source-sync.service --no-pager
    sudo systemctl status tvbox-source-sync.timer --no-pager

查看日志：

    sudo journalctl -u tvbox-source-sync.service -n 100 --no-pager
    sudo journalctl -u tvbox-source.service -n 100 --no-pager
    systemctl list-timers tvbox-source-sync.timer

## 8. HTTPS 反向代理

Node 服务只监听 127.0.0.1:8787，不直接暴露到公网。推荐使用 Caddy，因为它可以自动申请和续期 HTTPS 证书。

### 8.1 Caddy 配置

安装 Caddy 后，在 Caddyfile 中加入：

    tv.example.com {
        reverse_proxy 127.0.0.1:8787
    }

把 tv.example.com 替换成自己的域名，并将 DNS 的 A 记录指向服务器公网 IP。开放 80 和 443 端口：

    sudo systemctl reload caddy

最终测试：

    curl -I https://tv.example.com/tvbox/result.txt
    curl -fsS https://tv.example.com/healthz
    curl -fsS https://tv.example.com/tvbox/result.txt | head -n 20

正常情况下应看到：

- result.txt 返回 HTTP 200；
- Content-Type 为 text/plain；
- healthz 返回包含 ok:true 的 JSON；
- result.txt 内容包含 #genre# 和频道 URL；
- 访问速度通常应在 1 秒内完成。

### 8.2 已经使用 Nginx 的情况

如果服务器已经使用 Nginx，可以把下面配置放到对应的 HTTPS server 中：

    location /tvbox/ {
        proxy_pass http://127.0.0.1:8787;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

然后检查并重载：

    sudo nginx -t
    sudo systemctl reload nginx

## 9. TVBox 端修改

服务器部署并验证成功后，把 TVBox 的直播源地址改成：

    https://你的域名/tvbox/result.txt

当前项目中的地址位置：

    app/src/main/java/com/tvbox/app/data/LiveRepository.kt

需要修改 LIVE_SOURCE_URL，然后重新构建和安装 APK。解析器不需要调整，因为服务器返回的仍然是原来的 result.txt 格式：

    央视卫视,#genre#
    CCTV1,https://example.com/cctv1.m3u8
    CCTV1,https://example.com/cctv1-backup.m3u8$LR•IPV4•29『线路2』

TVBox 会继续完成以下处理：

- 按 #genre# 分组；
- 同组同名频道合并；
- 去掉 URL 后面的线路元数据；
- 遥控器上/下切换线路；
- 当前线路失败时尝试下一条线路。

## 10. 故障处理策略

### 原始地址请求失败

sync.mjs 会重试 3 次。如果仍然失败：

- 不修改旧的 result.txt；
- systemd 服务返回失败状态；
- TVBox 仍然可以读取上一次成功的直播源；
- 可以通过 journalctl 查看原因。

### 原始地址返回 HTML 错误页

脚本会检查 #genre# 和频道线路数量。错误页面通常无法通过校验，因此不会覆盖旧文件。

### 缓存文件被读到一半

脚本先写入带进程号的临时文件，完成后使用 rename 替换正式文件。临时文件和正式文件在同一个目录，Linux 下替换是原子的。

### TVBox 仍然加载很慢

依次检查：

    curl -I https://你的域名/tvbox/result.txt
    sudo systemctl status tvbox-source.service --no-pager
    sudo systemctl status tvbox-source-sync.timer --no-pager
    sudo journalctl -u tvbox-source.service -n 100 --no-pager
    sudo journalctl -u tvbox-source-sync.service -n 100 --no-pager

如果 curl 请求也很慢，检查 HTTPS 反代配置；如果 curl 很快但 TVBox 很慢，检查电视盒子的 DNS、网络和 HTTPS 证书。

## 11. 安全建议

- 使用 HTTPS，不建议长期使用裸 IP 的 HTTP 地址。
- Node 服务只监听 127.0.0.1，不要直接开放 8787 端口。
- 不要把它实现成任意 URL 代理，避免变成开放代理。
- 只返回固定的 /tvbox/result.txt 和 /healthz 路径。
- 同步脚本不应把服务器密钥写入代码或直播源文件。
- 定期检查同步日志，确认源文件仍然能够通过校验。

## 12. 部署完成检查清单

- [ ] Node.js 版本至少为 18.17。
- [ ] /opt/tvbox-source-sync/package.json 存在。
- [ ] sync.mjs 和 server.mjs 可以正常执行。
- [ ] /var/lib/tvbox-source/result.txt 已生成。
- [ ] tvbox-source.service 处于 active 状态。
- [ ] tvbox-source-sync.timer 已启用。
- [ ] systemctl list-timers 可以看到下一次同步时间。
- [ ] HTTPS 域名可以访问 /healthz。
- [ ] HTTPS 域名可以访问 /tvbox/result.txt。
- [ ] curl 请求在较短时间内返回 HTTP 200。
- [ ] TVBox 已改用自己的服务器地址。
- [ ] 盒子进入直播页后可以正常加载频道。


