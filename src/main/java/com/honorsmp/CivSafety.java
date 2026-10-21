package com.honorsmp;

import com.honorsmp.HonorData.Civ;
import com.honorsmp.logic.FriendlyFire;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Tameable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Game-side glue for civ chat and friendly fire (rules live in logic/). */
public final class CivSafety {
    private CivSafety() {}

    /** Players with civ chat mode on (in memory only). */
    public static final Set<UUID> CHAT_MODE = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------ civ chat

    public static void sendCivChat(Honor h, ServerPlayerEntity p, Civ civ, String msg) {
        Text line = Text.literal("[" + civ.name + "] <" + p.getName().getString() + "> " + msg).formatted(Formatting.AQUA);
        for (String id : civ.members) {
            ServerPlayerEntity m = h.online(id);
            if (m != null) m.sendMessage(line, false);
        }
    }

    /** Fabric ALLOW_CHAT_MESSAGE: true = let the message through as normal chat. */
    public static boolean allowChat(ServerPlayerEntity sender, String content) {
        if (!CHAT_MODE.contains(sender.getUuid())) return true;
        Honor h = Honor.I;
        if (h == null) return true;
        Civ civ = h.civOf(sender.getUuid().toString());
        if (civ == null) {
            CHAT_MODE.remove(sender.getUuid());
            sender.sendMessage(Text.literal("Civ chat turned off: you are not in a civilization anymore.").formatted(Formatting.GOLD), false);
            return true;
        }
        sendCivChat(h, sender, civ, content);
        return false;
    }

    // ------------------------------------------------------------ friendly fire

    /** The player uuid an entity counts as: a player itself, or the owner of a tamed entity. */
    private static String ownerOrSelf(Entity e) {
        if (e == null) return null;
        if (e instanceof ServerPlayerEntity p) return p.getUuid().toString();
        if (e instanceof Tameable t) {
            var ref = t.getOwnerReference();
            if (ref != null) return ref.getUuid().toString();
        }
        return null;
    }

    private static String civId(Honor h, String uuid) {
        if (uuid == null) return null;
        Civ c = h.civOf(uuid);
        return c == null ? null : c.id;
    }

    /** True when this attacker entity may not hurt this victim entity. */
    public static boolean blocked(Entity attacker, Entity victim) {
        Honor h = Honor.I;
        if (h == null || attacker == null || victim == null || attacker == victim) return false;
        String a = ownerOrSelf(attacker);
        String v = ownerOrSelf(victim);
        if (a == null || v == null) return false;
        if (a.equals(v)) return false; // own pet / self: unchanged vanilla behaviour
        return FriendlyFire.blocked(civId(h, a), civId(h, v));
    }

    /** ALLOW_DAMAGE: false = cancel. Covers melee, arrows, tridents, other projectiles, potion damage. */
    public static boolean allowDamage(Entity victim, DamageSource source) {
        return !blocked(source.getAttacker(), victim);
    }

    /** Called from the addStatusEffect mixin: true = cancel the effect (harmful effect from a civ-mate). */
    public static boolean blockEffect(Entity victim, StatusEffectInstance effect, Entity cause) {
        if (cause == null || effect == null) return false;
        if (effect.getEffectType().value().getCategory() != StatusEffectCategory.HARMFUL) return false;
        Entity thrower = cause;
        if (cause instanceof ProjectileEntity pe) thrower = pe.getOwner();
        else if (cause instanceof AreaEffectCloudEntity cloud) thrower = cloud.getOwner();
        return blocked(thrower, victim);
    }
}
