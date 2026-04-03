# Fish Client

Fish Client is a Fabric-based Minecraft client for **1.21.11** with custom modules, ClickGUI, HUD/ESP features, config management, and launcher tooling.

## Quick Start

### Option A: Official Minecraft Launcher (recommended)
1. Run `install-official-launcher.bat`.
2. Open official Minecraft Launcher.
3. Select profile `Fish Client 1.21.11` and click Play.

Full guide: [OFFICIAL_LAUNCHER_SETUP.md](OFFICIAL_LAUNCHER_SETUP.md)

### Option B: Manual install (mods folder)
1. Build jar:
   - `./gradlew remapJar`
2. Copy output jar from:
   - `build/libs/fishclient-1.1.0.jar`
3. Put it in your `.minecraft/mods` folder with matching Fabric + Fabric API.

## Development

### Requirements
- Java 21
- Gradle Wrapper (`gradlew` / `gradlew.bat`)

### Build
- `./gradlew remapJar`

### Run dev client
- `./gradlew runClient`

## Included Systems
- ClickGUI with module categories and settings
- HUD + ArrayList overlay
- Runtime gameplay modules (movement/combat/world/render)
- Config save/load manager
- Alt/account and Microsoft auth helpers
- Discord RPC integration
- Render mixins (including first-person item scale module)

## Project Docs
- Setup with official launcher: [OFFICIAL_LAUNCHER_SETUP.md](OFFICIAL_LAUNCHER_SETUP.md)
- Add more modules: [ADDING_MODULES.md](ADDING_MODULES.md)
- Electron launcher docs: [launcher/README.md](launcher/README.md)

## Release
Latest release page:
- [GitHub Releases](https://github.com/CozyArio/Fish-Client/releases)

## License
No license file is currently defined in this repository.
