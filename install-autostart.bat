@echo off
setlocal enabledelayedexpansion
pushd "%~dp0"

set TASKNAME=FileDashboard
set JARPATH=%~dp0FileDashboard.jar

echo ============================================
echo  Installing FileDashboard autostart
echo ============================================
echo.

if not exist "%JARPATH%" (
    echo Could not find FileDashboard.jar next to this script:
    echo   %JARPATH%
    echo.
    echo Run build-jar.bat first, then try this again.
    echo.
    popd
    pause
    exit /b 1
)

echo Looking for javaw.exe on your PATH...
set JAVAW=
for /f "delims=" %%J in ('where javaw 2^>nul') do (
    if not defined JAVAW set JAVAW=%%J
)

if not defined JAVAW (
    echo.
    echo Could not find javaw.exe on your PATH.
    echo Make sure Java is installed and its "bin" folder is on your PATH,
    echo then run this script again.
    echo.
    popd
    pause
    exit /b 1
)

echo Found: %JAVAW%
echo Jar:   %JARPATH%
echo.
echo Creating a scheduled task that starts File Dashboard when you log in
echo (this is the closest Windows equivalent to a "systemctl --user enable"
echo  style autostart - it runs in your own account, no admin rights needed)...
echo.

schtasks /create /tn "%TASKNAME%" /tr "\"%JAVAW%\" -jar \"%JARPATH%\"" /sc onlogon /rl limited /f

if errorlevel 1 (
    echo.
    echo *** Something went wrong creating the scheduled task. See the message above. ***
    echo.
    popd
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Done.
echo ============================================
echo File Dashboard will now start automatically every time you log in.
echo.
echo Starting it now in the background so you don't have to log out/in first...
start "" "%JAVAW%" -jar "%JARPATH%"
timeout /t 2 /nobreak >nul
echo.
echo Open http://localhost:8080 to check it's running.
echo.
echo To remove the autostart entry later, run uninstall-autostart.bat.
echo To stop a currently-running instance without removing autostart, run stop.bat.
echo.
popd
pause
