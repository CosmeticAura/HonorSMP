package com.honorsmp.logic;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 1.6.7: a civ can only use its chest with at least {@code civChestMinMembers} members (leader included).
 * Pure rules, no Minecraft types. Below the minimum the chest is locked but keeps its items.
 */
public final class CivChestMinMembers {
    private CivChestMinMembers() {}

    /** Distinct people in the civ: leader plus everyone in the officer, member and guard sets. */
    public static int count(String leader, Collection<String> officers, Collection<String> members, Collection<String> guards) {
        Set<String> all = new HashSet<>();
        if (leader != null) all.add(leader);
        if (officers != null) all.addAll(officers);
        if (members != null) all.addAll(members);
        if (guards != null) all.addAll(guards);
        return all.size();
    }

    /** True when the civ is big enough. A minimum of 1 or less never blocks (a civ always has its leader). */
    public static boolean unlocked(int memberCount, int min) {
        return memberCount >= min;
    }

    /** "Your civ needs 3 members to unlock the civ chest (you have 2)." */
    public static String message(int min, int have) {
        return "Your civ needs " + min + " members to unlock the civ chest (you have " + have + ").";
    }

    /** Line for /civ info. */
    public static String infoLine(int memberCount, int min) {
        return unlocked(memberCount, min) ? "Civ chest: unlocked" : "Civ chest: locked, needs " + min + " members";
    }

    /** Message for a viewer whose chest was closed because the civ dropped below the minimum. */
    public static String closedMessage(int min) {
        return "Your civ dropped below " + min + " members, so the civ chest was closed. Its items are kept.";
    }
}
