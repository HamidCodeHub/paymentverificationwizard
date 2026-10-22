<?php
/**
 * Pantea – My account look
 *
 * My account (حساب من) in the site's style: beige page, white cards with the sand border, Vazirmatn, black
 * primary buttons, blue accent. Downloads (دانلودها) is removed from the menu, and Orders (سفارش‌ها) is made
 * the centre of the page: highlighted in the menu, and the dashboard shows the latest orders with their
 * buttons (view, start quiz, catalog) plus a big "all orders" button.
 * Deactivating this snippet restores the default WooCommerce look.
 */

// Menu: no downloads; orders right after the dashboard.
add_filter( 'woocommerce_account_menu_items', function ( $items ) {
	unset( $items['downloads'] );
	$order = array( 'dashboard', 'orders', 'edit-account', 'edit-address', 'customer-logout' );
	$out   = array();
	foreach ( $order as $key ) {
		if ( isset( $items[ $key ] ) ) {
			$out[ $key ] = $items[ $key ];
			unset( $items[ $key ] );
		}
	}
	// Anything another plugin added goes before "log out".
	$logout = isset( $out['customer-logout'] ) ? array( 'customer-logout' => $out['customer-logout'] ) : array();
	unset( $out['customer-logout'] );
	return $out + $items + $logout;
}, 50 );

// The downloads page itself also goes away (link from old e-mails, bookmarks).
add_action( 'template_redirect', function () {
	if ( function_exists( 'is_wc_endpoint_url' ) && is_wc_endpoint_url( 'downloads' ) ) {
		wp_safe_redirect( wc_get_account_endpoint_url( 'orders' ) );
		exit;
	}
} );

// Dashboard: greeting, latest orders, quick links. (The default dashboard text is hidden by the CSS below.)
add_action( 'woocommerce_account_dashboard', function () {
	$user   = wp_get_current_user();
	$name   = $user->first_name ? $user->first_name : $user->display_name;
	$orders = wc_get_orders( array(
		'customer_id' => $user->ID,
		'limit'       => 3,
		'orderby'     => 'date',
		'order'       => 'DESC',
		'status'      => array_keys( wc_get_order_statuses() ),
	) );
	$all = wc_get_account_endpoint_url( 'orders' );

	echo '<div class="pd-acc-dash">';
	echo '<h2 class="pd-acc-hello">سلام ' . esc_html( $name ) . '</h2>';
	echo '<p class="pd-acc-sub">سفارش‌ها، پرسشنامه و کاتالوگ‌های شما همه از همین‌جا در دسترس است.</p>';

	echo '<section class="pd-acc-card pd-acc-orders">';
	echo '<div class="pd-acc-card-head"><h3>سفارش‌های من</h3>';
	if ( $orders ) {
		echo '<a class="pd-acc-link" href="' . esc_url( $all ) . '">همه سفارش‌ها ←</a>';
	}
	echo '</div>';

	if ( ! $orders ) {
		echo '<p class="pd-acc-empty">هنوز سفارشی ثبت نکرده‌اید.</p>';
	} else {
		echo '<ul class="pd-acc-list">';
		foreach ( $orders as $order ) {
			$status  = $order->get_status();
			$created = $order->get_date_created();
			echo '<li class="pd-acc-order">';
			echo '<div class="pd-acc-order-main">';
			echo '<a class="pd-acc-order-no" href="' . esc_url( $order->get_view_order_url() ) . '">سفارش #' . esc_html( $order->get_order_number() ) . '</a>';
			echo '<span class="pd-acc-status pd-acc-status--' . esc_attr( $status ) . '">' . esc_html( wc_get_order_status_name( $status ) ) . '</span>';
			echo '</div>';
			echo '<div class="pd-acc-order-meta">';
			if ( $created ) {
				echo '<span>' . esc_html( wc_format_datetime( $created ) ) . '</span>';
			}
			echo '<span>' . wp_kses_post( $order->get_formatted_order_total() ) . '</span>';
			echo '</div>';
			$actions = wc_get_account_orders_actions( $order );
			if ( $actions ) {
				echo '<div class="pd-acc-order-actions">';
				foreach ( $actions as $key => $action ) {
					echo '<a class="button pd-acc-btn pd-acc-btn--' . esc_attr( sanitize_html_class( $key ) ) . '" href="' . esc_url( $action['url'] ) . '">' . esc_html( $action['name'] ) . '</a>';
				}
				echo '</div>';
			}
			echo '</li>';
		}
		echo '</ul>';
		echo '<a class="pd-acc-all" href="' . esc_url( $all ) . '">مشاهده همه سفارش‌ها</a>';
	}
	echo '</section>';

	echo '<div class="pd-acc-tiles">';
	foreach ( array(
		'edit-account'    => array( 'جزئیات حساب', 'نام، ایمیل و رمز عبور' ),
		'edit-address'    => array( 'نشانی', 'نشانی صورتحساب' ),
		'customer-logout' => array( 'خروج', 'خروج از حساب کاربری' ),
	) as $endpoint => $labels ) {
		$url = 'customer-logout' === $endpoint ? wc_logout_url() : wc_get_account_endpoint_url( $endpoint );
		echo '<a class="pd-acc-tile" href="' . esc_url( $url ) . '"><strong>' . esc_html( $labels[0] ) . '</strong><span>' . esc_html( $labels[1] ) . '</span></a>';
	}
	echo '</div>';
	echo '</div>';
} );

// Styles, only on My account.
add_action( 'wp_head', function () {
	if ( ! function_exists( 'is_account_page' ) || ! is_account_page() ) {
		return;
	}
	?>
<style id="pd-account">
body.woocommerce-account{--pd-bg:#faf8f5;--pd-card:#fff;--pd-line:#e2d9cb;--pd-line2:#ede4d8;--pd-sand:#f3e9e1;--pd-ink:#111;--pd-text:#333;--pd-muted:#7c7c7c;--pd-blue:#375efb;background:var(--pd-bg)}
body.woocommerce-account .site-content,body.woocommerce-account .ast-container,body.woocommerce-account #primary,body.woocommerce-account .entry-content{background:transparent}
body.woocommerce-account .woocommerce,body.woocommerce-account .woocommerce *{font-family:Vazirmatn,Tahoma,Arial,sans-serif}
body.woocommerce-account .woocommerce{color:var(--pd-text)}
body.woocommerce-account .woocommerce a{color:var(--pd-ink)}
body.woocommerce-account .entry-title{display:none}

/* Menu */
.woocommerce-account .woocommerce-MyAccount-navigation ul{list-style:none;margin:0;padding:8px;background:var(--pd-card);border:1px solid var(--pd-line);border-radius:4px}
.woocommerce-account .woocommerce-MyAccount-navigation li{margin:0;border:0!important}
.woocommerce-account .woocommerce-MyAccount-navigation li a{display:block;padding:12px 14px;border-radius:3px;color:var(--pd-text)!important;font-size:15px;text-decoration:none;transition:background .15s}
.woocommerce-account .woocommerce-MyAccount-navigation li a:hover{background:var(--pd-sand)}
.woocommerce-account .woocommerce-MyAccount-navigation li.is-active a{background:var(--pd-ink);color:#fff!important;font-weight:600}
.woocommerce-account .woocommerce-MyAccount-navigation-link--orders a{font-weight:700;background:var(--pd-sand)}
.woocommerce-account .woocommerce-MyAccount-navigation-link--orders.is-active a{background:var(--pd-ink)}
.woocommerce-account .woocommerce-MyAccount-navigation-link--customer-logout a{color:var(--pd-muted)!important;font-size:14px}

/* Content */
.woocommerce-account .woocommerce-MyAccount-content{background:var(--pd-card);border:1px solid var(--pd-line);border-radius:4px;padding:24px}
.woocommerce-account .woocommerce-MyAccount-content:has(.pd-acc-dash){background:transparent;border:0;padding:0}
.woocommerce-account .woocommerce-MyAccount-content:has(.pd-acc-dash)>p{display:none}
.woocommerce-account .woocommerce-MyAccount-content h2,.woocommerce-account .woocommerce-MyAccount-content h3{color:#000;font-weight:700}

/* Buttons */
.woocommerce-account .woocommerce a.button,.woocommerce-account .woocommerce button.button,.woocommerce-account .woocommerce input.button{background:var(--pd-ink)!important;color:#fff!important;border:1px solid var(--pd-ink)!important;border-radius:3px!important;padding:10px 16px!important;font-weight:500;font-size:14px;line-height:1.4}
.woocommerce-account .woocommerce a.button:hover,.woocommerce-account .woocommerce button.button:hover{background:#333!important;border-color:#333!important}
.woocommerce-account .woocommerce a.button.view,.woocommerce-account .woocommerce a.pd-acc-btn--view{background:transparent!important;color:var(--pd-ink)!important}
.woocommerce-account .woocommerce a.button.view:hover,.woocommerce-account .woocommerce a.pd-acc-btn--view:hover{background:var(--pd-sand)!important}

/* Forms */
.woocommerce-account .woocommerce input.input-text,.woocommerce-account .woocommerce select,.woocommerce-account .woocommerce textarea{background:#fff;border:1px solid var(--pd-line);border-radius:3px;padding:10px 12px}
.woocommerce-account .woocommerce input.input-text:focus,.woocommerce-account .woocommerce select:focus{border-color:var(--pd-ink);outline:0}
.woocommerce-account .woocommerce fieldset{border:1px solid var(--pd-line2);border-radius:4px;padding:16px}

/* Orders table */
.woocommerce-account table.shop_table{border:1px solid var(--pd-line);border-radius:4px;border-collapse:separate;overflow:hidden;background:#fff}
.woocommerce-account table.shop_table thead th{background:var(--pd-sand);color:#000;font-weight:600;border:0}
.woocommerce-account table.shop_table td,.woocommerce-account table.shop_table th{border-color:var(--pd-line2)!important;padding:14px 12px;vertical-align:middle}
.woocommerce-account .woocommerce-orders-table__cell-order-actions a.button{margin:2px 0 2px 6px}
.woocommerce-account .woocommerce-message,.woocommerce-account .woocommerce-info{background:var(--pd-sand);border-top-color:var(--pd-ink);color:var(--pd-text)}
.woocommerce-account .woocommerce-message::before,.woocommerce-account .woocommerce-info::before{color:var(--pd-ink)}

/* Dashboard */
.pd-acc-hello{font-size:26px;margin:0 0 4px}
.pd-acc-sub{color:var(--pd-muted);margin:0 0 20px;font-size:14.5px}
.pd-acc-card{background:var(--pd-card);border:1px solid var(--pd-line);border-radius:4px;padding:20px;margin-bottom:16px}
.pd-acc-card-head{display:flex;justify-content:space-between;align-items:center;gap:12px;margin-bottom:12px}
.pd-acc-card-head h3{margin:0;font-size:19px}
.pd-acc-link{color:var(--pd-blue)!important;font-size:14px;text-decoration:none}
.pd-acc-list{list-style:none;margin:0;padding:0}
.pd-acc-order{padding:14px 0;border-top:1px solid var(--pd-line2)}
.pd-acc-order:first-child{border-top:0;padding-top:4px}
.pd-acc-order-main{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.pd-acc-order-no{font-weight:700;font-size:16px;text-decoration:none}
.pd-acc-order-meta{display:flex;gap:16px;color:var(--pd-muted);font-size:13.5px;margin:6px 0 10px;flex-wrap:wrap}
.pd-acc-order-actions{display:flex;gap:8px;flex-wrap:wrap}
.pd-acc-status{display:inline-block;padding:3px 10px;border-radius:20px;font-size:12.5px;background:var(--pd-sand);color:#5b4a3a}
.pd-acc-status--completed,.pd-acc-status--processing{background:#e7f0e6;color:#2f5d2a}
.pd-acc-status--on-hold,.pd-acc-status--pending{background:#fff3df;color:#7a4b00}
.pd-acc-status--cancelled,.pd-acc-status--failed,.pd-acc-status--refunded{background:#f8e6e6;color:#8a1f1f}
.pd-acc-all{display:block;margin-top:14px;padding:14px;text-align:center;background:var(--pd-ink);color:#fff!important;border-radius:3px;text-decoration:none;font-weight:500}
.pd-acc-all:hover{background:#333}
.pd-acc-empty{color:var(--pd-muted);margin:0}
.pd-acc-tiles{display:grid;grid-template-columns:repeat(3,1fr);gap:12px}
.pd-acc-tile{display:block;background:var(--pd-card);border:1px solid var(--pd-line);border-radius:4px;padding:16px;text-decoration:none;transition:border-color .15s}
.pd-acc-tile:hover{border-color:var(--pd-ink)}
.pd-acc-tile strong{display:block;color:#000;margin-bottom:4px}
.pd-acc-tile span{color:var(--pd-muted);font-size:13px}

/* Phones: menu becomes a row of tabs above the content */
@media (max-width:768px){
.woocommerce-account .woocommerce-MyAccount-navigation,.woocommerce-account .woocommerce-MyAccount-content{float:none;width:100%}
.woocommerce-account .woocommerce-MyAccount-navigation{margin-bottom:16px}
.woocommerce-account .woocommerce-MyAccount-navigation ul{display:flex;gap:6px;overflow-x:auto;padding:6px;-webkit-overflow-scrolling:touch;scrollbar-width:none}
.woocommerce-account .woocommerce-MyAccount-navigation ul::-webkit-scrollbar{display:none}
.woocommerce-account .woocommerce-MyAccount-navigation li{flex:0 0 auto}
.woocommerce-account .woocommerce-MyAccount-navigation li a{padding:10px 14px;white-space:nowrap;font-size:14px}
.woocommerce-account .woocommerce-MyAccount-content{padding:16px}
.pd-acc-tiles{grid-template-columns:1fr}
.pd-acc-hello{font-size:22px}
}
</style>
	<?php
} );
