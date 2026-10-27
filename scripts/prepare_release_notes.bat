@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%~1"=="" (
    echo Usage: %~nx0 ^<version^> 1>&2
    exit /b 1
)

set "VERSION=%~1"
echo %VERSION%| findstr /r "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo Version must use x.y.z format. 1>&2
    exit /b 1
)

set "REPO_ROOT=%~dp0.."
for %%I in ("%REPO_ROOT%") do set "REPO_ROOT=%%~fI"
set "TEMPLATE=%REPO_ROOT%\docs\release-notes-template.md"
set "OUTPUT_DIR=%REPO_ROOT%\release\%VERSION%"
set "OUTPUT=%OUTPUT_DIR%\RELEASE_NOTES.md"

if not exist "%TEMPLATE%" (
    echo Missing release note template: %TEMPLATE% 1>&2
    exit /b 1
)

set "VOCAGATE_RELEASE_VERSION=%VERSION%"
set "VOCAGATE_RELEASE_TEMPLATE=%TEMPLATE%"
set "VOCAGATE_RELEASE_OUTPUT=%OUTPUT%"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$version = $env:VOCAGATE_RELEASE_VERSION; $template = $env:VOCAGATE_RELEASE_TEMPLATE; $output = $env:VOCAGATE_RELEASE_OUTPUT; " ^
  "$content = [System.IO.File]::ReadAllText($template); $materialized = $content.Replace('{{version}}', $version); " ^
  "if ($materialized -match '\{\{[^}]+\}\}') { Write-Error 'Release note template has unresolved placeholders.'; exit 1 }; " ^
  "$outputDirectory = [System.IO.Path]::GetDirectoryName($output); [System.IO.Directory]::CreateDirectory($outputDirectory) | Out-Null; " ^
  "$temp = [System.IO.Path]::Combine($outputDirectory, [System.IO.Path]::GetRandomFileName()); " ^
  "try { [System.IO.File]::WriteAllText($temp, $materialized, [System.Text.UTF8Encoding]::new($false)); Move-Item -LiteralPath $temp -Destination $output -Force } " ^
  "finally { if (Test-Path -LiteralPath $temp) { Remove-Item -LiteralPath $temp -Force } }"
if errorlevel 1 exit /b 1

echo Release notes created: %OUTPUT%
