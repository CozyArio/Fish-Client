# Fish Client With Official Minecraft Launcher

Use this when you want Microsoft account login from the official Minecraft Launcher and still run Fish Client.

## One-Click Install

1. Double-click:
   - `C:\Users\motyl\Documents\AI CODING PEHNIS\Fish Client\install-official-launcher.bat`
2. It will:
   - build latest Fish Client jar
   - copy it to `%APPDATA%\.minecraft\mods`
   - download/copy matching `fabric-api` jar
   - auto-install Fabric Loader `1.21.11` profile if missing
   - create/update launcher profile: `Fish Client 1.21.11`

## Launch In Official Launcher

1. Open official Minecraft Launcher.
2. Log in with Microsoft account.
3. Select profile: `Fish Client 1.21.11`.
4. Click Play.

If `Fish Client 1.21.11` is not shown, run `install-official-launcher.bat` once more.

## Reinstall After Code Changes

Run `install-official-launcher.bat` again.
