# WordPress side of the receipt gate

`pantea-receipt-check.php` is the Code Snippets snippet **"Pantea – receipt check before upload (demo)"** live on
panteadesignstudio.com (paste without the first `<?php` line). It adds the automatic check to the upload form of
`[pd_iran_payment]` on `/iran-payment/` (snippet "Iranian bank transfer – receipt upload", unchanged):

| Service verdict | Customer sees |
|---|---|
| RECEIPT | form is submitted as before → on-hold order, Pantea confirms → quiz |
| NOT_RECEIPT | "not a payment receipt, upload the correct one" (not counted) |
| UNSURE | "not clear, upload a clearer one (attempt n of 3)"; after the 3rd → "e-mail the receipt to Pantea" with a mailto button (counter resets after 1 hour or on a good receipt) |
| service down / no API key | form is submitted as before → manual review (a paying customer is never blocked) |

Settings → General → **Receipt check**: API key (from `~/pantea/stack/.env` on the receipts server), API address
(default `https://receipts.panteadesignstudio.com`), e-mail for unclear receipts (default: the admin e-mail).

The browser re-encodes the image to JPEG (max 2000 px) before sending, so WebP and iPhone photos work and uploads
stay small; the API key stays on the WordPress server (admin-ajax proxy). All WordPress calls reach the service
from Hostinger's IP, so its per-IP rate limit (20/min) is effectively site-wide.
