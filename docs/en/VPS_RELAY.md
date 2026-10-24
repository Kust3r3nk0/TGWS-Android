# Origin VPS relay deployment and upgrades

Use your own Linux/systemd VPS, Node.js 22+, npm and a DNS name with a trusted TLS
certificate. Android connects using WSS/443 and verifies certificate/hostname.
There is no in-app SSH. Never publish tokens, private keys or credential-bearing URLs.

1. Check out the verified source commit/tag separately on the VPS. Run
   `npm --prefix relay ci --ignore-scripts` and `npm --prefix relay test`.
2. Ensure `/usr/bin/node` is Node 22+. As root run `bash relay/deploy/install.sh`.
   It installs into `/opt/tgws-relay` and creates a dedicated system user/unit;
   it never starts the service or replaces existing credentials.
3. Configure `/etc/tgws-relay/relay.env` using the provided example. Generate a token
   once on the VPS using `openssl rand -hex 32`; keep it in this external file.
4. Install trusted TLS chain/private key. Directory root:tgws-relay/0750, TLS files
   root:tgws-relay/0640, env file root:root/0600. Test localhost keys are not deployment keys.
5. Allow inbound TCP443 in host/provider firewall without disabling the firewall
   or losing SSH access. If outbound filtering is used, permit TCP443 to the exact
   DC allowlist in server.mjs; account for DNS/certificate renewal separately.
6. `systemctl enable --now tgws-relay`; inspect status/journal for generic messages.
7. Add `wss://YOUR_DOMAIN/YOUR_TOKEN/apiws` in Android Relay Manager, choose primary/
   backups, enable and save. Successful Upgrade alone does not prove Telegram.

The unit uses a non-root user, bounded capabilities, read-only system, protected
home/kernel, 256 MiB memory, TasksMax/NOFILE and a 15-second stop window. Default
origin limit is eight sessions (1–64 allowed), unrelated to Worker limits.
For ACME symlink trees use a renewal hook copying trusted files with the above
permissions and restarting; never make private-key directories world-readable.

For upgrades test the new source and rerun install.sh, then restart the service.
The env/token/TLS files remain intact. Restart closes sessions; Telegram opens new
ones. Keep Android config and MTProto secret. Roll back to verified previous code
while keeping the external configuration. Environment bounds: CONNECT_MS 1000–15000,
IDLE_MS 30000–3600000, WRITE_MS 1000–30000, MAX_SESSIONS 1–64, LISTEN_PORT 1–65535.
All names have TGWS_ prefix. LISTEN_HOST permits only 0.0.0.0/127.0.0.1/::/::1;
Android still requires public443, so a different bind needs safe TCP passthrough.
Do not inflate every timeout to mask failures. TLS, token path, exact DC/IP
allowlist, binary64-byte init, bounded messages and stream backpressure remain
mandatory. This is not an arbitrary TCP proxy. Device HD/media/background tests
remain separate owner acceptance.
