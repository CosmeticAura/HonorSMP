# HonorSMP update 1.1.0

Change the existing HonorSMP mod in this folder (~/AI Everything/MC Mods/Honor Mod). Read README.md and the code first. Keep everything not mentioned here as it is.

## 1. Remove land claiming completely

There is no land in this mod anymore.
- Delete /civ claim, /civ unclaim, the claim limits, claim storage, and the action bar message when entering a claim.
- Delete these honor events: breaking or placing blocks in another civ's claim, opening containers in another civ's claim, and killing a villager or iron golem in a claim.
- Delete the "cannot sleep in an honorable civ's claim" rule. Dishonored downsides are now only shorter potions and pricier villagers.
- Guards: a guard killing a dishonored player now gives +35 anywhere (was +35 only inside their own land). Non-guards still get +25.
- Remove any old claim data from data.json safely on load (don't crash on old files).

## 2. Civs are free and pay taxes

- /civ create <name> is free.
- Leaders can still invite, kick, promote officers, and name guards. Officers can invite and name guards.
- Taxes: every civ owes tax to the judges each week. Default 8 emeralds per member, due every 7 real days from when the civ was made. All numbers go in the config.
- /civ tax: shows how much is owed and when it's due.
- /civ tax pay: leader or officer pays from their inventory. They can pay early.
- Paid taxes go into a Treasury that only judges can open with /honor treasury (a chest UI, same rules as the bounty chest below). Judges use it for rewards.
- If a civ is late, every member loses 10 honor per day late until it's paid, and the civ shows as [Unpaid] in /civ info and /civ list. Warn the leader and officers in chat on login and 24 hours before it's due.

## 3. Judges are not automatic

- Ops are NOT judges by default anymore. Only players added with /honor judge add are judges.
- Ops can still run admin commands (set, judge add/remove, season reset, reload) but can't rule on cases or use the treasury unless they're also added as a judge.
- Judges can't rule on a case where they are the killer or the victim.

## 4. Bounty payouts become a loot pool

When a dishonored player kills an honorable player, the server gives them loot instead of a flat number of emeralds. The tier of the victim decides the pool. Roll a set number of items from a weighted list, all in the config so I can edit them. Defaults:

- Honorable victim (2 rolls): 8 to 16 emeralds, 8 iron ingots, 1 to 2 golden apples, 8 bottles o' enchanting, 16 arrows, 1 random enchanted book (level 1 to 2)
- Noble victim (3 rolls): 2 to 4 diamonds, 16 to 32 emeralds, 1 enchanted iron or diamond weapon, 2 to 4 golden apples, 1 random enchanted book (level 2 to 3), 1 ender pearl stack of 4
- Paragon victim (4 rolls): 4 to 8 diamonds, 1 netherite scrap, 1 totem of undying, 1 enchanted diamond armor piece, 1 random enchanted book (max level), 1 enchanted golden apple (rare weight)

Loot goes straight into the killer's inventory; anything that doesn't fit drops at their feet. Tell them in chat what they got.

## 5. Bounties use a chest UI

- /bounty set <player> opens a 27 slot chest UI. Whatever items you put in become the bounty when you close it. Closing it empty cancels.
- Only dishonored players can have bounties on them. You can't put a bounty on yourself or on a member of your own civ.
- Bounties stack: many players can add to the same target.
- When someone kills the target, all bounty items go into that killer's bounty box. They open it with /bounty claim (a chest UI they can take from but not put into). Tell them in chat they have a bounty to claim.
- /bounty list shows each target and how many items and stacks are on them, no locations.
- This must be dupe-proof. Handle shift clicking, number keys, dragging, the player disconnecting or dying with the UI open, and the server stopping with the UI open. Items are saved to the data file the moment the UI closes. Nothing may be lost or copied. Write tests for the escrow logic.

## 6. Also update

- README.md and CHANGELOG.md (version 1.1.0).
- discord-posts.md: update the how-it-works and commands posts to match (no land, free civs and taxes, judges are picked not ops, loot pool payouts, bounty chest, /civ tax, /bounty claim, /honor treasury). Keep the same short tone and don't use dashes as punctuation.
- Bump the version to 1.1.0 and build the jar.

## Testing

Run all tests. Start a local 1.21.11 Fabric server with the new jar and an old data.json from 1.0.0 to make sure it loads and migrates with no errors. Commit as Hector on main and push to the HonorSMP repo. No Co-Authored-By or "Generated with Claude" lines.

## Reporting

Only message me when you have a question, hit an error you can't fix, or finish. When done, send a short report: what changed, what you couldn't test, and where the jar is. Don't start any background process that is still running when you send the final report.

## How to work (important)

This chat compacts its memory at about 100k tokens, and the last attempt got stuck rereading files and compacting over and over without changing anything. So:
- Don't read every source file yourself. Read README.md and this file, then use delegate_task to give each numbered section above (1 to 5, then 6 and testing) to its own subagent, with the exact files it should touch. Run them one at a time, since they edit the same code.
- Each subagent reads only the files it needs, makes its change, runs `./gradlew build`, and replies with a short summary (under 15 lines), not file contents.
- Commit after each section passes the build, so progress is saved even if this chat compacts.
- Don't stop after a compaction. If you lose track, run `git log --oneline` and carry on with the next section that isn't committed.
