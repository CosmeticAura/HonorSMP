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
    /** Item id to the leftover count that did not split evenly yet. Added in 1.4.0. */
    public Map<String, Long> donatePots = new LinkedHashMap<>();
    /** Leader uuid to how many chest items from a disbanded civ were moved to their donation box while offline. Added in 1.5.0. */
    public Map<String, Long> civChestNotices = new LinkedHashMap<>();
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
        if (civChestNotices != null) civChestNotices.clear();
        logs.clear();
        cases.clear();
        treasuryEmeralds = 0;
        treasury.clear();
        pendingBooks.clear();
        fines.clear();
        antiFarm.clear();
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
        public int donateCarry = 0;
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
