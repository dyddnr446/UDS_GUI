@echo off
setlocal EnableExtensions
cd /d "%~dp0"

REM --- OS gate ---
if /I not "%OS%"=="Windows_NT" (
  echo ERROR: Windows only. This project is launched with run.bat on Windows.
  exit /b 1
)

REM --- PowerShell required for JDK/Maven resolve ---
where powershell >nul 2>&1
if errorlevel 1 (
  echo ERROR: PowerShell not found. Windows PowerShell is required.
  pause
  exit /b 1
)

if not exist "%~dp0tools\launch-gui.ps1" (
  echo ERROR: missing tools\launch-gui.ps1
  pause
  exit /b 1
)

if not exist "%~dp0pom.xml" (
  echo ERROR: pom.xml not found. Run this bat from the project root.
  pause
  exit /b 1
)

set "UDS_FROM_BAT=1"
set "UDS_PROJECT_ROOT=%CD%"
set "UDS_APP_HOME=%CD%"

echo Starting via run.bat ...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\launch-gui.ps1"
set "ERR=%ERRORLEVEL%"

if not "%ERR%"=="0" (
  echo.
  echo ERROR: launch failed ^(exit %ERR%^)
  echo Log: %CD%\output\launch.log
  pause
  exit /b %ERR%
)

exit /b 0
