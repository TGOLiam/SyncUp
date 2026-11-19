# EC2 backend deployment

This deployment runs the supervised LiveKit server, speech controller, watchdog,
and signaling gate together. Caddy provides public HTTPS/WSS and forwards only
the token/speech and protected signaling routes to loopback services. Supabase
is hosted separately; its schema migrations are applied separately as well.

## CI and deployment

GitHub Actions runs backend unit tests, builds the LiveKit image, and runs the
SQL tests against a disposable Supabase stack. The EC2 systemd timer checks
the public `main` branch every five minutes. It deploys only the current main
commit after the latest `push` run of `backend-ci.yml` has succeeded. No GitHub
token or SSH key is stored in GitHub or on the server for this process.

The deployment checkout and timer run as root because Docker requires
privileged access. Only trusted collaborators should be able to push to main
or change the workflow. Pull requests never deploy directly. Updates to the
deployer script or systemd units require an explicit administrator install;
the timer deliberately does not replace its own root-owned program.

Before configuration, the timer does nothing. When configured, it builds the
image before replacing services, checks that both containers are running and
the local LiveKit, token, and signaling endpoints accept connections, and records
the deployed commit. If startup fails, it attempts to rebuild the last recorded
commit. This local check does not prove DNS, TLS issuance, WebRTC connectivity,
or hosted Supabase authorization; verify those separately.

## Configuration prerequisites

1. Create a hosted Supabase project and apply the migrations. Configure Auth
   and test account creation and the event policies.
2. Point a domain at the EC2 public IP. Give Caddy inbound TCP ports 80 and 443
   for certificate issuance and HTTPS. Allow LiveKit UDP 7882 and TCP 7881
   in both the EC2 security group and the instance firewall. Limit SSH to the
   administrator's current IP. Network rules may need adjustment for TURN.
3. Create `/etc/syncup/backend.env` from `backend.env.example`, owned by root
   and readable only by root (`chmod 600`). Fill in the hosted Supabase URL and
   publishable key, domain, EC2 public IPv4 address, and a private LiveKit key
   and secret. Generate unique values; never commit or share the API secret.
4. Trigger the timer or run `sudo systemctl start syncup-deploy.service` once
   the CI workflow has succeeded on the current main commit. Inspect status
   with `sudo journalctl -u syncup-deploy.service -n 100 --no-pager` and
   `sudo docker compose --project-name syncup-backend --env-file /etc/syncup/backend.env -f /opt/syncup/repository/backend/deploy/compose.yaml ps`.
5. Build the Android app with the hosted Supabase URL and publishable key, and
   `syncup.livekit.tokenUrl=https://<domain>`. Test signup, event access,
   messages, phase/location polling, and live voice on two networks.

An automatically assigned EC2 public IPv4 address can change after the instance
is stopped and started. Update DNS and `LIVEKIT_PUBLIC_IP` if it changes; the
deployer detects configuration changes and applies them after CI has passed.

The current EC2 instance has approximately 1 GB RAM. A Docker build and TLS
proxy may compete for memory; test on the actual instance and resize if needed.
The hosted configuration has not been exercised end to end until a real domain
and Supabase project are available.
