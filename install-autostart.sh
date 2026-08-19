#!/usr/bin/env bash
# Installs a systemd --user unit that starts FileDashboard.jar when you log
# in, and starts it right now too. Linux equivalent of install-autostart.bat
# (which uses a Windows Registry Run key instead - systemd --user units are
# the closest per-user, no-admin-needed equivalent on Linux).
set -u
cd "$(dirname "$0")"

UNIT_NAME="filedashboard.service"
UNIT_DIR="$HOME/.config/systemd/user"
UNIT_FILE="$UNIT_DIR/$UNIT_NAME"
JAR_PATH="$(pwd)/FileDashboard.jar"

echo "============================================"
echo " Installing FileDashboard autostart"
echo "============================================"
echo

if [ ! -f "$JAR_PATH" ]; then
    echo "Could not find FileDashboard.jar next to this script:"
    echo "  $JAR_PATH"
    echo
    echo "Run build-jar.sh first, then try this again."
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

if ! command -v systemctl >/dev/null 2>&1; then
    echo "systemctl was not found - this script needs systemd (present on most"
    echo "modern Linux distributions: Ubuntu, Fedora, Debian, Arch, etc.)."
    echo "If your distro doesn't use systemd, add"
    echo "  java -jar \"$JAR_PATH\""
    echo "to whatever autostart mechanism it uses instead."
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo "Looking for java on your PATH..."
JAVA_BIN="$(command -v java || true)"
if [ -z "$JAVA_BIN" ]; then
    echo
    echo "Could not find java on your PATH."
    echo "Make sure a JDK/JRE is installed (e.g. 'sudo apt install default-jre')"
    echo "and try again."
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo "Found: $JAVA_BIN"
echo "Jar:   $JAR_PATH"
echo
echo "Writing systemd --user unit to $UNIT_FILE ..."
mkdir -p "$UNIT_DIR"

cat > "$UNIT_FILE" <<EOF
[Unit]
Description=File Dashboard
After=network.target

[Service]
Type=simple
ExecStart="$JAVA_BIN" -jar "$JAR_PATH"
WorkingDirectory=$(pwd)
Restart=on-failure
RestartSec=3

[Install]
WantedBy=default.target
EOF

echo "Reloading systemd user units..."
systemctl --user daemon-reload
if [ $? -ne 0 ]; then
    echo
    echo "*** 'systemctl --user daemon-reload' failed. See the error above. ***"
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo "Enabling and starting the service now..."
systemctl --user enable --now "$UNIT_NAME"
if [ $? -ne 0 ]; then
    echo
    echo "*** Something went wrong enabling the service. See the error above. ***"
    echo
    [ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
    exit 1
fi

echo
echo "============================================"
echo " Done."
echo "============================================"
echo "File Dashboard will now start automatically every time you log in,"
echo "and is running right now."
echo
echo "Open http://localhost:8080 to check it's running."
echo
echo "This starts File Dashboard when your desktop/login session starts -"
echo "the direct equivalent of the Windows Run-key entry. If you also want"
echo "it running before anyone logs in (e.g. on a headless box), additionally"
echo "run once:"
echo "  loginctl enable-linger $USER"
echo
echo "To remove the autostart entry later, run uninstall-autostart.sh."
echo "To stop a currently-running instance without removing autostart, run stop.sh"
echo "(or 'systemctl --user stop $UNIT_NAME')."
echo
[ -z "${FD_SILENT:-}" ] && read -rp "Press Enter to exit..."
exit 0
