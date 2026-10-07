package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Lädt ein Bild (z. B. für die Banner) von einer URL in eine temporäre Datei.
 * Es sind nur https-Adressen erlaubt, die Datei darf höchstens {@link #MAX_BYTES}
 * groß sein und muss sich als PNG, JPG oder GIF dekodieren lassen. Die Endung der
 * Ergebnisdatei richtet sich nach dem erkannten Format, nicht nach der URL.
 * <p>
 * Eine Instanz lädt ein Bild. Der Aufruf blockiert und gehört deshalb in einen
 * Hintergrund-Thread; {@link #cancel()} bricht ihn von einem anderen Thread aus ab.
 * Die zurückgegebene Datei gehört dem Aufrufer, der sie nach Gebrauch löschen muss.
 * Bei jedem Fehler und bei Abbruch räumt der Downloader seine Dateien selbst auf.
 */
public class LogoDownloader {

    /** Größenlimit für ein Bild (ca. 10 MB). */
    public static final long MAX_BYTES = 10L * 1024 * 1024;
    /** Begrenzt die dekodierte Größe, damit kleine Dateien keinen riesigen Speicherbedarf auslösen. */
    static final long MAX_PIXELS = 40_000_000L;

    private final Set<String> allowedSchemes;
    private final long maxBytes;
    private volatile boolean cancelled;
    private volatile HttpClient client;

    public LogoDownloader() {
        this(Set.of("https"), MAX_BYTES);
    }

    // erweiterte Schemata und Größe nur für Tests (lokaler HTTP-Server)
    LogoDownloader(Set<String> allowedSchemes, long maxBytes) {
        this.allowedSchemes = allowedSchemes;
        this.maxBytes = maxBytes;
    }

    /** Bricht einen laufenden {@link #download} ab; er endet dann mit einer Exception. */
    public void cancel() {
        cancelled = true;
        HttpClient current = client;
        if (current != null) {
            current.shutdownNow();
        }
    }

    /**
     * Lädt und prüft das Bild. Die Fehlermeldungen sind für den Nutzer formuliert.
     *
     * @throws IOException bei ungültiger Adresse, Netzwerk-/HTTP-Fehlern, zu großer oder
     *                     ungültiger Datei sowie bei Abbruch
     */
    public File download(String url) throws IOException {
        URI uri = parse(url);
        Path part = Files.createTempFile("scoreboard-logo-", ".part");
        Path result = null;
        HttpClient httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        client = httpClient;
        try {
            if (cancelled) {
                throw new IOException("Download abgebrochen");
            }
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("Der Server antwortete mit Status " + response.statusCode() + ".");
                }
                long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                if (declared > maxBytes) {
                    throw tooLarge();
                }
                copyLimited(body, part);
            }
            String extension = verifiedExtension(part);
            result = part.resolveSibling(
                    part.getFileName().toString().replaceFirst("\\.part$", "") + extension);
            Files.move(part, result, StandardCopyOption.REPLACE_EXISTING);
            return result.toFile();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            deleteQuietly(result);
            throw new IOException("Download abgebrochen", e);
        } catch (IOException e) {
            deleteQuietly(result);
            throw cancelled ? new IOException("Download abgebrochen", e) : e;
        } catch (RuntimeException e) {
            deleteQuietly(result);
            throw e;
        } finally {
            deleteQuietly(part);
            httpClient.shutdownNow();
            client = null;
        }
    }

    private URI parse(String url) throws IOException {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.strip());
        } catch (IllegalArgumentException e) {
            throw new IOException("Die Adresse ist ungültig.", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ("http".equals(scheme) && !allowedSchemes.contains("http")) {
            throw new IOException("Nur sichere Adressen (https://) sind erlaubt.");
        }
        if (!allowedSchemes.contains(scheme) || uri.getHost() == null) {
            throw new IOException("Die Adresse muss mit https:// beginnen.");
        }
        return uri;
    }

    /** Kopiert den Stream in die Datei und bricht hart ab, sobald das Limit überschritten wird. */
    private void copyLimited(InputStream in, Path target) throws IOException {
        long total = 0;
        byte[] buffer = new byte[16 * 1024];
        try (OutputStream out = Files.newOutputStream(target)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                if (cancelled || Thread.currentThread().isInterrupted()) {
                    throw new IOException("Download abgebrochen");
                }
                total += read;
                if (total > maxBytes) {
                    throw tooLarge();
                }
                out.write(buffer, 0, read);
            }
        }
    }

    private IOException tooLarge() {
        return new IOException("Das Bild ist größer als " + (maxBytes / (1024 * 1024)) + " MB.");
    }

    /** Dekodiert das Bild testweise und liefert die zum Format passende Endung (.png, .jpg, .gif). */
    private static String verifiedExtension(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            if (in == null) {
                throw notAnImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw notAnImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                String extension = switch (reader.getFormatName().toLowerCase(Locale.ROOT)) {
                    case "png" -> ".png";
                    case "jpeg", "jpg" -> ".jpg";
                    case "gif" -> ".gif";
                    default -> throw notAnImage();
                };
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_PIXELS) {
                    throw new IOException("Die Bildabmessungen sind zu groß.");
                }
                reader.read(0);
                return extension;
            } catch (RuntimeException e) {
                throw notAnImage(e);
            } finally {
                reader.dispose();
            }
        }
    }

    private static IOException notAnImage() {
        return new IOException("Die Adresse liefert kein gültiges Bild (PNG, JPG oder GIF).");
    }

    private static IOException notAnImage(Throwable cause) {
        return new IOException("Die Adresse liefert kein gültiges Bild (PNG, JPG oder GIF).", cause);
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            file.toFile().deleteOnExit();
        }
    }

    static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot >= 0) {
            String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (extension.matches("png|jpg|jpeg|gif")) {
                return "." + extension;
            }
        }
        return ".png";
    }
}
