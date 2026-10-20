package com.honorsmp;

import com.honorsmp.HonorData.Case;
import com.honorsmp.HonorData.Civ;
import com.honorsmp.HonorData.Hit;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/** Judge review: case creation, decisions and the optional Discord webhook. */
public final class Cases {
    private Cases() {}

    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public static boolean isWeapon(ItemStack s) {
        return s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES) || s.isIn(ItemTags.SPEARS)
                || s.isOf(Items.BOW) || s.isOf(Items.CROSSBOW) || s.isOf(Items.TRIDENT) || s.isOf(Items.MACE);
    }

    public static boolean noArmor(ServerPlayerEntity p) {
        return p.getEquippedStack(EquipmentSlot.HEAD).isEmpty() && p.getEquippedStack(EquipmentSlot.CHEST).isEmpty()
                && p.getEquippedStack(EquipmentSlot.LEGS).isEmpty() && p.getEquippedStack(EquipmentSlot.FEET).isEmpty();
    }

    public static boolean unarmedAndUnarmored(ServerPlayerEntity p) {
        return !isWeapon(p.getMainHandStack()) && !isWeapon(p.getOffHandStack()) && noArmor(p);
    }

    public static String gear(ServerPlayerEntity p) {
        StringBuilder sb = new StringBuilder("holding ");
        ItemStack m = p.getMainHandStack();
        sb.append(m.isEmpty() ? "nothing" : m.getName().getString());
        sb.append("; armor: ");
        List<String> a = new ArrayList<>();
        for (EquipmentSlot s : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack st = p.getEquippedStack(s);
            if (!st.isEmpty()) a.add(st.getName().getString());
        }
        sb.append(a.isEmpty() ? "none" : String.join(", ", a));
        return sb.toString();
    }

    public static Case open(Honor h, String type, ServerPlayerEntity killer, ServerPlayerEntity victim, String killerGear,
                            String victimGear, int provisional, int applied, List<DamageEvent> history, long now, String note) {
        Case c = new Case();
        c.id = h.data.nextCaseId++;
        c.type = type;
        c.time = now;
        c.killer = killer.getUuid().toString();
        c.killerName = killer.getName().getString();
        c.victim = victim.getUuid().toString();
        c.victimName = victim.getName().getString();
        c.killerScore = h.score(killer);
        c.victimScore = h.score(victim);
        c.dim = Honor.dim(victim.getEntityWorld());
        BlockPos bp = victim.getBlockPos();
        c.x = bp.getX();
        c.y = bp.getY();
        c.z = bp.getZ();
        c.killerGear = killerGear;
        c.victimGear = victimGear;
        c.provisional = provisional;
        c.applied = applied;
        c.note = note;

        long window = h.config.caseDamageLogSeconds * 1000L;
        List<DamageEvent> pair = new ArrayList<>();
        for (DamageEvent e : history) {
            boolean between = (e.attacker().equals(c.killer) && e.victim().equals(c.victim))
                    || (e.attacker().equals(c.victim) && e.victim().equals(c.killer));
            if (between && now - e.timeMs() <= window) pair.add(e);
        }
        pair.sort((x, y) -> Long.compare(x.timeMs(), y.timeMs()));
        for (DamageEvent e : pair) {
            Hit hit = new Hit();
            hit.agoMs = now - e.timeMs();
            hit.attacker = e.attacker().equals(c.killer) ? c.killerName : c.victimName;
            hit.amount = e.amount();
            c.damageLog.add(hit);
        }
        if (!pair.isEmpty()) {
            DamageEvent first = pair.get(0);
            c.firstDamager = first.attacker().equals(c.killer) ? c.killerName : c.victimName;
            c.firstDamage = first.amount();
        }
        h.data.cases.add(c);
        h.markDirty();
        discord(h, "**New " + title(c) + "** (" + c.type + ") " + c.killerName + " killed " + c.victimName
                + " at " + c.dim + " " + c.x + " " + c.y + " " + c.z
                + ". First damage: " + c.firstDamager + ". Provisional " + signed(c.provisional) + ". " + note);
        announce(h, c);
        return c;
    }

    // ------------------------------------------------------------ announcing, books

    /** Tells online judges about a new case, gives each judge outside the case the book, queues it for offline ones. */
    public static void announce(Honor h, Case c) {
        for (String j : h.data.judges) {
            ServerPlayerEntity p = h.online(j);
            if (p != null) {
                p.sendMessage(net.minecraft.text.Text.literal("[HonorSMP] New case #" + c.id + " [" + c.type + "]: " + c.killerName
                        + (c.type.equals("APPEAL") ? " appeals an honor change" : " killed " + c.victimName)
                        + ". Use /honor case " + c.id).formatted(net.minecraft.util.Formatting.YELLOW));
                giveBook(h, c, p);
            } else {
                List<Integer> q = h.data.pendingBooks.computeIfAbsent(j, x -> new ArrayList<>());
                if (!q.contains(c.id)) q.add(c.id);
                h.markDirty();
            }
        }
    }

    /** Gives the case book to a judge (also used by the "Give book" button). Returns false if it cannot be built. */
    public static boolean giveBook(Honor h, Case c, ServerPlayerEntity judge) {
        try {
            judge.getInventory().offerOrDrop(makeBook(c));
            return true;
        } catch (Exception e) {
            Honor.LOG.warn("Could not build the book for case #{}: {}", c.id, e.toString());
            return false;
        }
    }

    /** On login: hand over the books queued while the judge was offline (only for cases still open). */
    public static void deliverPending(Honor h, ServerPlayerEntity p) {
        List<Integer> q = h.data.pendingBooks.remove(p.getUuid().toString());
        if (q == null) return;
        h.markDirty();
        int n = 0;
        for (int id : q) {
            Case c = find(h, id);
            if (c == null || !c.status.equals("OPEN")) continue;
            if (giveBook(h, c, p)) n++;
        }
        if (n > 0) p.sendMessage(net.minecraft.text.Text.literal("[HonorSMP] You received " + n + " case book(s) from while you were away.")
                .formatted(net.minecraft.util.Formatting.YELLOW));
    }

    public static ItemStack makeBook(Case c) {
        List<net.minecraft.text.RawFilteredPair<net.minecraft.text.Text>> pages = new ArrayList<>();
        for (String pg : BookPager.paginate(bookLines(c), BookPager.PAGE_CHARS)) {
            pages.add(net.minecraft.text.RawFilteredPair.of(net.minecraft.text.Text.literal(pg)));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(net.minecraft.component.DataComponentTypes.WRITTEN_BOOK_CONTENT,
                new net.minecraft.component.type.WrittenBookContentComponent(
                        net.minecraft.text.RawFilteredPair.of(BookPager.title(title(c))), "HonorSMP", 0, pages, true));
        return book;
    }

    /** All the text of a case book, one entry per line (pages are cut by BookPager). */
    public static List<String> bookLines(Case c) {
        List<String> l = new ArrayList<>();
        boolean appeal = c.type.equals("APPEAL");
        l.add(title(c) + " [" + c.type + "]");
        l.add(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(c.time)));
        l.add("");
        if (appeal) {
            l.add("Appeal by: " + c.killerName + " (score " + c.killerScore + ")");
            l.add("About: honor change #" + c.appealLogId + " (" + signed(c.provisional) + ")");
            if (c.victimName != null && !c.victimName.isEmpty()) l.add("Other player: " + c.victimName);
            l.add("");
            l.add("Reason: " + c.appealReason);
        } else {
            l.add("Killer: " + c.killerName + " (score " + c.killerScore + ")");
            l.add("Victim: " + c.victimName + " (score " + c.victimScore + ")");
            l.add("");
            l.add("What happened: " + c.killerName + " killed " + c.victimName + ". " + c.note);
        }
        l.add("");
        l.add("Damage timeline (last " + (int) (c.damageLog.isEmpty() ? 0 : Math.ceil(c.damageLog.get(0).agoMs / 1000.0)) + "s):");
        if (c.damageLog.isEmpty()) l.add("none recorded");
        for (Hit hit : c.damageLog) l.add(String.format("-%.1fs %s hit %.1f", hit.agoMs / 1000.0, hit.attacker, hit.amount));
        if (!appeal) l.add("First damage: " + c.firstDamager);
        l.add("");
        if (!appeal) {
            l.add("Gear");
            l.add(c.killerName + ": " + c.killerGear);
            l.add(c.victimName + ": " + c.victimGear);
            l.add("");
        }
        l.add("Location: " + c.dim + " " + c.x + " " + c.y + " " + c.z);
        l.add("");
        l.add("Mod applied: " + signed(c.applied) + " to " + c.killerName);
        l.add("Suggested ruling: " + suggest(c));
        l.add("");
        l.add("/honor case " + c.id + " confirm | cancel | set <n>");
        return l;
    }

    public static String suggest(Case c) {
        switch (c.type) {
            case "IP_FLAG":
                return "Confirm (no honor was applied). Cancel if it was a real fight.";
            case "APPEAL":
                return "Cancel to undo the change, confirm to keep it. Check /honor log " + c.killerName + ".";
            default:
                if (c.firstDamager.equals(c.killerName)) return "Confirm (" + c.killerName + " hit first, " + signed(c.provisional) + " stands).";
                if (c.firstDamager.equals(c.victimName)) return "Cancel (" + c.victimName + " hit first: self defense).";
                return "Unclear (no first hit recorded). Judge decides; the provisional " + signed(c.provisional) + " is applied.";
        }
    }

    /** Opens an APPEAL case for an honor log entry. Caller already validated with AppealRules. */
    public static Case openAppeal(Honor h, ServerPlayerEntity appellant, HonorData.LogEntry e, String reason) {
        Case c = new Case();
        c.id = h.data.nextCaseId++;
        c.type = "APPEAL";
        c.time = System.currentTimeMillis();
        c.killer = appellant.getUuid().toString();
        c.killerName = appellant.getName().getString();
        PlayerRecord other = e.other == null || e.other.isEmpty() ? null : h.byName(e.other);
        c.victim = other == null ? "" : other.uuid;
        c.victimName = e.other == null ? "" : e.other;
        c.killerScore = h.score(appellant);
        c.victimScore = other == null ? 0 : other.score;
        c.dim = e.dim == null ? "" : e.dim;
        c.x = e.x;
        c.y = e.y;
        c.z = e.z;
        c.provisional = e.delta;
        c.applied = e.delta;
        c.appealLogId = e.id;
        c.appealReason = reason;
        c.note = "Appeal of honor change #" + e.id + " (" + signed(e.delta) + "): " + e.reason + ". Reason given: " + reason;
        h.data.cases.add(c);
        h.markDirty();
        discord(h, "**New appeal " + title(c) + "** by " + c.killerName + " about change #" + e.id + " (" + signed(e.delta)
                + " " + e.reason + "). Reason: " + reason);
        announce(h, c);
        return c;
    }

    /** The one display title for a case: "Case #7" or "Case #7 \"Name\"". */
    public static String title(Case c) {
        return com.honorsmp.logic.CaseName.title(c.id, c.name);
    }

    /** Sets (or clears, when empty) the case name. Returns the stored name. */
    public static String rename(Honor h, Case c, String rawName, String by) {
        String old = c.name == null ? "" : c.name;
        c.name = com.honorsmp.logic.CaseName.sanitize(rawName);
        h.markDirty();
        Honor.LOG.info("Case #{} renamed by {}: \"{}\" -> \"{}\"", c.id, by, old, c.name);
        return c.name;
    }

    public static Case find(Honor h, int id) {
        for (Case c : h.data.cases) if (c.id == id) return c;
        return null;
    }

    /** Returns an error message, or null when the decision was applied. */
    public static String decide(Honor h, Case c, String action, Integer amount, String reason, String judgeName) {
        if (c.type.equals("IP_FLAG")) {
            if (!action.equals("confirm") && !action.equals("cancel")) return "IP flag cases only take confirm or cancel (no honor was applied).";
        }
        String why = reason == null || reason.isBlank() ? "" : reason;
        PlayerRecord k = h.data.players.get(c.killer);
        String kn = k == null ? c.killerName : k.name;
        switch (action) {
            case "confirm" -> {
                c.status = "CONFIRMED";
            }
            case "cancel" -> {
                if (c.applied != 0) {
                    h.change(c.killer, kn, -c.applied, title(c) + " cancelled by " + judgeName, judgeName, null, c.victimName, c.id);
                    c.applied = 0;
                }
                c.status = "CANCELLED";
            }
            case "set" -> {
                int target = amount == null ? 0 : amount;
                int diff = target - c.applied;
                if (diff != 0) {
                    int real = h.change(c.killer, kn, diff, title(c) + " changed to " + signed(target) + " by " + judgeName
                            + (why.isEmpty() ? "" : " (" + why + ")"), judgeName, null, c.victimName, c.id);
                    c.applied += real;
                }
                c.status = "CHANGED";
            }
            default -> {
                return "Unknown action " + action;
            }
        }
        c.decidedBy = judgeName;
        c.decidedAt = System.currentTimeMillis();
        c.decisionReason = why;
        h.markDirty();
        if (c.type.equals("APPEAL")) {
            ServerPlayerEntity ap = h.online(c.killer);
            if (ap != null) ap.sendMessage(net.minecraft.text.Text.literal("Your appeal (" + title(c) + ") was ruled: " + c.status
                    + " by " + judgeName + (why.isEmpty() ? "" : " (" + why + ")")).formatted(net.minecraft.util.Formatting.YELLOW));
        }
        discord(h, "**" + title(c) + " " + c.status + "** by " + judgeName + ". " + c.killerName + " killed " + c.victimName
                + ". Honor applied to killer: " + signed(c.applied) + (why.isEmpty() ? "" : ". Reason: " + why));
        return null;
    }

    public static String signed(int n) {
        return n > 0 ? "+" + n : String.valueOf(n);
    }

    public static void discord(Honor h, String text) {
        String url = h.config.discordWebhookUrl;
        if (url == null || url.isBlank()) return;
        try {
            String body = "{\"content\":" + Honor.GSON.toJson(text.length() > 1900 ? text.substring(0, 1900) : text) + "}";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HTTP.sendAsync(req, HttpResponse.BodyHandlers.discarding()).whenComplete((r, t) -> {
                if (t != null) Honor.LOG.warn("Discord webhook failed: {}", t.toString());
                else if (r.statusCode() >= 300) Honor.LOG.warn("Discord webhook answered HTTP {}", r.statusCode());
            });
        } catch (Exception e) {
            Honor.LOG.warn("Discord webhook failed: {}", e.toString());
        }
    }
}
