package com.pantea.receiptgate.gate;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "GateResult", description = """
        Whether an upload is a bank-transfer receipt. This is NOT payment verification: a receipt can be
        pending (Paya), cancelled or forged. RECEIPT means "forward it to a human", nothing more.""")
public record GateResult(

        @Schema(description = """
                RECEIPT: receipt wording found - attach to the order and notify the admin.
                UNSURE: looks like a receipt but the text could not be read (blurry, cropped, tiny) - ask the
                customer for a clearer screenshot, and offer "send anyway" (flagged for the admin).
                NOT_RECEIPT: block, and explain what is expected.""",
                example = "RECEIPT")
        Verdict verdict,

        @Schema(description = "Machine-readable reason, for choosing the message shown to the customer.",
                example = "RECEIPT_TEXT_FOUND")
        Reason reason,

        @Schema(description = "Weighted count of receipt words found by OCR.", example = "17")
        int keywordScore,

        @Schema(description = "Score needed for RECEIPT (gate.decision.min-keyword-score).", example = "5")
        int minKeywordScore,

        @Schema(description = "The receipt words that were found. The OCR text itself is never returned or logged.",
                example = "[\"پیگیری\", \"شبا مقصد\", \"مبلغ\", \"ریال\"]")
        List<String> keywordsFound,

        @Schema(description = "CLIP probe's probability that the image looks like a receipt (0..1). "
                + "Null when no probe is trained (references folder empty).", example = "0.87", nullable = true)
        Double clipProbability,

        @Schema(description = "Probability at or above which a receipt without readable words is UNSURE "
                + "rather than blocked (gate.decision.unsure-probability).", example = "0.5")
        double unsureProbability,

        @Schema(description = "Rotation (clockwise degrees) at which the words were read; 90/270 = sideways photo.",
                example = "0")
        int ocrRotation,

        @Schema(description = "Tesseract passes run: 1 upright, up to 3 with the sideways retries.", example = "1")
        int ocrPasses,

        @Schema(description = "SHA-256 of the uploaded bytes. Store it on the order to refuse the same file on "
                + "another order.", example = "3f1a…")
        String sha256,

        @Schema(example = "540") int width,
        @Schema(example = "1170") int height,
        @Schema(example = "1840") long elapsedMs
) {

    public enum Verdict { RECEIPT, UNSURE, NOT_RECEIPT }

    public enum Reason {
        /** Enough receipt words were read. */
        RECEIPT_TEXT_FOUND,
        /** Few or no receipt words, but the image looks like a receipt (bad photo, crop, low resolution). */
        RECEIPT_LIKE_BUT_TEXT_UNCLEAR,
        /** Neither the words nor the look of a receipt. */
        NOT_A_RECEIPT,
        /** Too small to hold readable text. */
        IMAGE_TOO_SMALL,
        /** Tesseract failed or timed out; decided on CLIP alone, leaning towards a human look. */
        OCR_UNAVAILABLE
    }
}
