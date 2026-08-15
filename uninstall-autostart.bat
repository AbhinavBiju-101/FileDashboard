@echo off
set TASKNAME=FileDashboard

echo ============================================
echo  Removing FileDashboard autostart
echo ============================================
echo.

schtasks /query /tn "%TASKNAME%" >nul 2>&1
if errorlevel 1 (
    echo No autostart task named "%TASKNAME%" was found - nothing to remove.
    echo.
    pause
    exit /b 0
)

schtasks /delete /tn "%TASKNAME%" /f

if errorlevel 1 (
    echo.
    echo *** Could not remove the task automatically. ***
    echo You can remove it manually: open Task Scheduler, find "%TASKNAME%", and delete it.
    echo.
    pause
    exit /b 1
)

echo.
echo Done. File Dashboard will no longer start automatically at logon.
echo (If it's currently running, this doesn't stop it - run stop.bat for that.)
echo.
pause
