# HonorSMP

A server-side Fabric mod for Minecraft 1.21.11. Players join with a vanilla client. Every player has an honor score from -1000 to +1000. Honorable players build civilizations and get quality-of-life perks. Dishonored players get stealth, bounties and a combat edge. Clear cases are scored automatically; unclear kills go to a human judge.

## Install

1. Java 21 or newer, a Fabric server for 1.21.11 (loader 0.19.5 or newer) and Fabric API 0.141.6+1.21.11.
2. Put `honorsmp-1.1.0.jar` and Fabric API in the server's `mods/` folder.
3. Start the server. `config/honorsmp.json` is created with every number below. Edit it and run `/honor reload`.
4. Data is saved in the world folder at `honorsmp/data.json` (on every change, and on shutdown).

Build from source: `./gradlew build` (the jar is `build/libs/honorsmp-1.1.0.jar`).

## Tiers

| Score | Tier | Side |
|---|---|---|
| 800+ | Paragon | Honorable |
| 400 to 799 | Noble | Honorable |
| 100 to 399 | Honorable | Honorable |
| -99 to 99 | Neutral | Neutral |
| -100 to -399 | Outlaw | Dishonored |
| -400 to -799 | Marauder | Dishonored |
| -800 and below | Warlord | Dishonored |

The tier shows as a colored prefix in chat and the tab list (gold, gray, dark red) using scoreboard teams named `hsmp_*`. A chat message announces side changes.

## Perks

Honorable (Honorable / Noble / Paragon): beneficial potions last +15/30/50% longer, villager trades 5/10/15% cheaper, hunger drains 10/15/20% slower, and Noble+ can use the Bounty Compass.

Dishonored (Outlaw / Marauder / Warlord): hidden from the tab list except to their own civ and ops, name tag hidden while sneaking, +0.5/1/1.5 attack damage, +3/5/7% speed, loot pool payouts for killing honorable players. Downsides: beneficial potions -20% and villagers +20%.

## Automatic honor events

| Event | Honor |
|---|---|
| Kill a dishonored player | +25 (+35 if you are a guard), anywhere |
| Kill an honorable/neutral player who hit you first within 15 s | 0, no review |
| Kill an unarmed, unarmored honorable/neutral player | -60 |
| Kill an honorable/neutral player who did not hit first | -30, sent to judge review |
| 3 hours online in a day without losing honor | +5 once per day |
| Civ taxes unpaid | -10 per day late, for every member |

Anti-farming: the same killer/victim pair counts once per 30 minutes. Kills between players on the same IP never count and are flagged as a case. A victim with under 5 minutes of playtime since their last death never gives positive honor.

## Judge review

Questionable kills create a case with scores, location, held item and armor, who hit first and the last 30 seconds of damage between the pair. The provisional honor is applied at once. Judges are picked, not ops: only players added with `/honor judge add` can rule on cases or open the treasury. Ops can run admin commands but cannot rule unless they are also judges. A judge cannot rule on a case where they are the killer or victim. Set `discordWebhookUrl` in the config to post every new case and decision to Discord.

## Civilizations, taxes and the treasury

`/civ create` is free. Each civ pays a weekly tax to the judges: 8 emeralds per member, due every 7 real days from creation (`taxPerMember`, `taxIntervalDays`). `/civ tax` shows what is owed and when it is due. The leader or an officer pays with `/civ tax pay` from their inventory, and can pay early.

If a civ is late, every member loses 10 honor per day late (`taxLatePenaltyHonor`) and the civ shows `[Unpaid]` in `/civ info` and `/civ list`. The leader and officers are warned on login and 24 hours before the due time (`taxWarnHours`).

Paid tax goes into the Treasury. Only judges can open it, with `/honor treasury` (chest UI).

## Bounties

A dishonored player who kills an honorable player gets a loot pool, not emeralds. The number of weighted rolls depends on the victim's tier: Honorable 2, Noble 3, Paragon 4. Pools are set in `bountyLoot` in the config. Defaults:

- Honorable: 8 to 16 emeralds, 8 iron ingots, 1 to 2 golden apples, 8 bottles o' enchanting, 16 arrows, 1 random enchanted book (level 1 to 2)
- Noble: 2 to 4 diamonds, 16 to 32 emeralds, 1 enchanted iron or diamond weapon, 2 to 4 golden apples, 1 random enchanted book (level 2 to 3), 1 stack of 4 ender pearls
- Paragon: 4 to 8 diamonds, 1 netherite scrap, 1 totem of undying, 1 enchanted diamond armor piece, 1 random enchanted book (max level), 1 enchanted golden apple (rare)

Loot goes into the killer's inventory; overflow drops at their feet.

Player bounties use chest UIs. `/bounty set <player>` opens a 27 slot chest. Items you put in become the bounty when you close it; closing it empty cancels. Only dishonored targets are allowed, not yourself or your own civ, and bounties from different players stack. When the target is killed, all bounty items go to the killer's bounty box, opened with `/bounty claim` (take only). `/bounty list` shows targets with item and stack counts, no locations. Items are saved when the UI closes, including on disconnect, death and server stop. Season resets do not clear bounties. Old 1.0.0 emerald bounties convert to emerald stacks.

Bounty Compass: Noble and up get one with `/honor tracker`, or craft it shapeless: 1 compass + 4 emeralds. It points at the nearest dishonored player with a bounty, refreshes every 5 s, and only works in the overworld and for Noble+ holders.

## Commands

Players
- `/honor` score, tier, perks, last 10 changes
- `/honor top` top 10 and bottom 10
- `/honor tracker` get a Bounty Compass (Noble+)
- `/civ create <name>` (free), `invite <player>`, `join <name>`, `leave`, `kick <player>`, `promote <player>`, `guard <player>` (toggle), `tax`, `tax pay` (leader or officer), `disband`, `info [name]`, `list`
- `/bounty set <player>` (chest UI), `/bounty claim`, `/bounty list`

Judges (added with `/honor judge add`)
- `/honor treasury`
- `/honor cases`, `/honor case <id>`, `/honor case <id> confirm | cancel [reason] | set <amount> [reason]`
- `/honor give <player> <amount> <reason>`
- `/honor log <player>`

Admins (ops, no judge powers unless also judges)
- `/honor set <player> <score>`
- `/honor judge add|remove <player>`
- `/honor season reset` (run twice within 10 s; backs up the data file first)
- `/honor reload`

## Notes

- Admin commands check the op list and judge commands check the judge list in code, so no permission mod is needed.
- There is no land claiming. Old claim data in `data.json` is dropped on load.
- Honor tests: `./gradlew test`.
