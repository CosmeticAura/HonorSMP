package com.honorsmp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Everything saved to the world folder (honorsmp/data.json). Plain fields so Gson can read and write it. */
public class HonorData {
    public int version = 1;
    public long nextLogId = 1;
    public int nextCaseId = 1;

    public Map<String, PlayerRecord> players = new LinkedHashMap<>();
    public Map<String, Civ> civs = new LinkedHashMap<>();
    /** "minecraft:overworld|chunkX|chunkZ" to civ id. */
    public Map<String, String> claims = new LinkedHashMap<>();
    /** victim uuid to total emeralds on their head. */
    public Map<String, Integer> bounties = new LinkedHashMap<>();
    public List<LogEntry> logs = new ArrayList<>();
    public List<Case> cases = new ArrayList<>();
    public Set<String> judges = new LinkedHashSet<>();
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
        public int blockPenaltyToday = 0;
        public int containerPenaltyToday = 0;
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
        public String land = "wilderness";
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
    }
}
