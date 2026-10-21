package com.pantea.receiptgate.ocr;

import com.pantea.receiptgate.config.GateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs the {@code tesseract} command line in a child process.
 *
 * <p>Why a process and not Tess4J/JNA: a native crash or hang then costs one OCR call instead of the whole
 * JVM, there is no native-library version matching to get wrong, and it adds no Java dependency. The
 * ~30 ms process start is noise next to the OCR itself (1-3 s per image on CPU).
 *
 * <p>The service only ever looks for <em>words</em> in the output. The text is never logged or returned,
 * because receipts carry names, national ID numbers and phone numbers.
 */
@Component
public class TesseractCli {

    private static final Logger log = LoggerFactory.getLogger(TesseractCli.class);

    private final GateProperties.Ocr props;
    private final Semaphore slots;
    /** The executable actually used: the configured one, or a standard install location found on Windows. */
    private final String command;
    private final String version;

    public TesseractCli(GateProperties properties) {
        this.props = properties.getOcr();
        this.slots = new Semaphore(Math.max(1, props.getMaxConcurrent()));

        String found = null;
        String foundVersion = null;
        List<String> tried = candidates(props.getCommand());
        for (String candidate : tried) {
            foundVersion = probeVersion(candidate);
            if (foundVersion != null) {
                found = candidate;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException("Tesseract not found. Tried: " + String.join(", ", tried) + ". "
                    + "Install it - Windows: `winget install UB-Mannheim.TesseractOCR` (or the installer from "
                    + "https://github.com/UB-Mannheim/tesseract/wiki); Debian/Ubuntu: `apt install tesseract-ocr` - "
                    + "or point gate.ocr.command at the executable.");
        }
        this.command = found;
        this.version = foundVersion;
        checkLanguageData();
        log.info("Tesseract ready: {} at {} (language={}, psm={}, tessdata={})", version, command,
                props.getLanguage(), props.getPageSegMode(),
                props.getTessdataDir().isBlank() ? "<default>" : tessdataDir());
    }

    /**
     * The configured command first. If that is the bare default "tesseract" on Windows, also the standard
     * install folders: the UB Mannheim installer does not add itself to PATH, and an IDE started before an
     * install never sees a PATH change anyway.
     */
    static List<String> candidates(String configured) {
        List<String> out = new ArrayList<>(List.of(configured));
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        if (windows && "tesseract".equals(configured)) {
            for (String base : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)"),
                    System.getenv("LOCALAPPDATA") == null ? null : System.getenv("LOCALAPPDATA") + "\\Programs",
                    "C:\\Program Files"}) {
                if (base != null) {
                    String exe = base + "\\Tesseract-OCR\\tesseract.exe";
                    if (!out.contains(exe)) {
                        out.add(exe);
                    }
                }
            }
        }
        return out;
    }

    public String getVersion() {
        return version;
    }

    public String getLanguage() {
        return props.getLanguage();
    }

    /** OCR one image. Throws IOException on timeout, non-zero exit or a missing executable. */
    public String read(BufferedImage image) throws IOException, InterruptedException {
        slots.acquire();
        Path in = null;
        Path out = null;
        Path err = null;
        try {
            in = Files.createTempFile("receipt-ocr-", ".png");
            out = Files.createTempFile("receipt-ocr-", ".txt");
            err = Files.createTempFile("receipt-ocr-", ".err");
            if (!ImageIO.write(image, "png", in.toFile())) {
                throw new IOException("Could not encode image for OCR");
            }

            List<String> cmd = new ArrayList<>(List.of(command, in.toString(), "stdout"));
            if (!props.getTessdataDir().isBlank()) {
                cmd.addAll(List.of("--tessdata-dir", tessdataDir().toString()));
            }
            cmd.addAll(List.of("-l", props.getLanguage(), "--psm", Integer.toString(props.getPageSegMode())));

            ProcessBuilder pb = new ProcessBuilder(cmd)
                    .redirectOutput(out.toFile())
                    .redirectError(err.toFile());
            // Tesseract's OpenMP threads fight each other when several run at once; one thread each is faster.
            pb.environment().put("OMP_THREAD_LIMIT", "1");

            Process p = pb.start();
            if (!p.waitFor(props.getTimeoutSeconds(), TimeUnit.SECONDS)) {
                p.destroyForcibly();
                throw new IOException("Tesseract timed out after " + props.getTimeoutSeconds() + " s");
            }
            if (p.exitValue() != 0) {
                throw new IOException("Tesseract exited with " + p.exitValue() + ": " + head(Files.readString(err)));
            }
            return Files.readString(out, StandardCharsets.UTF_8);
        } finally {
            slots.release();
            deleteQuietly(in);
            deleteQuietly(out);
            deleteQuietly(err);
        }
    }

    private Path tessdataDir() {
        return Path.of(props.getTessdataDir()).toAbsolutePath().normalize();
    }

    /** Version line, or null when there is no such executable. A present-but-broken install fails loudly. */
    private static String probeVersion(String candidate) {
        try {
            Process p = new ProcessBuilder(candidate, "--version").redirectErrorStream(true).start();
            String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!p.waitFor(15, TimeUnit.SECONDS) || p.exitValue() != 0) {
                throw new IllegalStateException("`" + candidate + " --version` failed: " + head(text));
            }
            return text.lines().findFirst().orElse("tesseract").trim();
        } catch (IOException e) {
            return null; // not installed at this location
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while checking Tesseract", e);
        }
    }

    private void checkLanguageData() {
        if (props.getTessdataDir().isBlank()) {
            return; // Tesseract's default folder; a missing language fails loudly on the first call
        }
        Path data = tessdataDir().resolve(props.getLanguage() + ".traineddata");
        if (!Files.isRegularFile(data)) {
            throw new IllegalStateException("Tesseract language data not found at " + data
                    + ". Run scripts/download-models.ps1 (Windows) or scripts/download-models.sh first.");
        }
    }

    private static String head(String s) {
        String t = s == null ? "" : s.strip();
        return t.length() > 300 ? t.substring(0, 300) + "..." : t;
    }

    private static void deleteQuietly(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException ignored) {
            // temp file; the OS cleans its temp folder eventually
        }
    }
}
