package com.honorsmp;

import com.honorsmp.HonorData.Case;
import com.honorsmp.logic.JudgeRules;
import com.honorsmp.logic.MenuClicks;
import com.honorsmp.logic.SignInput;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Chest menus for judge cases: the list (/honor cases) and the per-case menu. */
public final class CaseMenus {
    private CaseMenus() {}

    private static void tell(ServerPlayerEntity p, String msg, Formatting f) {
        p.sendMessage(Text.literal(msg).formatted(f));
    }

    private static Text line(String s) {
        return Text.literal(s).formatted(Formatting.GRAY);
    }

    private static boolean checkJudge(Honor h, ServerPlayerEntity p) {
        if (h.isJudge(p)) return true;
        tell(p, "Only judges can do that.", Formatting.RED);
        return false;
    }

    // ------------------------------------------------------------ list

    public static void openList(Honor h, ServerPlayerEntity p) {
        if (!checkJudge(h, p)) return;
        List<Case> open = new ArrayList<>();
        for (Case c : h.data.cases) if (c.status.equals("OPEN")) open.add(c);
        Menu m = Menu.create(Text.literal("Open cases (" + open.size() + ")"), 6);
        int shown = Math.min(open.size(), open.size() > 54 ? 53 : 54);
        long now = System.currentTimeMillis();
        for (int i = 0; i < shown; i++) {
            Case c = open.get(i);
            String victim = c.victimName == null || c.victimName.isEmpty() ? "-" : c.victimName;
            int id = c.id;
            m.set(i, Menu.icon(c.type.equals("APPEAL") ? Items.BOOK : Items.PAPER, Text.literal("Case #" + c.id).formatted(Formatting.YELLOW),
                    List.of(line("Killer: " + c.killerName), line("Victim: " + victim), line("Type: " + c.type),
                            line("Age: " + MenuClicks.age(now - c.time)), Text.literal("Click to open").formatted(Formatting.DARK_GRAY))),
                    (who, r, s) -> openCase(h, who, id));
        }
        if (open.size() > shown) {
            m.icon(53, Menu.icon(Items.CHEST, "+" + (open.size() - shown) + " more", Formatting.WHITE, "Rule on some to see the rest."));
        }
        if (open.isEmpty()) m.icon(22, Menu.icon(Items.BARRIER, "No open cases", Formatting.GRAY));
        if (!m.open(p)) tell(p, "Could not open the cases menu.", Formatting.RED);
    }

    // ------------------------------------------------------------ one case

    public static void openCase(Honor h, ServerPlayerEntity p, int caseId) {
        if (!checkJudge(h, p)) return;
        Case c = Cases.find(h, caseId);
        if (c == null || !c.status.equals("OPEN")) {
            tell(p, "Case #" + caseId + " is no longer open.", Formatting.YELLOW);
            openList(h, p);
            return;
        }
        Menu m = Menu.create(Text.literal("Case #" + c.id), 3);
        String victim = c.victimName == null || c.victimName.isEmpty() ? "-" : c.victimName;
        m.icon(4, Menu.icon(c.type.equals("APPEAL") ? Items.BOOK : Items.PAPER, Text.literal("Case #" + c.id + " [" + c.type + "]").formatted(Formatting.YELLOW),
                List.of(line("Killer: " + c.killerName), line("Victim: " + victim),
                        line("Provisional: " + Cases.signed(c.provisional) + ", applied: " + Cases.signed(c.applied)),
                        line("First damage: " + c.firstDamager))));
        m.set(10, Menu.icon(Items.LIME_WOOL, "Confirm", Formatting.GREEN, "Keep the honor change as applied."),
                (who, r, s) -> decide(h, who, caseId, "confirm", null));
        m.set(11, Menu.icon(Items.RED_WOOL, "Cancel", Formatting.RED, "Undo the honor change."),
                (who, r, s) -> decide(h, who, caseId, "cancel", null));
        m.set(12, Menu.icon(Items.OAK_SIGN, "Set amount", Formatting.GOLD, "Type the killer's honor change", "(-1000 to 1000) on a sign."),
                (who, r, s) -> askAmount(h, who, caseId));
        m.set(14, Menu.icon(Items.GOLD_INGOT, "Fine", Formatting.GOLD, "Fine a player over this case."),
                (who, r, s) -> {
                    if (!checkJudge(h, who)) return;
                    Case cc = Cases.find(h, caseId);
                    if (cc == null) return;
                    Fines.openFineFor(h, who, cc);
                });
        m.set(16, Menu.icon(Items.BARRIER, "Cancel a fine", Formatting.RED, "List open fines; click one to cancel it."),
                (who, r, s) -> h.fines.openCancelMenu(who));
        m.set(15, Menu.icon(Items.WRITTEN_BOOK, "Give book", Formatting.AQUA, "Get the case book again."),
                (who, r, s) -> {
                    Case cc = Cases.find(h, caseId);
                    if (cc != null && Cases.giveBook(h, cc, who)) tell(who, "Gave you the book for case #" + caseId + ".", Formatting.GREEN);
                    else tell(who, "Could not give the book.", Formatting.RED);
                });
        m.set(22, Menu.icon(Items.ARROW, "Back", Formatting.WHITE), (who, r, s) -> openList(h, who));
        if (!m.open(p)) tell(p, "Could not open the case menu.", Formatting.RED);
    }

    private static void askAmount(Honor h, ServerPlayerEntity p, int caseId) {
        if (!checkJudge(h, p)) return;
        tell(p, "Type the amount on the first line of the sign (for example -30 or 15).", Formatting.YELLOW);
        SignPrompt.ask(p, lines -> {
            OptionalInt a = SignInput.parseAmount(lines);
            if (a.isEmpty()) {
                tell(p, "That is not a number. Use whole numbers like 25 or -40.", Formatting.RED);
                openCase(h, p, caseId);
            } else if (a.getAsInt() < -1000 || a.getAsInt() > 1000) {
                tell(p, "The amount must be between -1000 and 1000.", Formatting.RED);
                openCase(h, p, caseId);
            } else {
                decide(h, p, caseId, "set", a.getAsInt());
            }
        });
    }

    private static void decide(Honor h, ServerPlayerEntity p, int caseId, String action, Integer amount) {
        if (!checkJudge(h, p)) return;
        Case c = Cases.find(h, caseId);
        if (c == null || !c.status.equals("OPEN")) {
            tell(p, "Case #" + caseId + " is no longer open.", Formatting.YELLOW);
            openList(h, p);
            return;
        }
        String e = Cases.decide(h, c, action, amount, null, p.getName().getString());
        if (e != null) {
            tell(p, e, Formatting.RED);
            openCase(h, p, caseId);
            return;
        }
        tell(p, "Case #" + c.id + " " + c.status.toLowerCase() + ". Killer's honor from this case: " + Cases.signed(c.applied) + ".", Formatting.GREEN);
        openList(h, p);
    }
}
