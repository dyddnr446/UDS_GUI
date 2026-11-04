@echo off
chcp 65001 >nul
cd /d "%~dp0"
title UDS Diagnostic GUI

REM Ensure Maven is visible when launched by double-click (Explorer PATH)
if exist "%USERPROFILE%\apache-maven-3.9.9\bin\mvn.cmd" set "PATH=%USERPROFILE%\apache-maven-3.9.9\bin;%PATH%"
if exist "%USERPROFILE%\apache-maven-3.9.6\bin\mvn.cmd" set "PATH=%USERPROFILE%\apache-maven-3.9.6\bin;%PATH%"
if exist "C:\Program Files\Apache\maven\bin\mvn.cmd" set "PATH=C:\Program Files\Apache\maven\bin;%PATH%"
if exist "%MAVEN_HOME%\bin\mvn.cmd" set "PATH=%MAVEN_HOME%\bin;%PATH%"

where mvn >nul 2>&1
if errorlevel 1 (
  echo.
  echo [ERROR] Maven(mvn) not found.
  echo Install Maven or set MAVEN_HOME, then try again.
  echo.
  pause
  exit /b 1
)

where java >nul 2>&1
if errorlevel 1 (
  echo.
  echo [ERROR] Java not found on PATH.
  echo.
  pause
  exit /b 1
)

if not exist "%~dp0.env" (
  echo Tip: copy .env.example to .env and set UDS_AI_API_KEY for AI features.
  echo.
)

echo.
echo Starting UDS Diagnostic GUI...
echo Project: %CD%
echo Keep this window open while the app runs.
echo.

mvn javafx:run
set ERR=%ERRORLEVEL%
echo.
if not "%ERR%"=="0" (
  echo [ERROR] Launch failed. Exit code %ERR%
  echo Scroll up for the Maven/JavaFX error.
) else (
  echo App closed.
)
echo.
pause
exit /b %ERR%
