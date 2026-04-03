const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('fishLauncher', {
  getBootstrap: () => ipcRenderer.invoke('launcher:get-bootstrap'),
  saveState: (state) => ipcRenderer.invoke('launcher:save-state', state),
  saveConfig: (config) => ipcRenderer.invoke('launcher:save-config', config),
  saveModpack: (modpack) => ipcRenderer.invoke('launcher:save-modpack', modpack),
  authLogin: (payload) => ipcRenderer.invoke('launcher:auth-login', payload),
  authLogout: () => ipcRenderer.invoke('launcher:auth-logout'),
  msDeviceStart: () => ipcRenderer.invoke('launcher:ms-device-start'),
  msDevicePoll: (sessionId) => ipcRenderer.invoke('launcher:ms-device-poll', sessionId),
  msDeviceCancel: (sessionId) => ipcRenderer.invoke('launcher:ms-device-cancel', sessionId),
  msWebStart: () => ipcRenderer.invoke('launcher:ms-web-start'),
  msWebPoll: (sessionId) => ipcRenderer.invoke('launcher:ms-web-poll', sessionId),
  msWebCancel: (sessionId) => ipcRenderer.invoke('launcher:ms-web-cancel', sessionId),
  launchProfile: (payload) => ipcRenderer.invoke('launcher:launch-profile', payload),
  openPath: (targetPath) => ipcRenderer.invoke('launcher:open-path', targetPath),
  openUrl: (url) => ipcRenderer.invoke('launcher:open-url', url),
  windowAction: (action) => ipcRenderer.invoke('launcher:window-action', action)
});
