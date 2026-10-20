# Changelog

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
