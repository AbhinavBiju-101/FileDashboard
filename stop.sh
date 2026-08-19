#!/usr/bin/env bash
# Stops whatever's listening on File Dashboard's port (8080 by default).
# Linux equivalent of stop.bat. If it's running as the systemd --user
# service, prefer that path (keeps systemd's own state consistent);
# otherwise falls back to finding and killing the process directly.
set -u
PORT=8080
UNIT_NAME="filedashboard.service"

echo "Looking for a process listening on port $PORT..."

if command -v systemctl >/dev/null 2>&1 && systemctl --user is-active --quiet "$UNIT_NAME" 2>/dev/null; then
    echo "File Dashboard is running as the systemd --user service - stopping it via systemctl..."
    systemctl --user stop "$UNIT_NAME"
    if [ $? -eq 0 ]; then
        echo
        echo "Stopped. Note: if autostart is still enabled, it will start again next"
        echo "time you log in. Run uninstall-autostart.sh to also remove autostart."
    else
        echo
        echo "*** Could not stop it via systemctl. See the error above. ***"
    fi
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 0
fi

PID=""
if command -v fuser >/dev/null 2>&1; then
    PID="$(fuser -n tcp "$PORT" 2>/dev/null | awk '{print $1}')"
elif command -v lsof >/dev/null 2>&1; then
    PID="$(lsof -t -i tcp:"$PORT" -sTCP:LISTEN 2>/dev/null | head -n1)"
elif command -v ss >/dev/null 2>&1; then
    PID="$(ss -ltnp "sport = :$PORT" 2>/dev/null | grep -oP 'pid=\K[0-9]+' | head -n1)"
fi

if [ -z "$PID" ]; then
    echo "Nothing appears to be listening on port $PORT - File Dashboard isn't running."
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 0
fi

echo "Found process ID $PID - stopping it..."
kill "$PID"
sleep 1
if kill -0 "$PID" 2>/dev/null; then
    echo "Still running - forcing it..."
    kill -9 "$PID"
fi

if kill -0 "$PID" 2>/dev/null; then
    echo
    echo "*** Could not stop it. You may need to kill PID $PID manually. ***"
else
    echo
    echo "Stopped. Note: this only stops the currently running copy - if autostart"
    echo "is installed, it will start again next time you log in. Run"
    echo "uninstall-autostart.sh to also remove the autostart entry."
fi
echo
[ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
exit 0
