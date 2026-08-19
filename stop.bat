@echo off
set PORT=8080

echo Looking for a process listening on port %PORT%...

set FOUND=
for /f "tokens=5" %%P in ('netstat -aon ^| findstr ":%PORT% " ^| findstr "LISTENING"') do (
    set FOUND=%%P
)

if not defined FOUND (
    echo Nothing appears to be listening on port %PORT% - File Dashboard isn't running.
    echo.
    if not defined FD_SILENT pause
    exit /b 0
)

echo Found process ID %FOUND% - stopping it...
taskkill /PID %FOUND% /F

if errorlevel 1 (
    echo.
    echo *** Could not stop it. It may need to be closed manually via Task Manager. ***
) else (
    echo.
    echo Stopped. Note: this only stops the currently running copy - if autostart
    echo is installed, it will start again next time you log in. Run
    echo uninstall-autostart.bat to also remove the autostart entry.
)
echo.
if not defined FD_SILENT pause
