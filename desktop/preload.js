const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("workstationDesktop", {
  getServerConfig: () => ipcRenderer.invoke("server-config:get"),
  saveServerConfig: (config) => ipcRenderer.invoke("server-config:save", config),
});