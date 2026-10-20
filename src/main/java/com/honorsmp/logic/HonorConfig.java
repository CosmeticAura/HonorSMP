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
    public int civCreateEmeralds = 16;
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
    public int[] serverBountyEmeralds = {4, 8, 16};
    public int minBounty = 4;
    public int compassUpdateSeconds = 5;

    // admin
    public int seasonResetConfirmSeconds = 10;
    public int maxLogEntries = 50000;
    public int tabRefreshSeconds = 5;
}
