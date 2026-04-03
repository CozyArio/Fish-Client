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
let launchInProgress = false;
const MS_DEVICE_CLIENT_ID = '04b07795-8ddb-461a-bbee-02f9e1bf7b46';
const MS_DEVICE_SCOPE = 'XboxLive.signin offline_access';
const MS_CUSTOM_APP_ID = '2ecf6e90-db9f-40c8-9740-e72921a933b8';
const MS_LIVE_CLIENT_ID = '00000000402b5328';
const MS_LIVE_SCOPE = 'service::user.auth.xboxlive.com::MBI_SSL';
const MS_LIVE_REDIRECT = 'https://login.live.com/oauth20_desktop.srf';
const MS_WEB_REDIRECT_DEFAULT = 'https://login.microsoftonline.com/common/oauth2/nativeclient';
const MS_WEB_REDIRECT_FALLBACK = 'https://login.live.com/oauth20_desktop.srf';
const MS_WEB_REDIRECT_LOOPBACK = 'http://localhost';
const msDeviceSessions = new Map();
const msWebSessions = new Map();

function hashPassword(password) {
  return crypto.createHash('sha256').update(String(password || ''), 'utf8').digest('hex');
}

function sanitizeMinecraftUsername(username) {
  const cleaned = String(username || '').trim().replace(/[^A-Za-z0-9_]/g, '');
  return cleaned.slice(0, 16);
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
    alts: [
      {
        id: 'alt-ari',
        username: 'Ari',
        type: 'offline',
        note: 'Primary',
        rankIcon: '🐟'
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
    },
    msAuth: {
      clientId: MS_DEVICE_CLIENT_ID,
      tenant: 'consumers',
      scope: MS_DEVICE_SCOPE,
      redirectUri: MS_WEB_REDIRECT_DEFAULT
    }
  };
}

function defaultState() {
  return {
    selectedProfileId: 'fish-main',
    selectedAltId: 'alt-ari',
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
    msAuth: normalizeMsAuth(config?.msAuth, defaults.msAuth),
    accounts: normalizeAccounts(config.accounts, defaults.accounts),
    alts: normalizeAlts(config.alts, defaults.alts)
  };

  return normalized;
}

function normalizeMsAuth(msAuth, fallback) {
  const source = msAuth && typeof msAuth === 'object' ? msAuth : {};
  const tenantRaw = String(source.tenant || fallback.tenant || 'consumers').trim().toLowerCase();
  const tenant = tenantRaw === 'common' ? 'common' : 'consumers';
  const clientId = String(source.clientId || fallback.clientId || MS_DEVICE_CLIENT_ID).trim();
  const scope = String(source.scope || fallback.scope || MS_DEVICE_SCOPE).trim() || MS_DEVICE_SCOPE;
  const redirectUri = normalizeRedirectUri(source.redirectUri, fallback.redirectUri || MS_WEB_REDIRECT_DEFAULT);
  return {
    clientId,
    tenant,
    scope,
    redirectUri
  };
}

function normalizeRedirectUri(input, fallback) {
  const candidateRaw = String(input || fallback || '').trim();
  if (!candidateRaw) {
    return MS_WEB_REDIRECT_DEFAULT;
  }

  // Strip trailing slashes so comparisons and callback matching stay stable.
  const candidate = candidateRaw.replace(/\/+$/, '');
  const lower = candidate.toLowerCase();
  const isHttp = lower.startsWith('http://');
  const isHttps = lower.startsWith('https://');
  if (!isHttp && !isHttps) {
    return MS_WEB_REDIRECT_DEFAULT;
  }

  // Old launcher builds stored loopback /callback URIs with random ports.
  // Those are often unregistered and trigger invalid redirect_uri errors.
  const isLegacyLoopbackCallback = /^https?:\/\/(127\.0\.0\.1|localhost)(:\d+)?\/callback$/i.test(candidate);
  if (isLegacyLoopbackCallback) {
    return MS_WEB_REDIRECT_DEFAULT;
  }

  return candidate;
}

function buildMsWebRedirectCandidates(preferred) {
  const out = [];
  const pushUnique = (value) => {
    const normalized = normalizeRedirectUri(value, '');
    if (!normalized || out.includes(normalized)) {
      return;
    }
    out.push(normalized);
  };

  pushUnique(preferred);
  pushUnique(MS_WEB_REDIRECT_DEFAULT);
  pushUnique(MS_WEB_REDIRECT_FALLBACK);
  pushUnique(MS_WEB_REDIRECT_LOOPBACK);

  if (!out.length) {
    out.push(MS_WEB_REDIRECT_DEFAULT);
  }
  return out;
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
      launchCommandTemplate: normalizeLaunchTemplate(String(profile.launchCommandTemplate || 'gradlew.bat runClient'))
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

function normalizeAlts(alts, fallback) {
  const source = Array.isArray(alts) && alts.length ? alts : fallback;
  const out = [];
  const seenUsernames = new Set();

  source.forEach((alt, index) => {
    const username = sanitizeMinecraftUsername(alt?.username || alt?.name || `Player${index + 1}`);
    if (!username || seenUsernames.has(username.toLowerCase())) {
      return;
    }
    seenUsernames.add(username.toLowerCase());

    const typeRaw = String(alt?.type || 'offline').toLowerCase();
    out.push({
      id: String(alt?.id || `alt-${username.toLowerCase()}`),
      username,
      type: typeRaw === 'login' ? 'login' : 'offline',
      note: String(alt?.note || ''),
      rankIcon: String(alt?.rankIcon || '🐟'),
      authProvider: String(alt?.authProvider || (typeRaw === 'login' ? 'local' : 'offline')),
      mcUuid: alt?.mcUuid ? String(alt.mcUuid) : null,
      microsoft: alt?.microsoft && typeof alt.microsoft === 'object'
        ? {
            accessToken: String(alt.microsoft.accessToken || ''),
            refreshToken: String(alt.microsoft.refreshToken || ''),
            expiresAt: alt.microsoft.expiresAt ? String(alt.microsoft.expiresAt) : null,
            mcAccessToken: String(alt.microsoft.mcAccessToken || '')
          }
        : undefined
    });
  });

  if (out.length) {
    return out;
  }

  return (fallback || []).map((entry) => ({
    id: String(entry.id || `alt-${sanitizeMinecraftUsername(entry.username || 'ari').toLowerCase()}`),
    username: sanitizeMinecraftUsername(entry.username || 'Ari') || 'Ari',
    type: String(entry.type || 'offline').toLowerCase() === 'login' ? 'login' : 'offline',
    note: String(entry.note || ''),
    rankIcon: String(entry.rankIcon || '🐟'),
    authProvider: String(entry.authProvider || (String(entry.type || 'offline').toLowerCase() === 'login' ? 'local' : 'offline')),
    mcUuid: entry.mcUuid ? String(entry.mcUuid) : null,
    microsoft: entry.microsoft && typeof entry.microsoft === 'object'
      ? {
          accessToken: String(entry.microsoft.accessToken || ''),
          refreshToken: String(entry.microsoft.refreshToken || ''),
          expiresAt: entry.microsoft.expiresAt ? String(entry.microsoft.expiresAt) : null,
          mcAccessToken: String(entry.microsoft.mcAccessToken || '')
        }
      : undefined
  }));
}

function normalizeState(raw) {
  const defaults = defaultState();
  const state = raw && typeof raw === 'object' ? raw : {};

  return {
    selectedProfileId: String(state.selectedProfileId || defaults.selectedProfileId),
    selectedAltId: String(state.selectedAltId || defaults.selectedAltId),
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

function normalizeStateForProfiles(rawState, profiles, alts) {
  const state = normalizeState(rawState);
  let selectedAltId = state.selectedAltId;
  if (Array.isArray(alts) && alts.length) {
    selectedAltId = alts.some((alt) => alt.id === state.selectedAltId) ? state.selectedAltId : alts[0].id;
  } else {
    selectedAltId = '';
  }

  if (!Array.isArray(profiles) || !profiles.length) {
    return {
      ...state,
      selectedAltId
    };
  }

  const selected = profiles.find((profile) => profile.id === state.selectedProfileId);
  if (selected) {
    return {
      ...state,
      selectedAltId
    };
  }

  return {
    ...state,
    selectedAltId,
    selectedProfileId: profiles[0].id,
    selectedVersion: profiles[0].gameVersion || state.selectedVersion
  };
}

function mergeAltsPreservingAuth(existingAlts, incomingAlts) {
  const base = Array.isArray(existingAlts) ? existingAlts : [];
  if (!Array.isArray(incomingAlts)) {
    return base;
  }

  const merged = incomingAlts.map((incoming) => {
    if (!incoming || typeof incoming !== 'object') {
      return incoming;
    }
    const existing = base.find((entry) => String(entry.id || '') === String(incoming.id || ''));
    if (!existing) {
      return incoming;
    }
    const out = {
      ...existing,
      ...incoming
    };
    if (existing.microsoft && !incoming.microsoft) {
      out.microsoft = existing.microsoft;
    }
    return out;
  });

  return merged;
}

function sanitizeConfigForRenderer(config) {
  return {
    launcherName: config.launcherName,
    user: config.user,
    profiles: config.profiles,
    quickLinks: config.quickLinks,
    msAuth: config.msAuth,
    accounts: config.accounts.map(publicAccount),
    alts: (config.alts || []).map(publicAlt)
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

function publicAlt(alt) {
  return {
    id: alt.id,
    username: alt.username,
    type: alt.type,
    note: alt.note,
    rankIcon: alt.rankIcon || '🐟',
    authProvider: alt.authProvider || (alt.type === 'login' ? 'local' : 'offline'),
    mcUuid: alt.mcUuid || null
  };
}

async function postFormJson(url, formValues) {
  const body = new URLSearchParams(formValues);
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body
  });

  let payload = {};
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }

  if (!response.ok) {
    const reason = payload.error_description || payload.error || `${response.status}`;
    throw new Error(reason);
  }
  return payload;
}

async function postJson(url, jsonBody, extraHeaders) {
  const response = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(extraHeaders || {})
    },
    body: JSON.stringify(jsonBody || {})
  });

  let payload = {};
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }

  if (!response.ok) {
    const reason = payload.error_description || payload.errorMessage || payload.XErr || `${response.status}`;
    throw new Error(reason);
  }
  return payload;
}

async function getJson(url, headers) {
  const response = await fetch(url, {
    method: 'GET',
    headers: headers || {}
  });

  let payload = {};
  try {
    payload = await response.json();
  } catch {
    payload = {};
  }

  if (!response.ok) {
    const reason = payload.errorMessage || payload.error || `${response.status}`;
    throw new Error(reason);
  }
  return payload;
}

function parseMsTokenError(error) {
  const message = String(error && error.message ? error.message : error || '');
  const lower = message.toLowerCase();
  if (lower.includes('authorization_pending')) {
    return 'authorization_pending';
  }
  if (lower.includes('aadsts70016') || lower.includes('not yet been authorized by the user') || lower.includes('must input their code')) {
    return 'authorization_pending';
  }
  if (lower.includes('slow_down')) {
    return 'slow_down';
  }
  if (lower.includes('authorization_declined')) {
    return 'authorization_declined';
  }
  if (lower.includes('expired_token')) {
    return 'expired_token';
  }
  if (lower.includes('bad_verification_code')) {
    return 'bad_verification_code';
  }
  if (lower.includes('invalid app registration')) {
    return 'invalid_app_registration';
  }
  return '';
}

function recoverMsAuthToCompatDefaults() {
  try {
    ensureFiles();
    const config = normalizeConfig(readJson(CONFIG_PATH));
    config.msAuth = {
      clientId: MS_DEVICE_CLIENT_ID,
      tenant: 'consumers',
      scope: MS_DEVICE_SCOPE,
      redirectUri: normalizeRedirectUri(config?.msAuth?.redirectUri, MS_WEB_REDIRECT_DEFAULT)
    };
    writeJson(CONFIG_PATH, normalizeConfig(config));
  } catch {
    // Best effort.
  }
}

function randomBase64Url(bytes) {
  return crypto.randomBytes(bytes)
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function sha256Base64Url(input) {
  return crypto.createHash('sha256')
    .update(String(input || ''), 'utf8')
    .digest('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/g, '');
}

function htmlEscape(input) {
  return String(input || '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function pickLoopbackPort() {
  return 52100 + Math.floor(Math.random() * 800);
}

function buildWebAuthorizeUrl(msAuth, redirectUri, state, codeChallenge) {
  const params = new URLSearchParams({
    client_id: msAuth.clientId,
    response_type: 'code',
    redirect_uri: redirectUri,
    response_mode: 'query',
    scope: msAuth.scope,
    state,
    code_challenge: codeChallenge,
    code_challenge_method: 'S256',
    prompt: 'select_account'
  });
  return `https://login.microsoftonline.com/${msAuth.tenant}/oauth2/v2.0/authorize?${params.toString()}`;
}

function buildLiveAuthorizeUrl(flow, state) {
  const params = new URLSearchParams({
    client_id: flow.clientId,
    response_type: 'code',
    redirect_uri: flow.redirectUri,
    scope: flow.scope,
    state
  });
  return `https://login.live.com/oauth20_authorize.srf?${params.toString()}`;
}

function resolveWebAuthFlow(msAuth, stateToken, codeChallenge) {
  const tenant = String(msAuth?.tenant || 'consumers').trim().toLowerCase() === 'common' ? 'common' : 'consumers';
  const configuredClientId = String(msAuth?.clientId || '').trim();
  const configuredScope = String(msAuth?.scope || '').trim() || MS_DEVICE_SCOPE;

  // The public first-party device client frequently fails in auth-code mode with:
  // "application is first party... user is not permitted to consent".
  // Use the Microsoft Live desktop OAuth flow for email login in this default case.
  const useLiveCompat = !configuredClientId || configuredClientId === MS_DEVICE_CLIENT_ID;
  if (useLiveCompat) {
    const flow = {
      kind: 'live',
      tenant: 'consumers',
      clientId: MS_LIVE_CLIENT_ID,
      scope: MS_LIVE_SCOPE,
      redirectUri: MS_LIVE_REDIRECT,
      redirectCandidates: [MS_LIVE_REDIRECT],
      tokenEndpoint: 'https://login.live.com/oauth20_token.srf'
    };
    flow.authorizeUrl = buildLiveAuthorizeUrl(flow, stateToken);
    return flow;
  }

  const redirectCandidates = buildMsWebRedirectCandidates(msAuth?.redirectUri);
  const redirectUri = redirectCandidates[0];
  const flow = {
    kind: 'aad',
    tenant,
    clientId: configuredClientId,
    scope: configuredScope,
    redirectUri,
    redirectCandidates,
    tokenEndpoint: `https://login.microsoftonline.com/${tenant}/oauth2/v2.0/token`
  };
  flow.authorizeUrl = buildWebAuthorizeUrl(
    { clientId: flow.clientId, scope: flow.scope, tenant: flow.tenant },
    flow.redirectUri,
    stateToken,
    codeChallenge
  );
  return flow;
}

function setWebSessionStatus(session, status, message) {
  session.status = status;
  session.message = message;
  session.updatedAt = Date.now();
}

async function finalizeMsWebAltFromCode(session, code) {
  let token;
  if (session.flowKind === 'live') {
    token = await postFormJson(session.tokenEndpoint || 'https://login.live.com/oauth20_token.srf', {
      grant_type: 'authorization_code',
      client_id: session.clientId,
      code,
      redirect_uri: session.redirectUri,
      scope: session.scope
    });
  } else {
    token = await postFormJson(session.tokenEndpoint || `https://login.microsoftonline.com/${session.tenant}/oauth2/v2.0/token`, {
      grant_type: 'authorization_code',
      client_id: session.clientId,
      code,
      redirect_uri: session.redirectUri,
      code_verifier: session.codeVerifier
    });
  }

  const msAccessToken = String(token.access_token || '');
  const msRefreshToken = String(token.refresh_token || '');
  if (!msAccessToken) {
    throw new Error('Microsoft access token missing after web login.');
  }

  const mcData = await exchangeMicrosoftToMinecraft(msAccessToken);
  const profile = mcData.profile || {};
  const expiresInSec = Math.max(60, Number(token.expires_in || 3600));
  const expiresAtIso = new Date(Date.now() + expiresInSec * 1000).toISOString();

  const freshConfig = normalizeConfig(readJson(CONFIG_PATH));
  const freshState = normalizeStateForProfiles(readJson(STATE_PATH), freshConfig.profiles, freshConfig.alts);
  const alt = makeMicrosoftAlt(freshConfig, profile, {
    msAccessToken,
    msRefreshToken,
    mcAccessToken: mcData.mcAccessToken,
    expiresAt: expiresAtIso
  });

  const existingIndex = (freshConfig.alts || []).findIndex((entry) => entry.id === alt.id);
  if (existingIndex >= 0) {
    freshConfig.alts[existingIndex] = alt;
  } else {
    freshConfig.alts.push(alt);
  }
  writeJson(CONFIG_PATH, normalizeConfig(freshConfig));

  freshState.selectedAltId = alt.id;
  writeJson(STATE_PATH, normalizeStateForProfiles(freshState, freshConfig.profiles, freshConfig.alts));

  session.alt = publicAlt(alt);
  setWebSessionStatus(session, 'success', `Microsoft alt added: ${alt.username}`);
  appendMainLog(`ms-web: success session=${session.id} alt=${alt.username}`);
}

async function exchangeMicrosoftToMinecraft(msAccessToken) {
  const xbl = await postJson('https://user.auth.xboxlive.com/user/authenticate', {
    Properties: {
      AuthMethod: 'RPS',
      SiteName: 'user.auth.xboxlive.com',
      RpsTicket: `d=${msAccessToken}`
    },
    RelyingParty: 'http://auth.xboxlive.com',
    TokenType: 'JWT'
  });

  const xblToken = xbl && xbl.Token ? xbl.Token : '';
  const uhs = xbl && xbl.DisplayClaims && xbl.DisplayClaims.xui && xbl.DisplayClaims.xui[0] && xbl.DisplayClaims.xui[0].uhs
    ? String(xbl.DisplayClaims.xui[0].uhs)
    : '';
  if (!xblToken || !uhs) {
    throw new Error('Xbox Live token missing.');
  }

  const xsts = await postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
    Properties: {
      SandboxId: 'RETAIL',
      UserTokens: [xblToken]
    },
    RelyingParty: 'rp://api.minecraftservices.com/',
    TokenType: 'JWT'
  });

  const xstsToken = xsts && xsts.Token ? xsts.Token : '';
  if (!xstsToken) {
    throw new Error('XSTS token missing.');
  }

  const mcAuth = await postJson('https://api.minecraftservices.com/authentication/login_with_xbox', {
    identityToken: `XBL3.0 x=${uhs};${xstsToken}`
  });

  const mcAccessToken = mcAuth && mcAuth.access_token ? mcAuth.access_token : '';
  if (!mcAccessToken) {
    throw new Error('Minecraft access token missing.');
  }

  const mcProfile = await getJson('https://api.minecraftservices.com/minecraft/profile', {
    Authorization: `Bearer ${mcAccessToken}`
  });

  return {
    mcAccessToken,
    profile: mcProfile
  };
}

function makeMicrosoftAlt(config, profile, sessionData) {
  const usernameRaw = profile && profile.name ? String(profile.name) : 'Player';
  const username = sanitizeMinecraftUsername(usernameRaw) || 'Player';
  const uuid = profile && profile.id ? String(profile.id) : '';

  const existing = (config.alts || []).find((alt) => {
    if ((alt.authProvider || '').toLowerCase() !== 'microsoft') {
      return false;
    }
    if (uuid && alt.mcUuid && String(alt.mcUuid).toLowerCase() === uuid.toLowerCase()) {
      return true;
    }
    return String(alt.username || '').toLowerCase() === username.toLowerCase();
  });

  const baseId = `alt-ms-${username.toLowerCase()}`;
  let id = existing ? existing.id : baseId;
  if (!existing) {
    const used = new Set((config.alts || []).map((alt) => alt.id));
    let n = 2;
    while (used.has(id)) {
      id = `${baseId}-${n++}`;
    }
  }

  const expiresAt = sessionData && sessionData.expiresAt ? sessionData.expiresAt : null;
  const alt = {
    id,
    username,
    type: 'login',
    note: 'Microsoft Device Login',
    rankIcon: '🐟',
    authProvider: 'microsoft',
    mcUuid: uuid || null,
    microsoft: {
      accessToken: sessionData && sessionData.msAccessToken ? sessionData.msAccessToken : '',
      refreshToken: sessionData && sessionData.msRefreshToken ? sessionData.msRefreshToken : '',
      expiresAt,
      mcAccessToken: sessionData && sessionData.mcAccessToken ? sessionData.mcAccessToken : ''
    }
  };

  return alt;
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
  const normalizedState = normalizeStateForProfiles(readJson(STATE_PATH), normalizedConfig.profiles, normalizedConfig.alts);
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

function normalizeWhitespace(value) {
  return String(value || '').trim().replace(/\s+/g, ' ');
}

function normalizeLaunchTemplate(template) {
  const input = normalizeWhitespace(template);
  if (!input) {
    return 'gradlew.bat runClient --daemon --console=plain';
  }

  const parsed = parseGradleCommand(input);
  if (!parsed) {
    return input;
  }

  const cleanArgs = normalizeWhitespace(parsed.args);
  const wrapperCandidates = [
    stripWrappedQuotes(parsed.gradlewPath),
    path.join(PROJECT_ROOT, 'gradlew.bat'),
    path.join(process.cwd(), 'gradlew.bat')
  ];

  let wrapperPath = '';
  for (const candidate of wrapperCandidates) {
    if (!candidate) {
      continue;
    }
    try {
      const resolved = path.resolve(candidate);
      if (fs.existsSync(resolved)) {
        wrapperPath = resolved;
        break;
      }
    } catch {
      // ignore invalid candidate
    }
  }

  if (!wrapperPath) {
    wrapperPath = stripWrappedQuotes(parsed.gradlewPath);
  }

  return `"${wrapperPath}" ${cleanArgs}`.trim();
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

  const fastFlags = ['--daemon', '--console=plain'];
  let optimized = input.replace(/\s--configuration-cache(?=\s|$)/gi, ' ');
  optimized = normalizeWhitespace(optimized);

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

  const parsed = parseGradleCommand(output);
  if (parsed) {
    const wrapperCandidates = [
      stripWrappedQuotes(parsed.gradlewPath),
      path.join(workdir, 'gradlew.bat'),
      path.join(PROJECT_ROOT, 'gradlew.bat'),
      path.join(process.cwd(), 'gradlew.bat')
    ];
    let wrapperPath = '';
    for (const candidate of wrapperCandidates) {
      if (!candidate) {
        continue;
      }
      try {
        const resolved = path.resolve(candidate);
        if (fs.existsSync(resolved)) {
          wrapperPath = resolved;
          break;
        }
      } catch {
        // ignore invalid path
      }
    }
    if (!wrapperPath) {
      wrapperPath = stripWrappedQuotes(parsed.gradlewPath);
    }
    output = `"${wrapperPath}" ${normalizeWhitespace(parsed.args)}`.trim();
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

function withUsernameArg(command, username) {
  const input = String(command || '').trim();
  const safeUsername = sanitizeMinecraftUsername(username);
  if (!input || !safeUsername) {
    return input;
  }

  const looksLikeGradleRunClient = /\bgradlew(?:\.bat)?\b/i.test(input) && /\brunClient\b/i.test(input);
  if (!looksLikeGradleRunClient) {
    return input;
  }

  const argsRegex = /--args(?:=|\s+)("([^"]*)"|'([^']*)'|(\S+))/i;
  const match = input.match(argsRegex);
  if (match) {
    const existingArgs = (match[2] || match[3] || match[4] || '').trim();
    const withoutUsername = existingArgs.replace(/--username\s+\S+/gi, '').replace(/\s+/g, ' ').trim();
    const combined = `${withoutUsername ? `${withoutUsername} ` : ''}--username ${safeUsername}`;
    return input.replace(argsRegex, `--args="${combined}"`);
  }

  return `${input} --args="--username ${safeUsername}"`;
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

  const warmupCmd = `"${gradlewPath}" -q help classes compileClientJava --daemon --configuration-cache`;
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

function isGradleLaunchCommand(command) {
  const input = String(command || '').toLowerCase();
  return input.includes('gradlew') && input.includes('runclient');
}

function stripWrappedQuotes(value) {
  const text = String(value || '').trim();
  if (text.startsWith('"') && text.endsWith('"') && text.length > 1) {
    return text.slice(1, -1);
  }
  return text;
}

function javaHomeFromJavaPath(javaPath) {
  const cleaned = stripWrappedQuotes(javaPath);
  if (!cleaned) {
    return '';
  }

  let resolved = cleaned;
  try {
    resolved = path.resolve(cleaned);
  } catch {
    return '';
  }

  if (!fs.existsSync(resolved)) {
    return '';
  }

  const name = path.basename(resolved).toLowerCase();
  if (name === 'java' || name === 'java.exe') {
    return path.dirname(path.dirname(resolved));
  }

  const javaExe = path.join(resolved, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  if (fs.existsSync(javaExe)) {
    return resolved;
  }

  return '';
}

function parseJdkMajor(name) {
  const text = String(name || '').toLowerCase();
  const direct = text.match(/(?:^|[^0-9])(1[7-9]|2[0-9])(?:[^0-9]|$)/);
  if (direct) {
    return Number(direct[1]);
  }
  return 0;
}

function looksLikeJdkHome(candidatePath) {
  if (!candidatePath) {
    return false;
  }
  const javaExe = path.join(candidatePath, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
  return fs.existsSync(javaExe);
}

function listPossibleJdks(baseDir) {
  try {
    if (!baseDir || !fs.existsSync(baseDir)) {
      return [];
    }
    return fs.readdirSync(baseDir, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => path.join(baseDir, entry.name));
  } catch {
    return [];
  }
}

function resolveGradleJavaHome(profile) {
  const candidates = [];

  if (profile && profile.javaPath && profile.javaPath.trim()) {
    const fromProfile = javaHomeFromJavaPath(profile.javaPath);
    if (fromProfile) {
      candidates.push(fromProfile);
    }
  }

  if (process.env.JAVA_HOME) {
    candidates.push(process.env.JAVA_HOME);
  }

  const possibleBases = [
    process.env['ProgramFiles'],
    process.env['ProgramFiles(x86)']
  ].filter(Boolean);

  for (const base of possibleBases) {
    const scanRoots = [
      path.join(base, 'Java'),
      path.join(base, 'Eclipse Adoptium'),
      path.join(base, 'Microsoft')
    ];
    for (const root of scanRoots) {
      candidates.push(...listPossibleJdks(root));
    }
  }

  const normalizedUnique = [];
  for (const candidate of candidates) {
    try {
      const normalized = path.resolve(stripWrappedQuotes(candidate));
      if (!normalizedUnique.includes(normalized)) {
        normalizedUnique.push(normalized);
      }
    } catch {
      // ignore invalid path
    }
  }

  const valid = normalizedUnique
    .filter((jdkHome) => looksLikeJdkHome(jdkHome))
    .map((jdkHome) => ({
      home: jdkHome,
      major: parseJdkMajor(path.basename(jdkHome))
    }))
    .filter((entry) => entry.major >= 17 || /jdk-?2\d/i.test(path.basename(entry.home)))
    .map((entry) => ({
      ...entry,
      score: javaPreferenceScore(entry.major)
    }));

  valid.sort((a, b) => {
    if (b.score !== a.score) {
      return b.score - a.score;
    }
    return b.major - a.major;
  });
  return valid.length ? valid[0].home : '';
}

function javaPreferenceScore(major) {
  if (major === 21) {
    return 300;
  }
  if (major === 17) {
    return 250;
  }
  if (major === 22 || major === 20) {
    return 220;
  }
  if (major >= 18 && major <= 19) {
    return 200;
  }
  if (major > 22) {
    return 120 - Math.min(60, (major - 22) * 10);
  }
  return 100;
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
  const state = normalizeStateForProfiles(readJson(STATE_PATH), config.profiles, config.alts);
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
  const safeState = normalizeStateForProfiles(state, config.profiles, config.alts);
  writeJson(STATE_PATH, safeState);
  return safeState;
});

ipcMain.handle('launcher:save-config', async (_event, configPayload) => {
  const existing = normalizeConfig(readJson(CONFIG_PATH));
  const mergedAlts = Array.isArray(configPayload?.alts)
    ? mergeAltsPreservingAuth(existing.alts, configPayload.alts)
    : existing.alts;
  const merged = normalizeConfig({
    ...configPayload,
    accounts: existing.accounts,
    alts: mergedAlts
  });
  writeJson(CONFIG_PATH, merged);
  const updatedState = normalizeStateForProfiles(readJson(STATE_PATH), merged.profiles, merged.alts);
  writeJson(STATE_PATH, updatedState);
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

ipcMain.handle('launcher:ms-device-start', async () => {
  try {
    ensureFiles();
    const config = normalizeConfig(readJson(CONFIG_PATH));
    const msAuth = normalizeMsAuth(config.msAuth, defaultConfig().msAuth);
    const payload = await postFormJson(`https://login.microsoftonline.com/${msAuth.tenant}/oauth2/v2.0/devicecode`, {
      client_id: msAuth.clientId,
      scope: msAuth.scope
    });

    const sessionId = crypto.randomUUID();
    const now = Date.now();
    const intervalSec = Math.max(2, Number(payload.interval || 5));
    const expiresInSec = Math.max(60, Number(payload.expires_in || 900));
    const verificationUri = String(payload.verification_uri || 'https://www.microsoft.com/link');

    msDeviceSessions.set(sessionId, {
      id: sessionId,
      clientId: msAuth.clientId,
      tenant: msAuth.tenant,
      scope: msAuth.scope,
      deviceCode: String(payload.device_code || ''),
      userCode: String(payload.user_code || ''),
      verificationUri,
      verificationUriComplete: payload.verification_uri_complete ? String(payload.verification_uri_complete) : '',
      intervalMs: intervalSec * 1000,
      nextPollAt: now + intervalSec * 1000,
      expiresAt: now + expiresInSec * 1000
    });

    try {
      await shell.openExternal(payload.verification_uri_complete
        ? String(payload.verification_uri_complete)
        : `${verificationUri}`);
    } catch {
      // Ignore browser open failure; user can still type the link manually.
    }

    appendMainLog(`ms-device-start: session=${sessionId} tenant=${msAuth.tenant} clientId=${msAuth.clientId} scope=${msAuth.scope} uri=${verificationUri}`);

    return {
      ok: true,
      sessionId,
      userCode: String(payload.user_code || ''),
      verificationUri,
      verificationUriComplete: payload.verification_uri_complete ? String(payload.verification_uri_complete) : '',
      message: String(payload.message || `Open ${verificationUri} and enter code ${payload.user_code || ''}`),
      intervalMs: intervalSec * 1000,
      expiresInMs: expiresInSec * 1000
    };
  } catch (error) {
    const raw = error && error.message ? String(error.message) : String(error || '');
    appendMainLog(`ms-device-start: failed ${raw}`);
    const lower = raw.toLowerCase();
    const consentBlocked = lower.includes('first party application') || lower.includes('users are not permitted to consent');
    const fixedMessage = consentBlocked
      ? 'Microsoft blocked consent for this app on your account. Use a personal Microsoft account, or set your own Azure app Client ID in Launcher Settings.'
      : `Microsoft device login failed: ${raw}`;
    return {
      ok: false,
      message: fixedMessage
    };
  }
});

ipcMain.handle('launcher:ms-device-poll', async (_event, sessionId) => {
  const key = String(sessionId || '');
  const session = msDeviceSessions.get(key);
  if (!session) {
    return {
      ok: false,
      status: 'expired',
      message: 'Login session expired. Start again.'
    };
  }

  const now = Date.now();
  if (now >= session.expiresAt) {
    msDeviceSessions.delete(key);
    return {
      ok: false,
      status: 'expired',
      message: 'Device code expired. Start again.'
    };
  }

  if (now < session.nextPollAt) {
    return {
      ok: true,
      status: 'pending',
      retryInMs: Math.max(250, session.nextPollAt - now),
      message: 'Waiting for Microsoft confirmation...'
    };
  }

  session.nextPollAt = now + session.intervalMs;
  msDeviceSessions.set(key, session);

  try {
    const token = await postFormJson(`https://login.microsoftonline.com/${session.tenant || 'consumers'}/oauth2/v2.0/token`, {
      grant_type: 'urn:ietf:params:oauth:grant-type:device_code',
      client_id: session.clientId || MS_DEVICE_CLIENT_ID,
      device_code: session.deviceCode
    });

    const msAccessToken = String(token.access_token || '');
    const msRefreshToken = String(token.refresh_token || '');
    if (!msAccessToken) {
      throw new Error('Microsoft access token missing.');
    }

    const mcData = await exchangeMicrosoftToMinecraft(msAccessToken);
    const profile = mcData.profile || {};
    const expiresInSec = Math.max(60, Number(token.expires_in || 3600));
    const expiresAt = new Date(Date.now() + expiresInSec * 1000).toISOString();

    const config = normalizeConfig(readJson(CONFIG_PATH));
    const state = normalizeStateForProfiles(readJson(STATE_PATH), config.profiles, config.alts);
    const alt = makeMicrosoftAlt(config, profile, {
      msAccessToken,
      msRefreshToken,
      mcAccessToken: mcData.mcAccessToken,
      expiresAt
    });

    const existingIndex = (config.alts || []).findIndex((entry) => entry.id === alt.id);
    if (existingIndex >= 0) {
      config.alts[existingIndex] = alt;
    } else {
      config.alts.push(alt);
    }
    writeJson(CONFIG_PATH, normalizeConfig(config));

    state.selectedAltId = alt.id;
    writeJson(STATE_PATH, normalizeStateForProfiles(state, config.profiles, config.alts));

    msDeviceSessions.delete(key);
    appendMainLog(`ms-device-poll: success session=${key} alt=${alt.username}`);

    return {
      ok: true,
      status: 'success',
      alt: publicAlt(alt),
      message: `Microsoft alt added: ${alt.username}`
    };
  } catch (error) {
    const tokenCode = parseMsTokenError(error);
    if (tokenCode === 'authorization_pending') {
      return {
        ok: true,
        status: 'pending',
        retryInMs: session.intervalMs,
        message: 'Waiting for confirmation at microsoft.com/link...'
      };
    }
    if (tokenCode === 'slow_down') {
      session.intervalMs += 3000;
      session.nextPollAt = Date.now() + session.intervalMs;
      msDeviceSessions.set(key, session);
      return {
        ok: true,
        status: 'pending',
        retryInMs: session.intervalMs,
        message: 'Slowing down polling...'
      };
    }
    if (tokenCode === 'authorization_declined' || tokenCode === 'expired_token' || tokenCode === 'bad_verification_code') {
      msDeviceSessions.delete(key);
      return {
        ok: false,
        status: 'failed',
        message: 'Microsoft login was canceled or expired.'
      };
    }
    if (tokenCode === 'invalid_app_registration') {
      msDeviceSessions.delete(key);
      recoverMsAuthToCompatDefaults();
      const customHint = (session.clientId || '').trim() === MS_CUSTOM_APP_ID
        ? 'Your custom Azure app cannot complete Xbox/Minecraft auth.'
        : 'This app registration cannot complete Xbox/Minecraft auth.';
      return {
        ok: false,
        status: 'failed',
        message: `${customHint} Switched launcher to compatible Microsoft login defaults. Start login again.`
      };
    }

    msDeviceSessions.delete(key);
    appendMainLog(`ms-device-poll: failed session=${key} err=${error && error.message ? error.message : error}`);
    return {
      ok: false,
      status: 'failed',
      message: `Microsoft login failed: ${error.message}`
    };
  }
});

ipcMain.handle('launcher:ms-device-cancel', async (_event, sessionId) => {
  const key = String(sessionId || '');
  if (msDeviceSessions.has(key)) {
    msDeviceSessions.delete(key);
  }
  return { ok: true };
});

ipcMain.handle('launcher:ms-web-start', async () => {
  appendMainLog('ms-web-start: disabled (deprecated email/web flow); use device login');
  return {
    ok: false,
    message: 'Microsoft Email Login is disabled. Use "Login with Microsoft" (device code).'
  };
});

ipcMain.handle('launcher:ms-web-poll', async (_event, sessionId) => {
  msWebSessions.delete(String(sessionId || ''));
  return {
    ok: false,
    status: 'failed',
    message: 'Microsoft Email Login is disabled. Use "Login with Microsoft" (device code).'
  };
});

ipcMain.handle('launcher:ms-web-cancel', async (_event, sessionId) => {
  const key = String(sessionId || '');
  const session = msWebSessions.get(key);
  if (!session) {
    return { ok: true };
  }
  if (session.timer) {
    clearTimeout(session.timer);
    session.timer = null;
  }
  try {
    if (session.authWindow && !session.authWindow.isDestroyed()) {
      session.authWindow.close();
    }
  } catch {
    // ignore
  }
  msWebSessions.delete(key);
  return { ok: true };
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
  appendMainLog(`launch-profile: request profileId=${payload && payload.profileId ? payload.profileId : 'none'} inProgress=${launchInProgress}`);
  if (launchInProgress) {
    return {
      ok: false,
      message: 'Launch already in progress. Please wait a few seconds.',
      mainLog: MAIN_LOG_PATH
    };
  }

  try {
    launchInProgress = true;
    const config = normalizeConfig(readJson(CONFIG_PATH));
    const launcherState = normalizeStateForProfiles(readJson(STATE_PATH), config.profiles, config.alts);
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
    const activeAlt = (config.alts || []).find((alt) => alt.id === launcherState.selectedAltId)
      || (config.alts || [])[0]
      || null;
    const commandWithAlt = withUsernameArg(commandFilled, activeAlt?.username || payload?.altUsername || '');
    const optimizedCommand = optimizeLaunchCommand(commandWithAlt);

    const workdir = resolveWorkdir(profile);
    const launchCommand = makeCommandLauncherFriendly(optimizedCommand, workdir);
    const gradleJavaHome = isGradleLaunchCommand(launchCommand) ? resolveGradleJavaHome(profile) : '';
    appendMainLog(`launch-profile: profile=${profile.id} alt=${activeAlt ? activeAlt.username : 'none'} workdir=${workdir} cmd=${launchCommand} javaHome=${gradleJavaHome || 'auto'}`);
    fs.appendFileSync(LAUNCH_LOG_PATH, `[${new Date().toISOString()}] launch attempt: ${launchCommand}\n`, 'utf8');

    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const launchRunLogPath = path.join(DATA_DIR, `launch-${stamp}.log`);
    const launchScriptPath = path.join(DATA_DIR, `launch-${stamp}.bat`);
    fs.writeFileSync(launchRunLogPath, `[${new Date().toISOString()}] launch attempt: ${launchCommand}\n`, 'utf8');
    const child = process.platform === 'win32'
      ? (() => {
          // Run via generated .bat script to avoid fragile cmd quote parsing.
          const script = [
            '@echo off',
            'setlocal EnableExtensions',
            `cd /d ${quoteForCmd(workdir)}`,
            ...(gradleJavaHome ? [
              `set "JAVA_HOME=${gradleJavaHome}"`,
              'set "PATH=%JAVA_HOME%\\bin;%PATH%"',
              `echo [JAVA_HOME %DATE% %TIME%] %JAVA_HOME%>> ${quoteForCmd(launchRunLogPath)}`
            ] : []),
            `echo [START %DATE% %TIME%] ${launchCommand}>> ${quoteForCmd(launchRunLogPath)}`,
            `call ${launchCommand} >> ${quoteForCmd(launchRunLogPath)} 2>&1`,
            'set CODE=%ERRORLEVEL%',
            `echo [END %DATE% %TIME%] exit=%CODE%>> ${quoteForCmd(launchRunLogPath)}`,
            'exit /b %CODE%'
          ].join('\r\n');
          fs.writeFileSync(launchScriptPath, script, 'utf8');

          const launchArgs = ['/d', '/c', 'call', launchScriptPath];
          appendMainLog(`launch-profile: cmd=cmd.exe ${launchArgs.join(' ')}`);

          return spawn('cmd.exe', launchArgs, {
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
      alt: activeAlt ? activeAlt.username : null,
      launchScript: process.platform === 'win32' ? launchScriptPath : null,
      launchLog: process.platform === 'win32' ? launchRunLogPath : null,
      latestLog: process.platform === 'win32' ? LAUNCH_LOG_PATH : null,
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
  } finally {
    setTimeout(() => {
      launchInProgress = false;
    }, 1200);
  }
});
