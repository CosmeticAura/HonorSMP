# HonorSMP

A server-side Fabric mod for Minecraft 1.21.11. Players join with a vanilla client. Every player has an honor score from -1000 to +1000. Honorable players build civilizations and get quality-of-life perks. Dishonored players get stealth, bounties and a combat edge. Clear cases are scored automatically; unclear kills go to a human judge.

## Install

1. Java 21 or newer, a Fabric server for 1.21.11 (loader 0.19.5 or newer) and Fabric API 0.141.6+1.21.11.
2. Put `honorsmp-1.2.1.jar` and Fabric API in the server's `mods/` folder.
3. Start the server. `config/honorsmp.json` is created with every number below. Edit it and run `/honor reload`.
4. Data is saved in the world folder at `honorsmp/data.json` (on every change, and on shutdown).

Build from source: `./gradlew build` (the jar is `build/libs/honorsmp-1.2.1.jar`).

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

Anti-farming: the same killer/victim pair counts once per 30 minutes. Kills between players on the same IP never count, never pay out a player-set bounty (the bounty stays on the target) and are flagged as a case. A victim with under 5 minutes of playtime since their last death never gives positive honor.

## Judges and judge rules

A judge is the highest honorable rank. Judges are picked, not ops: only players added with `/honor judge add` can rule on cases, fine, or open the treasury. Ops can run admin commands but cannot rule unless they are also judges.

- Honor rules do not apply to judges: no automatic honor changes at all, and `/honor` shows `Judge` instead of a score. They cannot appeal.
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

## Civilizations, taxes and the treasury

`/civ create` is free. Each civ pays a weekly tax to the judges: 8 emeralds per member, due every 7 real days from creation (`taxPerMember`, `taxIntervalDays`). `/civ tax` shows what is owed and when it is due. The leader or an officer pays with `/civ tax pay` from their inventory, and can pay early.

If a civ is late, every member loses 10 honor per day late (`taxLatePenaltyHonor`) and the civ shows `[Unpaid]` in `/civ info` and `/civ list`. The leader and officers are warned on login and 24 hours before the due time (`taxWarnHours`).

Paid tax and paid fines go into the Treasury. Only judges can open it, with `/honor treasury` (chest UI).

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
- `/civ create <name>` (free), `invite <player>`, `join <name>`, `leave`, `kick <player>`, `promote <player>`, `guard <player>` (toggle), `tax`, `tax pay` (leader or officer), `disband`, `info [name]`, `list`
- `/bounty set <player>` (chest UI), `/bounty claim`, `/bounty list` (chest UI)
- `/appeal <reason>` or `/appeal <id> <reason>`
- `/fine` (your open fines), `/fine pay [id]` (chest UI)

Judges (added with `/honor judge add`)
- `/honor treasury`
- `/honor cases` (chest GUI with the case menu), `/honor cases text`, `/honor case <id>`, `/honor case <id> confirm | cancel [reason] | set <amount> [reason] | rename <name>`
- `/fine <player>` (chest UI), `/fine list`, `/fine cancel <id>`
- `/honor give <player> <amount> <reason>`
- `/honor log <player>`

Admins (ops, no judge powers unless also judges)
- `/honor set <player> <score>`
- `/honor judge add|remove <player>`
- `/honor season reset` (run twice within 10 s; backs up the data file first)
- `/honor reload`
- `/honor reset all` (run twice within 10 s; backs up the data file first). Wipes every score, civ, tax, bounty, bounty box, case, appeal, fine, treasury, log and anti-farm entry. Judges stay judges. Items in bounty boxes, the treasury and open chest screens are deleted, not returned. Online players are updated at once and the server is told in chat.

## Notes

- Config keys added in 1.2.0: `judgeLoot`, `fineIntervalHours`, `fineHonorPenalty`, `enchantCaps`, `combatElytraSeconds`.
- Admin commands check the op list and judge commands check the judge list in code, so no permission mod is needed.
- There is no land claiming. Old claim data in `data.json` is dropped on load.
- Honor tests: `./gradlew test`.
