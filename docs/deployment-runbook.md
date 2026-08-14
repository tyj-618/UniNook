# 首次部署与回滚手册

本手册用于将已验证的版本部署到一台 Linux 服务器。首个公开版本保持单机 Docker Compose 架构：应用、MySQL、Redis 和可选 Elasticsearch 仅监听本机，宿主机 Nginx 负责 HTTPS 和公网访问。

## 发布前检查

1. 在代码托管平台确认 `Verify` 工作流的后端测试、前端检查和 Compose 校验全部通过。
2. 准备已解析到服务器的域名；不要通过暴露 `8080`、`8088`、`3306`、`6379` 或 `9200` 端口对外提供服务。
3. 服务器安装 Docker Engine、Docker Compose Plugin、Nginx 和 Certbot，并仅在防火墙中开放 `22`、`80`、`443`。
4. 在发布前备份线上 MySQL 数据库；首次发布可跳过此项。

## 首次部署

在服务器中取得仓库后，创建生产配置。示例中的密钥均为空或占位符，不能直接用于生产。

```bash
cd /srv/campuscircle
cp deploy/production.env.example .env
chmod 600 .env
editor .env
docker compose --profile app --profile search up -d --build
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health
```

## 新服务器从零部署（完整步骤）

以下为 Debian/Ubuntu 系服务器的完整流程；其他发行版替换对应的包管理命令即可。

```bash
# 1. 基础软件：Docker Engine + Compose 插件
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"   # 重新登录后生效
docker compose version

# 2. Nginx 与证书工具
sudo apt-get install -y nginx certbot python3-certbot-nginx
sudo systemctl enable --now nginx

# 3. 防火墙只开放 22/80/443（ufw 示例）
sudo ufw allow 22/tcp && sudo ufw allow 80/tcp && sudo ufw allow 443/tcp && sudo ufw enable

# 4. 取得代码与配置
sudo mkdir -p /srv/campuscircle && sudo chown "$USER" /srv/campuscircle
git clone <repository-url> /srv/campuscircle
cd /srv/campuscircle
cp deploy/production.env.example .env
chmod 600 .env
editor .env                      # 必填：CAMPUSCIRCLE_DB_PASSWORD；其余按需

# 5. 启动全部服务（含 Elasticsearch；不需要混合检索时去掉 --profile search）
docker compose --profile app --profile search up -d --build
docker compose ps
curl --fail http://127.0.0.1:8080/actuator/health
```

首次构建需要下载依赖，可能耗时十分钟以上；`docker compose ps` 中 mysql、redis 应为 `healthy` 后再验收。

## MySQL 初始化机制

MySQL 命名卷首次创建时，容器会自动执行挂载到 `/docker-entrypoint-initdb.d/` 的 `src/main/resources/db/schema.sql` 与 `data.sql`：建库（`campuscircle`，`utf8mb4` / `utf8mb4_unicode_ci`）、建表并写入校区目录等种子数据。root 密码取 `.env` 的 `CAMPUSCIRCLE_DB_PASSWORD`。已有数据的卷不会重新初始化；后续结构变更一律走 `docs/db-migrations/` 下的迁移脚本。

## Redis 与 Elasticsearch

两者都由 Compose 管理，不需要单独安装：

- **Redis**：`redis:7.4-alpine`，开启 AOF 持久化，仅绑定回环地址；无密码（见 `docs/secrets-rotation.md` 的启用方法）。
- **Elasticsearch**：`--profile search` 下的单节点 8.x，关闭 xpack security，仅容器网络/回环可达。首次启动约需 30~60 秒；不启用混合检索时去掉 `--profile search` 并保持 `CAMPUSCIRCLE_SEARCH_ENABLED=false`。应用内置 ES/Embedding 健康探测与选择性降级，ES 故障不会导致服务整体不可用。

## RocketMQ（可选）

首个公开版本不强制部署 RocketMQ：未启用 `rocketmq` profile 时，评论/点赞等事件走数据库 outbox 定时派发（`CAMPUSCIRCLE_OUTBOX_DISPATCH_INTERVAL_MILLIS`）。需要启用时在服务器上单独部署 NameServer + Broker，例如：

```bash
docker run -d --name rmqnamesrv --restart unless-stopped \
  -p 127.0.0.1:9876:9876 apache/rocketmq:5.3.2 sh mqnamesrv
docker run -d --name rmqbroker --restart unless-stopped --network container:rmqnamesrv \
  -e "NAMESRV_ADDR=localhost:9876" apache/rocketmq:5.3.2 sh mqbroker
```

然后在 `.env` 配置 `CAMPUSCIRCLE_ROCKETMQ_NAME_SERVER=host.docker.internal:9876`（Linux 宿主机可用 `172.17.0.1:9876` 或自定义 docker 网络），并在启动命令追加 `--env-file` 注入 profile：应用的 `rocketmq` profile 由 `SPRING_PROFILES_ACTIVE` 控制（在 compose 的 `app.environment` 中追加 `SPRING_PROFILES_ACTIVE: redis,rocketmq` 后重建）。主题名见 `application-rocketmq.yaml`，需提前在 Broker 创建或开启自动建主题。

## HTTPS 证书签发

域名已解析到服务器、80 端口可访问后执行（示例配置里的 `/.well-known/acme-challenge/` 路径已就绪）：

```bash
sudo mkdir -p /var/www/certbot
# 将 deploy/nginx/campuscircle.conf.example 复制为站点配置并替换域名/证书路径
sudo nginx -t && sudo systemctl reload nginx
sudo certbot certonly --webroot -w /var/www/certbot -d your.domain.example
sudo nginx -t && sudo systemctl reload nginx
```

Certbot 会自动注册 systemd timer 续期；用 `sudo certbot renew --dry-run` 验证。证书就位后必须把 `CAMPUSCIRCLE_AUTH_REFRESH_COOKIE_SECURE=true` 写入 `.env` 并重建 app 容器。

## 既有数据库的校区目录迁移

`schema.sql` 和 `data.sql` 只会在 MySQL 命名卷首次创建时执行。已有部署升级到包含多校区目录的版本时，需要在备份后手动执行对应迁移；迁移可重复运行，不会删除用户、帖子、评论或已有校区。

当前校区目录迁移：

```bash
docker compose exec -T mysql sh -lc 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle' < docs/db-migrations/012_seed_campus_catalog.sql
```

执行后可用下列命令确认南京市已返回五个校区：

```bash
docker compose exec mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle -e "SELECT name, campus_name FROM school WHERE province=\"江苏省\" AND city=\"南京市\" AND status=0 ORDER BY name, campus_name;"'
```

如果曾在未指定客户端字符集的情况下执行过 `012`，并且页面出现乱码，执行一次下列修复迁移。它不会删除数据，只会停用编码损坏的目录行并恢复标准目录：

```bash
docker compose exec -T mysql sh -lc 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle' < docs/db-migrations/013_repair_campus_catalog_encoding.sql
```

## Admin console migration

The minimal admin console stores hide/restore, enable/disable, and index-rebuild actions in
`admin_action_log`. For an existing deployment, run this migration once after taking the usual
database backup:

```bash
docker compose exec -T mysql sh -lc 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle' < docs/db-migrations/014_admin_action_log.sql
```

There is no public administrator registration route. Promote a verified account only after checking
its identifier, then sign out and sign in again before opening `/admin`:

```bash
docker compose exec mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle -e "SELECT id, username, nickname, role, status FROM \`user\` ORDER BY id;"'
docker compose exec mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle -e "UPDATE \`user\` SET role = 1 WHERE id = <verified-user-id>;"'
```

## Content governance and feedback migration

For an existing deployment, run the following migration once after the usual database backup and
before deploying the content-governance release. It creates the report and assistant-feedback
tables without modifying existing posts, comments, or users:

```bash
docker compose exec -T mysql sh -lc 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" campuscircle' < docs/db-migrations/015_content_governance_feedback.sql
```

初次启动 Elasticsearch 可能需要几十秒。若首个版本暂不启用混合检索，可以移除 `--profile search`，并保持 `CAMPUSCIRCLE_SEARCH_ENABLED=false`。

将 `deploy/nginx/campuscircle.conf.example` 复制到 Nginx 站点配置，替换域名和证书路径。签发证书后检查并重载配置：

```bash
sudo nginx -t
sudo systemctl reload nginx
curl --fail http://127.0.0.1/health
```

最后在本机和移动网络各完成一次浏览器验收：注册/登录、发布帖子、评论与回复、问题追踪、候选答复、通知跳转、头像上传和退出登录。执行时间、版本标签和结果应记录在发布记录中。

## 发布后的验证与观察

Windows 本地或已安装 PowerShell 的服务器可执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\smoke-test.ps1
```

随后查看运行状态与最近日志：

```bash
docker compose ps
docker compose logs --tail=200 app web
```

健康检查只返回服务是否可用，不输出数据库、缓存或密钥细节。公网 Nginx 配置将 `/health` 限制为本机访问。

## 备份与回滚

每次发布前导出 MySQL，并将备份保存到受限目录或对象存储，不能提交到仓库。优先使用封装好的脚本（策略、恢复步骤与演练记录见 `docs/backup-runbook.md`）：

```bash
scripts/backup-mysql.sh                       # mysqldump + gzip，默认保留 7 天
scripts/restore-mysql.sh <备份文件> --drop   # 整库恢复（交互确认；FORCE_RESTORE=1 跳过）
```

手工导出（等价命令，供无脚本环境使用）：

```bash
mkdir -p deploy/backups
docker compose exec -T mysql sh -lc 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction campuscircle' > deploy/backups/campuscircle-$(date +%F-%H%M).sql
```

密钥/密码需要更换时按 `docs/secrets-rotation.md` 执行。

回滚仅切换到已经验证过的 Git 标签或提交，并重新构建应用；不删除命名卷，避免误删数据库与上传头像。

```bash
git fetch --tags
git checkout <verified-tag-or-commit>
docker compose --profile app --profile search up -d --build
curl --fail http://127.0.0.1:8080/actuator/health
```

如数据库结构已发生不可逆变化，必须先使用备份和对应的迁移方案恢复，不能仅依靠应用镜像回滚。

## 定时运维任务（cron）

在服务器 `crontab -e` 中加入（脚本均以非零退出码表示异常，失败行会写入对应告警日志）：

```cron
# 每 5 分钟检查应用健康（含 MySQL/Redis 聚合状态）；告警写入 /var/log/campuscircle/health-check.log
*/5 * * * * /srv/campuscircle/scripts/health-check.sh >/dev/null 2>&1

# 每 30 分钟检查磁盘空间（默认低于 5GB 告警）
*/30 * * * * /srv/campuscircle/scripts/disk-space-check.sh >/dev/null 2>&1

# 每天 03:00 数据库备份
0 3 * * * cd /srv/campuscircle && scripts/backup-mysql.sh >> /var/log/campuscircle/backup.log 2>&1
```

告警当前只写日志（预留钉钉/邮件接入点，见 `scripts/health-check.sh` 的 `alert()` 函数）。日志目录提前创建：`sudo mkdir -p /var/log/campuscircle`。

## 按 requestId 定位问题

助手请求在响应体中返回 `requestId`，全链路日志（检索/提示词/模型/工具/响应）均携带该字段：

```bash
scripts/query-by-request-id.sh <request-id>            # 默认查 docker compose 日志（最近 24h）
scripts/query-by-request-id.sh <request-id> -f app.log   # 查指定日志文件
```

更多可观测性说明见 `docs/agent-production-operations.md`。
