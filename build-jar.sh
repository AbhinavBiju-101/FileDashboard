#!/usr/bin/env bash
# Builds FileDashboard.jar from the .java sources in this folder.
# Linux/macOS equivalent of build-jar.bat.
set -u
cd "$(dirname "$0")"

echo "============================================"
echo " Building FileDashboard.jar"
echo "============================================"
echo

if [ -d build ]; then
    echo "Cleaning previous build..."
    rm -rf build
fi
mkdir -p build

if ! command -v javac >/dev/null 2>&1; then
    echo
    echo "*** javac was not found on your PATH. Install a JDK (e.g. 'sudo apt install default-jdk') and try again. ***"
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo "Compiling Java sources..."
javac -d build ./*.java
COMPILE_RESULT=$?
if [ $COMPILE_RESULT -ne 0 ]; then
    echo
    echo "*** Compilation failed. Fix the errors above and run this again. ***"
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo "Main-Class: FileServer" > build/MANIFEST.MF

echo "Packaging FileDashboard.jar..."
( cd build && jar cfm ../FileDashboard.jar MANIFEST.MF . )

if [ ! -f FileDashboard.jar ]; then
    echo
    echo "*** Something went wrong - FileDashboard.jar was not created. ***"
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo
echo "============================================"
echo " Done: $(pwd)/FileDashboard.jar"
echo "============================================"
echo
echo "Run it with:"
echo "  java -jar FileDashboard.jar"
echo
echo "To have it start automatically when you log in, run ./install-autostart.sh"
echo "(it will find and use this jar automatically)."
echo
[ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
exit 0
