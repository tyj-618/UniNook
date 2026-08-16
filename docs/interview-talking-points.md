# UniNook 面试话术

## Q1: 为什么做这个项目？
- 解决真实痛点：校园信息分散，新生找不到有用信息
- 技术探索：验证 Agent 在垂直场景的可行性
- 求职需要：全栈项目展示工程能力

## Q2: Agent 编排是怎么设计的？
- ReAct 循环：thought → action → observation
- 终止三件套：显式 finish / maxSteps 兜底 / 无工具可调即结束
- 防死循环：连续重复相同 (action+params) 达 2 次就打断
- 安全：user_id/campus_id 从登录态注入，不信任 LLM 返回值

## Q3: RAG 检索怎么做的？
- 混合检索：ES BM25（关键词）+ kNN（向量）+ RRF（融合）
- 选择性降级：ES 挂退化为纯 kNN，反向则纯 BM25，全挂才 SQL 兜底
- 检索结果注入 Prompt 作为上下文

## Q4: 流式输出怎么实现的？
- 后端：SseEmitter + 逐块推送
- 前端：fetch + ReadableStream 手动解析（不用 EventSource，因为不支持自定义 Authorization header）
- 会话管理：Redis 存消息数组，滑动窗口保留最近 N 条

## Q5: 遇到什么技术难点？
- Jackson 版本冲突：Spring Boot 4 引入 Jackson 3，RestClient 默认转换器不兼容 Jackson 2 JsonNode → 改为手动 String body + readTree 解析
- 重复举报漏洞：测试时发现 report 表无唯一约束 → 补了服务层检查 + 返回 40900
- 组件拆分时序：PostDetailPage 拆分时评论折叠状态初始化时序问题 → 用 replyFocus prop + version 触发

## Q6: 怎么保证代码质量？
- 测试：108/108 全绿，核心链路覆盖率 > 80%
- 异常细化：只 catch 网络/连接异常，RuntimeException 一律上抛
- 可观测性：requestId 全链路贯穿，分段埋点
- 设计语言：Design Token 体系，无硬编码样式值

## Q7: 如果重新做，会改进什么？
- 冷层会话存储：当前只有 Redis 热层，后续加 MySQL 冷层
- 多 Agent 协作：当前单 Agent，后续可拆分为检索 Agent + 回答 Agent
- 评测体系：建 golden set，改 Prompt 后回归测试
