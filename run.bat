@echo off
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set "UDS_PROJECT_ROOT=%CD%"
set "UDS_APP_HOME=%CD%"

if not exist "output" mkdir "output"
set "UDS_LAUNCH_LOG=%CD%\output\launch.log"

REM --- Explorer: hide build/dev clutter (Cursor/Maven still see them) ---
attrib +H "src" >nul 2>&1
attrib +H "test" >nul 2>&1
attrib +H "doc" >nul 2>&1
attrib +H "target" >nul 2>&1
attrib +H ".mvn" >nul 2>&1
attrib +H "mvnw" >nul 2>&1
attrib +H "mvnw.cmd" >nul 2>&1
attrib +H ".cursorrules" >nul 2>&1
attrib +H "pom.xml" >nul 2>&1

REM --- One-shot cleanup of legacy output clutter ---
if exist "output\gui-session" rd /s /q "output\gui-session" 2>nul
if exist "output\sessions" rd /s /q "output\sessions" 2>nul
if exist "output\smoke-gui" rd /s /q "output\smoke-gui" 2>nul
del /q "output\cp.txt" 2>nul
del /q "output\jdk25_run.log" 2>nul
del /q "output\run_bat_try.log" 2>nul
del /q "output\run_diag.log" 2>nul
del /q "output\launch-crash.log" 2>nul

if not exist "output\.session" mkdir "output\.session"
attrib +H "output\.session" >nul 2>&1

> "%UDS_LAUNCH_LOG%" echo ===== UDS launch %DATE% %TIME% =====
>> "%UDS_LAUNCH_LOG%" echo project=%UDS_PROJECT_ROOT%

call "%~dp0tools\resolve-runtime.bat"
if errorlevel 1 (
  echo See: %UDS_LAUNCH_LOG%
  pause
  exit /b 1
)

echo JAVA_HOME=%JAVA_HOME%
echo java=%UDS_JAVA_VERSION%
echo mvn=%UDS_MVN_CMD%
>> "%UDS_LAUNCH_LOG%" echo JAVA_HOME=%JAVA_HOME%
>> "%UDS_LAUNCH_LOG%" echo java=%UDS_JAVA_VERSION%
>> "%UDS_LAUNCH_LOG%" echo mvn=%UDS_MVN_CMD%

REM JDK 25: Maven 자체(Jansi) native-access 경고 완화
if not defined MAVEN_OPTS set "MAVEN_OPTS=--enable-native-access=ALL-UNNAMED"

echo.
echo Starting UDS Diagnostic GUI...
echo Log: %UDS_LAUNCH_LOG%
echo Output: %UDS_PROJECT_ROOT%\output\
echo.

call "%UDS_MVN_CMD%" -f "%UDS_PROJECT_ROOT%\pom.xml" javafx:run
set "ERR=!ERRORLEVEL!"
>> "%UDS_LAUNCH_LOG%" echo javafx:run exit=!ERR!

if not "!ERR!"=="0" (
  echo.
  echo ERROR: launch failed ^(exit !ERR!^)
  echo If the window closed immediately, check:
  echo   %UDS_LAUNCH_LOG%
  echo   %UDS_PROJECT_ROOT%\output\crash.log
  pause
  exit /b !ERR!
)

exit /b 0
