<?php
/**
 * Pantea – receipt check before upload (demo)
 *
 * Adds an automatic check to the upload form of [pd_iran_payment] on /iran-payment/ (snippet
 * "Iranian bank transfer – receipt upload", which is left unchanged). Before the form is sent, the image goes
 * to the receipt service (receipts.panteadesignstudio.com) through admin-ajax, so the API key never reaches
 * the browser:
 *   RECEIPT      -> the original form is submitted as before (on-hold order, Pantea checks and completes it)
 *   NOT_RECEIPT  -> "this is not a payment receipt, upload the correct one"
 *   UNSURE       -> "not clear, upload a better one" — after 3 unclear uploads the customer is asked to e-mail
 *                   the receipt to Pantea instead
 *   service down -> the original form is submitted as before, so a real customer is never blocked
 *
 * Settings → General → "Receipt check": API key (required), API address, e-mail for unclear receipts.
 * Deactivating this snippet restores the plain upload form.
 */

if ( ! defined( 'PD_RC_MAX_UNSURE' ) ) define( 'PD_RC_MAX_UNSURE', 3 );
// Unclear uploads are counted per customer for this long, then the customer may try again.
if ( ! defined( 'PD_RC_UNSURE_TTL' ) ) define( 'PD_RC_UNSURE_TTL', HOUR_IN_SECONDS );
if ( ! defined( 'PD_RC_DEFAULT_API' ) ) define( 'PD_RC_DEFAULT_API', 'https://receipts.panteadesignstudio.com' );

function pd_rc_api_base() {
	$base = trim( (string) get_option( 'pd_rc_api_base', '' ) );
	return untrailingslashit( $base !== '' ? $base : PD_RC_DEFAULT_API );
}

function pd_rc_review_email() {
	$email = sanitize_email( (string) get_option( 'pd_rc_review_email', '' ) );
	return $email !== '' ? $email : get_option( 'admin_email' );
}

function pd_rc_unsure_key( $user_id ) {
	return 'pd_rc_unsure_' . (int) $user_id;
}

function pd_rc_unsure_count( $user_id ) {
	return (int) get_transient( pd_rc_unsure_key( $user_id ) );
}

// ---------------------------------------------------------------------------------------------------------
// Settings → General → Receipt check
// ---------------------------------------------------------------------------------------------------------
add_action( 'admin_init', function () {
	register_setting( 'general', 'pd_rc_api_key', array( 'type' => 'string', 'sanitize_callback' => 'sanitize_text_field' ) );
	register_setting( 'general', 'pd_rc_api_base', array( 'type' => 'string', 'sanitize_callback' => 'esc_url_raw' ) );
	register_setting( 'general', 'pd_rc_review_email', array( 'type' => 'string', 'sanitize_callback' => 'sanitize_email' ) );

	add_settings_section( 'pd_rc', 'Receipt check (Iranian bank transfer)', function () {
		echo '<p>Checks uploaded receipts with the receipt service before they are sent. Health: <a href="'
			. esc_url( pd_rc_api_base() . '/api/v1/health' ) . '" target="_blank" rel="noopener">'
			. esc_html( pd_rc_api_base() . '/api/v1/health' ) . '</a></p>';
	}, 'general' );

	add_settings_field( 'pd_rc_api_key', 'API key', function () {
		$set = get_option( 'pd_rc_api_key', '' ) !== '';
		echo '<input type="password" name="pd_rc_api_key" value="' . esc_attr( get_option( 'pd_rc_api_key', '' ) )
			. '" class="regular-text" autocomplete="off" dir="ltr">';
		echo '<p class="description">API_KEY from <code>~/pantea/stack/.env</code> on the receipts server. '
			. ( $set ? 'Set.' : '<strong>Not set — every upload goes straight to manual review.</strong>' ) . '</p>';
	}, 'general', 'pd_rc' );

	add_settings_field( 'pd_rc_api_base', 'API address', function () {
		echo '<input type="url" name="pd_rc_api_base" value="' . esc_attr( get_option( 'pd_rc_api_base', '' ) )
			. '" placeholder="' . esc_attr( PD_RC_DEFAULT_API ) . '" class="regular-text" dir="ltr">';
	}, 'general', 'pd_rc' );

	add_settings_field( 'pd_rc_review_email', 'E-mail for unclear receipts', function () {
		echo '<input type="email" name="pd_rc_review_email" value="' . esc_attr( get_option( 'pd_rc_review_email', '' ) )
			. '" placeholder="' . esc_attr( get_option( 'admin_email' ) ) . '" class="regular-text" dir="ltr">';
		echo '<p class="description">After ' . (int) PD_RC_MAX_UNSURE . ' unclear uploads the customer is asked to send the receipt here.</p>';
	}, 'general', 'pd_rc' );
} );

// ---------------------------------------------------------------------------------------------------------
// AJAX: image in, verdict out. Logged-in customers only (the upload form itself requires login).
// ---------------------------------------------------------------------------------------------------------
add_action( 'wp_ajax_pd_receipt_check', function () {
	if ( ! check_ajax_referer( 'pd_receipt_check', 'nonce', false ) ) {
		wp_send_json( array( 'verdict' => 'ERROR', 'error' => 'nonce' ), 403 );
	}
	$user_id = get_current_user_id();
	$max     = (int) PD_RC_MAX_UNSURE;
	$count   = pd_rc_unsure_count( $user_id );
	$reply   = function ( $verdict, $extra = array() ) use ( $max, &$count ) {
		wp_send_json( array_merge( array(
			'verdict'  => $verdict,
			'attempts' => $count,
			'max'      => $max,
			'escalate' => $count >= $max,
		), $extra ) );
	};

	if ( $count >= $max ) {
		$reply( 'ESCALATED' );
	}

	$key = (string) get_option( 'pd_rc_api_key', '' );
	if ( $key === '' ) {
		$reply( 'ERROR', array( 'error' => 'not_configured' ) );
	}

	if ( empty( $_FILES['image']['tmp_name'] ) || ! is_uploaded_file( $_FILES['image']['tmp_name'] ) ) {
		$reply( 'ERROR', array( 'error' => 'no_file' ) );
	}
	$tmp  = $_FILES['image']['tmp_name'];
	$size = (int) $_FILES['image']['size'];
	$info = @getimagesize( $tmp );
	if ( $size <= 0 || $size > 15 * MB_IN_BYTES || ! $info || ! in_array( $info[2], array( IMAGETYPE_JPEG, IMAGETYPE_PNG ), true ) ) {
		$reply( 'BAD_IMAGE' );
	}
	$mime = $info[2] === IMAGETYPE_PNG ? 'image/png' : 'image/jpeg';
	$name = $info[2] === IMAGETYPE_PNG ? 'receipt.png' : 'receipt.jpg';

	$boundary = 'pd' . wp_generate_password( 24, false );
	$body     = '--' . $boundary . "\r\n"
		. 'Content-Disposition: form-data; name="image"; filename="' . $name . '"' . "\r\n"
		. 'Content-Type: ' . $mime . "\r\n\r\n"
		. file_get_contents( $tmp ) . "\r\n"
		. '--' . $boundary . "--\r\n";

	$res = wp_remote_post( pd_rc_api_base() . '/api/v1/receipts/check', array(
		'timeout' => 60,
		'headers' => array(
			'Content-Type' => 'multipart/form-data; boundary=' . $boundary,
			'X-Api-Key'    => $key,
			'Accept'       => 'application/json',
		),
		'body'    => $body,
	) );

	if ( is_wp_error( $res ) ) {
		$reply( 'ERROR', array( 'error' => 'unreachable' ) );
	}
	$status = (int) wp_remote_retrieve_response_code( $res );
	$data   = json_decode( wp_remote_retrieve_body( $res ), true );

	if ( $status === 415 || $status === 413 || ( $status === 400 && is_array( $data ) ) ) {
		$reply( 'BAD_IMAGE' );
	}
	if ( $status !== 200 || ! is_array( $data ) || empty( $data['verdict'] ) ) {
		$reply( 'ERROR', array( 'error' => 'http_' . $status ) );
	}

	switch ( $data['verdict'] ) {
		case 'RECEIPT':
			delete_transient( pd_rc_unsure_key( $user_id ) );
			$count = 0;
			$reply( 'RECEIPT' );
			break;
		case 'UNSURE':
			$count++;
			set_transient( pd_rc_unsure_key( $user_id ), $count, PD_RC_UNSURE_TTL );
			$reply( 'UNSURE' );
			break;
		default:
			$reply( 'NOT_RECEIPT' );
	}
} );

// ---------------------------------------------------------------------------------------------------------
// Front end: only on the page with the upload form.
// ---------------------------------------------------------------------------------------------------------
add_action( 'wp_footer', function () {
	if ( ! is_user_logged_in() || ! is_page( 'iran-payment' ) ) {
		return;
	}
	$user  = wp_get_current_user();
	$email = pd_rc_review_email();
	$cfg   = array(
		'ajaxUrl'   => admin_url( 'admin-ajax.php' ),
		'nonce'     => wp_create_nonce( 'pd_receipt_check' ),
		'attempts'  => pd_rc_unsure_count( $user->ID ),
		'max'       => (int) PD_RC_MAX_UNSURE,
		'email'     => $email,
		'userEmail' => $user->user_email,
	);
	?>
<style>
.pd-upload .pd-rc-msg{margin:12px 0 0}
.pd-msg.pd-warn{background:#fff6e5;color:#7a4b00;border:1px solid #f3d9a4}
.pd-msg.pd-info{background:#f2f2f2;color:#333}
.pd-rc-spin{display:inline-block;width:14px;height:14px;margin-inline-end:8px;vertical-align:-2px;border:2px solid currentColor;border-right-color:transparent;border-radius:50%;animation:pdrc 0.8s linear infinite}
@keyframes pdrc{to{transform:rotate(360deg)}}
.pd-upload .pd-btn[disabled]{opacity:.6;cursor:wait}
.pd-rc-escalate{margin-top:12px}
.pd-rc-escalate .pd-rc-mail{direction:ltr;font-weight:700;word-break:break-all}
.pd-rc-escalate .pd-btn{display:block;text-align:center;margin-top:12px}
</style>
<script>
(function () {
	var CFG = <?php echo wp_json_encode( $cfg ); ?>;
	var form = document.querySelector('form.pd-upload');
	if (!form) return;
	var input = form.querySelector('input[name="pd_receipt"]');
	var button = form.querySelector('button[name="pd_iran_submit"]');
	if (!input || !button) return;
	var fa = function (n) { return String(n).replace(/\d/g, function (d) { return '۰۱۲۳۴۵۶۷۸۹'[d]; }); };
	var busy = false, approved = false;

	// Use the image types the form accepts, but only those a browser can open (no PDF here).
	input.setAttribute('accept', 'image/*');

	var box = document.createElement('div');
	box.className = 'pd-rc-msg';
	button.parentNode.insertBefore(box, button);

	function show(kind, html) {
		box.innerHTML = html ? '<p class="pd-msg ' + kind + '">' + html + '</p>' : '';
	}

	function escalate() {
		var subject = 'رسید پرداخت – ' + CFG.userEmail;
		var body = 'سلام،\nتصویر رسید واریز من پیوست شده است.\n\nایمیل حساب کاربری: ' + CFG.userEmail
			+ '\nشماره واتس‌اپ: ' + ((form.querySelector('[name="pd_whatsapp"]') || {}).value || '') + '\n';
		var href = 'mailto:' + CFG.email + '?subject=' + encodeURIComponent(subject) + '&body=' + encodeURIComponent(body);
		var div = document.createElement('div');
		div.className = 'pd-rc-escalate';
		div.innerHTML = '<p class="pd-msg pd-warn">رسید شما پس از ' + fa(CFG.max) + ' بار بارگذاری به‌صورت خودکار قابل تأیید نبود.'
			+ ' لطفاً تصویر رسید را به ایمیل زیر بفرستید تا پانته‌آ آن را بررسی کند و از طریق ایمیل یا واتس‌اپ با شما تماس بگیرد.</p>'
			+ '<p class="pd-rc-mail">' + CFG.email + '</p>'
			+ '<a class="pd-btn" href="' + href + '">ارسال رسید از طریق ایمیل</a>';
		form.parentNode.replaceChild(div, form);
	}

	if (CFG.attempts >= CFG.max) { escalate(); return; }

	// Re-encode to JPEG (max 2000 px): WebP/HEIC-from-Safari become something the service reads, and the
	// upload stays small on slow connections. Fails for PDFs and formats the browser cannot open.
	function toJpeg(file) {
		return new Promise(function (resolve, reject) {
			var url = URL.createObjectURL(file);
			var img = new Image();
			img.onload = function () {
				var w = img.naturalWidth, h = img.naturalHeight, s = Math.min(1, 2000 / Math.max(w, h));
				var c = document.createElement('canvas');
				c.width = Math.round(w * s); c.height = Math.round(h * s);
				var g = c.getContext('2d');
				g.fillStyle = '#fff'; g.fillRect(0, 0, c.width, c.height);
				g.drawImage(img, 0, 0, c.width, c.height);
				URL.revokeObjectURL(url);
				c.toBlob(function (b) { b ? resolve(b) : reject(new Error('encode')); }, 'image/jpeg', 0.9);
			};
			img.onerror = function () { URL.revokeObjectURL(url); reject(new Error('decode')); };
			img.src = url;
		});
	}

	function submitOriginal() {
		approved = true;
		var flag = document.createElement('input');
		flag.type = 'hidden'; flag.name = 'pd_iran_submit'; flag.value = '1';
		form.appendChild(flag);
		HTMLFormElement.prototype.submit.call(form);
	}

	function done() { busy = false; button.disabled = false; }

	input.addEventListener('change', function () { show('', ''); });

	form.addEventListener('submit', function (e) {
		if (approved) return;
		e.preventDefault();
		if (busy) return;
		if (!form.reportValidity()) return;
		var file = input.files && input.files[0];
		if (!file) return;

		busy = true; button.disabled = true;
		show('pd-info', '<span class="pd-rc-spin"></span>در حال بررسی تصویر رسید… لطفاً چند لحظه صبر کنید.');

		toJpeg(file).then(function (jpeg) {
			var fd = new FormData();
			fd.append('action', 'pd_receipt_check');
			fd.append('nonce', CFG.nonce);
			fd.append('image', jpeg, 'receipt.jpg');
			return fetch(CFG.ajaxUrl, { method: 'POST', body: fd, credentials: 'same-origin' })
				.then(function (r) { return r.json(); })
				.then(handle, function () { handle({ verdict: 'ERROR' }); });
		}, function () {
			done();
			show('pd-err', 'این فایل قابل بررسی نیست. لطفاً عکس یا اسکرین‌شات رسید را با فرمت JPG یا PNG بارگذاری کنید.');
		});
	});

	function handle(res) {
		CFG.attempts = res.attempts != null ? res.attempts : CFG.attempts;
		switch (res.verdict) {
			case 'RECEIPT':
				show('pd-ok', 'رسید پرداخت تأیید شد. در حال ارسال…');
				submitOriginal();
				return;
			case 'NOT_RECEIPT':
				done();
				show('pd-err', 'تصویر بارگذاری‌شده رسید پرداخت نیست. لطفاً تصویر رسید صحیح واریز را بارگذاری کنید.');
				break;
			case 'UNSURE':
				if (res.escalate) { escalate(); return; }
				done();
				show('pd-warn', 'تصویر رسید به‌اندازه کافی واضح نیست. لطفاً تصویر واضح‌تری بارگذاری کنید'
					+ ' (تلاش ' + fa(res.attempts) + ' از ' + fa(res.max) + ').<br>'
					+ 'بهترین نتیجه: اسکرین‌شات مستقیم از اپلیکیشن بانک، بدون برش و بدون عکس گرفتن از صفحه.');
				break;
			case 'ESCALATED':
				escalate();
				return;
			case 'BAD_IMAGE':
				done();
				show('pd-err', 'این فایل قابل بررسی نیست. لطفاً عکس یا اسکرین‌شات رسید را با فرمت JPG یا PNG بارگذاری کنید.');
				break;
			default:
				// Service unreachable or not configured: never block a paying customer, send for manual review.
				show('pd-info', 'بررسی خودکار در حال حاضر در دسترس نیست؛ رسید شما برای بررسی دستی ارسال می‌شود…');
				submitOriginal();
				return;
		}
		input.value = '';
	}
})();
</script>
	<?php
}, 100 );
