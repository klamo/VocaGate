@echo off
setlocal

set "ROOT_DIR=%~dp0.."
set "VENV_DIR=%ROOT_DIR%\.venv-mediapipe"
if exist "%ROOT_DIR%\sidecar" (
  set "MODEL_DIR=%ROOT_DIR%\sidecar\models"
) else (
  set "MODEL_DIR=%ROOT_DIR%\desktop\src\main\python\models"
)
set "MODEL_PATH=%MODEL_DIR%\face_landmarker.task"
set "MODEL_URL=https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/latest/face_landmarker.task"

if "%VOCAGATE_PYTHON%"=="" (
  set "PYTHON_BIN=python"
) else (
  set "PYTHON_BIN=%VOCAGATE_PYTHON%"
)

"%PYTHON_BIN%" -m venv "%VENV_DIR%"
if errorlevel 1 exit /b 1

"%VENV_DIR%\Scripts\python.exe" -m pip install --upgrade pip
if errorlevel 1 exit /b 1

"%VENV_DIR%\Scripts\python.exe" -m pip install "mediapipe>=0.10.35,<0.11" "numpy>=1.26"
if errorlevel 1 exit /b 1

if not exist "%MODEL_DIR%" mkdir "%MODEL_DIR%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%MODEL_URL%' -OutFile '%MODEL_PATH%'"
if errorlevel 1 exit /b 1

echo MediaPipe sidecar is ready.
echo Python: %VENV_DIR%\Scripts\python.exe
