<?php
/**
 * Pantea – desktop layout
 *
 * Desktop (1025 px and wider) layouts for all pages, in the site's theme: sand background, white cards with the
 * #e2d9cb border, black/outlined buttons, Vazirmatn. Phones and tablets are not touched (everything is inside
 * one min-width media query, except the star fix at the end). Pages: home, صفحه خرید, رزرو جلسه مشاوره, درباره من, سوالات متداول, ارتباط با ما,
 * پرداخت از داخل ایران, پرسشنامه, shop, product, cart/checkout. Deactivate to get the old desktop look back.
 * The only rule outside the media query fixes the rating stars, which showed as letters on all screen sizes.
 * Page-specific rules use the page id (body.page-id-N) and Elementor element ids; if a page is rebuilt in
 * Elementor, its block here may need updating.
 */
add_action( 'wp_head', function () {
	?>
<style id="pd-desktop">
@media (min-width:1025px){

/* ===== Global: theme surface, buttons, headings ===== */
html,body,#page,.site,.site-content,#content{background:#faf8f5!important}
body .site-content,body #primary,body .ast-container,body .entry-content{background:transparent}
.elementor .e-con .e-button-base{transition:background .15s,color .15s,border-color .15s}
/* Content buttons (not the header/menu ones): outlined black like the phone version */
.elementor[data-elementor-type="wp-page"]>.e-con:nth-child(n+3) a.e-button-base{background:transparent!important;color:#111!important;border:1px solid #111!important;border-radius:0!important;font-weight:500;font-size:15px;padding:14px 36px!important;width:auto!important;min-width:260px;align-self:center;text-align:center;text-decoration:none}
.elementor[data-elementor-type="wp-page"]>.e-con:nth-child(n+3) a.e-button-base:hover{background:#111!important;color:#fff!important}
.elementor[data-elementor-type="wp-page"] hr.e-divider-base{border-color:#e2d9cb!important;max-width:1100px;margin-inline:auto}

/* ===== Home (page-id-35): hero beside the video, the two services side by side ===== */
html body.page-id-35 .elementor-35{display:grid;grid-template-columns:minmax(24px,1fr) minmax(0,560px) minmax(0,620px) minmax(24px,1fr);column-gap:56px;align-items:center}
html body.page-id-35 .elementor-35>.e-con{grid-column:1/-1;width:auto!important;max-width:none!important;min-width:0;margin-inline:0!important;justify-self:stretch}
html body.page-id-35 .elementor-35>.elementor-element-ff4cdc3{grid-column:2;grid-row:3;background:transparent!important;padding:0!important}
html body.page-id-35 .elementor-35>.elementor-element-e929193{grid-column:2;grid-row:4;background:transparent!important;padding:0!important;align-self:start}
html body.page-id-35 .elementor-35>.elementor-element-4c2cc57{grid-column:3;grid-row:3/span 2;padding:48px 0}
html body.page-id-35 .elementor-35>.elementor-element-0aea8ac{grid-column:2;grid-row:5}
html body.page-id-35 .elementor-35>.elementor-element-52c0d2a{grid-column:3;grid-row:5}
html body.page-id-35 .elementor-35>.e-con:nth-child(n+3)>.e-con-inner{max-width:100%!important;width:100%}
html body.page-id-35 .elementor .elementor-element-ff4cdc3>.e-con-inner{padding:48px 0 16px;gap:14px}
html body.page-id-35 .elementor .elementor-element-ff4cdc3 h4:first-of-type{font-size:36px!important;line-height:1.3!important;font-weight:700}
html body.page-id-35 .elementor .elementor-element-ff4cdc3 h4+h4{font-size:21px!important;font-weight:500;color:#555}
html body.page-id-35 .elementor .elementor-element-ff4cdc3 p,html body.page-id-35 .elementor .elementor-element-ff4cdc3 span{font-size:17px!important;line-height:2!important;color:#444}
body.page-id-35 .elementor[data-elementor-type="wp-page"]>.e-con.elementor-element-ff4cdc3 a.e-button-base,body.page-id-35 .elementor[data-elementor-type="wp-page"]>.e-con.elementor-element-e929193 a.e-button-base{align-self:flex-start!important}
body.page-id-35 .elementor[data-elementor-type="wp-page"]>.e-con.elementor-element-e929193 a.e-button-base{background:#111!important;color:#fff!important}
body.page-id-35 .elementor[data-elementor-type="wp-page"]>.e-con.elementor-element-e929193 a.e-button-base:hover{background:#333!important}
html body.page-id-35 .elementor .elementor-element-e929193 hr{display:none}
html body.page-id-35 .elementor .elementor-element-4c2cc57 video{width:100%!important;height:auto!important;max-height:72vh;object-fit:cover;border-radius:4px;box-shadow:0 20px 50px rgba(60,40,20,.15)}
html body.page-id-35 .elementor .elementor-element-0aea8ac,html body.page-id-35 .elementor .elementor-element-52c0d2a{align-self:stretch;background:#fff!important;border:1px solid #e2d9cb;border-radius:4px;padding:36px!important;margin:56px 0 72px}
html body.page-id-35 .elementor .elementor-element-0aea8ac>.e-con-inner,html body.page-id-35 .elementor .elementor-element-52c0d2a>.e-con-inner{height:100%;justify-content:space-between;gap:14px;padding:0}
html body.page-id-35 .elementor .elementor-element-0aea8ac h2,html body.page-id-35 .elementor .elementor-element-52c0d2a h2{font-size:24px!important;text-align:center!important}
html body.page-id-35 .elementor .elementor-element-0aea8ac p,html body.page-id-35 .elementor .elementor-element-52c0d2a p,html body.page-id-35 .elementor .elementor-element-0aea8ac span,html body.page-id-35 .elementor .elementor-element-52c0d2a span{text-align:center;font-size:16px!important;line-height:1.9!important;color:#555}
html body.page-id-35 .elementor .elementor-element-52c0d2a hr{display:none}


/* ===== Shared: custom HTML-widget pages get a wider canvas ===== */
.elementor[data-elementor-type="wp-page"] .elementor-widget-html{width:100%}

/* ===== صفحه خرید (page-id-1044): two steps side by side, wider ===== */
body.page-id-1044 .pd-shop{max-width:1120px!important;display:grid!important;grid-template-columns:1fr 1fr;column-gap:40px;padding:56px 24px 80px!important}
body.page-id-1044 .pd-shop>*{grid-column:1/-1}
body.page-id-1044 .pd-shop>.step{grid-column:auto;align-self:stretch;background:#fff;border:1px solid #e2d9cb;border-radius:4px;padding:22px 20px!important;margin:0 0 24px!important}
body.page-id-1044 .pd-shop>.prices{max-width:860px;width:100%;margin-inline:auto!important}
body.page-id-1044 .pd-shop .title{font-size:34px!important}
body.page-id-1044 .pd-shop .lead,body.page-id-1044 .pd-shop .sub,body.page-id-1044 .pd-shop .closing{font-size:17px!important}
body.page-id-1044 .pd-shop .closing{max-width:820px;margin-inline:auto!important;text-align:center}

/* ===== رزرو جلسه مشاوره (page-id-1251): intro beside the steps, topics as a grid ===== */
body.page-id-1251 .pc-page{max-width:1120px!important;display:grid!important;grid-template-columns:1fr 1fr;column-gap:64px;padding:56px 24px 40px!important}
body.page-id-1251 .pc-page>*{grid-column:1/-1}
body.page-id-1251 .pc-page>.pc-intro{grid-column:1;grid-row:4/span 2;width:auto!important;max-width:none!important;align-self:start;font-size:17px}
body.page-id-1251 .pc-page>.pc-intro p{font-size:17px!important;line-height:2.1!important;text-align:start!important}
body.page-id-1251 .pc-page>.pc-sec{grid-column:2;grid-row:4}
body.page-id-1251 .pc-page>.pc-steps{grid-column:2;grid-row:5;width:auto!important;max-width:none!important}
body.page-id-1251 .pc-page>.pc-note{grid-column:1;grid-row:6;justify-self:start;align-self:start}
body.page-id-1251 .pc-page>.pc-intro2{max-width:760px!important;width:auto!important;margin-inline:auto!important}
body.page-id-1251 .pc-page>.pc-topics{width:auto!important;max-width:none!important;display:grid!important;grid-template-columns:repeat(4,1fr);gap:16px}
body.page-id-1251 .pc-page>.pc-topics>li{background:#fff;border:1px solid #e2d9cb;border-radius:4px;padding:18px!important;margin:0!important;width:auto!important}
body.page-id-1251 .pc-page>.pc-box{grid-column:auto;width:auto!important;max-width:none!important;margin-top:28px!important}
body.page-id-1251 .pc-title{font-size:36px!important}

/* ===== سوالات متداول (page-id-18): intro on one side, questions on the other ===== */
body.page-id-18 .faq-page{max-width:1140px!important;display:grid!important;grid-template-columns:400px minmax(0,1fr);column-gap:56px;align-items:start;padding:48px 24px 80px!important}
body.page-id-18 .faq-page>*{grid-column:1;width:auto!important;max-width:none!important}
body.page-id-18 .faq-page>.acc{grid-column:2;grid-row:1/span 6}
body.page-id-18 .faq-page>.cta .cta-btn{width:100%!important}

/* ===== پرداخت از داخل ایران (page-id-1070): steps + bank details beside the payment box ===== */
body.page-id-1070 .pd-iran{max-width:1120px!important;display:grid!important;grid-template-columns:1fr 1fr;column-gap:40px;align-items:start;padding:48px 24px 80px!important}
body.page-id-1070 .pd-iran>*{grid-column:1/-1;width:auto!important;max-width:none!important}
body.page-id-1070 .pd-iran>.steps{grid-column:1;grid-row:4}
body.page-id-1070 .pd-iran>.bank{grid-column:1;grid-row:5}
body.page-id-1070 .pd-iran>.pd-box{grid-column:2;grid-row:4/span 2;position:sticky;top:24px;margin-top:0!important}
body.page-id-1070 .pd-iran>.title{font-size:34px!important}

/* ===== ارتباط با ما (page-id-655): form beside the contact details ===== */
body.page-id-655 .elementor-655{display:grid;grid-template-columns:minmax(24px,1fr) minmax(0,640px) minmax(0,440px) minmax(24px,1fr);column-gap:48px;align-items:start}
body.page-id-655 .elementor-655>.e-con{grid-column:1/-1;width:auto!important;max-width:none!important;min-width:0;margin-inline:0!important}
body.page-id-655 .elementor-655>.elementor-element-bf1d45c{grid-column:2;grid-row:4}
body.page-id-655 .elementor-655>.elementor-element-c494e8d{grid-column:3;grid-row:4;position:sticky;top:24px}
body.page-id-655 .elementor-655>.elementor-element-bf1d45c .pd-form,body.page-id-655 .elementor-655>.elementor-element-c494e8d .pd-contact{max-width:none!important;width:100%!important;margin:0!important}
body.page-id-655 .elementor-655>.elementor-element-c494e8d .pd-contact{background:#fff;border:1px solid #e2d9cb;border-radius:4px;padding:28px!important}
body.page-id-655 .elementor-655>.elementor-element-c494e8d .pd-divider{display:none}
body.page-id-655 .pd-page .pd-title{font-size:34px!important}


body.page-id-18 .elementor-element-06a46c6 .e-heading-base{text-align:center!important}

/* ===== درباره من (page-id-16): portrait beside the intro, photo pairs, 4-column gallery ===== */
body.page-id-16 .elementor-element-c67682e{padding:56px 24px!important}
body.page-id-16 .elementor-element-c67682e>.e-con-inner{display:grid!important;grid-template-columns:minmax(0,1fr) minmax(0,1fr);column-gap:64px;row-gap:40px;max-width:1140px!important;margin-inline:auto;align-items:start}
body.page-id-16 .elementor-element-c67682e>.e-con-inner>*,body.page-id-16 .elementor-element-94642c6>.e-con-inner>*{width:auto!important;max-width:none!important;margin:0!important;min-width:0}
body.page-id-16 .elementor-element-94642c6,body.page-id-16 .elementor-element-94642c6>.e-con-inner{display:contents!important}
body.page-id-16 .elementor-element-2e52499{grid-column:1;grid-row:1/span 2}
body.page-id-16 .elementor-element-34c47a9{grid-column:2;grid-row:1;align-self:end}
body.page-id-16 .elementor-element-b92b1fd{grid-column:2;grid-row:2}
body.page-id-16 .elementor-element-794dd24{grid-column:1/-1;grid-row:3}
body.page-id-16 .elementor-element-b75e092{grid-column:1/-1;grid-row:4;justify-self:center;max-width:820px!important;text-align:center}
body.page-id-16 .elementor-element-e083e47{grid-column:1/-1;grid-row:5;display:flex!important}
body.page-id-16 .elementor-element-a3f7251{grid-column:1/-1;grid-row:6;justify-self:center;max-width:820px!important;text-align:center}
body.page-id-16 .elementor-element-2e52499 img{width:100%!important;height:auto!important;max-height:720px;object-fit:cover;border-radius:4px}
body.page-id-16 .elementor-element-34c47a9 p{font-size:30px!important;line-height:1.5!important}
body.page-id-16 .elementor-element-b92b1fd *,body.page-id-16 .elementor-element-b75e092 *,body.page-id-16 .elementor-element-a3f7251 *{font-size:17px!important;line-height:2.1!important;color:#444}
body.page-id-16 .elementor-element-794dd24,body.page-id-16 .elementor-element-e083e47{flex-direction:row!important;gap:24px!important;padding:0!important}
body.page-id-16 .elementor-element-794dd24>*,body.page-id-16 .elementor-element-e083e47>*{flex:1 1 0!important;width:auto!important;max-width:none!important;min-width:0}
body.page-id-16 .elementor-element-794dd24 img,body.page-id-16 .elementor-element-e083e47 img{width:100%!important;height:auto!important;aspect-ratio:5/6;object-fit:cover;border-radius:4px}
body.page-id-16 .elementor-element-98425fb{padding:0 24px 72px!important}
body.page-id-16 .elementor-element-98425fb .gallery{max-width:1140px;margin-inline:auto!important;display:grid!important;grid-template-columns:repeat(4,1fr);gap:16px}
body.page-id-16 .elementor-element-98425fb .gallery-item{width:auto!important;max-width:none!important;margin:0!important;padding:0!important}
body.page-id-16 .elementor-element-98425fb .gallery-item img{width:100%!important;height:auto!important;aspect-ratio:3/4;object-fit:cover;border-radius:4px;border:0!important}

/* ===== پرسشنامه (page-id-289): the quiz as a centred card ===== */
body.page-id-289 .quiz{max-width:900px!important;margin:48px auto 72px!important;background:#fff!important;border:1px solid #e2d9cb;border-radius:4px;overflow:hidden}
body.page-id-289 .quiz .q-footer{background:#fff}
body.page-id-289 .quiz .btn-next{max-width:360px;margin-inline:auto;display:block}

/* ===== WooCommerce (shop, product, cart, checkout): theme colours instead of the purple default ===== */
.woocommerce a.button,.woocommerce button.button,.woocommerce input.button,.woocommerce #respond input#submit,.woocommerce a.button.alt,.woocommerce button.button.alt,.wc-block-components-button,.wp-block-button__link,.woocommerce .single_add_to_cart_button{background:#111!important;color:#fff!important;border:1px solid #111!important;border-radius:0!important;font-weight:500}
.woocommerce a.button:hover,.woocommerce button.button:hover,.wc-block-components-button:hover,.wp-block-button__link:hover,.woocommerce .single_add_to_cart_button:hover{background:#333!important;border-color:#333!important}
.woocommerce-page:not(.woocommerce-account) .entry-content a:not(.button):not(.wp-block-button__link),.woocommerce div.product .product_meta a,.woocommerce .woocommerce-breadcrumb a,.woocommerce-review-link{color:#111!important}
.woocommerce .star-rating span::before,.woocommerce .star-rating::before{color:#b89c7a!important}
.wc-block-cart__empty-cart__title,.woocommerce-page .wp-block-woocommerce-empty-cart-block{max-width:720px;margin-inline:auto}
.wp-block-woocommerce-empty-cart-block .wp-block-button__link{display:inline-block;width:auto!important;height:auto!important;padding:14px 36px!important}
.wp-block-woocommerce-empty-cart-block .wp-block-buttons{justify-content:center}

/* Shop archive: product cards in a grid */
body.woocommerce-shop .site-main,body.tax-product_cat .site-main{max-width:1140px;margin:0 auto;padding:48px 24px 80px}
body.woocommerce-shop ul.products,body.tax-product_cat ul.products{display:grid!important;grid-template-columns:repeat(2,minmax(0,1fr))!important;align-items:stretch!important;gap:32px;max-width:1000px;margin:24px auto 0!important}
body.woocommerce-shop ul.products::before,body.woocommerce-shop ul.products::after{display:none!important}
body.woocommerce-shop ul.products li.product,body.tax-product_cat ul.products li.product{width:auto!important;margin:0!important;float:none!important;background:#fff;border:1px solid #e2d9cb;border-radius:4px;padding:0 0 24px!important;text-align:center;overflow:hidden}
body.woocommerce-shop ul.products li.product img,body.tax-product_cat ul.products li.product img{width:100%!important;height:auto!important;aspect-ratio:4/3;object-fit:cover;margin:0 0 16px!important}
body.woocommerce-shop ul.products li.product .woocommerce-loop-product__title{font-size:20px!important;color:#000!important}
body.woocommerce-shop ul.products li.product .price{color:#333!important;font-size:17px}
body.woocommerce-shop .woocommerce-breadcrumb,body.woocommerce-shop .woocommerce-result-count,body.woocommerce-shop .woocommerce-ordering{max-width:1000px}

/* Single product */
body.single-product .site-main{max-width:1140px;margin:0 auto;padding:48px 24px 80px}
body.single-product div.product .product_title{font-size:32px;color:#000}
body.single-product div.product p.price,body.single-product div.product span.price{color:#111!important;font-size:24px}
body.single-product .woocommerce-product-gallery img{border-radius:4px}
body.single-product div.product{display:grid!important;grid-template-columns:minmax(0,1fr) minmax(0,1fr);column-gap:56px;align-items:start}
body.single-product div.product>*{grid-column:1/-1;width:auto!important;float:none!important;margin:0!important}
body.single-product div.product>.woocommerce-product-gallery{grid-column:1;grid-row:1}
body.single-product div.product>.summary{grid-column:2;grid-row:1;background:#fff;border:1px solid #e2d9cb;border-radius:4px;padding:36px!important}
body.single-product div.product>.woocommerce-tabs,body.single-product div.product>.related,body.single-product div.product>.up-sells{margin-top:56px!important}
body.single-product .woocommerce-product-gallery__image img{max-height:620px!important;width:100%!important;object-fit:cover!important}
body.single-product div.product .product_title{display:block!important;font-size:30px!important;margin:8px 0 12px!important}
body.single-product .single-product-category a,.woocommerce .ast-woo-product-category,body.single-product .product_meta a{color:#7c7c7c!important}
.woocommerce-tabs ul.tabs li.active,.woocommerce-tabs ul.tabs li.active a{border-color:#111!important;color:#111!important}
.woocommerce-tabs ul.tabs li.active:before,.woocommerce-tabs ul.tabs li.active::after{background:#111!important;border-color:#111!important}
#ast-scroll-top{background:#111!important}
body.single-product .related ul.products,body.single-product .up-sells ul.products{display:grid!important;grid-template-columns:repeat(3,minmax(0,1fr))!important;gap:32px;align-items:stretch!important;margin:24px 0 0!important}
body.single-product .related ul.products::before,body.single-product .related ul.products::after{display:none!important}
body.single-product .related ul.products li.product,body.single-product .up-sells ul.products li.product{width:auto!important;margin:0!important;float:none!important;background:#fff;border:1px solid #e2d9cb;border-radius:4px;padding:0 0 20px!important;text-align:center;overflow:hidden}
body.single-product .related ul.products li.product img,body.single-product .up-sells ul.products li.product img{width:100%!important;height:auto!important;aspect-ratio:4/3;object-fit:cover;margin:0 0 14px!important}

}

/* Rating stars: keep WooCommerce's star font (the site-wide font override showed them as "sssss") - all screen sizes */
.woocommerce .star-rating,.woocommerce .star-rating::before,.woocommerce .star-rating span::before{font-family:star!important}
</style>
	<?php
}, 100 );
