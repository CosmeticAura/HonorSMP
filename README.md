# HonorSMP

A server-side Fabric mod for Minecraft 1.21.11. Players join with a vanilla client. Every player has an honor score from -1000 to +1000. Honorable players build civilizations and get quality-of-life perks. Dishonored players get stealth, bounties and a combat edge. Clear cases are scored automatically; unclear kills go to a human judge.

## Install

1. Java 21 or newer, a Fabric server for 1.21.11 (loader 0.19.5 or newer) and Fabric API 0.141.6+1.21.11.
2. Put `honorsmp-1.5.0.jar` and Fabric API in the server's `mods/` folder.
3. Start the server. `config/honorsmp.json` is created with every number below. Edit it and run `/honor reload`.
4. Data is saved in the world folder at `honorsmp/data.json` (on every change, and on shutdown).

Build from source: `./gradlew build` (the jar is `build/libs/honorsmp-1.5.0.jar`).

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

- **Civ chat:** `/cc` toggles civ chat. While it is on, your chat messages go only to online members of your civ, shown as `[CivName] <player> message`. Type `/cc` again to switch it off. It is not saved across restarts.
- **No friendly fire:** members of the same civ cannot hurt each other with melee, arrows, tridents, other projectiles or harmful potion effects, and cannot hurt each other's tamed pets. Your own pet and yourself are unchanged.
- **Civ chest:** see "Civilization command replies" below.
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

## Civilization command replies

`/civ upgrades` (1.6.0) opens the civ upgrade menu (leader/officers buy, members can look); `/civ tax` shows the weekly upgrade tax and `/civ tax pay` (leader/officer) pays it into the judges' treasury. Late tax switches all upgrades off and shows `[Unpaid]` in `/civ info` and `/civ list` until paid.

`/civ chest` (1.5.0) opens a shared 54 slot chest for your civ: leader, officers, members and guards can put items in and take them out. Only one member can have it open at a time; others get "Someone in your civ is using the chest". Contents are saved to data.json (`chest` on each civ) when the chest closes (also on disconnect, death, being kicked or leaving the civ, and server stop) and at least every 30 seconds while it is open; after a crash the last saved state wins, so moves in the last 30 seconds can be reverted. If the civ is disbanded, the chest contents go to the leader's `/donation` claim box (the leader is told, on login if offline). `/honor reset all` deletes chest contents with everything else.

`/civ promote` on an officer says "Already an officer" and changes nothing. `/civ guard` is a toggle and says whether the player became or stopped being a guard. `/civ invite` on someone already in your civ says they are already a member.

## Nerfs (apply to everyone)

- Enchant caps (`enchantCaps` in the config, a map of enchant id to max level). Defaults: Sharpness 4, Protection 3, Density 4, Breach 3. Items above a cap (books, weapons, armor, tools, from anvils, enchanting tables, loot, villagers, fishing, inventories on login or pickup) are lowered to the cap. Only that enchant changes, every other enchant stays the same.
- No netherite swords, axes or armor. A smithing table upgrade to one is cancelled: you get the same diamond item back (same enchants, durability and name) plus the netherite upgrade template and the netherite ingot. Netherite pickaxes, shovels and hoes are still allowed.
- Any netherite sword, axe or armor piece that shows up anyway (bastion loot, old items, pickups, chests you open) turns into the diamond version with the same enchants, durability and name, and you get one netherite ingot.
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
- `/donate` (chest UI; extra words like `/donate food` just open the chest): put in plain food (anything edible plus cake), golden apples, enchanted golden apples, totems of undying or ender pearls (config `donateExtraItems`, item id to points), then Confirm. Items with a custom name, enchantments or other changed components are refused; rotten flesh, spider eyes, poisonous potatoes, pufferfish and suspicious stew too. 1 honor per `honorPerHungerPoints` (10) points, rounded down, so a stack of bread is 32 honor; leftover points carry over per type. Since 1.6.0 you can also donate ores, ingots and blocks (points in `donateExtraItems`, cap type in `donateCapTypes`, both configurable): common materials (cap `common_materials`, 30 honor a day): coal 1, coal block 9, raw copper / copper ingot 1, copper block / raw copper block 9, raw iron / iron ingot 5, iron block / raw iron block 45, raw gold / gold ingot 5, gold block / raw gold block 45, redstone 1, redstone block 9, lapis lazuli 2, lapis block 18, nether quartz 1, quartz block 4, amethyst shard 1; rare materials (cap `rare_materials`, 40 honor a day): emerald 20, emerald block 180, diamond 50, diamond block 450, netherite scrap 150, netherite ingot 600, netherite block 5400. Silk-touched ore blocks (stone, deepslate and nether variants) count as what they drop (coal ore = coal, iron ore = raw iron, copper ore = raw copper, gold / nether gold ore = raw gold, redstone ore = redstone, lapis ore = lapis lazuli, quartz ore = quartz, emerald ore = emerald, diamond ore = diamond, ancient debris = netherite scrap). Same rules as food: plain items only, the overall daily cap still applies, items past a cap are still shared. On first load of an older config the missing entries are added (`donateMaterialsVersion`); existing edits are never overwritten. Daily cap `donateDailyCap` (100 honor) plus per-type caps in `donateItemDailyCaps` (food combined 60, `golden_apple` 20, `enchanted_golden_apple` 30, `totem_of_undying` 32, `ender_pearl` 16; -1 or a missing type means no cap). Past a cap you can still donate, you just earn no honor, and the items are still shared. The info item in the chest's bottom row lists each type's remaining honor today and what the chest would give. Judges can donate but earn no honor. Confirm shares the items equally between every player who has ever joined (except you). What does not split evenly is handed out by rotation: the extra items go to different players each time, so everyone gets the same amount over time. Recipients who are online are told "You got ... from <donor>'s donation. Use /donation to claim it." With nobody else on the server nothing is taken. Everyone online gets a broadcast. Closing without confirming, disconnect, death and server stop return the items.
- `/donation` (chest UI): your share of donations, take only, paged. Same dupe protection as the bounty chest. Players with items waiting are told on login; with nothing waiting it says "Nothing to claim right now...". Data: `donateBoxes` in data.json (old `donatePots` are emptied on load). Food donated before 1.4.0 stays in the treasury.

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

- Changes in 1.5.0: civ taxes removed (`taxPerMember`, `taxIntervalDays`, `taxWarnHours`, `taxLatePenaltyHonor` and the per-civ tax fields in data.json are gone; no more `[Unpaid]`), `/cc`, no friendly fire, `/civ top`, `/civ chest` (`chest` on each civ in data.json), sieges (`siege*` keys; the next siege time is kept per civ as `nextSiegeAt` and members' bed positions in `respawnPoints` in data.json), donation rotation, new donation numbers and per-type caps. New config keys: `siegesEnabled`, `siegeMinDays`, `siegeMaxDays`, `siegeHomeRadius`, `siegeMinMembersOnline`, `siegeWarnSeconds`, `siegeSpawnMinDistance`, `siegeSpawnMaxDistance`, `siegeMobsPerMember`, `siegeMaxMobsPerWave`, `siegeWaveDelaySeconds`, `siegeAbandonRadius`, `siegeAbandonSeconds`, `siegeMaxMinutes`, `siegeRewardHonor`, `siegeCaptainHealth`, `siegeLoot`, `donateItemDailyCaps`, `donateRewardsVersion`.
- Config migration in 1.5.0: on first load `honorPerHungerPoints` 20 becomes 10 and `donateDailyCap` 25 becomes 100, each only if it still has the old value (a changed value is kept); `donateRewardsVersion` is then set to 1. Missing `donateItemDailyCaps` entries are added with their defaults. Old `donatePots` contents are moved into the players' `donateBoxes` at startup. The file is re-saved.
- Config keys added in 1.4.0: `donateExtraItems` (item id to points: golden apple 40, enchanted golden apple 300, totem of undying 160, ender pearl 5), `zombieCureHonor` (10), `zombieCureDailyCap` (30), `harvestPerCrops` (64), `harvestHonor` (1), `harvestDailyCap` (10), `breedPerBreeds` (5), `breedHonor` (1), `breedDailyCap` (5), `tradePerTrades` (10), `tradeHonor` (1), `tradeDailyCap` (5), `raidWinHonor` (15), `bossKillHonor` (15), `villagerKillPenalty` (15), `petKillPenalty` (20). `donateZeroFoods` is now the refused item list for all donations.
- 1.4.0 data.json fields: `donateBoxes`, `donatePots` (emptied by 1.5.0), and per-player counters for the automatic events. Older data loads unchanged.
- Limits: boss fight damage tracking is in memory, so a server restart in the middle of a boss fight forfeits that fight's rewards. Creative and spectator players earn no harvest honor. Harvest, breed and trade progress carries over days; only the honor earned counts against the daily cap.
- Config keys added in 1.3.0: `honorPerHungerPoints` (20, now 10), `donateDailyCap` (25, now 100), `donateCakeHunger` (14, cake has no food component), `donateZeroFoods`. `onlineBonusHonor` now defaults to 0 (off), `onlineBonusHours` stays 3.
- Config keys added in 1.2.0: `judgeLoot`, `fineIntervalHours`, `fineHonorPenalty`, `enchantCaps`, `combatElytraSeconds`.
- Admin commands check the op list and judge commands check the judge list in code, so no permission mod is needed.
- There is no land claiming. Old claim data in `data.json` is dropped on load.
- Honor tests: `./gradlew test`.
