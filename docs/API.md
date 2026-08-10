# API Reference

本文档列出 UniNook 的核心 REST API。接口统一返回：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

需要登录的接口通过请求头传入 Token：

```http
Authorization: Bearer <token>
```

## Auth

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/auth/register` | 用户注册 | 否 |
| POST | `/api/auth/login` | 用户登录，返回 Access Token 并写入 Refresh Token Cookie | 否 |
| POST | `/api/auth/refresh` | 使用 Refresh Token Cookie 轮换会话并获取新的 Access Token | 否 |
| POST | `/api/auth/logout` | 用户退出 | 是 |

注册请求：

```json
{
  "username": "alice",
  "password": "123456"
}
```

注册只创建登录账号。服务端会生成一个临时昵称；首次登录后，客户端会要求用户确认或修改昵称，再进入校区绑定和主应用。

登录请求：

```json
{
  "username": "alice",
  "password": "123456"
}
```

登录响应只包含短期 `token` 和 `expiresIn`；Refresh Token 不会出现在 JSON 响应中，而是以 `HttpOnly`、`SameSite=Lax` Cookie 写入浏览器，Cookie 路径限定为 `/api/auth`。调用 `POST /api/auth/refresh` 时无需请求体，浏览器会自动携带 Cookie；接口成功后会返回新的 Access Token，并轮换 Cookie 中的 Refresh Token。旧 Refresh Token 只能使用一次，退出登录后当前会话的两类 Token 都会失效。

本地 HTTP 开发环境可保持 `CAMPUSCIRCLE_AUTH_REFRESH_COOKIE_SECURE=false`。部署到 HTTPS 时必须设置 `CAMPUSCIRCLE_AUTH_REFRESH_COOKIE_SECURE=true`，令 Refresh Token Cookie 仅通过 HTTPS 传输。

## User

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| GET | `/api/users/me` | 查询当前用户资料 | 是 |
| PUT | `/api/users/me` | 修改当前用户资料 | 是 |
| GET | `/api/users/{userId}` | 查询公开用户主页 | 否 |
| GET | `/api/users/{userId}/posts` | 查询用户发布的帖子 | 是 |
| GET | `/api/users/me/comments` | 查询我的评论 | 是 |
| GET | `/api/users/me/likes` | 查询我的帖子和评论点赞记录 | 是 |
| GET | `/api/users/me/school-change-quota` | 查询本月校园校区修改额度 | 是 |

修改资料请求：

```json
{
  "nickname": "Alice",
  "bio": "南京大学学生",
  "schoolId": 1
}
```

`POST /api/users/me/avatar` 使用 `multipart/form-data` 上传字段 `file`，仅支持 PNG、JPEG，大小不超过 2MB，返回可直接展示的 `avatarUrl`。前端对超过 2MB 的 PNG/JPEG 会在浏览器中自动缩放、压缩后再上传；后端仍保留格式和大小校验。上传后的文件保存在 `CAMPUSCIRCLE_AVATAR_STORAGE_DIR`，容器部署时通过 Compose 数据卷持久化。

新注册账号会在服务端获得一个临时昵称。登录响应和用户资料中的 `nicknameSetupRequired=true` 表示客户端应先引导用户确认或修改昵称；用户通过 `PUT /api/users/me` 提交昵称后，该标记会变为 `false`。

`GET /api/users/{userId}` 返回头像、昵称、账号 ID、简介、公开帖子数、评论数和点赞记录数。学校、校区和城市是校园圈的核心归属信息，会在用户主页上稳定展示。用户主页和用户帖子列表属于已知内容入口，不按照发现范围拦截。

`GET /api/users/me/comments` 与 `GET /api/users/me/likes` 都支持 `page`、`size` 分页参数。点赞记录会返回 `targetType`（`POST` 或 `COMMENT`）、`postId`、可选 `commentId`、帖子标题、内容摘要和点赞时间，前端可据此跳转原帖或准确定位评论。

`GET /api/users/me/school-change-quota` 返回当前自然月已使用次数、总额度、剩余次数和下次重置日期。例如：

```json
{
  "used": 1,
  "limit": 5,
  "remaining": 4,
  "resetsOn": "2026-09-01"
}
```

首次绑定校园校区不计入修改次数；完成绑定后，每个自然月最多修改 5 次，额度在下个月 1 日重置。服务端始终会校验该限制，客户端提示仅用于辅助决策。

## School

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| GET | `/api/schools/search` | 按关键词搜索学校 | 否 |
| GET | `/api/schools/nearby` | 查询指定学校附近的学校 | 否 |
| GET | `/api/schools/provinces` | 查询可选省份 | 否 |
| GET | `/api/schools/cities` | 按省份查询可选城市 | 否 |
| GET | `/api/schools/campuses` | 按省份、城市及关键词查询校区 | 否 |

当前数据模型中，`university` 表表示高校实体；为兼容既有关联，`school` 表表示一个具有独立坐标的物理校区。用户资料中的 `schoolId` 即校区 ID，接口响应同时返回 `universityId`、高校名称和 `campusName`。

学校搜索参数：

| 参数 | 说明 |
| --- | --- |
| `keyword` | 高校名称、校区名称、省份或城市关键词 |
| `limit` | 返回数量，默认 `10`，最大 `50` |

附近学校参数：

| 参数 | 说明 |
| --- | --- |
| `schoolId` | 当前学校 ID |
| `radiusKm` | 查询半径，单位千米，默认 `10`，最大 `50` |

级联选择参数：

| 接口 | 参数 | 说明 |
| --- | --- | --- |
| `/api/schools/cities` | `province` | 必填，目标省份 |
| `/api/schools/campuses` | `province`、`city` | 必填，目标省份与城市 |
| `/api/schools/campuses` | `keyword`、`limit` | 可选，按高校或校区名称筛选；默认最多 `50` 条 |

## Category

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| GET | `/api/categories` | 查询启用的帖子分类 | 否 |

## Post

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/posts` | 发布帖子 | 是 |
| GET | `/api/posts` | 分页查询帖子 | 否 |
| GET | `/api/posts/{postId}` | 查询当前可见范围内的帖子详情 | 是 |
| PUT | `/api/posts/{postId}` | 编辑帖子 | 是 |
| DELETE | `/api/posts/{postId}` | 删除帖子 | 是 |
| GET | `/api/posts/hot` | 查询热门帖子 | 否 |
| GET | `/api/posts/feed` | 查询当前用户附近学校帖子 Feed | 是 |

发帖请求：

```json
{
  "categoryId": 1,
  "title": "期末复习资料怎么整理？",
  "content": "想问问大家期末复习有什么方法。"
}
```

帖子列表常用查询参数：

| 参数 | 说明 |
| --- | --- |
| `page` | 页码，默认 `1` |
| `size` | 每页数量，默认 `10` |
| `categoryId` | 分类 ID |
| `keyword` | 标题或内容关键词 |
| `sort` | 排序方式，`hot` 表示按热度排序 |
| `scope` | 发现范围：`CAMPUS`（同校区）、`UNIVERSITY`（同校）、`NEARBY_10`、`NEARBY_20`、`CITY`（同市） |

帖子详情查询参数：

| 参数 | 说明 |
| --- | --- |
| `radiusKm` | 保留用于客户端上下文传递。帖子详情不再按半径拦截，已知帖子的详情可正常查看和互动。 |

附近学校帖子 Feed 查询参数：

| 参数 | 说明 |
| --- | --- |
| `page` | 页码，默认 `1` |
| `size` | 每页数量，默认 `10` |
| `scope` | 推荐范围：`CAMPUS`、`UNIVERSITY`、`NEARBY_10`、`NEARBY_20` 或 `CITY`，默认 `NEARBY_10` |
| `radiusKm` | 兼容旧客户端。未传 `scope` 时，`1-10` 映射为 `NEARBY_10`，`11-20` 映射为 `NEARBY_20`，`21-50` 映射为 `CITY` |
| `categoryId` | 分类 ID |
| `sort` | 排序方式，`hot` 表示按热度排序 |

## AI Assistant

| Method | Path | Description | Login |
| --- | --- | --- | --- |
| POST | `/api/ai/assistant/ask` | Ask questions using posts from the current user's nearby-school scope | Yes |

Request:

```json
{
  "question": "附近有哪些适合复习的地方？",
  "scope": "UNIVERSITY"
}
```

后端会根据 Token 识别当前用户，并按 `scope` 计算允许检索的校区 ID。系统仅检索这些校区中处于正常发布状态的帖子，并在生成回答时返回对应的帖子引用。当查看范围内没有检索到相关帖子时，`insufficientEvidence` 为 `true`。`radiusKm` 仍可用于兼容旧客户端，映射规则与 Feed 相同。

本地开发默认使用 `CAMPUSCIRCLE_AI_PROVIDER=mock`，无需连接外部模型服务。需要接入兼容 OpenAI Chat Completions 协议的模型服务时，将 `CAMPUSCIRCLE_AI_PROVIDER` 设置为 `openai-compatible`，并通过环境变量提供 `CAMPUSCIRCLE_AI_BASE_URL`、`CAMPUSCIRCLE_AI_API_KEY` 和 `CAMPUSCIRCLE_AI_MODEL`。仅当模型服务支持 OpenAI 兼容的 JSON Object 响应格式时，才将 `CAMPUSCIRCLE_AI_STRUCTURED_OUTPUT` 设置为 `true`；该模式不会传递 `max_tokens`，用于降低 JSON 响应被截断的风险。对于支持混合思考模式的 Qwen 模型，简短的检索增强问答建议设置 `CAMPUSCIRCLE_AI_ENABLE_THINKING=false`，仅在复杂推理任务中启用思考模式。真实 API Key 只能保存在本地环境变量或未纳入版本控制的 `.env` 文件中，不得提交到仓库。Elasticsearch 请求超时由 `CAMPUSCIRCLE_SEARCH_REQUEST_TIMEOUT_SECONDS` 控制，默认 5 秒；超时后检索链路会回退至 SQL 关键词查询。

## Comment

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/posts/{postId}/comments` | 发表评论 | 是 |
| GET | `/api/posts/{postId}/comments` | 分页查询帖子评论 | 是 |
| DELETE | `/api/comments/{commentId}` | 删除评论 | 是 |
| POST | `/api/comments/{commentId}/like` | 点赞评论 | 是 |
| DELETE | `/api/comments/{commentId}/like` | 取消点赞评论 | 是 |

评论请求：

```json
{
  "content": "我一般先整理错题，再刷历年卷。"
}
```

评论读取和发表仍兼容 `radiusKm` 查询参数，但不再以半径限制已知帖子的读取、评论或点赞。校园距离仅作用于推荐 Feed、热门列表和 AI 助手检索。

前端发表评论时会额外携带 `X-UniNook-User-Id`，用于声明当前页面展示的账号。服务端始终以 Token 解析出的用户 ID 作为真实身份；当该请求头与 Token 身份不一致时，会拒绝写入并提示重新登录，避免浏览器会话错配导致评论归属错误。服务端暂时兼容旧请求头，便于已打开的旧版页面平滑过渡。

### 评论线程规则

`POST /api/posts/{postId}/comments` 的请求体支持可选字段 `parentCommentId`。不传时创建顶级评论；传入同一帖子的任意正常评论时创建二级回复。即使回复的是二级评论，服务端仍会将其归入该顶级评论线程，因此不会形成无限嵌套。评论读取结果包含 `rootCommentId`、`parentCommentId`、`replyToUserId`、`replyToNickname`，以及评论作者的学校名称，供客户端按线程展示和跳转被回复用户主页。

删除顶级评论会一并软删除其全部二级回复，并同步扣减帖子评论数与热度；删除二级评论仅影响当前回复。作者、帖子作者和管理员拥有相应删除权限，客户端仅向普通用户展示删除自己的评论入口。

评论点赞与帖子点赞一样支持幂等操作。列表会返回 `likeCount`、`liked`，便于客户端显示点赞状态和后续按热度排序。通知会包含发起者昵称、帖子标题，以及评论内容摘要；未读通知可通过 `GET /api/notices/unread-count` 展示红点或数量。

评论会保存发表当时的学校 ID 与学校名称快照。用户之后修改学校，不会改写既有评论的学校标签。首次绑定学校不计入变更次数；完成绑定后，每个自然月最多修改学校 5 次，超出后由服务端返回冲突提示。

## 问题追踪与候选答复

“问题追踪”用于把一个仍需等待明确结论的帖子或评论标记为可订阅的问题。内容作者可发起追踪；其他登录用户可订阅或取消订阅，并在个人中心读取自己发起或订阅的问题。已知内容入口不受推荐范围限制。

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/questions` | 基于帖子或评论发起问题追踪 | 是 |
| GET | `/api/questions/by-source` | 按内容节点查询问题追踪 | 是 |
| GET | `/api/questions/by-sources` | 批量查询多个来源节点的问题摘要 | 是 |
| GET | `/api/questions/{questionId}` | 查询一个问题追踪 | 是 |
| POST | `/api/questions/{questionId}/subscriptions` | 订阅问题，重复调用保持幂等 | 是 |
| DELETE | `/api/questions/{questionId}/subscriptions` | 取消订阅 | 是 |
| GET | `/api/questions/{questionId}/answers` | 查询候选答复 | 是 |
| POST | `/api/questions/{questionId}/answers/{answerId}/ai-review` | 发起者请求 AI 辅助判断待判断候选答复 | 是 |
| POST | `/api/questions/{questionId}/answers/{answerId}/accept` | 发起者通过候选答复，可通过多条 | 是 |
| POST | `/api/questions/{questionId}/answers/{answerId}/reject` | 发起者标记候选答复无效 | 是 |
| POST | `/api/questions/{questionId}/complete` | 发起者结束问题，停止接收新的候选答复 | 是 |
| POST | `/api/questions/{questionId}/reopen` | 发起者重新开启已完成问题，继续接收候选答复 | 是 |
| DELETE | `/api/questions/{questionId}` | 发起者或管理员删除问题并清理订阅 | 是 |
| GET | `/api/users/me/questions` | 查询我发起或订阅的问题 | 是 |

发起请求：

```json
{
  "sourceType": "POST",
  "sourceId": 12,
  "questionText": "希望持续追踪这条讨论的最终结论"
}
```

`sourceType` 取值为 `POST` 或 `COMMENT`。每篇帖子和每条评论仅可发起一个问题追踪；该问题独立维护订阅、候选答复、通过结果与完成状态。只有该内容作者或管理员可以发起问题。发起者和订阅者是两种独立身份：发起者不订阅自己的问题，其他用户可随时订阅或取消订阅。发起者至少通过一条候选答复后，可以调用完成接口结束问题；结束后不再接收新的候选答复。发起者也可以调用重新开启接口将问题恢复为进行中，已有的已通过答复和订阅记录会保留，订阅者会收到重新开启提醒。

每篇帖子和每条一级评论最多拥有一个问题追踪。`GET /api/questions/by-sources` 使用 `sourceType` 和重复传入的 `sourceIds` 批量读取多个来源的问题摘要，单次最多 50 个来源节点；该接口用于帖子详情页集中展示评论来源的问题，避免按评论逐条请求造成 N+1 查询。返回结果以来源节点 ID 为键，摘要包含问题状态、已通过答复数、订阅数和当前用户是否已订阅。

查询个人问题时使用 `role=ASKED`（我发起的）或 `role=SUBSCRIBED`（我订阅的），并支持 `page`、`size` 分页参数。接口会返回来源类型、原帖 ID、来源摘要、提问者、问题描述、状态、订阅人数及当前用户的 `subscribed` 状态。

候选答复通过发布真实评论时携带可选字段 `answerQuestionId` 创建：帖子来源的问题允许任意一级评论或回复作为候选答复；一级评论来源的问题只允许其直接回复作为候选答复。候选答复初始状态为 `PENDING`，仅问题发起者可通过或标记无效；同一问题允许多条 `ACCEPTED` 答复，通过答复本身不会结束问题。发起者在至少通过一条答复后调用完成接口，系统将问题更新为 `COMPLETED` 并向当前订阅者发送已结束通知；完成不会删除订阅记录。发起者可将已完成问题重新开启为 `OPEN`，保留已通过答复与订阅并再次接收候选答复。后续用户仍可订阅已完成问题，并立即读取全部已通过答复。发起者删除问题时会先通知订阅者，再删除问题、答复和订阅记录。

候选答复评论示例：

```json
{
  "content": "逸夫馆三楼工作日开放到 22:30，晚间通常有座位。",
  "parentCommentId": null,
  "answerQuestionId": 42
}
```

`GET /api/questions/{questionId}/answers` 返回候选答复数组；问题详情响应包含 `approvedAnswerCount` 与 `approvedAnswers`。候选答复状态为 `PENDING`、`ACCEPTED`、`REJECTED` 或 `WITHDRAWN`。

发起者可对处于 `OPEN` 状态的 `PENDING` 候选答复调用 `POST /api/questions/{questionId}/answers/{answerId}/ai-review`。接口只返回临时的关联度建议：`relevanceScore`、`verdict`（`RELEVANT` / `UNCERTAIN` / `IRRELEVANT`）与 `rationale`，不写入答复状态、不自动采纳、不将普通评论加入候选答复。模拟模式使用本地关键词关联规则；配置真实模型后使用受限提示词进行辅助评估。无论何种模式，是否通过答复始终由问题发起者决定。

## Like

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| POST | `/api/posts/{postId}/like` | 点赞帖子 | 是 |
| DELETE | `/api/posts/{postId}/like` | 取消点赞 | 是 |
| GET | `/api/posts/{postId}/like` | 查询当前用户点赞状态 | 是 |

点赞、取消点赞和点赞状态查询兼容 `radiusKm` 查询参数，但不以学校距离限制已知帖子的互动。

## Notice

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| GET | `/api/notices` | 分页查询站内通知 | 是 |
| GET | `/api/notices/unread-count` | 查询未读通知数 | 是 |
| PUT | `/api/notices/{noticeId}/read` | 标记单条通知已读 | 是 |
| PUT | `/api/notices/read-all` | 标记全部通知已读 | 是 |

通知列表查询参数：

| 参数 | 说明 |
| --- | --- |
| `page` | 页码，默认 `1` |
| `size` | 每页数量，默认 `10` |
| `readStatus` | 读取状态，`0` 未读，`1` 已读 |

## Admin

后台管理接口仅管理员可访问。

| 方法 | 路径 | 说明 | 登录 |
| --- | --- | --- | --- |
| PUT | `/api/admin/posts/{postId}/hide` | 隐藏帖子 | 是 |
| PUT | `/api/admin/posts/{postId}/restore` | 恢复帖子 | 是 |
| PUT | `/api/admin/users/{userId}/disable` | 禁用用户 | 是 |
| PUT | `/api/admin/users/{userId}/enable` | 启用用户 | 是 |
| POST | `/api/admin/search/posts/reindex` | 重建全部正常帖子的 Elasticsearch 检索索引，返回已处理数量 | 是 |

## Error Codes

## Campus Scope Rules

新注册用户不带默认校区，需要先通过 `PUT /api/users/me` 绑定有效的 `schoolId`。Feed、热门列表、关键词发现列表和 AI 助手根据 Token 中的当前用户计算可见校区，未绑定用户不能使用这些发现功能。范围枚举包括 `CAMPUS`（同校区）、`UNIVERSITY`（同校）、`NEARBY_10`、`NEARBY_20` 和 `CITY`（同市）。帖子详情、评论、点赞、通知和用户帖子列表不受发现范围拦截，确保用户能通过通知、主页或直接链接完成正常互动。客户端只能提交范围枚举或兼容的 `radiusKm`，不能提交实际允许访问的校区 ID。

| code | 说明 |
| --- | --- |
| `0` | 请求成功 |
| `40000` | 请求参数错误 |
| `40001` | 用户名或密码错误 |
| `40100` | 未登录或 Token 无效 |
| `40300` | 无权限访问 |
| `40400` | 资源不存在 |
| `40900` | 资源状态冲突 |
| `40901` | 用户名已存在 |
| `50000` | 系统内部错误 |
