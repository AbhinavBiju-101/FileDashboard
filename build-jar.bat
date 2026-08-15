@echo off
setlocal
pushd "%~dp0"

echo ============================================
echo  Building FileDashboard.jar
echo ============================================
echo.

if exist build (
    echo Cleaning previous build...
    rmdir /s /q build
)
mkdir build

echo Compiling Java sources...
dir /b *.java > sources.txt
javac -d build @sources.txt
set COMPILE_RESULT=%errorlevel%
del sources.txt
if %COMPILE_RESULT% neq 0 (
    echo.
    echo *** Compilation failed. Fix the errors above and run this again. ***
    echo.
    popd
    pause
    exit /b 1
)

echo Main-Class: FileServer> build\MANIFEST.MF

echo Packaging FileDashboard.jar...
pushd build
jar cfm ..\FileDashboard.jar MANIFEST.MF -C . .
popd

if not exist FileDashboard.jar (
    echo.
    echo *** Something went wrong - FileDashboard.jar was not created. ***
    echo.
    popd
    pause
    exit /b 1
)

echo.
echo ============================================
echo  Done: %~dp0FileDashboard.jar
echo ============================================
echo.
echo Run it with:
echo   java -jar FileDashboard.jar        (shows a console)
echo   javaw -jar FileDashboard.jar       (no console window)
echo.
echo To have it start automatically when you log in, run install-autostart.bat
echo (it will find and use this jar automatically).
echo.
popd
pause
