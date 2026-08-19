#!/usr/bin/env bash
# One-shot "just get it running" script for Linux: stops any copy that's
# already running, rebuilds the jar, and installs+starts the systemd --user
# autostart unit - so running this again after a code change rebuilds and
# restarts cleanly instead of leaving a stale jar running. Linux equivalent
# of start.bat.
set -u
cd "$(dirname "$0")"
export FD_SILENT=1

echo "============================================"
echo " File Dashboard - start/restart"
echo "============================================"
echo

echo "[1/3] Stopping any copy that's already running..."
./stop.sh
echo

echo "[2/3] Building FileDashboard.jar..."
./build-jar.sh
BUILD_RESULT=$?
echo
if [ $BUILD_RESULT -ne 0 ]; then
    echo "*** Build failed - not installing/starting autostart. See the errors above. ***"
    echo
    read -rp "Press Enter to exit..."
    exit 1
fi

echo "[3/3] Installing autostart and starting File Dashboard..."
./install-autostart.sh
INSTALL_RESULT=$?
echo

if [ $INSTALL_RESULT -ne 0 ]; then
    echo "*** Autostart install failed. See the errors above. ***"
    echo
    read -rp "Press Enter to exit..."
    exit 1
fi

echo "============================================"
echo " File Dashboard is (re)started."
echo "============================================"
echo "Open http://localhost:8080"
echo
read -rp "Press Enter to exit..."
exit 0
