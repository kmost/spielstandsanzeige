package de.kmost.scoreboard.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

/** Tests gegen einen lokalen HTTP-Server; http ist dafür nur in den Tests freigeschaltet. */
class LogoDownloaderTest {

    private static final Set<String> HTTP_ALLOWED = Set.of("https", "http");

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);
    private Set<String> tempFilesBefore;

    @BeforeEach
    void startServer() throws IOException {
        tempFilesBefore = logoTempFiles();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/logo.png", ex -> reply(ex, 200, "image/png", image("png")));
        // Endung und Content-Type der URL lügen: erkannt wird am Inhalt
        server.createContext("/liegt.png", ex -> reply(ex, 200, "image/png", image("jpg")));
        server.createContext("/logo.jpg", ex -> reply(ex, 200, "image/jpeg", image("jpg")));
        server.createContext("/logo.gif", ex -> reply(ex, 200, "image/gif", image("gif")));
        server.createContext("/nichts", ex -> reply(ex, 404, "text/plain", "weg".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/seite.png", ex -> reply(ex, 200, "image/png",
                "<html>keine Grafik</html>".getBytes(StandardCharsets.UTF_8)));
        server.createContext("/kaputt.png", ex -> reply(ex, 200, "image/png", truncated(image("png"))));
        server.createContext("/gross", ex -> reply(ex, 200, "image/png", new byte[5_000]));
        server.createContext("/gross-ohne-laenge", ex -> {
            ex.getResponseHeaders().add("Content-Type", "image/png");
            ex.sendResponseHeaders(200, 0); // chunked, keine Content-Length
            try (OutputStream out = ex.getResponseBody()) {
                out.write(new byte[5_000]);
            }
        });
        server.createContext("/umleitung", ex -> {
            ex.getResponseHeaders().add("Location", "/logo.png");
            ex.sendResponseHeaders(302, -1);
            ex.close();
        });
        server.createContext("/haengt", ex -> {
            ex.getResponseHeaders().add("Content-Type", "image/png");
            ex.sendResponseHeaders(200, 1_000_000);
            OutputStream out = ex.getResponseBody();
            out.write(new byte[100]);
            out.flush();
            try {
                release.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        release.countDown();
        server.stop(0);
    }

    @Test
    void downloadsPngAndDecodesIt() throws IOException {
        File file = downloader().download(url("/logo.png"));
        try {
            assertTrue(file.getName().endsWith(".png"));
            assertEquals(4, ImageIO.read(file).getWidth());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void extensionFollowsContentNotUrlOrContentType() throws IOException {
        File fromLyingUrl = downloader().download(url("/liegt.png"));
        File jpg = downloader().download(url("/logo.jpg"));
        File gif = downloader().download(url("/logo.gif"));
        try {
            assertTrue(fromLyingUrl.getName().endsWith(".jpg"));
            assertTrue(jpg.getName().endsWith(".jpg"));
            assertTrue(gif.getName().endsWith(".gif"));
        } finally {
            Files.deleteIfExists(fromLyingUrl.toPath());
            Files.deleteIfExists(jpg.toPath());
            Files.deleteIfExists(gif.toPath());
        }
    }

    @Test
    void followsRedirect() throws IOException {
        File file = downloader().download(url("/umleitung"));
        try {
            assertEquals(4, ImageIO.read(file).getWidth());
        } finally {
            Files.deleteIfExists(file.toPath());
        }
    }

    @Test
    void rejectsHttpStatusErrors() {
        IOException e = assertThrows(IOException.class, () -> downloader().download(url("/nichts")));
        assertTrue(e.getMessage().contains("404"));
        assertNoLeftoverTempFiles();
    }

    @Test
    void rejectsNonImageContent() {
        IOException e = assertThrows(IOException.class, () -> downloader().download(url("/seite.png")));
        assertTrue(e.getMessage().contains("kein gültiges Bild"));
        assertNoLeftoverTempFiles();
    }

    @Test
    void rejectsCorruptImage() {
        assertThrows(IOException.class, () -> downloader().download(url("/kaputt.png")));
        assertNoLeftoverTempFiles();
    }

    @Test
    void rejectsTooLargeByContentLength() {
        IOException e = assertThrows(IOException.class,
                () -> new LogoDownloader(HTTP_ALLOWED, 1_000).download(url("/gross")));
        assertTrue(e.getMessage().contains("größer"));
        assertNoLeftoverTempFiles();
    }

    @Test
    void rejectsTooLargeWhileStreamingWithoutContentLength() {
        IOException e = assertThrows(IOException.class,
                () -> new LogoDownloader(HTTP_ALLOWED, 1_000).download(url("/gross-ohne-laenge")));
        assertTrue(e.getMessage().contains("größer"));
        assertNoLeftoverTempFiles();
    }

    @Test
    void rejectsPlainHttpByDefault() {
        IOException e = assertThrows(IOException.class,
                () -> new LogoDownloader().download(url("/logo.png")));
        assertTrue(e.getMessage().contains("https"));
        assertNoLeftoverTempFiles();
    }

    @Test
    void rejectsOtherSchemesAndGarbage() {
        LogoDownloader downloader = new LogoDownloader();
        assertThrows(IOException.class, () -> downloader.download("file:///etc/passwd"));
        assertThrows(IOException.class, () -> downloader.download("ftp://example.org/logo.png"));
        assertThrows(IOException.class, () -> downloader.download("kein url"));
        assertThrows(IOException.class, () -> downloader.download(""));
        assertThrows(IOException.class, () -> downloader.download(null));
        assertNoLeftoverTempFiles();
    }

    @Test
    void cancelStopsStalledDownloadAndCleansUp() throws Exception {
        LogoDownloader downloader = downloader();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                downloader.download(url("/haengt"));
            } catch (Throwable t) {
                failure.set(t);
            }
        });
        worker.start();
        Thread.sleep(500); // Verbindung steht, Server liefert den Rest nicht
        downloader.cancel();
        worker.join(5_000);
        assertFalse(worker.isAlive(), "Download muss nach cancel() enden");
        assertTrue(failure.get() instanceof IOException);
        assertTrue(failure.get().getMessage().contains("abgebrochen"));
        assertNoLeftoverTempFiles();
    }

    @Test
    void cancelBeforeStartFailsImmediately() {
        LogoDownloader downloader = downloader();
        downloader.cancel();
        assertThrows(IOException.class, () -> downloader.download(url("/logo.png")));
        assertNoLeftoverTempFiles();
    }

    private LogoDownloader downloader() {
        return new LogoDownloader(HTTP_ALLOWED, LogoDownloader.MAX_BYTES);
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void assertNoLeftoverTempFiles() {
        Set<String> leftovers = logoTempFiles();
        leftovers.removeAll(tempFilesBefore);
        assertTrue(leftovers.isEmpty(), "Temp-Dateien übrig: " + leftovers);
    }

    private static Set<String> logoTempFiles() {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("scoreboard-logo-"))
                    .collect(Collectors.toCollection(java.util.HashSet::new));
        } catch (IOException e) {
            return new java.util.HashSet<>();
        }
    }

    private static void reply(com.sun.net.httpserver.HttpExchange ex, int status, String type, byte[] body)
            throws IOException {
        ex.getResponseHeaders().add("Content-Type", type);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static byte[] image(String format) {
        try {
            // JPG kennt keinen Alpha-Kanal, deshalb RGB für alle Formate
            BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] truncated(byte[] data) {
        return java.util.Arrays.copyOf(data, data.length / 2);
    }
}
