#!/usr/bin/env bash
# Instala o pulso.tv na TV via adb, baixando o APK do ultimo Release do GitHub.
# Uso:  curl -fsSL https://raw.githubusercontent.com/beetim/pulso-tv/main/install.sh | bash -s -- 192.168.0.11
#   ou: ./install.sh 192.168.0.11
set -euo pipefail

REPO="beetim/pulso-tv"
IP="${1:-}"

if ! command -v adb >/dev/null 2>&1; then
  echo "adb nao encontrado. Instale o platform-tools e rode de novo:"
  echo "https://developer.android.com/tools/releases/platform-tools"
  exit 1
fi

if [ -z "$IP" ]; then
  read -rp "IP da TV (ex.: 192.168.0.11): " IP </dev/tty
fi
case "$IP" in *:*) TARGET="$IP" ;; *) TARGET="$IP:5555" ;; esac

echo "Buscando a ultima versao..."
URL=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" \
  | grep -o '"browser_download_url": *"[^"]*\.apk"' | head -1 | sed 's/.*"\(https[^"]*\)"/\1/')
if [ -z "$URL" ]; then
  echo "Nenhum APK encontrado no ultimo Release."
  exit 1
fi

FILE="$(mktemp -d)/Pulso.apk"
echo "Baixando $URL"
curl -fsSL "$URL" -o "$FILE"

echo "Conectando em $TARGET (aceite a autorizacao na TV, se aparecer)..."
adb connect "$TARGET"
echo "Instalando..."
adb -s "$TARGET" install -r "$FILE"
adb -s "$TARGET" shell am start -n com.pulso.tv/.ui.pulso.PulsoHomeActivity >/dev/null
echo "Pronto! O pulso.tv foi instalado e aberto na TV."
