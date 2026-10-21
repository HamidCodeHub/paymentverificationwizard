# Pantea Receipt Gate

A customer who paid by bank transfer uploads their receipt. This service answers one question: **is that
image a bank-transfer receipt at all?** WordPress then forwards it to the admin, asks for a clearer one, or
blocks it.

Spring Boot service, sibling of the style-match service in `../wizard` (same CLIP model, same layout and
conventions). No per-request cost, nothing leaves your server, nothing is stored.

> **It does not verify payment.** It never reads amounts, IBANs or dates, and a receipt can be pending (Paya),
> cancelled or forged. `RECEIPT` means "worth the admin's time"; the admin confirms the money in the bank
> account before the order moves on.

---

## How it decides

Two free signals that fail in opposite places:

| Signal | Good at | Bad at |
|---|---|---|
| **Receipt words**: Tesseract reads the Persian *words* (never the digits) and scores receipt vocabulary | Telling a receipt from every other phone screenshot: chats, a bank app's home screen, the shop's own order page | Images too blurry, cropped or small to read |
| **CLIP probe**: logistic regression on CLIP vectors, trained on `references/` | Telling a receipt-like image from room photos, Instagram posts, letters, even when nothing is readable | Telling a receipt from other banking screenshots: to CLIP they all look alike |

```
words >= 5                 -> RECEIPT       attach to order, email the admin
else CLIP probability >= .5 -> UNSURE        ask for a clearer screenshot, offer "send anyway" (flagged)
else                        -> NOT_RECEIPT   block, explain that a bank transfer receipt is expected
```

Words decide. CLIP never makes anything a `RECEIPT`; it only rescues an unreadable receipt from being blocked.

**Word weights** (`KeywordScorer`): 3 for the tracking-number label (پیگیری، رهگیری، شماره مرجع، کد مرجع);
2 for receipt phrases (شبا مقصد، حساب/سپرده مبدا، واریز به، گیرنده، فرستنده، رسید، وضعیت انتقال، دستور پرداخت …);
1 for banking words that also appear elsewhere (مبلغ، ریال، شبا، پایا، ساتنا، انتقال …). Text is normalised first
(Arabic ي/ك → Persian ی/ک, hamza forms, ZWNJ, all whitespace), because banks and OCR mix them freely.

**Sideways photos:** if the upright pass finds fewer than 5 points, OCR retries at 90° and 270°, unless CLIP
is confident it is not a receipt (< 0.10, e.g. any room photo), which saves two Tesseract runs.

### Measured (`--verify=full`, 20 Oct 2026)

16 real receipts from 11 bank layouts, 80 degraded copies of them, 39 non-receipts (11 room photos + 28 rendered
Persian screenshots: chats, bank app home and history screens, the shop's cart and order-received pages with an
IBAN on them, pre-invoices, letters, notes, settings, inbox, calendar, gallery, home screen, Instagram). Every
image is judged by a probe trained **without its whole group**: a receipt from a bank layout the probe has never
seen, a kind of screenshot it has never seen.

| | RECEIPT | UNSURE | NOT_RECEIPT |
|---|---|---|---|
| Real receipts (16) | **16** | 0 | 0 |
| Degraded receipts (80: crop top/bottom 60%, rotated, blur + JPEG q35, 240 px wide) | 54 | 24 | 2 |
| Non-receipts (39) | **0** | 8 | 31 |

The 8 "unsure" non-receipts were chats, pre-invoices, the shop's order page and one bank home screen: they cost
the admin a glance, never a wrong acceptance. The 2 blocked receipts are the same printed branch form, cropped
to its bottom 40% and badly blurred.

**Small set, honest caveat.** These are 16 receipts and mostly synthetic negatives. Once customers use it, add
real uploads to `references/` (both kinds) and re-run `--verify`.

Speed on 2 CPU cores: ~1-1.5 s for a normal receipt (one CLIP pass + one Tesseract pass), up to ~3.5 s for a
sideways photo (three OCR passes).

---

## Setup (Windows)

**Requirements:** Java 17+, Maven, Tesseract 5, ~400 MB disk.

```powershell
# 1. Tesseract (once)
winget install UB-Mannheim.TesseractOCR

# 2. Models: copies the CLIP model from ..\wizard\models, downloads fas.traineddata (tessdata_best)
.\scripts\download-models.ps1

# 3. Check the numbers on your own references before wiring anything up (~2 min)
mvn spring-boot:run "-Dspring-boot.run.arguments=--verify"

# 4. Run it
mvn spring-boot:run
```

Tesseract does not need to be on PATH: on Windows the service also looks in `C:\Program Files\Tesseract-OCR\`
(and the x86 / per-user equivalents) and logs which one it used. Installed somewhere else, set
`gate.ocr.command` to the full path of `tesseract.exe`. Then open <http://localhost:8086/swagger-ui.html> and try
`POST /api/v1/receipts/check`.

First start embeds every reference image plus 5 degraded copies of each receipt (~30 s); the vectors are
cached in `models/reference-index.json`, so later starts take a few seconds.

On Linux/macOS: `./scripts/download-models.sh`, then the same Maven commands.

---

## References

```
references/
  receipts/
    blu/receipt-4.jpg ...          one sub-folder per bank or app layout
    branch-printout/...
  non-receipts/
    chats/...                      one sub-folder per kind of image
    bank-app-home/...
    room-photos/...
```

Sub-folders are optional; `--verify` uses them as groups to hold out. The more varied the receipts, the better
the probe: **add every real receipt customers send** (and every wrong upload to `non-receipts/`), then
`POST /api/v1/reindex`. Only new files are embedded.

`references/receipts/` is gitignored: real receipts carry names, IBANs, national ID and phone numbers.
Also kept out of git: `non-receipts/room-photos/` and the screenshots that embed those photos (third-party
images). A fresh clone therefore has the synthetic non-receipts only; add your own receipts and photos before
running `--verify`.

---

## API

| | |
|---|---|
| `POST /api/v1/receipts/check` | multipart `image` (JPG/PNG, ≤ 15 MB) → verdict |
| `GET  /api/v1/health` | public; Tesseract version, reference counts, whether the probe is trained |
| `POST /api/v1/reindex` | re-scan `references/` after adding examples |

Swagger UI: <http://localhost:8086/swagger-ui.html> (disabled in the `prod` profile). Everything except
`/health` requires `X-Api-Key` once `gate.api-key` is set.

```json
{
  "verdict": "RECEIPT",
  "reason": "RECEIPT_TEXT_FOUND",
  "keywordScore": 17,
  "minKeywordScore": 5,
  "keywordsFound": ["پیگیری", "سپرده مبدا", "وضعیت انتقال", "روش انتقال", "مبلغ", "پایا", "تراکنش", "انتقال", "مبدا", "انجام"],
  "clipProbability": 0.9476,
  "unsureProbability": 0.5,
  "ocrRotation": 0,
  "ocrPasses": 1,
  "sha256": "3c4d820438fd…",
  "width": 465,
  "height": 1080,
  "elapsedMs": 1443
}
```

`reason` is one of `RECEIPT_TEXT_FOUND`, `RECEIPT_LIKE_BUT_TEXT_UNCLEAR`, `NOT_A_RECEIPT`, `IMAGE_TOO_SMALL`,
`OCR_UNAVAILABLE`. Errors are always `{"error": "<customer-safe message>"}`: 400 unreadable or oversized, 401 key,
413 over 15 MB, 415 WebP/HEIC/AVIF/PDF, 429 rate limit.

### What the WordPress side should do

| Verdict | Order | Customer sees (suggestion) | Admin |
|---|---|---|---|
| `RECEIPT` | stays **on-hold**; receipt attached privately; order note with the verdict | «رسید شما دریافت شد و پس از تأیید واریز، سفارش شما پردازش می‌شود.» | email with a link to the order |
| `UNSURE` | unchanged | «تصویر واضح نیست. لطفاً اسکرین‌شات کامل رسید را بارگذاری کنید.» + "send anyway" | on "send anyway": email, flagged *unverified* |
| `NOT_RECEIPT` | unchanged | «این تصویر رسید واریز بانکی نیست. لطفاً رسید انتقال وجه را بارگذاری کنید.» | nothing |
| `OCR_UNAVAILABLE` (as UNSURE) | unchanged | same as UNSURE | flagged |

Also on the WordPress side: store `sha256` on the order and refuse the same file on another order; convert
WebP/HEIC to JPEG before calling (`wp_get_image_editor`); send the customer's IP as `X-Forwarded-For` so the rate
limit is per customer; never put the receipt in the public uploads folder.

---

## Tuning

| Property | Default | Effect |
|---|---|---|
| `gate.decision.min-keyword-score` | 5 | Words needed for RECEIPT. Receipts scored 5-22, hardest negatives 1-4. |
| `gate.decision.unsure-probability` | 0.5 | Lower = more unreadable receipts become UNSURE, and more chats too |
| `gate.decision.rotation-retry-min-probability` | 0.10 | CLIP below this skips sideways OCR retries |
| `gate.ocr.retry-rotations` | 90, 270 | Add 180 if customers upload upside-down photos |
| `gate.ocr.max-concurrent` | 2 | Tesseract processes at once; one per core is right |
| `gate.probe.l2` | 0.0005 | 0.05 made every score ~0.5; 0.0005 separates |
| `gate.clip.augment-receipts` | true | Train on degraded receipt copies too; keeps blurry photos out of NOT_RECEIPT |

Change a threshold, then run `--verify` (originals, ~2 min) or `--verify=full` (with degraded copies, ~4 min).

---

## Design notes

- **Tesseract runs as a process**, not through JNA: a native crash or hang costs one request, not the JVM, and
  there is no native-library version to match. `OMP_THREAD_LIMIT=1` per process, `max-concurrent` processes.
- **The OCR text never leaves the request**: it is not returned, not logged, and the upload is never written
  anywhere except a temp PNG for Tesseract, deleted in a `finally`. Logs hold verdict, scores and a 12-char hash.
- **Fail open to a human:** if Tesseract fails, anything CLIP does not rule out becomes UNSURE rather than
  blocking a paying customer.
- **Decompression bombs** are refused from the image header (`max-pixels`, 40 MP) before any pixels are decoded.
- `bench/` holds the Python scripts the thresholds came from (`clip_gate_bench.py`, `probe.py`).

## Deployment

See `deploy/DEPLOY.md`: Docker Compose behind Caddy, same as style-match; the models and references are mounted,
never baked into the image. Next to style-match on one server, share its Caddy (instructions there).
