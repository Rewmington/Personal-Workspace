const { contextBridge, ipcRenderer } = require("electron");

contextBridge.exposeInMainWorld("workstationDesktop", {
  getServerConfig: () => ipcRenderer.invoke("server-config:get"),
  saveServerConfig: (config) => ipcRenderer.invoke("server-config:save", config),
  chooseDirectory: () => ipcRenderer.invoke("directory:choose"),
  getTraySettings: () => ipcRenderer.invoke("tray-settings:get"),
  saveTraySettings: (settings) => ipcRenderer.invoke("tray-settings:save", settings),
});
