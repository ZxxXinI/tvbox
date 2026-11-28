# TVBox 仓库协作说明

## 提交和同步代码

- 主 TVBox 工程提交后，同步推送到 GitHub 的 `origin/main` 和 Gitee 的 `gitee/main`。提交使用现有的 GitHub 隐私邮箱和昵称。如果用户明确要求只在本地提交，以当次要求为准。
- GitHub 仓库为 [ZxxXinI/tvbox](https://github.com/ZxxXinI/tvbox)，Gitee 仓库为 [zhen-xin/tv-box](https://gitee.com/zhen-xin/tv-box)。Gitee 的 `agent` 是历史 OTA 分支，源码同步目标为 `main`。
- 远端历史存在差异时，通过同步提交保留两边历史。推送后核对远端分支，确认两边的最新文件内容一致。

## 同步发布附件

- 发布 APK、更新清单或其他附件时，将同一份文件上传到 GitHub 和 Gitee 上对应版本的发行版。
- Gitee 网页附件可使用 `playwright-cli` 技能上传。复用名为 `tvbox-gitee` 的 Edge 持久会话，在 Gitee 的发行版页面选择对应版本和附件文件。

```powershell
playwright-cli -s=tvbox-gitee open https://gitee.com/zhen-xin/tv-box/releases --headed --persistent --browser=msedge
```

- 首次登录、登录过期或遇到验证码时，由用户在浏览器里完成。之后使用同一个会话继续操作。
- 上传完成后，在两边核对版本和附件名称。面向用户下载的 APK 可用未登录的请求完整下载，校验文件大小和 SHA-256。
