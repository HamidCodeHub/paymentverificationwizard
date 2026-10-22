# WordPress side of the receipt gate

`pantea-receipt-check.php` is the Code Snippets snippet **"Pantea – receipt check before upload (demo)"** live on
panteadesignstudio.com (paste without the first `<?php` line). It adds the automatic check to the upload form of
`[pd_iran_payment]` on `/iran-payment/` (snippet "Iranian bank transfer – receipt upload", unchanged):

| Service verdict | Customer sees |
|---|---|
| RECEIPT | form is submitted as before → the new order is completed automatically → quiz right away, no e-mails (order note says it was auto-approved; Pantea still checks the bank) |
| NOT_RECEIPT | "not a payment receipt, upload the correct one" (not counted) |
| UNSURE | "not clear, upload a clearer one (attempt n of 3)"; after the 3rd the three images are e-mailed to Pantea (review address) and the customer is told she will be in touch; if the mail fails, a mailto button instead (counter resets after 1 hour or on a good receipt) |
| service down / no API key | form is submitted as before → manual review (a paying customer is never blocked) |

Settings → General → **Receipt check**: API key (from `~/pantea/stack/.env` on the receipts server), API address
(default `https://receipts.panteadesignstudio.com`), e-mail for unclear receipts (default: the admin e-mail).

**Admin demo:** administrators always see the upload form (even after buying), with a note customers never see. A good receipt
shows the confirmation and the quiz button on the page without creating an order; after 3 unclear uploads a
"شروع دوباره (فقط مدیر)" link resets the counter.

The browser re-encodes the image to JPEG (max 2000 px) before sending, so WebP and iPhone photos work and uploads
stay small; the API key stays on the WordPress server (admin-ajax proxy). All WordPress calls reach the service
from Hostinger's IP, so its per-IP rate limit (20/min) is effectively site-wide.

## Other site snippets

- `pantea-my-account.php` — **"Pantea – My account look"**: My account in the site's colours, Downloads hidden, orders first on the dashboard.
- `pantea-desktop.php` — **"Pantea – desktop layout"**: real desktop layouts (1025 px and wider) for the home page, صفحه خرید,
  رزرو جلسه مشاوره, درباره من, سوالات متداول, ارتباط با ما, پرداخت از داخل ایران, the quiz, shop, product pages and My account.
  Everything sits inside one `min-width:1025px` media query, so phones and tablets look exactly as before; the one exception
  fixes WooCommerce rating stars, which showed as "sssss" on every screen size. Deactivate the snippet to get the old
  desktop look back. Rules target page ids and Elementor element ids, so a page rebuilt in Elementor may need its block updated.
