# Deploying the receipt gate

Same pattern as the style-match service: Docker Compose, the JVM behind Caddy, models and references on the
host. Any Linux VPS with Docker works; 2 vCPU / 2 GB RAM is plenty (~700 MB with the full CLIP model).

## Alone on a server

```bash
# on your machine: copy the project, then the models and references (not in git)
scp -r paymentverificationwizard you@server:~/receipt-gate
scp -r models references you@server:~/receipt-gate/deploy/

# on the server
cd ~/receipt-gate/deploy
cp .env.example .env && nano .env        # DOMAIN, ACME_EMAIL, GATE_API_KEY
docker compose up -d --build
docker compose logs -f receipt-gate      # wait for "Reference index: 16 receipts ..." then "Started"
curl https://$DOMAIN/api/v1/health
```

`deploy/models/` must contain `clip-vit-base-patch32-vision.onnx` and `tessdata/fas.traineddata`
(`scripts/download-models.sh` puts both in `models/`; copy that folder). `deploy/references/` holds
`receipts/` and `non-receipts/`.

## Next to the style-match service

Both stacks would start a Caddy on ports 80/443, and only one can. Run one Caddy for both:

1. Copy the `receipt-gate:` service block from `docker-compose.yml` into the style-match compose file. Change
   `context: ..` to the path of this project, and adjust the two volume paths.
2. Add a second site to the style-match `Caddyfile`:

   ```
   receipts.yourdomain.com {
       request_body {
           max_size 16MB
       }
       handle /api/* {
           reverse_proxy receipt-gate:8086
       }
       handle {
           respond "Not found" 404
       }
   }
   ```
3. Add `GATE_API_KEY` to that stack's `.env`, then `docker compose up -d --build`.

## After deploying

- **Check the numbers on the server:** `docker compose run --rm --entrypoint sh receipt-gate -c 'exec java $JAVA_OPTS -jar /app/app.jar --verify'`
  should end with `RECEIPT originals -> {RECEIPT=16}` and no non-receipt accepted as RECEIPT.
- **Adding examples:** drop files into `references/receipts/<bank>/` or `references/non-receipts/<kind>/`,
  then `curl -X POST -H "X-Api-Key: $GATE_API_KEY" https://$DOMAIN/api/v1/reindex`.
- **Logs** hold verdicts, scores and the first 12 hex chars of each file's SHA-256 - never the image or its text.
