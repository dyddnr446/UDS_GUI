@echo off
REM Shortcut: launch packaged GUI (no Maven needed)
cd /d "%~dp0"
set "EXE=%~dp0dist\UdsDiagnosticGui\UdsDiagnosticGui.exe"
if not exist "%EXE%" (
  echo exe not found. Run scripts\package-win.bat first.
  pause
  exit /b 1
)
set "UDS_APP_HOME=%~dp0dist\UdsDiagnosticGui"
start "" "%EXE%"
