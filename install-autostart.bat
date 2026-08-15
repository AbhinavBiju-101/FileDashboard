@echo off
setlocal enabledelayedexpansion
pushd "%~dp0"

set REGKEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
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
echo Adding autostart entry directly to Windows Registry...
echo (This avoids Task Scheduler character limits and McAfee false positives)
echo.

:: Add the run command directly to the current user's registry startup keys.
:: No admin rights required, perfectly handles the nested paths, completely invisible.
reg add "%REGKEY%" /v "%TASKNAME%" /t REG_SZ /d "\"%JAVAW%\" -jar \"%JARPATH%\"" /f

if errorlevel 1 (
    echo.
    echo *** Something went wrong writing to the registry. See the error above. ***
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
