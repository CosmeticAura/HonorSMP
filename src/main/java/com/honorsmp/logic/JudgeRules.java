package com.honorsmp.logic;

/** Pure rules about who may rule on a case. */
public final class JudgeRules {
    private JudgeRules() {}

    /** A judge may not rule on a case where they are the killer or the victim. */
    public static boolean canRule(String judgeUuid, String killerUuid, String victimUuid) {
        if (judgeUuid == null) return true; // console
        return !judgeUuid.equals(killerUuid) && !judgeUuid.equals(victimUuid);
    }

    /**
     * Judges are exempt from every automatic honor change (daily bonus, tax penalty, kills, future fine
     * penalties): any caller that changes honor with source "auto" is skipped. Judges' honor can still be
     * changed by an explicit action of an admin or another judge.
     */
    public static boolean autoChangeAllowed(boolean isJudge, String source) {
        return !(isJudge && "auto".equals(source));
    }
}
