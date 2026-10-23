package com.honorsmp;

import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live Discord leaderboard (1.6.3): keeps one webhook message up to date. The server thread only builds the JSON (once
 * per leaderboardUpdateMinutes); every HTTP call is async with a timeout. A failure logs one warning line and tries
 * again next interval. The webhook URL is never logged.
 */
public final class LiveLeaderboard {
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    private final Honor h;
    private final AtomicBoolean inFlight = new AtomicBoolean(false);
    /** Server thread only. */
    private long nextAt = 0;
    private String lastFingerprint = null;
    private String lastUrl = "";

    public LiveLeaderboard(Honor h) {
        this.h = h;
    }

    /** Called when the config is (re)loaded, including /honor reload: a changed URL or interval takes effect now. */
    public void reconfigure() {
        String url = url();
        if (!url.equals(lastUrl)) {
            lastUrl = url;
            lastFingerprint = null; // the new webhook has not seen this message
            nextAt = 0;
            if (!h.data.leaderboardMessageId.isEmpty()) {
                h.data.leaderboardMessageId = ""; // a message id belongs to one webhook
                h.markDirty();
            }
        }
    }

    private String url() {
        String u = h.config.leaderboardWebhookUrl;
        return u == null ? "" : u.trim();
    }

    private long intervalMs() {
        return Math.max(1, h.config.leaderboardUpdateMinutes) * 60_000L;
    }

    /** Every second from the main tick (server thread). Cheap when off or not due. */
    public void second() {
        String url = url();
        if (url.isEmpty()) return;
        if (!url.equals(lastUrl)) reconfigure();
        long now = System.currentTimeMillis();
        if (now < nextAt || inFlight.get()) return;
        LeaderboardEmbed.Input in = snapshot(now);
        String fp = LeaderboardEmbed.fingerprint(in);
        if (fp.equals(lastFingerprint)) {
            nextAt = now + intervalMs(); // nothing changed since the last update
            return;
        }
        String body = LeaderboardEmbed.build(in, true).toString();
        if (!inFlight.compareAndSet(false, true)) return;
        nextAt = now + intervalMs();
        send(url, body, fp, h.data.leaderboardMessageId, true);
    }

    LeaderboardEmbed.Input snapshot(long now) {
        List<LeaderboardEmbed.PlayerRow> players = new ArrayList<>();
        for (PlayerRecord r : h.data.players.values()) {
            if (r.uuid == null || r.name == null || h.isJudge(r.uuid)) continue;
            Tier t = h.tier(r);
            players.add(new LeaderboardEmbed.PlayerRow(r.uuid, r.name, t.display, r.score, t.isDishonored()));
        }
        List<CivLeaderboard.CivIn> civIn = new ArrayList<>();
        for (HonorData.Civ civ : h.data.civs.values()) civIn.add(new CivLeaderboard.CivIn(civ.name, civ.members));
        List<LeaderboardEmbed.CivRow> civs = new ArrayList<>();
        for (CivLeaderboard.Row r : CivLeaderboard.top(civIn, id -> {
            PlayerRecord pr = h.data.players.get(id);
            return pr == null ? 0 : pr.score;
        }, h::isJudge, LeaderboardEmbed.TOP_CIVS)) civs.add(new LeaderboardEmbed.CivRow(r.name(), r.totalHonor(), r.memberCount()));
        return new LeaderboardEmbed.Input(players, civs, Donor.topThisWeek(h, now), h.config.leaderboardUpdateMinutes, Instant.ofEpochMilli(now).toString());
    }

    // ------------------------------------------------------------ http (off the server thread)

    private HttpRequest.Builder req(String uri) {
        return HttpRequest.newBuilder(URI.create(uri)).timeout(TIMEOUT).header("Content-Type", "application/json");
    }

    private void send(String url, String body, String fingerprint, String messageId, boolean mayRepost) {
        HttpRequest request;
        try {
            request = (messageId.isEmpty()
                    ? req(LeaderboardEmbed.postUrl(url)).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    : req(LeaderboardEmbed.patchUrl(url, messageId)).method("PATCH", HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))).build();
        } catch (Exception e) { // a bad URL: the exception text would contain it, so it is not logged
            Honor.LOG.warn("Leaderboard webhook: leaderboardWebhookUrl is not a valid URL");
            finish(false, 0);
            return;
        }
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).whenComplete((r, t) -> {
            try {
                if (t != null) {
                    Honor.LOG.warn("Leaderboard webhook failed: {}", t.getClass().getSimpleName());
                    finish(false, 0);
                    return;
                }
                int code = r.statusCode();
                if (code == 404 && !messageId.isEmpty() && mayRepost) { // message was deleted: post a new one
                    send(url, body, fingerprint, "", false);
                    return;
                }
                if (code == 429) {
                    long wait = LeaderboardEmbed.retryAfterMs(r.body(), r.headers().firstValue("Retry-After").orElse(null));
                    Honor.LOG.warn("Leaderboard webhook rate limited, retrying in {} s", wait / 1000);
                    finish(false, wait);
                    return;
                }
                if (code >= 300) {
                    Honor.LOG.warn("Leaderboard webhook answered HTTP {}", code);
                    finish(false, 0);
                    return;
                }
                String id = messageId.isEmpty() ? LeaderboardEmbed.messageId(r.body()) : messageId;
                if (messageId.isEmpty() && id == null) Honor.LOG.warn("Leaderboard webhook reply had no message id");
                final String saveId = id;
                h.server.execute(() -> {
                    if (Honor.I != h) return;
                    if (saveId != null && !saveId.equals(h.data.leaderboardMessageId)) {
                        h.data.leaderboardMessageId = saveId;
                        h.markDirty();
                    }
                    if (saveId != null) lastFingerprint = fingerprint;
                });
                finish(true, 0);
            } catch (Exception e) {
                Honor.LOG.warn("Leaderboard webhook failed: {}", e.getClass().getSimpleName());
                finish(false, 0);
            }
        });
    }

    private void finish(boolean ok, long retryMs) {
        if (retryMs > 0) {
            h.server.execute(() -> nextAt = Math.max(nextAt, System.currentTimeMillis() + retryMs));
        }
        inFlight.set(false);
    }
}
