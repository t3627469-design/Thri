package dev.rex.farmbuilder.modules;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

final class FarmFiles {
    private static final long MAX_DOWNLOAD_BYTES = 64L * 1024 * 1024;

    static File resolve(File directory, String name) throws Exception {
        if (name == null || name.isBlank() || Path.of(name).isAbsolute())
            throw new IllegalArgumentException("Use a filename inside the schematics folder.");
        Path root = directory.getCanonicalFile().toPath();
        Path target = new File(directory, name).getCanonicalFile().toPath();
        if (!target.startsWith(root) || target.equals(root))
            throw new IllegalArgumentException("The schematic must be inside the schematics folder.");
        String lower = target.getFileName().toString().toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".litematic") || lower.endsWith(".schem") || lower.endsWith(".schematic")))
            throw new IllegalArgumentException("Choose a .litematic, .schem or .schematic file.");
        return target.toFile();
    }

    static String nameFromUrl(String link) {
        URI uri = httpUri(link);
        String path = uri.getRawPath();
        String leaf = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        leaf = URLDecoder.decode(leaf.replace("+", "%2B"), StandardCharsets.UTF_8)
            .replaceAll("[^A-Za-z0-9._ +()-]", "_");
        if (leaf.isBlank() || leaf.equals(".") || leaf.equals("..")) leaf = "download.litematic";
        if (!leaf.contains(".")) leaf += ".litematic";
        return leaf;
    }

    private static URI httpUri(String link) {
        URI uri = URI.create(link);
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) || uri.getHost() == null)
            throw new IllegalArgumentException("The download link must start with http:// or https://.");
        return uri;
    }

    static void download(String link, File target) throws Exception {
        URI uri = httpUri(link);
        Files.createDirectories(target.toPath().toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(target.toPath().toAbsolutePath().getParent(), "farm-download-", ".part");
        try {
            for (int redirects = 0; redirects <= 5; redirects++) {
                if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Schematic loading cancelled.");
                HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", "FarmBuilder/3.19.0");
                try {
                    int status = connection.getResponseCode();
                    if (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
                        String location = connection.getHeaderField("Location");
                        if (location == null) throw new IllegalStateException("Download redirect has no destination.");
                        uri = httpUri(uri.resolve(location).toString());
                        continue;
                    }
                    if (status / 100 != 2) throw new IllegalStateException("Download failed: HTTP " + status);
                    long expectedSize = connection.getContentLengthLong();
                    if (expectedSize > MAX_DOWNLOAD_BYTES)
                        throw new IllegalStateException("Schematic download exceeds 64 MiB.");
                    long size = 0;
                    try (InputStream in = connection.getInputStream(); OutputStream out = Files.newOutputStream(temporary)) {
                        byte[] buffer = new byte[16384];
                        int n;
                        while ((n = in.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Schematic loading cancelled.");
                            size += n;
                            if (size > MAX_DOWNLOAD_BYTES) throw new IllegalStateException("Schematic download exceeds 64 MiB.");
                            out.write(buffer, 0, n);
                        }
                    }
                    if (size == 0) throw new IllegalStateException("The server returned an empty schematic.");
                    if (expectedSize >= 0 && size != expectedSize)
                        throw new IllegalStateException("The schematic download was incomplete.");
                    move(temporary, target.toPath());
                    return;
                } finally {
                    InputStream error = connection.getErrorStream();
                    if (error != null) error.close();
                    connection.disconnect();
                }
            }
            throw new IllegalStateException("Too many schematic download redirects.");
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void writeAtomic(Path target, String contents) throws Exception {
        Path absolute = target.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temporary = Files.createTempFile(absolute.getParent(), "farm-state-", ".tmp");
        try {
            Files.writeString(temporary, contents, StandardCharsets.UTF_8);
            move(temporary, absolute);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static void move(Path source, Path target) throws Exception {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
