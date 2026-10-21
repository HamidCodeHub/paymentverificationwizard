package com.pantea.receiptgate.gate;

import com.pantea.receiptgate.clip.ClipImageEncoder;
import com.pantea.receiptgate.config.GateProperties;
import com.pantea.receiptgate.gate.GateResult.Reason;
import com.pantea.receiptgate.gate.GateResult.Verdict;
import com.pantea.receiptgate.index.ReferenceIndex;
import com.pantea.receiptgate.ocr.KeywordScorer;
import com.pantea.receiptgate.ocr.TesseractCli;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The decision rule, with CLIP and Tesseract replaced by stubs. */
class ReceiptGateServiceTest {

    private static final String RECEIPT_TEXT = "شماره پیگیری  شبا مقصد  مبلغ  ریال";   // 3+2+1+1+1+1 = 9
    private static final String CHAT_TEXT = "سلام وقت بخیر";

    private final GateProperties props = new GateProperties();
    private ClipImageEncoder encoder;
    private ReferenceIndex index;
    private TesseractCli tesseract;
    private ReceiptGateService gate;

    @BeforeEach
    void setUp() throws Exception {
        encoder = mock(ClipImageEncoder.class);
        index = mock(ReferenceIndex.class);
        tesseract = mock(TesseractCli.class);
        when(encoder.embed(any(BufferedImage.class))).thenReturn(new float[]{1f});
        gate = new ReceiptGateService(props, encoder, index, tesseract, new KeywordScorer());
    }

    private static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void receiptWordsUprightMeanReceiptWithASingleOcrPass() throws Exception {
        when(index.probability(any())).thenReturn(0.9);
        when(tesseract.read(any())).thenReturn(RECEIPT_TEXT);

        GateResult r = gate.check(png(300, 600));

        assertThat(r.verdict()).isEqualTo(Verdict.RECEIPT);
        assertThat(r.reason()).isEqualTo(Reason.RECEIPT_TEXT_FOUND);
        assertThat(r.keywordScore()).isEqualTo(9);
        assertThat(r.ocrPasses()).isEqualTo(1);
        assertThat(r.sha256()).hasSize(64);
    }

    @Test
    void sidewaysReceiptIsFoundOnTheRetry() throws Exception {
        when(index.probability(any())).thenReturn(0.7);
        when(tesseract.read(any())).thenReturn(CHAT_TEXT, RECEIPT_TEXT);

        GateResult r = gate.check(png(600, 300));

        assertThat(r.verdict()).isEqualTo(Verdict.RECEIPT);
        assertThat(r.ocrRotation()).isEqualTo(90);
        assertThat(r.ocrPasses()).isEqualTo(2);
    }

    @Test
    void unreadableButReceiptLookingIsUnsure() throws Exception {
        when(index.probability(any())).thenReturn(0.62);
        when(tesseract.read(any())).thenReturn("");

        GateResult r = gate.check(png(300, 600));

        assertThat(r.verdict()).isEqualTo(Verdict.UNSURE);
        assertThat(r.reason()).isEqualTo(Reason.RECEIPT_LIKE_BUT_TEXT_UNCLEAR);
        assertThat(r.ocrPasses()).isEqualTo(3);
    }

    @Test
    void roomPhotoIsBlockedWithoutSidewaysRetries() throws Exception {
        when(index.probability(any())).thenReturn(0.03);
        when(tesseract.read(any())).thenReturn("");

        GateResult r = gate.check(png(600, 400));

        assertThat(r.verdict()).isEqualTo(Verdict.NOT_RECEIPT);
        assertThat(r.reason()).isEqualTo(Reason.NOT_A_RECEIPT);
        verify(tesseract, times(1)).read(any());
    }

    @Test
    void ocrOutageNeverBlocksAReceiptLookingImage() throws Exception {
        when(index.probability(any())).thenReturn(0.3);
        when(tesseract.read(any())).thenThrow(new IOException("Tesseract timed out"));

        GateResult r = gate.check(png(300, 600));

        assertThat(r.verdict()).isEqualTo(Verdict.UNSURE);
        assertThat(r.reason()).isEqualTo(Reason.OCR_UNAVAILABLE);
    }

    @Test
    void ocrOutageStillBlocksAnObviousPhoto() throws Exception {
        when(index.probability(any())).thenReturn(0.02);
        when(tesseract.read(any())).thenThrow(new IOException("boom"));

        assertThat(gate.check(png(600, 400)).verdict()).isEqualTo(Verdict.NOT_RECEIPT);
    }

    @Test
    void withoutATrainedProbeWordsAloneDecide() throws Exception {
        when(index.probability(any())).thenReturn(Double.NaN);
        when(tesseract.read(any())).thenReturn(CHAT_TEXT);

        GateResult r = gate.check(png(300, 600));

        assertThat(r.verdict()).isEqualTo(Verdict.NOT_RECEIPT);
        assertThat(r.clipProbability()).isNull();
    }

    @Test
    void tinyImageIsRejectedBeforeAnyModelRuns() throws Exception {
        GateResult r = gate.check(png(40, 40));

        assertThat(r.verdict()).isEqualTo(Verdict.NOT_RECEIPT);
        assertThat(r.reason()).isEqualTo(Reason.IMAGE_TOO_SMALL);
        verify(tesseract, times(0)).read(any());
    }
}
