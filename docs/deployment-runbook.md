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

每次发布前导出 MySQL，并将备份保存到受限目录或对象存储，不能提交到仓库：

```bash
mkdir -p deploy/backups
docker compose exec -T mysql sh -lc 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction campuscircle' > deploy/backups/campuscircle-$(date +%F-%H%M).sql
```

回滚仅切换到已经验证过的 Git 标签或提交，并重新构建应用；不删除命名卷，避免误删数据库与上传头像。

```bash
git fetch --tags
git checkout <verified-tag-or-commit>
docker compose --profile app --profile search up -d --build
curl --fail http://127.0.0.1:8080/actuator/health
```

如数据库结构已发生不可逆变化，必须先使用备份和对应的迁移方案恢复，不能仅依靠应用镜像回滚。
