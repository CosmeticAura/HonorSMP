package com.honorsmp.logic;

/** Item 8: what happens when a judge is the victim. Pure so it can be tested. */
public final class JudgeKillRules {
    private JudgeKillRules() {}

    /**
     * @param outcome       honor outcome. A judge counts as a Paragon-class honorable victim; a non-dishonored
     *                      killer gets the normal unprovoked rules (-30, or -60 unarmed, 0 self defense).
     * @param review        true only if the base rules want a review AND another judge exists who can rule
     *                      (the dead judge cannot rule on their own case, so without others no case is made).
     * @param judgeLoot     killer is dishonored and the kill counts: pay the judgeLoot pool.
     * @param announce      send the server-wide chat announcement (same condition as the loot).
     */
    public record Result(KillOutcome outcome, boolean review, boolean judgeLoot, boolean announce) {}

    public static Result evaluate(KillContext ctx, boolean killerDishonored, boolean otherJudgeExists, HonorConfig c) {
        KillOutcome base = KillRules.evaluate(ctx, c);
        boolean review = base.review() && otherJudgeExists;
        KillOutcome o = new KillOutcome(base.delta(), review, base.ipFlag(), base.counted(), base.reason());
        boolean loot = base.counted() && !ctx.victimUnder5MinPlaytime() && killerDishonored;
        return new Result(o, review, loot, loot);
    }
}
