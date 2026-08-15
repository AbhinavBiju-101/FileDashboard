@echo off
pushd "%~dp0"

set REGKEY=HKCU\Software\Microsoft\Windows\CurrentVersion\Run
set TASKNAME=FileDashboard

echo ============================================
echo  Removing FileDashboard autostart
echo ============================================
echo.

:: Checks the registry to see if the entry exists before attempting deletion
reg query "%REGKEY%" /v "%TASKNAME%" >nul 2>&1
if errorlevel 1 (
    echo No autostart entry named "%TASKNAME%" was found in the registry - nothing to remove.
    echo.
    popd
    pause
    exit /b 0
)

:: Deletes the specific value from the current user's Run path
reg delete "%REGKEY%" /v "%TASKNAME%" /f

if errorlevel 1 (
    echo.
    echo *** Failed to remove the autostart registry entry. ***
    echo.
    popd
    pause
    exit /b 1
)

echo.
echo Done. File Dashboard will no longer start automatically at logon.
echo (If it's currently running, this doesn't stop it - run stop.bat for that.)
echo.
popd
pause
