# 环境变量与密钥轮换手册

UniNook 的生产配置集中在服务器 `/srv/campuscircle/.env`（由 `deploy/production.env.example` 复制而来，权限 `600`）。本文说明各密钥的轮换步骤与注意事项。

## 通用原则

1. **轮换前先备份 MySQL**（`scripts/backup-mysql.sh`），防止操作失误无法回退。
2. 新密钥用强随机值生成，例如 `openssl rand -base64 32`；不要复用旧密钥，不要把密钥写进仓库、聊天记录的明文截图或备份文件。
3. 一次只轮换一种密钥，改完立即验证 `/actuator/health` 与核心页面，再轮换下一种。
4. 修改 `.env` 后用 `docker compose --profile app --profile search up -d` 重建受影响的容器才会生效；只 `restart` 不会重新读取 `.env`。
5. 轮换完成后在运维记录中登记：时间、轮换项、操作人、验证结果。

## 数据库密码（CAMPUSCIRCLE_DB_PASSWORD）

**注意**：官方 MySQL 镜像只在**命名卷首次初始化**时使用 `MYSQL_ROOT_PASSWORD` 创建密码；之后修改 `.env` 不会改变数据库里的实际密码，必须先在库内改密。

```bash
cd /srv/campuscircle
scripts/backup-mysql.sh

NEW_PWD="$(openssl rand -base64 24)"
echo "$NEW_PWD"   # 记录到密码管理器后再继续

# 1. 在数据库内修改 root 密码（用当前 .env 里的旧密码登录）
docker compose exec mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "ALTER USER \"root\"@\"%\" IDENTIFIED BY \"'\"$NEW_PWD\"'\"; ALTER USER \"root\"@\"localhost\" IDENTIFIED BY \"'\"$NEW_PWD\"'\"; FLUSH PRIVILEGES;"'

# 2. 更新 .env 中的 CAMPUSCIRCLE_DB_PASSWORD
chmod 600 .env && editor .env

# 3. 重建依赖数据库的容器
docker compose --profile app --profile search up -d
```

验证：

```bash
docker compose exec mysql sh -lc 'mysqladmin ping -uroot -p"$MYSQL_ROOT_PASSWORD" --silent'
curl --fail http://127.0.0.1:8080/actuator/health
```

`/health` 返回 `{"status":"UP"}` 即应用已用新密码连通（actuator 会聚合数据源健康指示器）。回退方式：用同样步骤把密码改回旧值。

## Redis 密码（CAMPUSCIRCLE_REDIS_PASSWORD）

当前 Compose 的 Redis 未启用密码，仅绑定回环地址。若需要启用/轮换：

```bash
cd /srv/campuscircle
NEW_PWD="$(openssl rand -base64 24)"

# 1. docker-compose.yml 的 redis 服务增加启动参数（一次性配置，之后只需换值）
#    command: ["redis-server", "--appendonly", "yes", "--requirepass", "${CAMPUSCIRCLE_REDIS_PASSWORD}"]
#    并在 .env 写入 CAMPUSCIRCLE_REDIS_PASSWORD=$NEW_PWD

# 2. 重建 redis 与应用（应用通过 spring.data.redis.password 读取）
docker compose --profile app --profile search up -d
```

注意事项：

- Redis 中存有登录 refresh token、会话历史与限流计数；重建 Redis 容器会让在线用户需要重新登录，属预期现象。
- 轮换期间如出现 `WRONGPASS`，检查 `.env` 值与 compose 命令是否一致，再重建容器。

## AI API Key（CAMPUSCIRCLE_AI_API_KEY / CAMPUSCIRCLE_SEARCH_EMBEDDING_API_KEY）

1. 在模型服务商控制台生成新 key。
2. 更新 `.env` 中对应变量（两者相互独立，可分别轮换）。
3. `docker compose --profile app --profile search up -d` 重建 `app` 容器。
4. 验证：登录后在智能问答页发起一次提问；同时用
   `docker compose logs --since=5m app | grep stage=agent-model`
   确认模型调用成功、无 401/403。
5. 回到服务商控制台**吊销旧 key**。先吊销再切新 key 会造成服务中断，顺序不能反。

## 访问令牌（无需轮换的说明）

应用访问令牌由 `TokenGenerator` 用 `SecureRandom` 生成、服务端存储校验，不存在签名密钥；“泄露处置”的方式是调用登出使 refresh token 失效或清空 Redis 会话，而不是轮换配置项。

## 检查清单

- [ ] `.env` 不在版本库中（`.gitignore` 已覆盖），权限 `600`
- [ ] 备份文件目录权限 `700`，不在公网可达路径
- [ ] 轮换后旧密钥已在源头（服务商控制台/密码管理器）吊销或删除
- [ ] `/actuator/health` 返回 UP，核心流程（登录、发帖、提问）验证通过
