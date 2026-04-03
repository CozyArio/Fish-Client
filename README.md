# Fish Client

Fish Client is a Fabric mod/client for Minecraft **1.21.11**.

## Install (Recommended)
1. Download the latest jar from [GitHub Releases](https://github.com/CozyArio/Fish-Client/releases).
2. Install Fabric Loader for Minecraft `1.21.11`.
3. Put `fishclient-*.jar` into your `.minecraft/mods` folder.
4. Make sure Fabric API for `1.21.11` is also in `.minecraft/mods`.
5. Launch the Fabric profile.

## Build From Source
### Requirements
- Java 21
- Gradle Wrapper (`gradlew` / `gradlew.bat`)

### Commands
- Build jar: `./gradlew remapJar`
- Run dev client: `./gradlew runClient`

Build output:
- `build/libs/fishclient-1.1.0.jar`

## Features
- ClickGUI with module categories and settings
- HUD + ArrayList overlay
- Runtime gameplay modules (movement/combat/world/render)
- Config save/load manager
- Alt/account and Microsoft auth helpers
- Discord RPC integration
- Render mixins (including first-person item scale module)

## Docs
- Add more modules: [ADDING_MODULES.md](ADDING_MODULES.md)

## License
No license file is currently defined in this repository.
