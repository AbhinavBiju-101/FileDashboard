#!/usr/bin/env bash
# Removes the systemd --user unit installed by install-autostart.sh, and
# stops it if it's currently running. Linux equivalent of
# uninstall-autostart.bat.
set -u
cd "$(dirname "$0")"

UNIT_NAME="filedashboard.service"
UNIT_FILE="$HOME/.config/systemd/user/$UNIT_NAME"

echo "============================================"
echo " Removing FileDashboard autostart"
echo "============================================"
echo

if ! command -v systemctl >/dev/null 2>&1; then
    echo "systemctl was not found - nothing to remove."
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 0
fi

if ! systemctl --user list-unit-files "$UNIT_NAME" 2>/dev/null | grep -q "$UNIT_NAME"; then
    echo "No autostart unit named \"$UNIT_NAME\" was found - nothing to remove."
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 0
fi

echo "Stopping and disabling the service..."
systemctl --user disable --now "$UNIT_NAME"
if [ $? -ne 0 ]; then
    echo
    echo "*** Failed to disable the autostart unit. See the error above. ***"
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

if [ -f "$UNIT_FILE" ]; then
    echo "Removing unit file $UNIT_FILE ..."
    rm -f "$UNIT_FILE"
    systemctl --user daemon-reload
fi

echo
echo "Done. File Dashboard will no longer start automatically at login."
echo "(This also stops it if it was running via the service. Run stop.sh"
echo "instead if you only want to stop a copy started some other way.)"
echo
[ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
exit 0
