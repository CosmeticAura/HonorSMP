Build a new Minecraft mod called HonorSMP. It is a brand new project, completely separate from RankedSMP. Do not copy, reuse or touch any RankedSMP code or files.

## Basics

- Fabric, Minecraft 1.21.11, Java 21, Fabric API. Server-side only: players must be able to join with a vanilla client.
- Put the project in its own folder: AI Everything/Minecraft/HonorSMP
- Make it a git repo, private GitHub repo HonorSMP on the CosmeticAura account. Commit as Hector. No Co-Authored-By or "Generated with Claude" trailers.
- Everything that is a number below goes in a config file (config/honorsmp.json) so I can tune it without rebuilding. Reload with /honor reload.
- Data (scores, civs, claims, bounties, logs, review queue) saves to the world folder and survives restarts. Save on change and on shutdown.

## The idea

Practically vanilla survival. Every player has an honor score. Honorable players build civilizations and get quality of life perks. Dishonored players are the outlaws and raiders and get stealth, bounties and a combat edge. The mod scores clear cases automatically; anything unclear goes to a human judge.

## Honor score and tiers

Score runs from -1000 to +1000. Everyone starts at 0.

| Score | Tier | Side |
|---|---|---|
| 800 and up | Paragon | Honorable |
| 400 to 799 | Noble | Honorable |
| 100 to 399 | Honorable | Honorable |
| -99 to 99 | Neutral | Neutral |
| -100 to -399 | Outlaw | Dishonored |
| -400 to -799 | Marauder | Dishonored |
| -800 and below | Warlord | Dishonored |

Tier shows as a colored prefix in chat and the tab list (gold for honorable tiers, gray for neutral, dark red for dishonored). Announce in chat when someone changes side.

## Perks

Honorable side (scales by tier, Honorable / Noble / Paragon):
- Potion duration +15% / +30% / +50% for beneficial effects
- Villager trade discount like a small Hero of the Village: 5% / 10% / 15%
- Hunger drains 10% / 15% / 20% slower
- Noble and up can craft or get (with /honor tracker) a Bounty Compass that points to the nearest dishonored player with an active bounty, updates every 5 seconds, only works in the overworld

Dishonored side (scales by tier, Outlaw / Marauder / Warlord):
- Stealth: hidden from the tab list for everyone except their own civ and ops. Name tag hidden while sneaking (use scoreboard team name tag visibility)
- Combat buff: +0.5 / +1 / +1.5 attack damage and +3% / +5% / +7% movement speed (attribute modifiers, removed when they leave the side)
- Bounty payouts: see Bounties below
- Downsides: beneficial potion duration -20%, villagers charge 20% more, cannot sleep in beds inside an honorable civ's claim

Neutral gets nothing extra.

## Civilizations

- /civ create <name>: costs 16 emeralds. The civ is honorable if its leader is honorable, dishonorable if the leader is dishonored, and neutral otherwise. Civ type updates if the leader changes side.
- /civ invite, join, leave, kick, promote (leader can make officers), disband, info, list
- /civ claim: claims the chunk you stand in. Max claims = 9 + 4 per member, capped at 64. /civ unclaim
- Claims do NOT block anything. Anyone can still break, place and open things. Claims only exist so the mod knows whose land it is, for honor scoring and the bed rule.
- /civ guard <player>: leader or officer marks a member as a guard. Guards get the guard bonus below.
- Entering a claim shows an action bar message with the civ name.

## Automatic honor events

The mod decides these on its own. Everything here is logged with time, place and who.

| Event | Honor |
|---|---|
| Kill a dishonored player | +25 |
| Guard kills a dishonored player inside their own civ's land | +35 instead of +25 |
| Kill an honorable or neutral player who hit you first in the last 15 seconds (self defense) | 0, no review |
| Kill an honorable or neutral player who was not holding a weapon and wearing no armor | -60 |
| Kill an honorable or neutral player who did not hit you first | -30 provisional, goes to judge review |
| Break or place a block in another civ's claim (not your civ) | -1 per block, max -50 per day per player |
| Open a container in another civ's claim | -5, max -50 per day |
| Kill a villager or iron golem in a claim | -15 |
| Spend 3 hours online in a day without losing honor | +5 (max once per day) |

Anti farming: kills of the same victim by the same killer only count once every 30 minutes. Kills between two players on the same IP never count and get flagged to the judge. A kill of a player with under 5 minutes playtime since their last death never gives positive honor.

## Judge review

- Every kill marked "goes to judge review" creates a case: ID, killer, victim, both scores, location, whose land, both players' armor and held item, who dealt damage first and how much, and the last 30 seconds of damage between them.
- The provisional honor is applied right away; the judge can confirm, cancel or change it.
- Judges are ops, plus anyone given the permission with /honor judge add <player> (and remove).
- Optional Discord webhook URL in config: when set, every new case and every judge decision posts there.

## Bounties

- When a dishonored player kills an honorable player, they get emeralds from the server: 4 for Honorable, 8 for Noble, 16 for Paragon.
- /bounty set <player> <emeralds>: anyone can put emeralds from their inventory on a dishonored player's head. Minimum 4.
- Whoever kills that player collects the full bounty. Bounties stack.
- /bounty list shows active bounties (stealthed players still appear here, by name only, no location).

## Commands

Players:
- /honor: your score, tier, perks and your last 10 honor changes
- /honor top: top 10 and bottom 10 (dishonored names still show)
- /honor tracker: get a Bounty Compass if Noble or higher
- /civ ... (see above)
- /bounty set, /bounty list

Judges:
- /honor cases: list open cases
- /honor case <id>: full details
- /honor case <id> confirm | cancel | set <amount> [reason]
- /honor give <player> <amount> <reason>: award or remove honor for anything (good deeds, building projects, reports)
- /honor log <player>: full history

Admins:
- /honor set <player> <score>
- /honor judge add|remove <player>
- /honor season reset: everyone back to 0, civs and claims stay, bounties cleared. Ask for confirmation by running it twice within 10 seconds. Back up the data file first.
- /honor reload

## Build order

1. Project setup, config, data storage with save and load
2. Honor score, tiers, chat and tab prefixes, /honor and /honor top
3. Civs and claims
4. Automatic honor events and anti farming
5. Judge review system and judge commands, Discord webhook
6. Perks for both sides
7. Bounties and the Bounty Compass
8. Seasons and admin commands
9. README with install steps and every command, and a CHANGELOG

Write tests for score math, tier lookup, the first-hit rule and anti farming. Build the jar and start a local test server to make sure it loads with no errors before you say it's done.

## How to report back

Only message me when you have a question, hit an error you can't fix, or finish. Each time, send a short report: what's done, what's left, any decisions you made on your own, and where the jar is.
