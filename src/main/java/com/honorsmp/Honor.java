package com.honorsmp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.honorsmp.HonorData.Civ;
import com.honorsmp.HonorData.PlayerRecord;
import com.honorsmp.logic.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.*;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Core state and rules. One instance per running server, reachable as Honor.I. */
public class Honor {
    public static Honor I;
    public static final Logger LOG = LoggerFactory.getLogger("HonorSMP");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public final MinecraftServer server;
    public HonorConfig config = new HonorConfig();
    public HonorData data = new HonorData();
    public final AntiFarm antiFarm = new AntiFarm();
    public final List<DamageEvent> damage = new ArrayList<>();
    public final Map<UUID, Invite> invites = new HashMap<>();
    public final Map<UUID, Long> seasonConfirm = new HashMap<>();
    private final Set<UUID> hiddenBefore = new HashSet<>();
    private boolean dirty = false;
    private int tickCounter = 0;
    private int seconds = 0;
    private Path dataPath;
    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("honorsmp.json");

    public record Invite(String civId, long expiresMs, String from) {}

    public final Treasury treasury = new Treasury(this);
    public final Bounties bounties = new Bounties(this);

    public Honor(MinecraftServer server) {
        this.server = server;
    }

    // ------------------------------------------------------------ storage

    public void start() {
        dataPath = server.getSavePath(WorldSavePath.ROOT).resolve("honorsmp").resolve("data.json");
        loadConfig();
        loadData();
        antiFarm.raw().putAll(data.antiFarm);
        data.antiFarm = antiFarm.raw();
        treasury.load();
        bounties.load();
        saveData();
        LOG.info("HonorSMP loaded: {} players, {} civs, {} open cases", data.players.size(),
                data.civs.size(), data.cases.stream().filter(c -> c.status.equals("OPEN")).count());
    }

    public String loadConfig() {
        try {
            if (Files.exists(configPath)) {
                HonorConfig c = GSON.fromJson(Files.readString(configPath, StandardCharsets.UTF_8), HonorConfig.class);
                if (c == null) throw new IOException("config file is empty");
                config = c;
            }
            // rewrite so new options show up in the file
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config), StandardCharsets.UTF_8);
            return null;
        } catch (Exception e) {
            LOG.error("Could not read config/honorsmp.json, keeping the previous values", e);
            return e.getMessage();
        }
    }

    private void loadData() {
        try {
            if (Files.exists(dataPath)) {
                HonorData d = HonorData.parse(Files.readString(dataPath, StandardCharsets.UTF_8));
                if (d != null) data = d;
            }
        } catch (Exception e) {
            LOG.error("Could not read honorsmp/data.json. Moving it aside so nothing is overwritten.", e);
            try {
                Files.move(dataPath, dataPath.resolveSibling("data.broken-" + System.currentTimeMillis() + ".json"));
            } catch (IOException ignored) {
            }
        }
        if (data.players == null) data.players = new LinkedHashMap<>();
        if (data.civs == null) data.civs = new LinkedHashMap<>();
        if (data.bounties == null) data.bounties = new LinkedHashMap<>();
        if (data.bountyItems == null) data.bountyItems = new LinkedHashMap<>();
        if (data.bountyBoxes == null) data.bountyBoxes = new LinkedHashMap<>();
        if (data.bountyPending == null) data.bountyPending = new LinkedHashMap<>();
        if (data.logs == null) data.logs = new ArrayList<>();
        if (data.cases == null) data.cases = new ArrayList<>();
        if (data.judges == null) data.judges = new LinkedHashSet<>();
        if (data.antiFarm == null) data.antiFarm = new LinkedHashMap<>();
        long loadNow = System.currentTimeMillis();
        for (HonorData.Civ civ : data.civs.values()) Taxes.init(this, civ, loadNow);
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveData() {
        try {
            Files.createDirectories(dataPath.getParent());
            treasury.sync();
            bounties.sync();
            Path tmp = dataPath.resolveSibling("data.json.tmp");
            Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, dataPath, StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException e) {
            LOG.error("Could not save honorsmp/data.json", e);
        }
    }

    public Path backupData() throws IOException {
        saveData();
        Path b = dataPath.resolveSibling("data-backup-" + System.currentTimeMillis() + ".json");
        Files.copy(dataPath, b, StandardCopyOption.REPLACE_EXISTING);
        return b;
    }

    public void stop() {
        treasury.closeAll();
        bounties.closeAll();
        saveData();
        I = null;
    }

    // ------------------------------------------------------------ players

    public PlayerRecord record(ServerPlayerEntity p) {
        String id = p.getUuid().toString();
        PlayerRecord r = data.players.get(id);
        if (r == null) {
            r = new PlayerRecord();
            r.uuid = id;
            data.players.put(id, r);
            markDirty();
        }
        r.name = p.getName().getString();
        return r;
    }

    public PlayerRecord byName(String name) {
        for (PlayerRecord r : data.players.values()) {
            if (r.name != null && r.name.equalsIgnoreCase(name)) return r;
        }
        return null;
    }

    public ServerPlayerEntity online(String uuid) {
        try {
            return server.getPlayerManager().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public int score(ServerPlayerEntity p) {
        return record(p).score;
    }

    public Tier tier(ServerPlayerEntity p) {
        return Tier.of(record(p).score, config);
    }

    public Tier tier(PlayerRecord r) {
        return Tier.of(r.score, config);
    }

    public static Formatting colorOf(Side s) {
        return switch (s) {
            case HONORABLE -> Formatting.GOLD;
            case NEUTRAL -> Formatting.GRAY;
            case DISHONORED -> Formatting.DARK_RED;
        };
    }

    public static Text tierText(Tier t) {
        return Text.literal(t.display).formatted(colorOf(t.side));
    }

    public boolean isOp(ServerPlayerEntity p) {
        return server.getPlayerManager().isOperator(p.getPlayerConfigEntry());
    }

    public boolean isJudge(ServerPlayerEntity p) {
        return data.judges.contains(p.getUuid().toString());
    }

    public static String dim(World w) {
        return w.getRegistryKey().getValue().toString();
    }

    // ------------------------------------------------------------ honor changes

    /**
     * Adds delta to a player's score (clamped), logs it and announces side changes.
     * Returns the change that was really applied after clamping.
     */
    public int change(String uuid, String name, int delta, String reason, String source, ServerPlayerEntity where,
                      String other, int caseId) {
        PlayerRecord r = data.players.get(uuid);
        if (r == null) {
            r = new PlayerRecord();
            r.uuid = uuid;
            r.name = name;
            data.players.put(uuid, r);
        }
        Tier before = tier(r);
        int old = r.score;
        r.score = ScoreMath.apply(r.score, delta, config);
        int applied = r.score - old;
        if (delta < 0) r.lostHonorToday = true;

        HonorData.LogEntry e = new HonorData.LogEntry();
        e.id = data.nextLogId++;
        e.time = System.currentTimeMillis();
        e.player = uuid;
        e.playerName = r.name;
        e.delta = applied;
        e.newScore = r.score;
        e.reason = reason;
        e.source = source;
        e.other = other == null ? "" : other;
        e.caseId = caseId;
        ServerPlayerEntity sp = where != null ? where : online(uuid);
        if (sp != null) {
            BlockPos bp = sp.getBlockPos();
            e.dim = dim(sp.getEntityWorld());
            e.x = bp.getX();
            e.y = bp.getY();
            e.z = bp.getZ();
        }
        data.logs.add(e);
        while (data.logs.size() > config.maxLogEntries) data.logs.remove(0);
        markDirty();

        Tier after = tier(r);
        if (after.side != before.side) {
            Text msg = Text.literal(r.name + " is now ").formatted(Formatting.YELLOW)
                    .append(tierText(after))
                    .append(Text.literal(" (" + sideName(after.side) + ")").formatted(Formatting.YELLOW));
            server.getPlayerManager().broadcast(msg, false);
        }
        r.lastTier = after.name();
        ServerPlayerEntity live = online(uuid);
        if (live != null) {
            refreshPlayer(live);
            if (applied != 0) {
                live.sendMessage(Text.literal((applied > 0 ? "+" : "") + applied + " honor: " + reason)
                        .formatted(applied > 0 ? Formatting.GREEN : Formatting.RED), true);
            }
        }
        return applied;
    }

    public static String sideName(Side s) {
        return switch (s) {
            case HONORABLE -> "honorable";
            case NEUTRAL -> "neutral";
            case DISHONORED -> "dishonored";
        };
    }

    public void setScore(String uuid, String name, int score, String source) {
        PlayerRecord r = data.players.get(uuid);
        int cur = r == null ? 0 : r.score;
        int target = ScoreMath.clamp(score, config);
        change(uuid, name, target - cur, "Score set to " + target, source, null, "", 0);
    }

    // ------------------------------------------------------------ teams (prefix, name tag) and perks

    private String teamName(Tier t, boolean hiddenTag) {
        return "hsmp_" + t.name().toLowerCase() + (hiddenTag ? "_h" : "");
    }

    private Team ensureTeam(Tier t, boolean hiddenTag) {
        Scoreboard sb = server.getScoreboard();
        String n = teamName(t, hiddenTag);
        Team team = sb.getTeam(n);
        if (team == null) team = sb.addTeam(n);
        Formatting c = colorOf(t.side);
        team.setColor(c);
        team.setPrefix(t == Tier.NEUTRAL ? Text.literal("[Neutral] ").formatted(c)
                : Text.literal("[" + t.display + "] ").formatted(c));
        team.setNameTagVisibilityRule(hiddenTag ? AbstractTeam.VisibilityRule.NEVER : AbstractTeam.VisibilityRule.ALWAYS);
        return team;
    }

    /** Puts the player in the right prefix team, applies attribute perks. Cheap enough to call every second. */
    public void refreshPlayer(ServerPlayerEntity p) {
        PlayerRecord r = record(p);
        Tier t = tier(r);
        boolean hide = t.isDishonored() && p.isSneaking();
        Team want = ensureTeam(t, hide);
        Scoreboard sb = server.getScoreboard();
        String holder = p.getNameForScoreboard();
        Team cur = sb.getScoreHolderTeam(holder);
        if (cur != want) sb.addScoreHolderToTeam(holder, want);
        r.lastTier = t.name();
        applyAttributes(p, t);
        Sidebar.update(p, t, r.score);
    }

    private static final Identifier DMG_ID = Identifier.of("honorsmp", "combat_damage");
    private static final Identifier SPD_ID = Identifier.of("honorsmp", "combat_speed");

    private void applyAttributes(ServerPlayerEntity p, Tier t) {
        EntityAttributeInstance dmg = p.getAttributeInstance(EntityAttributes.ATTACK_DAMAGE);
        EntityAttributeInstance spd = p.getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (dmg == null || spd == null) return;
        if (!t.isDishonored()) {
            if (dmg.hasModifier(DMG_ID)) dmg.removeModifier(DMG_ID);
            if (spd.hasModifier(SPD_ID)) spd.removeModifier(SPD_ID);
            return;
        }
        int i = t.perkIndex();
        double dv = config.attackDamageBonus[i];
        double sv = config.speedBonusPercent[i] / 100.0;
        EntityAttributeModifier cd = dmg.getModifier(DMG_ID);
        if (cd == null || cd.value() != dv) {
            dmg.removeModifier(DMG_ID);
            dmg.addTemporaryModifier(new EntityAttributeModifier(DMG_ID, dv, EntityAttributeModifier.Operation.ADD_VALUE));
        }
        EntityAttributeModifier cs = spd.getModifier(SPD_ID);
        if (cs == null || cs.value() != sv) {
            spd.removeModifier(SPD_ID);
            spd.addTemporaryModifier(new EntityAttributeModifier(SPD_ID, sv, EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    public void clearAttributes(ServerPlayerEntity p) {
        applyAttributes(p, Tier.NEUTRAL);
    }

    // perks used by mixins -------------------------------------------------

    public StatusEffectInstance scaleEffect(ServerPlayerEntity p, StatusEffectInstance e) {
        if (!e.getEffectType().value().isBeneficial() || e.isInfinite()) return e;
        Tier t = tier(p);
        double f;
        if (t.isHonorable()) f = 1.0 + config.potionBonusPercent[t.perkIndex()] / 100.0;
        else if (t.isDishonored()) f = 1.0 - config.dishonoredPotionPenaltyPercent / 100.0;
        else return e;
        int d = Math.max(1, (int) Math.round(e.getDuration() * f));
        if (d == e.getDuration()) return e;
        return new StatusEffectInstance(e.getEffectType(), d, e.getAmplifier(), e.isAmbient(), e.shouldShowParticles(), e.shouldShowIcon());
    }

    public float hungerFactor(ServerPlayerEntity p) {
        Tier t = tier(p);
        if (!t.isHonorable()) return 1.0f;
        return 1.0f - config.hungerSlowPercent[t.perkIndex()] / 100.0f;
    }

    /** Change to a villager trade's special price for this player, in items. Negative is cheaper. */
    public int priceChange(ServerPlayerEntity p, int baseCount) {
        Tier t = tier(p);
        if (t.isHonorable()) {
            double v = baseCount * config.tradeDiscountPercent[t.perkIndex()] / 100.0;
            int off = (int) Math.round(v);
            if (off == 0 && baseCount >= 2 && v > 0.2) off = 1;
            return -off;
        }
        if (t.isDishonored()) {
            int up = (int) Math.ceil(baseCount * config.dishonoredVillagerSurchargePercent / 100.0);
            return Math.max(up, 1);
        }
        return 0;
    }

    // ------------------------------------------------------------ civs

    public Civ civOf(String uuid) {
        for (Civ c : data.civs.values()) {
            if (c.members.contains(uuid)) return c;
        }
        return null;
    }

    public Civ civByName(String name) {
        for (Civ c : data.civs.values()) {
            if (c.name.equalsIgnoreCase(name)) return c;
        }
        return null;
    }

    public Side civSide(Civ c) {
        PlayerRecord leader = data.players.get(c.leader);
        return leader == null ? Side.NEUTRAL : tier(leader).side;
    }

    public String nameOf(String uuid) {
        PlayerRecord r = data.players.get(uuid);
        return r == null || r.name == null ? uuid.substring(0, 8) : r.name;
    }

    // ------------------------------------------------------------ tick

    public void tick() {
        if (dirty && tickCounter % 20 == 0) saveData();
        // sneak toggles the name tag for dishonored players, so check that every tick
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            Tier t = tier(p);
            if (t.isDishonored()) {
                boolean hide = p.isSneaking();
                Scoreboard sb = server.getScoreboard();
                Team cur = sb.getScoreHolderTeam(p.getNameForScoreboard());
                String wantName = teamName(t, hide);
                if (cur == null || !cur.getName().equals(wantName)) refreshPlayer(p);
            }
        }
        if (++tickCounter % 20 != 0) return;
        seconds++;
        secondTick();
    }

    private void secondTick() {
        String today = LocalDate.now().toString();
        long now = System.currentTimeMillis();
        invites.values().removeIf(i -> i.expiresMs() < now);
        if (seconds % 600 == 0) antiFarm.prune(now, config.sameVictimCooldownMinutes * 60_000L);
        damage.removeIf(d -> now - d.timeMs() > 120_000L);

        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            PlayerRecord r = record(p);
            if (!today.equals(r.dayKey)) {
                r.dayKey = today;
                r.onlineSecondsToday = 0;
                r.lostHonorToday = false;
                r.onlineBonusGiven = false;
                markDirty();
            }
            r.onlineSecondsToday++;
            r.secondsSinceDeath++;
            r.lastSeen = now;
            if (!r.onlineBonusGiven && !r.lostHonorToday && r.onlineSecondsToday >= config.onlineBonusHours * 3600L) {
                r.onlineBonusGiven = true;
                change(r.uuid, r.name, config.onlineBonusHonor, config.onlineBonusHours + " hours online without losing honor", "auto", p, "", 0);
            }
            if (seconds % 5 == 0) refreshPlayer(p);
        }
        if (seconds % 60 == 0) Taxes.tick(this);
        if (seconds % Math.max(1, config.compassUpdateSeconds) == 0) Compass.updateAll(this);
        if (seconds % Math.max(1, config.tabRefreshSeconds) == 0) updateTabVisibility();
    }

    // ------------------------------------------------------------ stealth tab list

    public void updateTabVisibility() {
        List<ServerPlayerEntity> all = server.getPlayerManager().getPlayerList();
        Set<UUID> hiddenNow = new HashSet<>();
        for (ServerPlayerEntity d : all) {
            if (tier(d).isDishonored()) hiddenNow.add(d.getUuid());
        }
        for (ServerPlayerEntity d : all) {
            boolean stealth = hiddenNow.contains(d.getUuid());
            if (!stealth && !hiddenBefore.contains(d.getUuid())) continue;
            Civ dc = civOf(d.getUuid().toString());
            for (ServerPlayerEntity v : all) {
                if (v == d) continue;
                boolean sees = !stealth || isOp(v) || (dc != null && dc.members.contains(v.getUuid().toString()));
                v.networkHandler.sendPacket(TabPackets.listed(d, sees));
            }
        }
        hiddenBefore.clear();
        hiddenBefore.addAll(hiddenNow);
    }

    // ------------------------------------------------------------ automatic events

    public void onPlayerKill(ServerPlayerEntity killer, ServerPlayerEntity victim) {
        long now = System.currentTimeMillis();
        PlayerRecord kr = record(killer);
        PlayerRecord vr = record(victim);
        kr.lastIp = ipOf(killer);
        vr.lastIp = ipOf(victim);
        String k = kr.uuid, v = vr.uuid;
        Tier vt = tier(vr);
        boolean sameIp = !kr.lastIp.isEmpty() && kr.lastIp.equals(vr.lastIp);
        long cooldown = config.sameVictimCooldownMinutes * 60_000L;
        boolean onCooldown = !antiFarm.wouldCount(k, v, now, cooldown);
        boolean newbie = vr.secondsSinceDeath < config.minVictimPlaytimeMinutes * 60;
        boolean hitFirst = FirstHitRule.victimHitFirst(damage, k, v, now, config.selfDefenseWindowSeconds * 1000L);

        Civ kc = civOf(k);
        boolean guardOwn = kc != null && kc.guards.contains(k);

        String killerGear = Cases.gear(killer);
        String victimGear = Cases.gear(victim);
        KillContext ctx = new KillContext(vt, guardOwn, hitFirst, Cases.unarmedAndUnarmored(victim), sameIp, onCooldown, newbie);
        KillOutcome o = KillRules.evaluate(ctx, config);

        if (o.counted()) antiFarm.tryCount(k, v, now, cooldown);
        vr.secondsSinceDeath = 0;
        markDirty();

        int applied = 0;
        if (o.delta() != 0) {
            applied = change(k, kr.name, o.delta(), o.reason() + " (" + vr.name + ")", "auto", killer, vr.name, 0);
        } else {
            change(k, kr.name, 0, o.reason() + " (" + vr.name + ")", "auto", killer, vr.name, 0);
        }

        if (o.ipFlag()) {
            Cases.open(this, "IP_FLAG", killer, victim, killerGear, victimGear, 0, 0, damage, now,
                    "Kill between players on the same IP. No honor and no bounty were applied.");
        } else if (o.review()) {
            Cases.open(this, "KILL", killer, victim, killerGear, victimGear, o.delta(), applied, damage, now, o.reason());
        }

        if (o.counted() && !newbie) {
            // dishonored killer of an honorable victim: loot pool by victim tier
            if (tier(kr).isDishonored() && vt.isHonorable()) {
                BountyLoot.pay(this, killer, vt.perkIndex());
            }
        }
        // player placed bounty on the victim's head
        if (KillRules.bountyPays(o, newbie)) bounties.payout(killer, kr, vr);
    }

    // ------------------------------------------------------------ helpers

    public static String ipOf(ServerPlayerEntity p) {
        try {
            SocketAddress a = p.networkHandler.getConnectionAddress();
            if (a instanceof InetSocketAddress isa && isa.getAddress() != null) return isa.getAddress().getHostAddress();
        } catch (Exception ignored) {
        }
        return "";
    }

    public static int countEmeralds(ServerPlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.EMERALD)) n += s.getCount();
        }
        return n;
    }

    public static void takeEmeralds(ServerPlayerEntity p, int amount) {
        int left = amount;
        for (int i = 0; i < p.getInventory().size() && left > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isOf(Items.EMERALD)) {
                int t = Math.min(left, s.getCount());
                s.decrement(t);
                left -= t;
            }
        }
    }

    public static void giveEmeralds(ServerPlayerEntity p, int amount) {
        int left = amount;
        while (left > 0) {
            int t = Math.min(64, left);
            p.getInventory().offerOrDrop(new ItemStack(Items.EMERALD, t));
            left -= t;
        }
    }

    public ServerWorld overworld() {
        return server.getOverworld();
    }
}
