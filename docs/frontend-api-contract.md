# CampusCircle 前端接口契约

本文档记录前端第一阶段与 Spring Boot 后端之间的约定，作为前端实现和项目理解的共同入口。

## 1. 通用响应和认证

后端响应统一为：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

需要登录的接口使用：

```http
Authorization: Bearer <token>
```

前端在 `frontend/src/api/client.ts` 中通过 Axios 请求拦截器读取本地会话并注入该请求头。若后端返回 `401`，响应拦截器会清理会话，并通知 Vue 认证状态模块将用户切换为未登录状态。

## 2. 认证流程

### 学校绑定

注册仅创建账号，不会为用户静默分配学校。登录后，前端先获取完整个人资料；当 `schoolId` 为空时，路由守卫会将用户引导至 `/onboarding/school`。用户通过 `GET /api/schools/search` 搜索学校，再调用 `PUT /api/users/me` 保存 `schoolId`。在完成绑定前，Feed、发帖、帖子互动和 AI 问答都会被服务端拒绝。

### 注册

```text
注册页 -> POST /api/auth/register -> 注册成功 -> 跳转登录页
```

请求体：

```json
{
  "username": "alice",
  "password": "123456",
  "nickname": "Alice"
}
```

注册接口不返回 Token，因此不能直接视为登录成功。

### 登录

```text
登录页 -> POST /api/auth/login -> token + UserSummary
       -> localStorage 保存会话 -> 跳转 /feed
```

登录返回：

```json
{
  "token": "token-value",
  "expiresIn": 86400,
  "user": {
    "id": 1,
    "username": "alice",
    "nickname": "Alice",
    "avatarUrl": null,
    "role": 0
  }
}
```

刷新页面后，Vue Router 的全局前置守卫会调用 `authStore.restore()`，再通过 `GET /api/users/me` 校验 Token 并恢复当前用户资料。路由元信息 `requiresAuth` 标识受保护页面；未登录用户访问这类页面时会被重定向到 `/login`，登录成功后返回原目标页面。

### 退出登录

```text
点击退出 -> POST /api/auth/logout -> 无论请求是否成功都清理本地会话
```

这是因为本地 Token 已无法继续作为可信凭证；后端异常不能阻止用户主动退出。

## 3. 第一阶段页面与 API 对应关系

| 页面 | 当前接口 | 第一阶段职责 |
| --- | --- | --- |
| 登录页 | `POST /api/auth/login` | 获取并保存会话，进入受保护页面 |
| 注册页 | `POST /api/auth/register` | 创建账号后转向登录 |
| 校园 Feed | `GET /api/posts/feed` | 按当前用户学校、距离和排序查看帖子 |
| 应用壳 | `GET /api/users/me` | 页面刷新时恢复并验证登录态 |

后续页面将依次接入发布/编辑、通知、个人资料、AI 问答和最小后台管理能力。帖子详情、评论和点赞已在第二阶段接入。

## 4. 附近校园 Feed

请求：

```http
GET /api/posts/feed?page=1&size=10&radiusKm=10&sort=latest
Authorization: Bearer <token>
```

参数：

| 参数 | 前端作用 |
| --- | --- |
| `page`、`size` | 普通分页；当前第一版默认加载第 1 页 |
| `radiusKm` | 用户选择的附近学校范围，允许 10、20、50 km |
| `categoryId` | 后续分类筛选使用 |
| `sort` | `latest` 最新，`hot` 热门 |

后端根据 Token 推导当前用户和所属学校，前端只传递半径、分类、排序等展示参数，不能也不应自行传递允许学校 ID。这是权限边界：学校范围的可信计算必须保留在后端。

帖子卡片使用的主要字段：

```text
id / title / summary
school.name / category.name / author.nickname
viewCount / likeCount / commentCount / createdAt
```

## 5. 前端目录职责

```text
frontend/src/
├── api/       请求封装，不放页面状态
├── auth/      会话存储和 Vue 响应式认证状态
├── components/ 可复用布局组件
├── pages/     路由页面，组织接口调用与页面状态
├── router/    路由定义和全局登录守卫
└── types/     后端响应对应的 TypeScript 类型
```

这样划分的目的不是增加文件数量，而是防止页面同时承担 HTTP 请求、Token 逻辑、全局状态与展示逻辑，导致后续帖子和 AI 页面难以维护。

## 6. 个人主页与互动记录

路由约定：

```text
/profile                 当前用户主页
/settings/profile        当前用户资料设置
/users/:id               其他用户公开主页
```

资料页仅负责修改昵称、头像和简介；头像通过文件选择或拖拽上传，超过 2MB 的 PNG/JPEG 在浏览器端优先保留分辨率与清晰度后再自动压缩，后端返回统一的公开资源地址。学校和校区属于校园圈业务状态，在个人主页提供独立的“切换校园”入口：确认新的校园校区后，用户可选择进入新校园圈，或返回个人主页。个人主页负责展示用户信息与内容记录。作者头像、昵称与二级回复中的 `@用户` 都使用 `RouterLink` 跳转到 `/users/:id`。当前用户主页额外提供三个记录页签：

```text
GET /api/users/{id}
GET /api/users/{id}/posts?page=1&size=20
GET /api/users/me/comments?page=1&size=20
GET /api/users/me/likes?page=1&size=20
GET /api/users/me/school-change-quota
```

从主页打开帖子时路由携带 `source=profile`、`profileUserId` 和当前页签。帖子详情据此返回原主页而不是默认返回 Feed；评论记录还携带 `commentId`，详情页会加载、展开并定位对应评论。

## 7. 问题订阅与候选答复

问题追踪由内容作者发起，订阅者与发起者是独立角色。发起者不订阅自己的问题；订阅者可在问题进行中或已完成后订阅，并可在“进行中 / 已完成”两个栏目取消订阅。

```text
POST   /api/questions
GET    /api/questions/by-source?sourceType=POST|COMMENT&sourceId={id}
GET    /api/questions/{questionId}
POST   /api/questions/{questionId}/subscriptions
DELETE /api/questions/{questionId}/subscriptions
GET    /api/questions/{questionId}/answers
POST   /api/questions/{questionId}/answers/{answerId}/accept
POST   /api/questions/{questionId}/answers/{answerId}/reject
POST   /api/questions/{questionId}/complete
POST   /api/questions/{questionId}/reopen
DELETE /api/questions/{questionId}
GET    /api/users/me/questions?role=ASKED|SUBSCRIBED
```

候选答复不是独立的文本表单。用户先发布真实评论或回复，再在同一次请求中带上 `answerQuestionId`：

```ts
createPostComment(postId, {
  content: '逸夫馆三楼工作日开放到 22:30。',
  parentCommentId: null,
  answerQuestionId: 42,
})
```

帖子来源的问题可接受其下任意评论或回复作为候选答复；评论来源的问题仅允许由一级评论发起，并仅接受该评论的直接回复。候选答复状态为 `PENDING`、`ACCEPTED`、`REJECTED` 或 `WITHDRAWN`。仅发起者可通过或标记无效；同一问题可通过多条答复，单次通过不会改变问题状态。发起者至少通过一条答复后调用 `POST /api/questions/{questionId}/complete` 结束问题，之后不再接收新候选答复；前端展示所有已通过答复，并向当时订阅者提供结束通知。发起者可用 `POST /api/questions/{questionId}/reopen` 重新开启已完成问题，保留已通过答复和订阅记录，同时恢复候选答复提交。完成后订阅记录保留，后来的用户也可以订阅并直接查看结果。删除问题会通知订阅者，然后清理答复和订阅记录。

当前帖子详情页覆盖帖子来源的问题追踪、候选答复预览、通过/标记无效、结束/重新开启问题和订阅；候选答复详情页按“已通过 / 未判断”展示全部答复。问题追踪列表统一先进入候选答复详情页，再跳转原帖；从该路径进入原帖后，返回链路为“候选答复 -> 原帖 -> 问题追踪”。帖子问题在详情页预览三条候选答复；评论问题统一聚合到帖子的问题追踪区域，并保留查看原评论、候选答复和快速作答入口。批量读取接口避免按评论逐条请求造成 N+1 问题。

## 9. 本地开发

前端使用 Vite 开发服务器，默认地址为 `http://127.0.0.1:5173`。`vite.config.ts` 会将 `/api` 代理到 `http://localhost:8080`，因此本地请求表现为同源请求，不需要在浏览器端写死后端完整地址。

环境变量示例见 `frontend/.env.example`：

```text
VITE_API_BASE_URL=/api
```

生产部署时 Nginx 也会采用相同思想：静态前端由 Nginx 提供，`/api` 反向代理至 Spring Boot。这样可以减少 CORS 配置，并避免把后端主机地址散落在前端代码中。

## 10. 帖子详情与互动

路由：

```text
/posts/:id?radiusKm=10
```

信息流卡片使用 `RouterLink` 进入详情页，并将当前选中的 `radiusKm` 一并传递。详情页请求：

```text
GET  /api/posts/{postId}?radiusKm=10
GET  /api/posts/{postId}/comments?page=1&size=20&radiusKm=10
POST /api/posts/{postId}/comments?radiusKm=10
POST /api/posts/{postId}/like?radiusKm=10
DELETE /api/posts/{postId}/like?radiusKm=10
```

所有接口均要求登录。发现层接口由后端从 Token 推导当前用户及其所属学校，再按 `scope` 计算可见学校列表；前端不传递学校 ID，避免将权限边界放到浏览器端。帖子详情、评论、点赞、通知跳转和个人主页属于已知内容入口，不会因为当前发现范围不同而被拦截。

详情页同时加载帖子正文和评论列表，分别处理加载、错误、空评论、重复点赞和重复提交评论等状态。帖子详情的 `liked` 字段已经提供当前用户点赞状态，因此首屏无需额外调用点赞状态接口。评论提交统一使用 Enter 发送、Shift + Enter 换行；当用户勾选“作为候选答复”时，前端只在当前存在进行中问题时传递 `answerQuestionId`。
