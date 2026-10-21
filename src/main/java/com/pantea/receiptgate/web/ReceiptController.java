package com.pantea.receiptgate.web;

import ai.onnxruntime.OrtException;
import com.pantea.receiptgate.config.GateProperties;
import com.pantea.receiptgate.gate.GateResult;
import com.pantea.receiptgate.gate.ReceiptGateService;
import com.pantea.receiptgate.index.ReferenceIndex;
import com.pantea.receiptgate.index.ReferenceIndex.Label;
import com.pantea.receiptgate.ocr.TesseractCli;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Receipt gate", description = "Is this upload a bank-transfer receipt? Forward, ask again, or block.")
public class ReceiptController {

    /** HEIC (iPhone camera) and WebP cannot be decoded by ImageIO; WordPress should convert them to JPEG first. */
    private static final Set<String> UNSUPPORTED = Set.of("image/webp", "image/heic", "image/heif", "image/avif",
            "application/pdf");

    private final ReceiptGateService gate;
    private final ReferenceIndex index;
    private final TesseractCli tesseract;
    private final GateProperties props;

    public ReceiptController(ReceiptGateService gate, ReferenceIndex index, TesseractCli tesseract,
                             GateProperties props) {
        this.gate = gate;
        this.index = index;
        this.tesseract = tesseract;
        this.props = props;
    }

    @Operation(
            summary = "Check whether an image is a bank-transfer receipt",
            description = """
                    The main endpoint. Send one image as multipart form data.

                    **What it does not do:** read amounts, IBANs or dates, or confirm that money arrived. A
                    receipt can be pending (Paya), cancelled or forged; the admin still checks the bank account.

                    **What WordPress should do with the verdict:**
                    - `RECEIPT` - attach to the order, add an order note, email the admin. Order stays on-hold.
                    - `UNSURE` - ask for a clearer, uncropped screenshot; offer "send anyway", which reaches
                      the admin flagged as unverified.
                    - `NOT_RECEIPT` - block, and say a bank transfer receipt is expected.

                    Typically 1-4 s on CPU: one CLIP pass (~0.2 s) plus 1-3 Tesseract passes. Nothing is stored;
                    the upload lives in memory for the request. Store `sha256` on the order to refuse the same
                    file on a second order.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The verdict"),
            @ApiResponse(responseCode = "400", description = "Missing, empty, unreadable or oversized image",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "Missing or wrong X-Api-Key",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "413", description = "File over the 15 MB limit",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "415", description = "WebP, HEIC, AVIF or PDF - convert to JPEG first",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Per-IP rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping(value = "/receipts/check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public GateResult check(
            @Parameter(description = "The customer's receipt: a JPG or PNG screenshot or photo, up to 15 MB. "
                    + "EXIF rotation is honoured.",
                    required = true,
                    content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE))
            @RequestParam("image") MultipartFile image) throws IOException, OrtException {

        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No image was uploaded");
        }
        String type = image.getContentType() == null ? "" : image.getContentType().toLowerCase(Locale.ROOT);
        if (UNSUPPORTED.contains(type)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Please upload the receipt as a JPG or PNG screenshot");
        }
        return gate.check(image.getBytes());
    }

    @Operation(
            summary = "Re-scan the references folder",
            description = """
                    Call after adding receipts or non-receipts to `references/`. Only new or changed files are
                    embedded (about 1 s per receipt, which also embeds 5 degraded copies); the probe retrains in
                    about a second. Requests keep being served from the previous index meanwhile.

                    Requires the API key. Do not expose it through the WordPress proxy.""",
            security = @SecurityRequirement(name = "ApiKey"))
    @PostMapping("/reindex")
    public Map<String, Object> reindex() throws IOException {
        index.rebuild();
        return references();
    }

    @Operation(
            summary = "Health and index status",
            description = "Public, no API key. `probeTrained: false` means the references folder is missing or "
                    + "one-sided, and only receipt words are being used.",
            security = {})
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "UP");
        out.put("tesseract", tesseract.getVersion());
        out.put("ocrLanguage", tesseract.getLanguage());
        out.putAll(references());
        out.put("minKeywordScore", props.getDecision().getMinKeywordScore());
        out.put("unsureProbability", props.getDecision().getUnsureProbability());
        return out;
    }

    private Map<String, Object> references() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("probeTrained", index.isProbeTrained());
        out.put("referenceReceipts", index.count(Label.RECEIPT, true));
        out.put("degradedReceiptCopies", index.count(Label.RECEIPT, false) - index.count(Label.RECEIPT, true));
        out.put("referenceNonReceipts", index.count(Label.NOT_RECEIPT, true));
        return out;
    }
}
