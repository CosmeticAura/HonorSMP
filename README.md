# HonorSMP

A server-side Fabric mod for Minecraft 1.21.11. Players join with a vanilla client. Every player has an honor score from -1000 to +1000. Honorable players build civilizations and get quality-of-life perks. Dishonored players get stealth, bounties and a combat edge. Clear cases are scored automatically; unclear kills go to a human judge.

## Install

1. Java 21 or newer, a Fabric server for 1.21.11 (loader 0.19.5 or newer) and Fabric API 0.141.6+1.21.11.
2. Put `honorsmp-1.0.0.jar` and Fabric API in the server's `mods/` folder.
3. Start the server. `config/honorsmp.json` is created with every number below. Edit it and run `/honor reload`.
4. Data is saved in the world folder at `honorsmp/data.json` (on every change, and on shutdown).

Build from source: `./gradlew build` (the jar is `build/libs/honorsmp-1.0.0.jar`).

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

Dishonored (Outlaw / Marauder / Warlord): hidden from the tab list except to their own civ and ops, name tag hidden while sneaking, +0.5/1/1.5 attack damage, +3/5/7% speed, bounty payouts. Downsides: beneficial potions -20%, villagers +20%, no sleeping in an honorable civ's claim.

## Automatic honor events

| Event | Honor |
|---|---|
| Kill a dishonored player | +25 (+35 for a guard inside their own civ's land) |
| Kill an honorable/neutral player who hit you first within 15 s | 0, no review |
| Kill an unarmed, unarmored honorable/neutral player | -60 |
| Kill an honorable/neutral player who did not hit first | -30, sent to judge review |
| Break/place a block in another civ's claim | -1 each, max -50 per day |
| Open a container in another civ's claim | -5, max -50 per day |
| Kill a villager or iron golem in a claim | -15 |
| 3 hours online in a day without losing honor | +5 once per day |

Anti-farming: the same killer/victim pair counts once per 30 minutes. Kills between players on the same IP never count and are flagged as a case. A victim with under 5 minutes of playtime since their last death never gives positive honor.

## Judge review

Questionable kills create a case with scores, location, land owner, held item and armor, who hit first and the last 30 seconds of damage between the pair. The provisional honor is applied at once. Judges are ops plus anyone added with `/honor judge add`. Set `discordWebhookUrl` in the config to post every new case and decision to Discord.

## Bounties

A dishonored player who kills an honorable player earns 4/8/16 emeralds (Honorable/Noble/Paragon victim). `/bounty set <player> <emeralds>` puts emeralds on a dishonored player (minimum 4, stacks). Whoever kills them collects it.

Bounty Compass: Noble and up get one with `/honor tracker`, or craft it shapeless: 1 compass + 4 emeralds. It points at the nearest dishonored player with a bounty, refreshes every 5 s, and only works in the overworld and for Noble+ holders.

## Commands

Players
- `/honor` score, tier, perks, last 10 changes
- `/honor top` top 10 and bottom 10
- `/honor tracker` get a Bounty Compass (Noble+)
- `/civ create <name>` (16 emeralds), `invite <player>`, `join <name>`, `leave`, `kick <player>`, `promote <player>`, `guard <player>` (toggle), `disband`, `claim`, `unclaim`, `info [name]`, `list`
- `/bounty set <player> <emeralds>`, `/bounty list`

Judges
- `/honor cases`, `/honor case <id>`, `/honor case <id> confirm | cancel [reason] | set <amount> [reason]`
- `/honor give <player> <amount> <reason>`
- `/honor log <player>`

Admins (ops)
- `/honor set <player> <score>`
- `/honor judge add|remove <player>`
- `/honor season reset` (run twice within 10 s; backs up the data file first)
- `/honor reload`

Claims never block anything. They only tell the mod whose land it is. Max claims = 9 + 4 per member, capped at 64.

## Notes

- Judge and admin commands check the op list in code, so no permission mod is needed.
- Honor tests: `./gradlew test`.
