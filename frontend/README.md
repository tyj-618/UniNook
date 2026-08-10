# UniNook Frontend

UniNook 的用户端采用 Vue 3、TypeScript、Vite、Vue Router 和 Axios 实现。当前已接入登录、注册、登录态恢复，以及按距离范围查看附近学校帖子的 Feed。

## 本地启动

```bash
npm install
npm run dev
```

默认访问地址为 `http://127.0.0.1:5173`。开发服务器会将 `/api` 代理到本机 Spring Boot 服务的 `http://localhost:8080`。

## 常用命令

```bash
npm run dev
npm run build
npm run preview
```

## 目录说明

```text
src/
├── api/        Axios 客户端与后端接口封装
├── auth/       本地会话和登录态恢复
├── components/ 可复用布局组件
├── pages/      页面级 Vue 单文件组件
├── router/     路由定义与登录守卫
└── types/      前后端接口对应的 TypeScript 类型
```

详细的接口约定见 [`../docs/frontend-api-contract.md`](../docs/frontend-api-contract.md)。
