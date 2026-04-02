const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fishLauncher', {
  getBootstrap: () => ipcRenderer.invoke('launcher:get-bootstrap'),
  saveState: (state) => ipcRenderer.invoke('launcher:save-state', state),
  saveConfig: (config) => ipcRenderer.invoke('launcher:save-config', config),
  saveModpack: (modpack) => ipcRenderer.invoke('launcher:save-modpack', modpack),
  authLogin: (payload) => ipcRenderer.invoke('launcher:auth-login', payload),
  authLogout: () => ipcRenderer.invoke('launcher:auth-logout'),
  launchProfile: (payload) => ipcRenderer.invoke('launcher:launch-profile', payload),
  openPath: (targetPath) => ipcRenderer.invoke('launcher:open-path', targetPath),
  openUrl: (url) => ipcRenderer.invoke('launcher:open-url', url),
  windowAction: (action) => ipcRenderer.invoke('launcher:window-action', action)
});
