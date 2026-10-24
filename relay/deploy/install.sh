#!/usr/bin/env bash
# Run on the OWNER'S VPS, not in the Android app. No credentials are generated/replaced here.
set -euo pipefail
if (( EUID != 0 )); then echo 'Run as root on your VPS.' >&2; exit 1; fi
relay_source=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
node -e 'if(Number(process.versions.node.split(".")[0])<22)process.exit(1)'
command -v npm >/dev/null
if ! getent passwd tgws-relay >/dev/null; then useradd --system --home-dir /nonexistent --shell /usr/sbin/nologin tgws-relay; fi
install -d -m 0755 /opt/tgws-relay
install -d -m 0750 -g tgws-relay /etc/tgws-relay
for relay_file in main.mjs server.mjs config.mjs package.json package-lock.json; do
  install -m 0644 "$relay_source/$relay_file" "/opt/tgws-relay/$relay_file"
done
npm --prefix /opt/tgws-relay ci --omit=dev --ignore-scripts --no-audit --no-fund
install -m 0644 "$relay_source/deploy/tgws-relay.service" /etc/systemd/system/tgws-relay.service
systemctl daemon-reload
if [[ ! -f /etc/tgws-relay/relay.env ]]; then
  install -m 0600 "$relay_source/deploy/relay.env.example" /etc/tgws-relay/relay.env
  echo 'Configure relay.env and install trusted TLS files before enabling the service.'
else
  echo 'Files updated. Existing token and TLS files retained. Restart the service after checks.'
fi
