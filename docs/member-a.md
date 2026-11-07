# 成员 A 实现与联调

成员 A 负责 M1 用户与应用入口，以及供四人共享的前后端工程骨架。当前实现使用 Vue 3、TypeScript、Spring Boot 4、Java 17、PostgreSQL。前端在 `frontend/`，后端在 `backend/`。M2、M3、M4 各自负责后续业务页面和数据表。

## 本地启动

准备 Java 17 或更高版本、Node.js 24、PostgreSQL 17。可使用仓库的 `compose.yaml` 启动 PostgreSQL。M4 开始联调消息时再启动 RabbitMQ profile。M1 本身只需要数据库。

1. 将 `.env.example` 复制为本地 `.env`，填写数据库密码、初始管理员账号及密码。首次数据库为空时必须提供管理员账号和至少 10 位的初始密码。`.env` 已被 Git 忽略。
2. 在有 Docker Desktop 的环境下，运行 `docker compose up -d postgres`。若已有本地 PostgreSQL，可创建 `checkcollect` 数据库和对应用户，并将 `DATABASE_URL`、`DATABASE_USER`、`DATABASE_PASSWORD` 指向该实例。
3. 运行 `powershell -File scripts/run-backend.ps1`。Maven Wrapper 下载依赖，Flyway 自动执行迁移，后端监听 `http://localhost:8080`。
4. 在 `frontend/` 运行 `npm ci`、`npm run dev`，打开 `http://localhost:5173`。Vite 将 `/api` 请求代理到后端。

设置 `APP_SEED_DEMO=true` 并提供至少 10 位的 `DEMO_PASSWORD` 时，后端在缺少对应账号时创建 `member-a`、`member-b`、`member-c`、`member-d` 四个独立测试账号。它们使用相同的**初始密码**，登录后各自必须改密。四个账号各有不同 UUID。默认角色分别为 A 的管理员和采集者、B 的采集者、C 的核验者、D 的第三方工作人员。开发成员与实际业务角色可由管理员另行调整。

正式构建时，先运行 `powershell -File scripts/build-app.ps1`，脚本安装锁定的前端依赖、构建页面、复制到后端静态资源目录，再打包后端。生成的静态文件已被 Git 忽略。部署时将 `FRONTEND_BASE_URL` 设置为浏览器实际访问的站点地址，并将 `SESSION_COOKIE_SECURE=true` 与 HTTPS 一起启用。

## 账号及身份接口

所有写操作发送 `X-XSRF-TOKEN` 请求头。先调用 `GET /api/auth/csrf` 获取 token。前端的 `src/api.ts` 已处理这一过程。浏览器通过 HttpOnly `JSESSIONID` Cookie 保持登录态，刷新页面时用 `GET /api/me` 重新取得当前用户。

| 方法和路径 | 说明 |
| --- | --- |
| `GET /api/auth/csrf` | 获取写请求使用的 CSRF token |
| `POST /api/auth/login` | `{"username":"member-b","password":"..."}` 登录，返回用户摘要 |
| `POST /api/auth/logout` | 退出并结束当前会话 |
| `GET /api/me` | 返回 `id`、`username`、`displayName`、`roles`、`enabled`、`mustChangePassword`、`wechatBound` |
| `POST /api/me/password` | `{"oldPassword":"...","newPassword":"..."}`，首次登录必做 |
| `GET /api/admin/users` | 管理员列出全部账号 |
| `POST /api/admin/users` | 管理员创建账号，传 `username`、`displayName`、`initialPassword`、`roles` |
| `PATCH /api/admin/users/{id}` | 管理员更新姓名、启用状态及角色 |
| `GET /api/auth/wechat/status` | 查看微信网站应用配置是否完整，不返回密钥 |
| `GET /api/auth/wechat/start` | 发起电脑端微信扫码登录 |
| `POST /api/me/wechat/bind/start` | 已登录用户发起绑定，返回微信授权 URL |
| `GET /api/auth/wechat/callback` | 微信平台回调，完成扫码登录或绑定 |

错误状态中，`401` 表示需要登录或当前账号已停用，`403` 表示权限不足或首次登录尚未改密，`409` 表示账号或微信绑定冲突。账号不以微信昵称为键，前后端通过稳定的 UUID 关联业务操作。

## 给 B、C、D 的接口

后端 `identity/IdentityService` 提供 `currentUser()`、`requireRole(Role)`、`listUsers(Role)`。`currentUser()` 返回最新用户信息并检查账号启用状态。`requireRole` 额外检查业务角色。`listUsers` 是供本应用内部调用的角色成员查询，返回指定角色的启用用户摘要，供 C 选第三方工作人员、D 生成群发收件人，包括系统后台任务发起的通知。服务方法在同一进程调用，不经 HTTP 绕行。

前端在 `src/router.ts` 注册 B 的 `/poi`、C 的 `/review`、D 的 `/messages` 入口。队友实现页面后替换对应 `ModuleView` 即可。角色入口只帮助用户导航，后端业务接口仍需使用 `requireRole` 及业务记录的参与人校验。M1 的数据库迁移只创建用户、角色和微信绑定表；其他模块的表由各自负责。

## 微信网站应用配置

在 `.env` 填写 `WECHAT_APP_ID`、`WECHAT_APP_SECRET`、`WECHAT_REDIRECT_URI`。回调 URI 指向后端的 `/api/auth/wechat/callback`，并与微信开放平台网站应用的回调配置匹配。`FRONTEND_BASE_URL` 指向登录页所在的前端站点，后端在授权完成后转回该地址。开发时使用已备案的可访问测试域名和 HTTPS 回调，浏览器访问域名、Cookie 和代理配置须保持一致。

首次扫码前，用户先用自己的团队账号登录，在“我的账号”绑定自己的微信。一个微信只绑定一个本地用户，同一用户最多绑定一个微信。扫码登录找到绑定用户的 UUID 后建立该用户的会话。授权使用 10 分钟有效的一次性 `state`，绑定回调还会核对当前登录用户。微信密钥只保存在后端环境变量，不提交仓库。

当前仓库没有真实网站应用凭据，因此真实微信扫码和绑定需在配置到位后联调。账号密码、角色、会话及数据库约束可在本地独立验证。

## 验收检查

从空数据库启动并确认迁移成功。分别登录四个测试账号，确认 `GET /api/me` 返回四个不同 UUID。完成首次改密后，检查普通用户无法访问 `/api/admin/users`。管理员停用某账号后，该账号已有会话再次请求应返回 `401`。退出后访问 `/api/me` 应返回 `401`。

微信配置到位后，由成员 A 的微信绑定账号 A，成员 B 的微信绑定账号 B。两人扫码后分别得到各自 UUID，再分别用手机密码登录，核对同一人的两个登录方式返回相同 UUID。重复、跨账号绑定应返回冲突，取消授权或过期回调应回到登录页并显示失败提示。
