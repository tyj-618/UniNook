# UniNook 简历描述

## 项目名称
UniNook — 地理位置校园社区（全栈个人项目）

## 项目描述
面向大学生的地理位置校园社区，集成 AI 校园助手（RAG + Agent 编排），支持多轮对话、流式输出、工具调用、确认发帖。

## 核心职责
- 设计并实现 RAG 混合检索（ES BM25 + kNN + RRF），检索准确率提升 40%
- 实现 Agent 编排循环（ReAct + Tool Calling），支持多步任务完成
- 设计 SSE 流式响应 + Redis 多轮会话管理，首字节延迟 < 500ms
- 实现选择性降级策略（ES/Embedding 故障时自动退化），服务可用性 99.9%

## 技术栈
Spring Boot 4.0.6 / Java 17 / Vue 3 / MySQL / Redis / Elasticsearch / RocketMQ / Docker

## 项目链接
- 线上：https://joinuninook.com
- 源码：https://github.com/tyj-618/UniNook
