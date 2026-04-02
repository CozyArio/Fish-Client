const state = {
  config: null,
  launcherState: null,
  modpack: null,
  authUser: null,
  selectedTab: 'home'
};

const el = {};

function byId(id) {
  return document.getElementById(id);
}

async function init() {
  bindElements();
  wireWindowButtons();
  wireTabs();

  const bootstrap = await window.fishLauncher.getBootstrap();
  state.config = bootstrap.config;
  state.launcherState = bootstrap.state;
  state.modpack = bootstrap.modpack;
  state.authUser = bootstrap.authUser;

  seedParticles();
  renderAll();

  if (!isAuthenticated()) {
    showLoginGate();
    setStatus('Please login to continue.');
  } else {
    hideLoginGate();
    setStatus(`Welcome back, ${state.authUser.displayName}.`);
  }
}

function bindElements() {
  el.accountChip = byId('accountChip');
  el.profileChip = byId('profileChip');
  el.heroCard = byId('heroCard');
  el.heroKicker = byId('heroKicker');
  el.heroTitle = byId('heroTitle');
  el.heroSub = byId('heroSub');
  el.heroMeta = byId('heroMeta');
  el.versionGrid = byId('versionGrid');
  el.playBtn = byId('playBtn');
  el.openHomeLinkBtn = byId('openHomeLinkBtn');
  el.statusText = byId('statusText');
  el.modsList = byId('modsList');
  el.launchCommandInput = byId('launchCommandInput');
  el.selectedVersionInput = byId('selectedVersionInput');
  el.saveSettingsBtn = byId('saveSettingsBtn');
  el.openConfigBtn = byId('openConfigBtn');
  el.supportBtn = byId('supportBtn');
  el.reloadModsBtn = byId('reloadModsBtn');
  el.openFolderBtn = byId('openFolderBtn');
  el.profilesCount = byId('profilesCount');
  el.recentList = byId('recentList');
  el.clearRecentBtn = byId('clearRecentBtn');
  el.lastLaunchText = byId('lastLaunchText');
  el.modsCountText = byId('modsCountText');
  el.runtimeText = byId('runtimeText');

  el.accountName = byId('accountName');
  el.accountRank = byId('accountRank');
  el.accountSub = byId('accountSub');
  el.accountStatus = byId('accountStatus');
  el.accountAvatar = byId('accountAvatar');
  el.logoutBtn = byId('logoutBtn');

  el.loginGate = byId('loginGate');
  el.loginForm = byId('loginForm');
  el.loginUsername = byId('loginUsername');
  el.loginPassword = byId('loginPassword');
  el.loginSubmit = byId('loginSubmit');
  el.loginError = byId('loginError');

  el.playBtn.addEventListener('click', launchSelected);
  el.openHomeLinkBtn.addEventListener('click', () => openQuickLink('home'));
  el.saveSettingsBtn.addEventListener('click', saveSettings);
  el.openConfigBtn.addEventListener('click', () => window.fishLauncher.openPath('./data'));
  el.supportBtn.addEventListener('click', () => openQuickLink('support'));
  el.reloadModsBtn.addEventListener('click', reloadModpackFromDisk);
  el.openFolderBtn.addEventListener('click', () => window.fishLauncher.openPath('..'));
  el.clearRecentBtn.addEventListener('click', clearRecent);
  el.logoutBtn.addEventListener('click', logout);
  el.loginForm.addEventListener('submit', onLoginSubmit);
}

function wireWindowButtons() {
  document.querySelectorAll('[data-win]').forEach((btn) => {
    btn.addEventListener('click', () => {
      window.fishLauncher.windowAction(btn.dataset.win);
    });
  });
}

function wireTabs() {
  const sideButtons = document.querySelectorAll('.side-btn[data-tab]');
  sideButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      state.selectedTab = btn.dataset.tab;
      sideButtons.forEach((b) => b.classList.toggle('active', b === btn));
      document.querySelectorAll('.tab-panel').forEach((panel) => {
        panel.classList.toggle('active', panel.id === `${state.selectedTab}Tab`);
      });
      setStatus(`Opened ${state.selectedTab} tab.`);
    });
  });
}

function selectedProfile() {
  const profiles = Array.isArray(state.config?.profiles) ? state.config.profiles : [];
  const target = state.launcherState?.selectedProfileId;
  return profiles.find((p) => p.id === target) || profiles[0] || null;
}

function isAuthenticated() {
  return Boolean(state.launcherState?.authSession?.loggedIn && state.authUser);
}

function renderAll() {
  renderHeader();
  renderHome();
  renderMods();
  renderSettings();
  renderAccount();
}

function renderHeader() {
  if (state.authUser) {
    const sub = state.authUser.subscriptionType || 'Lifetime';
    el.accountChip.textContent = `${state.authUser.displayName} • ${state.authUser.rankIcon || '🐟'} ${state.authUser.rankName || 'Fish'} • ${sub}`;
  } else {
    el.accountChip.textContent = 'Not logged in';
  }

  const profile = selectedProfile();
  if (profile) {
    el.profileChip.textContent = `${profile.name} | ${profile.gameVersion} | ${profile.loader}`;
  } else {
    el.profileChip.textContent = 'No profile loaded';
  }
}

function renderHome() {
  const profile = selectedProfile();
  if (!profile) {
    el.heroTitle.textContent = 'No profile configured';
    el.heroSub.textContent = 'Create one in launcher-config.json';
    el.versionGrid.innerHTML = '';
    return;
  }

  const selectedVersion = state.launcherState.selectedVersion || profile.gameVersion;

  el.heroKicker.textContent = profile.branch || 'Stable';
  el.heroTitle.textContent = `Minecraft ${selectedVersion}`;
  el.heroSub.textContent = profile.name;
  el.heroMeta.textContent = `${profile.loader || 'Fabric'} • ${profile.jvmArgs || '-Xmx4G'} • ${profile.gameDir || 'Project Workspace'}`;
  el.runtimeText.textContent = profile.javaPath && profile.javaPath.trim() ? profile.javaPath : 'Java Auto';

  applyHeroBackground(profile);

  const profiles = state.config.profiles || [];
  el.profilesCount.textContent = `${profiles.length} total`;

  el.versionGrid.innerHTML = '';
  profiles.forEach((profileItem, index) => {
    const card = document.createElement('button');
    card.className = 'version-card';
    card.style.setProperty('--card-bg', pickCardBackground(index, profileItem));

    const active = profileItem.id === state.launcherState.selectedProfileId;
    if (active) {
      card.classList.add('selected');
    }

    card.innerHTML = `
      <div class="version-top">
        <span class="version-tag">${escapeHtml(profileItem.branch || 'Stable')}</span>
        <span class="loader-tag">${escapeHtml(profileItem.loader || 'Fabric')}</span>
      </div>
      <h4 class="version-title">${escapeHtml(profileItem.name)}</h4>
      <div class="version-sub">${escapeHtml(profileItem.gameVersion || '1.21.11')} • ${escapeHtml(profileItem.id)}</div>
    `;

    card.addEventListener('click', async () => {
      state.launcherState.selectedProfileId = profileItem.id;
      state.launcherState.selectedVersion = profileItem.gameVersion;
      await persistState();
      renderAll();
      setStatus(`Selected profile: ${profileItem.name}`);
    });

    el.versionGrid.appendChild(card);
  });

  renderRecent();
  renderStats();
}

function renderStats() {
  const recent = Array.isArray(state.launcherState.recentLaunches) ? state.launcherState.recentLaunches : [];
  if (!recent.length) {
    el.lastLaunchText.textContent = 'Never';
  } else {
    const dt = new Date(recent[0].at);
    el.lastLaunchText.textContent = Number.isNaN(dt.getTime()) ? recent[0].at : dt.toLocaleString();
  }

  const mods = (state.modpack && state.modpack.mods) || [];
  const enabled = mods.filter((m) => m.enabled).length;
  el.modsCountText.textContent = `${enabled}/${mods.length}`;
}

function renderRecent() {
  const recent = Array.isArray(state.launcherState.recentLaunches) ? state.launcherState.recentLaunches : [];
  el.recentList.innerHTML = '';

  if (!recent.length) {
    const empty = document.createElement('div');
    empty.className = 'recent-item';
    empty.innerHTML = '<div class="recent-meta"><strong>No launches yet</strong><span>Hit Launch to start a session.</span></div>';
    el.recentList.appendChild(empty);
    return;
  }

  recent.slice(0, 6).forEach((item) => {
    const profile = (state.config.profiles || []).find((p) => p.id === item.profileId);
    const row = document.createElement('div');
    row.className = 'recent-item';

    const when = new Date(item.at);
    const whenText = Number.isNaN(when.getTime()) ? item.at : when.toLocaleString();

    row.innerHTML = `
      <div class="recent-meta">
        <strong>${escapeHtml(profile ? profile.name : item.profileId)}</strong>
        <span>${escapeHtml(item.version)} • ${escapeHtml(whenText)}</span>
      </div>
      <span class="version-tag">${escapeHtml((item.command || '').slice(0, 28) || 'launch')}</span>
    `;

    el.recentList.appendChild(row);
  });
}

function renderAccount() {
  if (!state.authUser) {
    el.accountName.textContent = 'Not logged in';
    el.accountRank.textContent = 'Rank: -';
    el.accountSub.textContent = 'Subscription: -';
    el.accountStatus.textContent = 'Status: Offline';
    el.accountAvatar.textContent = '🐟';
    return;
  }

  const rankIcon = state.authUser.rankIcon || '🐟';
  const rankName = state.authUser.rankName || 'Fish';
  const sub = state.authUser.subscriptionType || 'Lifetime';
  const expires = state.authUser.expiresAt ? ` (expires ${state.authUser.expiresAt})` : '';

  el.accountAvatar.textContent = rankIcon;
  el.accountName.textContent = state.authUser.displayName;
  el.accountRank.textContent = `Rank: ${rankIcon} ${rankName}`;
  el.accountSub.textContent = `Subscription: ${sub}${expires}`;
  el.accountStatus.textContent = 'Status: Online';
}

function applyHeroBackground(profile) {
  const imgPath = profile.image && profile.image.trim() ? profile.image.trim() : '';
  if (!imgPath) {
    el.heroCard.style.removeProperty('--hero-bg');
    return;
  }

  const css = `
    linear-gradient(180deg, rgba(4,6,10,0.12), rgba(4,6,10,0.72)),
    url('${imgPath.replaceAll('\\', '/')}') center/cover no-repeat,
    linear-gradient(125deg, #404862, #1b2030 54%, #121726)
  `;
  el.heroCard.style.setProperty('--hero-bg', css);
}

function pickCardBackground(index, profile) {
  if (profile.image && profile.image.trim()) {
    return `linear-gradient(180deg, rgba(5,8,12,0.2), rgba(6,8,13,0.84)), url('${profile.image.replaceAll('\\', '/')}') center/cover no-repeat`;
  }

  const presets = [
    'linear-gradient(140deg, rgba(70,80,108,0.92), rgba(25,30,45,0.95))',
    'linear-gradient(140deg, rgba(59,73,113,0.92), rgba(20,25,42,0.95))',
    'linear-gradient(140deg, rgba(88,66,116,0.92), rgba(23,20,39,0.95))'
  ];
  return presets[index % presets.length];
}

async function reloadModpackFromDisk() {
  const bootstrap = await window.fishLauncher.getBootstrap();
  state.modpack = bootstrap.modpack;
  renderMods();
  renderStats();
  setStatus('Reloaded local modpack file.');
}

function renderMods() {
  const mods = (state.modpack && state.modpack.mods) || [];
  el.modsList.innerHTML = '';

  if (!mods.length) {
    const empty = document.createElement('div');
    empty.className = 'mod-item';
    empty.innerHTML = '<div class="mod-meta"><strong>No mods in modpack.json</strong><span class="mod-version">Add entries in launcher/data/modpack.json</span></div>';
    el.modsList.appendChild(empty);
    return;
  }

  mods.forEach((mod, index) => {
    const card = document.createElement('div');
    card.className = 'mod-item';

    const toggle = document.createElement('button');
    toggle.className = `mod-toggle ${mod.enabled ? 'on' : ''}`;
    toggle.textContent = mod.enabled ? 'Enabled' : 'Disabled';
    toggle.addEventListener('click', async () => {
      mod.enabled = !mod.enabled;
      state.modpack.mods[index] = mod;
      state.modpack = await window.fishLauncher.saveModpack(state.modpack);
      renderMods();
      renderStats();
      setStatus(`${mod.name}: ${mod.enabled ? 'enabled' : 'disabled'}`);
    });

    const meta = document.createElement('div');
    meta.className = 'mod-meta';
    meta.innerHTML = `
      <strong>${escapeHtml(mod.name)}</strong>
      <span class="mod-version">${escapeHtml(mod.version || 'unknown')} • ${escapeHtml(mod.source || 'local')}</span>
    `;

    card.append(meta, toggle);
    el.modsList.appendChild(card);
  });
}

function renderSettings() {
  const profile = selectedProfile();
  if (!profile) {
    el.launchCommandInput.value = '';
    el.selectedVersionInput.value = '';
    return;
  }

  el.launchCommandInput.value = profile.launchCommandTemplate || '';
  el.selectedVersionInput.value = state.launcherState.selectedVersion || profile.gameVersion || '1.21.11';
}

async function saveSettings() {
  const profile = selectedProfile();
  if (!profile) {
    return;
  }

  profile.launchCommandTemplate = el.launchCommandInput.value.trim() || 'gradlew.bat runClient';
  state.launcherState.selectedVersion = el.selectedVersionInput.value.trim() || profile.gameVersion;

  await persistState();
  await persistConfig();
  renderAll();
  setStatus('Saved launcher settings.');
}

async function clearRecent() {
  state.launcherState.recentLaunches = [];
  await persistState();
  renderRecent();
  renderStats();
  setStatus('Cleared recent launches.');
}

async function persistState() {
  state.launcherState = await window.fishLauncher.saveState(state.launcherState);
}

async function persistConfig() {
  state.config = await window.fishLauncher.saveConfig(state.config);
}

async function launchSelected() {
  if (!isAuthenticated()) {
    showLoginGate();
    setStatus('Login required before launching.');
    return;
  }

  const profile = selectedProfile();
  if (!profile) {
    setStatus('No profile selected.');
    return;
  }

  const version = state.launcherState.selectedVersion || profile.gameVersion;
  setStatus(`Starting ${profile.name}...`);
  el.playBtn.disabled = true;
  el.playBtn.textContent = 'Launching...';

  try {
    const launch = await window.fishLauncher.launchProfile({
      profileId: profile.id,
      version
    });

    if (launch.ok) {
      state.launcherState.recentLaunches.unshift({
        profileId: profile.id,
        version,
        at: new Date().toISOString(),
        command: launch.command
      });
      state.launcherState.recentLaunches = state.launcherState.recentLaunches.slice(0, 20);
      await persistState();
      renderRecent();
      renderStats();

      const paths = [];
      if (launch.launchLog) {
        paths.push(`LaunchLog: ${launch.launchLog}`);
      }
      if (launch.mainLog) {
        paths.push(`MainLog: ${launch.mainLog}`);
      }

      setStatus(paths.length ? `${launch.message} ${paths.join(' | ')}` : launch.message);
    } else {
      const commandInfo = launch.command ? ` Command: ${launch.command}` : '';
      const mainLogInfo = launch.mainLog ? ` MainLog: ${launch.mainLog}` : '';
      setStatus((launch.message || 'Launch failed.') + commandInfo + mainLogInfo);
    }
  } catch (error) {
    setStatus(`Launch IPC error: ${error.message}`);
    console.error('launchSelected failed:', error);
  } finally {
    el.playBtn.disabled = false;
    el.playBtn.textContent = 'Launch';
  }
}

async function onLoginSubmit(event) {
  event.preventDefault();

  const username = el.loginUsername.value.trim();
  const password = el.loginPassword.value;
  if (!username || !password) {
    el.loginError.textContent = 'Enter username and password.';
    return;
  }

  el.loginSubmit.disabled = true;
  el.loginError.textContent = '';

  const result = await window.fishLauncher.authLogin({ username, password });
  el.loginSubmit.disabled = false;

  if (!result.ok) {
    el.loginError.textContent = result.message || 'Login failed.';
    return;
  }

  state.authUser = result.user;
  state.launcherState.authSession = result.authSession;
  await persistState();

  el.loginPassword.value = '';
  hideLoginGate();
  renderAll();
  setStatus(`Logged in as ${result.user.displayName}.`);
}

async function logout() {
  const result = await window.fishLauncher.authLogout();
  if (!result.ok) {
    setStatus('Logout failed.');
    return;
  }

  state.launcherState.authSession = result.authSession;
  state.authUser = null;
  await persistState();
  renderAll();
  showLoginGate();
  setStatus('Logged out.');
}

function showLoginGate() {
  el.loginGate.classList.remove('hidden');
  el.loginUsername.focus();
}

function hideLoginGate() {
  el.loginGate.classList.add('hidden');
}

function openQuickLink(key) {
  const link = state.config?.quickLinks?.[key];
  if (!link) {
    setStatus(`No URL configured for ${key}.`);
    return;
  }
  window.fishLauncher.openUrl(link);
}

function seedParticles() {
  const holder = byId('heroParticles');
  if (!holder) {
    return;
  }

  holder.innerHTML = '';
  for (let i = 0; i < 14; i++) {
    const star = document.createElement('span');
    star.textContent = i % 4 === 0 ? '✦' : '•';
    star.style.left = `${10 + Math.random() * 78}%`;
    star.style.top = `${25 + Math.random() * 55}%`;
    star.style.animationDuration = `${2.4 + Math.random() * 2.7}s`;
    star.style.animationDelay = `${Math.random() * 2.2}s`;
    holder.appendChild(star);
  }
}

function setStatus(text) {
  el.statusText.textContent = text;
}

function escapeHtml(input) {
  const s = String(input ?? '');
  return s
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

init().catch((error) => {
  console.error(error);
  const status = byId('statusText');
  if (status) {
    status.textContent = `Failed to initialize launcher: ${error.message}`;
  }
});
