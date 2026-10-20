package com.honorsmp;

import com.honorsmp.logic.Tier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.packet.s2c.play.ScoreboardDisplayS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardObjectiveUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScoreboardScoreUpdateS2CPacket;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.number.BlankNumberFormat;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Per-player right-hand sidebar showing their own tier and honor. Sent as packets from a detached
 * scoreboard, so only that player sees it and the real server scoreboard is untouched.
 */
public final class Sidebar {
    private Sidebar() {}

    private static final String OBJECTIVE = "hsmp_honor";
    private static final Scoreboard DETACHED = new Scoreboard();
    private static final ScoreboardObjective OBJ = new ScoreboardObjective(DETACHED, OBJECTIVE, ScoreboardCriterion.DUMMY,
            Text.literal("Honor").formatted(Formatting.GOLD, Formatting.BOLD), ScoreboardCriterion.RenderType.INTEGER, false,
            BlankNumberFormat.INSTANCE);

    /** Last text sent per player, so packets only go out when something changed. */
    private static final Map<UUID, String> SENT = new HashMap<>();

    public static void update(ServerPlayerEntity p, Tier t, int score) {
        String key = t.name() + ":" + score;
        String prev = SENT.get(p.getUuid());
        if (key.equals(prev)) return;
        var net = p.networkHandler;
        if (prev == null) {
            net.sendPacket(new ScoreboardObjectiveUpdateS2CPacket(OBJ, ScoreboardObjectiveUpdateS2CPacket.ADD_MODE));
            net.sendPacket(new ScoreboardDisplayS2CPacket(ScoreboardDisplaySlot.SIDEBAR, OBJ));
        }
        Formatting c = Honor.colorOf(t.side);
        line(p, "hsmp_l2", 2, Text.literal("Tier: ").formatted(Formatting.WHITE).append(Text.literal(t.display).formatted(c)));
        line(p, "hsmp_l1", 1, Text.literal("Honor: ").formatted(Formatting.WHITE).append(Text.literal(t == Tier.JUDGE ? "Judge" : String.valueOf(score)).formatted(c)));
        SENT.put(p.getUuid(), key);
    }

    private static void line(ServerPlayerEntity p, String holder, int n, Text text) {
        p.networkHandler.sendPacket(new ScoreboardScoreUpdateS2CPacket(holder, OBJECTIVE, n, Optional.of(text),
                Optional.of(BlankNumberFormat.INSTANCE)));
    }

    /** Forget everything sent (reset): next update resends the whole sidebar. */
    public static void forgetAll() {
        SENT.clear();
    }

    /** Join: the client has no sidebar yet, so everything must be sent again. */
    public static void forget(ServerPlayerEntity p) {
        SENT.remove(p.getUuid());
    }
}
