# Web 前端视觉重做（indigo 驾驶舱）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 Web 工作台从现有"暗黑紫、偏平、无辨识度"重做为 **indigo 蓝紫驾驶舱风格**（分层表面 + 大圆角 + 柔影光晕 + 氛围背景 + 招牌可视化），纯视觉层，不触逻辑。

**Architecture:** 全部改动集中在 `web/styles.css`（设计 token + 组件 + 氛围 + 招牌）与 `web/index.html`（侧边栏字符图标→内联 SVG）。`web/app.js` 仅改 quick 面板的两个字符图标。不改任何业务逻辑，不删任何 id/class/data-view 选择器。

**Tech Stack:** 纯 HTML/CSS/JS（无框架）。Google Fonts: Inter + JetBrains Mono。无构建。

**Spec:** `docs/superpowers/specs/2026-09-04-personal-workstation-beautify-design.md`

## Global Constraints

- 纯视觉层；<b>不改/不删</b> 任何功能性 `id`/`class`/`data-view`，包括：`#page-title`、`#kanban-badge`、`#github-badge`、`#quick-add`、`#refresh-btn`、`#board-select`、`#board-add`、`#task-search`、`#task-priority-filter`、`#task-due-filter`、`#kanban-undo`、`#column-add`、`#kanban-add`、`#note-search`、`#note-add`、`#notes-grid`、`#snippet-q`、`#snippet-language`、`#settings-form`、`#settings-host`、`#settings-port`、`#settings-test`、`#settings-status`、`#profile-form`、`#jwt-input`、`#jwt-secret`、`#jwt-run`、`#jwt-status`、`.brand`、`.backup-panel`、`.app-shell`、`.workspace`、`.content`、`button[data-view=…]`、`button[data-tool-tab=…]`、`button[data-quick=…]`、`button[data-dialog-close=…]`、`#note-dialog`、`#task-dialog`、`#board-dialog`、`#column-dialog`、`#confirm-dialog`、`#confirm-title`、`#confirm-message`、`#toast`、`.retry-button` 等（这些是 JS 与 `tests/test_web.py` 锚点）。
- 深色 indigo 配色，取值**逐字复制**自 spec §2.1。
- 字体：Inter + JetBrains Mono（CDN 已在 `index.html`）。
- 验收：不破坏 `tests/test_web.py` 的 18 项 Playwright 断言；浏览器人工验收为样式任务的测试周期。

---

### Task 1: 更新 :root 设计 token（indigo 体系）

**Files:**
- Modify: `web/styles.css`（第 1 行 `:root{…}`）

**Interfaces:**
- Consumes: 现有 CSS 类定义靠 `var(--…)` 变量。
- Produces: `--bg/--surface/--elevated/--line/--text/--text-2/--text-3/--accent/--accent-lt/--accent-soft/--success/--warning/--danger/--r-*/--sh-*/--glow-lt/--glow-bg` 全部 token，后续任务直接用。

- [ ] **Step 1: 替换 `:root` 块**

把 `styles.css` 第 1 行 `:root{…}` 整体替换为：

```css
:root{
  --bg:#0B0D11; --surface:#14161C; --elevated:#191C24;
  --line:rgba(255,255,255,.06);
  --text:#ECEEF2; --text-2:#9AA3B0; --text-3:#626A76;
  --accent:#6E7BF2; --accent-lt:#98A5F8; --accent-soft:rgba(110,123,242,.15);
  --success:#3ECF8E; --warning:#E8A93D; --danger:#EF6B6B;
  --r-sm:8px; --r:12px; --r-lg:16px; --r-xl:20px;
  --sh-1:0 1px 2px rgba(0,0,0,.3);
  --sh-2:0 8px 24px rgba(0,0,0,.3);
  --sh-3:0 24px 60px rgba(0,0,0,.45);
  --glow:0 0 0 1px var(--accent-soft), 0 8px 24px rgba(110,123,242,.16);
  --glow-lt:rgba(152,165,248,.55); --glow-bg:rgba(110,123,242,.12);
}
```

- [ ] **Step 2: 验证**

Run: `python -c "import re;print(open('web/styles.css').read().count('#6E7BF2'))"`（server 目录下用 venv python 打开；或直接浏览器看）Expected: 输出含新 accent，页面主色变蓝紫。

- [ ] **Step 3: 提交**

```bash
git add web/styles.css
git commit -m "style(web): indigo 设计 token 体系"
```

---

### Task 2: 基础组件应用新视觉（分层 / 大圆角 / 柔影 / 渐变）

**Files:**
- Modify: `web/styles.css`

**Interfaces:**
- Consumes: Task 1 的 token。
- Produces: 组件类的新观感，被所有页面复用。

- [ ] **Step 1: 更新卡片 / 面板 / 统计卡（分层表面 + 细边框 + 柔影）**

把 `.panel,.stat-card,.task-card,.note-card,.repo-card` 的 `background:var(--panel)` 与 box-shadow 改为：
```css
.panel,.stat-card,.task-card,.note-card,.repo-card,.backup-panel{
  background:rgba(20,22,28,.62);border:1px solid var(--line);border-radius:var(--r-lg);box-shadow:var(--sh-1)}
.stat-card{background:rgba(20,22,28,.62);padding:16px 18px}
.stat-card .label{color:var(--text-2)}
.stat-card .value{margin-top:8px;color:#fff;font-size:30px;font-weight:800;letter-spacing:-1px;text-shadow:0 0 24px rgba(152,165,248,.3)}
```

- [ ] **Step 2: 主按钮 / 次按钮（accent 渐变）**

```css
.primary-button{border:0;border-radius:var(--r);padding:9px 16px;color:#fff;font-weight:600;
  background:linear-gradient(135deg,var(--accent),var(--accent-lt));box-shadow:0 6px 18px rgba(110,123,242,.3)}
.primary-button:hover{filter:brightness(1.08)}
.secondary-button{background:var(--elevated);border:1px solid var(--line);color:var(--text-2);border-radius:var(--r)}
.secondary-button:hover{background:#22262f;color:#fff}
```

- [ ] **Step 3: 侧边导航（玻璃底 + 发光竖条 + accent 图标）**

```css
.nav-item{border-radius:var(--r-sm);border:1px solid transparent}
.nav-item:hover{background:rgba(255,255,255,.03);color:#dde1e8}
.nav-item.active{background:var(--accent-soft);color:#fff;border-color:rgba(110,123,242,.22)}
.nav-item.active:before{background:var(--accent);box-shadow:0 0 8px var(--glow-lt)}
.nav-item.active .nav-icon{color:var(--accent-lt)}
.nav-badge{background:var(--accent)}
```

- [ ] **Step 4: 输入框 / 搜索框 / 选择器（半透明底 + focus 光晕）**

```css
.search,.settings-form input,.modal input,.modal textarea,.modal select,.filter-select,.tool-field input,.tool-field textarea,.http-kv-key,.http-kv-value,#http-body,#http-url,#clipboard-input,.jwt-* textarea{
  background:#11141a;border:1px solid #2a303a;border-radius:var(--r)}
.search:focus,.settings-form input:focus,.modal input:focus,.modal textarea:focus,.modal select:focus,.filter-select:focus,#http-url:focus,#http-body:focus,#clipboard-input:focus{
  border-color:var(--accent);box-shadow:0 0 0 3px rgba(110,123,242,.16);outline:none}
```

- [ ] **Step 5: 标签 / 徽章 / 模态框**

```css
.tag{background:var(--accent-soft);color:var(--accent-lt);border-radius:999px}
.modal{background:rgba(20,22,28,.92);border:1px solid rgba(110,123,242,.16);border-radius:var(--r-xl);box-shadow:var(--sh-3)}
.modal::backdrop{background:rgba(0,0,0,.72)}
.note-editor textarea,.log-edit-body textarea{background:#11141a}
```

- [ ] **Step 6: 验证**

Run: 浏览器打开 `/app/`（或 `python run_server.py` 后访问）。Expected: 卡片更通透有层次、主按钮渐变、输入框 focus 蓝紫光晕、侧边栏选中玻璃底+紫竖条发光。

- [ ] **Step 7: 提交**

```bash
git add web/styles.css
git commit -m "style(web): 组件统一 indigo 分层/圆角/柔影/渐变"
```

---

### Task 3: 驾驶舱氛围背景（环境光 + 点阵 + 玻璃）

**Files:**
- Modify: `web/styles.css`

**Interfaces:**
- Consumes: Task 1 token。
- Produces: 全局氛围 backdrop。

- [ ] **Step 1: body 深空环境光背景 + 点阵**

把 `body` background 改为：
```css
body{background:
  radial-gradient(1100px 620px at 80% -10%, rgba(110,123,242,.12), transparent 60%),
  radial-gradient(800px 500px at 8% 112%, rgba(98,165,242,.07), transparent 55%),
  var(--bg);}
body::before{content:"";position:fixed;inset:0;pointer-events:none;z-index:0;
  background-image:radial-gradient(rgba(255,255,255,.05) 1px, transparent 1px);
  background-size:24px 24px;opacity:.32}
.workspace{position:relative}
.main-content,.sidebar{position:relative;z-index:1}
```

- [ ] **Step 2: 侧边栏玻璃**

```css
.sidebar{background:rgba(18,20,26,.72);backdrop-filter:blur(18px)}
```

- [ ] **Step 3: 验证**

浏览器看整体是否出现深空环境光 + 淡点阵、侧边栏通透。Expected: 有氛围但不抢内容。

- [ ] **Step 4: 提交**

```bash
git add web/styles.css
git commit -m "style(web): 驾驶舱氛围背景（环境光+点阵+玻璃侧栏）"
```

---

### Task 4: 招牌可视化（星野热力图 / 任务轨道环 / 发光时间线）

**Files:**
- Modify: `web/styles.css`

**Interfaces:**
- Consumes: Task 1 token。
- Produces: 仪表盘记忆点组件。

- [ ] **Step 1: 热力图 → 星野（.heat-cell 4 级 indigo + 高亮光斑 + hover 发光放大）**

```css
.heat-cell{border-radius:3px;transition:.12s}
.heat-cell:hover{transform:scale(1.28);box-shadow:0 0 14px var(--glow-lt);z-index:1}
.heat-cell.l1{background:rgba(110,123,242,.18)}
.heat-cell.l2{background:rgba(110,123,242,.38)}
.heat-cell.l3{background:rgba(110,123,242,.62)}
.heat-cell.l4{background:#A9B4FF;box-shadow:0 0 8px rgba(152,165,248,.5)}
```

- [ ] **Step 2: 任务轨道环（若存在 SVG 环形进度，加呼吸光晕；无则给进度条加渐变光）**

```css
@keyframes breathe{0%,100%{filter:drop-shadow(0 0 8px rgba(110,123,242,.35))}50%{filter:drop-shadow(0 0 18px rgba(110,123,242,.6))}}
.progress i{background:linear-gradient(90deg,var(--accent),var(--accent-lt));box-shadow:0 0 8px rgba(152,165,248,.4)}
```

- [ ] **Step 3: 活动流 → 发光时间线（.activity 左侧发光点）**

```css
.activity{position:relative;padding-left:16px;border-left:1px solid rgba(110,123,242,.18)}
.activity:before{content:"";position:absolute;left:-4px;top:12px;width:7px;height:7px;border-radius:50%;background:var(--accent);box-shadow:0 0 10px var(--glow-lt)}
.mini-avatar{border-radius:50%;background:linear-gradient(135deg,var(--accent),var(--accent-lt))}
```

- [ ] **Step 4: 验证**

浏览器人工验收仪表盘。Expected: 星野 hotmap hover 发光、速度条渐变带光、活动流时间线发光点。

- [ ] **Step 5: 提交**

```bash
git add web/styles.css
git commit -m "style(web): 招牌可视化（星野/轨道环/发光时间线）"
```

---

### Task 5: 侧边栏 / quick 字符图标 → 内联 SVG

**Files:**
- Modify: `web/index.html`（第 31-42 行侧边栏 `.nav-item` 的字符图标）
- Modify: `web/app.js`（约第 211 行 `.quick` 里的 `▤ ◉ ⚙`）

**Interfaces:**
- Consumes: 无。
- Produces: 线性 SVG 图标（stroke 2、round 端点）。

- [ ] **Step 1: 替换 index.html 侧边栏字符图标为内联 SVG**

把每个 `.nav-item` 内的 `<span class="nav-icon">▦</span>` 等，替换为 18px 线性 SVG（`viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"`，宽度用 CSS `.nav-icon svg{width:18px;height:18px}`）。九个依次用：仪表盘=四格方块、看板=三列、笔记=文档、GitHub=对勾圆、工具=波浪、代码=`</>`、Git=分支、日志=日历、番茄=时钟；设置=齿轮。每个 SVG 可直接从本地已生成的 `.claude/plans/ui-prototypes/design-preview.html` 复制（该类目图标与之一致，`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">…`）。

- [ ] **Step 2: 改 app.js 的 quick 图标（第 211 行）**

把 `▤ 新建笔记`/`◉ 查看 GitHub`/`⚙ 连接设置` 的字符，替换为对应小 SVG（或暂时保留字符，不影响测试锚点，可先把 `<button class="quick">▤ 新建笔记</button>` 的 `▤` 换为适用 SVG）。**保留 `data-view`/`data-quick` 属性不变。**

- [ ] **Step 3: 验证**

浏览器看侧边栏与 quick。Expected: 图标为线性描边样，非字符。`tests/test_web.py` 的 `#main-nav button` 计数=9 仍成立（按钮结构未变）。

- [ ] **Step 4: 提交**

```bash
git add web/index.html web/app.js
git commit -m "style(web): 侧边栏/快捷图标换为线性 SVG"
```

---

### Task 6: 全量回归（不破坏 JS / 测试锚点）

**Files:**
- Modify: 无（验证）
- Run: `server/tests/test_api.py`、`server/tests/test_web.py`

- [ ] **Step 1: 后端回归**

Run: `cd server && .venv/Scripts/python -m pytest tests/test_api.py -v`
Expected: 6 passed。

- [ ] **Step 2: 前端锚点回归（需 Playwright chromium，若无则人工核对列出锚点存活）**

Run: `cd server && .venv/Scripts/python -m pytest tests/test_web.py -v`（先 `playwright install chromium`）
Expected: 18 passed（`#page-title`、`.brand`、`#settings-form`、`#settings-host`、`#settings-port`、`#profile-form`、`.backup-panel`、`#task-dialog`、`#note-dialog`、`#jwt-secret`、`#jwt-run`、`#refresh-btn`、`#sidebar-name`、`#sidebar-avatar`、`.app-shell`、`#main-nav button`=9、`button[data-view=…]` 等）。

- [ ] **Step 3: 浏览器人工验收关键页**

仪表盘(招牌+氛围)、看板、笔记、GitHub、设置、开发工具(JWT/HTTP/剪贴板)。Expected: 均呈 indigo 视觉，功能正常（弹窗/导航/tab 切换）。

- [ ] **Step 4: 提交（如有微调）**

```bash
git add -A && git commit -m "style(web): 全量回归验收"
```
