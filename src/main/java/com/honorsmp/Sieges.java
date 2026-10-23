package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.HonorConfig;
import com.honorsmp.logic.LootTable;
import com.honorsmp.logic.SiegeFlow;
import com.honorsmp.logic.SiegeHome;
import com.honorsmp.logic.SiegeSchedule;
import com.honorsmp.logic.SiegeWaves;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.raid.RaiderEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.village.raid.Raid;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;

/**
 * Raid sieges (1.5.0): game side. The rules live in logic/ (SiegeHome, SiegeSchedule, SiegeWaves, SiegeLimits,
 * SiegeFlow); this class reads the world, spawns mobs, shows the boss bar and pays out.
 * Sieges are never persisted. Siege mobs carry the command tag "honorsmp_siege:&lt;civId&gt;".
 */
public final class Sieges {
    public static final String TAG = "honorsmp_siege:";
    static final int MAX_ACTIVE = 3;
    private static final String PLAIN_TAG_PREFIX = TAG;

    static final class Siege {
        final String civId;
        final String civName;
        final SiegeHome.Home home;
        final SiegeFlow flow;
        final Set<UUID> mobs = new LinkedHashSet<>();
        final Set<String> credit = new HashSet<>();
        int spawned = 0;
        ServerBossBar bar;

        Siege(String civId, String civName, SiegeHome.Home home, SiegeFlow flow) {
            this.civId = civId;
            this.civName = civName;
            this.home = home;
            this.flow = flow;
        }
    }

    private final Honor h;
    private final Map<String, Siege> active = new LinkedHashMap<>();
    private final List<Entity> pendingDiscard = new ArrayList<>();
    private final java.util.Random rnd = new java.util.Random();

    public Sieges(Honor h) {
        this.h = h;
    }

    // ------------------------------------------------------------ helpers

    private ServerWorld overworld() {
        return h.server.getWorld(World.OVERWORLD);
    }

    private static boolean inOverworld(ServerPlayerEntity p) {
        return p.getEntityWorld().getRegistryKey() == World.OVERWORLD;
    }

    private static double dist(ServerPlayerEntity p, SiegeHome.Home home) {
        return SiegeHome.horizontalTo(home, p.getX(), p.getZ());
    }

    /** Online members of the civ in the overworld within radius (horizontal) of the home. */
    private List<ServerPlayerEntity> membersWithin(Civ civ, SiegeHome.Home home, double radius) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (String id : civ.members) {
            ServerPlayerEntity p = h.online(id);
            if (p != null && inOverworld(p) && dist(p, home) <= radius) out.add(p);
        }
        return out;
    }

    private List<ServerPlayerEntity> onlineMembers(Civ civ) {
        List<ServerPlayerEntity> out = new ArrayList<>();
        for (String id : civ.members) {
            ServerPlayerEntity p = h.online(id);
            if (p != null) out.add(p);
        }
        return out;
    }

    /** The civ's home from the cached respawn points of ALL members (offline ones too), or null. */
    public SiegeHome.Home homeOf(Civ civ) {
        List<SiegeHome.Point> pts = new ArrayList<>();
        if (h.data.respawnPoints == null) return null;
        for (String id : civ.members) {
            HonorData.RespawnPoint rp = h.data.respawnPoints.get(id);
            if (rp != null && rp.dim != null) pts.add(new SiegeHome.Point(id, rp.dim, rp.x, rp.y, rp.z));
        }
        return SiegeHome.find(pts, h.config.siegeHomeRadius);
    }

    public boolean isActive(Civ civ) {
        return active.containsKey(civ.id);
    }

    /** True while the civ's siege is in a wave or between waves (not during the warning countdown). */
    public boolean inWave(Civ civ) {
        Siege s = active.get(civ.id);
        return s != null && s.flow.running();
    }

    private void tellCiv(Civ civ, Text t) {
        for (ServerPlayerEntity p : onlineMembers(civ)) p.sendMessage(t);
    }

    private static Text civMsg(String civName, String msg, Formatting f) {
        return Text.literal("[" + civName + "] " + msg).formatted(f);
    }

    // ------------------------------------------------------------ respawn points (home source)

    /**
     * Every 5 s the online players' respawn points are compared with the cached ones and the cache (persisted in
     * data.json) is updated, so offline members' beds still count. The home itself is recomputed from the cache
     * whenever it is needed (join, leave, kick and bed changes need no extra hooks).
     */
    private void pollRespawnPoints() {
        if (h.data.respawnPoints == null) h.data.respawnPoints = new LinkedHashMap<>();
        boolean changed = false;
        for (ServerPlayerEntity p : h.server.getPlayerManager().getPlayerList()) {
            String id = p.getUuid().toString();
            ServerPlayerEntity.Respawn r = p.getRespawn();
            if (r == null) {
                if (h.data.respawnPoints.remove(id) != null) changed = true;
                continue;
            }
            BlockPos bp = r.respawnData().getPos();
            String dim = r.respawnData().getDimension().getValue().toString();
            double x = bp.getX() + 0.5, y = bp.getY(), z = bp.getZ() + 0.5;
            HonorData.RespawnPoint old = h.data.respawnPoints.get(id);
            if (old == null || !dim.equals(old.dim) || old.x != x || old.y != y || old.z != z) {
                HonorData.RespawnPoint np = new HonorData.RespawnPoint();
                np.dim = dim;
                np.x = x;
                np.y = y;
                np.z = z;
                h.data.respawnPoints.put(id, np);
                changed = true;
            }
        }
        if (changed) h.markDirty();
    }

    // ------------------------------------------------------------ per-tick / per-second driver

    /** Every server tick: only drains orphans found by the entity-load hook. */
    public void tick() {
        if (pendingDiscard.isEmpty()) return;
        List<Entity> l = new ArrayList<>(pendingDiscard);
        pendingDiscard.clear();
        for (Entity e : l) {
            if (!e.isRemoved()) e.discard();
        }
    }

    /** Once per second. */
    public void second(int seconds) {
        if (seconds % 5 == 0) pollRespawnPoints();
        long now = System.currentTimeMillis();
        if (seconds % 3 == 0) schedule(now);
        for (Siege s : new ArrayList<>(active.values())) {
            try {
                update(s, now, seconds);
            } catch (Exception e) {
                Honor.LOG.error("Siege of {} failed, ending it", s.civName, e);
                end(s, false, null, true);
            }
        }
    }

    private void schedule(long now) {
        if (!h.config.siegesEnabled) return;
        HonorConfig cfg = h.config;
        for (Civ civ : new ArrayList<>(h.data.civs.values())) {
            if (active.containsKey(civ.id)) continue;
            SiegeHome.Home home = homeOf(civ);
            if (home == null) continue;
            if (civ.nextSiegeAt <= 0) {
                civ.nextSiegeAt = SiegeSchedule.ensureScheduled(now, civ.nextSiegeAt, rnd, cfg.siegeMinDays, cfg.siegeMaxDays);
                h.markDirty();
            }
            if (active.size() >= MAX_ACTIVE) continue;
            int near = membersWithin(civ, home, cfg.siegeHomeRadius).size();
            if (SiegeSchedule.shouldStart(now, civ.nextSiegeAt, true, near, cfg.siegeMinMembersOnline)) {
                begin(civ, home, cfg.siegeWarnSeconds, now);
            }
        }
    }

    // ------------------------------------------------------------ start / stop

    private void begin(Civ civ, SiegeHome.Home home, int warnSeconds, long now) {
        SiegeFlow flow = new SiegeFlow(now, warnSeconds, SiegeWaves.WAVES, h.config.siegeWaveDelaySeconds,
                h.config.siegeAbandonSeconds, h.config.siegeMaxMinutes);
        active.put(civ.id, new Siege(civ.id, civ.name, home, flow));
        Text title = Text.literal("Raid incoming!").formatted(Formatting.DARK_RED, Formatting.BOLD);
        Text sub = Text.literal("A raid is coming to " + civ.name + " in " + warnSeconds + " seconds!").formatted(Formatting.RED);
        for (ServerPlayerEntity p : onlineMembers(civ)) {
            p.networkHandler.sendPacket(new TitleFadeS2CPacket(10, 80, 20));
            p.networkHandler.sendPacket(new SubtitleS2CPacket(sub));
            p.networkHandler.sendPacket(new TitleS2CPacket(title));
            p.sendMessage(sub);
        }
        h.server.getPlayerManager().broadcast(
                Text.literal("Pillagers are marching on " + civ.name + "!").formatted(Formatting.DARK_RED), false);
    }

    /**
     * Judge start: needs a home and enough members near it; warning shortened to 5 s.
     * @return an error text, or null when the siege has been started
     */
    public String startNow(Civ civ) {
        if (active.containsKey(civ.id)) return civ.name + " is already being sieged.";
        if (active.size() >= MAX_ACTIVE) return "Too many sieges are running at once (" + MAX_ACTIVE + "). Wait for one to end.";
        SiegeHome.Home home = homeOf(civ);
        HonorConfig cfg = h.config;
        if (home == null) {
            return civ.name + " has no home yet: at least 2 members need a bed (respawn point) in the overworld within "
                    + cfg.siegeHomeRadius + " blocks of each other.";
        }
        int near = membersWithin(civ, home, cfg.siegeHomeRadius).size();
        if (near < cfg.siegeMinMembersOnline) {
            return "Not enough members near the home of " + civ.name + " (" + (int) home.x() + ", " + (int) home.y() + ", "
                    + (int) home.z() + "): " + near + " online within " + cfg.siegeHomeRadius + " blocks, "
                    + cfg.siegeMinMembersOnline + " needed.";
        }
        begin(civ, home, 5, System.currentTimeMillis());
        return null;
    }

    /** Judge stop: ends as a loss, no reward and no message to the civ. @return false if none was running */
    public boolean stop(Civ civ) {
        Siege s = active.get(civ.id);
        if (s == null) return false;
        s.flow.stop();
        end(s, false, null, true);
        return true;
    }

    /** Server stopping: discard every siege mob, nothing is saved or announced. */
    public void stopAll() {
        for (Siege s : new ArrayList<>(active.values())) {
            s.flow.stop();
            end(s, false, null, true);
        }
        tick();
    }

    // ------------------------------------------------------------ the siege itself

    private void update(Siege s, long now, int seconds) {
        Civ civ = h.data.civs.get(s.civId);
        if (civ == null) { // disbanded
            end(s, false, null, true);
            return;
        }
        HonorConfig cfg = h.config;
        if (seconds % 3 == 0) {
            SiegeHome.Home now0 = homeOf(civ);
            if (now0 == null || SiegeHome.horizontalTo(now0, s.home.x(), s.home.z()) > cfg.siegeHomeRadius) {
                end(s, false, "the home was lost", false);
                return;
            }
        }
        List<ServerPlayerEntity> near = membersWithin(civ, s.home, cfg.siegeHomeRadius);
        boolean enoughNear = near.size() >= cfg.siegeMinMembersOnline;
        boolean anyNearAbandon = !membersWithin(civ, s.home, cfg.siegeAbandonRadius).isEmpty();
        int left = countAlive(s);

        // warning is over but nobody is home: cancel; the civ stays due and gets a fresh warning later
        if (s.flow.phase() == SiegeFlow.Phase.WARNING && s.flow.warningOver(now) && !enoughNear) {
            active.remove(s.civId);
            return;
        }
        SiegeFlow.Action a = s.flow.tick(now, enoughNear, anyNearAbandon, left);
        switch (a) {
            case START_WAVE:
                if (s.bar == null) {
                    s.bar = new ServerBossBar(Text.literal("Siege of " + s.civName), BossBar.Color.RED, BossBar.Style.PROGRESS);
                }
                spawnWave(s, civ, near.size());
                left = countAlive(s);
                break;
            case WAVE_CLEARED: {
                Text t = civMsg(s.civName, "Wave " + s.flow.wave() + "/" + s.flow.waves() + " cleared! The next wave comes in "
                        + cfg.siegeWaveDelaySeconds + " seconds.", Formatting.GREEN);
                for (ServerPlayerEntity p : near) p.sendMessage(t);
                break;
            }
            case WON:
                end(s, true, null, false);
                return;
            case LOST:
                end(s, false, "nobody defended the home in time", false);
                return;
            default:
                break;
        }
        if (s.flow.running()) {
            updateBar(s, near, left);
            if (seconds % 2 == 0) steer(s);
        }
    }

    private int countAlive(Siege s) {
        ServerWorld w = overworld();
        int alive = 0;
        java.util.Iterator<UUID> it = s.mobs.iterator();
        while (it.hasNext()) {
            Entity e = w == null ? null : w.getEntity(it.next());
            if (e instanceof MobEntity m && m.isAlive() && !m.isRemoved()) alive++;
            else it.remove(); // dead, discarded or unloaded: gone
        }
        return alive;
    }

    private void updateBar(Siege s, List<ServerPlayerEntity> near, int left) {
        if (s.bar == null) return;
        String name = s.flow.phase() == SiegeFlow.Phase.WAVE
                ? "Siege of " + s.civName + ": wave " + s.flow.wave() + "/" + s.flow.waves() + " - " + left + " mobs left"
                : "Siege of " + s.civName + ": wave " + s.flow.wave() + "/" + s.flow.waves() + " cleared";
        s.bar.setName(Text.literal(name));
        float pct = s.flow.phase() == SiegeFlow.Phase.WAVE && s.spawned > 0 ? Math.min(1f, Math.max(0f, left / (float) s.spawned)) : 0f;
        s.bar.setPercent(pct);
        Set<ServerPlayerEntity> want = new HashSet<>(near);
        for (ServerPlayerEntity p : new ArrayList<>(s.bar.getPlayers())) {
            if (!want.contains(p)) s.bar.removePlayer(p);
        }
        for (ServerPlayerEntity p : near) {
            if (!s.bar.getPlayers().contains(p)) s.bar.addPlayer(p);
        }
    }

    /** Idle siege mobs walk toward the home; mobs with a target keep their own goals. */
    private void steer(Siege s) {
        ServerWorld w = overworld();
        if (w == null) return;
        for (UUID id : s.mobs) {
            if (w.getEntity(id) instanceof MobEntity m && m.isAlive() && m.getTarget() == null && m.getNavigation().isIdle()) {
                m.getNavigation().startMovingTo(s.home.x(), s.home.y(), s.home.z(), 1.0);
            }
        }
    }

    // ------------------------------------------------------------ spawning

    private void spawnWave(Siege s, Civ civ, int membersNear) {
        HonorConfig cfg = h.config;
        ServerWorld w = overworld();
        List<SiegeWaves.Spawn> list = SiegeWaves.compose(s.flow.wave(), Math.max(1, membersNear), cfg.siegeMobsPerMember,
                cfg.siegeMaxMobsPerWave);
        int wanted = SiegeWaves.totalCount(list);
        for (SiegeWaves.Spawn sp : list) {
            for (int i = 0; i < sp.count(); i++) spawnOne(s, w, sp.kind());
        }
        s.spawned = s.mobs.size();
        Honor.LOG.info("Siege of {}: wave {} spawned {}/{} mobs", s.civName, s.flow.wave(), s.spawned, wanted);
        Text t = civMsg(s.civName, "Wave " + s.flow.wave() + "/" + s.flow.waves() + ": " + s.spawned + " raiders attack!", Formatting.RED);
        for (ServerPlayerEntity p : membersWithin(civ, s.home, cfg.siegeAbandonRadius)) p.sendMessage(t);
    }

    private void spawnOne(Siege s, ServerWorld w, SiegeWaves.SiegeMob kind) {
        HonorConfig cfg = h.config;
        double lo = Math.max(1, cfg.siegeSpawnMinDistance);
        double hi = Math.max(lo, cfg.siegeSpawnMaxDistance);
        for (int attempt = 0; attempt < 12; attempt++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double d = lo + rnd.nextDouble() * (hi - lo);
            int x = (int) Math.floor(s.home.x() + Math.cos(ang) * d);
            int z = (int) Math.floor(s.home.z() + Math.sin(ang) * d);
            if (!w.getChunkManager().isChunkLoaded(x >> 4, z >> 4)) continue; // loaded chunks only
            int y = w.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos below = feet.down();
            if (y <= w.getBottomY() + 1) continue;
            if (!w.getFluidState(feet).isEmpty() || !w.getFluidState(below).isEmpty()) continue;
            if (!w.getBlockState(feet).getCollisionShape(w, feet).isEmpty()) continue;
            if (!w.getBlockState(below).isSideSolidFullSquare(w, below, Direction.UP)) continue;
            if (spawnAt(s, w, kind, feet)) return;
        }
    }

    private boolean spawnAt(Siege s, ServerWorld w, SiegeWaves.SiegeMob kind, BlockPos pos) {
        EntityType<? extends MobEntity> type;
        switch (kind) {
            case VINDICATOR: type = EntityType.VINDICATOR; break;
            case WITCH: type = EntityType.WITCH; break;
            case EVOKER: type = EntityType.EVOKER; break;
            case RAVAGER: type = EntityType.RAVAGER; break;
            default: type = EntityType.PILLAGER; break; // PILLAGER and CAPTAIN
        }
        MobEntity mob = type.create(w, SpawnReason.EVENT);
        if (mob == null) return false;
        mob.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, rnd.nextFloat() * 360f, 0f);
        mob.initialize(w, w.getLocalDifficulty(pos), SpawnReason.EVENT, null);
        mob.addCommandTag(TAG + s.civId);
        mob.setPersistent(); // never despawns
        if (kind == SiegeWaves.SiegeMob.CAPTAIN) {
            mob.setCustomName(Text.literal("Siege Captain").formatted(Formatting.DARK_RED));
            mob.setCustomNameVisible(true);
            if (mob instanceof net.minecraft.entity.raid.RaiderEntity r) r.setPatrolLeader(false); // no Bad Omen on kill
            // the banner is only worn: the mob is NOT made a patrol leader and the banner never drops
            mob.equipStack(EquipmentSlot.HEAD, Raid.createOminousBanner(w.getRegistryManager().getOrThrow(RegistryKeys.BANNER_PATTERN)));
            mob.setEquipmentDropChance(EquipmentSlot.HEAD, 0f);
            EntityAttributeInstance max = mob.getAttributeInstance(EntityAttributes.MAX_HEALTH);
            if (max != null) max.setBaseValue(Math.max(1.0, h.config.siegeCaptainHealth));
            mob.setHealth(mob.getMaxHealth());
        }
        s.mobs.add(mob.getUuid()); // before spawnEntity so the entity-load hook knows it
        if (!w.spawnEntity(mob)) {
            s.mobs.remove(mob.getUuid());
            return false;
        }
        return true;
    }

    // ------------------------------------------------------------ ending

    private void end(Siege s, boolean win, String reason, boolean quiet) {
        active.remove(s.civId);
        ServerWorld w = overworld();
        for (UUID id : s.mobs) {
            Entity e = w == null ? null : w.getEntity(id);
            if (e != null && !e.isRemoved()) e.discard();
        }
        s.mobs.clear();
        if (s.bar != null) {
            s.bar.clearPlayers();
            s.bar.setVisible(false);
        }
        Civ civ = h.data.civs.get(s.civId);
        if (civ == null) return;
        long now = System.currentTimeMillis();
        civ.lastSiegeAt = now;
        civ.nextSiegeAt = SiegeSchedule.rollAfterEnd(now, rnd, h.config.siegeMinDays, h.config.siegeMaxDays);
        h.markDirty();
        if (win) {
            reward(s, civ);
        } else if (!quiet) {
            tellCiv(civ, civMsg(civ.name, "The raiders withdrew" + (reason == null ? "." : " (" + reason + ")."), Formatting.GRAY));
        }
    }

    private void reward(Siege s, Civ civ) {
        HonorConfig cfg = h.config;
        int paid = 0;
        for (String uuid : s.credit) {
            if (!civ.members.contains(uuid) || h.isJudge(uuid)) continue;
            String name = h.nameOf(uuid);
            h.change(uuid, name, cfg.siegeRewardHonor, "Defended " + civ.name + " against a siege", "siege", null, "", 0);
            paid++;
        }
        String where = placeLoot(s, civ);
        h.server.getPlayerManager().broadcast(
                Text.literal(civ.name + " defended their home!").formatted(Formatting.GOLD, Formatting.BOLD), false);
        tellCiv(civ, civMsg(civ.name, "Siege won! " + paid + " defender" + (paid == 1 ? "" : "s") + " earned +"
                + cfg.siegeRewardHonor + " honor. " + where, Formatting.GREEN));
    }

    private String placeLoot(Siege s, Civ civ) {
        ServerWorld w = overworld();
        LootTable table = h.config.siegeLoot;
        if (table == null || table.entries == null || table.entries.isEmpty()) table = HonorConfig.defaultSiegeLoot();
        List<ItemStack> stacks = BountyLoot.rollStacks(table, w.getRegistryManager(), w.getRandom());
        if (stacks.isEmpty()) return "";
        BlockPos spot = findChestSpot(w, s.home);
        if (spot == null) {
            BlockPos hp = BlockPos.ofFloored(s.home.x(), s.home.y(), s.home.z());
            for (ItemStack st : stacks) ItemScatterer.spawn(w, hp.getX() + 0.5, hp.getY() + 0.5, hp.getZ() + 0.5, st);
            return "The loot was dropped at the home.";
        }
        w.setBlockState(spot, Blocks.CHEST.getDefaultState());
        BlockEntity be = w.getBlockEntity(spot);
        int slot = 0;
        for (ItemStack st : stacks) {
            if (be instanceof ChestBlockEntity ce && slot < ce.size()) ce.setStack(slot++, st);
            else ItemScatterer.spawn(w, spot.getX() + 0.5, spot.getY() + 1.0, spot.getZ() + 0.5, st);
        }
        if (be != null) be.markDirty();
        return "A loot chest appeared at " + spot.getX() + " " + spot.getY() + " " + spot.getZ() + ".";
    }

    private static BlockPos findChestSpot(ServerWorld w, SiegeHome.Home home) {
        BlockPos base = BlockPos.ofFloored(home.x(), home.y(), home.z());
        int[] dys = {0, 1, -1, 2, -2, 3, -3};
        for (int r = 0; r <= 5; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy : dys) {
                        BlockPos p = base.add(dx, dy, dz);
                        if (!w.getChunkManager().isChunkLoaded(p.getX() >> 4, p.getZ() >> 4)) continue;
                        BlockPos below = p.down();
                        if (w.getBlockState(p).isAir() && w.getFluidState(p).isEmpty()
                                && w.getBlockState(below).isSideSolidFullSquare(w, below, Direction.UP)) {
                            return p;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------ hooks from HonorSMP

    /** True for any mob spawned by a siege (used by the ravager mixin). */
    public static boolean isSiegeMob(Entity e) {
        return tagOf(e) != null;
    }

    private static String tagOf(Entity e) {
        for (String t : e.getCommandTags()) {
            if (t.startsWith(PLAIN_TAG_PREFIX)) return t.substring(PLAIN_TAG_PREFIX.length());
        }
        return null;
    }

    /** Damage credit: a player (or the owner of a projectile, which getAttacker already resolves) hurt a siege mob. */
    public void onDamage(LivingEntity victim, DamageSource source) {
        if (active.isEmpty()) return;
        String civId = tagOf(victim);
        if (civId == null) return;
        Siege s = active.get(civId);
        if (s == null) return;
        Entity a = source.getAttacker();
        if (a instanceof net.minecraft.entity.passive.TameableEntity pet && pet.getOwner() instanceof ServerPlayerEntity owner) a = owner;
        if (a instanceof ServerPlayerEntity p) s.credit.add(p.getUuid().toString());
    }

    /** Entity (re)loaded: a tagged mob that no active siege knows is an orphan and is removed. */
    public void onEntityLoad(Entity e) {
        if (tagOf(e) == null) return;
        for (Siege s : active.values()) {
            if (s.mobs.contains(e.getUuid())) return;
        }
        pendingDiscard.add(e);
    }

    /** Server started: remove leftover siege mobs from already loaded chunks. */
    public void sweepAll() {
        for (ServerWorld w : h.server.getWorlds()) {
            List<Entity> orphans = new ArrayList<>();
            for (Entity e : w.iterateEntities()) {
                if (tagOf(e) != null) orphans.add(e);
            }
            for (Entity e : orphans) e.discard();
            if (!orphans.isEmpty()) Honor.LOG.info("Removed {} leftover siege mobs", orphans.size());
        }
    }
}
