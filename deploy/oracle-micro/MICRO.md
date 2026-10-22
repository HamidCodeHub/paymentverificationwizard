# Both services on two free Oracle micro machines (€0)

Oracle's bigger free ARM machine (A1) has been "out of capacity" in Milan for days. Every Always Free account
also gets **two AMD `VM.Standard.E2.1.Micro`** machines (1 GB RAM each), which are available right away, so
each service gets its own:

```
https://receipts.panteadesignstudio.com/api/...  -> machine "pantea"        80.225.93.215  receipt gate
https://match.panteadesignstudio.com/api/...     -> machine "pantea-match"  80.225.83.29   style match
```

WordPress stays on Hostinger. Both machines are Ubuntu 24.04, same SSH key (`oracle_pantea`), same VCN; ports
80/443 are already open in the VCN's security list and both DNS A records already exist (TTL 300).

## What is different from the A1 setup

| | A1 (deploy/oracle) | Micro (this folder) |
|---|---|---|
| Machines | 1 × 2 OCPU / 8 GB ARM | 2 × 1 GB AMD, one service each |
| CLIP model | 350 MB fp32 | 96 MB int8 (`scripts/quantize-model.py`) |
| JVM | `-Xms512m`, 50% of RAM | `-Xmx300m`, small code cache, 1 Tesseract at a time |
| Swap | none | 2 GB swap file (for the one-time Maven build) |

The int8 model was checked before switching: the receipt gate's `--verify` gives identical verdicts (16/16
receipts pass, no non-receipt passes) and the style match's leave-one-out check gives identical predictions.
Measured peaks with these settings: receipt gate ~540 MB (+ ~120 MB Tesseract while reading), style match
~400 MB. Inference is ~3× faster than with the fp32 model.

## 1. Make the int8 model (once, on Windows)

Skip if `models\clip-vit-base-patch32-vision-int8.onnx` is already there.

```powershell
cd E:\Work\wordpress\Pantea\paymentverificationwizard
py -m pip install onnxruntime onnx
py scripts\quantize-model.py
```

## 2. Upload (from Windows, every time something changes)

```powershell
cd E:\Work\wordpress\Pantea\paymentverificationwizard
.\deploy\oracle-micro\upload.ps1 -Role receipts -Server 80.225.93.215
.\deploy\oracle-micro\upload.ps1 -Role match    -Server 80.225.83.29
```

Later uploads of code or photos only: add `-SkipModels`.

## 3. First start (on each machine)

```bash
ssh -i $HOME\.ssh\oracle_pantea ubuntu@80.225.93.215      # then the same for 80.225.83.29

bash ~/pantea/stack/server-setup.sh      # swap + Docker + firewall, once
exit                                     # log out and back in so the docker group applies
ssh -i $HOME\.ssh\oracle_pantea ubuntu@80.225.93.215

cd ~/pantea/stack
cp -n .env.example .env
openssl rand -base64 32                  # the API key for this service
nano .env                                # DOMAIN (receipts... or match...), your e-mail, API_KEY
docker compose up -d --build             # first build ~15-20 min on this small machine
docker compose logs -f app               # until "Started ...Application", then Ctrl+C
```

`.env` on the receipts machine: `DOMAIN=receipts.panteadesignstudio.com`; on the match machine:
`DOMAIN=match.panteadesignstudio.com`. Each machine has its own `API_KEY`.

Check from anywhere:
```bash
curl https://receipts.panteadesignstudio.com/api/v1/health
curl https://match.panteadesignstudio.com/api/v1/health
```

Optional, on the receipts machine (~2 min):
```bash
docker compose run --rm --entrypoint sh app -c 'exec java $JAVA_OPTS -jar /app/app.jar --verify'
```

## 4. WordPress
- **Settings → Style Match:** API base `https://match.panteadesignstudio.com`, key = the match machine's `API_KEY`.
- **Receipt upload plugin:** API base `https://receipts.panteadesignstudio.com`, key = the receipts machine's
  `API_KEY`.

## Keeping it up

- Containers restart by themselves after a crash or a reboot (`restart: unless-stopped`, Docker starts at
  boot).
- **Oracle's idle rule:** Oracle may stop an Always Free machine whose CPU (95th percentile) and network both
  stay under 20% for 7 days. A quiet shop can hit that. If it happens: Console → Compute → Instances → the
  machine → **Start**; everything comes back on its own, same IP. Moving to the A1 machine later (when Milan
  has capacity) avoids this, because the A1 rule also requires memory under 20% and the services keep more
  than that in use.
- WordPress must treat "service unreachable" as "send to the admin for manual review", never as "blocked", so
  an outage never stops a real customer.

## Day to day
| Task | How |
|---|---|
| Deploy a code change | `upload.ps1 -Role <role> -Server <IP> -SkipModels`, then on that machine `cd ~/pantea/stack && docker compose up -d --build` |
| Add receipts / style photos | same upload, then `docker compose restart app` (the embedding cache updates itself) |
| Logs | `docker compose logs -f app` (verdicts and scores only — never images or OCR text) |
| Memory | `free -h` — some swap in use is normal after the build |

## Troubleshooting
- **`ssh` times out:** wrong IP, or the machine is stopped (Console → Instances).
- **Browser can't reach the domain:** `Resolve-DnsName receipts.panteadesignstudio.com` must show the IP;
  Caddy retries the certificate by itself once DNS is right.
- **Build killed / "exit code 137":** swap is missing — run `server-setup.sh` again, then rebuild.
- **`docker: permission denied`:** log out and back in after `server-setup.sh`.
- **Container restarts in a loop:** `docker compose logs app` — a missing `.env` value or model file is named
  in the first error lines.
