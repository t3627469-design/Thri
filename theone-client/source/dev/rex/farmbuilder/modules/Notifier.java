package dev.rex.farmbuilder.modules;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Sends short status messages to a Discord webhook so an overnight run can reach the owner's phone. */
final class Notifier {
    private static final int MAX_PER_HOUR = 30;
    private static final long DUPLICATE_WINDOW_MS = 300_000L;
    private final Deque<Long> sent = new ArrayDeque<>();
    private String lastText = "";
    private long lastAt;
    private ExecutorService worker;

    static boolean validUrl(String url) {
        if (url == null) return false;
        try {
            URI u = URI.create(url.trim());
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            boolean discord = host.equals("discord.com") || host.equals("discordapp.com")
                || host.equals("ptb.discord.com") || host.equals("canary.discord.com");
            return "https".equalsIgnoreCase(u.getScheme()) && discord && u.getPath() != null && u.getPath().startsWith("/api/webhooks/")
                && u.getUserInfo() == null && u.getPort() == -1;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String json(String content) {
        String text = content == null ? "" : content;
        if (text.length() > 1800) text = text.substring(0, 1800);
        StringBuilder sb = new StringBuilder("{\"username\":\"Farm Builder\",\"allowed_mentions\":{\"parse\":[]},\"content\":\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.append("\"}").toString();
    }

    /** Applies the duplicate and hourly limits; true means the message may be sent now. */
    synchronized boolean allow(String text, long now) {
        while (!this.sent.isEmpty() && now - this.sent.peekFirst() > 3_600_000L) this.sent.removeFirst();
        if (this.sent.size() >= MAX_PER_HOUR) return false;
        if (text.equals(this.lastText) && now - this.lastAt < DUPLICATE_WINDOW_MS) return false;
        if (!this.sent.isEmpty() && now - this.sent.peekLast() < 2000L) return false;
        this.sent.addLast(now);
        this.lastText = text;
        this.lastAt = now;
        return true;
    }

    /** Never blocks the game thread and never throws. */
    void send(String url, String text, long now) {
        if (!validUrl(url) || text == null || text.isBlank() || !this.allow(text, now)) return;
        final String target = url.trim();
        final byte[] body = json(text).getBytes(StandardCharsets.UTF_8);
        synchronized (this) {
            if (this.worker == null) {
                this.worker = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r, "farm-builder-notify");
                    t.setDaemon(true);
                    return t;
                });
            }
            this.worker.execute(() -> post(target, body));
        }
    }

    private static void post(String url, byte[] body) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) URI.create(url).toURL().openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("User-Agent", "FarmBuilder");
            try (OutputStream out = c.getOutputStream()) { out.write(body); }
            c.getResponseCode();
        } catch (Exception ignored) {
            // A failed notification must never disturb the farm job.
        } finally {
            if (c != null) c.disconnect();
        }
    }

    synchronized void shutdown() {
        if (this.worker != null) { this.worker.shutdown(); this.worker = null; }
    }
}
