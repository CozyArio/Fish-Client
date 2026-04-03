@echo off
setlocal
set SCRIPT_DIR=%~dp0
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%tools\install-official-launcher.ps1" -OpenModsDir
echo.
echo Press any key to close...
pause >nul
