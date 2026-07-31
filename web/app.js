const state = {
  view: "dashboard",
  baseUrl: window.localStorage.getItem("workstationBaseUrl") || window.location.origin,
  boardId: null,
  boards: [],
  columns: [],
  kanbanUndo: [],
  tool: "json",
  noteEditingId: null,
  noteTag: "all",
  noteMode: "edit",
  taskEditingId: null,
};

if (window.workstationDesktop) state.baseUrl = window.location.origin;

const $ = (selector) => document.querySelector(selector);
const content = $("#content");
let realtimeSocket = null;
let realtimeRefreshTimer = null;

class ReconnectingWebSocket {
  constructor(url) {
    this.url = url;
    this.retryCount = 0;
    this.lastSeq = 0;
    this.timer = null;
    this.connect();
  }
  connect() {
    if (this.ws) { this.ws.onclose = null; this.ws.close(); }
    setServerStatus(false, this.retryCount ? "实时同步重连中" : "实时同步连接中");
    this.ws = new WebSocket(this.url);
    this.ws.onopen = () => {
      this.retryCount = 0;
      setServerStatus(true, "服务已连接 · 实时同步");
      this.ws.send(JSON.stringify({ type: "sync_request", last_seq: this.lastSeq }));
    };
    this.ws.onmessage = (event) => {
      let message;
      try { message = JSON.parse(event.data); } catch (_) { return; }
      if (message.seq) this.lastSeq = Math.max(this.lastSeq, message.seq);
      if (message.type === "ping") { this.ws.send(JSON.stringify({ type: "pong" })); return; }
      if (["sync_state", "task_created", "task_updated", "task_deleted", "note_created", "note_updated", "note_deleted", "snippet_created", "snippet_updated", "snippet_deleted", "board_created", "column_created"].includes(message.type)) {
        window.clearTimeout(realtimeRefreshTimer);
        realtimeRefreshTimer = window.setTimeout(() => navigate(state.view), 180);
      }
    };
    this.ws.onclose = () => {
      setServerStatus(false, "实时同步重连中");
      const delay = Math.min(1000 * (2 ** this.retryCount), 30000) * (0.75 + Math.random() * 0.5);
      this.retryCount += 1;
      this.timer = window.setTimeout(() => this.connect(), delay);
    };
    this.ws.onerror = () => this.ws.close();
  }
}

function startRealtime() {
  if (realtimeSocket?.timer) window.clearTimeout(realtimeSocket.timer);
  const url = new URL(state.baseUrl);
  url.protocol = url.protocol === "https:" ? "wss:" : "ws:";
  url.pathname = "/ws";
  url.search = "";
  realtimeSocket = new ReconnectingWebSocket(url.toString());
}

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
}

// Markdown 渲染配置（marked v4 + highlight.js + DOMPurify）。
// marked v5 已移除 setOptions.highlight 回调，故锁定 4.3.0 以沿用回调式高亮。
if (window.marked && window.hljs) {
  marked.setOptions({
    breaks: true,
    gfm: true,
    headerIds: false,
    highlight(code, lang) {
      if (lang && hljs.getLanguage(lang)) {
        try { return hljs.highlight(code, { language: lang }).value; } catch (_) { /* fall through */ }
      }
      try { return hljs.highlightAuto(code).value; } catch (_) { return code; }
    },
  });
}

// 渲染 Markdown 原文为已清理的 HTML。输出统一经 DOMPurify 处理以防 XSS。
function renderMarkdown(rawMd) {
  if (!rawMd || !window.marked) return "";
  const html = marked.parse(rawMd);
  return window.DOMPurify ? window.DOMPurify.sanitize(html) : html;
}

async function api(path, options = {}) {
  const response = await fetch(`${state.baseUrl}${path}`, { headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
  if (!response.ok) {
    let detail = `${response.status} ${response.statusText}`;
    try { detail = (await response.json()).detail || detail; } catch (_) { /* response is not JSON */ }
    throw new Error(detail);
  }
  return response.status === 204 ? null : response.json();
}

function toast(message, isError = false) {
  const node = $("#toast");
  node.textContent = message;
  node.style.borderColor = isError ? "#a84b43" : "#4a4a4a";
  node.classList.add("show");
  window.clearTimeout(toast.timer);
  toast.timer = window.setTimeout(() => node.classList.remove("show"), 2600);
}

let confirmResolver = null;
function confirmAction(message, title = "确认删除") {
  const dialog = $("#confirm-dialog");
  if (!dialog) return Promise.resolve(window.confirm(message));
  $("#confirm-title").textContent = title;
  $("#confirm-message").textContent = message;
  dialog.showModal();
  return new Promise((resolve) => { confirmResolver = resolve; });
}

function setNavBadge(id, value) {
  const badge = $("#" + id);
  if (!badge) return;
  const count = Number(value) || 0;
  badge.textContent = count > 99 ? "99+" : String(count);
  badge.hidden = count <= 0;
}

function setServerStatus(online, label) {
  const node = $("#server-status");
  node.innerHTML = `<span class="status-dot ${online ? "online" : "offline"}"></span><span>${escapeHtml(label)}</span>`;
}

function formatDate(value) {
  if (!value) return "刚刚";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleDateString("zh-CN", { month: "short", day: "numeric" });
}

function showLoading() { content.innerHTML = '<div class="empty">正在同步数据…</div>'; }

function showError(error) { content.innerHTML = `<div class="empty">加载失败：${escapeHtml(error.message)}<br><button class="secondary-button retry-button">重试</button></div>`; $(".retry-button").addEventListener("click", () => navigate(state.view)); }

async function navigate(view) {
  state.view = view;
  document.querySelectorAll("[data-view]").forEach((item) => item.classList.toggle("active", item.dataset.view === view));
  const titles = { dashboard: "仪表盘", kanban: "任务看板", notes: "笔记", github: "GitHub", tools: "开发工具", snippets: "代码片段", git: "本地 Git", logs: "开发日志", focus: "番茄专注", settings: "连接设置" };
  $("#page-title").textContent = titles[view] || "仪表盘";
  showLoading();
  try {
    if (view === "dashboard") await renderDashboard();
    if (view === "kanban") await renderKanban();
    if (view === "notes") await renderNotes();
    if (view === "github") await renderGithub();
    if (view === "tools") renderTools();
    if (view === "snippets") await renderSnippets();
    if (view === "git") await renderLocalGit();
    if (view === "logs") await renderLogs();
    if (view === "focus") await renderFocus();
    if (view === "settings") await renderSettings();
    $("#last-updated").textContent = `同步于 ${new Date().toLocaleTimeString("zh-CN", { hour: "2-digit", minute: "2-digit" })}`;
  } catch (error) { showError(error); }
}

async function renderDashboard() {
  const [summary, heatmap, activity, repos] = await Promise.all([
    api("/api/dashboard/summary"),
    api("/api/dashboard/github-heatmap?days=90"),
    api("/api/github/activity?limit=6"),
    api("/api/github/repos"),
  ]);
  const cells = heatmap.items.map((item) => `<span class="heat-cell ${item.count >= 5 ? "l4" : item.count >= 3 ? "l3" : item.count === 2 ? "l2" : item.count === 1 ? "l1" : ""}" title="${item.date}：${item.count} 次"></span>`).join("");
  const activities = activity.length ? activity.map((item) => `<div class="activity"><div class="mini-avatar">${escapeHtml((item.actor || "G").slice(0, 1).toUpperCase())}</div><div><strong>${escapeHtml(item.actor || "GitHub")}</strong><p>${escapeHtml(eventLabel(item.type))}</p><time>${formatDate(item.created_at)}</time></div></div>`).join("") : '<div class="empty">还没有 GitHub 活动，配置账号后刷新即可。</div>';
  const completion = Math.min(100, Number(summary.task_completion_rate || 0));
  setNavBadge("kanban-badge", summary.tasks_total);
  setNavBadge("github-badge", summary.github_repositories);
  const repoCards = repos.slice(0, 3).map((repo) => `<article class="repo-card"><h3>${escapeHtml(repo.name)}</h3><p>${escapeHtml(repo.description || "暂无描述")}</p><div class="repo-meta"><span>${escapeHtml(repo.language || "未知语言")}</span><span>★ ${repo.stars || 0}</span><a class="repo-link" href="${escapeHtml(repo.html_url || "#")}" target="_blank" rel="noreferrer">打开仓库 ↗</a></div></article>`).join("");
  const today = new Date().toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "long" });
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">${today.toUpperCase()}</div><h2 id="dashboard-greeting">下午好，Liu</h2><p>这是你的本地工作概览。</p></div><button class="secondary-button" data-view="kanban">查看任务</button></div>
    <div class="stats-grid"><div class="stat-card"><div class="label">任务</div><div class="value">${summary.tasks_total}</div><div class="delta">▲ ${summary.tasks_completed} 项已完成</div></div><div class="stat-card"><div class="label">笔记</div><div class="value">${summary.notes_total}</div><div class="delta">▲ 本地存储</div></div><div class="stat-card"><div class="label">GitHub</div><div class="value">${summary.github_commits_30d}</div><div class="delta">▲ 近 30 天提交</div></div><div class="stat-card"><div class="label">活跃项目</div><div class="value">${summary.github_repositories}</div><div class="delta">— 保持稳定</div></div></div>
    <div class="dashboard-grid"><div class="panel"><div class="panel-head"><h3>工作热力图</h3><span>近 90 天 · ${heatmap.total} 次提交</span></div><div class="heatmap">${cells}</div><div class="progress-row"><span>任务完成率</span><strong>${completion}%</strong></div><div class="progress"><i style="width:${completion}%"></i></div></div><div class="panel"><div class="panel-head"><h3>活动动态</h3><span>最近</span></div><div class="activity-list">${activities}</div></div></div>
    <div class="dashboard-grid"><div class="panel"><div class="panel-head"><h3>活跃仓库</h3><span>${repos.length} 个</span></div><div class="repo-grid">${repoCards || '<div class="empty">暂无仓库数据</div>'}</div></div><div class="panel"><div class="panel-head"><h3>工作分布</h3><span>本地概览</span></div><div class="bars"><div class="bar-row"><span>任务</span><i style="width:${Math.max(12, Math.min(100, Number(summary.tasks_total) * 3))}%"></i><b>${summary.tasks_total}</b></div><div class="bar-row"><span>笔记</span><i style="width:${Math.max(12, Math.min(100, Number(summary.notes_total) * 4))}%"></i><b>${summary.notes_total}</b></div><div class="bar-row"><span>提交</span><i style="width:${Math.max(12, Math.min(100, Number(summary.github_commits_30d) * 2))}%"></i><b>${summary.github_commits_30d}</b></div></div></div></div>
    <div class="panel"><div class="panel-head"><h3>快捷操作</h3><span>常用入口</span></div><div class="quick-grid"><button class="quick" data-quick="task">＋ 新建任务</button><button class="quick" data-quick="note">▤ 新建笔记</button><button class="quick" data-view="github">◉ 查看 GitHub</button><button class="quick" data-view="settings">⚙ 连接设置</button></div></div>`;
  bindViewButtons();
  document.querySelectorAll("[data-quick]").forEach((button) => button.addEventListener("click", () => openQuick(button.dataset.quick)));
}
function eventLabel(type) {
  return ({ PushEvent: "推送了新的提交", PullRequestEvent: "更新了一个拉取请求", IssuesEvent: "更新了一个 Issue", IssueCommentEvent: "评论了一个 Issue" }[type] || "产生了新的 GitHub 活动");
}

async function renderKanban() {
  state.boards = await api("/api/boards");
  if (!state.boards.length) throw new Error("还没有创建看板");
  if (!state.boardId || !state.boards.some((board) => board.id === state.boardId)) state.boardId = state.boards[0].id;
  state.columns = await api(`/api/boards/${state.boardId}/tasks`);
  setNavBadge("kanban-badge", state.columns.reduce((total, column) => total + column.tasks.length, 0));
  renderKanbanMarkup();
}

function renderKanbanMarkup(filter = "", priorityFilter = "all", dueFilter = "all") {
  const normalized = filter.trim().toLowerCase();
  const columns = state.columns.map((column) => {
    const tasks = column.tasks.filter((task) => {
      const textMatch = !normalized || `${task.title} ${task.description}`.toLowerCase().includes(normalized);
      const priorityMatch = priorityFilter === "all" || task.priority === priorityFilter;
      const dueState = taskDueState(task, column.name);
      const dueMatch = dueFilter === "all" || dueState === dueFilter;
      return textMatch && priorityMatch && dueMatch;
    });
    return `<section class="kanban-column"><div class="column-head"><strong>${escapeHtml(column.name)}</strong><span class="count">${tasks.length}</span></div><div class="task-list">${tasks.length ? tasks.map((task) => taskMarkup(task, column.name)).join("") : '<div class="empty">暂无任务</div>'}</div></section>`;
  }).join("");
  const boardOptions = state.boards.map((board) => `<option value="${board.id}">${escapeHtml(board.name)}</option>`).join("");
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">SPRINT BOARD</div><div class="board-heading"><select id="board-select" class="board-select">${boardOptions}</select><button id="board-add" class="icon-button" title="新建看板" aria-label="新建看板">＋</button></div><p>把今天的工作保持在一个清晰的节奏里。</p></div><div class="kanban-toolbar"><input id="task-search" class="search" placeholder="搜索任务…" value="${escapeHtml(filter)}"><select id="task-priority-filter" class="filter-select"><option value="all">全部优先级</option><option value="high">高优先级</option><option value="medium">中优先级</option><option value="low">低优先级</option></select><select id="task-due-filter" class="filter-select"><option value="all">全部日期</option><option value="today">今天到期</option><option value="overdue">已逾期</option><option value="none">无截止日期</option></select>${state.kanbanUndo.length ? `<button id="kanban-undo" class="icon-button" title="撤销上一步" aria-label="撤销上一步">↶</button>` : ""}<button id="column-add" class="secondary-button">＋ 新建列</button><button id="kanban-add" class="primary-button">＋ 新建任务</button></div></div><div class="kanban-columns">${columns}</div>`;
  $("#board-select").value = String(state.boardId);
  $("#board-select").addEventListener("change", async (event) => { state.boardId = Number(event.target.value); state.kanbanUndo = []; await renderKanban(); });
  $("#board-add").addEventListener("click", () => $("#board-dialog").showModal());
  $("#column-add").addEventListener("click", () => $("#column-dialog").showModal());
  $("#task-search").addEventListener("input", (event) => renderKanbanMarkup(event.target.value, $("#task-priority-filter").value, $("#task-due-filter").value));
  $("#task-priority-filter").value = priorityFilter;
  $("#task-priority-filter").addEventListener("change", () => renderKanbanMarkup($("#task-search").value, $("#task-priority-filter").value, $("#task-due-filter").value));
  $("#task-due-filter").value = dueFilter;
  $("#task-due-filter").addEventListener("change", () => renderKanbanMarkup($("#task-search").value, $("#task-priority-filter").value, $("#task-due-filter").value));
  $("#kanban-add").addEventListener("click", () => openQuick("task"));
  $("#kanban-undo")?.addEventListener("click", () => taskAction("undo", 0));
  document.querySelectorAll("[data-task-action]").forEach((button) => button.addEventListener("click", () => taskAction(button.dataset.taskAction, Number(button.dataset.taskId))));
}

function taskDueState(task, columnName = "") {
  if (!task.due_date) return "none";
  if (/完成|归档|done/i.test(columnName)) return "completed";
  const today = new Date().toISOString().slice(0, 10);
  if (task.due_date < today) return "overdue";
  if (task.due_date === today) return "today";
  return "upcoming";
}

function taskMarkup(task, columnName) {
  const dueState = taskDueState(task, columnName);
  const dueLabel = dueState === "overdue" ? "已逾期" : dueState === "today" ? "今天到期" : task.due_date ? `截止 ${formatDate(task.due_date)}` : "无截止日期";
  return `<article class="task-card ${escapeHtml(task.priority)} ${dueState === "overdue" ? "overdue" : ""}"><h4>${escapeHtml(task.title)}</h4><p>${escapeHtml(task.description || "暂无描述")}</p><div class="task-foot"><span>${escapeHtml(task.priority === "high" ? "高优先级" : task.priority === "low" ? "低优先级" : "中优先级")} · <i class="due-label ${dueState}">${escapeHtml(dueLabel)}</i></span><div class="task-actions"><button class="small-button" data-task-action="edit" data-task-id="${task.id}" title="编辑任务" aria-label="编辑任务">✎</button><button class="small-button" data-task-action="move-left" data-task-id="${task.id}" title="移动到上一列" aria-label="移动到上一列">←</button><button class="small-button" data-task-action="move-right" data-task-id="${task.id}" title="移动到下一列" aria-label="移动到下一列">→</button><button class="small-button danger" data-task-action="delete" data-task-id="${task.id}" title="删除任务" aria-label="删除任务">×</button></div></div></article>`;
}
async function taskAction(action, taskId) {
  try {
    if (action === "undo") {
      const previous = state.kanbanUndo.pop();
      if (!previous) return;
      try {
        await api(`/api/tasks/${previous.taskId}`, { method: "PUT", body: JSON.stringify({ column_id: previous.previousColumnId }) });
        toast(`已撤销，任务退回${previous.previousColumnName}`);
      } catch (error) {
        state.kanbanUndo.push(previous);
        throw error;
      }
      await renderKanban();
      return;
    }
    const columnIndex = state.columns.findIndex((column) => column.tasks.some((task) => task.id === taskId));
    const task = state.columns[columnIndex]?.tasks.find((item) => item.id === taskId);
    if (!task) return;
    if (action === "edit") {
      openTaskEditor(task);
      return;
    }
    if (action === "delete") {
      if (!(await confirmAction(`确认删除“${task.title}”？`))) return;
      await api(`/api/tasks/${taskId}`, { method: "DELETE" });
      state.kanbanUndo = state.kanbanUndo.filter((item) => item.taskId !== taskId);
      toast("任务已删除");
    } else if (action === "move-left" || action === "move-right") {
      const offset = action === "move-left" ? -1 : 1;
      const target = state.columns[columnIndex + offset];
      if (!target) { toast(action === "move-left" ? "任务已经在第一列" : "任务已经在最后一列"); return; }
      await api(`/api/tasks/${taskId}`, { method: "PUT", body: JSON.stringify({ column_id: target.id }) });
      state.kanbanUndo.push({ taskId, previousColumnId: task.column_id, previousColumnName: state.columns[columnIndex].name });
      toast(`已移动到${target.name}`);
    }
    await renderKanban();
  } catch (error) { toast(error.message, true); }
}
async function renderNotes() {
  const notes = await api("/api/notes");
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">KNOWLEDGE BASE</div><h2>笔记</h2><p>${notes.length} 篇本地笔记</p></div><div class="notes-toolbar"><input id="note-search" class="search" placeholder="搜索笔记…"><button id="note-add" class="primary-button">＋ 新建笔记</button></div></div><div id="notes-grid" class="notes-grid">${notes.length ? notes.map(noteMarkup).join("") : '<div class="empty">还没有笔记。</div>'}</div>`;
  const tagValues = [...new Set(notes.flatMap((note) => note.tags || []))].sort();
  const tagOptions = ["<option value=\"all\">全部标签</option>", ...tagValues.map((tag) => "<option value=\"" + escapeHtml(tag) + "\">" + escapeHtml(tag) + "</option>")].join("");
  $(".notes-toolbar").insertAdjacentHTML("afterbegin", "<select id=\"note-tag\" class=\"filter-select\">" + tagOptions + "</select>");
  $("#note-add").addEventListener("click", () => openQuick("note"));
  bindNoteCards();
  const refreshNotes = async () => {
    const query = $("#note-search").value.trim();
    const tag = $("#note-tag").value;
    const params = new URLSearchParams();
    if (query) params.set("search", query);
    if (tag !== "all") params.set("tag", tag);
    const filtered = params.toString() ? await api("/api/notes?" + params.toString()) : notes;
    $("#notes-grid").innerHTML = filtered.length ? filtered.map(noteMarkup).join("") : '<div class="empty">没有匹配的笔记。</div>';
    bindNoteCards();
  };
  $("#note-search").addEventListener("input", refreshNotes);
  $("#note-tag").addEventListener("change", refreshNotes);
}

function noteMarkup(note) {
  const tags = (note.tags || []).map((tag) => `<span class="tag">${escapeHtml(tag)}</span>`).join("");
  const excerpt = renderMarkdown(note.content || "") || "<span class=\"muted\">暂无内容</span>";
  return `<article class="note-card"><h3>${escapeHtml(note.title)}</h3><div class="note-excerpt">${excerpt}</div><div class="tags">${tags || '<span class="tag">未分类</span>'}</div><div class="repo-meta"><span>${formatDate(note.updated_at)}</span><span>#${note.id}</span></div><div class="note-actions"><button class="small-button" data-note-action="edit" data-note-id="${note.id}">编辑</button><button class="small-button danger" data-note-action="delete" data-note-id="${note.id}">删除</button></div></article>`;
}

function bindNoteCards() {
  document.querySelectorAll("[data-note-action]").forEach((button) => button.addEventListener("click", () => noteAction(button.dataset.noteAction, Number(button.dataset.noteId))));
}

async function noteAction(action, noteId) {
  try {
    if (action === "edit") {
      const note = await api(`/api/notes/${noteId}`);
      openNoteEditor(note);
      return;
    }
    const title = document.querySelector(`[data-note-id="${noteId}"][data-note-action="delete"]`)?.closest(".note-card")?.querySelector("h3")?.textContent || "这篇笔记";
    if (!(await confirmAction(`确认删除“${title}”？`))) return;
    await api(`/api/notes/${noteId}`, { method: "DELETE" });
    toast("笔记已删除");
    await renderNotes();
  } catch (error) { toast(error.message, true); }
}

async function renderGithub() {
  const [repos, activity] = await Promise.all([api("/api/github/repos"), api("/api/github/activity?limit=20")]);
  setNavBadge("github-badge", repos.length);
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">OPEN SOURCE ACTIVITY</div><h2>GitHub 追踪</h2><p>服务端缓存 ${repos.length} 个仓库，${activity.length} 条近期活动。</p></div><button id="github-refresh" class="primary-button">刷新 GitHub</button></div><div class="github-layout"><div class="panel"><div class="panel-head"><h3>近期活动</h3><span>${activity.length} 条</span></div><div class="activity-list">${activity.length ? activity.map((item) => `<div class="activity"><div class="mini-avatar">${escapeHtml((item.actor || "G").slice(0, 1).toUpperCase())}</div><div><strong>${escapeHtml(item.actor || "GitHub")}</strong><p>${escapeHtml(eventLabel(item.type))}</p><time>${formatDate(item.created_at)}</time></div></div>`).join("") : '<div class="empty">暂无缓存活动。</div>'}</div></div><div class="panel"><div class="panel-head"><h3>仓库列表</h3><span>${repos.length} 个</span></div><div class="repo-grid">${repos.length ? repos.map((repo) => `<article class="repo-card"><h3>${escapeHtml(repo.name)}</h3><p>${escapeHtml(repo.description || "暂无描述")}</p><div class="repo-meta"><span>${escapeHtml(repo.language || "未知语言")}</span><span>★ ${repo.stars}</span><a class="repo-link" href="${escapeHtml(repo.html_url || "#")}" target="_blank" rel="noreferrer">打开仓库 ↗</a></div></article>`).join("") : '<div class="empty">请先在服务端配置 GitHub 用户名。</div>'}</div></div></div>`;
  $("#github-refresh").addEventListener("click", async () => {
    const button = $("#github-refresh");
    button.disabled = true;
    try { await api("/api/github/refresh", { method: "POST", body: "{}" }); toast("GitHub 数据已刷新"); await renderGithub(); } catch (error) { toast(error.message, true); } finally { button.disabled = false; }
  });
}

async function renderSnippets() {
  const [items, meta] = await Promise.all([api("/api/snippets"), api("/api/snippets/meta")]);
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">SNIPPET LIBRARY</div><h2>代码片段</h2><p>${items.length} 个可复用片段</p></div><button id="snippet-save" class="primary-button">＋ 保存片段</button></div><div class="panel"><div class="snippet-toolbar"><input id="snippet-q" class="search" placeholder="搜索标题、代码或描述"><select id="snippet-language" class="filter-select"><option value="">全部语言</option>${Object.keys(meta.languages).sort().map((x) => `<option>${escapeHtml(x)}</option>`).join("")}</select></div><div id="snippets-grid" class="notes-grid" style="margin-top:14px">${items.length ? items.map(snippetMarkup).join("") : '<div class="empty">还没有代码片段。</div>'}</div></div>`;
  const repaint = async () => { const q = encodeURIComponent($("#snippet-q").value); const lang = encodeURIComponent($("#snippet-language").value); const data = await api(`/api/snippets?q=${q}&language=${lang}`); $("#snippets-grid").innerHTML = data.length ? data.map(snippetMarkup).join("") : '<div class="empty">没有匹配片段。</div>'; bindSnippetActions(); };
  $("#snippet-q").addEventListener("input", repaint); $("#snippet-language").addEventListener("change", repaint); $("#snippet-save").addEventListener("click", () => openSnippetEditor()); bindSnippetActions();
}
function snippetMarkup(item) { return `<article class="note-card snippet-card"><h3>${escapeHtml(item.title)}</h3><p>${escapeHtml(item.description || item.language)}</p><pre class="snippet-preview"><code>${escapeHtml(item.code.split("\n").slice(0, 5).join("\n"))}</code></pre><div class="tags">${(item.tags || []).map((tag) => `<span class="tag">${escapeHtml(tag)}</span>`).join("")}</div><div class="note-actions"><button class="small-button" data-copy-snippet="${item.id}">复制</button><button class="small-button danger" data-delete-snippet="${item.id}">删除</button></div></article>`; }
function bindSnippetActions() { document.querySelectorAll("[data-copy-snippet]").forEach((b) => b.onclick = async () => { const item = await api(`/api/snippets/${b.dataset.copySnippet}`); await navigator.clipboard.writeText(item.code); toast("代码已复制"); }); document.querySelectorAll("[data-delete-snippet]").forEach((b) => b.onclick = async () => { if (await confirmAction("删除这个代码片段？")) { await api(`/api/snippets/${b.dataset.deleteSnippet}`, { method: "DELETE" }); await renderSnippets(); } }); }
function openSnippetEditor() { const title = prompt("片段标题"); if (!title) return; const code = prompt("粘贴代码"); if (code == null) return; const language = prompt("语言（如 python、js）", "plain") || "plain"; const tags = (prompt("标签，用逗号分隔", "") || "").split(",").map((x) => x.trim()).filter(Boolean); api("/api/snippets", { method: "POST", body: JSON.stringify({ title, code, language, tags }) }).then(() => { toast("片段已保存"); renderSnippets(); }).catch((e) => toast(e.message, true)); }

async function renderLocalGit() {
  const repos = await api("/api/git/repos");
  const stored = JSON.parse(localStorage.getItem("git_scan_directories") || "[]");
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">LOCAL REPOSITORIES</div><h2>本地 Git 仓库</h2><p>选择一个工作目录，扫描该目录下的 Git 仓库。</p></div><button id="git-refresh" class="primary-button">开始扫描</button></div><section class="git-scan-panel panel"><div class="panel-head"><div><h3>扫描工作目录</h3><span>只读取 Git 状态，不修改仓库内容</span></div><button id="git-choose" class="secondary-button">选择文件夹</button></div><div id="git-directories" class="git-directories">${stored.length ? stored.map((path) => `<span class="git-directory">${escapeHtml(path)}<button data-remove-dir="${escapeHtml(path)}" title="移除">×</button></span>`).join("") : '<span class="muted">尚未选择目录</span>'}</div><div class="git-scan-hint">桌面版会打开系统文件夹选择器；浏览器模式可点击“选择文件夹”后手动输入路径。</div></section><div class="repo-grid">${repos.length ? repos.map((r) => `<article class="repo-card" data-repo-card="${r.id}"><div class="repo-card-head"><div><h3>${escapeHtml(r.name)}</h3><p>${escapeHtml(r.path)}</p></div></div><div class="repo-meta"><span>${escapeHtml(r.branch || "无分支")}</span><span>${r.changed + r.staged + r.untracked} 项变更</span></div><small class="muted">最近提交：${escapeHtml(r.last_commit || "暂无")}</small><button class="small-button repo-details-button" data-git-details="${r.id}">查看变更</button><div class="repo-details" id="git-details-${r.id}" hidden></div></article>`).join("") : '<div class="empty">选择工作目录后开始扫描。</div>'}</div>`;
  const saveDirs = (dirs) => { localStorage.setItem("git_scan_directories", JSON.stringify(dirs)); return dirs; };
  $("#git-choose").onclick = async () => { let selected = null; if (window.workstationDesktop?.chooseDirectory) selected = await window.workstationDesktop.chooseDirectory(); if (!selected) selected = prompt("输入工作目录的完整路径", stored[0] || ""); if (!selected) return; const dirs = saveDirs([...new Set([...stored, selected])]); await renderLocalGit(); };
  $("#git-refresh").onclick = async () => { const dirs = JSON.parse(localStorage.getItem("git_scan_directories") || "[]"); if (!dirs.length) { toast("请先选择工作目录", true); return; } const button = $("#git-refresh"); button.disabled = true; try { await api("/api/git/repos/refresh", { method: "POST", body: JSON.stringify(dirs) }); toast("Git 仓库扫描完成"); await renderLocalGit(); } catch (e) { toast(e.message, true); } finally { button.disabled = false; } };
  document.querySelectorAll("[data-remove-dir]").forEach((button) => button.onclick = () => { saveDirs(stored.filter((path) => path !== button.dataset.removeDir)); renderLocalGit(); });
  document.querySelectorAll("[data-git-details]").forEach((button) => button.onclick = async () => { const target = $("#git-details-" + button.dataset.gitDetails); if (!target.hidden) { target.hidden = true; button.textContent = "查看变更"; return; } button.disabled = true; try { const detail = await api(`/api/git/repos/${button.dataset.gitDetails}`); target.innerHTML = `<div class="git-detail-section"><strong>工作区变更 ${detail.changes.length} 项</strong>${detail.changes.length ? detail.changes.map((change) => `<div class="git-change"><span class="git-change-status ${change.status}">${change.status === "untracked" ? "未跟踪" : change.status === "staged" ? "已暂存" : "已修改"}</span><code>${escapeHtml(change.path)}</code></div>`).join("") : '<span class="muted">工作区干净</span>'}</div><div class="git-detail-section"><strong>提交记录 ${detail.commits.length} 条</strong>${detail.commits.length ? detail.commits.map((commit) => `<div class="git-commit"><code>${escapeHtml(commit.sha.slice(0, 7))}</code><span>${escapeHtml(commit.message)}</span><time>${formatDate(commit.pushed_at)}</time></div>`).join("") : '<span class="muted">暂无提交记录</span>'}</div>`; target.hidden = false; button.textContent = "收起变更"; } catch (e) { toast(e.message, true); } finally { button.disabled = false; } });
}

async function renderFocus() { const today = await api("/api/focus/today"); content.innerHTML = `<div class="view-head"><div><div class="eyebrow">FOCUS MODE</div><h2>番茄专注</h2><p>今日完成 ${today.count} 个番茄，共 ${today.total_minutes} 分钟。</p></div></div><div class="panel focus-panel"><div id="focus-clock" class="focus-clock">25:00</div><label class="tool-field">关联任务<input id="focus-title" placeholder="可选"></label><div class="tool-actions"><button id="focus-start" class="primary-button">开始 25 分钟</button><button id="focus-stop" class="secondary-button" disabled>完成</button><button id="focus-interrupt" class="secondary-button" disabled>中断</button></div></div>`; let timer = null; let session = null; let end = 0; const tick = () => { const left = Math.max(0, Math.ceil((end - Date.now()) / 1000)); $("#focus-clock").textContent = `${String(Math.floor(left / 60)).padStart(2, "0")}:${String(left % 60).padStart(2, "0")}`; if (!left && timer) { clearInterval(timer); timer = null; } }; $("#focus-start").onclick = async () => { session = await api("/api/focus/start", { method: "POST", body: JSON.stringify({ duration: 1500, task_title: $("#focus-title").value }) }); end = Date.now() + 1500000; timer = setInterval(tick, 500); tick(); $("#focus-start").disabled = true; $("#focus-stop").disabled = false; $("#focus-interrupt").disabled = false; }; const finish = async (path) => { if (!session) return; await api(`/api/focus/${session.id}/${path}`, { method: "PUT", body: "{}" }); toast(path === "stop" ? "专注已完成" : "专注已中断"); await renderFocus(); }; $("#focus-stop").onclick = () => finish("stop"); $("#focus-interrupt").onclick = () => finish("interrupt"); }

async function renderLogs(selectedDate = new Date()) {
  const dateValue = `${selectedDate.getFullYear()}-${String(selectedDate.getMonth() + 1).padStart(2, "0")}-${String(selectedDate.getDate()).padStart(2, "0")}`;
  const [log, streak] = await Promise.all([api(`/api/logs?date=${dateValue}`), api("/api/logs/streak")]);
  const moods = [["happy", "愉快", "☀"], ["neutral", "平静", "—"], ["frustrated", "受阻", "!"], ["tired", "疲惫", "…"]];
  content.innerHTML = `<div class="log-head"><div><div class="eyebrow">DAILY DEVELOPMENT LOG</div><div class="log-title-row"><button id="log-prev" class="icon-button" title="前一天">‹</button><div><h2>每日开发日志</h2><p id="log-date-label">${selectedDate.toLocaleDateString("zh-CN", { year: "numeric", month: "long", day: "numeric", weekday: "long" })}</p></div><button id="log-next" class="icon-button" title="后一天">›</button></div></div><div class="log-head-actions"><span class="streak-pill">连续记录 <strong>${streak.streak}</strong> 天</span><button id="log-save" class="primary-button">保存日志</button></div></div><div class="log-layout"><aside class="log-sidebar panel"><div class="panel-head"><h3>今天的状态</h3><span>${dateValue}</span></div><div class="mood-grid">${moods.map(([value, label, icon]) => `<button class="mood-choice ${log.mood === value ? "active" : ""}" data-mood="${value}"><b>${icon}</b><span>${label}</span></button>`).join("")}</div><div class="log-tip"><strong>记录建议</strong><p>写下完成的事项、遇到的问题和下一步计划，支持 Markdown。</p></div></aside><section class="log-editor panel"><div class="log-editor-bar"><span>开发记录</span><div class="log-mode-switch"><button class="active" data-log-mode="edit">编辑</button><button data-log-mode="preview">预览</button><button data-log-mode="split">分栏</button></div></div><div id="log-edit-body" class="log-edit-body"><textarea id="log-content" placeholder="# 今天完成了什么？\n\n- 完成…\n- 遇到…\n\n## 明天计划">${escapeHtml(log.content)}</textarea><div id="log-preview" class="log-preview"><span class="muted">预览区为空</span></div></div></section></div>`;
  let mode = "edit"; const input = $("#log-content"), preview = $("#log-preview"); const refreshPreview = () => { preview.innerHTML = renderMarkdown(input.value) || '<span class="muted">预览区为空</span>'; }; const applyMode = (next) => { mode = next; $("#log-edit-body").dataset.mode = next; document.querySelectorAll("[data-log-mode]").forEach((button) => button.classList.toggle("active", button.dataset.logMode === next)); refreshPreview(); }; input.oninput = refreshPreview; document.querySelectorAll("[data-log-mode]").forEach((button) => button.onclick = () => applyMode(button.dataset.logMode));
  document.querySelectorAll("[data-mood]").forEach((button) => button.onclick = () => { document.querySelectorAll("[data-mood]").forEach((item) => item.classList.remove("active")); button.classList.add("active"); });
  $("#log-prev").onclick = () => renderLogs(new Date(selectedDate.getTime() - 86400000)); $("#log-next").onclick = () => renderLogs(new Date(selectedDate.getTime() + 86400000)); $("#log-save").onclick = async () => { const mood = document.querySelector("[data-mood].active")?.dataset.mood || ""; await api(`/api/logs/${log.id}`, { method: "PUT", body: JSON.stringify({ content: input.value, mood }) }); toast("开发日志已保存"); }; applyMode(mode);
}

function renderTools() {
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">DEVELOPER TOOLKIT</div><h2>开发工具</h2><p>常用数据处理工具在本地运行，不会上传内容。</p></div><span class="tool-local-badge">仅本地处理</span></div><div class="tool-tabs" role="tablist"><button class="tool-tab" data-tool-tab="json">JSON</button><button class="tool-tab" data-tool-tab="yaml">JSON ↔ YAML</button><button class="tool-tab" data-tool-tab="base64">Base64</button><button class="tool-tab" data-tool-tab="url">URL 编解码</button><button class="tool-tab" data-tool-tab="timestamp">时间戳</button><button class="tool-tab" data-tool-tab="jwt">JWT</button><button class="tool-tab" data-tool-tab="regex">正则测试</button></div><section id="tool-panel" class="tool-panel"></section>`;
  document.querySelectorAll("[data-tool-tab]").forEach((button) => button.addEventListener("click", () => {
    state.tool = button.dataset.toolTab;
    renderToolPanel();
  }));
  renderToolPanel();
}

function renderToolPanel() {
  const panel = $("#tool-panel");
  if (!panel) return;
  document.querySelectorAll("[data-tool-tab]").forEach((button) => button.classList.toggle("active", button.dataset.toolTab === state.tool));
  panel.innerHTML = toolMarkup(state.tool);
  if (state.tool === "json") bindJsonTool();
  if (state.tool === "base64") bindBase64Tool();
  if (state.tool === "url") bindUrlTool();
  if (state.tool === "regex") bindRegexTool();
  if (state.tool === "yaml") bindYamlTool();
  if (state.tool === "timestamp") bindTimestampTool();
  if (state.tool === "jwt") bindJwtTool();
}

function toolMarkup(tool) {
  if (tool === "yaml") return `<div class="tool-head"><div><h3>JSON 与 YAML</h3><p>轻量转换，使用浏览器内置 JSON 解析。</p></div></div><div class="tool-split"><label class="tool-field">输入<textarea id="yaml-input" rows="13" placeholder='JSON 或简单 YAML'></textarea></label><label class="tool-field">输出<textarea id="yaml-output" rows="13" readonly></textarea></label></div><div class="tool-actions"><button class="primary-button" id="yaml-to-json">YAML → JSON</button><button class="secondary-button" id="json-to-yaml">JSON → YAML</button></div>`;
  if (tool === "timestamp") return `<div class="tool-head"><div><h3>时间戳转换</h3><p>自动识别秒或毫秒时间戳。</p></div></div><div class="tool-split"><label class="tool-field">输入<input id="timestamp-input" placeholder="例如 1710000000 或 2026-01-01"></label><label class="tool-field">输出<textarea id="timestamp-output" rows="8" readonly></textarea></label></div><div class="tool-actions"><button class="primary-button" id="timestamp-run">转换</button></div>`;
  if (tool === "jwt") return `<div class="tool-head"><div><h3>JWT 解析</h3><p>仅在本地解码 Header 和 Payload，不验证签名。</p></div></div><label class="tool-field">Token<textarea id="jwt-input" rows="5" placeholder="粘贴 JWT"></textarea></label><label class="tool-field">结果<textarea id="jwt-output" rows="8" readonly></textarea></label><div class="tool-actions"><button class="primary-button" id="jwt-run">解析</button></div>`;
  if (tool === "base64") return `<div class="tool-head"><div><h3>Base64 编解码</h3><p>适合处理 UTF-8 文本、配置片段和短令牌。</p></div><button class="secondary-button" data-tool-copy="base64-output">复制结果</button></div><div class="tool-split"><label class="tool-field">输入<textarea id="base64-input" rows="13" placeholder="输入要编码或解码的文本"></textarea></label><label class="tool-field">输出<textarea id="base64-output" rows="13" readonly placeholder="结果会显示在这里"></textarea></label></div><div class="tool-actions"><button class="primary-button" id="base64-encode">编码</button><button class="secondary-button" id="base64-decode">解码</button><button class="secondary-button" id="base64-clear">清空</button></div>`;
  if (tool === "url") return `<div class="tool-head"><div><h3>URL 编解码</h3><p>转换查询参数、路径片段和中文 URL。</p></div><button class="secondary-button" data-tool-copy="url-output">复制结果</button></div><div class="tool-split"><label class="tool-field">输入<textarea id="url-input" rows="13" placeholder="例如：个人工作台?tab=notes"></textarea></label><label class="tool-field">输出<textarea id="url-output" rows="13" readonly placeholder="结果会显示在这里"></textarea></label></div><div class="tool-actions"><button class="primary-button" id="url-encode">编码</button><button class="secondary-button" id="url-decode">解码</button><button class="secondary-button" id="url-clear">清空</button></div>`;
  if (tool === "regex") return `<div class="tool-head"><div><h3>正则测试</h3><p>即时查看匹配结果和捕获组，支持 JavaScript 正则标志。</p></div></div><div class="regex-controls"><label class="tool-field">表达式<input id="regex-pattern" placeholder="例如：(?<name>\\w+)@\\w+\\.com"></label><label class="tool-field regex-flags">标志<input id="regex-flags" value="g" placeholder="gim"></label></div><label class="tool-field">测试文本<textarea id="regex-input" rows="8" placeholder="粘贴要测试的文本"></textarea></label><div class="tool-actions"><button class="primary-button" id="regex-run">运行匹配</button><button class="secondary-button" id="regex-clear">清空</button></div><div id="regex-output" class="regex-output"><div class="empty">输入表达式和文本后运行匹配。</div></div>`;
  return `<div class="tool-head"><div><h3>JSON 格式化</h3><p>校验、格式化或压缩 JSON，错误位置会直接提示。</p></div><button class="secondary-button" data-tool-copy="json-output">复制结果</button></div><div class="tool-split"><label class="tool-field">输入<textarea id="json-input" rows="15" placeholder="粘贴 JSON 数据"></textarea></label><label class="tool-field">输出<textarea id="json-output" rows="15" readonly placeholder="格式化结果会显示在这里"></textarea></label></div><div class="tool-actions"><button class="primary-button" id="json-format">格式化</button><button class="secondary-button" id="json-minify">压缩</button><button class="secondary-button" id="json-clear">清空</button></div>`;
}

function bindYamlTool() { const input=$("#yaml-input"), output=$("#yaml-output"); $("#json-to-yaml").onclick=()=>{ try { const value=JSON.parse(input.value); output.value=Object.entries(value).map(([k,v])=>`${k}: ${typeof v === "object" ? JSON.stringify(v) : v}`).join("\n"); } catch(e) { output.value=`解析失败：${e.message}`; } }; $("#yaml-to-json").onclick=()=>{ try { const object={}; input.value.split(/\r?\n/).forEach((line)=>{ const i=line.indexOf(":"); if(i>0) object[line.slice(0,i).trim()]=line.slice(i+1).trim(); }); output.value=JSON.stringify(object,null,2); } catch(e) { output.value=`解析失败：${e.message}`; } }; }
function bindTimestampTool() { $("#timestamp-run").onclick=()=>{ const raw=$("#timestamp-input").value.trim(); const n=Number(raw); const d=Number.isFinite(n) && raw !== "" ? new Date(raw.length>10?n:n*1000) : new Date(raw); $("#timestamp-output").value=Number.isNaN(d.getTime())?"无法识别时间":`${d.toISOString()}\n${d.toLocaleString("zh-CN")}`; }; }
function bindJwtTool() { $("#jwt-run").onclick=()=>{ try { const parts=$("#jwt-input").value.trim().split("."); if(parts.length<2) throw new Error("JWT 至少包含两段"); const decode=(x)=>JSON.stringify(JSON.parse(new TextDecoder().decode(Uint8Array.from(atob(x.replace(/-/g,"+").replace(/_/g,"/")),c=>c.charCodeAt(0)))),null,2); $("#jwt-output").value=`Header:\n${decode(parts[0])}\n\nPayload:\n${decode(parts[1])}`; } catch(e) { $("#jwt-output").value=`解析失败：${e.message}`; } }; }

function bindJsonTool() {
  const input = $("#json-input");
  const output = $("#json-output");
  const transform = (space) => {
    try { output.value = JSON.stringify(JSON.parse(input.value), null, space); toast(space ? "JSON 格式化完成" : "JSON 已压缩"); }
    catch (error) { output.value = `解析失败：${error.message}`; toast("JSON 格式不正确", true); }
  };
  $("#json-format").addEventListener("click", () => transform(2));
  $("#json-minify").addEventListener("click", () => transform(0));
  $("#json-clear").addEventListener("click", () => { input.value = ""; output.value = ""; });
  bindToolCopy();
}

function bindBase64Tool() {
  const input = $("#base64-input");
  const output = $("#base64-output");
  $("#base64-encode").addEventListener("click", () => { try { output.value = encodeBase64(input.value); toast("Base64 编码完成"); } catch (error) { toast(error.message, true); } });
  $("#base64-decode").addEventListener("click", () => { try { const bytes = Uint8Array.from(atob(input.value.trim()), (char) => char.charCodeAt(0)); output.value = new TextDecoder().decode(bytes); toast("Base64 解码完成"); } catch (error) { output.value = "解码失败：请输入有效的 Base64"; toast("Base64 内容不正确", true); } });
  $("#base64-clear").addEventListener("click", () => { input.value = ""; output.value = ""; });
  bindToolCopy();
}

function encodeBase64(value) {
  const bytes = new TextEncoder().encode(value);
  let binary = "";
  for (let offset = 0; offset < bytes.length; offset += 0x8000) binary += String.fromCharCode(...bytes.subarray(offset, offset + 0x8000));
  return btoa(binary);
}

function bindUrlTool() {
  const input = $("#url-input");
  const output = $("#url-output");
  $("#url-encode").addEventListener("click", () => { output.value = encodeURIComponent(input.value); toast("URL 编码完成"); });
  $("#url-decode").addEventListener("click", () => { try { output.value = decodeURIComponent(input.value); toast("URL 解码完成"); } catch (_) { output.value = "解码失败：请输入有效的 URL 编码"; toast("URL 内容不正确", true); } });
  $("#url-clear").addEventListener("click", () => { input.value = ""; output.value = ""; });
  bindToolCopy();
}

function bindRegexTool() {
  const run = () => {
    const output = $("#regex-output");
    try {
      const expression = new RegExp($("#regex-pattern").value, $("#regex-flags").value);
      const text = $("#regex-input").value;
      const matches = expression.global ? [...text.matchAll(expression)] : (text.match(expression) ? [text.match(expression)] : []);
      output.innerHTML = matches.length ? `<div class="regex-summary">匹配到 ${matches.length} 处</div>${matches.map((match, index) => `<div class="regex-match"><b>#${index + 1}</b><code>${escapeHtml(match[0])}</code><span>位置 ${match.index ?? 0}${match.length > 1 ? ` · 捕获组 ${escapeHtml(match.slice(1).filter(Boolean).join(" / "))}` : ""}</span></div>`).join("")}` : '<div class="empty">没有匹配结果。</div>';
    } catch (error) { output.innerHTML = `<div class="empty">表达式错误：${escapeHtml(error.message)}</div>`; }
  };
  $("#regex-run").addEventListener("click", run);
  $("#regex-clear").addEventListener("click", () => { $("#regex-pattern").value = ""; $("#regex-input").value = ""; $("#regex-output").innerHTML = '<div class="empty">输入表达式和文本后运行匹配。</div>'; });
}

function bindToolCopy() {
  document.querySelectorAll("[data-tool-copy]").forEach((button) => button.addEventListener("click", async () => {
    const value = $("#" + button.dataset.toolCopy)?.value || "";
    if (!value) { toast("没有可复制的结果", true); return; }
    try { await navigator.clipboard.writeText(value); toast("结果已复制"); } catch (_) { toast("复制失败，请手动选择结果", true); }
  }));
}

async function renderSettings() {
  const url = new URL(state.baseUrl);
  const desktopBridge = window.workstationDesktop;
  let desktopConfig = null;
  if (desktopBridge) {
    try { desktopConfig = await desktopBridge.getServerConfig(); } catch (_) { /* browser mode or older desktop build */ }
  }
  let github = { username: "", token_configured: false };
  try { github = await api("/api/github/settings"); } catch (_) { /* older server without local settings */ }
  let profile = { display_name: "Liu Developer", github_username: github.username || "" };
  try { profile = await api("/api/profile"); } catch (_) { /* older server without profile settings */ }
  const localHost = ["127.0.0.1", "localhost", "::1"].includes(window.location.hostname);
  const configuredHost = desktopConfig?.host || url.hostname;
  const configuredPort = desktopConfig?.port || url.port || "8080";
  const lanUrlValues = desktopConfig?.lanUrls || [];
  const lanUrls = lanUrlValues.map((item) => `<span class="connection-address">${escapeHtml(item)}</span>`).join("");
  const qrAddress = lanUrlValues[0] || (localHost ? window.location.origin : "");
  const qrMarkup = qrAddress ? `<img class="connection-qr" src="${escapeHtml(`${state.baseUrl}/api/connect/qr?url=${encodeURIComponent(qrAddress)}`)}" alt="电脑连接二维码" loading="lazy"><small>用手机扫描二维码自动填写连接地址。</small>` : "";
  const listenerNote = desktopBridge
    ? `<div class="connection-note"><span class="connection-icon">⌁</span><div><strong>手机连接地址</strong><div class="connection-addresses">${lanUrls || '<span class="connection-empty">未检测到局域网地址</span>'}</div><small>手机和电脑连接同一 Wi-Fi 后，使用上面的地址。</small>${qrMarkup}</div></div><div class="settings-note">软件启动时会自动打开服务；保存监听设置后自动重启，关闭软件时服务也会停止。</div>`
    : `<div class="settings-note">这里用于连接已经运行的工作台服务。手机或其他电脑请填写运行服务电脑的局域网 IPv4 地址。</div>`;  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">LOCAL CONNECTION</div><h2>连接设置</h2><p>服务地址和 GitHub 凭据都保存在本机配置中。</p></div></div><div class="settings-grid"><div class="panel"><div class="panel-head"><h3>${desktopBridge ? "服务器监听" : "服务端地址"}</h3><span id="settings-status">${desktopBridge ? "运行中" : "未测试"}</span></div><form id="settings-form" class="settings-form"><label>${desktopBridge ? "监听 HOST" : "HOST"}<input name="host" value="${escapeHtml(configuredHost)}" placeholder="0.0.0.0"></label><label>PORT<input name="port" value="${escapeHtml(configuredPort)}" inputmode="numeric"></label>${listenerNote}<button class="primary-button">${desktopBridge ? "保存并重启服务" : "测试并保存"}</button></form></div><div class="panel"><div class="panel-head"><h3>GitHub 数据</h3><span id="github-settings-status">${github.token_configured ? "Token 已保存" : "未配置 Token"}</span></div><form id="github-settings-form" class="settings-form"><label>用户名<input name="username" value="${escapeHtml(github.username)}" placeholder="例如 octocat" required></label><label>Token<input name="token" type="password" autocomplete="off" placeholder="${github.token_configured ? "留空保留当前 Token" : "可选，公开数据可不填"}"></label><div class="settings-note">只允许在运行服务的电脑上保存。配置文件不会放进 Web 目录，也不会上传。</div><button class="primary-button" ${localHost ? "" : "disabled"}>保存 GitHub 设置</button></form></div></div><div class="panel"><div class="panel-head"><h3>当前能力</h3><span>v0.1.0</span></div><div class="progress-row"><span>REST API</span><strong class="delta">已连接</strong></div><div class="progress-row"><span>SQLite 数据</span><strong class="delta">本地</strong></div><div class="progress-row"><span>WebSocket</span><strong class="delta">已就绪</strong></div><div class="progress-row"><span>GitHub 缓存</span><strong class="delta">按需刷新</strong></div></div>`;
  const settingsGrid = content.querySelector(".settings-grid");
  settingsGrid?.insertAdjacentHTML("beforeend", `<div class="panel"><div class="panel-head"><h3>个人资料</h3><span id="profile-status">已同步</span></div><form id="profile-form" class="settings-form"><label>显示名称<input name="display_name" value="${escapeHtml(profile.display_name)}" maxlength="100" required></label><label>GitHub 用户名<input name="github_username" value="${escapeHtml(profile.github_username || github.username)}" maxlength="100" placeholder="可选"></label><div class="settings-note">保存后手机端连接此电脑时会自动同步。</div><button class="primary-button">保存个人资料</button></form></div>`);
  settingsGrid?.insertAdjacentHTML("beforeend", '<div class="panel"><div class="panel-head"><h3>数据备份</h3><span>本机保存</span></div><div class="settings-note">导出 SQLite 可完整恢复；JSON 便于查看或迁移。恢复前会自动创建安全备份。</div><div class="backup-actions"><button id="backup-json" class="secondary-button">导出 JSON</button><button id="backup-sqlite" class="secondary-button">导出 SQLite</button></div><label class="backup-file">恢复文件<input id="backup-file" type="file" accept=".json,.db,application/json,application/x-sqlite3"></label><label>恢复方式<select id="backup-mode"><option value="replace">替换当前数据</option><option value="merge">合并 JSON 数据</option></select></label><button id="backup-import" class="primary-button">恢复备份</button></div>');
  const capabilityPanel = content.querySelector(".settings-grid + .panel");
  if (capabilityPanel && !$("#export-data")) capabilityPanel.insertAdjacentHTML("beforeend", '<div class="settings-export"><span>数据备份</span><button id="export-data" class="secondary-button">导出 JSON</button></div>');
  $("#export-data")?.addEventListener("click", exportLocalData);
  $("#backup-json")?.addEventListener("click", () => downloadServerBackup("json"));
  $("#backup-sqlite")?.addEventListener("click", () => downloadServerBackup("sqlite"));
  $("#backup-import")?.addEventListener("click", importServerBackup);
  if (!localHost) toast("请在运行服务的电脑上用 127.0.0.1 打开设置页", true);
  $("#settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const host = String(form.get("host") || "").trim();
    const port = Number(form.get("port"));
    try {
      if (desktopBridge) {
        const result = await desktopBridge.saveServerConfig({ host, port });
        state.baseUrl = result.localUrl;
        window.localStorage.setItem("workstationBaseUrl", result.localUrl);
        startRealtime();
        $("#settings-status").textContent = "已重启";
        setServerStatus(true, "服务已连接");
        toast("监听设置已保存，服务已重启");
      } else {
        const nextUrl = `http://${host}:${port}`;
        const result = await apiWithBase(nextUrl, "/api/health");
        state.baseUrl = nextUrl;
        window.localStorage.setItem("workstationBaseUrl", nextUrl);
        startRealtime();
        $("#settings-status").textContent = "已连接";
        setServerStatus(true, "服务已连接");
        toast(result.message || "连接成功");
      }
    } catch (error) {
      $("#settings-status").textContent = "连接失败";
      setServerStatus(false, "服务未连接");
      toast(error.message, true);
    }
  });
  $("#github-settings-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    try {
      const response = await fetch(`${window.location.origin}/api/github/settings`, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username: String(form.get("username") || "").trim(), token: String(form.get("token") || "").trim() || null }) });
      if (!response.ok) { let detail = `${response.status} ${response.statusText}`; try { detail = (await response.json()).detail || detail; } catch (_) {} throw new Error(detail); }
      const result = await response.json();
      $("#github-settings-status").textContent = result.token_configured ? "Token 已保存" : "仅用户名";
      formElement.elements.token.value = "";
      toast("GitHub 设置已保存到本机");
      await loadGithubProfile();
    } catch (error) { toast(error.message, true); }
  });
  $("#profile-form")?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const result = await api("/api/profile", { method: "PUT", body: JSON.stringify({ display_name: String(form.get("display_name") || "").trim(), github_username: String(form.get("github_username") || "").trim() }) });
      $("#profile-status").textContent = "已保存";
      const sidebarName = $("#sidebar-name");
      if (sidebarName) sidebarName.textContent = result.display_name;
      toast("个人资料已保存，手机端会自动同步");
    } catch (error) { $("#profile-status").textContent = "保存失败"; toast(error.message, true); }
  });
}
async function apiWithBase(base, path) {
  const response = await fetch(`${base}${path}`);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

async function exportLocalData() {
  const button = $("#export-data");
  if (button) button.disabled = true;
  try {
    const boards = await api("/api/boards");
    const boardData = [];
    for (const board of boards) boardData.push({ board, columns: await api(`/api/boards/${board.id}/tasks`) });
    const [notes, repos, activity] = await Promise.all([api("/api/notes"), api("/api/github/repos"), api("/api/github/activity?limit=100")]);
    const payload = { format: "personal-workstation-backup", version: 1, exported_at: new Date().toISOString(), boards: boardData, notes, github: { repos, activity } };
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `personal-workstation-backup-${new Date().toISOString().slice(0, 10)}.json`;
    link.click();
    URL.revokeObjectURL(url);
    toast("本地数据已导出");
  } catch (error) { toast(error.message, true); }
  finally { if (button) button.disabled = false; }
}

async function downloadServerBackup(format) {
  const button = format === "json" ? $("#backup-json") : $("#backup-sqlite");
  if (button) button.disabled = true;
  try {
    const response = await fetch(`${state.baseUrl}/api/backup/export?format=${format}`, { method: "POST" });
    if (!response.ok) throw new Error((await response.json().catch(() => ({}))).detail || "导出失败");
    const blob = await response.blob();
    const name = response.headers.get("content-disposition")?.match(/filename="?([^";]+)"?/i)?.[1] || `workstation_backup.${format === "sqlite" ? "db" : "json"}`;
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url; link.download = name; link.click(); URL.revokeObjectURL(url);
    toast(`${format === "sqlite" ? "SQLite" : "JSON"} 备份已导出`);
  } catch (error) { toast(error.message, true); }
  finally { if (button) button.disabled = false; }
}

async function importServerBackup() {
  const input = $("#backup-file");
  const mode = $("#backup-mode")?.value || "replace";
  const file = input?.files?.[0];
  if (!file) { toast("请先选择备份文件", true); return; }
  if (!window.confirm(`将以“${mode === "replace" ? "替换" : "合并"}”方式恢复 ${file.name}。恢复前会自动备份当前数据，是否继续？`)) return;
  const button = $("#backup-import");
  if (button) button.disabled = true;
  try {
    const body = new FormData(); body.append("file", file);
    const response = await fetch(`${state.baseUrl}/api/backup/import?mode=${mode}`, { method: "POST", body });
    if (!response.ok) throw new Error((await response.json().catch(() => ({}))).detail || "恢复失败");
    const result = await response.json();
    toast(`恢复完成，安全备份：${result.safety_backup}`);
    await navigate("settings");
  } catch (error) { toast(error.message, true); }
  finally { if (button) button.disabled = false; }
}

function openQuick(kind) {
  if (kind === "task") openNewTaskDialog();
  else if (kind === "note") {
    state.noteEditingId = null;
    $("#note-form").reset();
    $("#note-dialog-eyebrow").textContent = "NEW NOTE";
    $("#note-dialog-title").textContent = "新建笔记";
    $("#note-submit").textContent = "保存笔记";
    $("#note-dialog").showModal();
    initNoteEditor();
  }
  else toast("请从侧边栏选择具体页面");
}

async function loadTaskColumns() {
  if (state.columns.length) return state.columns;
  const boards = await api("/api/boards");
  if (!boards.length) throw new Error("请先创建任务看板");
  state.boardId = boards[0].id;
  state.columns = await api("/api/boards/" + state.boardId + "/tasks");
  return state.columns;
}

function fillTaskColumns(selectedId) {
  const select = $("#task-column");
  select.innerHTML = state.columns.map((column) => "<option value=\"" + column.id + "\">" + escapeHtml(column.name) + "</option>").join("");
  if (selectedId) select.value = String(selectedId);
}

async function openNewTaskDialog() {
  try {
    await loadTaskColumns();
    state.taskEditingId = null;
    $("#task-form").reset();
    fillTaskColumns(state.columns[0]?.id);
    $("#task-dialog-eyebrow").textContent = "NEW TASK";
    $("#task-dialog-title").textContent = "新建任务";
    $("#task-submit").textContent = "创建任务";
    $("#task-dialog").showModal();
  } catch (error) { toast(error.message, true); }
}

function openTaskEditor(task) {
  state.taskEditingId = task.id;
  const form = $("#task-form");
  form.elements.title.value = task.title || "";
  form.elements.description.value = task.description || "";
  form.elements.priority.value = task.priority || "medium";
  form.elements.due_date.value = task.due_date || "";
  fillTaskColumns(task.column_id);
  $("#task-dialog-eyebrow").textContent = "EDIT TASK";
  $("#task-dialog-title").textContent = "编辑任务";
  $("#task-submit").textContent = "更新任务";
  $("#task-dialog").showModal();
}

function openNoteEditor(note) {
  state.noteEditingId = note.id;
  const form = $("#note-form");
  form.elements.title.value = note.title || "";
  form.elements.content.value = note.content || "";
  form.elements.tags.value = (note.tags || []).join(", ");
  $("#note-dialog-eyebrow").textContent = "EDIT NOTE";
  $("#note-dialog-title").textContent = "编辑笔记";
  $("#note-submit").textContent = "更新笔记";
  $("#note-dialog").showModal();
  initNoteEditor();
}

// 笔记编辑/预览三态切换 + 实时预览。模式：edit / preview / split。
function initNoteEditor(mode = "edit") {
  const dialog = $("#note-dialog");
  if (!dialog) return;
  const textarea = dialog.querySelector('textarea[name="content"]');
  const preview = dialog.querySelector(".note-preview");
  if (!textarea || !preview) return;

  const render = () => { preview.innerHTML = renderMarkdown(textarea.value) || '<span class="muted">预览区为空</span>'; };
  const applyMode = (next) => {
    state.noteMode = next;
    dialog.querySelectorAll(".note-mode").forEach((btn) => btn.classList.toggle("active", btn.dataset.noteMode === next));
    dialog.dataset.noteMode = next;
    render();
  };

  if (!textarea.dataset.bound) {
    textarea.dataset.bound = "1";
    textarea.addEventListener("input", render);
  }
  dialog.querySelectorAll(".note-mode").forEach((btn) => {
    if (!btn.dataset.bound) {
      btn.dataset.bound = "1";
      btn.addEventListener("click", () => applyMode(btn.dataset.noteMode));
    }
  });
  applyMode(mode);
}

function bindViewButtons() {
  document.querySelectorAll("#content [data-view]").forEach((button) => button.addEventListener("click", () => navigate(button.dataset.view)));
}

document.querySelectorAll(".main-nav").forEach((nav) => nav.addEventListener("click", (event) => { const button = event.target.closest("[data-view]"); if (button) navigate(button.dataset.view); }));
document.querySelectorAll("[data-dialog-close]").forEach((button) => button.addEventListener("click", () => document.getElementById(button.dataset.dialogClose)?.close()));
$("#refresh-btn").addEventListener("click", () => navigate(state.view));
$("#sidebar-refresh").addEventListener("click", () => navigate(state.view));
$("#quick-add").addEventListener("click", () => openQuick(state.view === "notes" ? "note" : "task"));

$("#task-form").addEventListener("submit", async (event) => {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const formElement = event.currentTarget;
    const form = new FormData(formElement);
  try {
    const columns = await loadTaskColumns();
    if (!columns.length) throw new Error("请先创建任务列");
    const payload = { column_id: Number(form.get("column_id") || columns[0].id), title: form.get("title"), description: form.get("description"), priority: form.get("priority"), due_date: form.get("due_date") || null };
    const editing = Boolean(state.taskEditingId);
    await api(editing ? "/api/tasks/" + state.taskEditingId : "/api/tasks", { method: editing ? "PUT" : "POST", body: JSON.stringify(payload) });
    state.taskEditingId = null;
    event.currentTarget.reset(); $("#task-dialog").close(); toast(editing ? "任务已更新" : "任务已创建");
    if (state.view === "kanban") await renderKanban();
  } catch (error) { toast(error.message, true); }
});

$("#board-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  try {
    const board = await api("/api/boards", { method: "POST", body: JSON.stringify({ name: form.get("name") }) });
    state.boardId = board.id;
    event.currentTarget.reset(); $("#board-dialog").close(); toast("看板已创建");
    if (state.view === "kanban") await renderKanban();
  } catch (error) { toast(error.message, true); }
});

$("#column-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.currentTarget);
  try {
    await api("/api/boards/" + state.boardId + "/columns", { method: "POST", body: JSON.stringify({ name: form.get("name") }) });
    event.currentTarget.reset(); $("#column-dialog").close(); toast("任务列已创建");
    if (state.view === "kanban") await renderKanban();
  } catch (error) { toast(error.message, true); }
});

$("#note-form").addEventListener("submit", async (event) => {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const formElement = event.currentTarget;
    const form = new FormData(formElement);
  try {
    const payload = { title: form.get("title"), content: form.get("content"), tags: String(form.get("tags") || "").split(",").map((tag) => tag.trim()).filter(Boolean) };
    await api(state.noteEditingId ? `/api/notes/${state.noteEditingId}` : "/api/notes", { method: state.noteEditingId ? "PUT" : "POST", body: JSON.stringify(payload) });
    state.noteEditingId = null;
    event.currentTarget.reset(); $("#note-dialog").close(); toast("笔记已保存");
    if (state.view === "notes") await renderNotes();
  } catch (error) { toast(error.message, true); }
});

$("#confirm-form").addEventListener("submit", (event) => {
  event.preventDefault();
  $("#confirm-dialog").close();
  const resolve = confirmResolver;
  confirmResolver = null;
  resolve?.(true);
});
$("#confirm-cancel").addEventListener("click", () => {
  $("#confirm-dialog").close();
  const resolve = confirmResolver;
  confirmResolver = null;
  resolve?.(false);
});


async function loadGithubProfile() {
  try {
    const local = await api("/api/profile").catch(() => ({ display_name: "", github_username: "" }));
    const profile = await api("/api/github/profile").catch(() => ({}));
    const login = profile?.login || local.github_username || "";
    const displayName = local.display_name || profile?.name || login;
    if (!login && !displayName) return;
    const avatar = $("#sidebar-avatar");
    const name = $("#sidebar-name");
    const subtitle = $("#sidebar-subtitle");
    if (avatar) {
      avatar.textContent = (login || displayName || "G").slice(0, 1).toUpperCase();
      if (profile.avatar_url) {
        avatar.style.backgroundImage = `url("${String(profile.avatar_url).replace(/"/g, "%22")}")`;
        avatar.classList.add("has-image");
      }
    }
    if (name) name.textContent = displayName;
    if (subtitle) subtitle.textContent = login ? `@${login}` : "本地工作区";
    const greeting = $("#dashboard-greeting");
    if (greeting) greeting.textContent = `下午好，${displayName}`;
  } catch (_) {
    // GitHub profile is optional; the local fallback remains visible.
  }
}
async function boot() {
  try { await api("/api/health"); setServerStatus(true, "服务已连接"); } catch (_) { setServerStatus(false, "服务未连接"); }
  startRealtime();
  loadGithubProfile();
  await navigate("dashboard");
  initScratchpad();
}

function initScratchpad() {
  const panel = $("#scratchpad"), input = $("#scratch-content"); if (!panel || !input) return;
  input.value = localStorage.getItem("scratchpad_content") || "";
  $("#scratch-toggle").onclick = () => panel.classList.toggle("collapsed");
  input.oninput = () => localStorage.setItem("scratchpad_content", input.value);
  $("#scratch-copy").onclick = async () => { await navigator.clipboard.writeText(input.value); toast("便签已复制"); };
  $("#scratch-time").onclick = () => { const stamp = new Date().toLocaleString("zh-CN"); const start=input.selectionStart; input.value=input.value.slice(0,start)+stamp+input.value.slice(input.selectionEnd); input.selectionStart=input.selectionEnd=start+stamp.length; input.dispatchEvent(new Event("input")); };
  $("#scratch-clear").onclick = async () => { if (await confirmAction("清空便签内容？", "清空快捷便签")) { input.value=""; input.dispatchEvent(new Event("input")); } };
  $("#scratch-note").onclick = async () => { if (!input.value.trim()) return; await api("/api/notes", { method:"POST", body: JSON.stringify({ title:`便签 ${new Date().toLocaleDateString("zh-CN")}`, content:input.value, tags:["便签"] }) }); input.value=""; input.dispatchEvent(new Event("input")); toast("已转为笔记"); };
  document.addEventListener("keydown", (event) => { if (event.ctrlKey && event.shiftKey && event.key.toLowerCase() === "p") { event.preventDefault(); panel.classList.toggle("collapsed"); } });
}

boot();
