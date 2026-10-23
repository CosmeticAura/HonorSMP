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
    public int[] potionBonusPercent = {20, 35, 50};
    public int[] tradeDiscountPercent = {10, 20, 30};
    public int[] hungerSlowPercent = {15, 25, 35};
    /** Extra max health in HP (2 = one heart) for Honorable / Noble / Paragon. */
    public int[] healthBonusHp = {2, 4, 6};
    /** Lowest honorable rank (1 Honorable, 2 Noble, 3 Paragon) that keeps XP / hotbar+offhand on death / has a gold name. 0 = nobody. */
    public int keepXpMinRank = 2;
    public int keepHotbarMinRank = 3;
    public int goldNameMinRank = 3;
    /** 0 = file written before 1.6.0: the old honorable perk defaults get replaced once on load. */
    public int perksVersion = 0;
    public double[] attackDamageBonus = {0.5, 1.0, 1.5};
    public int[] speedBonusPercent = {3, 5, 7};
    public int dishonoredPotionPenaltyPercent = 20;
    public int dishonoredVillagerSurchargePercent = 20;

    // civilizations
    public int civNameMaxLength = 24;
    public int inviteExpirySeconds = 120;

    // civ upgrades (1.6.0) and their daily upkeep (1.6.5). Arrays are indexed by level - 1 (level 1, level 2).
    public boolean civUpgradesEnabled = true;
    /** Upgrade id (resistance, strength, speed, haste, fortitude) to the item its upkeep and purchase are paid in. Never emeralds. */
    public java.util.Map<String, String> civUpgradeItems = defaultUpgradeItems();
    /** Upgrade id to items per day for [level 1, level 2]. */
    public java.util.Map<String, int[]> civUpkeepPerDay = defaultUpkeepPerDay();
    /** Buying a level costs this many days of that level's upkeep (in the upgrade's item). */
    public int civBuyDays = 4;
    /** Extra diamonds for level 2. */
    public int civLevel2Diamonds = 8;
    /** The leader and officers are warned this many hours before the upkeep is due. */
    public int civUpkeepWarningHours = 2;
    /** Upkeep can be paid at most this many days ahead. */
    public int civUpkeepMaxAheadDays = 3;
    /** Damage members take less, in percent. */
    public int[] upgradeResistancePercent = {5, 10};
    /** Extra attack damage. */
    public double[] upgradeStrengthBonus = {0.5, 1.0};
    /** Extra movement speed, in percent. */
    public int[] upgradeSpeedPercent = {5, 10};
    /** Haste level shown to players (1 = Haste I, 2 = Haste II). */
    public int[] upgradeHasteLevel = {1, 2};
    /** Extra max health in HP (2 = one heart). Adds to the rank health perk. */
    public int[] upgradeFortitudeHp = {2, 4};

    public static java.util.Map<String, String> defaultUpgradeItems() {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        m.put("resistance", "minecraft:iron_ingot");
        m.put("strength", "minecraft:gunpowder");
        m.put("speed", "minecraft:redstone");
        m.put("haste", "minecraft:lapis_lazuli");
        m.put("fortitude", "minecraft:gold_ingot");
        return m;
    }

    public static java.util.Map<String, int[]> defaultUpkeepPerDay() {
        java.util.Map<String, int[]> m = new java.util.LinkedHashMap<>();
        m.put("resistance", new int[] {16, 32});
        m.put("strength", new int[] {16, 32});
        m.put("speed", new int[] {32, 64});
        m.put("haste", new int[] {16, 32});
        m.put("fortitude", new int[] {8, 16});
        return m;
    }

    // donor perks (1.6.0): tiers by lifetime honor earned from donating. Judges get none.
    public int donorSupporterMin = 200;
    public int donorPatronMin = 750;
    public int donorBenefactorMin = 2000;
    /** Supporter and up: hunger drains this much slower, added to the rank perk. */
    public int donorHungerSlowPercent = 20;
    /** Rank + donor hunger slowdown never goes above this (it never lowers the rank perk itself). */
    public int donorHungerCapPercent = 50;
    /** Patron and up: extra Luck attribute. */
    public double donorLuckBonus = 1.0;
    /** Benefactor: Regeneration I for this many seconds on every respawn. */
    public int donorRespawnRegenSeconds = 30;
    /** Weekly top donor (Monday 00:00 server time): Hero of the Village I for this many days. */
    public boolean topDonorEnabled = true;
    public int topDonorHeroDays = 7;

    // live Discord leaderboard (1.6.3)
    /** Discord webhook for the live leaderboard message. Empty = off. Never logged. */
    public String leaderboardWebhookUrl = "";
    public int leaderboardUpdateMinutes = 5;

    // trade cooldown (1.6.4): trades never lock, except for the items below
    /** false = vanilla trade locking for everything. */
    public boolean noTradeCooldown = true;
    /**
     * Trades whose sell item (what the villager gives) is listed here lock and restock like vanilla. Item ids, or
     * "#tag" ids. Enchanted tools/weapons/armor and anything /donate accepts are covered automatically as well.
     */
    public java.util.List<String> tradeCooldownItems = TradeCooldownRules.defaultBlocklist();
    /** Never limited, wins over tradeCooldownItems and the automatic rules. */
    public java.util.List<String> tradeCooldownAlwaysFree = TradeCooldownRules.defaultAlwaysFree();

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
    public int honorPerHungerPoints = 10;
    public int donateDailyCap = 100;
    /**
     * Honor per day each donation type can earn on top of donateDailyCap (reset at midnight). Keys: food (all food
     * combined), or the item id without "minecraft:". Past a cap, items are still accepted and shared, just no honor.
     * A type missing from the map has no per-type cap; a negative value also means no cap. A file that lists only some
     * of the default types gets the others filled in with their defaults on load (set -1 to turn one off).
     */
    public java.util.Map<String, Integer> donateItemDailyCaps = defaultItemDailyCaps();
    /** 0 = file written before the 1.5.0 donation rewards (old 20 / 25 defaults get replaced once on load). */
    public int donateRewardsVersion = 0;

    public static java.util.Map<String, Integer> defaultItemDailyCaps() {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        m.put("food", 60);
        m.put("golden_apple", 20);
        m.put("enchanted_golden_apple", 30);
        m.put("totem_of_undying", 32);
        m.put("ender_pearl", 16);
        m.put(DonateMath.COMMON_MATERIALS, 30);
        m.put(DonateMath.RARE_MATERIALS, 40);
        return m;
    }

    /** 0 = file written before 1.6.0: missing default ingots, ores and blocks are added once to donateExtraItems / donateCapTypes. */
    public int donateMaterialsVersion = 0;
    /**
     * Item id to the cap type its honor counts toward (see donateItemDailyCaps). Checked before the food and
     * bare-id rules. Ore blocks sit under the type of the item they drop.
     */
    public java.util.Map<String, String> donateCapTypes = defaultMaterialCapTypes();

    private static void mat(java.util.Map<String, Integer> pts, java.util.Map<String, String> types, String type, int points, String... ids) {
        for (String id : ids) {
            pts.put("minecraft:" + id, points);
            if (types != null) types.put("minecraft:" + id, type);
        }
    }

    private static void materials(java.util.Map<String, Integer> pts, java.util.Map<String, String> types) {
        String c = DonateMath.COMMON_MATERIALS;
        String r = DonateMath.RARE_MATERIALS;
        mat(pts, types, c, 1, "coal", "coal_ore", "deepslate_coal_ore");
        mat(pts, types, c, 9, "coal_block");
        mat(pts, types, c, 1, "raw_copper", "copper_ingot", "copper_ore", "deepslate_copper_ore");
        mat(pts, types, c, 9, "copper_block", "raw_copper_block");
        mat(pts, types, c, 5, "raw_iron", "iron_ingot", "iron_ore", "deepslate_iron_ore");
        mat(pts, types, c, 45, "iron_block", "raw_iron_block");
        mat(pts, types, c, 5, "raw_gold", "gold_ingot", "gold_ore", "deepslate_gold_ore", "nether_gold_ore");
        mat(pts, types, c, 45, "gold_block", "raw_gold_block");
        mat(pts, types, c, 1, "redstone", "redstone_ore", "deepslate_redstone_ore");
        mat(pts, types, c, 9, "redstone_block");
        mat(pts, types, c, 2, "lapis_lazuli", "lapis_ore", "deepslate_lapis_ore");
        mat(pts, types, c, 18, "lapis_block");
        mat(pts, types, c, 1, "quartz", "nether_quartz_ore");
        mat(pts, types, c, 4, "quartz_block");
        mat(pts, types, c, 1, "amethyst_shard");
        mat(pts, types, r, 50, "diamond", "diamond_ore", "deepslate_diamond_ore");
        mat(pts, types, r, 450, "diamond_block");
        mat(pts, types, r, 150, "netherite_scrap", "ancient_debris");
        mat(pts, types, r, 600, "netherite_ingot");
        mat(pts, types, r, 5400, "netherite_block");
    }

    public static java.util.Map<String, Integer> defaultMaterialPoints() {
        java.util.Map<String, Integer> m = new java.util.LinkedHashMap<>();
        materials(m, null);
        return m;
    }

    public static java.util.Map<String, String> defaultMaterialCapTypes() {
        java.util.Map<String, String> t = new java.util.LinkedHashMap<>();
        materials(new java.util.LinkedHashMap<>(), t);
        return t;
    }

    /**
     * Run after loading config/honorsmp.json. Once (perksVersion 0 to 1): old honorable perk arrays still at the 1.5.0 defaults become the 1.6.0 ones. Once (donateRewardsVersion 0 to 1): honorPerHungerPoints 20 becomes 10
     * and donateDailyCap 25 becomes 100, each only if it still has the old value. Always: a missing/null caps map
     * becomes the defaults and default types missing from a user map are added (user values win).
     * Returns true when anything changed (the caller re-saves the file). Running it again changes nothing.
     */
    private static final String[] EMERALD_ITEMS = {"minecraft:emerald", "minecraft:emerald_block", "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore"};

    public boolean migrate() {
        boolean changed = false;
        if (donateRewardsVersion < 1) {
            if (honorPerHungerPoints == 20) honorPerHungerPoints = 10;
            if (donateDailyCap == 25) donateDailyCap = 100;
            donateRewardsVersion = 1;
            changed = true;
        }
        if (perksVersion < 1) {
            if (java.util.Arrays.equals(potionBonusPercent, new int[] {15, 30, 50})) potionBonusPercent = new int[] {20, 35, 50};
            if (java.util.Arrays.equals(tradeDiscountPercent, new int[] {5, 10, 15})) tradeDiscountPercent = new int[] {10, 20, 30};
            if (java.util.Arrays.equals(hungerSlowPercent, new int[] {10, 15, 20})) hungerSlowPercent = new int[] {15, 25, 35};
            if (healthBonusHp == null) healthBonusHp = new int[] {2, 4, 6};
            perksVersion = 1;
            changed = true;
        }
        if (donateMaterialsVersion < 1) {
            // add what is missing, never touch entries the admin already has
            if (donateExtraItems == null) donateExtraItems = defaultExtraItems();
            for (var e : defaultMaterialPoints().entrySet()) donateExtraItems.putIfAbsent(e.getKey(), e.getValue());
            if (donateCapTypes == null) donateCapTypes = new java.util.LinkedHashMap<>();
            for (var e : defaultMaterialCapTypes().entrySet()) donateCapTypes.putIfAbsent(e.getKey(), e.getValue());
            donateMaterialsVersion = 1;
            changed = true;
        }
        if (donateMaterialsVersion < 2) {
            // 1.6.5: emeralds are out of the economy. Take them out of an existing config once; an admin can add them back later.
            if (donateExtraItems != null) {
                for (String id : EMERALD_ITEMS) if (donateExtraItems.remove(id) != null) changed = true;
            }
            if (donateCapTypes != null) {
                for (String id : EMERALD_ITEMS) if (donateCapTypes.remove(id) != null) changed = true;
            }
            donateMaterialsVersion = 2;
            changed = true;
        }
        if (civUpgradeItems == null) {
            civUpgradeItems = defaultUpgradeItems();
            changed = true;
        }
        if (civUpkeepPerDay == null) {
            civUpkeepPerDay = defaultUpkeepPerDay();
            changed = true;
        }
        if (tradeCooldownItems == null) {
            tradeCooldownItems = TradeCooldownRules.defaultBlocklist();
            changed = true;
        }
        if (tradeCooldownAlwaysFree == null) {
            tradeCooldownAlwaysFree = TradeCooldownRules.defaultAlwaysFree();
            changed = true;
        }
        if (donateItemDailyCaps == null) {
            donateItemDailyCaps = defaultItemDailyCaps();
            changed = true;
        } else {
            for (var e : defaultItemDailyCaps().entrySet()) {
                if (!donateItemDailyCaps.containsKey(e.getKey())) {
                    donateItemDailyCaps.put(e.getKey(), e.getValue());
                    changed = true;
                }
            }
        }
        return changed;
    }
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
        materials(m, null);
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
    /** Seconds after being hurt by another player during which /civ chest cannot be opened. 0 turns the lock off. Added in 1.6.6. */
    public int civChestCombatLockSeconds = 300;
    /** Members (leader included) a civ needs before /civ chest works. Below it the chest locks but keeps its items. Added in 1.6.7. */
    public int civChestMinMembers = 3;

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
