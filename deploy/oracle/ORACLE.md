# Both services on Oracle Cloud Always Free (€0)

WordPress stays on Hostinger (shared hosting cannot run Java). The two Java services — style match
(`wizard`) and the receipt gate (this project) — run in Docker on one free Oracle ARM machine, behind one Caddy
that gets HTTPS certificates automatically:

```
https://match.panteadesignstudio.com/api/...     -> style match   (port 8085 inside)
https://receipts.panteadesignstudio.com/api/...  -> receipt gate  (port 8086 inside)
```

## 1. Oracle account (once)
signup.cloud.oracle.com · Customer type Individual · Home region **Italy North (Milan)** or Germany Central
(Frankfurt) — permanent · card verification only (small temporary hold) · stay on **Free Tier**, never
"Upgrade to Pay As You Go" if it must cost nothing.

SSH key on Windows (PowerShell):
```powershell
ssh-keygen -t ed25519 -f $HOME\.ssh\oracle_pantea -C "pantea-oracle"
type $HOME\.ssh\oracle_pantea.pub      # the PUBLIC key, pasted into the machine setup
```

## 2. The machine
Console → Compute → Instances → **Create instance**:

| Setting | Value |
|---|---|
| Name | `pantea` |
| Image | **Canonical Ubuntu 24.04** (the aarch64 build is picked automatically for Ampere) |
| Shape | Ampere → **VM.Standard.A1.Flex**, **2 OCPU, 8 GB** |
| Networking | create a new VCN + public subnet, **assign a public IPv4 address** |
| SSH keys | paste the contents of `oracle_pantea.pub` |
| Boot volume | default (~50 GB) |

8 GB rather than the free maximum of 12: Oracle reclaims an Always Free A1 machine that keeps CPU, network **and
memory** under 20% for a week. Two JVMs started with `-Xms512m` plus the ONNX models use ~2.5 GB, about 30% of
8 GB, so the machine never counts as idle without any busy-work script.

*"Out of capacity for shape VM.Standard.A1.Flex"* is common: try another availability domain in the same form,
or retry later (often early morning).

**Open ports 80 and 443:** Networking → Virtual Cloud Networks → your VCN → Security → the subnet's security list →
Add Ingress Rules: source `0.0.0.0/0`, TCP, destination ports `80,443`. (`server-setup.sh` opens the machine's own
firewall too — Oracle's Ubuntu image blocks everything but SSH, so both are needed.)

## 3. DNS (Hostinger)
hPanel → Domains → panteadesignstudio.com → DNS / Nameservers → add two **A** records, TTL 300:

| Name | Points to |
|---|---|
| `match` | the machine's public IP |
| `receipts` | the machine's public IP |

Check from PowerShell: `Resolve-DnsName receipts.panteadesignstudio.com` must show the IP before step 5.

## 4. Upload (from Windows, every time something changes)
```powershell
cd E:\Work\wordpress\Pantea\paymentverificationwizard
.\deploy\oracle\upload.ps1 -Server <PUBLIC-IP>
```
Packs both projects, the shared CLIP model + Persian tessdata (~330 MB, first time; later add `-SkipModels`),
the style-match photos (`themes\new`) and the receipt references, copies them over SSH and unpacks them into
`~/pantea/` on the server. Sources and photos are replaced; model caches and `.env` are kept.

## 5. First start (on the server)
```bash
ssh -i $HOME\.ssh\oracle_pantea ubuntu@<PUBLIC-IP>

bash ~/pantea/stack/server-setup.sh      # Docker + firewall, once
exit                                     # log out and back in so the docker group applies
ssh -i $HOME\.ssh\oracle_pantea ubuntu@<PUBLIC-IP>

cd ~/pantea/stack
cp -n .env.example .env
openssl rand -base64 32                  # run twice: one key per service
nano .env                                # domains, your e-mail, the two keys
docker compose up -d --build             # first build ~10 min on ARM
docker compose logs -f                   # until both say "Started ...Application", then Ctrl+C
```

Check:
```bash
curl https://receipts.panteadesignstudio.com/api/v1/health
curl https://match.panteadesignstudio.com/api/v1/health
docker compose run --rm --entrypoint sh receipt-gate -c 'exec java $JAVA_OPTS -jar /app/app.jar --verify'
free -h                                  # "used" should be well above 20% of total (idle-reclaim rule)
```

## 6. WordPress
- **Settings → Style Match:** API base `https://match.panteadesignstudio.com`, key = `MATCHER_API_KEY`.
- **Receipt upload plugin** (next step): API base `https://receipts.panteadesignstudio.com`, key = `GATE_API_KEY`.

Hostinger shared hosting can make outbound HTTPS calls, which is all this needs.

## Day to day
| Task | How |
|---|---|
| Deploy a code change | `upload.ps1 -Server <IP> -SkipModels`, then on the server `cd ~/pantea/stack && docker compose up -d --build` |
| Add receipts / style photos | same upload, or copy files into `~/pantea/data/...` and `curl -X POST -H "X-Api-Key: <key>" https://<domain>/api/v1/reindex` |
| Logs | `docker compose logs -f receipt-gate` (verdicts and scores only — never images or OCR text) |
| Restart everything | `docker compose restart` — containers also come back by themselves after a reboot |

## Troubleshooting
- **`ssh` times out:** the instance has no public IP, or the security list lacks the default SSH (22) rule.
- **Browser can't reach the domain:** DNS not pointing at the IP yet, or port 80/443 missing in the security
  list. Caddy needs both to get a certificate; it retries by itself once they are fixed.
- **`docker: permission denied`:** you did not log out and back in after `server-setup.sh`.
- **A container keeps restarting:** `docker compose logs receipt-gate` — a missing `.env` value or model file is
  named in the first error lines.
