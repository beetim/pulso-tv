#!/usr/bin/env bash
# Installs pulso.tv on the TV via adb, downloading the APK from the latest GitHub Release.
# Usage:  curl -fsSL https://raw.githubusercontent.com/beetim/pulso-tv/main/install.sh | bash -s -- 192.168.0.11
#   or: ./install.sh 192.168.0.11
set -euo pipefail

REPO="beetim/pulso-tv"
IP="${1:-}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb not found. Install platform-tools and run again:"
  echo "https://developer.android.com/tools/releases/platform-tools"
  exit 1
fi

if [ -z "$IP" ]; then
  read -rp "TV IP address (e.g. 192.168.0.11): " IP </dev/tty
fi
case "$IP" in *:*) TARGET="$IP" ;; *) TARGET="$IP:5555" ;; esac

echo "Looking for the latest version..."
URL=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
  | grep -o '"browser_download_url": *"[^"]*\.apk"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')
if [ -z "$URL" ]; then
  echo "No APK found in the latest release."
  exit 1
fi

FILE="$(mktemp -d)/Pulso.apk"
echo "Downloading $URL"
curl -fsSL "$URL" -o "$FILE"

echo "Connecting to $TARGET (accept the authorization on the TV if asked)..."
adb connect "$TARGET"
echo "Installing..."
adb -s "$TARGET" install -r "$FILE"
adb -s "$TARGET" shell am start -n com.pulso.tv/.ui.pulso.PulsoHomeActivity >/dev/null
echo "Done! pulso.tv was installed and opened on the TV."
