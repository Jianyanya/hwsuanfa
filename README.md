# 校队算法训练平台

第一版：用于算法竞赛队的刷题记录、训练任务、比赛与内容交流管理。

**已知问题：洛谷题目标签抓取尚不稳定，部分题目回填失败或缺少标签。第一版暂保留该功能，后续继续修复；能力雷达只统计已成功保存标签的题目。**

## 功能

- 训练首页：能力雷达、平台过题占比、难度分布、提交热力图。
- 队伍、作业、每日一题、题单投稿与审核、积分及排行榜。
- 比赛报名、个人/队伍成绩、获奖审核与证书。
- 博客、草稿、点赞、收藏、评论及附件，公告和个人通知。
- 个人资料、平台账号绑定、隐私设置和站主后台。
- Codeforces / AtCoder 提交同步，洛谷油猴脚本同步及其他平台手动导入。

技术栈：Spring Boot 3 / Java 21、MySQL 8、Redis、React 19、TypeScript、Vite、Tailwind CSS 3。

## 目录

```text
backend/       后端源码、结构迁移及公共规则初始化 SQL
frontend/      前端源码、静态资源及测试
tools/        洛谷同步脚本、离线检查和独立建库验证工具
```

## 本地运行

需要 JDK 21+、Maven 3.8+、Node.js 22+、MySQL 8 和 Redis。先自行启动数据库与 Redis。

### 数据库

仅对全新、空数据库执行：

```sql
SOURCE backend/src/main/resources/db/schema.sql;
SOURCE backend/src/main/resources/db/data.sql;
```

在项目根目录运行 MySQL 客户端后执行上述命令。`schema.sql` 含 DROP TABLE，**已上线或已有成员数据的数据库不要重新执行**。`data.sql` 仅初始化公共积分规则和难度分值，不创建账号、队伍或示例公告。

已有库升级前先备份，按现有结构选择 `backend/src/main/resources/db/migration-*.sql`。本次发布清理没有新增迁移，也不需要清空已有数据。

### 配置与启动

后端从系统环境变量读取配置；仓库不提供默认登录账号、固定密码或固定邀请码。

| 环境变量                    | 用途                                |
| --------------------------- | ----------------------------------- |
| `DB_URL`                    | JDBC 地址；缺省为本机 `hwsuanfa` 库 |
| `DB_USERNAME`               | 数据库用户名，缺省 `root`           |
| `DB_PASSWORD`               | 数据库密码，必须配置                |
| `JWT_SECRET`                | 至少 32 字节的随机密钥，必须配置    |
| `REDIS_HOST` / `REDIS_PORT` | 缺省 `localhost` / `6379`           |
| `REDIS_PASSWORD`            | Redis 密码，无密码实例可留空        |
| `APP_REGISTER_ENABLED`      | 是否允许注册，发布配置默认 `false`  |
| `APP_REGISTER_MEMBER_CODE`  | 普通成员邀请码                      |
| `APP_REGISTER_ADMIN_CODE`   | 站主邀请码，不可对成员公开          |

也可使用 `backend/application-local.yml` 保存本机配置，该文件已被 Git 忽略。它相对于后端启动工作目录加载；环境变量优先级高于本地 YAML。不要将本机配置复制进发布包。普通 `.env` 不会被 Spring Boot 自动加载。

```powershell
# 在当前终端设置实际值，不要将实际口令提交到仓库
$env:DB_PASSWORD = '<数据库密码>'
$env:JWT_SECRET = '<至少32字节随机密钥>'
cd backend
mvn spring-boot:run
```

另开终端：

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://localhost:5173`，开发代理将 `/api` 转发到 `8080`。Windows 可在配置好环境变量或本地 YAML 后运行 `dev-start.bat`，脚本不负责安装依赖或初始化数据库。

### 首个站主

新站点临时设置 `APP_REGISTER_ENABLED=true` 和随机的 `APP_REGISTER_ADMIN_CODE`，通过注册页创建站主。创建后清空管理员邀请码并重启服务；需要成员注册时单独配置普通邀请码。已有站点继续使用原账号，删除默认账号提示不会改动数据库中的任何账号或密码。

## 已上线站点更新

1. 备份 MySQL 和 Redis，保留现有密钥及运行配置。
2. 配置实际的数据库口令、JWT 密钥和邀请码；本版源码已移除内置开发凭据。
3. 后端执行 `mvn clean package`，将生成的可执行 JAR 部署到服务端并重启。
4. 前端执行 `npm ci`、`npm run build`，部署 `frontend/dist`，更新静态资源后刷新页面。
5. 使用 HTTPS；静态服务器对前端路由回退到 `index.html`，并将 `/api/` 反向代理到后端。生产环境不用 Vite 开发服务器。

健康检查为 `/actuator/health`。若此前生产环境沿用了源码中的开发口令、邀请码或默认账号密码，请在部署端更换；变更 JWT 密钥会使已有登录令牌失效，应安排在发布时进行。

## 洛谷同步与限制

脚本位于 `tools/hwsuanfa-luogu-sync.user.js`，安装到 Tampermonkey 后，将 `SITE_BASE` 改成自己的站点地址，在本站绑定洛谷账号，再按面板提示设置访问令牌。

提交同步读取登录用户的记录；元数据回填目前调用站主接口，因此普通成员不能使用该按钮。

**题目标签抓取仍有问题，尚未完成稳定性修复。** 当前后端仍逐题获取洛谷页面，尚未实现完整的共享元数据队列或断点续传；部分题目可能返回 HTTP 401/403，或因页面结构、标签解析及请求超时而无法回填。将请求移到后端不能消除洛谷的权限和访问限制，离线解析测试通过也不代表真实批量回填成功。首次发布不承诺标签完整率，缺少标签会影响首页能力统计。详细说明见 [工具说明](tools/README.md)。

## 验证

```powershell
cd backend
mvn test
cd ../frontend
npm run lint
npm test
npm run build
cd ..
node tools/check-schema.cjs
node --test tools/test-luogu-metadata.cjs
node tools/check-release.cjs
```
