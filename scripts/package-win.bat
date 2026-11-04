@echo off
setlocal
cd /d "%~dp0.."

where mvn >nul 2>&1
if errorlevel 1 (
  echo Maven not on PATH.
  exit /b 1
)
where jpackage >nul 2>&1
if errorlevel 1 (
  echo jpackage not on PATH. Need full JDK 17+.
  exit /b 1
)

echo [1/3] Maven package-win...
call mvn -q -Ppackage-win -DskipTests package
if errorlevel 1 exit /b 1

set "INPUT=target\jpackage-input"
set "MAIN_JAR=uds-diagnostic-gui-0.1.0-SNAPSHOT.jar"
if not exist "%INPUT%\%MAIN_JAR%" (
  echo Missing main jar under target\jpackage-input
  exit /b 1
)

echo [2/3] jpackage app-image...
taskkill /F /IM UdsDiagnosticGui.exe >nul 2>&1
if exist "dist\UdsGuiBuild" rmdir /s /q "dist\UdsGuiBuild"
if exist "dist\UdsDiagnosticGui" rmdir /s /q "dist\UdsDiagnosticGui"

REM $APPDIR is the ...\app folder, so module-path is $APPDIR\lib
jpackage --type app-image --name UdsDiagnosticGui --input "%INPUT%" --main-jar "%MAIN_JAR%" --main-class uds.gui.app.UdsGuiApplication --dest dist\UdsGuiBuild --java-options "--module-path=$APPDIR\lib" --java-options "--add-modules=javafx.controls,javafx.fxml" --java-options "-Dfile.encoding=UTF-8"
if errorlevel 1 (
  echo jpackage failed.
  exit /b 1
)

echo [3/3] Copy docs samples config...
xcopy /E /I /Y /Q "docs" "dist\UdsGuiBuild\UdsDiagnosticGui\docs"
xcopy /E /I /Y /Q "samples" "dist\UdsGuiBuild\UdsDiagnosticGui\samples"
xcopy /E /I /Y /Q "config" "dist\UdsGuiBuild\UdsDiagnosticGui\config"
copy /Y ".env.example" "dist\UdsGuiBuild\UdsDiagnosticGui\.env.example" >nul
move "dist\UdsGuiBuild\UdsDiagnosticGui" "dist\UdsDiagnosticGui" >nul
rmdir /s /q "dist\UdsGuiBuild" >nul 2>&1

echo.
echo Done: dist\UdsDiagnosticGui\UdsDiagnosticGui.exe
echo Double-click that file. For AI, copy .env.example to .env beside the exe.
endlocal
