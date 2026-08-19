@echo off
setlocal
pushd "%~dp0"

echo ============================================
echo  File Dashboard - start/restart
echo ============================================
echo.
echo This runs build-jar.bat and install-autostart.bat together: it
echo (re)compiles the jar, installs the login autostart entry, and starts
echo File Dashboard. Safe to run again any time - if it's already running,
echo the old copy is stopped first and replaced with a freshly built one.
echo.

:: FD_SILENT tells build-jar.bat / install-autostart.bat / stop.bat to skip
:: their own "pause" prompts, since they're being driven from here rather
:: than run by hand. Only this outer script pauses, once, at the very end.
set FD_SILENT=1

echo [1/3] Stopping any copy that's already running...
call stop.bat
echo.

echo [2/3] Building FileDashboard.jar...
call build-jar.bat
if errorlevel 1 (
    echo.
    echo *** Build failed - not installing/starting autostart. See the errors above. ***
    echo.
    popd
    pause
    exit /b 1
)
echo.

echo [3/3] Installing autostart and starting File Dashboard...
call install-autostart.bat
if errorlevel 1 (
    echo.
    echo *** Autostart install failed. See the errors above. ***
    echo.
    popd
    pause
    exit /b 1
)
echo.

echo ============================================
echo  File Dashboard is (re)started.
echo ============================================
echo Open http://localhost:8080
echo.
popd
pause
