@echo off
REM ASCII/CRLF launcher - delegates to run.ps1 (avoids UTF-16 / LF batch breakage)
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
set ERR=%ERRORLEVEL%
if not "%ERR%"=="0" (
  echo.
  echo Launch failed. If this window showed EnableExtensions/xist errors,
  echo right-click run.ps1 -^> Run with PowerShell
  echo Or: powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
  pause
)
exit /b %ERR%
