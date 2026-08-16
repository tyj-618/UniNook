# UniNook 前端设计语言升级 — Qoder 实施指令

> 配套文档：`uninook-design-language.md`（设计语言规范）
> 项目路径：`D:\GitCode\UniNook\frontend`
> 技术栈：Vue 3 + Vite + 原生 CSS（无预处理器、无组件库）

---

## 总体原则

- **不要改任何业务逻辑和 Vue 组件结构**，只改样式层
- **不要改 class 名**（除非本文档明确要求），只改 class 对应的 CSS 属性值
- **每完成一个阶段，确保页面功能正常、无样式错乱**
- **所有颜色、间距、圆角、阴影值必须用 CSS 变量**，不允许硬编码

---

## 阶段 1：Design Token 基础设施（P0）

### 任务 1.1：创建 token 文件

新建 `src/styles/tokens.css`，将 `uninook-design-language.md` 第一章"Design Tokens"中所有变量写入。格式如下：

```css
:root {
  /* === 品牌色 === */
  --brand-50:   #edf8f4;
  --brand-100:  #d4f0e6;
  /* ... 完整照搬文档中的变量 ... */

  /* === 语义色 === */
  /* ... */

  /* === 中性色 === */
  /* ... */

  /* === 渐变 === */
  /* ... */

  /* === 字体 === */
  /* ... */

  /* === 间距 === */
  /* ... */

  /* === 圆角 === */
  /* ... */

  /* === 阴影 === */
  /* ... */

  /* === 动效 === */
  /* ... */

  /* === 层级 === */
  /* ... */
}
```

### 任务 1.2：创建全局基础样式文件

新建 `src/styles/base.css`，包含：

1. `:root` 中设置 `color`, `background`, `font-family` 使用 token 变量
2. 全局 reset（保留现有 `* { box-sizing: border-box }` 等）
3. 自定义滚动条样式（用 token 变量）
4. `::selection` 选中态样式：`background: var(--brand-100); color: var(--brand-800)`

### 任务 1.3：在入口引入

修改 `src/main.ts`，在现有 import 之前加入：

```ts
import './styles/tokens.css'
import './styles/base.css'
```

### 验收标准

- [ ] `tokens.css` 包含文档中全部 token 变量
- [ ] `base.css` 包含 reset + 滚动条 + selection
- [ ] 页面显示正常（因为还没替换硬编码值，视觉上应与之前一致）
- [ ] 无 console 报错

---

## 阶段 2：全局硬编码值替换（P0）

### 任务 2.1：替换 `src/index.css` 中的硬编码值

按以下映射表，将 `index.css` 中所有硬编码颜色值替换为 token 变量：

| 原值 | 替换为 |
|------|--------|
| `#0e7667` | `var(--brand-600)` |
| `#075f52` | `var(--brand-700)` |
| `#0a6659` | `var(--brand-700)` |
| `#096355` | `var(--brand-700)` |
| `#0b6e60` | `var(--brand-700)` |
| `#087664` | `var(--brand-700)` |
| `#162c26` | `var(--text-primary)` |
| `#17241f` | `var(--text-primary)` |
| `#1d3027` | `var(--text-primary)` |
| `#17352d` | `var(--text-primary)` |
| `#30413a` | `var(--text-secondary)` |
| `#31423a` | `var(--text-secondary)` |
| `#2c3c35` | `var(--text-secondary)` |
| `#385249` | `var(--text-secondary)` |
| `#405149` | `var(--text-secondary)` |
| `#4d6258` | `var(--text-secondary)` |
| `#496157` | `var(--text-secondary)` |
| `#506158` | `var(--text-tertiary)` |
| `#526159` | `var(--text-tertiary)` |
| `#54675e` | `var(--text-tertiary)` |
| `#597067` | `var(--text-tertiary)` |
| `#5e6f66` | `var(--text-tertiary)` |
| `#627168` | `var(--text-tertiary)` |
| `#63736c` | `var(--text-tertiary)` |
| `#64756c` | `var(--text-tertiary)` |
| `#6a7a71` | `var(--text-tertiary)` |
| `#6d8178` | `var(--text-tertiary)` |
| `#708078` | `var(--text-tertiary)` |
| `#718078` | `var(--text-tertiary)` |
| `#9aaba3` | `var(--text-disabled)` |
| `#f6f8f7` | `var(--bg-page)` |
| `#f4f8f6` | `var(--bg-page)` |
| `#fbfcfb` | `var(--bg-subtle)` |
| `#fbfdfc` | `var(--bg-subtle)` |
| `#f8fcfa` | `var(--bg-subtle)` |
| `#f4fbf8` | `var(--bg-subtle)` |
| `#f8fbf9` | `var(--bg-subtle)` |
| `#edf5f1` | `var(--bg-muted)` |
| `#eaf5f1` | `var(--brand-50)` |
| `#e4f0ec` | `var(--avatar-bg)` |
| `#dff0e9` | `var(--brand-100)` |
| `#edf8f4` | `var(--brand-50)` |
| `#e2f4ed` | `var(--brand-50)` |
| `#e4f5ef` | `var(--brand-50)` |
| `#f4f8f6` | `var(--bg-page)` |
| `#dce4df` | `var(--border-subtle)` |
| `#e2e9e5` | `var(--border-subtle)` |
| `#dbe3df` | `var(--border-subtle)` |
| `#d8e9e2` | `var(--brand-200)` |
| `#d7e7e1` | `var(--border-subtle)` |
| `#cbd6d0` | `var(--border-default)` |
| `#d5dfda` | `var(--border-default)` |
| `#bac9c1` | `var(--border-strong)` |
| `#bac6c0` | `var(--border-strong)` |
| `#b9dcd0` | `var(--border-strong)` |
| `#82afa4` | `var(--brand-300)` |
| `#9bcfc1` | `var(--brand-300)` |
| `#9bcbb3` | `var(--success-border)` |
| `#78a99c` | `var(--brand-400)` |
| `#77b6a9` | `var(--brand-400)` |
| `#c8e2d9` | `var(--brand-200)` |
| `#b9d9cf` | `var(--brand-200)` |
| `#cbdcd5` | `var(--border-default)` |
| `#d9c586` | `var(--warning-border)` |
| `#fffaf0` | `var(--warning-bg)` |
| `#81691b` | `var(--warning-text)` |
| `#e3cf95` | `var(--warning-border)` |
| `#fffcf3` | `var(--warning-bg)` |
| `#8a6a15` | `var(--warning-text)` |
| `#c7363f` | `var(--danger-icon)` |
| `#b42318` | `var(--danger-text)` |
| `#b4232c` | `var(--danger-text)` |
| `#921d26` | `var(--danger-700, #921d26)` /* 新增 token 或保留硬编码 */ |
| `#fff3f3` | `var(--danger-bg)` |
| `#fff8f8` | `var(--danger-bg)` |
| `#e7c5c5` | `var(--danger-border)` |
| `#9b4040` | `var(--danger-text)` |
| `#b4232c` | `var(--danger-text)` |
| `#19734c` | `var(--success-text)` |
| `#edf8f1` | `var(--success-bg)` |
| `#f6fcf8` | `var(--success-bg)` |
| `#476056` | `var(--text-secondary)` |
| `#557167` | `var(--text-tertiary)` |
| `rgb(14 118 103 / 12%)` | `var(--shadow-focus-input)` /* 用变量替代 */ |
| `rgb(14 118 103 / 24%)` | `rgba(14, 158, 116, 0.18)` /* 对应 shadow-focus-ring */ |
| `rgb(22 42 35 / 8%)` | 对应 `var(--shadow-lg)` 的 rgba 值 |
| `rgb(22 42 35 / 7%)` | 对应 `var(--shadow-card-hover)` 的 rgba 值 |
| `rgb(22 42 35 / 6%)` | 对应 `var(--shadow-sm)` 的 rgba 值 |
| `rgb(15 31 25 / 36%)` | `var(--bg-overlay)` |
| `rgb(15 31 25 / 28%)` | 略浅的 overlay，可保留或新建 `--bg-overlay-light` |
| `rgb(15 31 25 / 20%)` | 对应 `var(--shadow-dialog)` |
| `rgb(14 118 103 / 16%)` | 对应 `var(--shadow-button)` |
| `rgb(14 118 103 / 17%)` | 对应 `var(--shadow-button-hover)` |
| `rgb(14 118 103 / 10%)` | 保留或对应 `var(--shadow-xs)` |
| `rgb(11 58 49 / 72%)` | 保留（头像上传遮罩，特殊场景） |
| `rgb(11 25 21 / 74%)` | 保留（图片预览遮罩，特殊场景） |

### 任务 2.2：替换 Vue 组件 `<style>` 中的硬编码值

检查所有 `.vue` 文件的 `<style>` 块，同样按上述映射替换。涉及文件：

- `src/components/AppShell.vue`
- `src/pages/FeedPage.vue`
- `src/pages/LoginPage.vue`
- `src/pages/AssistantPage.vue`
- 以及其他所有含 `<style>` 的 `.vue` 文件

### 任务 2.3：替换圆角硬编码

| 原值 | 替换为 |
|------|--------|
| `border-radius: 6px`（按钮/输入框） | `var(--radius-sm)` |
| `border-radius: 7px`（segmented/小组件） | `var(--radius-sm)` 或 `calc(var(--radius-sm) + 1px)` |
| `border-radius: 8px`（卡片/面板/弹窗） | `var(--radius-md)` |
| `border-radius: 9px` | `var(--radius-md)` |
| `border-radius: 10px` | `var(--radius-md)` |
| `border-radius: 12px`（对话气泡） | `var(--radius-lg)` |
| `border-radius: 50%` | `var(--radius-full)` |
| `border-radius: 999px`（pill） | `var(--radius-pill)` |
| `border-radius: 4px`（segmented 内部 active） | `4px`（保留，内部元素） |

### 任务 2.4：替换阴影硬编码

将 `box-shadow` 中的硬编码值替换为对应 token：

- 卡片 hover 阴影 → `var(--shadow-card-hover)`
- 弹窗阴影 → `var(--shadow-dialog)`
- 按钮阴影 → `var(--shadow-button)` / `var(--shadow-button-hover)`
- focus ring → `var(--shadow-focus-input)` 或 `var(--shadow-focus-ring)`

### 验收标准

- [ ] `index.css` 中无硬编码颜色值（特殊遮罩除外）
- [ ] 所有 `.vue` 文件 `<style>` 中无硬编码颜色值
- [ ] 页面视觉与之前基本一致（允许因色值微调产生的细微差异）
- [ ] 所有页面功能正常，无样式丢失
- [ ] 在 Chrome DevTools 中检查 computed style，确认变量已生效

---

## 阶段 3：组件样式升级（P0）

### 任务 3.1：按钮升级

在 `index.css` 中修改按钮样式：

**`.primary-button`：**
- `background` 改为 `var(--gradient-brand)`
- 添加 `box-shadow: var(--shadow-button)`
- `border-radius` 改为 `var(--radius-button)`
- hover 态：`background` 改为 `var(--brand-700)`（取消渐变，纯色加深），`box-shadow: var(--shadow-button-hover)`，添加 `transform: translateY(-1px)`
- active 态：`transform: scale(0.98)`，`box-shadow: var(--shadow-xs)`
- focus-visible：`box-shadow: var(--shadow-focus-ring)`
- 添加 `transition: var(--transition-all)`

**`.secondary-button`：**
- `border-color` 改为 `var(--brand-400)`
- `color` 改为 `var(--brand-700)`
- hover：`background: var(--brand-50)`，`border-color: var(--brand-500)`

**`.icon-button`：**
- `border-radius` 改为 `var(--radius-sm)`
- hover：`border-color: var(--brand-400)`，`color: var(--brand-600)`，`background: var(--brand-50)`

**`.text-button`：**
- hover：`color: var(--brand-600)`
- 添加 `transition: color var(--duration-fast) var(--ease-default)`

**`.danger-button`：**
- hover：`background: var(--danger-700, #921d26)`

### 任务 3.2：卡片升级

**`.post-card`：**
- `border-radius` 改为 `var(--radius-card)`
- `border-color` 改为 `var(--border-subtle)`
- 添加 `transition: var(--transition-all)`
- hover：`border-color: var(--border-strong)`，`box-shadow: var(--shadow-card-hover)`，`transform: translateY(-2px)`

**`.empty-feed`：**
- `border` 改为 `1px dashed var(--border-strong)`
- `border-radius` 改为 `var(--radius-md)`
- `background` 改为 `var(--gradient-surface)`

### 任务 3.3：输入框升级

所有 `input`, `textarea`, `select`：
- `border-radius` 改为 `var(--radius-input)`
- focus：`border-color: var(--brand-500)`，`box-shadow: var(--shadow-focus-input)`
- 添加 `transition: border-color var(--duration-fast) var(--ease-default), box-shadow var(--duration-fast) var(--ease-default)`

### 任务 3.4：分段控制器升级

**`.segmented-control`：**
- `border-radius` 改为 `var(--radius-sm)`
- active 按钮：`background: var(--brand-100)`，`color: var(--brand-700)`，`border-radius: 4px`
- 添加 `transition: background var(--duration-fast) var(--ease-bounce)` 给 active 按钮

### 任务 3.5：弹窗升级

**`.confirm-dialog`：**
- `border-radius` 改为 `var(--radius-dialog)`
- `box-shadow` 改为 `var(--shadow-dialog)`
- 遮罩：添加 `backdrop-filter: blur(4px)`

**`.confirm-backdrop`：**
- `background` 改为 `var(--bg-overlay)`
- 添加 `backdrop-filter: blur(4px)`

### 任务 3.6：对话气泡升级

**`.assistant-message--user`：**
- `background` 改为 `var(--brand-50)`
- `border-color` 改为 `var(--brand-200)`
- `border-radius` 改为 `var(--radius-lg) var(--radius-lg) 4px var(--radius-lg)`

**`.assistant-message--assistant`：**
- `border-radius` 改为 `var(--radius-lg) var(--radius-lg) var(--radius-lg) 4px`

### 验收标准

- [ ] 按钮有渐变底、hover 上浮、点击缩放效果
- [ ] 卡片 hover 有明显上浮 + 阴影加深
- [ ] 输入框 focus 有绿色光晕
- [ ] 分段控制器切换有弹性动画
- [ ] 弹窗遮罩有毛玻璃效果
- [ ] 对话气泡有差异化圆角
- [ ] 所有过渡动画流畅，无跳变

---

## 阶段 4：导航与布局升级（P1）

### 任务 4.1：顶栏升级

**`.topbar`：**
- `border-bottom-color` 改为 `var(--border-subtle)`
- 添加 `position: sticky; top: 0; z-index: var(--z-sticky)`

**`.brand-mark`：**
- `background` 改为 `var(--gradient-brand)`
- `border-radius` 改为 `8px`

### 任务 4.2：侧栏升级

**`.sidebar`：**
- `background` 改为 `var(--bg-subtle)`
- `border-right-color` 改为 `var(--border-subtle)`

**`.nav-item`：**
- `border-radius` 改为 `var(--radius-sm)`
- active 态：`background: var(--brand-50)`，`color: var(--brand-700)`
- 添加左侧指示线：active 时 `box-shadow: inset 3px 0 var(--brand-500)` 或 `border-left: 3px solid var(--brand-500)`（注意 padding 调整）
- 添加 `transition: var(--transition-color)`

### 任务 4.3：主内容区升级

**`.main-content`：**
- `background` 设为 `var(--bg-page)`（确保页面底色正确）

### 验收标准

- [ ] 顶栏滚动时固定在顶部
- [ ] Brand mark 有渐变效果
- [ ] 侧栏导航 active 项有品牌色背景 + 左侧竖线
- [ ] 导航项 hover 有平滑过渡

---

## 阶段 5：细节打磨（P2）

### 任务 5.1：评论区升级

**`.comment-item--focused`：**
- `background` 改为 `var(--brand-50)`
- `box-shadow: inset 3px 0 var(--brand-500)`

**`.school-tag`：**
- 颜色全部用 token 变量

**`.author-badge`：**
- 颜色用 token 变量

### 任务 5.2：个人主页升级

**`.profile-avatar-large`：**
- hover 时添加 `cursor: zoom-in`（已有 button 变体）
- focus-visible ring 用 `var(--shadow-focus-ring)`

### 任务 5.3：通知升级

**`.notice-item.unread`：**
- `border-left-color` 改为 `var(--brand-500)`

### 任务 5.4：空状态升级

添加进场动画：
```css
.empty-feed, .assistant-empty-state {
  animation: fadeInUp var(--duration-slow) var(--ease-out);
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
```

### 任务 5.5：页面进场动画

为主要页面容器添加进场动画：

```css
.content-page {
  animation: fadeIn var(--duration-normal) var(--ease-default);
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
```

### 任务 5.6：流式输出光标

在助手对话流式输出时，末尾显示闪烁光标：

```css
.assistant-message--assistant p:last-child::after {
  content: '▍';
  color: var(--brand-500);
  animation: blink 1s step-end infinite;
}

/* 仅在 streaming 状态时显示 — 需要通过 JS 添加 class */
.assistant-message--streaming p:last-child::after {
  content: '▍';
  /* ... */
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}
```

> 注意：需要 Vue 组件配合，给 streaming 状态的消息加 `assistant-message--streaming` class。如果不想改组件逻辑，可以跳过此项。

### 验收标准

- [ ] 评论区聚焦态有品牌色左边线
- [ ] 空状态有进场上浮动画
- [ ] 页面切换有淡入效果
- [ ] 所有过渡/动画流畅无卡顿

---

## 阶段 6：清理与验证（P3）

### 任务 6.1：删除废弃样式

检查 `index.css` 中是否有不再使用的 class，清理死代码。

### 任务 6.2：响应式验证

在以下断点验证所有页面：
- 375px（iPhone SE）
- 480px（手机横屏）
- 720px（平板竖屏）
- 1024px（平板横屏）
- 1280px（桌面）
- 1536px（大屏）

重点关注：
- 移动端导航抽屉
- 卡片单列堆叠
- 按钮全宽
- 分段控制器撑满

### 任务 6.3：跨浏览器验证

- Chrome/Edge（主要）
- Firefox
- Safari（如果可用）

### 任务 6.4：性能检查

- 确认没有引入额外的网络请求
- 确认 CSS 文件体积增长合理（tokens + base 预计增加 3-5KB gzip）
- 动画使用 `transform` 和 `opacity`（GPU 加速），避免触发 layout

### 验收标准

- [ ] 无死代码
- [ ] 所有断点下布局正确
- [ ] 主流浏览器显示一致
- [ ] 无性能退化

---

## 执行顺序总结

```
阶段 1（30 min）  → tokens.css + base.css + 入口引入
阶段 2（60 min）  → 全局硬编码替换（最机械、最耗时）
阶段 3（45 min）  → 组件样式升级（按钮/卡片/输入框/弹窗/气泡）
阶段 4（20 min）  → 导航与布局升级
阶段 5（30 min）  → 细节打磨与动画
阶段 6（20 min）  → 清理与验证
─────────────────────────────────
总计约 3.5 小时
```

---

## 注意事项

1. **不要一次改完所有文件** — 按阶段提交，每阶段一个 git commit
2. **每阶段完成后 `npm run dev` 验证** — 确保无白屏、无样式丢失
3. **如果遇到 token 不够用** — 新增 token 到 `tokens.css`，不要回退到硬编码
4. **不要改 HTML 结构** — 这是纯样式升级，不改组件逻辑和模板
5. **保留 `index.css` 的选择器结构** — 只替换属性值，不改选择器
6. **渐变遮罩的 rgba 值** — 部分特殊遮罩（头像上传、图片预览）保留硬编码，不强行替换
