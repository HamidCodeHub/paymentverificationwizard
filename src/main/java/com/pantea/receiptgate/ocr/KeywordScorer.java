package com.pantea.receiptgate.ocr;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Scores OCR text by the receipt vocabulary it contains. Words only - Tesseract reads Persian words
 * reliably but garbles Persian digits, so no number is ever trusted.
 *
 * <p>Weights:
 * <ul>
 *   <li><b>3</b> - the tracking/reference-number label. Every transfer receipt has one; almost nothing else does.</li>
 *   <li><b>2</b> - phrases that only a transfer receipt uses together (destination IBAN, source account, ...).</li>
 *   <li><b>1</b> - banking words that also appear on a bank app's home screen or a shop's checkout page.</li>
 * </ul>
 * Measured: every one of 16 real receipts scored 5-22; the hardest non-receipts (bank home screen,
 * transaction history, the shop's own order page with an IBAN, a pre-invoice) scored 1-4.
 *
 * <p>Matching is done after normalisation and with all whitespace removed, because Tesseract splits and
 * joins Persian words unpredictably. A phrase therefore also counts its parts: "شبا مقصد" adds 2 for the
 * phrase plus 1 each for "شبا" and "مقصد" - intended, and how the thresholds were measured.
 */
@Component
public class KeywordScorer {

    public record Result(int score, List<String> found) {
    }

    private static final Map<String, Integer> PHRASES = new LinkedHashMap<>();

    static {
        for (String k : List.of("پیگیری", "رهگیری", "شماره مرجع", "کد مرجع")) {
            PHRASES.put(k, 3);
        }
        for (String k : List.of("شبا مقصد", "حساب مقصد", "سپرده مبدا", "حساب مبدا", "واریز به", "گیرنده",
                "فرستنده", "انتقال دهنده", "رسید", "وضعیت انتقال", "وضعیت تراکنش", "نوع تراکنش", "روش انتقال",
                "تاریخ تراکنش", "زمان تراکنش", "دستور پرداخت")) {
            PHRASES.put(k, 2);
        }
        for (String k : List.of("مبلغ", "ریال", "شبا", "پایا", "ساتنا", "تراکنش", "انتقال", "واریز", "بابت",
                "مقصد", "مبدا", "موفق", "انجام")) {
            PHRASES.put(k, 1);
        }
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    public Result score(String text) {
        String haystack = normalize(text);
        int score = 0;
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, Integer> e : PHRASES.entrySet()) {
            if (haystack.contains(normalize(e.getKey()))) {
                score += e.getValue();
                found.add(e.getKey());
            }
        }
        return new Result(score, List.copyOf(found));
    }

    /**
     * Arabic Yeh/Kaf to Persian, hamza forms of alef to alef, drop the hamza mark and ZWNJ, drop whitespace.
     * Banks mix Arabic and Persian code points ("مرتضي", "مبدأ"), and OCR picks either.
     */
    static String normalize(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('ي', 'ی')   // ي -> ی
                .replace('ى', 'ی')       // ى -> ی
                .replace('ك', 'ک')       // ك -> ک
                .replace('أ', 'ا')       // أ -> ا
                .replace('إ', 'ا')       // إ -> ا
                .replace("ٔ", "")              // combining hamza above
                .replace("‌", "")              // ZWNJ
                .replace("‏", "")              // RLM
                .replace("‎", "");             // LRM
        return WHITESPACE.matcher(t).replaceAll("");
    }
}
