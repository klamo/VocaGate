@echo off
setlocal EnableExtensions DisableDelayedExpansion

if not "%~1"=="--platform" goto :usage
if /I not "%~2"=="windows-x64" goto :usage
if "%~3"=="" goto :usage

set "RELEASE_DIR=%~f3"
set "REPO_ROOT=%~dp0.."
for %%I in ("%REPO_ROOT%") do set "REPO_ROOT=%%~fI"
for /f "tokens=1,* delims==" %%A in ('findstr /B "vocagate.version=" "%REPO_ROOT%\gradle.properties"') do set "VERSION=%%B"
set "ZIP=%RELEASE_DIR%\VocaGate-%VERSION%-windows-x64.zip"
set "NOTES=%RELEASE_DIR%\RELEASE_NOTES.md"

if not exist "%ZIP%" (
    echo Missing Windows portable release artifact: %ZIP% 1>&2
    exit /b 1
)
if not exist "%NOTES%" (
    echo Missing release notes: %NOTES% 1>&2
    exit /b 1
)

set "EXPAND_DIR=%TEMP%\vocagate-release-verify-%RANDOM%-%RANDOM%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%ZIP%' -DestinationPath '%EXPAND_DIR%' -Force"
if errorlevel 1 exit /b 1

set "SIDECAR=%EXPAND_DIR%\VocaGate\app\sidecar\vocagate-mediapipe.exe"
set "MODEL=%EXPAND_DIR%\VocaGate\app\sidecar\models\face_landmarker.task"
if not exist "%SIDECAR%" (
    echo Portable ZIP is missing the MediaPipe sidecar. 1>&2
    rmdir /S /Q "%EXPAND_DIR%"
    exit /b 1
)
"%SIDECAR%" --model "%MODEL%" --healthcheck
if errorlevel 1 (
    rmdir /S /Q "%EXPAND_DIR%"
    exit /b 1
)

certutil -hashfile "%ZIP%" SHA256 | findstr /R /I "^[0-9A-F][0-9A-F]" > "%RELEASE_DIR%\VocaGate-%VERSION%-windows-x64.zip.sha256"
rmdir /S /Q "%EXPAND_DIR%"
echo Windows release artifact verified: %ZIP%
exit /b 0

:usage
echo Usage: %~nx0 --platform windows-x64 ^<release-directory^> 1>&2
exit /b 2
