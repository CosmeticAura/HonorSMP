# Changelog

## 1.3.0
- New `/donate` for players (judges cannot use it): opens a 27 slot chest where only food goes in (anything edible plus cake). The bottom row has Back, Close, an info item (honor the contents are worth and honor still earnable today) and a green wool Confirm.
- 1 honor per 20 hunger points donated, rounded down (a stack of bread is 64 x 5 = 320 hunger = 16 honor). Leftover hunger points carry over to the next donation. Daily cap of 25 honor from donating, reset at midnight server time; once reached the chest says so and refuses more food.
- Rotten flesh, spider eyes, poisonous potatoes, pufferfish and suspicious stew are worth 0.
- Confirm takes the food, gives honor logged as "Donated food" and puts the food in the judges' treasury. Closing without confirming gives everything back; disconnect, death and server stop also return the items.
- New config keys `honorPerHungerPoints` (20), `donateDailyCap` (25), `donateCakeHunger` (14, cake has no food component) and `donateZeroFoods` (the five foods above).
- The playtime bonus is off by default: `onlineBonusHonor` now defaults to 0 (`onlineBonusHours` stays 3). With 0 it gives no honor and writes no log entry. Existing configs that set a value keep it. Honor now comes from PvP, judge awards and donations.

## 1.2.1
- Fixed the netherite smithing upgrade losing the upgrade template: the diamond item, the template and the ingot all come back. The found-netherite conversion path never takes a template.
- New `/honor reset all` (ops only, run twice within 10 s, backs up `data.json` first). Wipes honor, civs, taxes, bounties and bounty boxes, cases, appeals, fines, treasury, logs and anti-farm data. Judges stay. Items in bounty boxes, the treasury and open chest screens are deleted. Announced in chat.
- Judges can rule on any case, including ones they are part of.
- Cases can be renamed: Rename button (name tag) in the case menu or `/honor case <id> rename <name>`. The name shows in `/honor cases`, the case book and the Discord posts.
- Back button on every chest page opened from another page, keeping the list page. Top level lists have a Close button. Long lists are paged.
- A kill between players on the same IP no longer pays out a player-set bounty (it stays on the target) and is flagged as a case.

## 1.2.0
- Honor sidebar: each player sees their own tier and honor on the right of the screen (per-player, server-side).
- Judge tier: the highest honorable rank, gold tag above Paragon. No automatic honor changes for judges and `/honor` shows `Judge`. Killing a judge is an unprovoked Paragon kill for honor; a dishonored killer gets the new `judgeLoot` pool plus a server-wide announcement.
- Kills are decided automatically when the mod can tell who started the fight. Only unclear cases go to a judge. New `/appeal <reason>` (or `<id> <reason>`) for your latest or a chosen honor change, one open appeal at a time. Judges get a written `Case #N` book for each case (on next login if offline).
- Chest GUIs: `/honor cases` with a case menu (Confirm, Cancel, Set amount sign, Fine, Give book, Back), own cases greyed out, and `/bounty list` with heads and a read-only loot view. Items are icons only, no taking or duping.
- Fines: `/fine <player>`, `/fine`, `/fine pay`, `/fine list`, `/fine cancel <id>`. Paid items go to the treasury. Unpaid fines cost honor every `fineIntervalHours` (2.5), `fineHonorPenalty` (10), also offline. New config keys `fineIntervalHours` and `fineHonorPenalty`.
- Bounty Compass: no target shows "No wanted players nearby" and spins, and it stops working below Noble with "You must be Noble or higher to use this".
- `/honor top` shows only currently honorable players on top and currently dishonored players on the bottom.
- `/civ promote` says "Already an officer", `/civ guard` says what changed, `/civ invite` says the player is already a member.
- Fixed `/bounty claim` giving nothing after a bounty kill; the claim box now survives restarts.
- Enchant caps for everyone (`enchantCaps`): Sharpness 4, Protection 3, Density 4, Breach 3. Only the capped enchant is lowered.
- No netherite swords, axes or armor: smithing upgrades are cancelled and refunded, existing pieces convert to diamond with the same enchants, durability and name, plus one netherite ingot. Netherite pickaxes, shovels and hoes are allowed.
- Elytra combat lock: no opening an elytra for 30 seconds after player damage (`combatElytraSeconds`).

## 1.1.0
- Removed land claiming: no `/civ claim` or `/civ unclaim`, claim limits or storage. Removed the honor events for blocks, containers and villagers or golems in claims, and the sleep in claim rule. Dishonored downsides are now shorter potions and pricier villagers. Killing a dishonored player gives +35 to guards and +25 to everyone else, anywhere. Old claim data is dropped on load.
- `/civ create` is free. Civs pay a weekly tax to the judges (default 8 emeralds per member, every 7 days). `/civ tax` and `/civ tax pay` (leader or officer, early payment allowed). Late civs lose 10 honor per member per day and show [Unpaid]. Warnings on login and 24 hours before due. New config: `taxPerMember`, `taxIntervalDays`, `taxLatePenaltyHonor`, `taxWarnHours`.
- Paid tax goes to the Treasury, opened by judges with `/honor treasury`.
- Ops are no longer judges. Only players added with `/honor judge add` can use `cases`, `case`, `give`, `log` and the treasury. Ops keep admin commands. Judges cannot rule on cases they are part of.
- A dishonored player killing an honorable player gets a loot pool instead of emeralds (2/3/4 rolls for Honorable/Noble/Paragon victims), configurable with `bountyLoot`. Overflow drops at the killer's feet.
- Bounties use chest UIs. `/bounty set <player>` takes any items, stacks across players, dishonored targets only. Payouts go to a bounty box opened with `/bounty claim`. `/bounty list` shows item and stack counts. Escrow is saved on close, disconnect, death and server stop. Season reset keeps bounties. 1.0.0 emerald bounties become emerald stacks.

## 1.0.0
- First release: honor score and tiers, chat/tab prefixes, civilizations and claims, automatic honor events with anti-farming, judge review with Discord webhook, perks for both sides, bounties and the Bounty Compass, seasons and admin commands.
