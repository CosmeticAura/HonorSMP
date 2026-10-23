package com.honorsmp.logic;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Pure rules for the live Discord leaderboard (1.6.3): which players and civs show, the embed JSON, webhook URLs. */
public final class LeaderboardEmbed {
    private LeaderboardEmbed() {}

    public static final int TOP_PLAYERS = 10;
    public static final int BOTTOM_PLAYERS = 5;
    public static final int TOP_CIVS = 5;
    /** Gold (Discord's gold, 0xF1C40F). */
    public static final int COLOR = 0xF1C40F;
    private static final int FIELD_MAX = 1000; // Discord allows 1024 per field value

    /** One player. tier is the display name (Paragon, Neutral, Warlord...). */
    public record PlayerRow(String uuid, String name, String tier, int score, boolean dishonored) {}

    public record CivRow(String name, int totalHonor, int members) {}

    /** The weekly top donor so far (this week since Monday 00:00). Null in Input = nobody yet. */
    public record DonorRow(String name, long amount) {}

    public record Input(List<PlayerRow> players, List<CivRow> civs, DonorRow donor, int updateMinutes, String isoTime) {}

    // ------------------------------------------------------------ selection

    /** Top 10 by score (judges are not passed in), ties by name. */
    public static List<PlayerRow> top(List<PlayerRow> all) {
        List<PlayerRow> out = new ArrayList<>(all);
        out.sort(Comparator.comparingInt(PlayerRow::score).reversed().thenComparing(PlayerRow::name, String.CASE_INSENSITIVE_ORDER));
        return out.size() > TOP_PLAYERS ? new ArrayList<>(out.subList(0, TOP_PLAYERS)) : out;
    }

    /** Bottom 5: dishonored players only, lowest first, not repeating anyone already shown in the top list. */
    public static List<PlayerRow> bottom(List<PlayerRow> all, List<PlayerRow> shownInTop) {
        Set<String> shown = new HashSet<>();
        for (PlayerRow r : shownInTop) shown.add(r.uuid());
        List<PlayerRow> out = new ArrayList<>();
        for (PlayerRow r : all) if (r.dishonored() && !shown.contains(r.uuid())) out.add(r);
        out.sort(Comparator.comparingInt(PlayerRow::score).thenComparing(PlayerRow::name, String.CASE_INSENSITIVE_ORDER));
        return out.size() > BOTTOM_PLAYERS ? new ArrayList<>(out.subList(0, BOTTOM_PLAYERS)) : out;
    }

    // ------------------------------------------------------------ embed

    /** Escapes Discord markdown so names like __x__ or *bob* show as typed. Pings are blocked separately with allowed_mentions. */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '*' || c == '_' || c == '`' || c == '~' || c == '|' || c == '>' || c == '#' || c == '[' || c == ']' || c == '(' || c == ')' || c == ':' || c == '@' || c == '<') b.append('\\');
            if (c == '\n' || c == '\r') b.append(' ');
            else b.append(c);
        }
        return b.toString();
    }

    private static String clip(String s) {
        return s.length() <= FIELD_MAX ? s : s.substring(0, FIELD_MAX - 1) + "…";
    }

    private static String playerLines(List<PlayerRow> rows) {
        if (rows.isEmpty()) return "Nobody yet";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            PlayerRow r = rows.get(i);
            if (i > 0) b.append('\n');
            b.append(i + 1).append(". ").append(escape(r.name())).append(" · ").append(escape(r.tier())).append(" · ").append(r.score());
        }
        return clip(b.toString());
    }

    private static String civLines(List<CivRow> rows) {
        if (rows.isEmpty()) return "No civilizations yet";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            CivRow r = rows.get(i);
            if (i > 0) b.append('\n');
            b.append(i + 1).append(". ").append(escape(r.name())).append(" · ").append(r.totalHonor()).append(" honor · ")
                    .append(r.members()).append(r.members() == 1 ? " member" : " members");
        }
        return clip(b.toString());
    }

    public static String footerText(int minutes) {
        int m = Math.max(1, minutes);
        return "Updates every " + (m == 1 ? "minute" : m + " minutes");
    }

    private static JsonObject field(String name, String value) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value);
        f.addProperty("inline", false);
        return f;
    }

    /**
     * The webhook body. withTime = false leaves out the embed timestamp (that is the form compared to decide whether
     * anything changed). Caller has already left judges out of the players and sorted nothing: this sorts and cuts.
     */
    public static JsonObject build(Input in, boolean withTime) {
        List<PlayerRow> top = top(in.players());
        List<PlayerRow> bottom = bottom(in.players(), top);
        List<CivRow> civs = in.civs().size() > TOP_CIVS ? in.civs().subList(0, TOP_CIVS) : in.civs();

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Honor leaderboard");
        embed.addProperty("color", COLOR);
        JsonArray fields = new JsonArray();
        fields.add(field("Top 10 by honor", playerLines(top)));
        fields.add(field("Bottom 5 (most dishonored)", playerLines(bottom)));
        fields.add(field("Top 5 civs", civLines(civs)));
        DonorRow d = in.donor();
        fields.add(field("Top donor this week", d == null ? "Nobody yet" : escape(d.name()) + " · " + d.amount() + " honor from donations"));
        embed.add("fields", fields);
        JsonObject footer = new JsonObject();
        footer.addProperty("text", footerText(in.updateMinutes()));
        embed.add("footer", footer);
        if (withTime && in.isoTime() != null && !in.isoTime().isEmpty()) embed.addProperty("timestamp", in.isoTime());

        JsonObject body = new JsonObject();
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        body.add("embeds", embeds);
        JsonObject mentions = new JsonObject();
        mentions.add("parse", new JsonArray()); // nobody can be pinged by a name
        body.add("allowed_mentions", mentions);
        return body;
    }

    /** Stable fingerprint of what the message says (time left out). Equal fingerprints = nothing changed. */
    public static String fingerprint(Input in) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(build(in, false).toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return build(in, false).toString();
        }
    }

    // ------------------------------------------------------------ webhook urls and replies

    private static String base(String url) {
        String u = url.trim();
        int q = u.indexOf('?');
        if (q >= 0) u = u.substring(0, q);
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        return u;
    }

    private static String query(String url) {
        String u = url.trim();
        int q = u.indexOf('?');
        return q >= 0 && q < u.length() - 1 ? u.substring(q + 1) : "";
    }

    /** First post: the webhook with wait=true so Discord returns the message (and its id). */
    public static String postUrl(String url) {
        String q = query(url);
        q = q.replaceAll("(^|&)wait=[^&]*", "").replaceAll("^&", "");
        return base(url) + "?" + (q.isEmpty() ? "" : q + "&") + "wait=true";
    }

    /** Later updates: PATCH /messages/<id> on the same webhook (keeps thread_id and other query parameters). */
    public static String patchUrl(String url, String messageId) {
        String q = query(url);
        return base(url) + "/messages/" + messageId + (q.isEmpty() ? "" : "?" + q);
    }

    /** The "id" of Discord's reply, or null when the reply has none. */
    public static String messageId(String responseBody) {
        try {
            JsonElement e = JsonParser.parseString(responseBody);
            if (e.isJsonObject() && e.getAsJsonObject().has("id") && !e.getAsJsonObject().get("id").isJsonNull()) {
                String id = e.getAsJsonObject().get("id").getAsString();
                return id.isBlank() ? null : id;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Milliseconds to wait after a 429: retry_after (seconds, may be fractional) from the body, else the Retry-After header value, else 5 s. Capped at one hour. */
    public static long retryAfterMs(String responseBody, String retryAfterHeader) {
        double sec = -1;
        try {
            JsonElement e = JsonParser.parseString(responseBody);
            if (e.isJsonObject() && e.getAsJsonObject().has("retry_after")) sec = e.getAsJsonObject().get("retry_after").getAsDouble();
        } catch (Exception ignored) {
        }
        if (sec < 0 && retryAfterHeader != null) {
            try {
                sec = Double.parseDouble(retryAfterHeader.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        if (sec < 0) sec = 5;
        return Math.min(3_600_000L, Math.max(1000L, (long) Math.ceil(sec * 1000)));
    }
}
