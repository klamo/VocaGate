@echo off
setlocal EnableExtensions DisableDelayedExpansion

if "%~1"=="" goto :usage
if /I "%~1"=="windows-x64" goto :target_ok
if /I "%~1"=="macos-arm64" (
  echo macos-arm64 staging must run from the macOS .sh helper on a macOS ARM64 host; cross-compilation is not supported. >&2
  exit /b 2
)
goto :usage

:target_ok
set "TARGET=windows-x64"
set "REPO_ROOT=%~dp0.."
for %%I in ("%REPO_ROOT%") do set "REPO_ROOT=%%~fI"
if "%VOCAGATE_PYTHON%"=="" (set "PYTHON_BIN=python") else (set "PYTHON_BIN=%VOCAGATE_PYTHON%")
set "PYTHON_DIR=%REPO_ROOT%\desktop\src\main\python"
set "LOCK_PATH=%PYTHON_DIR%\models\face_landmarker.lock.json"
set "REQUIREMENTS=%PYTHON_DIR%\requirements-release.txt"
set "LOCAL_MODEL=%PYTHON_DIR%\models\face_landmarker.task"
set "BUILD_DIR=%REPO_ROOT%\desktop\build\sidecar\%TARGET%"
set "VENV_DIR=%BUILD_DIR%\venv"
set "MODEL_DIR=%BUILD_DIR%\models"
set "MODEL_PATH=%MODEL_DIR%\face_landmarker.task"

for /f "tokens=1,* delims==" %%A in ('powershell -NoProfile -Command "$d = ConvertFrom-Json -InputObject ([System.IO.File]::ReadAllText('%LOCK_PATH%')); [Console]::WriteLine('URL=' + $d.url); [Console]::WriteLine('SHA256=' + $d.sha256)"') do (
  if /I "%%A"=="URL" set "MODEL_URL=%%B"
  if /I "%%A"=="SHA256" set "MODEL_SHA256=%%B"
)
if "%MODEL_URL%"=="" (
  echo Unable to read the model lock. >&2
  exit /b 1
)

if not exist "%MODEL_DIR%" mkdir "%MODEL_DIR%"
call :verify_model "%MODEL_PATH%"
if not errorlevel 1 goto :model_ready
if exist "%LOCAL_MODEL%" (
  call :verify_model "%LOCAL_MODEL%"
  if not errorlevel 1 copy /Y "%LOCAL_MODEL%" "%MODEL_PATH%" >nul
)
call :verify_model "%MODEL_PATH%"
if not errorlevel 1 goto :model_ready
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%MODEL_URL%' -OutFile '%MODEL_PATH%.download'"
if errorlevel 1 exit /b 1
move /Y "%MODEL_PATH%.download" "%MODEL_PATH%" >nul
call :verify_model "%MODEL_PATH%"
if errorlevel 1 (
  echo MediaPipe model SHA-256 mismatch: %MODEL_PATH% >&2
  exit /b 1
)

:model_ready
"%PYTHON_BIN%" -m venv "%VENV_DIR%"
if errorlevel 1 exit /b 1
set "VENV_PYTHON=%VENV_DIR%\Scripts\python.exe"
"%VENV_PYTHON%" -m pip install --requirement "%REQUIREMENTS%"
if errorlevel 1 exit /b 1
"%VENV_PYTHON%" -m PyInstaller --noconfirm --clean --onedir --name vocagate-mediapipe --hidden-import mediapipe.tasks.c --collect-binaries mediapipe.tasks.c --distpath "%BUILD_DIR%" --workpath "%BUILD_DIR%\pyinstaller-work" --specpath "%BUILD_DIR%\pyinstaller-spec" "%PYTHON_DIR%\vocagate_mediapipe_sidecar.py"
if errorlevel 1 exit /b 1
"%VENV_PYTHON%" -m pip freeze --all > "%BUILD_DIR%\requirements-installed.txt"
copy /Y "%LOCK_PATH%" "%BUILD_DIR%\face_landmarker.lock.json" >nul
> "%BUILD_DIR%\THIRD_PARTY_NOTICES.txt" echo VocaGate MediaPipe sidecar notices: MediaPipe Apache-2.0; NumPy BSD-3-Clause; PyInstaller GPL-2.0-or-later with bootloader exception.
"%VENV_PYTHON%" -c "import json,sys; from pathlib import Path; lock=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8')); Path(sys.argv[3]).write_text(json.dumps({'target':sys.argv[2],'model':lock},indent=2,sort_keys=True)+'\n',encoding='utf-8')" "%LOCK_PATH%" "%TARGET%" "%BUILD_DIR%\build-metadata.json"
if errorlevel 1 exit /b 1
echo Prepared MediaPipe sidecar: %BUILD_DIR%\vocagate-mediapipe\vocagate-mediapipe.exe
exit /b 0

:verify_model
if not exist "%~1" exit /b 1
set "ACTUAL_SHA256="
for /f "delims=" %%H in ('powershell -NoProfile -Command "(Get-FileHash -LiteralPath '%~1' -Algorithm SHA256).Hash.ToLowerInvariant()"') do set "ACTUAL_SHA256=%%H"
if /I "%ACTUAL_SHA256%"=="%MODEL_SHA256%" exit /b 0
echo Expected model SHA-256: %MODEL_SHA256% 1>&2
echo Actual model SHA-256: %ACTUAL_SHA256% 1>&2
exit /b 1

:usage
echo Usage: %~nx0 windows-x64 >&2
exit /b 2
