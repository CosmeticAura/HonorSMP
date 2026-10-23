package com.honorsmp;

import com.google.gson.JsonElement;
import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.CivChestEscrow;
import com.honorsmp.logic.CivChestLock;
import com.honorsmp.logic.CivChestMinMembers;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryOps;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * /civ chest: one 54-slot chest per civ, usable by every member, by one member at a time.
 *
 * <p>Dupe safety: the rules live in {@link CivChestEscrow}. While a member has it open the live
 * SimpleInventory is the single owner of the stacks (they were moved out of the escrow, not copied),
 * the civ lock keeps every other member out, and the contents go back to the escrow exactly once,
 * guarded by {@link Session#closed}: on normal close, disconnect, death, being kicked or leaving the
 * civ, civ disband (viewer closed first) and server stop. All clicks are plain vanilla container
 * clicks: nothing is duplicated by shift click, number keys, drag or double click because those only
 * move stacks between this inventory and the player's own, and the cursor stack is returned by the
 * vanilla onClosed before we read the inventory.
 *
 * <p>Crash limit: data.json is written when the screen closes and, while a chest is open, at least every
 * 30 seconds (see Honor.tick). After a crash the last saved state wins; stacks moved in the last
 * <=30 seconds before the crash may be duplicated or reverted relative to player inventories,
 * which are saved by the world, never lost.
 */
public final class CivChest {
    private static final int SLOTS = 54;

    private static final class Session {
        final ServerPlayerEntity player;
        final String civId;
        final SimpleInventory inv = new SimpleInventory(SLOTS);
        ScreenHandler handler;
        boolean closed;

        Session(ServerPlayerEntity player, String civId) {
            this.player = player;
            this.civId = civId;
        }
    }

    private final Honor h;
    private final CivChestEscrow<ItemStack> escrow = new CivChestEscrow<>(Bounties.OPS, SLOTS);
    /** civ id to the open session. */
    private final Map<String, Session> open = new HashMap<>();

    public CivChest(Honor h) {
        this.h = h;
    }

    private RegistryOps<JsonElement> ops() {
        return RegistryOps.of(JsonOps.INSTANCE, h.server.getRegistryManager());
    }

    // ------------------------------------------------------------ storage

    public void load() {
        for (Civ civ : h.data.civs.values()) {
            if (civ.chest == null) continue;
            for (JsonElement je : civ.chest) {
                try {
                    ItemStack s = ItemStack.CODEC.parse(ops(), je).getOrThrow();
                    if (!s.isEmpty()) escrow.load(civ.id, s);
                } catch (Exception ex) {
                    Honor.LOG.error("Skipping unreadable civ chest stack: {}", je, ex);
                }
            }
        }
    }

    /** Writes stored stacks plus the live open screens into the civ records. Called from saveData. */
    public void sync() {
        for (Civ civ : h.data.civs.values()) {
            List<ItemStack> all = new ArrayList<>();
            Session s = open.get(civ.id);
            if (s != null && !s.closed) {
                for (int i = 0; i < SLOTS; i++) if (!s.inv.getStack(i).isEmpty()) all.add(s.inv.getStack(i));
            }
            all.addAll(escrow.stored(civ.id));
            List<JsonElement> out = new ArrayList<>();
            for (ItemStack st : all) {
                try {
                    out.add(ItemStack.CODEC.encodeStart(ops(), st).getOrThrow());
                } catch (Exception ex) {
                    Honor.LOG.error("Could not save a civ chest stack: {}", st, ex);
                }
            }
            civ.chest = out.isEmpty() ? null : out;
        }
    }

    public boolean anyOpen() {
        return !open.isEmpty();
    }

    // ------------------------------------------------------------ open

    /** Returns an error message, or null when the chest opened. */
    public String open(ServerPlayerEntity p) {
        String uuid = p.getUuid().toString();
        Civ civ = h.civOf(uuid);
        if (civ == null) return "You are not in a civilization. Create one with /civ create <name> or ask a leader to /civ invite you.";
        // 1.6.7: the civ must have enough members
        int have = memberCount(civ);
        if (!CivChestMinMembers.unlocked(have, h.config.civChestMinMembers)) return CivChestMinMembers.message(h.config.civChestMinMembers, have);
        Session cur = open.get(civ.id);
        if (cur != null && !cur.closed) {
            boolean stale = cur.player.isRemoved() || cur.player.currentScreenHandler != cur.handler;
            if (stale) close(cur); // its screen is gone without onClosed having run
            else if (cur.player == p) return "Your civ chest is already open. Press Esc to close it, then type /civ chest again.";
        }
        // 1.6.6: no chest within the combat lock of being hurt by another player, and not during a siege wave
        int lockLeft = h.chestLock.remainingSeconds(uuid, System.currentTimeMillis(), h.config.civChestCombatLockSeconds);
        if (lockLeft > 0) return CivChestLock.message(lockLeft);
        if (h.sieges.inWave(civ)) return CivChestLock.SIEGE_MESSAGE;
        boolean may = CivChestEscrow.mayUse(uuid, civ.leader, civ.officers, civ.members, civ.guards);
        var res = escrow.open(civ.id, uuid, may);
        switch (res.status()) {
            case NOT_MEMBER:
                return "You are not in this civilization.";
            case BUSY:
                return CivChestEscrow.BUSY_MESSAGE;
            default:
        }
        Session s = new Session(p, civ.id);
        for (int i = 0; i < SLOTS; i++) s.inv.setStack(i, i < res.items().size() ? res.items().get(i) : ItemStack.EMPTY);
        open.put(civ.id, s);
        s.handler = ChestUi.open(p, Text.literal(civ.name + " chest"), 6, s.inv, who -> close(s));
        if (s.handler == null) {
            close(s);
            return "Could not open the civ chest. Press Esc to close any other screen and type /civ chest again.";
        }
        h.markDirty();
        return null;
    }

    /** Leader plus distinct members, officers and guards. */
    public static int memberCount(Civ civ) {
        return CivChestMinMembers.count(civ.leader, civ.officers, civ.members, civ.guards);
    }

    /** Whether the civ currently meets {@code civChestMinMembers}. */
    public boolean unlocked(Civ civ) {
        return CivChestMinMembers.unlocked(memberCount(civ), h.config.civChestMinMembers);
    }

    /**
     * Call after someone left or was kicked. If the civ is now below the minimum and anyone still has the chest
     * open, their screen is closed and the stacks go back to the escrow exactly once; the items stay stored.
     */
    public void recheck(Civ civ) {
        if (unlocked(civ)) return;
        Session s = open.get(civ.id);
        if (s == null || s.closed) return;
        s.player.closeHandledScreen();
        close(s);
        s.player.sendMessage(Text.literal(CivChestMinMembers.closedMessage(h.config.civChestMinMembers)).formatted(Formatting.RED));
    }

    // ------------------------------------------------------------ close

    /** Moves what is in the screen back to the escrow, once. */
    private void close(Session s) {
        if (s.closed) return;
        s.closed = true;
        if (open.get(s.civId) == s) open.remove(s.civId);
        List<ItemStack> left = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            ItemStack st = s.inv.getStack(i);
            if (!st.isEmpty()) left.add(st);
            s.inv.setStack(i, ItemStack.EMPTY);
        }
        if (!escrow.close(s.civId, s.player.getUuid().toString(), left)) {
            // should not happen (the lock is ours until here); never lose items if it does
            Honor.LOG.error("Civ chest close without the lock for civ {}; storing {} stacks anyway", s.civId, left.size());
            for (ItemStack st : left) escrow.load(s.civId, st);
        }
        h.markDirty();
        h.saveData();
    }

    /** Closes the player's chest screen if they have it open (disconnect, death, kick, leave). */
    public void closeFor(ServerPlayerEntity p) {
        for (Session s : new ArrayList<>(open.values())) {
            if (s.player.getUuid().equals(p.getUuid())) {
                s.player.closeHandledScreen();
                close(s);
            }
        }
    }

    /**
     * Damage hook. When another player (directly, or through an arrow, potion, firework, TNT, crystal or pet)
     * hurt {@code victim}, starts the combat lock and closes the victim's open chest at once. The stacks go back
     * to the escrow exactly as they were on screen at that moment, so nothing is lost or duplicated.
     */
    public void onHurt(ServerPlayerEntity victim, String attackerPlayer, String attackerOwner, String sourcePlayer, String sourceOwner) {
        if (h.config.civChestCombatLockSeconds <= 0) return;
        if (!h.chestLock.hit(victim.getUuid().toString(), System.currentTimeMillis(), attackerPlayer, attackerOwner, sourcePlayer, sourceOwner)) return;
        for (Session s : new ArrayList<>(open.values())) {
            if (s.player.getUuid().equals(victim.getUuid()) && !s.closed) {
                victim.closeHandledScreen();
                close(s);
                victim.sendMessage(Text.literal(CivChestLock.message(h.config.civChestCombatLockSeconds)).formatted(Formatting.RED));
            }
        }
    }

    /** Like closeFor but by uuid; also tells the player. Used when someone is kicked or leaves the civ. */
    public void evict(String uuid, String reason) {
        for (Session s : new ArrayList<>(open.values())) {
            if (s.player.getUuid().toString().equals(uuid)) {
                s.player.closeHandledScreen();
                close(s);
                s.player.sendMessage(Text.literal(reason).formatted(Formatting.RED));
            }
        }
    }

    // ------------------------------------------------------------ disband

    /**
     * Closes any viewer first, then moves the whole chest into the leader's /donation claim box.
     * Call before the civ is removed from data. The leader is told now if online, else on login.
     */
    public void disband(Civ civ) {
        Session s = open.get(civ.id);
        if (s != null) {
            s.player.closeHandledScreen();
            close(s);
            s.player.sendMessage(Text.literal("The civilization was disbanded.").formatted(Formatting.RED));
        }
        List<ItemStack> items = escrow.disband(civ.id);
        if (items == null) {
            Honor.LOG.error("Civ chest of {} still open at disband; not handing out", civ.name);
            return;
        }
        long n = 0;
        for (ItemStack st : items) {
            n += st.getCount();
            // addToBox merges into the escrow list, which is safe even if the leader has /donation open
            h.donationBox.addToBox(civ.leader, st);
        }
        civ.chest = null;
        if (n <= 0) return;
        ServerPlayerEntity leader = null;
        try {
            leader = h.server.getPlayerManager().getPlayer(UUID.fromString(civ.leader));
        } catch (IllegalArgumentException ignored) {
        }
        if (leader != null) {
            leader.sendMessage(chestNotice(civ.name, n));
        } else {
            h.data.civChestNotices.merge(civ.leader, n, Long::sum);
        }
        h.markDirty();
        h.saveData();
    }

    private static Text chestNotice(String civName, long n) {
        return Text.literal("The " + n + " items from " + civName + "'s chest were moved to your donation box. Use /donation to claim them.")
                .formatted(Formatting.GREEN);
    }

    /** On login: tells a leader whose civ was disbanded while they were offline. */
    public void onLogin(ServerPlayerEntity p) {
        Long n = h.data.civChestNotices.remove(p.getUuid().toString());
        if (n == null || n <= 0) return;
        p.sendMessage(Text.literal("The " + n + " items from your disbanded civilization's chest were moved to your donation box. Use /donation to claim them.")
                .formatted(Formatting.GREEN));
        h.markDirty();
    }

    // ------------------------------------------------------------ lifecycle

    /** Full reset: drop everything WITHOUT handing it out, then close screens. */
    public void wipe() {
        List<ServerPlayerEntity> viewers = new ArrayList<>();
        for (Session s : open.values()) {
            s.closed = true;
            for (int i = 0; i < SLOTS; i++) s.inv.setStack(i, ItemStack.EMPTY);
            viewers.add(s.player);
        }
        open.clear();
        escrow.clear();
        h.data.civChestNotices = new LinkedHashMap<>();
        for (ServerPlayerEntity p : viewers) p.closeHandledScreen();
    }

    public void closeAll() {
        for (Session s : new ArrayList<>(open.values())) {
            s.player.closeHandledScreen();
            close(s);
        }
    }
}
