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
        discord(h, "**New case #" + c.id + "** (" + c.type + ") " + c.killerName + " killed " + c.victimName
                + " at " + c.dim + " " + c.x + " " + c.y + " " + c.z
                + ". First damage: " + c.firstDamager + ". Provisional " + signed(c.provisional) + ". " + note);
        for (ServerPlayerEntity p : h.server.getPlayerManager().getPlayerList()) {
            if (h.isJudge(p)) {
                p.sendMessage(net.minecraft.text.Text.literal("[HonorSMP] New case #" + c.id + ": " + c.killerName
                        + " killed " + c.victimName + ". Use /honor case " + c.id).formatted(net.minecraft.util.Formatting.YELLOW));
            }
        }
        return c;
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
                    h.change(c.killer, kn, -c.applied, "Case #" + c.id + " cancelled by " + judgeName, judgeName, null, c.victimName, c.id);
                    c.applied = 0;
                }
                c.status = "CANCELLED";
            }
            case "set" -> {
                int target = amount == null ? 0 : amount;
                int diff = target - c.applied;
                if (diff != 0) {
                    int real = h.change(c.killer, kn, diff, "Case #" + c.id + " changed to " + signed(target) + " by " + judgeName
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
        discord(h, "**Case #" + c.id + " " + c.status + "** by " + judgeName + ". " + c.killerName + " killed " + c.victimName
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
