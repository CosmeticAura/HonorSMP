package com.honorsmp;

import com.honorsmp.HonorData.Case;
import com.honorsmp.HonorData.Civ;
import com.honorsmp.HonorData.Hit;
import com.honorsmp.HonorData.LogEntry;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import java.text.SimpleDateFormat;
import java.util.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class Cmds {
    private Cmds() {}

    private static LiteralArgumentBuilder<ServerCommandSource> lit(String n) {
        return LiteralArgumentBuilder.literal(n);
    }

    private static <T> RequiredArgumentBuilder<ServerCommandSource, T> arg(String n, com.mojang.brigadier.arguments.ArgumentType<T> t) {
        return RequiredArgumentBuilder.argument(n, t);
    }

    private static CommandSyntaxException err(String m) {
        return new SimpleCommandExceptionType(Text.literal(m)).create();
    }

    private static Honor h() throws CommandSyntaxException {
        if (Honor.I == null) throw err("HonorSMP is not ready yet.");
        return Honor.I;
    }

    private static ServerPlayerEntity player(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayer();
        if (p == null) throw err("This command can only be used by a player.");
        return p;
    }

    private static boolean isJudge(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayer();
        return p == null || h().isJudge(p);
    }

    private static void needJudge(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        if (!isJudge(c)) throw err("Only judges (players added with /honor judge add) can do that. Ops are not judges by default.");
    }

    private static void needAdmin(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        ServerPlayerEntity p = c.getSource().getPlayer();
        if (p != null && !h().isOp(p)) throw err("Only operators can do that.");
    }

    private static String who(CommandContext<ServerCommandSource> c) {
        ServerPlayerEntity p = c.getSource().getPlayer();
        return p == null ? "console" : p.getName().getString();
    }

    private static void say(CommandContext<ServerCommandSource> c, String s) {
        c.getSource().sendFeedback(() -> Text.literal(s), false);
    }

    private static void say(CommandContext<ServerCommandSource> c, Text t) {
        c.getSource().sendFeedback(() -> t, false);
    }

    private static PlayerRecord target(CommandContext<ServerCommandSource> c, String arg) throws CommandSyntaxException {
        String n = StringArgumentType.getString(c, arg);
        PlayerRecord r = h().byName(n);
        if (r == null) throw err("Unknown player " + n + " (they must have joined this server at least once).");
        return r;
    }

    private static com.mojang.brigadier.suggestion.SuggestionProvider<ServerCommandSource> names() {
        return (ctx, b) -> {
            if (Honor.I != null) {
                for (PlayerRecord r : Honor.I.data.players.values()) {
                    if (r.name != null && r.name.toLowerCase().startsWith(b.getRemainingLowerCase())) b.suggest(r.name);
                }
            }
            return b.buildFuture();
        };
    }

    private static RequiredArgumentBuilder<ServerCommandSource, String> playerArg(String n) {
        return arg(n, StringArgumentType.word()).suggests(names());
    }

    public static void register(CommandDispatcher<ServerCommandSource> d) {
        d.register(honor());
        d.register(civ());
        d.register(bounty());
        d.register(appeal());
    }

    // =============================================================== /honor

    private static LiteralArgumentBuilder<ServerCommandSource> honor() {
        return lit("honor")
                .executes(c -> {
                    showSelf(c);
                    return 1;
                })
                .then(lit("top").executes(c -> {
                    top(c);
                    return 1;
                }))
                .then(lit("tracker").executes(c -> {
                    ServerPlayerEntity p = player(c);
                    if (!h().tier(p).isNobleOrHigher()) throw err("You need to be Noble or higher to get a Bounty Compass.");
                    p.getInventory().offerOrDrop(Compass.make());
                    say(c, "You received a Bounty Compass. It points to the nearest dishonored player with a bounty (overworld only).");
                    return 1;
                }))
                .then(lit("treasury").executes(c -> {
                    needJudge(c);
                    String e = h().treasury.openFor(player(c));
                    if (e != null) throw err(e);
                    return 1;
                }))
                .then(lit("cases").executes(c -> {
                    needJudge(c);
                    if (c.getSource().getPlayer() == null) listCases(c);
                    else CaseMenus.openList(h(), player(c));
                    return 1;
                }).then(lit("text").executes(c -> {
                    needJudge(c);
                    listCases(c);
                    return 1;
                })))
                .then(lit("case").then(arg("id", IntegerArgumentType.integer(1))
                        .executes(c -> {
                            needJudge(c);
                            showCase(c);
                            return 1;
                        })
                        .then(lit("confirm").executes(c -> decide(c, "confirm", null, null)))
                        .then(lit("cancel").executes(c -> decide(c, "cancel", null, null))
                                .then(arg("reason", StringArgumentType.greedyString()).executes(c -> decide(c, "cancel", null, StringArgumentType.getString(c, "reason")))))
                        .then(lit("set").then(arg("amount", IntegerArgumentType.integer(-1000, 1000))
                                .executes(c -> decide(c, "set", IntegerArgumentType.getInteger(c, "amount"), null))
                                .then(arg("reason", StringArgumentType.greedyString())
                                        .executes(c -> decide(c, "set", IntegerArgumentType.getInteger(c, "amount"), StringArgumentType.getString(c, "reason"))))))))
                .then(lit("give").then(playerArg("player").then(arg("amount", IntegerArgumentType.integer(-2000, 2000))
                        .then(arg("reason", StringArgumentType.greedyString()).executes(c -> {
                            needJudge(c);
                            PlayerRecord r = target(c, "player");
                            int amt = IntegerArgumentType.getInteger(c, "amount");
                            String reason = StringArgumentType.getString(c, "reason");
                            int real = h().change(r.uuid, r.name, amt, reason, who(c), null, "", 0);
                            say(c, r.name + " " + (real >= 0 ? "gained " : "lost ") + Math.abs(real) + " honor. Now " + r.score + ".");
                            return 1;
                        })))))
                .then(lit("log").then(playerArg("player").executes(c -> {
                    needJudge(c);
                    PlayerRecord r = target(c, "player");
                    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    say(c, Text.literal("Honor history for " + r.name + " (score " + r.score + ")").formatted(Formatting.GOLD));
                    int n = 0;
                    for (LogEntry e : h().data.logs) {
                        if (!e.player.equals(r.uuid)) continue;
                        n++;
                        say(c, f.format(new Date(e.time)) + "  " + Cases.signed(e.delta) + " -> " + e.newScore + "  " + e.reason
                                + "  [" + e.source + (e.dim.isEmpty() ? "" : " @ " + e.dim + " " + e.x + " " + e.y + " " + e.z) + "]");
                    }
                    if (n == 0) say(c, "No entries.");
                    return 1;
                })))
                .then(lit("set").then(playerArg("player").then(arg("score", IntegerArgumentType.integer(-1000, 1000)).executes(c -> {
                    needAdmin(c);
                    PlayerRecord r = target(c, "player");
                    h().setScore(r.uuid, r.name, IntegerArgumentType.getInteger(c, "score"), "admin:" + who(c));
                    say(c, r.name + " is now at " + r.score + " (" + h().tier(r).display + ").");
                    return 1;
                }))))
                .then(lit("judge")
                        .then(lit("add").then(playerArg("player").executes(c -> {
                            needAdmin(c);
                            PlayerRecord r = target(c, "player");
                            h().data.judges.add(r.uuid);
                            h().markDirty();
                            say(c, r.name + " is now a judge.");
                            return 1;
                        })))
                        .then(lit("remove").then(playerArg("player").executes(c -> {
                            needAdmin(c);
                            PlayerRecord r = target(c, "player");
                            h().data.judges.remove(r.uuid);
                            h().markDirty();
                            say(c, r.name + " is no longer a judge.");
                            return 1;
                        }))))
                .then(lit("season").then(lit("reset").executes(c -> {
                    needAdmin(c);
                    season(c);
                    return 1;
                })))
                .then(lit("reload").executes(c -> {
                    needAdmin(c);
                    String e = h().loadConfig();
                    if (e != null) throw err("Config not reloaded: " + e);
                    say(c, "Reloaded config/honorsmp.json.");
                    return 1;
                }));
    }

    private static void showSelf(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Honor h = h();
        ServerPlayerEntity p = player(c);
        PlayerRecord r = h.record(p);
        Tier t = h.tier(r);
        HonorConfig k = h.config;
        if (t == Tier.JUDGE) {
            say(c, Text.literal("Honor: Judge  ").formatted(Formatting.GOLD).append(Honor.tierText(t)));
            say(c, "Judges are above the honor system: no automatic honor changes. Use /honor cases to see open cases.");
            return;
        }
        say(c, Text.literal("Honor: " + r.score + "  ").formatted(Formatting.YELLOW).append(Honor.tierText(t))
                .append(Text.literal(" (" + Honor.sideName(t.side) + ")").formatted(Formatting.GRAY)));
        int i = t.perkIndex();
        if (t.isHonorable()) {
            say(c, "Perks: potions +" + k.potionBonusPercent[i] + "% longer, villager trades " + k.tradeDiscountPercent[i]
                    + "% cheaper, hunger " + k.hungerSlowPercent[i] + "% slower" + (t.isNobleOrHigher() ? ", Bounty Compass (/honor tracker)" : ""));
        } else if (t.isDishonored()) {
            say(c, "Perks: hidden from tab list, name tag hidden when sneaking, +" + k.attackDamageBonus[i] + " attack damage, +"
                    + k.speedBonusPercent[i] + "% speed, bounty payouts. Downsides: potions -" + k.dishonoredPotionPenaltyPercent
                    + "%, villagers +" + k.dishonoredVillagerSurchargePercent + "%.");
        } else {
            say(c, "Perks: none (reach " + k.honorableMin + " for the honorable side).");
        }
        Civ civ = h.civOf(r.uuid);
        if (civ != null) say(c, "Civilization: " + civ.name);
        List<LogEntry> mine = new ArrayList<>();
        for (int j = h.data.logs.size() - 1; j >= 0 && mine.size() < 10; j--) {
            LogEntry e = h.data.logs.get(j);
            if (e.player.equals(r.uuid) && e.delta != 0) mine.add(e);
        }
        say(c, "Last " + mine.size() + " honor changes:");
        for (LogEntry e : mine) say(c, "  " + Cases.signed(e.delta) + "  " + e.reason);
    }

    private static void top(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Honor h = h();
        List<PlayerRecord> top = Leaderboard.top(h.data.players.values(), r -> r.score, h.config, 10);
        List<PlayerRecord> bottom = Leaderboard.bottom(h.data.players.values(), r -> r.score, h.config, 10);
        say(c, Text.literal("Top 10 (honorable)").formatted(Formatting.GOLD));
        if (top.isEmpty()) say(c, "  nobody yet");
        for (int i = 0; i < top.size(); i++) {
            PlayerRecord r = top.get(i);
            say(c, (i + 1) + ". " + r.name + "  " + r.score + "  " + h.tier(r).display);
        }
        say(c, Text.literal("Bottom 10 (dishonored)").formatted(Formatting.DARK_RED));
        if (bottom.isEmpty()) say(c, "  nobody yet");
        for (int i = 0; i < bottom.size(); i++) {
            PlayerRecord r = bottom.get(i);
            say(c, (i + 1) + ". " + r.name + "  " + r.score + "  " + h.tier(r).display);
        }
    }

    private static void listCases(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        int n = 0;
        for (Case k : h().data.cases) {
            if (!k.status.equals("OPEN")) continue;
            n++;
            say(c, "#" + k.id + " [" + k.type + "] " + k.killerName + " killed " + k.victimName + ", provisional " + Cases.signed(k.provisional));
        }
        if (n == 0) say(c, "No open cases.");
    }

    private static void showCase(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Case k = Cases.find(h(), IntegerArgumentType.getInteger(c, "id"));
        if (k == null) throw err("No such case.");
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        say(c, Text.literal("Case #" + k.id + " [" + k.type + "] " + k.status).formatted(Formatting.GOLD));
        say(c, "Time: " + f.format(new Date(k.time)));
        say(c, "Killer: " + k.killerName + " (score " + k.killerScore + ")  " + k.killerGear);
        say(c, "Victim: " + k.victimName + " (score " + k.victimScore + ")  " + k.victimGear);
        say(c, "Where: " + k.dim + " " + k.x + " " + k.y + " " + k.z + "");
        say(c, "First damage: " + k.firstDamager + (k.firstDamage > 0 ? " (" + String.format("%.1f", k.firstDamage) + ")" : ""));
        say(c, "Provisional: " + Cases.signed(k.provisional) + "  Currently applied to killer: " + Cases.signed(k.applied));
        say(c, "Damage between them in the last " + h().config.caseDamageLogSeconds + "s:");
        if (k.damageLog.isEmpty()) say(c, "  none recorded");
        for (Hit hit : k.damageLog) say(c, String.format("  %.1fs before death: %s hit for %.1f", hit.agoMs / 1000.0, hit.attacker, hit.amount));
        if (!k.note.isEmpty()) say(c, "Note: " + k.note);
        if (!k.decidedBy.isEmpty()) {
            say(c, "Decided by " + k.decidedBy + " at " + f.format(new Date(k.decidedAt)) + (k.decisionReason.isEmpty() ? "" : ": " + k.decisionReason));
        }
    }

    private static int decide(CommandContext<ServerCommandSource> c, String action, Integer amount, String reason) throws CommandSyntaxException {
        needJudge(c);
        Case k = Cases.find(h(), IntegerArgumentType.getInteger(c, "id"));
        if (k == null) throw err("No such case.");
        ServerPlayerEntity jp = c.getSource().getPlayer();
        if (!JudgeRules.canRule(jp == null ? null : jp.getUuid().toString(), k.killer, k.victim)) {
            throw err("You cannot rule on a case where you are the killer or the victim.");
        }
        String e = Cases.decide(h(), k, action, amount, reason, who(c));
        if (e != null) throw err(e);
        say(c, "Case #" + k.id + " " + k.status.toLowerCase() + ". Killer's honor from this case: " + Cases.signed(k.applied) + ".");
        return 1;
    }

    private static void season(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Honor h = h();
        UUID key = c.getSource().getPlayer() == null ? new UUID(0, 0) : c.getSource().getPlayer().getUuid();
        long now = System.currentTimeMillis();
        Long last = h.seasonConfirm.get(key);
        if (last == null || now - last > h.config.seasonResetConfirmSeconds * 1000L) {
            h.seasonConfirm.put(key, now);
            say(c, Text.literal("This sets EVERY player back to 0 (civs stay; bounty items are kept). Run /honor season reset again within "
                    + h.config.seasonResetConfirmSeconds + " seconds to confirm.").formatted(Formatting.RED));
            return;
        }
        h.seasonConfirm.remove(key);
        try {
            var b = h.backupData();
            say(c, "Backed up data to " + b);
        } catch (Exception e) {
            throw err("Backup failed, nothing was reset: " + e.getMessage());
        }
        for (PlayerRecord r : new ArrayList<>(h.data.players.values())) {
            h.change(r.uuid, r.name, -r.score, "Season reset", "admin:" + who(c), null, "", 0);
        }
        h.markDirty();
        h.saveData();
        h.server.getPlayerManager().broadcast(Text.literal("A new season has begun. Everyone's honor is back to 0.").formatted(Formatting.GOLD), false);
    }

    // =============================================================== /appeal

    private static LiteralArgumentBuilder<ServerCommandSource> appeal() {
        return lit("appeal").then(arg("text", StringArgumentType.greedyString()).executes(c -> {
            Honor h = h();
            ServerPlayerEntity p = player(c);
            String me = p.getUuid().toString();
            if (h.isJudge(me)) throw err("Judges have no automatic honor changes to appeal.");
            List<AppealRules.LogView> logs = new ArrayList<>();
            for (LogEntry e : h.data.logs) logs.add(new AppealRules.LogView(e.id, e.player, e.delta));
            List<AppealRules.AppealView> ap = new ArrayList<>();
            for (Case k : h.data.cases) {
                if (k.type.equals("APPEAL")) ap.add(new AppealRules.AppealView(k.killer, k.appealLogId, k.status.equals("OPEN")));
            }
            AppealRules.Parsed parsed = AppealRules.parse(StringArgumentType.getString(c, "text"), id -> {
                for (AppealRules.LogView l : logs) if (l.id() == id && l.player().equals(me) && l.delta() != 0) return true;
                return false;
            });
            if (parsed.reason().isEmpty()) throw err("Usage: /appeal <reason> or /appeal <change id> <reason>");
            AppealRules.Pick pick = AppealRules.pick(me, parsed.logId(), logs, ap);
            if (pick.error() != null) throw err(pick.error());
            LogEntry target = null;
            for (LogEntry e : h.data.logs) if (e.id == pick.logId()) target = e;
            Case k = Cases.openAppeal(h, p, target, parsed.reason());
            say(c, "Appeal #" + k.id + " sent to the judges for honor change #" + target.id + " (" + Cases.signed(target.delta) + ").");
            return 1;
        }));
    }

    // =============================================================== /bounty

    private static LiteralArgumentBuilder<ServerCommandSource> bounty() {
        return lit("bounty")
                .then(lit("set").then(playerArg("player").executes(c -> {
                    Honor h = h();
                    String err = h.bounties.openSet(player(c), target(c, "player"));
                    if (err != null) throw err(err);
                    return 1;
                })))
                .then(lit("claim").executes(c -> {
                    String err = h().bounties.openClaim(player(c));
                    if (err != null) throw err(err);
                    return 1;
                }))
                .then(lit("list").executes(c -> {
                    if (c.getSource().getPlayer() != null) {
                        h().bounties.openList(player(c));
                        return 1;
                    }
                    return bountyText(c);
                }).then(lit("text").executes(c -> bountyText(c))));
    }

    private static int bountyText(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        {
            {
                {
                    Honor h = h();
                    int n = 0;
                    for (Map.Entry<String, java.util.List<net.minecraft.item.ItemStack>> e : h.bounties.view().entrySet()) {
                        long items = h.bounties.itemsOn(e.getKey());
                        if (items <= 0) continue;
                        n++;
                        say(c, h.nameOf(e.getKey()) + ": " + items + " items in " + e.getValue().size() + " stacks");
                    }
                    if (n == 0) say(c, "No active bounties.");
                    return 1;
                }
            }
        }
    }

    // =============================================================== /civ

    private static Civ myCiv(CommandContext<ServerCommandSource> c) throws CommandSyntaxException {
        Civ civ = h().civOf(player(c).getUuid().toString());
        if (civ == null) throw err("You are not in a civilization.");
        return civ;
    }

    private static boolean canManage(Civ civ, String uuid) {
        return civ.leader.equals(uuid) || civ.officers.contains(uuid);
    }

    private static LiteralArgumentBuilder<ServerCommandSource> civ() {
        return lit("civ")
                .then(lit("create").then(arg("name", StringArgumentType.greedyString()).executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    String name = StringArgumentType.getString(c, "name").trim();
                    if (name.isEmpty() || name.length() > h.config.civNameMaxLength) throw err("Names are 1 to " + h.config.civNameMaxLength + " characters.");
                    if (h.civOf(p.getUuid().toString()) != null) throw err("Leave your current civilization first.");
                    if (h.civByName(name) != null) throw err("That name is taken.");
                    Civ civ = new Civ();
                    civ.id = UUID.randomUUID().toString();
                    civ.name = name;
                    civ.leader = p.getUuid().toString();
                    civ.members.add(civ.leader);
                    civ.created = System.currentTimeMillis();
                    Taxes.init(h, civ, civ.created);
                    h.data.civs.put(civ.id, civ);
                    h.markDirty();
                    h.server.getPlayerManager().broadcast(Text.literal(p.getName().getString() + " founded " + name + " (" + Honor.sideName(h.civSide(civ)) + " civilization).")
                            .formatted(Formatting.GOLD), false);
                    return 1;
                })))
                .then(lit("invite").then(playerArg("player").executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    Civ civ = myCiv(c);
                    if (!canManage(civ, p.getUuid().toString())) throw err("Only the leader or officers can invite.");
                    String n = StringArgumentType.getString(c, "player");
                    ServerPlayerEntity t = h.server.getPlayerManager().getPlayer(n);
                    if (t == null) throw err(n + " is not online.");
                    if (civ.members.contains(t.getUuid().toString())) throw err(n + " is already a member of your civilization.");
                    if (h.civOf(t.getUuid().toString()) != null) throw err(n + " is already in a civilization.");
                    h.invites.put(t.getUuid(), new Honor.Invite(civ.id, System.currentTimeMillis() + h.config.inviteExpirySeconds * 1000L, p.getName().getString()));
                    t.sendMessage(Text.literal(p.getName().getString() + " invited you to " + civ.name + ". Type /civ join " + civ.name + " within "
                            + h.config.inviteExpirySeconds + " seconds.").formatted(Formatting.GOLD));
                    say(c, "Invited " + n + ".");
                    return 1;
                })))
                .then(lit("join").then(arg("name", StringArgumentType.greedyString()).executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    if (h.civOf(p.getUuid().toString()) != null) throw err("Leave your current civilization first.");
                    Civ civ = h.civByName(StringArgumentType.getString(c, "name").trim());
                    Honor.Invite inv = h.invites.get(p.getUuid());
                    if (civ == null || inv == null || !inv.civId().equals(civ.id) || inv.expiresMs() < System.currentTimeMillis()) {
                        throw err("You have no valid invite to that civilization.");
                    }
                    h.invites.remove(p.getUuid());
                    civ.members.add(p.getUuid().toString());
                    h.markDirty();
                    say(c, "You joined " + civ.name + ".");
                    broadcastCiv(h, civ, p.getName().getString() + " joined the civilization.");
                    return 1;
                })))
                .then(lit("leave").executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    Civ civ = myCiv(c);
                    String id = p.getUuid().toString();
                    if (civ.leader.equals(id)) throw err("The leader cannot leave. Use /civ disband.");
                    civ.members.remove(id);
                    civ.officers.remove(id);
                    civ.guards.remove(id);
                    h.markDirty();
                    say(c, "You left " + civ.name + ".");
                    broadcastCiv(h, civ, p.getName().getString() + " left the civilization.");
                    return 1;
                }))
                .then(lit("kick").then(playerArg("player").executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    Civ civ = myCiv(c);
                    if (!canManage(civ, p.getUuid().toString())) throw err("Only the leader or officers can kick.");
                    PlayerRecord r = target(c, "player");
                    if (!civ.members.contains(r.uuid)) throw err(r.name + " is not in your civilization.");
                    if (civ.leader.equals(r.uuid)) throw err("You cannot kick the leader.");
                    if (civ.officers.contains(r.uuid) && !civ.leader.equals(p.getUuid().toString())) throw err("Only the leader can kick an officer.");
                    civ.members.remove(r.uuid);
                    civ.officers.remove(r.uuid);
                    civ.guards.remove(r.uuid);
                    h.markDirty();
                    broadcastCiv(h, civ, r.name + " was kicked by " + p.getName().getString() + ".");
                    return 1;
                })))
                .then(lit("promote").then(playerArg("player").executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    Civ civ = myCiv(c);
                    if (!civ.leader.equals(p.getUuid().toString())) throw err("Only the leader can promote.");
                    PlayerRecord r = target(c, "player");
                    if (!civ.members.contains(r.uuid)) throw err(r.name + " is not in your civilization.");
                    if (civ.leader.equals(r.uuid)) throw err("That is you.");
                    if (!civ.officers.add(r.uuid)) {
                        say(c, "Already an officer");
                        return 0;
                    }
                    h.markDirty();
                    broadcastCiv(h, civ, r.name + " is now an officer.");
                    return 1;
                })))
                .then(lit("guard").then(playerArg("player").executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    Civ civ = myCiv(c);
                    if (!canManage(civ, p.getUuid().toString())) throw err("Only the leader or officers can assign guards.");
                    PlayerRecord r = target(c, "player");
                    if (!civ.members.contains(r.uuid)) throw err(r.name + " is not in your civilization.");
                    boolean added = civ.guards.add(r.uuid);
                    if (!added) civ.guards.remove(r.uuid);
                    h.markDirty();
                    say(c, added ? r.name + " was not a guard, so they are now a guard." : r.name + " was already a guard, so they are no longer a guard.");
                    broadcastCiv(h, civ, r.name + (added ? " is now a guard." : " is no longer a guard."));
                    return 1;
                })))
                .then(lit("tax")
                        .executes(c -> {
                            say(c, Taxes.status(h(), myCiv(c)));
                            return 1;
                        })
                        .then(lit("pay").executes(c -> {
                            Honor h = h();
                            Civ civ = myCiv(c);
                            String e = Taxes.pay(h, civ, player(c));
                            if (e != null) throw err(e);
                            say(c, "Tax paid. " + Taxes.status(h, civ));
                            broadcastCiv(h, civ, who(c) + " paid the civ tax to the judges.");
                            return 1;
                        })))
                .then(lit("disband").executes(c -> {
                    Honor h = h();
                    ServerPlayerEntity p = player(c);
                    Civ civ = myCiv(c);
                    if (!civ.leader.equals(p.getUuid().toString())) throw err("Only the leader can disband.");
                    h.data.civs.remove(civ.id);
                    h.markDirty();
                    h.server.getPlayerManager().broadcast(Text.literal(civ.name + " has been disbanded.").formatted(Formatting.GOLD), false);
                    return 1;
                }))
                .then(lit("info")
                        .executes(c -> {
                            info(c, myCiv(c));
                            return 1;
                        })
                        .then(arg("name", StringArgumentType.greedyString()).executes(c -> {
                            Civ civ = h().civByName(StringArgumentType.getString(c, "name").trim());
                            if (civ == null) throw err("No civilization with that name.");
                            info(c, civ);
                            return 1;
                        })))
                .then(lit("list").executes(c -> {
                    Honor h = h();
                    if (h.data.civs.isEmpty()) say(c, "No civilizations yet.");
                    for (Civ civ : h.data.civs.values()) {
                        say(c, civ.name + "  (" + Honor.sideName(h.civSide(civ)) + ", " + civ.members.size() + " members)  leader "
                                + h.nameOf(civ.leader) + (Taxes.isLate(h, civ) ? "  [Unpaid]" : ""));
                    }
                    return 1;
                }));
    }

    private static void info(CommandContext<ServerCommandSource> c, Civ civ) throws CommandSyntaxException {
        Honor h = h();
        say(c, Text.literal(civ.name + " (" + Honor.sideName(h.civSide(civ)) + ")" + (Taxes.isLate(h, civ) ? " [Unpaid]" : "")).formatted(Honor.colorOf(h.civSide(civ))));
        say(c, Taxes.status(h, civ));
        say(c, "Leader: " + h.nameOf(civ.leader));
        List<String> off = new ArrayList<>(), gu = new ArrayList<>(), mem = new ArrayList<>();
        for (String id : civ.officers) off.add(h.nameOf(id));
        for (String id : civ.guards) gu.add(h.nameOf(id));
        for (String id : civ.members) mem.add(h.nameOf(id));
        say(c, "Officers: " + (off.isEmpty() ? "none" : String.join(", ", off)));
        say(c, "Guards: " + (gu.isEmpty() ? "none" : String.join(", ", gu)));
        say(c, "Members (" + mem.size() + "): " + String.join(", ", mem));
    }

    private static void broadcastCiv(Honor h, Civ civ, String msg) {
        for (String id : civ.members) {
            ServerPlayerEntity m = h.online(id);
            if (m != null) m.sendMessage(Text.literal("[" + civ.name + "] " + msg).formatted(Formatting.GOLD));
        }
    }
}
