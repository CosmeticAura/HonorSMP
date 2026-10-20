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
    public List<LogEntry> logs = new ArrayList<>();
    public List<Case> cases = new ArrayList<>();
    public Set<String> judges = new LinkedHashSet<>();
    /** Emeralds paid as civ tax, held for the judges (opened later via /honor treasury). */
    public long treasuryEmeralds = 0;
    /** Treasury contents as ItemStack codec JSON (replaces treasuryEmeralds, which is only read for migration). */
    public List<com.google.gson.JsonElement> treasury = new ArrayList<>();
    /** Judge uuid to case ids whose written book is waiting for them to log in. */
    public Map<String, List<Integer>> pendingBooks = new LinkedHashMap<>();
    /** "killer>victim" to the time of the last counted kill (ms). */
    public Map<String, Long> antiFarm = new LinkedHashMap<>();

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
    }

    public static class Civ {
        public String id;
        public String name;
        public String leader;
        public Set<String> officers = new LinkedHashSet<>();
        public Set<String> members = new LinkedHashSet<>();
        public Set<String> guards = new LinkedHashSet<>();
        public long created;
        /** Epoch ms the current tax cycle is due. 0 = not set yet (old data); Taxes.init fills it in. */
        public long taxDue = 0;
        /** True once the current cycle was paid early (the due date rolls forward when it passes). */
        public boolean taxPaid = false;
        /** Days of late penalty already applied this cycle. */
        public int taxPenaltyDays = 0;
        /** True once the pre-due warning went out this cycle. */
        public boolean taxWarned = false;
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
