package com.pantea.receiptgate.ocr;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordScorerTest {

    private final KeywordScorer scorer = new KeywordScorer();

    @Test
    void typicalReceiptScoresWellAboveTheBar() {
        // Wording of a Pasargad Paya receipt, as Tesseract tends to return it (split words, stray spaces).
        String text = """
                رسید انتقال وجه پایا
                سپرده مبدا: 289.8000.1
                شبای مقصد: IR36...
                گیرنده: ...
                مبلغ انتقال: 270,000,000 ریال
                شماره پیگیری: 1405...
                بابت: خرید کالا
                """;
        KeywordScorer.Result r = scorer.score(text);
        assertThat(r.score()).isGreaterThanOrEqualTo(15);
        assertThat(r.found()).contains("پیگیری", "رسید", "گیرنده", "سپرده مبدا");
    }

    @Test
    void bankHomeScreenStaysBelowTheBar() {
        // The hardest negative in the bench: banking words, but no tracking number and no receipt phrases.
        String text = "موجودی حساب ۳۳۲٬۸۲۸ ریال  انتقال وجه  کارت به کارت  پرداخت قبض  پایا / ساتنا  بیمه";
        assertThat(scorer.score(text).score()).isLessThan(5);
    }

    @Test
    void chatTextScoresZero() {
        assertThat(scorer.score("سلام وقت بخیر، کاتالوگ سبک معاصر رو دیدم خیلی قشنگ بود").score()).isZero();
    }

    @Test
    void arabicCodePointsZwnjAndBrokenSpacingStillMatch() {
        // Arabic Yeh (ي) in "پيگيري", ZWNJ in "انتقال‌دهنده", hamza form in "مبدأ", and OCR-inserted spaces.
        String text = "شماره پيگيري   انتقال‌دهنده  سپرده  مبدأ  شب ا مقصد";
        KeywordScorer.Result r = scorer.score(text);
        assertThat(r.found()).contains("پیگیری", "انتقال دهنده", "سپرده مبدا", "شبا مقصد");
    }

    @Test
    void phraseAlsoCountsItsParts() {
        // "شبا مقصد" = 2 for the phrase + 1 for "شبا" + 1 for "مقصد": how the thresholds were measured.
        assertThat(scorer.score("شبا مقصد").score()).isEqualTo(4);
    }

    @Test
    void nullAndEmptyAreZero() {
        assertThat(scorer.score(null).score()).isZero();
        assertThat(scorer.score("").score()).isZero();
    }
}
