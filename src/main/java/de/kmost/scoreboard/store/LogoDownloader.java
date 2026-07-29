package de.kmost.scoreboard.store;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;

/** Lädt ein Bild (z. B. für die Banner) von einer URL in eine temporäre Datei. */
public final class LogoDownloader {

    private LogoDownloader() {
    }

    public static File download(String url) throws IOException, InterruptedException {
        URI uri = URI.create(url.strip());
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(5))
                .build()) {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(15))
                    .build();
            String path = uri.getPath() == null ? "" : uri.getPath();
            Path target = Files.createTempFile("scoreboard-logo-", extensionOf(path));
            HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(target);
                throw new IOException("HTTP-Status " + response.statusCode());
            }
            return target.toFile();
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
