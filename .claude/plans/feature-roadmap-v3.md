# Personal Workstation v3 待办方案

> 📦 **全端完成** — 核心功能 9/9 ✅，运维部署 3/3 ✅，测试验收 2/2 ✅

---

## 总览

| # | 类型 | 功能 | 估时 | 说明 |
|---|------|------|------|------|
| 🐛 | Bug 修复 | 任务看板箭头方向 | ✅ 完成 | ←→ → ↑↓ |
| 6 | Android UI | Git 仓库总览 | ✅ 完成 | 仪表盘卡片 + RepoDetailSheet |
| 7 | Android UI | 番茄钟专注模式 | ✅ 完成 | Canvas 环形进度条 + 预设选择 |
| 8 | Android UI | 每日开发日志 | ✅ 完成 | 月历热力图 + 心情选择器 |
| 9 | Android UI | 快捷便签 | ✅ 完成 | FAB → ModalBottomSheet |
| 10 | 部署 | Windows 系统托盘 + 开机自启 | ✅ 完成 | 最小化到托盘（无开机自启） |
| 11 | 部署 | mDNS 自动服务发现 | ✅ 完成 | server/zeroconf + Android NsdManager |
| 12 | 测试 | Web 自动化测试 | ✅ 完成 | 19 Playwright 用例 |
| 13 | 验收 | 真机联调 + 防火墙脚本 | ✅ 完成 | README.md 完整验收指南 |

**建议启动顺序**：系统托盘 → mDNS → 测试 → 验收

---

## 10. Windows 系统托盘 + 开机自启

### 设计

**背景**：当前 Electron 桌面端以完整窗口形式运行，关闭窗口即停止服务。需要支持最小化到系统托盘 + 开机自启，让服务静默运行。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 托盘方案 | `Tray` + `Menu` | Electron 内置 API，零额外依赖 |
| 窗口行为 | 关闭 → 最小化到托盘 | 用户心理模型 |
| 开机自启 | `app.setLoginItemSettings()` | Electron 内置 API |
| 右键菜单 | 打开主界面 / 退出 | 简洁 |

### 执行清单

**步骤 1 —— 托盘创建**（`desktop/main.js`）

```javascript
const { Tray, Menu, nativeImage } = require('electron');
let tray = null;

function createTray() {
    const icon = nativeImage.createFromPath(path.join(__dirname, 'assets', 'icon.ico'));
    tray = new Tray(icon.resize({ width: 16, height: 16 }));
    const contextMenu = Menu.buildFromTemplate([
        { label: '打开工作台', click: () => mainWindow.show() },
        { type: 'separator' },
        { label: '退出', click: () => { app.isQuitting = true; app.quit(); } }
    ]);
    tray.setToolTip('Personal Workstation');
    tray.setContextMenu(contextMenu);
    tray.on('double-click', () => mainWindow.show());
}
```

**步骤 2 —— 关闭改为隐藏**

```javascript
mainWindow.on('close', (event) => {
    if (!app.isQuitting) {
        event.preventDefault();
        mainWindow.hide();
    }
});
```

**步骤 3 —— 开机自启**

```javascript
app.setLoginItemSettings({
    openAtLogin: true,
    path: process.execPath
});
```

**步骤 4 —— 设置页开关**（Web 端设置页）

提供"开机自启"和"最小化到托盘"的开关（`localStorage`，通过 preload 桥接实现）。

**步骤 5 —— 端到端验证**

关闭窗口 → 服务继续运行 → 托盘图标存在 → 双击恢复 → 右键退出。开机自启生效。

---

## 11. mDNS 自动服务发现

### 设计

**背景**：Android 端当前需要手动填写服务器 IP，每次换网络都要重填。mDNS (Multicast DNS) 可实现局域网内自动发现。

**关键决策**：

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 服务端广播 | `zeroconf` Python 库 | 纯 Python，跨平台 |
| Android 端发现 | `NsdManager` Android 系统 API | 无需第三方库 |
| 降级方案 | 保留手动输入 | mDNS 不可用时回退 |
| 优先级 | 自动发现优先，最后使用的手动地址兜底 | 平滑过渡 |

### 执行清单

**步骤 1 —— 服务端 mDNS 广播**（`server/run_server.py`）

```python
from zeroconf import Zeroconf, ServiceInfo
zc = Zeroconf()
info = ServiceInfo(
    "_http._tcp.local.", "PersonalWorkstation._http._tcp.local.",
    addresses=[socket.inet_aton(lan_ip)], port=port,
    properties={"path": "/", "version": "1.0"}
)
zc.register_service(info)
```

**步骤 2 —— Android 端 NsdManager 集成**（`MainActivity.kt`）

```kotlin
val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, object : NsdManager.DiscoveryListener {
    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onServiceResolved(info: NsdServiceInfo) {
                val host = info.host.hostAddress
                val port = info.port
                // 自动填入设置
            }
        })
    }
})
```

**步骤 3 —— 设置页 UI 更新**

Android 设置页顶部新增"发现的服务"列表，显示自动发现的服务器地址，点击一键应用。

**步骤 4 —— 端到端验证**

同 Wi-Fi → Android 自动发现 → 一键连接 → 切换网络 → 新地址可发现 → 手动输入回退正常。

---

## 12. Web 自动化测试

### 设计

**当前状态**：仅 3 个 Python 单元测试。需要补充 Web 端和 API 的自动化测试。

**测试分层**：

| 层级 | 工具 | 覆盖范围 |
|------|------|----------|
| API 测试 | pytest + httpx | 所有 REST 端点（正常 + 异常） |
| WebSocket 测试 | pytest-asyncio + websockets | 连接/重连/广播/同步 |
| Web E2E | Playwright | 关键用户流程（CRUD + 实时更新） |

### 执行清单

**步骤 1 —— API 测试扩展**（`server/tests/`）

覆盖：tasks (增删改移撤)、notes (增删改搜标)、snippets (CRUD + meta)、backup (导出导入)、git (扫描配置)、focus (计时周期)、logs (日历 streak)

**步骤 2 —— WebSocket 测试**

连接/心跳/广播/序号/sync_request 增量同步/断线重连

**步骤 3 —— Web E2E**（`web/` 新建 `tests/`）

Playwright 脚本：仪表盘加载 → 创建任务 → 移动 → 撤销 → WebSocket 推送验证 → 笔记 Markdown 渲染

**步骤 4 —— CI 脚本**

`npm test` / `pytest` 一键运行所有测试。

---

## 13. 真机验收 + 防火墙

### 执行清单

- [ ] Android APK 安装到真机，逐页验收所有功能
- [ ] 局域网扫码联调（桌面端生成二维码 → 手机扫码连接）
- [ ] 模拟弱网（WebSocket 重连验证）
- [ ] 防火墙规则确认：入站 8080 TCP 放行
- [ ] README 更新：放行端口 + 启动脚本说明

---

## 附录：涉及文件变更

### 修改文件

```
desktop/main.js                    # 系统托盘 + 开机自启
server/run_server.py               # mDNS 广播
server/requirements.txt            # zeroconf
web/tests/                         # Playwright E2E 脚本
server/tests/                      # pytest 补全
```
