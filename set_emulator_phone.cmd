@echo off
setlocal
cd /d "%~dp0"

rem All UI text is printed by the PowerShell script, so this batch file stays ASCII-only.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0set_emulator_phone.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

rem Keep the window open when launched by double-click.
if "%~1"=="" pause

exit /b %EXIT_CODE%
