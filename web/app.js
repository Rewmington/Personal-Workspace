const state = {
  view: "dashboard",
  baseUrl: window.localStorage.getItem("workstationBaseUrl") || window.location.origin,
  boardId: null,
  columns: [],
  kanbanUndo: [],
};

if (window.workstationDesktop) state.baseUrl = window.location.origin;

const $ = (selector) => document.querySelector(selector);
const content = $("#content");

function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>'"]/g, (char) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" })[char]);
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
  const titles = { dashboard: "仪表盘", kanban: "任务看板", notes: "笔记", github: "GitHub", settings: "连接设置" };
  $("#page-title").textContent = titles[view] || "仪表盘";
  showLoading();
  try {
    if (view === "dashboard") await renderDashboard();
    if (view === "kanban") await renderKanban();
    if (view === "notes") await renderNotes();
    if (view === "github") await renderGithub();
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
  const boards = await api("/api/boards");
  if (!boards.length) throw new Error("还没有创建看板");
  state.boardId = boards[0].id;
  state.columns = await api(`/api/boards/${state.boardId}/tasks`);
  renderKanbanMarkup();
}

function renderKanbanMarkup(filter = "") {
  const normalized = filter.trim().toLowerCase();
  const columns = state.columns.map((column) => {
    const tasks = column.tasks.filter((task) => !normalized || `${task.title} ${task.description}`.toLowerCase().includes(normalized));
    return `<section class="kanban-column"><div class="column-head"><strong>${escapeHtml(column.name)}</strong><span class="count">${tasks.length}</span></div><div class="task-list">${tasks.length ? tasks.map((task) => taskMarkup(task)).join("") : '<div class="empty">暂无任务</div>'}</div></section>`;
  }).join("");
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">SPRINT BOARD</div><h2>${escapeHtml(state.columns[0]?.board_name || "个人工作台")}</h2><p>把今天的工作保持在一个清晰的节奏里。</p></div><div class="kanban-toolbar"><input id="task-search" class="search" placeholder="搜索任务…" value="${escapeHtml(filter)}">${state.kanbanUndo.length ? `<button id="kanban-undo" class="icon-button" title="撤销上一步" aria-label="撤销上一步">↶</button>` : ""}<button id="kanban-add" class="primary-button">＋ 新建任务</button></div></div><div class="kanban-columns">${columns}</div>`;
  $("#task-search").addEventListener("input", (event) => renderKanbanMarkup(event.target.value));
  $("#kanban-add").addEventListener("click", () => openQuick("task"));
  $("#kanban-undo")?.addEventListener("click", () => taskAction("undo", 0));
  document.querySelectorAll("[data-task-action]").forEach((button) => button.addEventListener("click", () => taskAction(button.dataset.taskAction, Number(button.dataset.taskId))));
}

function taskMarkup(task) {
  return `<article class="task-card ${escapeHtml(task.priority)}"><h4>${escapeHtml(task.title)}</h4><p>${escapeHtml(task.description || "暂无描述")}</p><div class="task-foot"><span>${escapeHtml(task.priority === "high" ? "高优先级" : task.priority === "low" ? "低优先级" : "中优先级")}</span><div class="task-actions"><button class="small-button" data-task-action="move-left" data-task-id="${task.id}" title="移动到上一列" aria-label="移动到上一列">←</button><button class="small-button" data-task-action="move-right" data-task-id="${task.id}" title="移动到下一列" aria-label="移动到下一列">→</button><button class="small-button danger" data-task-action="delete" data-task-id="${task.id}" title="删除任务" aria-label="删除任务">×</button></div></div></article>`;
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
    if (action === "delete") {
      if (!window.confirm(`确认删除“${task.title}”？`)) return;
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
  $("#note-add").addEventListener("click", () => openQuick("note"));
  $("#note-search").addEventListener("input", async (event) => {
    const query = event.target.value.trim();
    const filtered = query ? await api(`/api/notes?search=${encodeURIComponent(query)}`) : notes;
    $("#notes-grid").innerHTML = filtered.length ? filtered.map(noteMarkup).join("") : '<div class="empty">没有匹配的笔记。</div>';
  });
}

function noteMarkup(note) {
  const tags = (note.tags || []).map((tag) => `<span class="tag">${escapeHtml(tag)}</span>`).join("");
  return `<article class="note-card"><h3>${escapeHtml(note.title)}</h3><p>${escapeHtml(note.content || "暂无内容")}</p><div class="tags">${tags || '<span class="tag">未分类</span>'}</div><div class="repo-meta"><span>${formatDate(note.updated_at)}</span><span>#${note.id}</span></div></article>`;
}

async function renderGithub() {
  const [repos, activity] = await Promise.all([api("/api/github/repos"), api("/api/github/activity?limit=20")]);
  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">OPEN SOURCE ACTIVITY</div><h2>GitHub 追踪</h2><p>服务端缓存 ${repos.length} 个仓库，${activity.length} 条近期活动。</p></div><button id="github-refresh" class="primary-button">刷新 GitHub</button></div><div class="github-layout"><div class="panel"><div class="panel-head"><h3>近期活动</h3><span>${activity.length} 条</span></div><div class="activity-list">${activity.length ? activity.map((item) => `<div class="activity"><div class="mini-avatar">${escapeHtml((item.actor || "G").slice(0, 1).toUpperCase())}</div><div><strong>${escapeHtml(item.actor || "GitHub")}</strong><p>${escapeHtml(eventLabel(item.type))}</p><time>${formatDate(item.created_at)}</time></div></div>`).join("") : '<div class="empty">暂无缓存活动。</div>'}</div></div><div class="panel"><div class="panel-head"><h3>仓库列表</h3><span>${repos.length} 个</span></div><div class="repo-grid">${repos.length ? repos.map((repo) => `<article class="repo-card"><h3>${escapeHtml(repo.name)}</h3><p>${escapeHtml(repo.description || "暂无描述")}</p><div class="repo-meta"><span>${escapeHtml(repo.language || "未知语言")}</span><span>★ ${repo.stars}</span><a class="repo-link" href="${escapeHtml(repo.html_url || "#")}" target="_blank" rel="noreferrer">打开仓库 ↗</a></div></article>`).join("") : '<div class="empty">请先在服务端配置 GitHub 用户名。</div>'}</div></div></div>`;
  $("#github-refresh").addEventListener("click", async () => {
    const button = $("#github-refresh");
    button.disabled = true;
    try { await api("/api/github/refresh", { method: "POST", body: "{}" }); toast("GitHub 数据已刷新"); await renderGithub(); } catch (error) { toast(error.message, true); } finally { button.disabled = false; }
  });
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
  const localHost = ["127.0.0.1", "localhost", "::1"].includes(window.location.hostname);
  const configuredHost = desktopConfig?.host || url.hostname;
  const configuredPort = desktopConfig?.port || url.port || "8080";
  const lanUrls = (desktopConfig?.lanUrls || []).map((item) => `<span class="connection-address">${escapeHtml(item)}</span>`).join("");
  const listenerNote = desktopBridge
    ? `<div class="connection-note"><span class="connection-icon">⌁</span><div><strong>手机连接地址</strong><div class="connection-addresses">${lanUrls || '<span class="connection-empty">未检测到局域网地址</span>'}</div><small>手机和电脑连接同一 Wi-Fi 后，使用上面的地址。</small></div></div><div class="settings-note">软件启动时会自动打开服务；保存监听设置后自动重启，关闭软件时服务也会停止。</div>`
    : `<div class="settings-note">这里用于连接已经运行的工作台服务。手机或其他电脑请填写运行服务电脑的局域网 IPv4 地址。</div>`;  content.innerHTML = `<div class="view-head"><div><div class="eyebrow">LOCAL CONNECTION</div><h2>连接设置</h2><p>服务地址和 GitHub 凭据都保存在本机配置中。</p></div></div><div class="settings-grid"><div class="panel"><div class="panel-head"><h3>${desktopBridge ? "服务器监听" : "服务端地址"}</h3><span id="settings-status">${desktopBridge ? "运行中" : "未测试"}</span></div><form id="settings-form" class="settings-form"><label>${desktopBridge ? "监听 HOST" : "HOST"}<input name="host" value="${escapeHtml(configuredHost)}" placeholder="0.0.0.0"></label><label>PORT<input name="port" value="${escapeHtml(configuredPort)}" inputmode="numeric"></label>${listenerNote}<button class="primary-button">${desktopBridge ? "保存并重启服务" : "测试并保存"}</button></form></div><div class="panel"><div class="panel-head"><h3>GitHub 数据</h3><span id="github-settings-status">${github.token_configured ? "Token 已保存" : "未配置 Token"}</span></div><form id="github-settings-form" class="settings-form"><label>用户名<input name="username" value="${escapeHtml(github.username)}" placeholder="例如 octocat" required></label><label>Token<input name="token" type="password" autocomplete="off" placeholder="${github.token_configured ? "留空保留当前 Token" : "可选，公开数据可不填"}"></label><div class="settings-note">只允许在运行服务的电脑上保存。配置文件不会放进 Web 目录，也不会上传。</div><button class="primary-button" ${localHost ? "" : "disabled"}>保存 GitHub 设置</button></form></div></div><div class="panel"><div class="panel-head"><h3>当前能力</h3><span>v0.1.0</span></div><div class="progress-row"><span>REST API</span><strong class="delta">已连接</strong></div><div class="progress-row"><span>SQLite 数据</span><strong class="delta">本地</strong></div><div class="progress-row"><span>WebSocket</span><strong class="delta">已就绪</strong></div><div class="progress-row"><span>GitHub 缓存</span><strong class="delta">按需刷新</strong></div></div>`;
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
        $("#settings-status").textContent = "已重启";
        setServerStatus(true, "服务已连接");
        toast("监听设置已保存，服务已重启");
      } else {
        const nextUrl = `http://${host}:${port}`;
        const result = await apiWithBase(nextUrl, "/api/health");
        state.baseUrl = nextUrl;
        window.localStorage.setItem("workstationBaseUrl", nextUrl);
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
}
async function apiWithBase(base, path) {
  const response = await fetch(`${base}${path}`);
  if (!response.ok) throw new Error(`${response.status} ${response.statusText}`);
  return response.json();
}

function openQuick(kind) {
  if (kind === "task") $("#task-dialog").showModal();
  else if (kind === "note") $("#note-dialog").showModal();
  else toast("请从侧边栏选择具体页面");
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
    const columns = state.columns.length ? state.columns : await api(`/api/boards/${state.boardId || 1}/tasks`);
    if (!columns.length) throw new Error("请先创建任务列");
    await api("/api/tasks", { method: "POST", body: JSON.stringify({ column_id: columns[0].id, title: form.get("title"), description: form.get("description"), priority: form.get("priority") }) });
    event.currentTarget.reset(); $("#task-dialog").close(); toast("任务已创建");
    if (state.view === "kanban") await renderKanban();
  } catch (error) { toast(error.message, true); }
});

$("#note-form").addEventListener("submit", async (event) => {
  if (event.submitter?.value === "cancel") return;
  event.preventDefault();
  const formElement = event.currentTarget;
    const form = new FormData(formElement);
  try {
    await api("/api/notes", { method: "POST", body: JSON.stringify({ title: form.get("title"), content: form.get("content"), tags: String(form.get("tags") || "").split(",").map((tag) => tag.trim()).filter(Boolean) }) });
    event.currentTarget.reset(); $("#note-dialog").close(); toast("笔记已保存");
    if (state.view === "notes") await renderNotes();
  } catch (error) { toast(error.message, true); }
});


async function loadGithubProfile() {
  try {
    const profile = await api("/api/github/profile");
    if (!profile?.login) return;
    const avatar = $("#sidebar-avatar");
    const name = $("#sidebar-name");
    const subtitle = $("#sidebar-subtitle");
    if (avatar) {
      avatar.textContent = (profile.login || "G").slice(0, 1).toUpperCase();
      if (profile.avatar_url) {
        avatar.style.backgroundImage = `url("${String(profile.avatar_url).replace(/"/g, "%22")}")`;
        avatar.classList.add("has-image");
      }
    }
    if (name) name.textContent = profile.name || profile.login;
    if (subtitle) subtitle.textContent = `@${profile.login}`;
    const greeting = $("#dashboard-greeting");
    if (greeting) greeting.textContent = `下午好，${profile.name || profile.login}`;
  } catch (_) {
    // GitHub profile is optional; the local fallback remains visible.
  }
}
async function boot() {
  try { await api("/api/health"); setServerStatus(true, "服务已连接"); } catch (_) { setServerStatus(false, "服务未连接"); }
  loadGithubProfile();
  await navigate("dashboard");
}

boot();








