# UniNook Agent 升级 — Codex 任务书

> 目标：把 UniNook 的**单轮 RAG 校园助手**，升级为具备**多轮对话、工具调用（Function Calling）、流式输出**的最小可用 Agent。
> 执行方式：**分阶段交付，每个阶段一个独立 commit/PR**。阶段间可暂停让用户 review，不要一次把全部阶段塞进一个改动。

---

## 0. 项目上下文（先读这些，少走弯路）

- **项目根**：`D:\GitCode\UniNook`（本仓库）
- **构建**：Maven，Java 17，Spring Boot **4.0.6**（`spring-boot-starter-web`，servlet 栈）
- **测试命令**：`mvnw.cmd test`（Windows）/ `./mvnw test`。测试用 H2 + 现有 Mock 客户端，不依赖外部服务。**任何改动后必须跑全量测试并保持全绿。**
- **后端包根**：`com.uninook`；AI 相关集中在 `com.uninook.ai`
- **前端**：`frontend/`（Vue 3 + Vite + Axios）
- **配置前缀**：`campuscircle.ai`（历史遗留，**不要改名**，改名会破坏现有配置）

### 关键现有类（升级起点，动手前先读这些）

| 类 | 职责 | 升级角色 |
|---|---|---|
| `AiAssistantService` | 单轮 RAG 入口，`ask(auth, request)` | 改造成多轮 / Agent 编排入口 |
| `AiModelClient`（接口） | `generate()` / `generateText()` | 增加 `stream()` + tools 支持 |
| `OpenAiCompatibleModelClient` | 千问 OpenAI 兼容实现，**已内置重试+退避+结构化输出+超时** | 增加流式与 `tool_calls` |
| `MockAiModelClient` | 测试 mock | **每加新接口方法必须同步实现** |
| `HybridPostRetriever` / `PostRetriever` | 混合检索（BM25+kNN+RRF） | 作为 Agent 的"检索工具" |
| `PromptBuilder` | 拼 system + user prompt | 增加历史 / 工具描述注入 |
| `AiProperties` | 配置项（前缀 `campuscircle.ai`） | 增加新配置项 |
| `AiRequestRateLimiter` | 已有 QPM 限流 | 复用 |
| `CurrentUserService` | 当前登录态（`requireUserId`） | 安全：参数从这注入 |

### 全局红线（每个阶段都必须遵守）

1. **安全决策只在服务端**：`user_id` / `campus_id` / 校区权限一律从 `CurrentUserService` 登录态注入，**绝不信任 LLM 返回的这类参数**（参数覆写）。
2. **工具白名单**：只注册安全工具；不在注册表里的调用一律拒绝。
3. **破坏性操作需用户确认**：发帖、删除、@、群发等，Agent 只返回"待确认动作"，由前端向用户确认后才执行，Agent 不自作主张。
4. **不删除任何有效业务数据**；检索回查后不足时只清理脏数据，不重删有效帖子。
5. **异常捕获要细化**：网络/连接异常才降级（`IOException` / `ResourceAccessException` / `RestClientResponseException`），**不要 `catch (RuntimeException)` 一刀切降级**，避免掩盖代码 bug（现有 `HybridPostRetriever` 有这个问题）。
6. **保持抽象一致**：`AiModelClient` 接口每加方法，`OpenAiCompatibleModelClient` 和 `MockAiModelClient` 同步实现，保证 mock 路径可测。
7. **每阶段**：全量测试全绿 + 为新能力补测试。

---

## Phase 1 — 多轮对话 + 会话管理（P0，最小可用多轮）

**目标**：同一 session 内的多轮对话能关联上下文。

**改动**：
1. `AiAssistantRequest` 增加可选 `sessionId` 字段。
2. 新增会话存储抽象 + Redis 实现（如 `ChatSessionStore` 接口 + `RedisChatSessionStore`，复用现有 `StringRedisTemplate`），key 形如 `session:{id}:messages`，存 JSON 消息数组，TTL 可配（默认 30 分钟）。
3. `AiAssistantService.ask()`：读历史 → 拼消息 → 调 LLM → 追加历史写回。无 `sessionId` 时行为不变（兼容单轮）。
4. `AiModelClient` 的 `generate()` 支持传入**完整 messages 列表**（而不是现在只 system+user 两条）——需要改 `buildRequestBody`。
5. `PromptBuilder` 增加历史注入能力。
6. 上下文压缩：先做**滑动窗口**（保留最近 N 条，N 可配，默认 10~15）；摘要压缩留到后续，但抽象上预留扩展点。

**验收**：
- 新增测试：同一 `sessionId` 连续两次提问，第二次的请求 messages 里含第一次内容（用 Mock 客户端断言）。
- 无 `sessionId` 的单轮路径不受影响。
- 全量测试绿。

**注意**：此阶段只做 Redis 热层历史，**不做 MySQL 冷层**（冷层属 P2，避免范围爆炸），但 `ChatSessionStore` 接口预留冷层扩展位。

---

## Phase 2 — 流式响应（SSE，P1）

**目标**：LLM 输出逐字流式返回前端。

**改动**：
1. `AiModelClient` 增加流式方法（返回流 / 回调）。
2. `OpenAiCompatibleModelClient`：请求体加 `stream: true`，逐行解析 SSE 响应（`data: {...}` 行，结束 `data: [DONE]`）。当前用 `RestClient` 一次性 `body(String.class)`，流式需改用能读流的方式（`WebClient` 或原生流读取），注意设置足够读超时。
3. 新增流式 Controller（`AiAssistantStreamController`），用 `SseEmitter`（servlet 栈，`org.springframework.web.servlet.mvc.method.annotation.SseEmitter`）逐块推送。
4. 前端：`AssistantPage.vue` 改为 `fetch` + `ReadableStream` 手动解析 SSE。**不要用 `EventSource`**——它不支持自定义 `Authorization` header。
5. `MockAiModelClient` 提供 mock 流式（分块返回）。

**验收**：mock 下流式端点能逐块推送；非流式路径不受影响；全量测试绿。

---

## Phase 3 — Agent 编排循环 + 工具调用（P0/P1，核心）

**目标**：LLM 在循环里自主决定调用哪个工具（至少把"检索帖子"做成内置工具），多步完成任务。

**改动**：
1. 新增 `ToolDefinition` / `ToolRegistry` / `ToolCallExecutor`。
2. 新增 `AgentOrchestrator`：实现 ReAct 循环（thought → action → observation），含**终止三件套**（显式 finish / `maxSteps` 兜底 5~8 / 无工具可调即结束）+ **防死循环**（连续重复相同 (action+params) 达 2 次就打断回填提示）。
3. `OpenAiCompatibleModelClient`：支持 `tools` + `tool_choice`，工具执行结果以 `role: "tool"` 消息回填（OpenAI 兼容格式），继续下一轮推理。
4. 安全：新增 `ToolSecurityValidator`（白名单 + 参数覆写 + 权限校验）。`user_id` / `campus_id` 等服务端已知参数**不接受 LLM 提取值**，从登录态注入。
5. 结果校验 + 自纠错：LLM 返回的 tool_call 参数做 JSON Schema 校验，失败把错误信息回填让 LLM 重试（最多 2~3 次），仍失败则跳过该工具、让 LLM 用已有信息回答。
6. 工具分级：写操作（发帖/删除等）只返回"待确认"，前端确认后才执行。
7. 先把检索能力封装为内置工具（如 `search_posts`）；或保留现有检索管线、额外注册只读工具，二选一，选更小改动路径。

**验收**：Mock 测试覆盖 ReAct 循环、工具调用、参数覆写、自纠错、死循环兜底、写操作确认。全量测试绿。

---

## Phase 4 — 可观测性 + 降级增强 + 评测（P1/P2）

**目标**：生产可用性。

**改动**：
1. 统一 `requestId` 贯穿全链路（检索 → Prompt → LLM → 响应），分段埋点（检索 trace / LLM 输入输出 + token + 延迟 / 降级路径标记）。
2. 选择性降级：`HybridPostRetriever.retrieve()` 分路 try-catch——ES 挂、Embedding 正常则退化为纯 kNN；反向则纯 BM25；全挂才 SQL 兜底。同时细化异常类型（对应红线 5）。
3. 主动健康检查：定时检测 ES / Embedding，连续失败 N 次标记不可用、恢复后自动切回。
4. 评测（轻量起步）：建一份 30~50 条的 golden set（query → 期望命中帖子/期望回答要点），提供命中率计算脚本，供改 Prompt 后回归。

**验收**：日志字段完整可追溯；选择性降级 + 健康检查有测试；评测脚本可跑通。全量测试绿。

---

## 交付与沟通约定

- 每个 Phase 完成后：跑 `mvnw.cmd test`，在 PR 描述里写清"改了什么、新增了哪些测试、测试是否全绿、有没有偏离本任务书的地方（尤其红线）"。
- 遇到本任务书与真实代码冲突时：**以真实代码为准**，但必须在 PR 描述里说明冲突点，不要擅自改安全/配置前缀等红线约定。
- 不确定的破坏性改动（改表结构、改鉴权、改 MQ 等）先停下来问，不要猜。
