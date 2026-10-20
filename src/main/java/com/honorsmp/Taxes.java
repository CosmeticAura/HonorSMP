package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.TaxMath;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Weekly civ tax: penalties, warnings, payment into data.treasuryEmeralds. */
public final class Taxes {
    private Taxes() {}

    /** Gives civs without tax fields (old data, new civs) their first due date. */
    public static void init(Honor h, Civ civ, long now) {
        if (civ.taxDue <= 0) {
            civ.taxDue = TaxMath.initialDue(civ.created, now, h.config.taxIntervalDays);
            civ.taxPaid = false;
            civ.taxPenaltyDays = 0;
            civ.taxWarned = false;
            h.markDirty();
        }
    }

    public static long amount(Honor h, Civ civ) {
        return TaxMath.amountDue(civ.members.size(), h.config.taxPerMember);
    }

    public static boolean isLate(Honor h, Civ civ) {
        return TaxMath.isLate(System.currentTimeMillis(), civ.taxDue, civ.taxPaid);
    }

    public static boolean isManager(Civ civ, String uuid) {
        return civ.leader.equals(uuid) || civ.officers.contains(uuid);
    }

    /** Run about once a minute: roll paid cycles, apply daily penalties, send the pre-due warning. */
    public static void tick(Honor h) {
        long now = System.currentTimeMillis();
        for (Civ civ : h.data.civs.values()) {
            init(h, civ, now);
            if (civ.taxPaid && now >= civ.taxDue) {
                civ.taxDue = TaxMath.rollForward(civ.taxDue, now, h.config.taxIntervalDays);
                civ.taxPaid = false;
                civ.taxPenaltyDays = 0;
                civ.taxWarned = false;
                h.markDirty();
            }
            int owed = civ.taxPaid ? 0 : TaxMath.penaltyDaysOwed(now, civ.taxDue, civ.taxPenaltyDays);
            if (owed > 0) {
                civ.taxPenaltyDays += owed; // persisted first so a restart never repeats it
                h.markDirty();
                int loss = owed * h.config.taxLatePenaltyHonor;
                for (String id : civ.members) {
                    h.change(id, h.nameOf(id), -loss, "Civ tax late", "auto", null, "", 0);
                }
                notifyManagers(h, civ, civ.name + " is " + TaxMath.daysLate(now, civ.taxDue) + " day(s) late on its tax ("
                        + amount(h, civ) + " emeralds). Every member lost " + loss + " honor. Use /civ tax pay.", Formatting.RED);
            } else if (TaxMath.shouldWarn(now, civ.taxDue, civ.taxPaid, civ.taxWarned, h.config.taxWarnHours)) {
                civ.taxWarned = true;
                h.markDirty();
                notifyManagers(h, civ, civ.name + " owes " + amount(h, civ) + " emeralds in tax, due in less than "
                        + h.config.taxWarnHours + " hours. Use /civ tax pay.", Formatting.YELLOW);
            }
        }
    }

    private static void notifyManagers(Honor h, Civ civ, String msg, Formatting color) {
        for (String id : civ.members) {
            if (!isManager(civ, id)) continue;
            ServerPlayerEntity p = h.online(id);
            if (p != null) p.sendMessage(Text.literal("[Tax] " + msg).formatted(color));
        }
    }

    public static void onLogin(Honor h, ServerPlayerEntity p) {
        Civ civ = h.civOf(p.getUuid().toString());
        if (civ == null || !isManager(civ, p.getUuid().toString())) return;
        long now = System.currentTimeMillis();
        init(h, civ, now);
        if (TaxMath.needsReminder(now, civ.taxDue, civ.taxPaid, h.config.taxWarnHours)) {
            boolean late = now > civ.taxDue;
            p.sendMessage(Text.literal("[Tax] " + civ.name + " " + (late ? "is LATE on" : "owes") + " its tax: " + amount(h, civ)
                    + " emeralds, " + (late ? "was due " : "due ") + dueText(civ.taxDue - now) + ". Use /civ tax pay.")
                    .formatted(late ? Formatting.RED : Formatting.YELLOW));
        }
    }

    public static String dueText(long msUntil) {
        long abs = Math.abs(msUntil);
        long d = abs / TaxMath.DAY_MS, hr = (abs % TaxMath.DAY_MS) / TaxMath.HOUR_MS, m = (abs % TaxMath.HOUR_MS) / 60_000L;
        String t = (d > 0 ? d + "d " : "") + hr + "h " + m + "m";
        return msUntil >= 0 ? "in " + t : t + " ago";
    }

    /** Returns lines describing the civ's tax status. */
    public static String status(Honor h, Civ civ) {
        long now = System.currentTimeMillis();
        init(h, civ, now);
        long amt = amount(h, civ);
        if (civ.taxPaid) return "Tax paid for this cycle. Next payment of " + amt + " emeralds due " + dueText(civ.taxDue - now) + ".";
        if (now > civ.taxDue) {
            return "UNPAID: " + amt + " emeralds, " + dueText(civ.taxDue - now) + " (" + TaxMath.daysLate(now, civ.taxDue) + " day(s) late, -"
                    + h.config.taxLatePenaltyHonor + " honor per member per day).";
        }
        return "Tax owed: " + amt + " emeralds (" + civ.members.size() + " members x " + h.config.taxPerMember + "), due " + dueText(civ.taxDue - now) + ".";
    }

    /** Pays the full amount from the player's inventory. Returns an error message or null on success. */
    public static String pay(Honor h, Civ civ, ServerPlayerEntity p) {
        if (!isManager(civ, p.getUuid().toString())) return "Only the leader or officers can pay the tax.";
        long now = System.currentTimeMillis();
        init(h, civ, now);
        if (civ.taxPaid) return "This cycle is already paid. Next payment is due " + dueText(civ.taxDue - now) + ".";
        long amt = amount(h, civ);
        if (amt > Integer.MAX_VALUE) return "Tax amount is too large.";
        long have = emeraldValue(p);
        if (have < amt) return "You need " + amt + " emeralds (blocks count as 9) but only have " + have + ". Nothing was taken.";
        takeValue(p, (int) amt);
        h.treasury.addEmeralds(amt);
        if (now > civ.taxDue) {
            civ.taxDue = TaxMath.nextDueAfterLatePay(civ.taxDue, now, h.config.taxIntervalDays);
            civ.taxPaid = false;
        } else {
            civ.taxPaid = true; // early: covers this cycle; due date rolls +interval when it passes
        }
        civ.taxPenaltyDays = 0;
        civ.taxWarned = false;
        h.markDirty();
        return null;
    }

    static long emeraldValue(ServerPlayerEntity p) {
        long n = 0;
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.EMERALD)) n += s.getCount();
            else if (s.isOf(Items.EMERALD_BLOCK)) n += 9L * s.getCount();
        }
        return n;
    }

    /** Takes loose emeralds first, then blocks, returning change in emeralds. Caller checked the total. */
    static void takeValue(ServerPlayerEntity p, int amount) {
        int fromLoose = Math.min(amount, Honor.countEmeralds(p));
        Honor.takeEmeralds(p, fromLoose);
        int left = amount - fromLoose;
        if (left <= 0) return;
        int blocks = (left + 8) / 9;
        PlayerInventory inv = p.getInventory();
        int need = blocks;
        for (int i = 0; i < inv.size() && need > 0; i++) {
            ItemStack s = inv.getStack(i);
            if (s.isOf(Items.EMERALD_BLOCK)) {
                int t = Math.min(need, s.getCount());
                s.decrement(t);
                need -= t;
            }
        }
        int change = blocks * 9 - left;
        if (change > 0) Honor.giveEmeralds(p, change);
    }
}
