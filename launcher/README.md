# Fish Launcher

Custom desktop launcher (Electron) for Fish Client with a Meow-inspired dark UI.

## Features
- Frameless custom window + controls
- Profile/version cards with selected state
- Play button that executes a configurable launch command
- Local modpack panel with toggleable entries
- Settings panel + persistent launcher config/state JSON files

## Run
```powershell
cd launcher
npm install
npm run start
```

## Data files
- `launcher/data/launcher-config.json`
- `launcher/data/launcher-state.json`
- `launcher/data/modpack.json`

## Launch command template
In `launcher-config.json`, each profile has:
- `launchCommandTemplate`

Default:
```text
gradlew.bat runClient
```

Template placeholders you can use:
- `{MC_VERSION}`
- `{JAVA}`
- `{JVM_ARGS}`
- `{GAME_DIR}`

Example:
```text
"C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe" {JVM_ARGS} -jar my-launcher.jar --version {MC_VERSION}
```

## Add more profiles
Add items to `profiles` in `launcher-config.json`.

## Add more mods
Add entries in `modpack.json` under `mods`.

## Notes
- Current launcher uses a command-template launch hook (simple and reliable).
- Full Microsoft auth + download pipeline can be added next phase.
