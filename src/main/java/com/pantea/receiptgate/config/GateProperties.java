package com.pantea.receiptgate.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything tunable lives here, grouped the same way as {@code application.yml}.
 * The defaults are the values measured on the 16 sample receipts and 39 non-receipts (see README).
 */
@ConfigurationProperties(prefix = "gate")
public class GateProperties {

    /**
     * Folder with two sub-folders, {@code receipts/} and {@code non-receipts/}. Images may sit directly in
     * them or one level down ({@code receipts/blu/x.jpg}); that first sub-folder is the image's "group",
     * which {@code --verify} holds out as a whole (a bank layout, a kind of screenshot).
     */
    private String referencesDir = "references";

    /** Shared secret required in the X-Api-Key header. Empty = no auth (local dev only). */
    private String apiKey = "";

    /** Per-IP throttle on the check endpoint. 0 disables it. */
    private int rateLimitPerMinute = 20;

    /** Comma-separated origins allowed to call the API from a browser. Prefer the WordPress proxy. */
    private String allowedOrigins = "";

    private final Clip clip = new Clip();
    private final Probe probe = new Probe();
    private final Ocr ocr = new Ocr();
    private final Decision decision = new Decision();

    public String getReferencesDir() { return referencesDir; }
    public void setReferencesDir(String referencesDir) { this.referencesDir = referencesDir; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public int getRateLimitPerMinute() { return rateLimitPerMinute; }
    public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }

    public String getAllowedOrigins() { return allowedOrigins; }
    public void setAllowedOrigins(String allowedOrigins) { this.allowedOrigins = allowedOrigins; }

    public Clip getClip() { return clip; }
    public Probe getProbe() { return probe; }
    public Ocr getOcr() { return ocr; }
    public Decision getDecision() { return decision; }

    public static class Clip {
        /** The same ONNX vision encoder the style-match service uses. */
        private String modelPath = "models/clip-vit-base-patch32-vision.onnx";
        /** Reference embeddings cached here, keyed by model fingerprint + file size + mtime. */
        private String indexCache = "models/reference-index.json";
        private int inputSize = 224;
        private int intraOpThreads = 2;
        /** Average a centre crop and a full-frame squash. Receipts are tall; one centre crop loses most of it. */
        private boolean multiCrop = true;
        /**
         * Also embed 5 degraded copies of every reference receipt (crops, rotation, blur + low JPEG quality,
         * tiny) so the probe learns that a bad photo of a receipt is still a receipt.
         */
        private boolean augmentReceipts = true;

        public String getModelPath() { return modelPath; }
        public void setModelPath(String modelPath) { this.modelPath = modelPath; }
        public String getIndexCache() { return indexCache; }
        public void setIndexCache(String indexCache) { this.indexCache = indexCache; }
        public int getInputSize() { return inputSize; }
        public void setInputSize(int inputSize) { this.inputSize = inputSize; }
        public int getIntraOpThreads() { return intraOpThreads; }
        public void setIntraOpThreads(int intraOpThreads) { this.intraOpThreads = intraOpThreads; }
        public boolean isMultiCrop() { return multiCrop; }
        public void setMultiCrop(boolean multiCrop) { this.multiCrop = multiCrop; }
        public boolean isAugmentReceipts() { return augmentReceipts; }
        public void setAugmentReceipts(boolean augmentReceipts) { this.augmentReceipts = augmentReceipts; }
    }

    /** Logistic regression trained on the reference vectors at startup and on every reindex. */
    public static class Probe {
        /** L2 penalty. Measured: 5e-2 is too timid (every score ~0.5), 5e-4 separates. */
        private double l2 = 5e-4;
        private int iterations = 6000;
        private double learningRate = 0.5;

        public double getL2() { return l2; }
        public void setL2(double l2) { this.l2 = l2; }
        public int getIterations() { return iterations; }
        public void setIterations(int iterations) { this.iterations = iterations; }
        public double getLearningRate() { return learningRate; }
        public void setLearningRate(double learningRate) { this.learningRate = learningRate; }
    }

    public static class Ocr {
        /** The tesseract executable. On Windows usually C:/Program Files/Tesseract-OCR/tesseract.exe. */
        private String command = "tesseract";
        /** Folder holding {@code fas.traineddata} (tessdata_best). Empty = Tesseract's own default folder. */
        private String tessdataDir = "models/tessdata";
        private String language = "fas";
        /** 6 = "a single uniform block of text"; measured best on these receipts. */
        private int pageSegMode = 6;
        private int timeoutSeconds = 30;
        /** Tesseract processes allowed at once. Each one is single-threaded (OMP_THREAD_LIMIT=1). */
        private int maxConcurrent = 2;
        /** Quarter turns tried when the upright pass finds too few words (sideways photos). */
        private List<Integer> retryRotations = new ArrayList<>(List.of(90, 270));

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
        public String getTessdataDir() { return tessdataDir; }
        public void setTessdataDir(String tessdataDir) { this.tessdataDir = tessdataDir; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public int getPageSegMode() { return pageSegMode; }
        public void setPageSegMode(int pageSegMode) { this.pageSegMode = pageSegMode; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        public List<Integer> getRetryRotations() { return retryRotations; }
        public void setRetryRotations(List<Integer> retryRotations) { this.retryRotations = retryRotations; }
    }

    public static class Decision {
        /** Keyword score at or above which the image is a receipt. Hard negatives topped out at 4. */
        private int minKeywordScore = 5;
        /** Below the keyword bar, a CLIP probability at or above this makes the image UNSURE instead of blocked. */
        private double unsureProbability = 0.5;
        /**
         * Rotated OCR retries are skipped when CLIP is this sure the image is not a receipt (room photos score
         * ~0.02-0.06, the worst degraded receipt 0.17). Saves two Tesseract runs on every holiday photo.
         */
        private double rotationRetryMinProbability = 0.10;
        /** Decompression-bomb guard, checked from the header before decoding. */
        private long maxPixels = 40_000_000L;
        /** Anything smaller than this on its short side cannot hold readable text. */
        private int minSide = 64;

        public int getMinKeywordScore() { return minKeywordScore; }
        public void setMinKeywordScore(int minKeywordScore) { this.minKeywordScore = minKeywordScore; }
        public double getUnsureProbability() { return unsureProbability; }
        public void setUnsureProbability(double unsureProbability) { this.unsureProbability = unsureProbability; }
        public double getRotationRetryMinProbability() { return rotationRetryMinProbability; }
        public void setRotationRetryMinProbability(double v) { this.rotationRetryMinProbability = v; }
        public long getMaxPixels() { return maxPixels; }
        public void setMaxPixels(long maxPixels) { this.maxPixels = maxPixels; }
        public int getMinSide() { return minSide; }
        public void setMinSide(int minSide) { this.minSide = minSide; }
    }
}
