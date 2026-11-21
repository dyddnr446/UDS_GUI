@echo off
REM Resolve JDK 25+ and Maven. call this file; exports JAVA_HOME, PATH, UDS_MVN_CMD, UDS_JAVA_VERSION.
setlocal EnableExtensions EnableDelayedExpansion

REM Ensure core Windows tools (powershell needed by mvnw only-script wrapper)
set "PATH=%SystemRoot%\System32;%SystemRoot%\System32\WindowsPowerShell\v1.0;%PATH%"

set "FOUND_HOME="
set "FOUND_MVN="
set "FOUND_VER="
set "MAJOR=0"

REM --- JDK ---
if defined JAVA_HOME if exist "!JAVA_HOME!\bin\java.exe" (
  call :major "!JAVA_HOME!\bin\java.exe"
  if !MAJOR! GEQ 25 (
    set "FOUND_HOME=!JAVA_HOME!"
    set "FOUND_VER=!VER!"
    goto :jdk_ok
  )
)

for %%R in (
  "%ProgramFiles%\Eclipse Adoptium"
  "%ProgramFiles%\Java"
  "%LOCALAPPDATA%\Programs\Eclipse Adoptium"
) do (
  if exist "%%~R" for /f "delims=" %%D in ('dir /b /ad /o-n "%%~R\jdk-25*" 2^>nul') do (
    if exist "%%~R\%%D\bin\java.exe" (
      set "FOUND_HOME=%%~R\%%D"
      call :major "!FOUND_HOME!\bin\java.exe"
      set "FOUND_VER=!VER!"
      goto :jdk_ok
    )
  )
)

where java >nul 2>&1
if not errorlevel 1 (
  for /f "delims=" %%J in ('where java') do (
    call :major "%%J"
    if !MAJOR! GEQ 25 (
      pushd "%%~dpJ.."
      set "FOUND_HOME=!CD!"
      popd
      set "FOUND_VER=!VER!"
      goto :jdk_ok
    )
  )
)

echo ERROR: JDK 25+ not found. Install Temurin 25 or set JAVA_HOME.
exit /b 1

:jdk_ok
REM --- Maven: prefer installed mvn (no PowerShell), then wrapper, then common dirs ---
where mvn.cmd >nul 2>&1
if not errorlevel 1 (
  for /f "delims=" %%M in ('where mvn.cmd') do (
    set "FOUND_MVN=%%M"
    goto :mvn_ok
  )
)

for /f "delims=" %%D in ('dir /b /ad /o-n "%USERPROFILE%\apache-maven-*" 2^>nul') do (
  if exist "%USERPROFILE%\%%D\bin\mvn.cmd" (
    set "FOUND_MVN=%USERPROFILE%\%%D\bin\mvn.cmd"
    goto :mvn_ok
  )
)
if exist "C:\apache-maven-3.9.9\bin\mvn.cmd" (
  set "FOUND_MVN=C:\apache-maven-3.9.9\bin\mvn.cmd"
  goto :mvn_ok
)

if defined UDS_PROJECT_ROOT if exist "!UDS_PROJECT_ROOT!\mvnw.cmd" (
  set "FOUND_MVN=!UDS_PROJECT_ROOT!\mvnw.cmd"
  goto :mvn_ok
)
if exist "%CD%\mvnw.cmd" (
  set "FOUND_MVN=%CD%\mvnw.cmd"
  goto :mvn_ok
)

echo ERROR: Maven not found. Install Maven or keep project mvnw.cmd.
exit /b 1

:mvn_ok
for %%A in ("!FOUND_HOME!") do for %%B in ("!FOUND_MVN!") do for %%C in ("!FOUND_VER!") do (
  endlocal & set "JAVA_HOME=%%~A" & set "UDS_MVN_CMD=%%~B" & set "UDS_JAVA_VERSION=%%~C" & set "PATH=%SystemRoot%\System32;%SystemRoot%\System32\WindowsPowerShell\v1.0;%%~A\bin;%PATH%"
)
exit /b 0

:major
set "MAJOR=0"
set "VER="
set "JAVA_EXE=%~1"
for /f "tokens=3 delims= " %%V in ('"!JAVA_EXE!" -version 2^>^&1 ^| findstr /i version') do (
  set "VER=%%~V"
  goto :maj_parse
)
exit /b 0
:maj_parse
set "VER=!VER:"=!"
for /f "tokens=1 delims=." %%M in ("!VER!") do set "MAJOR=%%M"
exit /b 0
