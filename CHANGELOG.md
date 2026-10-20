# Changelog

## 1.1.0
- Removed land claiming: no `/civ claim` or `/civ unclaim`, claim limits or storage. Removed the honor events for blocks, containers and villagers or golems in claims, and the sleep in claim rule. Dishonored downsides are now shorter potions and pricier villagers. Killing a dishonored player gives +35 to guards and +25 to everyone else, anywhere. Old claim data is dropped on load.
- `/civ create` is free. Civs pay a weekly tax to the judges (default 8 emeralds per member, every 7 days). `/civ tax` and `/civ tax pay` (leader or officer, early payment allowed). Late civs lose 10 honor per member per day and show [Unpaid]. Warnings on login and 24 hours before due. New config: `taxPerMember`, `taxIntervalDays`, `taxLatePenaltyHonor`, `taxWarnHours`.
- Paid tax goes to the Treasury, opened by judges with `/honor treasury`.
- Ops are no longer judges. Only players added with `/honor judge add` can use `cases`, `case`, `give`, `log` and the treasury. Ops keep admin commands. Judges cannot rule on cases they are part of.
- A dishonored player killing an honorable player gets a loot pool instead of emeralds (2/3/4 rolls for Honorable/Noble/Paragon victims), configurable with `bountyLoot`. Overflow drops at the killer's feet.
- Bounties use chest UIs. `/bounty set <player>` takes any items, stacks across players, dishonored targets only. Payouts go to a bounty box opened with `/bounty claim`. `/bounty list` shows item and stack counts. Escrow is saved on close, disconnect, death and server stop. Season reset keeps bounties. 1.0.0 emerald bounties become emerald stacks.

## 1.0.0
- First release: honor score and tiers, chat/tab prefixes, civilizations and claims, automatic honor events with anti-farming, judge review with Discord webhook, perks for both sides, bounties and the Bounty Compass, seasons and admin commands.
