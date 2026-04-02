const { app, BrowserWindow, ipcMain, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const { spawn } = require('child_process');

const ROOT = __dirname ? path.resolve(__dirname, '..') : process.cwd();
const PROJECT_ROOT = path.resolve(ROOT, '..');
const DATA_DIR = path.join(ROOT, 'data');
const CONFIG_PATH = path.join(DATA_DIR, 'launcher-config.json');
const STATE_PATH = path.join(DATA_DIR, 'launcher-state.json');
const MODPACK_PATH = path.join(DATA_DIR, 'modpack.json');
const LAUNCH_LOG_PATH = path.join(DATA_DIR, 'launch-last.log');
const MAIN_LOG_PATH = path.join(DATA_DIR, 'launcher-main.log');

const ADMIN_PASSWORD_HASH = '6c9fb2eb7ccf62004b11686635dc3758d76650aae9f289dd733a405c921fad2e';
let gradleWarmupStarted = false;

function hashPassword(password) {
  return crypto.createHash('sha256').update(String(password || ''), 'utf8').digest('hex');
}

function defaultConfig() {
  return {
    launcherName: 'Fish Launcher',
    user: {
      displayName: 'Ari'
    },
    accounts: [
      {
        id: 'admin-ari',
        username: 'Ari',
        displayName: 'Ari',
        passwordHash: ADMIN_PASSWORD_HASH,
        rankName: 'Fish',
        rankIcon: '🐟',
        subscriptionType: 'Lifetime',
        expiresAt: null
      }
    ],
    profiles: [
      {
        id: 'fish-main',
        name: 'Fish Client 1.21.11',
        branch: 'Stable',
        image: 'assets/bg-main.jpg',
        gameVersion: '1.21.11',
        loader: 'Fabric',
        javaPath: '',
        gameDir: '',
        jvmArgs: '-Xmx4G',
        launchCommandTemplate: 'gradlew.bat runClient'
      }
    ],
    quickLinks: {
      home: 'https://github.com',
      support: 'https://discord.com',
      updates: 'https://github.com'
    }
  };
}

function defaultState() {
  return {
    selectedProfileId: 'fish-main',
    selectedVersion: '1.21.11',
    recentLaunches: [],
    authSession: {
      loggedIn: false,
      userId: null,
      loginAt: null
    }
  };
}

function defaultModpack() {
  return {
    profileId: 'fish-main',
    mods: [
      {
        id: 'fabric-api',
        name: 'Fabric API',
        version: '0.139.4+1.21.11',
        enabled: true,
        source: 'builtin'
      },
      {
        id: 'fish-client',
        name: 'Fish Client Core',
        version: '1.1.0',
        enabled: true,
        source: 'local'
      }
    ]
  };
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function writeJson(filePath, value) {
  fs.writeFileSync(filePath, JSON.stringify(value, null, 2), 'utf8');
}

function appendMainLog(message) {
  try {
    const line = `[${new Date().toISOString()}] ${message}\n`;
    fs.appendFileSync(MAIN_LOG_PATH, line, 'utf8');
  } catch {
    // Logging is best effort.
  }
}

function normalizeConfig(raw) {
  const defaults = defaultConfig();
  const config = raw && typeof raw === 'object' ? raw : {};

  const normalized = {
    launcherName: String(config.launcherName || defaults.launcherName),
    user: {
      displayName: String(config?.user?.displayName || defaults.user.displayName)
    },
    profiles: normalizeProfiles(config.profiles, defaults.profiles),
    quickLinks: {
      home: String(config?.quickLinks?.home || defaults.quickLinks.home),
      support: String(config?.quickLinks?.support || defaults.quickLinks.support),
      updates: String(config?.quickLinks?.updates || defaults.quickLinks.updates)
    },
    accounts: normalizeAccounts(config.accounts, defaults.accounts)
  };

  return normalized;
}

function normalizeProfiles(profiles, fallback) {
  const source = Array.isArray(profiles) && profiles.length ? profiles : fallback;
  const out = [];

  source.forEach((profile, index) => {
    if (!profile || typeof profile !== 'object') {
      return;
    }

    const id = String(profile.id || `fish-profile-${index + 1}`);
    const name = String(profile.name || `Fish Profile ${index + 1}`);
    const looksLikeDev = id.toLowerCase().includes('dev') || name.toLowerCase().includes('dev');
    if (looksLikeDev) {
      return;
    }

    out.push({
      id,
      name,
      branch: String(profile.branch || 'Stable'),
      image: String(profile.image || ''),
      gameVersion: String(profile.gameVersion || '1.21.11'),
      loader: String(profile.loader || 'Fabric'),
      javaPath: String(profile.javaPath || ''),
      gameDir: String(profile.gameDir || ''),
      jvmArgs: String(profile.jvmArgs || '-Xmx4G'),
      launchCommandTemplate: String(profile.launchCommandTemplate || 'gradlew.bat runClient')
    });
  });

  return out.length ? out : fallback;
}

function normalizeAccounts(accounts, fallback) {
  const source = Array.isArray(accounts) && accounts.length ? accounts : fallback;
  const out = [];

  source.forEach((account, index) => {
    const id = String(account.id || `account-${index + 1}`);
    const username = String(account.username || account.displayName || id);
    const displayName = String(account.displayName || username);
    const passwordHash = account.passwordHash ? String(account.passwordHash) : (account.password ? hashPassword(account.password) : '');

    out.push({
      id,
      username,
      displayName,
      passwordHash,
      rankName: String(account.rankName || 'Fish'),
      rankIcon: String(account.rankIcon || '🐟'),
      subscriptionType: String(account.subscriptionType || 'Lifetime'),
      expiresAt: account.expiresAt ? String(account.expiresAt) : null
    });
  });

  return out.length ? out : fallback;
}

function normalizeState(raw) {
  const defaults = defaultState();
  const state = raw && typeof raw === 'object' ? raw : {};

  return {
    selectedProfileId: String(state.selectedProfileId || defaults.selectedProfileId),
    selectedVersion: String(state.selectedVersion || defaults.selectedVersion),
    recentLaunches: Array.isArray(state.recentLaunches) ? state.recentLaunches.slice(0, 30) : [],
    authSession: {
      loggedIn: Boolean(state?.authSession?.loggedIn),
      userId: state?.authSession?.userId ? String(state.authSession.userId) : null,
      loginAt: state?.authSession?.loginAt ? String(state.authSession.loginAt) : null
    }
  };
}

function normalizeModpack(raw) {
  const fallback = defaultModpack();
  const modpack = raw && typeof raw === 'object' ? raw : {};
  return {
    profileId: String(modpack.profileId || fallback.profileId),
    mods: Array.isArray(modpack.mods) ? modpack.mods : fallback.mods
  };
}

function normalizeStateForProfiles(rawState, profiles) {
  const state = normalizeState(rawState);
  if (!Array.isArray(profiles) || !profiles.length) {
    return state;
  }

  const selected = profiles.find((profile) => profile.id === state.selectedProfileId);
  if (selected) {
    return state;
  }

  return {
    ...state,
    selectedProfileId: profiles[0].id,
    selectedVersion: profiles[0].gameVersion || state.selectedVersion
  };
}

function sanitizeConfigForRenderer(config) {
  return {
    launcherName: config.launcherName,
    user: config.user,
    profiles: config.profiles,
    quickLinks: config.quickLinks,
    accounts: config.accounts.map(publicAccount)
  };
}

function publicAccount(account) {
  return {
    id: account.id,
    username: account.username,
    displayName: account.displayName,
    rankName: account.rankName,
    rankIcon: account.rankIcon,
    subscriptionType: account.subscriptionType,
    expiresAt: account.expiresAt
  };
}

function findAccount(config, username) {
  const needle = String(username || '').trim().toLowerCase();
  if (!needle) {
    return null;
  }

  return (config.accounts || []).find((account) => account.username.toLowerCase() === needle);
}

function verifyAccountPassword(account, password) {
  if (!account) {
    return false;
  }

  const incomingHash = hashPassword(password);
  if (account.passwordHash && incomingHash === account.passwordHash) {
    return true;
  }

  return false;
}

function resolveAuthUser(config, state) {
  if (!state?.authSession?.loggedIn || !state.authSession.userId) {
    return null;
  }

  const account = (config.accounts || []).find((entry) => entry.id === state.authSession.userId);
  return account ? publicAccount(account) : null;
}

function ensureFiles() {
  fs.mkdirSync(DATA_DIR, { recursive: true });

  if (!fs.existsSync(CONFIG_PATH)) {
    writeJson(CONFIG_PATH, defaultConfig());
  }
  if (!fs.existsSync(STATE_PATH)) {
    writeJson(STATE_PATH, defaultState());
  }
  if (!fs.existsSync(MODPACK_PATH)) {
    writeJson(MODPACK_PATH, defaultModpack());
  }

  const normalizedConfig = normalizeConfig(readJson(CONFIG_PATH));
  const normalizedState = normalizeStateForProfiles(readJson(STATE_PATH), normalizedConfig.profiles);
  const normalizedModpack = normalizeModpack(readJson(MODPACK_PATH));

  writeJson(CONFIG_PATH, normalizedConfig);
  writeJson(STATE_PATH, normalizedState);
  writeJson(MODPACK_PATH, normalizedModpack);
}

function resolveWorkdir(profile) {
  if (profile && profile.gameDir && profile.gameDir.trim()) {
    return path.resolve(profile.gameDir);
  }

  const candidates = [
    PROJECT_ROOT,
    process.cwd(),
    path.resolve(process.cwd(), '..'),
    path.resolve(ROOT, '..')
  ];

  for (const candidate of candidates) {
    try {
      if (fs.existsSync(path.join(candidate, 'gradlew.bat'))) {
        return candidate;
      }
    } catch {
      // Ignore invalid candidate.
    }
  }

  return PROJECT_ROOT;
}

function commandHasFlag(command, flag) {
  const escaped = flag.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(`(^|\\s)${escaped}(\\s|$)`, 'i');
  return re.test(command);
}

function optimizeLaunchCommand(command) {
  const input = String(command || '').trim();
  if (!input) {
    return input;
  }

  const lower = input.toLowerCase();
  const looksLikeGradle = lower.includes('gradlew') && lower.includes('runclient');
  if (!looksLikeGradle) {
    return input;
  }

  const fastFlags = ['--daemon'];
  let optimized = input;

  for (const flag of fastFlags) {
    if (!commandHasFlag(optimized, flag)) {
      optimized += ` ${flag}`;
    }
  }

  return optimized;
}

function makeCommandLauncherFriendly(command, workdir) {
  let output = String(command || '').trim();
  if (!output) {
    return output;
  }

  if (process.platform !== 'win32') {
    return output;
  }

  const gradleRegex = /\bgradlew(?:\.bat)?\b/i;
  if (!gradleRegex.test(output)) {
    return output;
  }

  // If command already references an absolute gradle wrapper path, keep it unchanged.
  const absoluteGradlePathRegex = /(?:^|\s)"?[a-zA-Z]:\\[^"\r\n]*gradlew(?:\.bat)?"?/i;
  if (absoluteGradlePathRegex.test(output)) {
    return output;
  }

  const wrapperCandidates = [
    path.join(workdir, 'gradlew.bat'),
    path.join(PROJECT_ROOT, 'gradlew.bat'),
    path.join(process.cwd(), 'gradlew.bat')
  ];

  let wrapperPath = '';
  for (const candidate of wrapperCandidates) {
    if (fs.existsSync(candidate)) {
      wrapperPath = candidate;
      break;
    }
  }

  if (wrapperPath) {
    output = output.replace(gradleRegex, `"${wrapperPath}"`);
  }

  return output;
}

function parseGradleCommand(command) {
  const input = String(command || '').trim();
  if (!input) {
    return null;
  }

  let match = input.match(/^"([^"]*gradlew(?:\.bat)?)"\s*(.*)$/i);
  if (!match) {
    match = input.match(/^([^\s"]*gradlew(?:\.bat)?)\s*(.*)$/i);
  }
  if (!match) {
    return null;
  }

  return {
    gradlewPath: match[1],
    args: match[2] || ''
  };
}

function quoteForCmd(value) {
  return `"${String(value || '').replace(/"/g, '""')}"`;
}

function prewarmGradleDaemon() {
  if (gradleWarmupStarted || process.platform !== 'win32') {
    return;
  }
  gradleWarmupStarted = true;

  const gradlewPath = path.join(PROJECT_ROOT, 'gradlew.bat');
  if (!fs.existsSync(gradlewPath)) {
    return;
  }

  const warmupCmd = `"${gradlewPath}" -q help --daemon --configuration-cache`;
  try {
    const child = spawn('cmd.exe', ['/d', '/s', '/c', warmupCmd], {
      cwd: PROJECT_ROOT,
      windowsHide: true,
      detached: true,
      stdio: 'ignore'
    });
    child.unref();
  } catch {
    // Warmup is best effort only.
  }
}

function createWindow() {
  const win = new BrowserWindow({
    width: 1180,
    height: 760,
    minWidth: 980,
    minHeight: 620,
    backgroundColor: '#07080D',
    title: 'Fish Launcher',
    frame: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  win.loadFile(path.join(__dirname, 'renderer.html'));
}

app.whenReady().then(() => {
  ensureFiles();
  prewarmGradleDaemon();
  createWindow();

  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) {
      createWindow();
    }
  });
});

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') {
    app.quit();
  }
});

ipcMain.handle('launcher:get-bootstrap', async () => {
  ensureFiles();

  const config = normalizeConfig(readJson(CONFIG_PATH));
  const state = normalizeStateForProfiles(readJson(STATE_PATH), config.profiles);
  const modpack = normalizeModpack(readJson(MODPACK_PATH));
  const authUser = resolveAuthUser(config, state);

  return {
    config: sanitizeConfigForRenderer(config),
    state,
    modpack,
    authUser,
    platform: process.platform,
    cwd: path.resolve(ROOT, '..')
  };
});

ipcMain.handle('launcher:save-state', async (_event, state) => {
  const config = normalizeConfig(readJson(CONFIG_PATH));
  const safeState = normalizeStateForProfiles(state, config.profiles);
  writeJson(STATE_PATH, safeState);
  return safeState;
});

ipcMain.handle('launcher:save-config', async (_event, configPayload) => {
  const existing = normalizeConfig(readJson(CONFIG_PATH));
  const merged = normalizeConfig({
    ...configPayload,
    accounts: existing.accounts
  });
  writeJson(CONFIG_PATH, merged);
  return sanitizeConfigForRenderer(merged);
});

ipcMain.handle('launcher:save-modpack', async (_event, modpack) => {
  const safe = normalizeModpack(modpack);
  writeJson(MODPACK_PATH, safe);
  return safe;
});

ipcMain.handle('launcher:auth-login', async (_event, payload) => {
  ensureFiles();

  const config = normalizeConfig(readJson(CONFIG_PATH));
  const state = normalizeState(readJson(STATE_PATH));
  const username = payload?.username || '';
  const password = payload?.password || '';

  const account = findAccount(config, username);
  if (!account || !verifyAccountPassword(account, password)) {
    return {
      ok: false,
      message: 'Invalid username or password.'
    };
  }

  state.authSession = {
    loggedIn: true,
    userId: account.id,
    loginAt: new Date().toISOString()
  };
  writeJson(STATE_PATH, state);

  return {
    ok: true,
    user: publicAccount(account),
    authSession: state.authSession
  };
});

ipcMain.handle('launcher:auth-logout', async () => {
  ensureFiles();
  const state = normalizeState(readJson(STATE_PATH));
  state.authSession = {
    loggedIn: false,
    userId: null,
    loginAt: null
  };
  writeJson(STATE_PATH, state);
  return { ok: true, authSession: state.authSession };
});

ipcMain.handle('launcher:open-path', async (_event, targetPath) => {
  const resolved = path.resolve(ROOT, targetPath || '.');
  await shell.openPath(resolved);
  return resolved;
});

ipcMain.handle('launcher:open-url', async (_event, url) => {
  if (!url || typeof url !== 'string') {
    return false;
  }
  await shell.openExternal(url);
  return true;
});

ipcMain.handle('launcher:window-action', async (event, action) => {
  const win = BrowserWindow.fromWebContents(event.sender);
  if (!win) {
    return;
  }

  if (action === 'minimize') {
    win.minimize();
  } else if (action === 'maximize') {
    if (win.isMaximized()) {
      win.unmaximize();
    } else {
      win.maximize();
    }
  } else if (action === 'close') {
    win.close();
  }
});

ipcMain.handle('launcher:launch-profile', async (_event, payload) => {
  try {
    const config = normalizeConfig(readJson(CONFIG_PATH));
    const profile = (config.profiles || []).find((p) => p.id === payload.profileId);
    if (!profile) {
      appendMainLog(`launch-profile: profile not found for id=${payload && payload.profileId}`);
      return { ok: false, message: 'Profile not found.' };
    }

    const commandRaw = profile.launchCommandTemplate || 'gradlew.bat runClient';
    const commandFilled = commandRaw
      .replaceAll('{MC_VERSION}', profile.gameVersion || payload.version || '1.21.11')
      .replaceAll('{JAVA}', profile.javaPath || 'java')
      .replaceAll('{JVM_ARGS}', profile.jvmArgs || '')
      .replaceAll('{GAME_DIR}', profile.gameDir || '');
    const optimizedCommand = optimizeLaunchCommand(commandFilled);

    const workdir = resolveWorkdir(profile);
    const launchCommand = makeCommandLauncherFriendly(optimizedCommand, workdir);
    appendMainLog(`launch-profile: profile=${profile.id} workdir=${workdir} cmd=${launchCommand}`);
    fs.appendFileSync(LAUNCH_LOG_PATH, `[${new Date().toISOString()}] launch attempt: ${launchCommand}\n`, 'utf8');

    const child = process.platform === 'win32'
      ? (() => {
          const gradle = parseGradleCommand(launchCommand);
          const launchCore = gradle
            ? `call ${quoteForCmd(gradle.gradlewPath)} ${String(gradle.args || '').trim()}`.trim()
            : launchCommand;
          const launchCmd = `${launchCore} >> ${quoteForCmd(LAUNCH_LOG_PATH)} 2>&1`;

          appendMainLog(`launch-profile: cmd=${launchCmd}`);

          return spawn('cmd.exe', ['/d', '/s', '/c', launchCmd], {
            cwd: workdir,
            windowsHide: true,
            detached: true,
            stdio: 'ignore'
          });
        })()
      : spawn('/bin/bash', ['-lc', launchCommand], {
          cwd: workdir,
          detached: true,
          stdio: 'ignore'
        });
    child.unref();

    appendMainLog(`launch-profile: spawned pid=${child.pid}`);

    return {
      ok: true,
      message: `Launching ${profile.name}...`,
      command: launchCommand,
      workdir,
      pid: child.pid,
      launchLog: process.platform === 'win32' ? LAUNCH_LOG_PATH : null,
      mainLog: MAIN_LOG_PATH
    };
  } catch (error) {
    appendMainLog(`launch-profile: exception=${error && error.stack ? error.stack : error}`);
    return {
      ok: false,
      message: `Launch failed: ${error.message}`,
      command: null,
      workdir: null,
      mainLog: MAIN_LOG_PATH
    };
  }
});
