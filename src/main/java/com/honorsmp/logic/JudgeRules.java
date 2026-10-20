package com.honorsmp.logic;

/** Pure rules about who may rule on a case. */
public final class JudgeRules {
    private JudgeRules() {}

    /** A judge may not rule on a case where they are the killer or the victim. */
    public static boolean canRule(String judgeUuid, String killerUuid, String victimUuid) {
        if (judgeUuid == null) return true; // console
        return !judgeUuid.equals(killerUuid) && !judgeUuid.equals(victimUuid);
    }
}
