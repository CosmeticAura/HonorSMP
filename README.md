# HonorSMP

A server-side Fabric mod for Minecraft 1.21.11. Players join with a vanilla client. Every player has an honor score from -1000 to +1000. Honorable players build civilizations and get quality-of-life perks. Dishonored players get stealth, bounties and a combat edge. Clear cases are scored automatically; unclear kills go to a human judge.

## Install

1. Java 21 or newer, a Fabric server for 1.21.11 (loader 0.19.5 or newer) and Fabric API 0.141.6+1.21.11.
2. Put `honorsmp-1.6.0.jar` and Fabric API in the server's `mods/` folder.
3. Start the server. `config/honorsmp.json` is created with every number below. Edit it and run `/honor reload`.
4. Data is saved in the world folder at `honorsmp/data.json` (on every change, and on shutdown).

Build from source: `./gradlew build` (the jar is `build/libs/honorsmp-1.6.0.jar`).

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

The Judge is a special tier above Paragon (gold tag in chat and tab). Judges are the players added with `/honor judge add`.

Honor sidebar: every player sees their own tier and honor on the right side of the screen (a per-player sidebar sent by packets, nobody else sees it, the real scoreboard is untouched). Judges see `Judge` instead of a score.

## Perks

Honorable (Honorable / Noble / Paragon): +2/4/6 max health, beneficial potions last +20/35/50% longer, villager trades 10/20/30% cheaper, hunger drains 15/25/35% slower. Noble+ keep XP on death and can use the Bounty Compass. Paragon also keeps the hotbar and offhand on death and has a gold name in chat.

Dishonored (Outlaw / Marauder / Warlord): hidden from the tab list except to their own civ and ops, name tag hidden while sneaking, +0.5/1/1.5 attack damage, +3/5/7% speed, loot pool payouts for killing honorable players. Downsides: beneficial potions -20% and villagers +20%.

## Automatic honor events

| Event | Honor |
|---|---|
| Kill a dishonored player | +25 (+35 if you are a guard), anywhere |
| Kill an honorable/neutral player who hit you first within 15 s | 0, no review |
| Kill an unarmed, unarmored honorable/neutral player | -60 |
| Kill an honorable/neutral player who did not hit first | -30, sent to judge review |
| Hours online in a day without losing honor | Off by default (`onlineBonusHonor` 0, `onlineBonusHours` 3). Set `onlineBonusHonor` above 0 to give that much once per day |
| Donate items with `/donate` | 1 per 10 points (bread = 5 points, so a stack of bread = 32 honor; golden apple 40, enchanted golden apple 300, totem 160, ender pearl 5), leftover points carry over per type, max 100 a day in total, plus per-type daily caps (food 60, golden apple 20, enchanted golden apple 30, totem 32, ender pearl 16, common materials 30, rare materials 40; see below); items past a cap are still accepted and shared, they just stop earning honor. Judges earn none. Shown as "Donated items" in `/honor log` |
| Cure a zombie villager (vanilla cure trigger) | +10, max 30 a day |
| Harvest a fully grown wheat, carrot, potato, beetroot or nether wart by hand | +1 per 64 harvested, max 10 a day (pistons, water, villagers, explosions, creative don't count) |
| Breed animals (vanilla bred-animals trigger) | +1 per 5 breeds, max 5 a day |
| Trade with villagers or wandering traders (vanilla trade trigger) | +1 per 10 trades, max 5 a day |
| Win a raid (Hero of the Village trigger) | +15 |
| Help kill the Wither, Ender Dragon or Warden (damaged it during the fight) | +15 each, once per boss type per day |
| Kill a villager or wandering trader (you or your projectile) | -15 |
| Kill another player's tamed animal | -20 (your own pet costs nothing) |
| Win a siege on your civ | +10 for each member who damaged siege mobs (judges excepted), see Sieges |

All of these are automatic: no judge, no case, not part of anti-farming, shown in `/honor log` with their reason, numbers and caps in the config (`zombieCureHonor`, `harvestPerCrops`, `bossKillHonor`, `petKillPenalty`, ...). Daily caps reset at midnight server time; judges are exempt.

Anti-farming: the same killer/victim pair counts once per 30 minutes. Kills between players on the same IP never count, never pay out a player-set bounty (the bounty stays on the target) and are flagged as a case. A victim with under 5 minutes of playtime since their last death never gives positive honor.

## Judges and judge rules

A judge is the highest honorable rank. Judges are picked, not ops: only players added with `/honor judge add` can rule on cases, fine, or open the treasury. Ops can run admin commands but cannot rule unless they are also judges.

- Honor rules do not apply to judges: no automatic honor changes at all, and `/honor` shows `Judge` instead of a score. They cannot appeal.
- Judges get exactly the Paragon perks (extra health, villager discount, potion and hunger bonuses, keep XP and hotbar on death, gold name, Bounty Compass) using the Paragon config values, and never the dishonored-side effects or any donor perks; `/honor` lists them.
- Killing a judge counts like an unprovoked kill of a Paragon for honor. A dishonored player who kills a judge also gets the Judge loot pool and a server-wide chat announcement. Anyone else killing a judge is a normal unprovoked kill.
- Judge loot pool (`judgeLoot` in the config, 5 rolls). Defaults: 8 to 12 diamonds, 2 netherite scrap, 1 totem of undying, 1 enchanted golden apple, 1 max level enchanted book.
- A judge can rule on any case, including one where they are the killer or victim.

## Cases, auto-decisions and appeals

The mod decides a kill by itself whenever it can tell what happened: who hit first (from the damage timeline), armor, held item and tiers. Only cases it really cannot decide go to a judge: no hits recorded between the pair (fire, lava, explosions), the victim hit the killer earlier in an older feud, or both players are in the same civ (could be a duel). Same IP kills are flagged as a case with no honor applied. For judge cases the provisional honor is applied at once and the judge can confirm, cancel or set a different amount.

`/appeal <reason>` appeals your most recent honor change, or `/appeal <id> <reason>` a specific change from your log. It creates an appeal case for the judges. One open appeal per player at a time, and each change can be appealed once.

Case books: when a case is created every judge gets a written book called `Case #N` with everything: who, what happened, damage timeline, gear, location and the mod's suggested ruling. A judge who is offline gets it on their next login. The Give book button in the case menu hands out the book again.

Set `discordWebhookUrl` in the config to post every new case and decision to Discord.

## Live Discord leaderboard

Set `leaderboardWebhookUrl` in the config (empty = off) and the mod keeps one Discord message up to date: the top 10 by honor, the bottom 5 (most dishonored), the top 5 civs, this week's top donor so far, and a footer with the update interval and the time. Judges are not listed.

- `leaderboardUpdateMinutes` (default 5) is how often it updates. It also updates right after a restart and skips an update when nothing changed.
- The first post creates the message and its id is saved in `data.json`; later updates edit that same message. If someone deletes it, the next update posts a new one.
- Requests run off the server thread with a timeout. A failure logs one warning line and is retried next interval. Discord rate limits (429) are respected. The webhook URL is never logged.
- `/honor reload` picks up a changed URL or interval.

## Trade cooldown (1.6.4)

Villager trades never run out (`noTradeCooldown`, default true), except when the item the villager gives is blocked: it then locks and restocks like vanilla. Blocked: everything in `tradeCooldownItems` (item ids or `#tag` ids; default is emeralds, diamonds, the `/donate` ores, ingots, blocks and gems, golden apples, golden carrots, totems, ender pearls, name tags, saddles, diamond and netherite gear), any enchanted tool, weapon or armor piece, and anything `/donate` accepts. `tradeCooldownAlwaysFree` (default enchanted books, experience bottles, ender pearls) is never limited and wins over everything. `/honor reload` applies changes.

## Chest menus

All menus are server-side chests that work on vanilla clients. Items in them are icons only: every click type, shift click, number key, drag and double click collect is cancelled, so nothing can be taken or duped.

- `/honor cases` opens a chest with one item per open case (paper, or a book for appeals) with the killer, victim, type and age. Clicking a case opens its menu: Confirm (green wool), Cancel (red wool), Set amount (sign), Rename (name tag), Fine, Give book and Back. Lists with more than 45 entries are paged with Previous and Next arrows in the bottom row. `/honor cases text` lists cases in chat instead.
- Set amount opens a sign editor where you type the number. Invalid input says so and reopens the case menu. Rename opens a sign editor for the case name (up to 40 characters, empty clears it). The name shows in `/honor cases`, the case book title and the Discord posts. You can also use `/honor case <id> rename <name>`.
- Every page opened from another page has a Back button (arrow, bottom left) that returns to the same list page. Top level lists have a Close button (barrier) instead. Cancelling a sign editor returns to the case menu. Chest screens that are all item slots (treasury, bounty set and claim, fines) have no button.
- `/bounty list` opens a chest with the head of every player who has a bounty, with the item count in the lore. Clicking a head opens a read-only view of that bounty's loot, with a Back button, paged 45 per screen.

## Fines

Judges can fine a player. `/fine <player>` (or the Fine button in a case menu) opens a chest. The judge puts in the items the fine consists of and closes it to confirm; closing it empty cancels. The judge's items are not taken, they only define the fine.

- The fined player is told in chat and again on next login. `/fine` shows their open fines.
- `/fine pay` opens a chest showing what is owed. Put the matching items in to pay. Items and counts must match exactly (durability is ignored on undamaged items, enchanted items must match enchants). Paid items go into the treasury.
- For every `fineIntervalHours` (default 2.5 real hours) a fine stays unpaid the player loses `fineHonorPenalty` (default 10) honor. This also runs while they are offline and catches up on login.
- Judges can cancel a fine from the case menu or with `/fine cancel <id>`. `/fine list` shows all open fines.

## Civilizations, sieges and the treasury

`/civ create` is free.

`/civ chest` (back in 1.6.6) opens a shared 54 slot chest for your civ: leader, officers, members and guards can put items in and take them out. Only one member can have it open at a time; others get "Someone in your civ is using the chest". Contents are saved to data.json (`chest` on each civ), and the screen closes with nothing lost or duplicated on death, disconnect, kick, disband and server stop. New civs, and civs that existed in 1.6.5, start with an empty chest (the old items were moved to the leaders' `/donation` boxes in 1.6.1 and are not moved back).

**Chest rows (1.6.8):** the chest has 6 rows. Row 1 is open once the chest is unlocked (3 members); rows 2 to 6 are bought once each, in order, in `/civ upgrades` ("Chest rows", the chest icon shows the next row's price). The leader or an officer buys, members can look, and there is no daily upkeep. Default prices, all in the config as `civChestRowCosts` (a list of item:count lists, row 2 first): row 2 32 iron ingots, row 3 32 gold ingots, row 4 64 redstone and 32 lapis lazuli, row 5 16 diamonds, row 6 2 netherite ingots. Never emeralds. The cost is taken from the buyer's inventory (plain items only, all or nothing, "Not enough X" otherwise) and goes to the judges' treasury. Locked rows show as gray panes named "Locked row, buy it in /civ upgrades"; nothing can be put in, taken out, shift clicked, number keyed or dragged into them. Items that were stored before 1.6.8 stay; stacks that no longer fit the open rows are moved to the leader's `/donation` box and the leader is told (nothing lost or duplicated). Disbanding takes the bought rows with the civ. `/civ info` shows "Civ chest: N of 6 rows".

**Minimum members (1.6.7):** `/civ chest` only works once the civ has at least `civChestMinMembers` members, leader included (default 3). A smaller civ gets "Your civ needs 3 members to unlock the civ chest (you have N)." If a civ drops below the minimum (someone leaves or is kicked) the chest locks again but keeps its items; anyone who has it open gets it closed safely, nothing lost or duplicated, and the items are there again when the civ is back at the minimum. `/civ info` shows "Civ chest: locked, needs 3 members" or "Civ chest: unlocked".

**Combat lock:** you cannot open the civ chest for `civChestCombatLockSeconds` (default 300 = 5 minutes, 0 turns it off) after another player hurt you: swords, arrows, tridents, potions, fireworks, TNT or end crystals set off by a player, and pets owned by a player. Mob damage and your own damage do not count. Trying early says "You were in a fight. The civ chest opens again in M:SS." If another player hits you while you have the chest open, it closes at once and the items stay exactly as they were. It is also blocked for a civ while its siege wave is running ("Your civ is under siege. The civ chest opens again when the raid is over.").

- **Civ chat:** `/cc` toggles civ chat. While it is on, your chat messages go only to online members of your civ, shown as `[CivName] <player> message`. Type `/cc` again to switch it off. It is not saved across restarts.
- **No friendly fire:** members of the same civ cannot hurt each other with melee, arrows, tridents, other projectiles or harmful potion effects, and cannot hurt each other's tamed pets. Your own pet and yourself are unchanged.
- **Civ leaderboard:** `/civ top` lists the top 10 civs by the total honor of their members (judges not counted).
- **Treasury:** paid fines go into the Treasury. Only judges can open it, with `/honor treasury` (chest UI).

## Sieges

Raid sieges (1.5.0) are random pillager attacks on civs. Only civs get them.

- **Home:** a civ's home is found from the overworld bed respawn points of its members. It needs at least 2 members whose beds are within `siegeHomeRadius` (100) blocks of each other. A civ without a home gets no siege.
- **When:** each civ gets a random siege every 2 to 4 real days (`siegeMinDays`, `siegeMaxDays`), and only when at least `siegeMinMembersOnline` (2) members are online within 100 blocks of home.
- **Warning:** 60 seconds (`siegeWarnSeconds`) before the first wave, members get a title and the chat message "A raid is coming to <civ> in 60 seconds!". The whole server gets "Pillagers are marching on <civ>!".
- **Waves:** 3 waves. Wave 1 pillagers and vindicators, wave 2 adds a witch and an evoker, wave 3 adds a ravager and a "Siege Captain" (`siegeCaptainHealth` 60). Mobs per wave scale with members online (`siegeMobsPerMember` 3, up to `siegeMaxMobsPerWave` 60) and spawn 40 to 60 blocks from home. The next wave starts `siegeWaveDelaySeconds` (20) after the last mob of the previous wave dies. A boss bar shows the progress.
- **Loss:** nobody from the civ within `siegeAbandonRadius` (150) blocks of home for `siegeAbandonSeconds` (120) seconds, or `siegeMaxMinutes` (15) passed. The remaining siege mobs are removed and the civ is told.
- **Win:** every member who damaged siege mobs gets +`siegeRewardHonor` (10) honor (judges are excepted), a loot chest appears at home (`siegeLoot`) and the server sees "<civ> defended their home!".
- **Judge commands:** `/honor siege <civ>` starts a siege right now with a 5 second warning (only if the civ has a home and members online), `/honor siege stop <civ>` ends a running siege (counts as a loss, no reward).
- **Config keys:** `siegesEnabled` (true), `siegeMinDays` (2), `siegeMaxDays` (4), `siegeHomeRadius` (100), `siegeMinMembersOnline` (2), `siegeWarnSeconds` (60), `siegeSpawnMinDistance` (40), `siegeSpawnMaxDistance` (60), `siegeMobsPerMember` (3), `siegeMaxMobsPerWave` (60), `siegeWaveDelaySeconds` (20), `siegeAbandonRadius` (150), `siegeAbandonSeconds` (120), `siegeMaxMinutes` (15), `siegeRewardHonor` (10), `siegeCaptainHealth` (60), `siegeLoot`.

## Bounties

A dishonored player who kills an honorable player gets a loot pool, not emeralds. The number of weighted rolls depends on the victim's tier: Honorable 2, Noble 3, Paragon 4. Pools are set in `bountyLoot` in the config. Defaults:

- Honorable: 8 to 16 emeralds, 8 iron ingots, 1 to 2 golden apples, 8 bottles o' enchanting, 16 arrows, 1 random enchanted book (level 1 to 2)
- Noble: 2 to 4 diamonds, 16 to 32 emeralds, 1 enchanted iron or diamond weapon, 2 to 4 golden apples, 1 random enchanted book (level 2 to 3), 1 stack of 4 ender pearls
- Paragon: 4 to 8 diamonds, 1 netherite scrap, 1 totem of undying, 1 enchanted diamond armor piece, 1 random enchanted book (max level), 1 enchanted golden apple (rare)

Loot goes into the killer's inventory; overflow drops at their feet.

Player bounties use chest UIs. `/bounty set <player>` opens a 27 slot chest. Items you put in become the bounty when you close it; closing it empty cancels. Only dishonored targets are allowed, not yourself or your own civ, and bounties from different players stack. When the target is killed, all bounty items go to the killer's bounty box, opened with `/bounty claim` (take only). `/bounty list` is a chest GUI of target heads with item counts (click a head to see the loot, read-only), no locations. If the bounty box was empty after a kill, that was a bug fixed in 1.2.0: bounties now always pay out on a kill and the claim box is saved across restarts. Items are saved when the UI closes, including on disconnect, death and server stop. A kill between players on the same IP does not pay out, the bounty stays on the target. Season resets do not clear bounties. Old 1.0.0 emerald bounties convert to emerald stacks.

Bounty Compass: Noble and up get one with `/honor tracker`, or craft it shapeless: 1 compass + 4 emeralds. It points at the nearest dishonored player with a bounty, refreshes every 5 s, and only works in the overworld and for Noble+ holders. With no target it spins and shows "No wanted players nearby" in the action bar instead of pointing at an old spot. If the holder drops below Noble the compass stops working and says "You must be Noble or higher to use this" (checked on use and on every update).

## Leaderboard

`/honor top` lists the top 10 only among players who are honorable right now, and the bottom 10 only among players who are dishonored right now. Someone who went from Marauder back to Neutral drops off the bottom list.

## Civ upgrades and the daily upkeep (1.6.0, upkeep since 1.6.5)

`/civ upgrades` opens a chest menu. The leader and officers buy (click, then confirm; the cost is taken from their inventory, plain items only), other members can open it to look. Each upgrade has 2 levels and applies to all online members everywhere. It stacks with rank perks (max health from rank and Fortitude add up). No emeralds are involved anywhere: every upgrade is bought and kept running with its own item.

| Upgrade | Level 1 | Level 2 | Item | Upkeep a day (L1 / L2) |
|---|---|---|---|---|
| Resistance | 5% less damage | 10% less damage | iron ingot | 16 / 32 |
| Strength | +0.5 attack damage | +1 attack damage | gunpowder | 16 / 32 |
| Speed | +5% speed | +10% speed | redstone dust | 32 / 64 |
| Haste | Haste I | Haste II | lapis lazuli | 16 / 32 |
| Fortitude | +2 max health | +4 max health | gold ingot | 8 / 16 |

- **Buying:** level 1 costs 4 days of that level's upkeep (`civBuyDays`), level 2 costs 4 days of the level 2 upkeep plus 8 diamonds (`civLevel2Diamonds`). Example: Speed 1 is 128 redstone, Speed 2 is 256 redstone and 8 diamonds.
- **Upkeep:** every owned upgrade level costs its item every day, whatever the civ's size. Due 24 hours after the first upgrade was bought, then 24 hours after each paid day. `/civ upkeep` shows what is owed per upgrade and until when. `/civ upkeep pay` (leader or officer) opens a chest like the fine payment: put the items in and close it, as many whole days as they cover are paid, the rest comes back, and the paid items go to the judges' treasury. You can pay up to `civUpkeepMaxAheadDays` (3) days ahead. The leader and officers get a warning on login and `civUpkeepWarningHours` (2) hours before it is due.
- **Unpaid:** when the upkeep is late all of the civ's upgrades switch off, they cannot buy more, and `[Unpaid]` shows in `/civ info` and `/civ list`. Only one day is ever owed however long it was late. Nobody loses honor. Paying turns the upgrades back on at once.
- **Disband:** upgrades and bought chest rows are lost, no refund.
- **Config:** `civUpgradesEnabled`, `civUpgradeItems` (upgrade id to item id), `civUpkeepPerDay` (upgrade id to [level 1, level 2]), `civBuyDays`, `civLevel2Diamonds`, `civUpkeepWarningHours`, `civUpkeepMaxAheadDays`, `upgradeResistancePercent`, `upgradeStrengthBonus`, `upgradeSpeedPercent`, `upgradeHasteLevel`, `upgradeFortitudeHp` (arrays: level 1, level 2). The old `upgradeEmeraldCost`, `upgradeDiamondCost` and tax keys are removed from the config on load.
- **Existing civs** keep the upgrades they own, with the first upkeep due 24 hours after 1.6.5 first loads. The old weekly tax fields in data.json are dropped.

## Donor perks (1.6.0)

Each player has a lifetime total of honor earned from donating (`donatedHonorTotal`). On the first load of 1.6.0 it is filled in from the honor log entries "Donated items" and "Donated food", so existing donors keep their credit. Judges get no donor perks.

| Tier | Lifetime donation honor | Perks |
|---|---|---|
| Supporter | 200 (`donorSupporterMin`) | `[Supporter]` tag after the rank tag in chat; hunger drains 20% slower (`donorHungerSlowPercent`), added to the rank perk, never above 50% in total (`donorHungerCapPercent`) |
| Patron | 750 (`donorPatronMin`) | Permanent Luck +1 (`donorLuckBonus`) |
| Benefactor | 2000 (`donorBenefactorMin`) | Regeneration I for 30 seconds on every respawn (`donorRespawnRegenSeconds`); gold `[Benefactor]` tag instead of Supporter |

Weekly top donor (`topDonorEnabled`): every Monday 00:00 server time the player who earned the most donation honor in the past 7 days gets Hero of the Village I for `topDonorHeroDays` (7) days, and the server is told in chat. Ties go to whoever reached the amount first. `/honor` and the `/donate` info item show your lifetime donation honor, your tier and how much is left to the next one.

## Civilization command replies

`/civ upgrades` (1.6.0) opens the civ upgrade menu (leader/officers buy, members can look); `/civ upkeep` shows the daily upgrade upkeep and `/civ upkeep pay` (leader/officer) pays it into the judges' treasury. Late upkeep switches all upgrades off and shows `[Unpaid]` in `/civ info` and `/civ list` until paid.


`/civ promote` on an officer says "Already an officer" and changes nothing. `/civ guard` is a toggle and says whether the player became or stopped being a guard. `/civ invite` on someone already in your civ says they are already a member.

## Nerfs (apply to everyone)

- Enchant caps (`enchantCaps` in the config, a map of enchant id to max level). Defaults: Sharpness 4, Protection 3, Density 4, Breach 3. Items above a cap (books, weapons, armor, tools, from anvils, enchanting tables, loot, villagers, fishing, inventories on login or pickup) are lowered to the cap. Only that enchant changes, every other enchant stays the same.
- No netherite swords, axes or armor. A smithing table upgrade to one is cancelled: you get the same diamond item back (same enchants, durability and name) plus the netherite upgrade template and the netherite ingot. Netherite pickaxes, shovels and hoes are still allowed.
- Any netherite sword, axe or armor piece that shows up anyway (bastion loot, old items, pickups, chests you open) turns into the diamond version with the same enchants, durability and name, and you get one netherite ingot.
- Civ chest rows (1.6.8): `civChestRowCosts`, a list of item:count lists for rows 2 to 6 (defaults: 32 iron ingots; 32 gold ingots; 64 redstone + 32 lapis lazuli; 16 diamonds; 2 netherite ingots). Never emeralds.
- Civ chest minimum members (1.6.7): `civChestMinMembers` (default 3, leader included); below it `/civ chest` is locked and the items stay inside.
- Civ chest combat lock (1.6.6): `civChestCombatLockSeconds` (default 300) after another player hurt you, `/civ chest` will not open; 0 disables it.
- Elytra combat lock: for `combatElytraSeconds` (default 30) after taking damage from a player an elytra cannot open, and the action bar tells you. 0 disables it.

## Commands

Players
- `/honor` score (or `Judge`), tier, perks, last 10 changes
- `/honor top` top 10 and bottom 10 (honorable and dishonored only)
- `/honor tracker` get a Bounty Compass (Noble+)
- `/civ create <name>` (free), `invite <player>`, `join <name>`, `leave`, `kick <player>`, `promote <player>`, `guard <player>` (toggle), `chest`, `disband`, `info [name]`, `list`, `top`
- `/cc` toggle civ chat
- `/bounty set <player>` (chest UI), `/bounty claim`, `/bounty list` (chest UI)
- `/appeal <reason>` or `/appeal <id> <reason>`
- `/fine` (your open fines), `/fine pay [id]` (chest UI)
- `/donate` (chest UI; extra words like `/donate food` just open the chest): put in plain food (anything edible plus cake), golden apples, enchanted golden apples, totems of undying or ender pearls (config `donateExtraItems`, item id to points), then Confirm. Items with a custom name, enchantments or other changed components are refused; rotten flesh, spider eyes, poisonous potatoes, pufferfish and suspicious stew too. 1 honor per `honorPerHungerPoints` (10) points, rounded down, so a stack of bread is 32 honor; leftover points carry over per type. Since 1.6.0 you can also donate ores, ingots and blocks (points in `donateExtraItems`, cap type in `donateCapTypes`, both configurable): common materials (cap `common_materials`, 30 honor a day): coal 1, coal block 9, raw copper / copper ingot 1, copper block / raw copper block 9, raw iron / iron ingot 5, iron block / raw iron block 45, raw gold / gold ingot 5, gold block / raw gold block 45, redstone 1, redstone block 9, lapis lazuli 2, lapis block 18, nether quartz 1, quartz block 4, amethyst shard 1; rare materials (cap `rare_materials`, 40 honor a day): diamond 50, diamond block 450, netherite scrap 150, netherite ingot 600, netherite block 5400. Silk-touched ore blocks (stone, deepslate and nether variants) count as what they drop (coal ore = coal, iron ore = raw iron, copper ore = raw copper, gold / nether gold ore = raw gold, redstone ore = redstone, lapis ore = lapis lazuli, quartz ore = quartz, diamond ore = diamond, ancient debris = netherite scrap). Same rules as food: plain items only, the overall daily cap still applies, items past a cap are still shared. On first load of an older config the missing entries are added (`donateMaterialsVersion`); existing edits are never overwritten. Daily cap `donateDailyCap` (100 honor) plus per-type caps in `donateItemDailyCaps` (food combined 60, `golden_apple` 20, `enchanted_golden_apple` 30, `totem_of_undying` 32, `ender_pearl` 16; -1 or a missing type means no cap). Past a cap you can still donate, you just earn no honor, and the items are still shared. The info item in the chest's bottom row lists each type's remaining honor today and what the chest would give. Judges can donate but earn no honor. Confirm shares the items equally between every player who has ever joined (except you). What does not split evenly is handed out by rotation: the extra items go to different players each time, so everyone gets the same amount over time. Recipients who are online are told "You got ... from <donor>'s donation. Use /donation to claim it." With nobody else on the server nothing is taken. Everyone online gets a broadcast. Closing without confirming, disconnect, death and server stop return the items.
- `/donation` (chest UI): your share of donations, take only, paged. Same dupe protection as the bounty chest. Players with items waiting are told on login; with nothing waiting it says "Nothing to claim right now...". Data: `donateBoxes` in data.json (old `donatePots` are emptied on load).

Judges (added with `/honor judge add`)
- `/honor treasury`
- `/honor siege <civ>`, `/honor siege stop <civ>`
- `/honor cases` (chest GUI with the case menu), `/honor cases text`, `/honor case <id>`, `/honor case <id> confirm | cancel [reason] | set <amount> [reason] | rename <name>`
- `/fine <player>` (chest UI), `/fine list`, `/fine cancel <id>`
- `/honor give <player> <amount> <reason>`
- `/honor log <player>`

Admins (ops, no judge powers unless also judges)
- `/honor set <player> <score>`
- `/honor judge add|remove <player>`
- `/honor season reset` (run twice within 10 s; backs up the data file first)
- `/honor reload`
- `/honor reset all` (run twice within 10 s; backs up the data file first). Wipes every score, civ, bounty, bounty box, case, appeal, fine, treasury, log and anti-farm entry. Judges stay judges. Items in bounty boxes, the treasury and open chest screens are deleted, not returned. Online players are updated at once and the server is told in chat.

## Notes

- Changes in 1.6.8: the civ chest has 6 rows; row 1 opens with the chest (3 members) and rows 2 to 6 are bought once each, in order, in `/civ upgrades`. New config key `civChestRowCosts`, new data.json fields `chestRows` (per civ) and `civChestOverflowNotices`. Old chests keep their items; what no longer fits goes to the leader's `/donation` box.
- Changes in 1.6.7: `/civ chest` needs a civ of at least 3 members (new config key `civChestMinMembers`, default 3). Below that it is locked but keeps its items, an open chest is closed safely when the civ drops below, and `/civ info` shows whether the chest is locked or unlocked.
- Changes in 1.6.6: `/civ chest` is back (shared 54 slot civ chest, empty at start, saved in data.json) with a combat lock: `civChestCombatLockSeconds` (default 300, 0 = off) after being hurt by another player, closes at once if you are hit while it is open, and blocked during your civ's siege wave. New config key `civChestCombatLockSeconds`; no data migration.
- Changes in 1.6.5: no emeralds in the economy. Emeralds and emerald blocks (and emerald ores) are no longer donatable (an existing config loses them once, `donateMaterialsVersion` 2). The weekly civ tax is gone (`/civ tax` is replaced by `/civ upkeep` and `/civ upkeep pay`); upgrades cost their own item daily and are bought with 4 days of it (level 2 plus 8 diamonds). See the civ upgrades section.
- Changes in 1.6.2: donations never go to the judges' treasury (it holds only paid fines and the civ upgrade upkeep). On first load, donatable stacks still in the treasury (any food, golden apples, totems, ender pearls, ores, ingots and blocks) are shared out into everyone's `/donation` box like a new donation; emeralds and stacks with changed components stay. Runs once (`treasuryDonationsMoved` in data.json). No new config keys.
- Changes in 1.6.0: stronger honorable perks (+2/4/6 max health, 10/20/30% cheaper trades, +20/35/50% potions, 15/25/35% slower hunger, keep XP from Noble, keep hotbar and offhand and gold name at Paragon), civ upgrades and the weekly upgrade tax (`/civ upgrades`, `/civ tax`, `/civ tax pay`), donor tiers and the weekly top donor, judges get the Paragon perks, and donating ores, ingots and ore blocks (`common_materials` cap 30 a day, `rare_materials` cap 40 a day). New config keys: `healthBonusHp`, `keepXpMinRank`, `keepHotbarMinRank`, `goldNameMinRank`, `perksVersion`, the civ upgrade and tax keys and donor keys listed above, `donateCapTypes`, `donateMaterialsVersion`. New data.json fields: per-civ upgrade levels and tax dates, per-player `donatedHonorTotal`, weekly top donor state.
- Admin notes for 1.6.0: the rank perk values changed. On first load (`perksVersion` 0 to 1) `potionBonusPercent`, `tradeDiscountPercent` and `hungerSlowPercent` are replaced by the new values only if they are still exactly the old defaults (15/30/50, 5/10/15, 10/15/20); anything you edited is kept. `healthBonusHp` is added. The material entries are added to `donateExtraItems` and `donateCapTypes` only where missing (`donateMaterialsVersion`), and the new cap types are added to `donateItemDailyCaps`. Old data loads unchanged and the donor backfill runs once.
- Known limit: items kept on death (XP and hotbar) are held in memory until the player respawns. If the server restarts while a player is on the death screen, those kept items are lost.
- Changes in 1.5.0: civ taxes removed (`taxPerMember`, `taxIntervalDays`, `taxWarnHours`, `taxLatePenaltyHonor` and the per-civ tax fields in data.json are gone; no more `[Unpaid]`), `/cc`, no friendly fire, `/civ top`, `/civ chest` (removed again in 1.6.1), sieges (`siege*` keys; the next siege time is kept per civ as `nextSiegeAt` and members' bed positions in `respawnPoints` in data.json), donation rotation, new donation numbers and per-type caps. New config keys: `siegesEnabled`, `siegeMinDays`, `siegeMaxDays`, `siegeHomeRadius`, `siegeMinMembersOnline`, `siegeWarnSeconds`, `siegeSpawnMinDistance`, `siegeSpawnMaxDistance`, `siegeMobsPerMember`, `siegeMaxMobsPerWave`, `siegeWaveDelaySeconds`, `siegeAbandonRadius`, `siegeAbandonSeconds`, `siegeMaxMinutes`, `siegeRewardHonor`, `siegeCaptainHealth`, `siegeLoot`, `donateItemDailyCaps`, `donateRewardsVersion`.
- Config migration in 1.5.0: on first load `honorPerHungerPoints` 20 becomes 10 and `donateDailyCap` 25 becomes 100, each only if it still has the old value (a changed value is kept); `donateRewardsVersion` is then set to 1. Missing `donateItemDailyCaps` entries are added with their defaults. Old `donatePots` contents are moved into the players' `donateBoxes` at startup. The file is re-saved.
- Config keys added in 1.4.0: `donateExtraItems` (item id to points: golden apple 40, enchanted golden apple 300, totem of undying 160, ender pearl 5), `zombieCureHonor` (10), `zombieCureDailyCap` (30), `harvestPerCrops` (64), `harvestHonor` (1), `harvestDailyCap` (10), `breedPerBreeds` (5), `breedHonor` (1), `breedDailyCap` (5), `tradePerTrades` (10), `tradeHonor` (1), `tradeDailyCap` (5), `raidWinHonor` (15), `bossKillHonor` (15), `villagerKillPenalty` (15), `petKillPenalty` (20). `donateZeroFoods` is now the refused item list for all donations.
- 1.4.0 data.json fields: `donateBoxes`, `donatePots` (emptied by 1.5.0), and per-player counters for the automatic events. Older data loads unchanged.
- Limits: boss fight damage tracking is in memory, so a server restart in the middle of a boss fight forfeits that fight's rewards. Creative and spectator players earn no harvest honor. Harvest, breed and trade progress carries over days; only the honor earned counts against the daily cap.
- Config keys added in 1.3.0: `honorPerHungerPoints` (20, now 10), `donateDailyCap` (25, now 100), `donateCakeHunger` (14, cake has no food component), `donateZeroFoods`. `onlineBonusHonor` now defaults to 0 (off), `onlineBonusHours` stays 3.
- Config keys added in 1.2.0: `judgeLoot`, `fineIntervalHours`, `fineHonorPenalty`, `enchantCaps`, `combatElytraSeconds`.
- Admin commands check the op list and judge commands check the judge list in code, so no permission mod is needed.
- There is no land claiming. Old claim data in `data.json` is dropped on load.
- Honor tests: `./gradlew test`.
- Changes in 1.6.4: trade cooldown with a blocklist (`noTradeCooldown`, `tradeCooldownItems`, `tradeCooldownAlwaysFree`); leaderboard Bottom 5 fix. See above.
- Changes in 1.6.3: live Discord leaderboard (`leaderboardWebhookUrl`, `leaderboardUpdateMinutes`). See above.
