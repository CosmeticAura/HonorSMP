package com.honorsmp.logic;

/** Every tunable number. Loaded from config/honorsmp.json. Missing fields keep these defaults. */
public class HonorConfig {
    // score range and tier thresholds
    public int minScore = -1000;
    public int maxScore = 1000;
    public int paragonMin = 800;
    public int nobleMin = 400;
    public int honorableMin = 100;
    public int outlawMax = -100;
    public int marauderMax = -400;
    public int warlordMax = -800;

    // perks, indexed [Honorable/Outlaw, Noble/Marauder, Paragon/Warlord]
    public int[] potionBonusPercent = {15, 30, 50};
    public int[] tradeDiscountPercent = {5, 10, 15};
    public int[] hungerSlowPercent = {10, 15, 20};
    public double[] attackDamageBonus = {0.5, 1.0, 1.5};
    public int[] speedBonusPercent = {3, 5, 7};
    public int dishonoredPotionPenaltyPercent = 20;
    public int dishonoredVillagerSurchargePercent = 20;

    // civilizations
    public int civNameMaxLength = 24;
    public int inviteExpirySeconds = 120;

    // automatic honor events
    public int killDishonored = 25;
    public int killDishonoredGuard = 35;
    public int killUnarmed = -60;
    public int killNoFirstHit = -30;
    public int selfDefenseWindowSeconds = 15;
    public int onlineBonusHours = 3;
    public int onlineBonusHonor = 0;

    // automatic events (1.4.0). Daily caps reset at midnight server time. Judges are exempt.
    public int zombieCureHonor = 10;
    public int zombieCureDailyCap = 30;
    public int harvestPerCrops = 64;
    public int harvestHonor = 1;
    public int harvestDailyCap = 10;
    public int breedPerBreeds = 5;
    public int breedHonor = 1;
    public int breedDailyCap = 5;
    public int tradePerTrades = 10;
    public int tradeHonor = 1;
    public int tradeDailyCap = 5;
    public int raidWinHonor = 15;
    public int bossKillHonor = 15;
    public int villagerKillPenalty = 15;
    public int petKillPenalty = 20;

    // donations (/donate): "points" convert to honor, honorPerHungerPoints points = 1 honor
    public int honorPerHungerPoints = 20;
    public int donateDailyCap = 25;
    /** Items refused even though they are food (kept under its 1.3.0 name so old config files still load). */
    public java.util.List<String> donateZeroFoods = new java.util.ArrayList<>(java.util.List.of(
            "minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:poisonous_potato",
            "minecraft:pufferfish", "minecraft:suspicious_stew"));
    /** Cake has no food component; this is its points value. */
    public int donateCakeHunger = 14;
    /** Non-food donatable items (and overrides for food): item id to points per item. Add more here. */
    public java.util.Map<String, Integer> donateExtraItems = defaultExtraItems();

    public static java.util.Map<String, Integer> defaultExtraItems() {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        m.put("minecraft:golden_apple", 40);
        m.put("minecraft:enchanted_golden_apple", 300);
        m.put("minecraft:totem_of_undying", 160);
        m.put("minecraft:ender_pearl", 5);
        return m;
    }

    // anti farming
    public int sameVictimCooldownMinutes = 30;
    public int minVictimPlaytimeMinutes = 5;

    // judge review
    public int caseDamageLogSeconds = 30;
    public String discordWebhookUrl = "";

    // bounties
    /** Loot pools for a dishonored player killing an Honorable, Noble or Paragon player (index = victim tier). */
    public LootTable[] bountyLoot = defaultBountyLoot();
    /** Loot pool for a dishonored player who kills a judge. rolls is configurable. */
    public LootTable judgeLoot = defaultJudgeLoot();
    public int minBounty = 4;
    public int compassUpdateSeconds = 5;

    // fines
    /** Real hours a fine can stay unpaid before each honor penalty. */
    public double fineIntervalHours = 2.5;
    public int fineHonorPenalty = 10;

    // nerfs (apply to everyone)
    /** Highest allowed level per enchantment id. Anything above is lowered to the cap. */
    public java.util.Map<String, Integer> enchantCaps = defaultEnchantCaps();
    /** Seconds after taking player damage during which an elytra cannot open. 0 disables. */
    public int combatElytraSeconds = 30;

    // sieges (1.5.0). Only civs get sieges. Distances are blocks (horizontal for home/abandon checks).
    public boolean siegesEnabled = true;
    public double siegeMinDays = 2.0;
    public double siegeMaxDays = 4.0;
    public int siegeHomeRadius = 100;
    public int siegeMinMembersOnline = 2;
    public int siegeWarnSeconds = 60;
    public int siegeSpawnMinDistance = 40;
    public int siegeSpawnMaxDistance = 60;
    public int siegeMobsPerMember = 3;
    public int siegeMaxMobsPerWave = 60;
    public int siegeWaveDelaySeconds = 20;
    public int siegeAbandonRadius = 150;
    public int siegeAbandonSeconds = 120;
    public int siegeMaxMinutes = 15;
    public int siegeRewardHonor = 10;
    public double siegeCaptainHealth = 60;
    /** Loot chest given to the civ after a won siege. */
    public LootTable siegeLoot = defaultSiegeLoot();

    // admin
    public int seasonResetConfirmSeconds = 10;
    public int maxLogEntries = 50000;
    public int tabRefreshSeconds = 5;

    public static java.util.Map<String, Integer> defaultEnchantCaps() {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        m.put("minecraft:sharpness", 4);
        m.put("minecraft:protection", 3);
        m.put("minecraft:density", 4);
        m.put("minecraft:breach", 3);
        return m;
    }

    public static LootTable[] defaultBountyLoot() {
        return new LootTable[] {
            new LootTable(2, new java.util.ArrayList<>(java.util.List.of(
                LootEntry.item(10, 8, 16, "minecraft:emerald"),
                LootEntry.item(10, 8, 8, "minecraft:iron_ingot"),
                LootEntry.item(6, 1, 2, "minecraft:golden_apple"),
                LootEntry.item(8, 8, 8, "minecraft:experience_bottle"),
                LootEntry.item(8, 16, 16, "minecraft:arrow"),
                LootEntry.book(5, 1, 2, false)))),
            new LootTable(3, new java.util.ArrayList<>(java.util.List.of(
                LootEntry.item(8, 2, 4, "minecraft:diamond"),
                LootEntry.item(10, 16, 32, "minecraft:emerald"),
                LootEntry.gear(6, 15, 30, "minecraft:iron_sword", "minecraft:iron_axe", "minecraft:diamond_sword", "minecraft:diamond_axe"),
                LootEntry.item(8, 2, 4, "minecraft:golden_apple"),
                LootEntry.book(6, 2, 3, false),
                LootEntry.item(5, 4, 4, "minecraft:ender_pearl")))),
            new LootTable(4, new java.util.ArrayList<>(java.util.List.of(
                LootEntry.item(10, 4, 8, "minecraft:diamond"),
                LootEntry.item(6, 1, 1, "minecraft:netherite_scrap"),
                LootEntry.item(4, 1, 1, "minecraft:totem_of_undying"),
                LootEntry.gear(6, 20, 35, "minecraft:diamond_helmet", "minecraft:diamond_chestplate",
                        "minecraft:diamond_leggings", "minecraft:diamond_boots"),
                LootEntry.book(6, 1, 1, true),
                LootEntry.item(1, 1, 1, "minecraft:enchanted_golden_apple"))))
        };
    }

    public static LootTable defaultJudgeLoot() {
        return new LootTable(5, new java.util.ArrayList<>(java.util.List.of(
                LootEntry.item(10, 8, 12, "minecraft:diamond"),
                LootEntry.item(6, 2, 2, "minecraft:netherite_scrap"),
                LootEntry.item(5, 1, 1, "minecraft:totem_of_undying"),
                LootEntry.item(3, 1, 1, "minecraft:enchanted_golden_apple"),
                LootEntry.book(6, 1, 1, true))));
    }

    public static LootTable defaultSiegeLoot() {
        return new LootTable(6, new java.util.ArrayList<>(java.util.List.of(
                LootEntry.item(10, 8, 16, "minecraft:iron_ingot"),
                LootEntry.item(8, 4, 8, "minecraft:gold_ingot"),
                LootEntry.item(10, 8, 16, "minecraft:emerald"),
                LootEntry.item(8, 16, 32, "minecraft:arrow"),
                LootEntry.item(5, 1, 3, "minecraft:golden_apple"),
                LootEntry.book(3, 1, 1, true),
                LootEntry.item(1, 1, 1, "minecraft:totem_of_undying"))));
    }
}
