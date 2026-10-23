package com.honorsmp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything saved to the world folder (honorsmp/data.json). Plain fields so Gson can read and write it. */
public class HonorData {
    private static final com.google.gson.Gson PARSER = new com.google.gson.Gson();

    /** Parses data.json. Unknown fields (e.g. old claims) are ignored, so they are not written back. */
    public static HonorData parse(String json) {
        return PARSER.fromJson(json, HonorData.class);
    }

    /** 1 = 1.0/1.1 data, 2 = 1.2.0 (appeals, pending case books). Missing fields always default on load. */
    public int version = 2;
    public long nextLogId = 1;
    public int nextCaseId = 1;

    public Map<String, PlayerRecord> players = new LinkedHashMap<>();
    public Map<String, Civ> civs = new LinkedHashMap<>();
    /** Old (1.0.0) emerald bounties: victim uuid to emeralds. Only read for migration into bountyItems, then emptied. */
    public Map<String, Integer> bounties = new LinkedHashMap<>();
    /** Target uuid to the bounty ItemStacks (codec JSON) on their head. */
    public Map<String, List<com.google.gson.JsonElement>> bountyItems = new LinkedHashMap<>();
    /** Player uuid to the stacks waiting in their bounty claim box (/bounty claim). */
    public Map<String, List<com.google.gson.JsonElement>> bountyBoxes = new LinkedHashMap<>();
    /** Stacks sitting in an open set-bounty screen at save time; returned to the owner's claim box on load. */
    public Map<String, List<com.google.gson.JsonElement>> bountyPending = new LinkedHashMap<>();
    public Map<String, List<com.google.gson.JsonElement>> donatePending = new LinkedHashMap<>();
    /** Player uuid to the donated stacks (codec JSON) waiting in their /donation claim box. Added in 1.4.0. */
    public Map<String, List<com.google.gson.JsonElement>> donateBoxes = new LinkedHashMap<>();
    /** Old (1.4.0) leftover pot, item id to count. Only read once to hand it out, then emptied (entries that could not be read stay). */
    public Map<String, Long> donatePots = new LinkedHashMap<>();
    /** Item id to the rotation index of its remainder hand-out (index into the sorted recipient uuids). Added in 1.5.0. */
    public Map<String, Integer> donateRotation = new LinkedHashMap<>();
    /** Player uuid to messages waiting for their next login about donation shares (max 20). Added in 1.5.0. */
    public Map<String, List<String>> donateNotices = new LinkedHashMap<>();
    /** Leader uuid to how many chest items from a disbanded civ were moved to their donation box while offline. Added in 1.5.0. */
    public Map<String, Long> civChestNotices = new LinkedHashMap<>();
    /** Leader uuid to items moved to their donation box because civ chest rows were locked (1.6.8). */
    public Map<String, Long> civChestOverflowNotices = new LinkedHashMap<>();
    /** Member uuid to the last known respawn point (bed/anchor). Cached so offline members still count for the siege home. Added in 1.5.0. */
    public Map<String, RespawnPoint> respawnPoints = new LinkedHashMap<>();
    public List<LogEntry> logs = new ArrayList<>();
    public List<Case> cases = new ArrayList<>();
    public Set<String> judges = new LinkedHashSet<>();
    /** Old emerald count for the judges' treasury (migration only; replaced by treasury) (opened later via /honor treasury). */
    public long treasuryEmeralds = 0;
    /** Treasury contents as ItemStack codec JSON (replaces treasuryEmeralds, which is only read for migration). */
    public List<com.google.gson.JsonElement> treasury = new ArrayList<>();
    /** Judge uuid to case ids whose written book is waiting for them to log in. */
    public Map<String, List<Integer>> pendingBooks = new LinkedHashMap<>();
    public int nextFineId = 1;
    /** All fines, open and closed. A missing field (1.1.0 data) means none. */
    public List<Fine> fines = new ArrayList<>();
    /** "killer>victim" to the time of the last counted kill (ms). */
    public Map<String, Long> antiFarm = new LinkedHashMap<>();

    /** True once the 1.6.2 move of donatable treasury stacks into the /donation boxes has run. Missing in older data = false. */
    public boolean treasuryDonationsMoved = false;
    /** True once the 1.6.0 donor backfill (donatedHonorTotal from the honor log) has run. Missing in 1.5.0 data = false. */
    public boolean donorBackfilled = false;
    /** Id of the Discord message the live leaderboard keeps editing (1.6.3). Empty = none posted yet. */
    public String leaderboardMessageId = "";
    /** Monday 00:00 (epoch ms, server time) of the last weekly top donor run. 0 = never. */
    public long lastWeeklyDonorRun = 0;
    /** Current weekly top donor (uuid empty = none), their name, the amount they earned and when their Hero effect ends (epoch ms). */
    public String topDonorUuid = "";
    public String topDonorName = "";
    public int topDonorAmount = 0;
    public long topDonorExpiresAt = 0;
    /** Former top donors who were offline when their week ended: their leftover Hero effect is removed on next login. */
    public Set<String> topDonorClear = new LinkedHashSet<>();

    /** Full wipe for /honor reset all: everything except the judge list. Clears in place (antiFarm is shared with the live AntiFarm). */
    public void wipeAllExceptJudges() {
        players.clear();
        civs.clear();
        bounties.clear();
        bountyItems.clear();
        bountyBoxes.clear();
        bountyPending.clear();
        if (donatePending != null) donatePending.clear();
        if (donateBoxes != null) donateBoxes.clear();
        if (donatePots != null) donatePots.clear();
        if (donateRotation != null) donateRotation.clear();
        if (donateNotices != null) donateNotices.clear();
        if (civChestNotices != null) civChestNotices.clear();
        if (civChestOverflowNotices != null) civChestOverflowNotices.clear();
        logs.clear();
        cases.clear();
        treasuryEmeralds = 0;
        treasury.clear();
        pendingBooks.clear();
        fines.clear();
        antiFarm.clear();
        topDonorUuid = "";
        topDonorName = "";
        topDonorAmount = 0;
        topDonorExpiresAt = 0;
        if (topDonorClear != null) topDonorClear.clear();
        nextLogId = 1;
        nextCaseId = 1;
        nextFineId = 1;
    }

    public static class Fine {
        public int id;
        public String target, targetName, judge, judgeName;
        public int caseId = 0;
        /** The fine's contents as ItemStack codec JSON. */
        public List<com.google.gson.JsonElement> items = new ArrayList<>();
        public long createdAt;
        /** Time of the last honor penalty charged (0 = none yet). */
        public long lastPenaltyAt = 0;
        public int penalties = 0;
        /** OPEN, PAID or CANCELLED */
        public String status = "OPEN";
        public long closedAt = 0;
        public String closedBy = "";
        /** False until the fined player has been told (they are told on next login if offline). */
        public boolean notified = false;
    }

    public static class PlayerRecord {
        public String uuid;
        public String name;
        public int score = 0;
        public String lastIp = "";
        public String lastTier = "NEUTRAL";
        public String dayKey = "";
        public int onlineSecondsToday = 0;
        public boolean lostHonorToday = false;
        public boolean onlineBonusGiven = false;
        public int secondsSinceDeath = 0;
        public long lastSeen = 0;
        public String donateDayKey = "";
        public int donatedHonorToday = 0;
        /** Lifetime honor earned from donating (drives the donor tiers). Added in 1.6.0; backfilled once from the honor log. */
        public long donatedHonorTotal = 0;
        /** Recent donation honor awards for the weekly top donor, newest last; pruned to about 14 days. Added in 1.6.0. */
        public List<DonationAward> donationAwards = new ArrayList<>();
        /** Pre-1.5.0 single leftover-points carry; counted as food and folded into donateCarryTypes on the next donation. */
        public int donateCarry = 0;
        /** Honor earned from donations today per cap type (reset with donatedHonorToday). Added in 1.5.0. */
        public Map<String, Integer> donatedTypeToday = new LinkedHashMap<>();
        /** Leftover points per cap type (only types still under their cap keep any). Added in 1.5.0. */
        public Map<String, Integer> donateCarryTypes = new LinkedHashMap<>();
        // 1.4.0 automatic events. Day-based fields reset when autoDayKey is not today; counts carry over days.
        public String autoDayKey = "";
        public int cureHonorToday = 0;
        public int harvestCount = 0;
        public int harvestHonorToday = 0;
        public int breedCount = 0;
        public int breedHonorToday = 0;
        public int tradeCount = 0;
        public int tradeHonorToday = 0;
        /** Boss types (wither, ender_dragon, warden) already paid today. */
        public java.util.List<String> bossesToday = new java.util.ArrayList<>();
    }

    public static class DonationAward {
        public long time;
        public int amount;

        public DonationAward() {}

        public DonationAward(long time, int amount) {
            this.time = time;
            this.amount = amount;
        }
    }

    public static class RespawnPoint {
        public String dim;
        public double x;
        public double y;
        public double z;
    }

    public static class Civ {
        public String id;
        public String name;
        public String leader;
        public Set<String> officers = new LinkedHashSet<>();
        public Set<String> members = new LinkedHashSet<>();
        public Set<String> guards = new LinkedHashSet<>();
        public long created;
        /** Shared /civ chest contents as ItemStack codec JSON. Added in 1.5.0; null or missing = empty. */
        public List<com.google.gson.JsonElement> chest = null;
        /** Siege schedule (1.5.0), epoch ms. 0 = not scheduled yet (rolled from the first time it is seen). */
        public long nextSiegeAt = 0;
        public long lastSiegeAt = 0;
        /**
         * Civ upgrades (1.6.0): upgrade id (resistance, strength, speed, haste, fortitude) to level 1..2. Null or
         * missing (data written by 1.5.0) = no upgrades. Read it through CivUpgradeApi, which handles null.
         */
        public java.util.Map<String, Integer> upgrades = null;
        /** Epoch ms the daily upgrade upkeep is next due (1.6.5; the old taxDueAt is dropped on load). 0 = no upgrades, nothing due. */
        public long upkeepDueAt = 0;
        /** The upkeepDueAt value the "due soon" warning was already sent for (so it is sent once). 0 = none. */
        public long upkeepWarnedFor = 0;
        /**
         * 1.6.9: each owned upgrade has its own upkeep clock. Upgrade id to epoch ms its upkeep is next due. Null or a
         * missing id = no clock yet (migrated from the old shared upkeepDueAt, which is left untouched and no longer used).
         */
        public java.util.Map<String, Long> upgradeDue = null;
        /** 1.6.9: upgrade id to the due time the "due soon" warning was already sent for. */
        public java.util.Map<String, Long> upgradeWarned = null;
        /** 1.6.9: paused upgrades: upgrade id to the paid time (ms) it still had left when it was paused. Level is kept. */
        public java.util.Map<String, Long> upgradePaused = null;
        /** Civ chest rows bought (1.6.8), 0..5: the chest has 1 + this many open rows. Missing in older data = 0. */
        public int chestRows = 0;
    }

    public static class LogEntry {
        public long id;
        public long time;
        public String player;
        public String playerName;
        public int delta;
        public int newScore;
        public String reason;
        /** "auto", a judge name, or "admin:<name>". */
        public String source;
        public String dim = "";
        public int x, y, z;
        public String other = "";
        public int caseId = 0;
    }

    public static class Hit {
        public long agoMs;
        public String attacker;
        public float amount;
    }

    public static class Case {
        public int id;
        /** Optional judge-given name; empty = none. */
        public String name = "";
        public String type = "KILL";
        public long time;
        public String killer, killerName, victim, victimName;
        public int killerScore, victimScore;
        public String dim = "";
        public int x, y, z;
        public String killerGear = "", victimGear = "";
        public String firstDamager = "none";
        public float firstDamage = 0;
        public List<Hit> damageLog = new ArrayList<>();
        public int provisional;
        public int applied;
        /** OPEN, CONFIRMED, CANCELLED, CHANGED */
        public String status = "OPEN";
        public String decidedBy = "";
        public long decidedAt = 0;
        public String decisionReason = "";
        public String note = "";
        /** APPEAL cases: the honor log entry being appealed (0 = not an appeal) and the player's reason. */
        public long appealLogId = 0;
        public String appealReason = "";
    }
}
