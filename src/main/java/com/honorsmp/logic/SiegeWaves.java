package com.honorsmp.logic;

import java.util.ArrayList;
import java.util.List;

/** Wave sizes and composition. */
public final class SiegeWaves {
    public static final int WAVES = 3;

    private SiegeWaves() {
    }

    public enum SiegeMob { PILLAGER, VINDICATOR, WITCH, EVOKER, RAVAGER, CAPTAIN }

    public record Spawn(SiegeMob kind, int count) {
    }

    /** Base mobs per wave: perMember per nearby member, at least 3, at most maxPerWave. */
    public static int baseMobs(int members, int perMember, int maxPerWave) {
        int n = Math.max(0, members) * Math.max(0, perMember);
        return Math.max(1, Math.min(Math.max(3, n), Math.max(1, maxPerWave)));
    }

    /**
     * Wave 1..3 for N nearby members. The pillager/vindicator count (60/40 split) is baseMobs x1.0 / 1.3 / 1.6, capped
     * at maxPerWave; specials (witch, evoker, ravager, captain) come on top of the cap.
     * Wave 2: witches and evokers max(1, N/8) each. Wave 3: ravagers 1 + N/6, and 1 captain.
     */
    public static List<Spawn> compose(int wave, int members, int perMember, int maxPerWave) {
        int w = Math.max(1, Math.min(WAVES, wave));
        int base = baseMobs(members, perMember, maxPerWave);
        double mult = w == 1 ? 1.0 : w == 2 ? 1.3 : 1.6;
        int total = Math.min(Math.max(1, maxPerWave), (int) Math.round(base * mult));
        int pillagers = (int) Math.round(total * 0.6);
        int vindicators = total - pillagers;
        int n = Math.max(0, members);
        List<Spawn> out = new ArrayList<>();
        out.add(new Spawn(SiegeMob.PILLAGER, pillagers));
        out.add(new Spawn(SiegeMob.VINDICATOR, vindicators));
        if (w >= 2) {
            out.add(new Spawn(SiegeMob.WITCH, Math.max(1, n / 8)));
            out.add(new Spawn(SiegeMob.EVOKER, Math.max(1, n / 8)));
        }
        if (w == 3) {
            out.add(new Spawn(SiegeMob.RAVAGER, 1 + n / 6));
            out.add(new Spawn(SiegeMob.CAPTAIN, 1));
        }
        return out;
    }

    public static int totalCount(List<Spawn> spawns) {
        int t = 0;
        for (Spawn s : spawns) {
            t += s.count();
        }
        return t;
    }
}
