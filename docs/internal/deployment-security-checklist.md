# 部署安全检查清单

首次上线前必须完成以下检查：

- 使用强随机值设置 `CAMPUSCIRCLE_DB_PASSWORD`，不要保留示例密码。
- 保持 `CAMPUSCIRCLE_BIND_ADDRESS=127.0.0.1`；由宿主机 Nginx 或 Caddy 负责对外提供 HTTPS，不能直接暴露 MySQL、Redis、Elasticsearch 或应用端口。
- 在 HTTPS 域名下设置 `CAMPUSCIRCLE_AUTH_REFRESH_COOKIE_SECURE=true`。
- 将 `.env`、数据库卷、Redis 卷和上传文件卷纳入备份策略；备份文件不得公开访问。
- 搜索服务仅在容器网络内访问。生产环境若需要直接开放 Elasticsearch，必须启用认证和 TLS。
- 反向代理应配置有效证书、HTTP 到 HTTPS 跳转和访问日志轮转。

Compose 的默认端口绑定为 `127.0.0.1`，因此服务器上的反向代理可转发到 `http://127.0.0.1:8088`。只有确实需要局域网联调时，才将 `CAMPUSCIRCLE_BIND_ADDRESS` 改为特定网卡地址；不要使用 `0.0.0.0` 公开内部服务。
