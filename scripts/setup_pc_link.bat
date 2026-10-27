@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "PORT=27183"
set "DEVICE_SERIAL="
set "REMOVE=0"

:parse_args
if "%~1"=="" goto after_args
if /I "%~1"=="--device" (
  if "%~2"=="" (
    echo Error: --device requires a serial 1>&2
    exit /b 1
  )
  set "DEVICE_SERIAL=%~2"
  shift
  shift
  goto parse_args
)
if /I "%~1"=="--port" (
  if "%~2"=="" (
    echo Error: --port requires a value 1>&2
    exit /b 1
  )
  set "PORT=%~2"
  shift
  shift
  goto parse_args
)
if /I "%~1"=="--remove" (
  set "REMOVE=1"
  shift
  goto parse_args
)
if /I "%~1"=="--help" goto usage
if /I "%~1"=="-h" goto usage
if "%~1"=="/?" goto usage

echo Error: unknown option: %~1 1>&2
exit /b 1

:usage
echo Usage: scripts\setup_pc_link.bat [options]
echo.
echo Create the adb reverse tunnel used by the Android app to reach the desktop companion.
echo.
echo Options:
echo   --device SERIAL   Use a specific adb device serial.
echo   --port PORT       Use a custom port. Default: 27183.
echo   --remove          Remove the reverse tunnel instead of creating it.
echo   --help            Show this help.
exit /b 0

:after_args
call :find_adb
if not defined ADB (
  echo Error: adb not found. Install Android SDK platform-tools, then add platform-tools to PATH or set ANDROID_HOME. 1>&2
  exit /b 1
)

if defined DEVICE_SERIAL (
  set "ADB_DEVICE_ARGS=-s %DEVICE_SERIAL%"
) else (
  set "ADB_DEVICE_ARGS="
)

"%ADB%" start-server >nul
if errorlevel 1 (
  echo Error: failed to start adb server. 1>&2
  exit /b 1
)

set "DEVICE_COUNT=0"
set "CONNECTED_DEVICES="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if "%%B"=="device" (
    set /a DEVICE_COUNT+=1
    if defined CONNECTED_DEVICES (
      set "CONNECTED_DEVICES=!CONNECTED_DEVICES! %%A"
    ) else (
      set "CONNECTED_DEVICES=%%A"
    )
  )
)

if "%DEVICE_COUNT%"=="0" (
  echo Error: no authorized adb device found. Connect the phone by USB, enable USB debugging, unlock the phone, and tap Allow on the authorization prompt. 1>&2
  exit /b 1
)

if not defined DEVICE_SERIAL (
  if not "%DEVICE_COUNT%"=="1" (
    echo Connected devices: 1>&2
    echo %CONNECTED_DEVICES% 1>&2
    echo Error: multiple devices found. Re-run with --device SERIAL. 1>&2
    exit /b 1
  )
)

if "%REMOVE%"=="1" (
  "%ADB%" %ADB_DEVICE_ARGS% reverse --remove "tcp:%PORT%" >nul 2>nul
  echo Removed adb reverse tcp:%PORT%.
  exit /b 0
)

"%ADB%" %ADB_DEVICE_ARGS% reverse --remove "tcp:%PORT%" >nul 2>nul

set "REVERSE_OUTPUT=%TEMP%\vocagate-adb-reverse-%RANDOM%-%RANDOM%.txt"
"%ADB%" %ADB_DEVICE_ARGS% reverse "tcp:%PORT%" "tcp:%PORT%" >"%REVERSE_OUTPUT%" 2>&1
if errorlevel 1 (
  type "%REVERSE_OUTPUT%" 1>&2
  findstr /C:"Address already in use" "%REVERSE_OUTPUT%" >nul 2>nul
  if not errorlevel 1 (
    echo Error: tcp:%PORT% is already occupied on the Android device. 1>&2
    echo Try: 1>&2
    echo   adb reverse --remove-all 1>&2
    echo   adb shell am force-stop com.vocagate.demo 1>&2
    echo   adb shell "toybox netstat -an" 1>&2
    echo Then run scripts\setup_pc_link.bat again. 1>&2
  )
  del "%REVERSE_OUTPUT%" >nul 2>nul
  exit /b 1
)

del "%REVERSE_OUTPUT%" >nul 2>nul
echo Created adb reverse tcp:%PORT% -^> tcp:%PORT%.
exit /b 0

:find_adb
set "ADB="
for /f "delims=" %%P in ('where adb.exe 2^>nul') do (
  if not defined ADB set "ADB=%%P"
)
if defined ADB exit /b 0

if defined ANDROID_HOME (
  if exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
    exit /b 0
  )
)

if defined ANDROID_SDK_ROOT (
  if exist "%ANDROID_SDK_ROOT%\platform-tools\adb.exe" (
    set "ADB=%ANDROID_SDK_ROOT%\platform-tools\adb.exe"
    exit /b 0
  )
)
exit /b 0
