const { app, BrowserWindow, dialog, Menu, shell, ipcMain, Tray, nativeImage } = require("electron");
const { spawn } = require("child_process");
const http = require("http");
const net = require("net");
const fs = require("fs");
const os = require("os");
const path = require("path");

const DEFAULT_PORT = Number(process.env.WORKSTATION_PORT || 8080);
const DEFAULT_HOST = process.env.WORKSTATION_HOST || "0.0.0.0";
let mainWindow = null;
let tray = null;
let serverProcess = null;
let ownsServer = false;
let serverConfig = null;
let shutdownStarted = false;
let minimizeToTray = true;
// 每次启动使用新的页面地址，避免 Chromium 复用旧的 index.html 缓存。
const WEB_CACHE_BUSTER = Date.now().toString(36);

function resourcePath(name) {
  return app.isPackaged ? path.join(process.resourcesPath, name) : path.resolve(__dirname, "..", name);
}

function configFile() {
  return path.join(app.getPath("userData"), "server-config.json");
}

function traySettingsFile() {
  return path.join(app.getPath("userData"), "tray-settings.json");
}

function loadTraySettings() {
  try {
    const data = JSON.parse(fs.readFileSync(traySettingsFile(), "utf8"));
    minimizeToTray = data.minimizeToTray !== false; // 默认 true
  } catch (_) {
    minimizeToTray = true;
  }
}

function saveTraySettings() {
  const target = traySettingsFile();
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, JSON.stringify({ minimizeToTray }, null, 2), "utf8");
}

function validHost(value) {
  const host = String(value || "").trim();
  return host === "localhost" || host === "0.0.0.0" || host === "::" || net.isIP(host) > 0;
}

function normalizeConfig(value = {}) {
  const rawHost = String(value.host || DEFAULT_HOST).trim();
  const rawPort = Number(value.port || DEFAULT_PORT);
  return {
    host: validHost(rawHost) ? rawHost : DEFAULT_HOST,
    port: Number.isInteger(rawPort) && rawPort >= 1 && rawPort <= 65535 ? rawPort : DEFAULT_PORT,
  };
}

function readServerConfig() {
  try {
    return normalizeConfig(JSON.parse(fs.readFileSync(configFile(), "utf8")));
  } catch (_) {
    return normalizeConfig();
  }
}

function saveServerConfig(config) {
  const target = configFile();
  fs.mkdirSync(path.dirname(target), { recursive: true });
  fs.writeFileSync(target, `${JSON.stringify(config, null, 2)}\n`, "utf8");
}

function healthHost() {
  if (serverConfig.host === "0.0.0.0") return "127.0.0.1";
  if (serverConfig.host === "::") return "[::1]";
  if (serverConfig.host === "localhost") return "127.0.0.1";
  return serverConfig.host.includes(":") ? `[${serverConfig.host}]` : serverConfig.host;
}

function localUrl() {
  return `http://${healthHost()}:${serverConfig.port}`;
}

function appUrl() {
  return `${localUrl()}/app/?v=${WEB_CACHE_BUSTER}`;
}

function networkUrls() {
  const urls = [];
  for (const addresses of Object.values(os.networkInterfaces())) {
    for (const address of addresses || []) {
      if (address.family === "IPv4" && !address.internal) urls.push(`http://${address.address}:${serverConfig.port}`);
    }
  }
  return [...new Set(urls)];
}

function requestHealth() {
  return new Promise((resolve) => {
    const request = http.get(`${localUrl()}/api/health`, (response) => {
      response.resume();
      resolve(response.statusCode === 200);
    });
    request.on("error", () => resolve(false));
    request.setTimeout(1000, () => {
      request.destroy();
      resolve(false);
    });
  });
}

async function waitForServer(timeoutMs = 20000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await requestHealth()) return true;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  return false;
}

function startServer() {
  const dataDir = path.join(app.getPath("userData"), "data");
  const environment = {
    ...process.env,
    WORKSTATION_HOST: serverConfig.host,
    WORKSTATION_PORT: String(serverConfig.port),
    WORKSTATION_DATA_DIR: dataDir,
    WORKSTATION_WEB_DIR: resourcePath("web"),
  };

  if (app.isPackaged) {
    const executable = path.join(process.resourcesPath, "server", "personal-workstation-server.exe");
    serverProcess = spawn(executable, [], { env: environment, windowsHide: true, stdio: "ignore" });
  } else {
    serverProcess = spawn(process.env.PYTHON || "python", [path.join(resourcePath("server"), "run_server.py")], {
      cwd: resourcePath("server"),
      env: environment,
      windowsHide: true,
      stdio: "ignore",
    });
  }
  ownsServer = true;
  serverProcess.on("error", (error) => console.error("本地服务启动失败", error));
}

function stopServer() {
  const child = serverProcess;
  serverProcess = null;
  ownsServer = false;
  if (!child || child.exitCode !== null) return Promise.resolve();
  return new Promise((resolve) => {
    let finished = false;
    const finish = () => {
      if (finished) return;
      finished = true;
      resolve();
    };
    child.once("exit", finish);
    child.once("error", finish);
    try { child.kill(); } catch (_) { finish(); }
    setTimeout(finish, 4000);
  });
}

async function restartServer() {
  await stopServer();
  startServer();
  const ready = await waitForServer();
  if (ready && mainWindow && !mainWindow.isDestroyed()) mainWindow.loadURL(appUrl());
  return ready;
}

async function ensureServer() {
  startServer();
  return waitForServer();
}

function createTray() {
  const iconPath = path.join(__dirname, "assets", "workstation.ico");
  let trayIcon;
  try {
    trayIcon = nativeImage.createFromPath(iconPath);
    if (trayIcon.isEmpty()) throw new Error("empty icon");
    trayIcon = trayIcon.resize({ width: 16, height: 16 });
  } catch (_) {
    trayIcon = nativeImage.createEmpty();
  }
  tray = new Tray(trayIcon);
  const contextMenu = Menu.buildFromTemplate([
    { label: "打开工作台", click: () => { if (mainWindow) { mainWindow.show(); mainWindow.focus(); } } },
    { type: "separator" },
    {
      label: "最小化到托盘", type: "checkbox", checked: minimizeToTray, click: (item) => {
        minimizeToTray = item.checked;
        saveTraySettings();
      }
    },
    { type: "separator" },
    { label: "退出", click: () => { shutdownStarted = true; app.quit(); } }
  ]);
  tray.setToolTip("个人工作台");
  tray.setContextMenu(contextMenu);
  tray.on("double-click", () => {
    if (mainWindow) {
      mainWindow.isVisible() ? mainWindow.focus() : mainWindow.show();
    }
  });
}

function createWindow() {
  mainWindow = new BrowserWindow({
    icon: path.join(__dirname, "assets", "workstation.ico"),
    width: 1360,
    height: 860,
    minWidth: 980,
    minHeight: 680,
    frame: false,
    backgroundColor: "#181818",
    show: false,
    webPreferences: {
      contextIsolation: true,
      nodeIntegration: false,
      preload: path.join(__dirname, "preload.js"),
    },
  });
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    if (url.startsWith("https://") || url.startsWith("http://")) {
      shell.openExternal(url);
      return { action: "deny" };
    }
    return { action: "allow" };
  });

  // ready-to-show 防止白屏闪烁
  mainWindow.once("ready-to-show", () => {
    mainWindow.show();
    mainWindow.focus();
  });

  // 先显示启动页（秒开），等服务器就绪后再加载应用页
  mainWindow.loadURL(`data:text/html,${encodeURIComponent(
    `<!DOCTYPE html>
<html style="margin:0;height:100%">
<head><meta charset="UTF-8"><style>
body{margin:0;height:100%;background:#181818;color:#999;font:14px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:18px;user-select:none;-webkit-user-select:none}
.spinner{width:36px;height:36px;border:3px solid #333;border-top-color:#7c3aed;border-radius:50%;animation:spin 1s linear infinite}
@keyframes spin{to{transform:rotate(360deg)}}
.status{font-size:13px;letter-spacing:.02em}
.brand{font-size:15px;font-weight:600;color:#bbb}
</style></head>
<body>
<div class="brand">个人工作台</div>
<div class="spinner"></div>
<div class="status">正在启动本地服务…</div>
</body>
</html>`
  )}`);

  // 关闭窗口行为：如果设置了最小化到托盘，则隐藏而不是退出
  mainWindow.on("close", (event) => {
    if (!shutdownStarted && minimizeToTray) {
      event.preventDefault();
      mainWindow.hide();
    }
  });
  mainWindow.on("closed", () => { mainWindow = null; });
}

function registerIpc() {
  ipcMain.handle("server-config:get", () => ({ ...serverConfig, localUrl: localUrl(), lanUrls: networkUrls() }));
  ipcMain.handle("server-config:save", async (_event, next) => {
    const normalized = normalizeConfig(next);
    const changed = normalized.host !== serverConfig.host || normalized.port !== serverConfig.port;
    serverConfig = normalized;
    saveServerConfig(serverConfig);
    if (changed && serverProcess) {
      const ready = await restartServer();
      if (!ready) throw new Error(`服务无法启动，请检查 ${serverConfig.host}:${serverConfig.port} 是否被占用`);
    }
    return { ...serverConfig, localUrl: localUrl(), lanUrls: networkUrls() };
  });
  ipcMain.handle("directory:choose", async () => {
    const result = await dialog.showOpenDialog(mainWindow, { properties: ["openDirectory", "createDirectory"] });
    return result.canceled ? null : result.filePaths[0];
  });
  ipcMain.handle("tray-settings:get", () => ({ minimizeToTray }));
  ipcMain.handle("tray-settings:save", async (_event, settings) => {
    if (typeof settings.minimizeToTray === "boolean") minimizeToTray = settings.minimizeToTray;
    saveTraySettings();
    if (tray) createTray();
    return { minimizeToTray };
  });

  // 自定义标题栏窗口控制
  ipcMain.on("window:minimize", () => mainWindow?.minimize());
  ipcMain.on("window:maximize", () => {
    if (mainWindow?.isMaximized()) mainWindow.unmaximize();
    else mainWindow?.maximize();
  });
  ipcMain.on("window:close", () => mainWindow?.close());
  ipcMain.on("window:isMaximized", (event) => {
    event.returnValue = mainWindow?.isMaximized() ?? false;
  });
}

async function boot() {
  Menu.setApplicationMenu(null);
  serverConfig = readServerConfig();
  loadTraySettings();
  registerIpc();

  // 先创建窗口（暗色背景，立即可见），避免白屏等待服务器
  createWindow();

  const ready = await ensureServer();
  if (!ready) {
    await dialog.showMessageBox({
      type: "error",
      title: "个人工作台启动失败",
      message: `无法连接服务（${serverConfig.host}:${serverConfig.port}）。`,
      detail: "请确认监听地址有效且端口没有被其他程序占用，然后重新启动软件。",
    });
    app.quit();
    return;
  }
  // 服务就绪后加载应用页面
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.loadURL(appUrl());
  createTray();
}

app.whenReady().then(boot);
app.on("window-all-closed", () => {
  // 如果用户设置了最小化到托盘，则不退出应用
  // macOS 默认行为需要特殊处理，Windows 上由 close 事件的 hide() 处理
});
app.on("before-quit", (event) => {
  if (shutdownStarted || !ownsServer) return;
  event.preventDefault();
  shutdownStarted = true;
  stopServer().finally(() => app.quit());
});
