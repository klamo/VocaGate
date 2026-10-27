@echo off
setlocal EnableExtensions DisableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..") do set "REPO_ROOT=%%~fI"

set "JLINK="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jlink.exe" set "JLINK=%JAVA_HOME%\bin\jlink.exe"
if not defined JLINK for /f "delims=" %%P in ('where jlink.exe 2^>nul') do if not defined JLINK set "JLINK=%%P"
if not defined JLINK (
    echo Required JDK tool not found: jlink.exe 1>&2
    exit /b 1
)

set "JPACKAGE="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\jpackage.exe" set "JPACKAGE=%JAVA_HOME%\bin\jpackage.exe"
if not defined JPACKAGE for /f "delims=" %%P in ('where jpackage.exe 2^>nul') do if not defined JPACKAGE set "JPACKAGE=%%P"
if not defined JPACKAGE (
    echo Required JDK tool not found: jpackage.exe 1>&2
    exit /b 1
)

for /f "tokens=1,* delims==" %%A in ('findstr /B "vocagate.version=" "%REPO_ROOT%\gradle.properties"') do set "VERSION=%%B"
if "%VERSION%"=="" (
    echo Missing vocagate.version in gradle.properties 1>&2
    exit /b 1
)

set "TARGET=windows-x64"
set "STAGING=%REPO_ROOT%\desktop\build\release-staging\%TARGET%"
set "BUILD_ROOT=%REPO_ROOT%\desktop\build\release-packaging\%TARGET%"
set "RUNTIME=%BUILD_ROOT%\runtime"
set "APP_IMAGE_DESTINATION=%BUILD_ROOT%\app-image"
set "EXE_DESTINATION=%BUILD_ROOT%\exe"
set "RELEASE_DIR=%REPO_ROOT%\release\%VERSION%"
set "FINAL_ZIP=%RELEASE_DIR%\VocaGate-%VERSION%-windows-x64.zip"
set "FINAL_EXE=%RELEASE_DIR%\VocaGate-%VERSION%-windows-x64.exe"

if exist "%BUILD_ROOT%" rmdir /S /Q "%BUILD_ROOT%"
mkdir "%APP_IMAGE_DESTINATION%"
mkdir "%EXE_DESTINATION%"
mkdir "%RELEASE_DIR%"

call "%SCRIPT_DIR%prepare_mediapipe_sidecar.bat" %TARGET%
if errorlevel 1 exit /b 1

call "%REPO_ROOT%\gradlew.bat" :desktop:stageReleaseApp -PtargetPlatform=%TARGET%
if errorlevel 1 exit /b 1

set "SIDECAR=%STAGING%\app\sidecar\vocagate-mediapipe.exe"
set "MODEL=%STAGING%\app\sidecar\models\face_landmarker.task"
if not exist "%SIDECAR%" (
    echo Missing staged MediaPipe sidecar: %SIDECAR% 1>&2
    exit /b 1
)
"%SIDECAR%" --model "%MODEL%" --healthcheck
if errorlevel 1 exit /b 1

"%JLINK%" --add-modules java.base,java.desktop,java.logging,java.management,java.prefs,jdk.unsupported --strip-debug --no-header-files --no-man-pages --compress=zip-6 --output "%RUNTIME%"
if errorlevel 1 exit /b 1

"%JPACKAGE%" --type app-image --name VocaGate --app-version %VERSION% --vendor VocaGate --input "%STAGING%\app" --main-jar lib/desktop.jar --main-class com.vocagate.desktop.app.DesktopCompanionApp --runtime-image "%RUNTIME%" --dest "%APP_IMAGE_DESTINATION%"
if errorlevel 1 exit /b 1

set "APP_IMAGE=%APP_IMAGE_DESTINATION%\VocaGate"
if not exist "%APP_IMAGE%\app\sidecar\vocagate-mediapipe.exe" (
    echo jpackage did not create the expected portable app image. 1>&2
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Path '%APP_IMAGE%' -DestinationPath '%FINAL_ZIP%' -Force"
if errorlevel 1 exit /b 1

where candle.exe >nul 2>nul
if errorlevel 1 (
    echo Windows portable ZIP created: %FINAL_ZIP%
    echo EXE installer skipped because WiX Toolset candle.exe is not installed.
) else (
    "%JPACKAGE%" --type exe --name VocaGate --app-version %VERSION% --vendor VocaGate --app-image "%APP_IMAGE%" --dest "%EXE_DESTINATION%" --win-dir-chooser --win-menu --win-shortcut
    if errorlevel 1 exit /b 1
    for %%I in ("%EXE_DESTINATION%\*.exe") do copy /Y "%%~fI" "%FINAL_EXE%" >nul
    if not exist "%FINAL_EXE%" (
        echo jpackage did not create a Windows EXE installer. 1>&2
        exit /b 1
    )
    echo Windows EXE installer created: %FINAL_EXE%
)

call "%SCRIPT_DIR%prepare_release_notes.bat" %VERSION%
if errorlevel 1 exit /b 1
echo Windows release package created: %FINAL_ZIP%
exit /b 0
