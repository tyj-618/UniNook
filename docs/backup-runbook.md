# MySQL 备份与恢复手册

本手册覆盖 UniNook（数据库名 `campuscircle`）的备份策略、恢复步骤与演练记录。备份对象是 Compose 中的 MySQL 服务及其命名卷 `campuscircle_campuscircle-mysql-data`；Redis、Elasticsearch 与上传文件卷的处置见文末说明。

## 备份策略

| 项 | 策略 |
|---|---|
| 频率 | 每日一次（cron），另在每次发布/迁移前手动执行一次 |
| 方式 | `mysqldump --single-transaction`（InnoDB 一致性快照，不锁表）+ gzip |
| 保留 | 默认 7 天，超期自动清理（可配） |
| 存放 | 服务器 `/srv/campuscircle/backups/`（权限 `700`），条件允许时异地复制一份到对象存储 |
| 红线 | 备份文件不得提交到仓库、不得公网可访问（`.gitignore` 已排除 `backups/`） |

## 脚本

### scripts/backup-mysql.sh

从 Compose MySQL 容器内执行 `mysqldump`，压缩后写入 `BACKUP_DIR`，并清理超过保留期的旧备份。数据库密码取自容器环境变量（来自 `.env` 的 `CAMPUSCIRCLE_DB_PASSWORD`），不会出现在命令行参数中。

```bash
scripts/backup-mysql.sh
# 输出示例：
# [backup-mysql] OK: /srv/campuscircle/backups/campuscircle-2026-08-15-030001.sql.gz (16K)
```

可用环境变量：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `COMPOSE_PROJECT_NAME` | `campuscircle` | Compose 项目名 |
| `MYSQL_SERVICE` | `mysql` | Compose 服务名 |
| `DB_NAME` | `campuscircle` | 数据库名 |
| `BACKUP_DIR` | 仓库下 `backups/` | 备份目录 |
| `BACKUP_RETENTION_DAYS` | `7` | 保留天数 |

脚本会在写入后校验文件首行是 mysqldump 头；校验失败会删除残缺文件并以非零码退出，可直接被 cron 邮件或监控捕获。

### scripts/restore-mysql.sh

从备份文件恢复：

```bash
# 覆盖式恢复到现有库（表结构与数据按 dump 内容覆盖）
scripts/restore-mysql.sh backups/campuscircle-2026-08-15-030001.sql.gz

# 整库重建：先 DROP DATABASE 再以 utf8mb4/utf8mb4_unicode_ci 重建，然后恢复
scripts/restore-mysql.sh backups/campuscircle-2026-08-15-030001.sql.gz --drop
```

默认会交互确认；自动化/演练场景用 `FORCE_RESTORE=1` 跳过确认。恢复完成后脚本输出库内表数量供核对。

## 定时备份（cron）

```cron
# 每天 03:00 备份；03:30 磁盘检查（见 disk-space-check.sh）
0 3 * * * cd /srv/campuscircle && COMPOSE_PROJECT_NAME=campuscircle BACKUP_DIR=/srv/campuscircle/backups scripts/backup-mysql.sh >> /var/log/campuscircle/backup.log 2>&1
30 3 * * * /srv/campuscircle/scripts/disk-space-check.sh >/dev/null 2>&1
```

## 恢复步骤（生产故障）

1. 评估影响面，决定恢复点：`ls -lh /srv/campuscircle/backups/`，选择故障前最近一次成功备份。
2. （可选但推荐）把当前损坏的库再备份一份，保留现场：`BACKUP_DIR=/tmp/incident scripts/backup-mysql.sh`。
3. 停写：`docker compose stop app web`，避免恢复期间的并发写入。
4. 恢复：`FORCE_RESTORE=1 scripts/restore-mysql.sh <备份文件> --drop`。
5. 启动：`docker compose --profile app --profile search up -d`。
6. 验证：
   - `curl --fail http://127.0.0.1:8080/actuator/health` 返回 `{"status":"UP"}`；
   - 抽查关键表行数（`user`、`post`、`comment`）与备份前记录一致；
   - 浏览器走一遍登录、发帖、评论流程。
7. 若迁移不可逆（参见 `docs/db-migrations/`），恢复旧备份后不要直接跑新镜像，须按 `deployment-runbook.md` 的回滚节回到与备份匹配的提交。

## 演练记录

| 日期 | 环境 | 过程 | 结果 |
|---|---|---|---|
| 2026-08-15 | 本地 Docker（Windows + WSL） | `backup-mysql.sh` 生成 16K 备份 → `DROP DATABASE campuscircle` → `restore-mysql.sh --drop` 恢复 | 通过。恢复后 16 张表，`user=25 post=14 comment=63 school=6` 与演练前完全一致，中文标题/昵称抽样无乱码 |

演练要求：每次生产大版本发布前至少复现一次上表流程；恢复耗时与数据量一并记录。

## 其他有状态组件

- **Redis**：`appendonly yes`，数据在 `campuscircle_campuscircle-redis-data` 卷。缓存与会话可丢失重建，一般不纳入每日备份；如需备份，停写后复制卷内 `appendonlydir`。
- **Elasticsearch**：索引可由 MySQL 重建（管理后台的索引重建动作），不单独备份。
- **上传头像**：`campuscircle-upload-data` 卷，纳入卷级备份或对象存储同步。
