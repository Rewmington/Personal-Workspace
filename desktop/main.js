const { app, BrowserWindow, dialog, Menu, shell, ipcMain } = require("electron");
const { spawn } = require("child_process");
const http = require("http");
const net = require("net");
const fs = require("fs");
const os = require("os");
const path = require("path");

const DEFAULT_PORT = Number(process.env.WORKSTATION_PORT || 8080);
const DEFAULT_HOST = process.env.WORKSTATION_HOST || "0.0.0.0";
let mainWindow = null;
let serverProcess = null;
let ownsServer = false;
let serverConfig = null;
let shutdownStarted = false;

function resourcePath(name) {
  return app.isPackaged ? path.join(process.resourcesPath, name) : path.resolve(__dirname, "..", name);
}

function configFile() {
  return path.join(app.getPath("userData"), "server-config.json");
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
    await new Promise((resolve) => setTimeout(resolve, 250));
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
  if (ready && mainWindow && !mainWindow.isDestroyed()) mainWindow.loadURL(`${localUrl()}/app/`);
  return ready;
}

async function ensureServer() {
  startServer();
  return waitForServer();
}

function createWindow() {
  mainWindow = new BrowserWindow({
    icon: path.join(__dirname, "assets", "workstation.ico"),
    width: 1360,
    height: 860,
    minWidth: 980,
    minHeight: 680,
    backgroundColor: "#181818",
    autoHideMenuBar: true,
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
  mainWindow.webContents.session.clearCache().finally(() => {
    if (mainWindow && !mainWindow.isDestroyed()) mainWindow.loadURL(`${localUrl()}/app/?v=${Date.now()}`);
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
}

async function boot() {
  Menu.setApplicationMenu(null);
  serverConfig = readServerConfig();
  registerIpc();
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
  createWindow();
}

app.whenReady().then(boot);
app.on("window-all-closed", () => app.quit());
app.on("before-quit", (event) => {
  if (shutdownStarted || !ownsServer) return;
  event.preventDefault();
  shutdownStarted = true;
  stopServer().finally(() => app.quit());
});
