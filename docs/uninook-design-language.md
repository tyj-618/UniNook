# UniNook Design Language — 清新校园风

> 版本 1.0 | 2026-08-14
> 目标：在现有青绿基因上升级品牌感、层次感、微交互，让 UniNook 从"能用的工具"变成"想用的产品"。

---

## 一、Design Tokens

所有 token 以 CSS 自定义属性（`var(--xxx)`）定义在 `:root`，方便全局引用和后续扩展暗色模式。

### 1.1 色彩体系

#### 品牌色（Brand）

```css
/* 品牌主色 — 青绿，用于 CTA、活跃态、品牌标识 */
--brand-50:   #edf8f4;   /* 最浅底，hover 背景、标签底 */
--brand-100:  #d4f0e6;   /* 选中态背景、segmented active */
--brand-200:  #a8e0cd;   /* 装饰线、分隔线高亮 */
--brand-300:  #6dc9a8;   /* 图标强调 */
--brand-400:  #2eaa82;   /* 按钮 hover 起始 */
--brand-500:  #0e9e74;   /* 品牌主色（替代原 #0e7667，略提亮） */
--brand-600:  #0b8260;   /* 按钮默认态 */
--brand-700:  #08664b;   /* 按钮 active/pressed */
--brand-800:  #064a37;   /* 深色文字强调 */
--brand-900:  #042f23;   /* 极少用，仅极深强调 */
```

#### 语义色（Semantic）

```css
/* 成功 */
--success-bg:     #edf8f1;
--success-border: #9bcbb3;
--success-text:   #19734c;
--success-icon:   #22915f;

/* 警告 */
--warning-bg:     #fffcf3;
--warning-border: #e3cf95;
--warning-text:   #81691b;
--warning-icon:   #a68520;

/* 错误/危险 */
--danger-bg:      #fff3f3;
--danger-border:  #e7c5c5;
--danger-text:    #b42318;
--danger-icon:    #d03027;

/* 信息/提示 */
--info-bg:        #f0f6ff;
--info-border:    #b3d0f7;
--info-text:      #1a5fa1;
--info-icon:      #2874c0;
```

#### 中性色（Neutral）

```css
/* 文字 */
--text-primary:    #1a2e26;   /* 标题、正文 */
--text-secondary:  #4a5e54;   /* 次要说明 */
--text-tertiary:   #6d8178;   /* 辅助信息、placeholder */
--text-disabled:   #9aaba3;   /* 禁用态 */
--text-inverse:    #ffffff;   /* 深色底上的白字 */

/* 背景 */
--bg-page:         #f5f9f7;   /* 页面底色（比原来略偏绿） */
--bg-surface:      #ffffff;   /* 卡片、面板 */
--bg-subtle:       #f8fbf9;   /* 次级区域（侧栏、编辑器底） */
--bg-muted:        #edf3ef;   /* 分组底色、空状态 */
--bg-overlay:      rgba(15, 31, 25, 0.42);  /* 弹窗遮罩 */

/* 边框 */
--border-subtle:   #e4ebe7;   /* 卡片、列表项 */
--border-default:  #d0dbd5;   /* 输入框、分隔线 */
--border-strong:   #a8bdb1;   /* hover 态边框 */
--border-brand:    var(--brand-500);

/* 头像底色 */
--avatar-bg:       #dff0e9;
--avatar-text:     var(--brand-600);
```

#### 渐变（Gradient）

```css
/* 品牌渐变 — 用于 CTA 按钮、品牌区域 */
--gradient-brand:  linear-gradient(135deg, var(--brand-500) 0%, var(--brand-400) 100%);

/* 卡片渐变底色 — 用于空状态、引导区域 */
--gradient-surface: linear-gradient(180deg, var(--bg-subtle) 0%, var(--bg-page) 100%);

/* 校园标签渐变 — 用于学校标签的背景 */
--gradient-campus: linear-gradient(90deg, var(--brand-50) 0%, transparent 60%);
```

### 1.2 字体排版

```css
/* 字体族 */
--font-sans:  'Inter', 'PingFang SC', 'Microsoft YaHei', system-ui, -apple-system, sans-serif;
--font-mono:  'JetBrains Mono', 'Fira Code', 'Cascadia Code', ui-monospace, monospace;

/* 字号阶梯（基于 1rem = 16px） */
--text-xs:    0.75rem;     /* 12px — 标签、时间戳、badge */
--text-sm:    0.8125rem;   /* 13px — 辅助文字、meta */
--text-base:  0.875rem;    /* 14px — 正文默认 */
--text-md:    1rem;        /* 16px — 大段正文 */
--text-lg:    1.125rem;    /* 18px — 卡片标题 */
--text-xl:    1.25rem;     /* 20px — 区域标题 h2 */
--text-2xl:   1.5rem;      /* 24px — 页面标题 h1（移动端） */
--text-3xl:   1.875rem;    /* 30px — 页面标题 h1（桌面端） */

/* 字重 */
--font-normal:    400;
--font-medium:    500;
--font-semibold:  600;
--font-bold:      700;
--font-extrabold: 800;

/* 行高 */
--leading-tight:   1.25;   /* 标题 */
--leading-normal:  1.5;    /* 单行文本 */
--leading-relaxed: 1.7;    /* 正文段落 */
--leading-loose:   1.85;   /* 长文阅读 */

/* 字间距 */
--tracking-tight:  -0.01em;
--tracking-normal:  0;
--tracking-wide:    0.02em;
--tracking-eyebrow: 0.06em;  /* eyebrow 大写标签专用 */
```

### 1.3 间距系统

基于 4px 基础单元，使用 T-shirt 尺寸命名：

```css
--space-0:   0;
--space-1:   0.25rem;   /* 4px */
--space-2:   0.5rem;    /* 8px */
--space-3:   0.75rem;   /* 12px */
--space-4:   1rem;      /* 16px */
--space-5:   1.25rem;   /* 20px */
--space-6:   1.5rem;    /* 24px */
--space-8:   2rem;      /* 32px */
--space-10:  2.5rem;    /* 40px */
--space-12:  3rem;      /* 48px */
--space-16:  4rem;      /* 64px */

/* 语义间距 */
--gap-xs:    var(--space-2);    /* 8px — 紧凑元素间 */
--gap-sm:    var(--space-3);    /* 12px — 表单元素间 */
--gap-md:    var(--space-4);    /* 16px — 列表项间 */
--gap-lg:    var(--space-6);    /* 24px — 区块间 */
--gap-xl:    var(--space-8);    /* 32px — 页面区块间 */

/* 内边距 */
--padding-input:   0.5625rem 0.75rem;  /* 9px 12px — 输入框 */
--padding-button:  0.5625rem 1rem;     /* 9px 16px — 按钮 */
--padding-card:    var(--space-5);      /* 20px — 卡片 */
--padding-panel:   var(--space-6);      /* 24px — 面板 */
--padding-page:    var(--space-8);      /* 32px — 页面边距（桌面） */
```

### 1.4 圆角

```css
--radius-sm:    6px;     /* 按钮、输入框、小标签 */
--radius-md:    10px;    /* 卡片、弹窗 */
--radius-lg:    14px;    /* 大面板、对话气泡 */
--radius-xl:    20px;    /* 品牌区域、特殊卡片 */
--radius-full:  9999px;  /* 头像、pill 标签、badge */

/* 语义圆角 */
--radius-button:  var(--radius-sm);
--radius-input:   var(--radius-sm);
--radius-card:    var(--radius-md);
--radius-dialog:  var(--radius-md);
--radius-avatar:  var(--radius-full);
--radius-badge:   var(--radius-full);
--radius-pill:    var(--radius-full);
```

### 1.5 阴影

```css
/* 阴影层级 */
--shadow-xs:   0 1px 2px rgba(15, 31, 25, 0.04);
--shadow-sm:   0 2px 8px rgba(15, 31, 25, 0.06);
--shadow-md:   0 4px 16px rgba(15, 31, 25, 0.08);
--shadow-lg:   0 8px 24px rgba(15, 31, 25, 0.10);
--shadow-xl:   0 16px 48px rgba(15, 31, 25, 0.14);

/* 语义阴影 */
--shadow-card:         var(--shadow-sm);
--shadow-card-hover:   var(--shadow-md);
--shadow-dialog:       var(--shadow-xl);
--shadow-button:       0 2px 6px rgba(14, 158, 116, 0.18);
--shadow-button-hover: 0 4px 12px rgba(14, 158, 116, 0.24);
--shadow-focus-ring:   0 0 0 3px rgba(14, 158, 116, 0.18);

/* 内阴影 — 用于 focused 输入框 */
--shadow-focus-input:  0 0 0 3px rgba(14, 158, 116, 0.12);
```

### 1.6 动效

```css
/* 过渡时长 */
--duration-instant:  100ms;
--duration-fast:     150ms;
--duration-normal:   200ms;
--duration-slow:     300ms;
--duration-slower:   500ms;

/* 缓动曲线 */
--ease-default:   cubic-bezier(0.4, 0, 0.2, 1);
--ease-in:        cubic-bezier(0.4, 0, 1, 1);
--ease-out:       cubic-bezier(0, 0, 0.2, 1);
--ease-bounce:    cubic-bezier(0.34, 1.56, 0.64, 1);

/* 语义过渡 */
--transition-color:     color var(--duration-fast) var(--ease-default),
                        background-color var(--duration-fast) var(--ease-default),
                        border-color var(--duration-fast) var(--ease-default);
--transition-shadow:    box-shadow var(--duration-normal) var(--ease-default);
--transition-transform: transform var(--duration-normal) var(--ease-default);
--transition-opacity:   opacity var(--duration-normal) var(--ease-default);
--transition-all:       var(--transition-color), var(--transition-shadow), var(--transition-transform);
```

### 1.7 层级（Z-Index）

```css
--z-base:      0;
--z-dropdown:  10;
--z-sticky:    20;
--z-overlay:   40;
--z-modal:     50;
--z-toast:     60;
--z-tooltip:   70;
```

### 1.8 断点

```css
--bp-sm:   480px;    /* 手机横屏 */
--bp-md:   720px;    /* 平板竖屏 / 现有移动端点 */
--bp-lg:   1024px;   /* 平板横屏 / 小笔记本 */
--bp-xl:   1280px;   /* 桌面 */
--bp-2xl:  1536px;   /* 大屏 */
```

---

## 二、组件规范

### 2.1 按钮（Button）

#### 变体

| 变体 | 用途 | 样式 |
|------|------|------|
| `primary` | 主操作（登录、发布、确认） | 品牌渐变底 + 白字 + shadow-button |
| `secondary` | 次操作（取消、返回） | 白底 + brand 边框 + brand 文字 |
| `ghost` | 弱操作（清空、辅助） | 透明底 + 次要文字色，hover 显底色 |
| `danger` | 危险操作（删除） | danger-text + danger-border，hover 变 danger-bg |
| `icon` | 图标按钮（刷新、菜单） | 34×34 正方形，圆角 sm |

#### 尺寸

| 尺寸 | 高度 | 内边距 | 字号 | 用途 |
|------|------|--------|------|------|
| `sm` | 32px | 4px 12px | 13px | 列表内操作、评论 |
| `md` | 38px | 7px 16px | 13px | 默认（工具栏、表单） |
| `lg` | 44px | 10px 20px | 14px | 页面主操作、登录 |

#### 交互状态

```
默认态 → hover（背景加深 + shadow 增强 + translateY(-1px)）
       → active（scale(0.98) + shadow 收缩）
       → focus-visible（shadow-focus-ring）
       → disabled（opacity: 0.55, cursor: not-allowed）
```

#### 规则

- 按钮内图标与文字间距 `var(--space-2)`
- 主按钮最多一个页面出现 1-2 次（视觉焦点）
- 按钮组间距 `var(--space-3)`
- 全宽按钮仅用于移动端和登录表单

### 2.2 卡片（Card）

#### 基础卡片（Post Card）

```
结构：
┌─────────────────────────────────┐
│ [meta] 学校 · 分类 · 时间        │  ← text-xs, text-tertiary
│ [avatar] 昵称                    │  ← text-sm, text-secondary
│                                 │
│ 标题                            │  ← text-lg, font-bold, text-primary
│ 摘要文字（最多 2 行截断）         │  ← text-base, text-secondary
│                                 │
│ 👁 128  ❤ 32  💬 8              │  ← text-xs, text-tertiary, icon 16px
└─────────────────────────────────┘
```

- 背景：`var(--bg-surface)`
- 边框：`1px solid var(--border-subtle)`
- 圆角：`var(--radius-card)` = 10px
- 内边距：`var(--padding-card)` = 20px
- 卡片间距：`var(--gap-md)` = 16px

#### 交互

```
默认：border = border-subtle, shadow = none
hover：border = border-strong, shadow = shadow-card-hover, translateY(-2px)
active：shadow 收缩回 shadow-sm
```

#### 变体

| 变体 | 差异 | 用途 |
|------|------|------|
| `card-default` | 上述基础样式 | 帖子列表 |
| `card-interactive` | hover 更明显 + cursor pointer | 可点击卡片 |
| `card-highlighted` | 左边框 3px brand-500 | 当前选中/聚焦 |
| `card-empty` | 虚线边框 + 居中内容 + 渐变底 | 空状态 |

### 2.3 输入框（Input）

#### 基础样式

```css
height:          42px;
padding:         var(--padding-input);
border:          1px solid var(--border-default);
border-radius:   var(--radius-input);
background:      var(--bg-surface);
color:           var(--text-primary);
font-size:       var(--text-base);
transition:      border-color, box-shadow;
```

#### 状态

| 状态 | 边框色 | 额外 |
|------|--------|------|
| 默认 | `border-default` | — |
| hover | `border-strong` | — |
| focus | `border-brand` | `shadow-focus-input` |
| error | `danger-border` | `shadow: 0 0 0 3px rgba(180,35,24,0.1)` |
| disabled | `border-subtle` | `opacity: 0.55; bg: var(--bg-muted)` |

#### Textarea

- 最小高度 96px
- 可垂直 resize
- 行高 `var(--leading-relaxed)` = 1.7
- 其余同 input

#### Select

- 同 input 基础样式
- 右侧展开箭头图标（18px）
- padding-right 加 8px 给箭头留位

#### 标签（Label）

```css
font-size:    var(--text-base);   /* 14px */
font-weight:  var(--font-semibold); /* 600 */
color:        var(--text-secondary);
gap:          var(--space-2);     /* label 与 input 间距 */
```

#### 辅助文字（Helper Text）

```css
font-size:    var(--text-xs);
color:        var(--text-tertiary);
line-height:  var(--leading-normal);
```

#### 错误文字

```css
font-size:    13px;
color:        var(--danger-text);
display:      flex;
align-items:  center;
gap:          6px;
/* 前面放 CircleAlert 图标 16px */
```

### 2.4 导航（Navigation）

#### 顶部导航栏（Topbar）

```
高度：64px
背景：var(--bg-surface)
底部边框：1px solid var(--border-subtle)
内边距：0 var(--space-6)（桌面）/ 0 var(--space-4)（移动）
```

结构：
```
┌──────────────────────────────────────────────────┐
│ [U] UniNook          [导航项...]   [🔔] [头像] [退出] │
└──────────────────────────────────────────────────┘
```

- Brand 区域：logo mark（30×30 圆角 7px 品牌色底白字）+ 文字（font-extrabold 20px）
- 用户头像：30×30 圆形，品牌底色 + 首字母 / 头像图
- 图标按钮：34×34，hover 显 brand 色

#### 侧边导航（Sidebar）

```
宽度：224px
背景：var(--bg-subtle)
右边框：1px solid var(--border-subtle)
内边距：20px 12px
```

导航项：
```
┌────────────────────────┐
│ 🧭 校园动态             │  ← 42px 高, 12px 左右 padding
│ ✏️ 发布讨论             │
│ 🤖 校园助手             │  ← active: bg brand-50, text brand-700
│ 📋 问题追踪             │     左边出现 3px brand-500 竖线
│ 🔔 通知            [3] │
│ 👤 我的主页             │
└────────────────────────┘
```

- 导航项高度 42px，圆角 sm
- 图标 19px + 文字 14px font-semibold，间距 11px
- 默认色：`text-tertiary`
- hover：`bg-muted`
- active：`bg brand-50` + `text brand-700` + 左侧 3px 竖线
- Badge：最小 18×18 圆形，danger 底色白字，11px font-bold

#### 移动端导航

- 汉堡菜单触发侧滑面板（280px 宽或 86vw）
- 遮罩层 `var(--bg-overlay)`
- 从左侧滑入，`duration-slow` + `ease-out`

### 2.5 头像（Avatar）

| 尺寸 | 场景 | 字号 |
|------|------|------|
| 26px | 帖子卡片内作者 | 11px |
| 30px | 顶栏用户 | 13px |
| 32px | 评论区 | 13px |
| 58px | 移动端个人主页 | 21px |
| 84px | 桌面端个人主页 | 31px |

- 形状：圆形（`radius-full`）
- 底色：`var(--avatar-bg)` + `var(--avatar-text)`
- 首字母大写居中
- 图片模式：`object-fit: cover`
- hover：`outline: 2px solid var(--brand-300); outline-offset: 2px`

### 2.6 标签与徽章（Tag & Badge）

#### 学校标签（School Tag）

```css
padding:       2px 8px;
border:        1px solid var(--brand-200);
border-radius: var(--radius-pill);
background:    var(--brand-50);
color:         var(--brand-700);
font-size:     var(--text-xs);
font-weight:   var(--font-bold);
```

#### 通知 Badge

```css
min-width:     18px;
height:        18px;
padding:       0 5px;
border-radius: var(--radius-badge);
background:    var(--danger-icon);
color:         white;
font-size:     11px;
font-weight:   var(--font-bold);
line-height:   18px;
text-align:    center;
```

#### 状态标签（Status Pill）

| 状态 | 边框 | 背景 | 文字 |
|------|------|------|------|
| 进行中 | `warning-border` | `warning-bg` | `warning-text` |
| 已完成 | `success-border` | `success-bg` | `success-text` |
| 已关闭 | `border-default` | `bg-muted` | `text-tertiary` |

#### Eyebrow（大写引导标签）

```css
color:         var(--brand-600);
font-size:     var(--text-xs);
font-weight:   var(--font-extrabold);
letter-spacing: var(--tracking-eyebrow);
text-transform: uppercase;
```

### 2.7 分段控制器（Segmented Control）

```
结构：
┌──────────────────────────────────────────┐
│  同校区  |  同校  | [10 km] | 20 km | 同市  │
└──────────────────────────────────────────┘
```

- 外框：`1px solid var(--border-default)`, `radius-sm`
- 内边距：3px
- 选项高度：32px，padding 4px 12px
- 默认：透明底，`text-tertiary`，font-bold 13px
- Active：`bg brand-100`，`text brand-700`，`radius-4px`
- Hover（非 active）：`bg muted`
- 切换动画：active 背景块 `transition: all var(--duration-fast)`

### 2.8 弹窗与确认框（Dialog）

#### 遮罩

```css
background:    var(--bg-overlay);
backdrop-filter: blur(4px);   /* 毛玻璃效果 */
```

#### 对话框

```
宽度：min(100%, 440px)
内边距：28px
背景：var(--bg-surface)
边框：1px solid var(--border-subtle)
圆角：var(--radius-dialog) = 10px
阴影：var(--shadow-dialog)
```

结构：
```
┌──────────────────────────────┐
│ 标题（text-xl font-bold）     │
│                              │
│ 说明文字（text-base text-     │
│ secondary line-height 1.65） │
│                              │
│              [取消]  [确认]   │
└──────────────────────────────┘
```

- 按钮区右对齐，间距 16px
- 危险确认用 danger 按钮
- 进场动画：`scale(0.95) opacity(0)` → `scale(1) opacity(1)`，`duration-normal ease-out`

### 2.9 评论组件（Comment）

#### 评论项

```
结构：
┌───┬────────────────────────────────┐
│ A │  昵称        学校标签  3h ago   │
│ v │                                │
│ a │  评论内容正文                   │
│ t │                                │
│ a │  [👍 12]  [💬 回复]  [⋯]       │
│ r │                                │
└───┴────────────────────────────────┘
```

- 头像 32px
- 评论间距：`padding: 16px 0`，底部 `border-bottom: 1px solid var(--border-subtle)`
- 作者名字：`font-bold text-secondary`
- 内容：`text-base text-primary line-height 1.65`
- 操作区：`text-xs text-tertiary`，hover 变 brand

#### 回复（嵌套）

- `margin-left: 42px`
- `padding-left: 12px`
- `border-left: 2px solid var(--brand-200)`
- 移动端缩为 `18px`

#### 聚焦态

- `background: var(--brand-50)`
- `box-shadow: inset 3px 0 var(--brand-500)`
- `border-radius: var(--radius-sm)`

#### 作者标识

- 评论来自帖子作者时，显示 `author-badge`
- `bg brand-50 text brand-700 20px 高 4px 6px padding radius-4px 11px font-bold`

### 2.10 对话气泡（Assistant Chat）

#### 用户消息

```css
align-self:     flex-end;
background:     var(--brand-50);
border:         1px solid var(--brand-200);
border-radius:  var(--radius-lg) var(--radius-lg) 4px var(--radius-lg);  /* 右下角小圆角 */
padding:        16px 18px;
max-width:      680px;
```

#### 助手消息

```css
align-self:     flex-start;
background:     var(--bg-surface);
border:         1px solid var(--border-subtle);
border-radius:  var(--radius-lg) var(--radius-lg) var(--radius-lg) 4px;  /* 左下角小圆角 */
padding:        16px 18px;
max-width:      680px;
```

#### Meta 信息

```css
font-size:     var(--text-sm);
font-weight:   var(--font-bold);
color:         var(--text-tertiary);
margin-bottom: 6px;
```

#### 对话容器

- `max-height: min(60vh, 640px)`
- `overflow-y: auto`
- `scrollbar-gutter: stable`
- 自定义滚动条：宽 6px，圆角，`bg border-default`，hover `bg border-strong`

### 2.11 空状态（Empty State）

```
结构：
┌─────────────────────────────┐
│         🗺️ (icon 24px)      │
│                             │
│    这个范围内还没有动态       │  ← text-lg font-bold text-primary
│                             │
│ 换一个距离范围，或成为第一    │  ← text-base text-tertiary
│ 个发起讨论的人。             │
│                             │
│       [发布讨论]             │  ← primary-button
└─────────────────────────────┘
```

- 边框：`1px dashed var(--border-strong)`
- 圆角：`var(--radius-md)`
- 背景：`var(--gradient-surface)`
- 内边距：`44px 28px`
- 文字居中
- 图标色：`var(--brand-500)`

### 2.12 分割线（Divider）

```css
/* 普通分割 */
border-bottom: 1px solid var(--border-subtle);

/* 强调分割（区块之间） */
border-bottom: 1px solid var(--border-default);

/* 虚线分割（可操作区域边界） */
border-bottom: 1px dashed var(--border-strong);
```

### 2.13 滚动条（Scrollbar）

```css
/* 全局自定义 */
scrollbar-width: thin;
scrollbar-color: var(--border-default) transparent;

/* Webkit */
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb {
  background: var(--border-default);
  border-radius: 3px;
}
::-webkit-scrollbar-thumb:hover {
  background: var(--border-strong);
}
```

---

## 三、页面局部模板

### 3.1 登录/注册页

```
┌────────────────────────────────────────────────────────┐
│                    bg: var(--bg-page)                   │
│                                                        │
│         ┌──────────────────────────────┐               │
│         │  [U] UniNook                 │               │
│         │                              │               │
│         │  CAMPUS COMMUNITY            │  ← eyebrow    │
│         │  回到你的校园圈              │  ← h1         │
│         │  查看附近高校正在发生的讨论   │  ← muted      │
│         │                              │               │
│         │  用户名                      │               │
│         │  ┌──────────────────────┐    │               │
│         │  │                      │    │               │
│         │  └──────────────────────┘    │               │
│         │  密码                        │               │
│         │  ┌──────────────────────┐    │               │
│         │  │                      │    │               │
│         │  └──────────────────────┘    │               │
│         │                              │               │
│         │  ┌──────────────────────┐    │               │
│         │  │     登录 →           │    │  ← primary lg │
│         │  └──────────────────────┘    │     全宽       │
│         │                              │               │
│         │  还没有账号？创建账号         │  ← center     │
│         └──────────────────────────────┘               │
│                                                        │
│         面板：430px 宽，shadow-lg，radius-md            │
└────────────────────────────────────────────────────────┘
```

**升级点：**
- 面板进场动画：`translateY(12px) opacity(0)` → `translateY(0) opacity(1)`，`duration-slow ease-out`
- Brand mark 升级：加微渐变 `var(--gradient-brand)`
- 背景加极淡的装饰元素（可选）：右上角一个大圆 `bg brand-50 blur(80px)` 制造光晕

### 3.2 Feed 页（校园动态）

```
┌────────────────────────────────────────────────────────────┐
│ Topbar                                                     │
├──────────┬─────────────────────────────────────────────────┤
│ Sidebar  │  NEARBY CAMPUS FEED                             │
│          │  校园动态                                       │
│          │  按距离查看你所在学校及附近高校的讨论。           │
│          │                                                 │
│          │  ┌─────────────────────┐ ┌──────────────────┐   │
│          │  │ 同校区|同校|10km|…  │ │  最新 | 热门     │   │
│          │  └─────────────────────┘ └──────────────────┘   │
│          │                                                 │
│          │  ┌─────────────────────────────────────────┐    │
│          │  │ 南京大学 · 鼓楼校区 · 学术讨论 · 3h ago  │    │
│          │  │ [A] 张三                                 │    │
│          │  │                                         │    │
│          │  │ 标题文字                                │    │
│          │  │ 摘要文字最多两行…                       │    │
│          │  │                                         │    │
│          │  │ 👁 128   ❤ 32   💬 8                    │    │
│          │  └─────────────────────────────────────────┘    │
│          │                                                 │
│          │  ┌─────────────────────────────────────────┐    │
│          │  │ ...下一张卡片                           │    │
│          │  └─────────────────────────────────────────┘    │
└──────────┴─────────────────────────────────────────────────┘
```

**升级点：**
- 卡片 hover 时 `translateY(-2px)` + shadow 增强
- Segmented control 切换时 active 块有 `ease-bounce` 微弹动画
- 页面标题区 eyebrow + h1 + muted 三段式，层次分明
- 主内容区最大宽度 960px 居中

### 3.3 帖子详情页

```
┌────────────────────────────────────────────────────────────┐
│ ← 返回校园动态                    [点赞] [分享]            │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌─────────────────────────────┬──────────────────────┐    │
│  │ 帖子正文区                   │  问题追踪面板        │    │
│  │                             │                      │    │
│  │  [A] 作者名 · 3h ago        │  ┌──────────────┐    │    │
│  │                             │  │ 问题追踪      │    │    │
│  │  南京大学 · 鼓楼校区         │  │ 状态：进行中  │    │    │
│  │                             │  │ 摘要文字...   │    │    │
│  │  标题（text-3xl font-bold） │  │ [编辑] [删除] │    │    │
│  │                             │  └──────────────┘    │    │
│  │  正文内容                    │                      │    │
│  │  line-height: 1.85          │  ┌──────────────┐    │    │
│  │  white-space: pre-wrap      │  │ 回答列表      │    │    │
│  │                             │  │              │    │    │
│  │                             │  │ ✅ 已采纳回答 │    │    │
│  │  ─────────────────────      │  │ 回答内容...   │    │    │
│  │  [👍 32]                    │  │              │    │    │
│  │                             │  │ 📝 新回答     │    │    │
│  └─────────────────────────────┴──────────────────────┘    │
│                                                            │
│  ──────────────────────────────────────────────────────    │
│  评论区                                                    │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 写评论...                            [发送]          │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                            │
│  ┌───┬─────────────────────────────────────────────────┐   │
│  │ A │ 昵称    学校标签    3h ago                      │   │
│  │   │ 评论内容...                                     │   │
│  │   │ [👍 12]  [💬 回复]                              │   │
│  └───┴─────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

**布局规则：**
- 桌面：正文区 `1.08fr` + 侧面板 `0.92fr minmax(300px)`
- 移动：单列堆叠
- 帖子正文 `text-md line-height 1.85`（舒适阅读）
- 评论区与正文间 `padding-top: 28px` + 分割线

### 3.4 校园助手页

```
┌────────────────────────────────────────────────────────────┐
│  CAMPUS ASSISTANT                                          │
│  校园助手                                                  │
│  仅检索你当前附近校园范围内的公开帖子…      [清空会话]     │
├────────────────────────────────────────────────────────────┤
│                                                            │
│  ┌──────────────────────────────────────┐                  │
│  │ 你 · 10 km                    [用户] │ ← 右对齐气泡    │
│  └──────────────────────────────────────┘                  │
│                                                            │
│  ┌──────────────────────────────────────┐                  │
│  │ 校园助手                             │ ← 左对齐气泡    │
│  │                                      │                  │
│  │ 根据检索结果，附近适合…              │                  │
│  │                                      │                  │
│  │ 参考帖子                             │                  │
│  │ ─────────────────────                │                  │
│  │ 帖子标题                             │                  │
│  │ 南京大学 ·  excerpt...              │                  │
│  └──────────────────────────────────────┘                  │
│                                                            │
├────────────────────────────────────────────────────────────┤
│  查看范围                  你的问题                        │
│  ┌──────────┐  ┌──────────────────────────────┐           │
│  │ 10 km ▾  │  │                              │           │
│  └──────────┘  │  例如：附近有哪些适合…       │           │
│                │                              │           │
│                └──────────────────────────────┘           │
│  Enter 发送，Shift + Enter 换行                           │
│                                            [停止] [发送]  │
└────────────────────────────────────────────────────────────┘
```

**升级点：**
- 对话气泡圆角差异化（用户右下小、助手左下小），更有对话感
- 助手气泡内 `参考帖子` 区域用分割线 + 链接列表
- 底部 composer 用 `shadow-sm` 浮起，与对话区视觉分离
- 流式输出时末尾显示闪烁光标（`|` 字符 + `animation: blink 1s step-end infinite`）

### 3.5 个人主页

```
┌────────────────────────────────────────────────────────────┐
│                                                            │
│  ┌────┐                                                    │
│  │ 84 │  昵称                                  [设置]     │
│  │ px │  学号 · 邮箱                                       │
│  └────┘  南京大学 · 鼓楼校区                               │
│          个人简介文字…                                     │
│          📅 2024 年加入                                    │
│                                                            │
│  ┌──────────┬──────────┬──────────┐                        │
│  │  📝 帖子  │  💬 评论  │  ❤ 获赞  │                       │
│  │   12     │   48     │   156    │                        │
│  └──────────┴──────────┴──────────┘                        │
│                                                            │
│  [帖子]  [评论]  [回答]    ← tabs                         │
│                                                            │
│  ┌─────────────────────────────────────────────────────┐   │
│  │ 帖子标题                                👁128 ❤32   │   │
│  │ 摘要文字...                             3h ago      │   │
│  └─────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────┘
```

**升级点：**
- 头像 hover 显示 zoom-in cursor + 点击放大预览（带毛玻璃遮罩）
- 统计区数字 `text-xl font-bold text-primary`，图标 `brand-500`
- Tab 切换用底部 2px brand 色下划线指示器
- 活动列表项 hover 同 feed 卡片

### 3.6 Onboarding 页（学校选择）

```
┌────────────────────────────────────────────────────────┐
│                    bg: var(--bg-muted)                  │
│                                                        │
│         ┌──────────────────────────────┐               │
│         │  ← 返回                      │               │
│         │                              │               │
│         │  选择你的学校                 │  ← h1        │
│         │  搜索并选择你所在的学校校区   │  ← muted     │
│         │                              │               │
│         │  省份                        │               │
│         │  ┌──────────────────────┐    │               │
│         │  │ 江苏省           ▾   │    │               │
│         │  └──────────────────────┘    │               │
│         │                              │               │
│         │  学校                        │               │
│         │  ┌──────────────────────┐    │               │
│         │  │ 南京大学           ▾ │    │               │
│         │  └──────────────────────┘    │               │
│         │                              │               │
│         │  校区                        │               │
│         │  ┌──────────────────────┐    │               │
│         │  │ 鼓楼校区           ▾ │    │               │
│         │  └──────────────────────┘    │               │
│         │                              │               │
│         │  [← 上一步]    [确认 →]     │               │
│         └──────────────────────────────┘               │
└────────────────────────────────────────────────────────┘
```

- 面板 620px 宽
- 表单控件全宽
- 底部按钮双栏等分：上一步（secondary）+ 确认（primary）

---

## 四、图标规范

- 图标库：Lucide（`@lucide/vue`）— 已在使用
- 尺寸阶梯：`14 | 16 | 18 | 19 | 20 | 24` px
- 描边宽度：默认（Lucide 默认 2px）
- 颜色：继承父元素 `color`
- 与文字间距：`var(--space-2)` = 8px

| 场景 | 尺寸 |
|------|------|
| 导航项图标 | 19px |
| 顶栏操作图标 | 18px |
| 按钮内图标 | 16-18px |
| 帖子数据图标（👁❤💬） | 16px |
| 空状态图标 | 24px |
| 评论区操作图标 | 14-15px |

---

## 五、全局样式重置清单

实现时需要覆盖/替换现有 `index.css` 中的硬编码值。以下是对照表：

| 现有值 | 替换为 Token |
|--------|-------------|
| `#0e7667` | `var(--brand-500)` 或 `var(--brand-600)` |
| `#075f52` | `var(--brand-700)` |
| `#0a6659` | `var(--brand-700)` |
| `#f6f8f7` | `var(--bg-page)` |
| `#fbfcfb` / `#fbfdfc` | `var(--bg-subtle)` |
| `#ffffff`（卡片/面板） | `var(--bg-surface)` |
| `#18222d` / `#17241f` | `var(--text-primary)` |
| `#30413a` / `#31423a` | `var(--text-secondary)` |
| `#63736c` / `#5e6f66` | `var(--text-tertiary)` |
| `#dce4df` | `var(--border-subtle)` |
| `#cbd6d0` | `var(--border-default)` |
| `#d5dfda` | `var(--border-default)` |
| `#b9dcd0` / `#82afa4` | `var(--border-strong)` / `var(--brand-300)` |
| `#dff0e9` / `#edf8f4` | `var(--brand-50)` / `var(--brand-100)` |
| `#e4f0ec` | `var(--avatar-bg)` |
| `#c7363f` / `#b42318` | `var(--danger-icon)` / `var(--danger-text)` |
| `6px` / `8px` 圆角 | `var(--radius-sm)` / `var(--radius-md)` |
| 硬编码阴影 | `var(--shadow-*)` |

---

## 六、实现优先级建议

| 优先级 | 内容 | 原因 |
|--------|------|------|
| P0 | Token 变量定义 + 全局替换 | 基础设施，其他全部依赖 |
| P0 | 按钮 + 输入框 + 卡片 | 高频组件，用户感知最强 |
| P1 | 导航（顶栏 + 侧栏） | 框架感 |
| P1 | 空状态 + 弹窗 | 提升完成度 |
| P2 | 评论 + 对话气泡 | 细节打磨 |
| P2 | 个人主页 + Onboarding | 页面级优化 |
| P3 | 滚动条 + 动效微调 | 锦上添花 |

---

## 七、设计原则备忘

1. **青绿为骨，层次为肉** — 保留品牌基因，用色阶创造深度
2. **克制装饰，强调排版** — 不堆特效，靠间距、字重、留白说话
3. **触手可及** — 可点击元素最小 42px 高，间距充足
4. **渐进披露** — 默认简洁，hover/展开后才显示复杂操作
5. **一致即美** — 同类元素统一 token，避免特例
