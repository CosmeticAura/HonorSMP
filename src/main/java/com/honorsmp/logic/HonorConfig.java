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
    public int taxPerMember = 8;
    public int taxIntervalDays = 7;
    public int taxLatePenaltyHonor = 10;
    public int taxWarnHours = 24;
    public int civNameMaxLength = 24;
    public int inviteExpirySeconds = 120;

    // automatic honor events
    public int killDishonored = 25;
    public int killDishonoredGuard = 35;
    public int killUnarmed = -60;
    public int killNoFirstHit = -30;
    public int selfDefenseWindowSeconds = 15;
    public int onlineBonusHours = 3;
    public int onlineBonusHonor = 5;

    // anti farming
    public int sameVictimCooldownMinutes = 30;
    public int minVictimPlaytimeMinutes = 5;

    // judge review
    public int caseDamageLogSeconds = 30;
    public String discordWebhookUrl = "";

    // bounties
    /** Loot pools for a dishonored player killing an Honorable, Noble or Paragon player (index = victim tier). */
    public LootTable[] bountyLoot = defaultBountyLoot();
    public int minBounty = 4;
    public int compassUpdateSeconds = 5;

    // admin
    public int seasonResetConfirmSeconds = 10;
    public int maxLogEntries = 50000;
    public int tabRefreshSeconds = 5;

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
}
