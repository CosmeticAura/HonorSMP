# HonorSMP update 1.2.0

Change the existing HonorSMP mod in this folder (~/AI Everything/MC Mods/Honor Mod). These come from Hector testing 1.1.0 on the real server. Keep everything not mentioned here as it is.

## How to work (important)

This chat compacts its memory at about 100k tokens. Don't read every source file yourself. Read README.md and this file, then use delegate_task to give each part below to its own subagent with the exact files it should touch, one at a time since they share code. Each subagent replies with a short summary, not file contents. Commit after each part passes `./gradlew build`. After a compaction, run `git log --oneline` and carry on with the next part that isn't committed. Never stop halfway.

## Part A: bugs and fixes from testing

1. Honor display: show each player their honor all the time. Use a sidebar scoreboard on the right side of the screen with their tier name and score (only they see their own; use a per-player objective or packets). Server-side only, vanilla clients.
2. Bounty Compass with no target: if no dishonored player with a bounty is in range, the compass should say "No wanted players nearby" in the action bar and spin or point nowhere, instead of pointing at something stale.
3. Bounty Compass rank lock: if the holder is no longer Noble or higher, the compass stops working and says "You must be Noble or higher to use this". Check on use and every update.
4. Leaderboard: /honor top bottom list must only show players who are actually dishonored right now. Someone who went from Marauder back to Neutral should drop off it. Check the top list the same way (only honorable players).
5. /civ promote on someone who is already an officer: reply "Already an officer" and change nothing. Same idea for guard (toggle is fine but say what happened) and invite (already a member).
6. Bounty claim is broken: Hector put a bounty on a dishonored player, killed them, and /bounty claim gave nothing. Find out why (write a test that reproduces it first), fix it, and make sure the claim box survives restarts.

## Part B: judges

7. A judge is the highest honorable rank. Show "Judge" as their tag in chat and tab (gold, above Paragon). Honor rules don't apply to judges: no automatic honor changes for them at all, and /honor shows "Judge" instead of a score.
8. Killing a judge counts like killing a Paragon but bigger: the judge is the server's boss. A dishonored player who kills the judge gets a special Judge loot pool (bigger than Paragon: for example 8 to 12 diamonds, 2 netherite scrap, a totem, an enchanted golden apple, a max level enchanted book; all in the config) and a server-wide chat announcement. Anyone else killing the judge is a normal unprovoked kill for honor purposes.
9. Decide kills automatically whenever the mod can tell what happened (who hit first, armor, held item, tiers). Only send a case to the judge when it really can't decide.
10. /appeal <reason>: any player can appeal their most recent honor change (or a specific log id with /appeal <id> <reason>). That creates a case for the judge. One open appeal per player at a time.
11. When a case is created, give the judge a written book for it (title "Case #N", pages with everything: who, what happened, damage timeline, gear, location, the mod's suggested ruling). If the judge is offline, give it when they next log in.

## Part C: chest GUIs (like DonutSMP)

All of these use server-side chest menus that work on vanilla clients. Items in them are icons only: players can't take or move them (cancel every click type, shift click, number keys, drag, double click collect). Same dupe safety as the bounty chest.

12. /honor cases opens a chest with one item per open case (paper or a book, name "Case #N", lore with killer, victim, type and age). Clicking one opens that case's menu: Confirm (green wool), Cancel (red wool), Set amount (sign), Fine (see 14), Give book (gives the case book again), Back.
13. Set amount opens a sign editor where the judge types the number (vanilla sign GUI through packets). Invalid input says so and reopens the case menu.
14. /bounty list opens a chest with the head of every player who has a bounty, with lore showing item count. Clicking a head opens a read-only view of that bounty's loot. Back button.
15. Judges can't rule on their own cases (already the rule). Show those cases greyed out with "You are part of this case".

## Part D: fines

16. /fine <player> (judge only) opens a chest UI. The judge puts in whatever items the fine is, then closes it to confirm (empty closes cancel). The judge's items are not taken; they're just the fine's contents.
17. The fined player is told in chat and on next login. /fine shows their open fines. /fine pay opens a chest showing what's owed; they put the matching items in to pay. Exact items and counts must match (ignore durability on undamaged items; enchanted items must match enchants). Paid items go into the judges' treasury.
18. For every 2.5 hours (real time, configurable) a fine stays unpaid, the player loses 10 honor (configurable). Applies while offline too, catch up on login.
19. Judges can cancel a fine from the case menu or with /fine cancel <id>.

## Part E: nerfs (apply to everyone)

20. Enchantment caps: Sharpness max 4, Protection max 3, Density max 4, Breach max 3 (all in the config as a map). Wherever an item has one of these above the cap (books, weapons, armor, tools, from anvils, enchanting tables, loot, villagers, trades, fishing, inventories when a player logs in or picks something up), lower ONLY that enchant to the cap. Every other enchant on the item stays exactly the same.
21. No netherite swords, axes or armor. If someone upgrades one at a smithing table, cancel it and give back the exact same diamond item (same enchants, durability, name), plus the netherite upgrade template and the netherite ingot. Netherite pickaxes, shovels and hoes are still allowed.
22. Any netherite sword, axe or armor piece that shows up anyway (bastion loot, old items, picked up, in a chest the player opens) turns into the diamond version with the same enchants, durability and name, and the player gets one netherite ingot.
23. No elytra in combat: for 30 seconds after taking damage from a player, elytra can't open. Tell them in the action bar. Configurable.

## Also

- README.md, CHANGELOG.md (1.2.0) and discord-posts.md (how-it-works and commands) updated to match. No dashes as punctuation in the Discord posts.
- Bump to 1.2.0 and build.
- Tests: bounty claim bug, enchant caps (only the capped enchant changes), netherite conversion keeps enchants and durability, fine timer, judge exemption, sign input parsing.
- Start the jar on a local test server with the 1.1.0 data to check it loads and migrates.
- Commit as Hector after each part, no Co-Authored-By or "Generated with Claude" lines. Push to origin main at the end.

## Reporting

Only message for a question, an error you can't fix, or when done. Final report: what changed, what wasn't tested, where the jar is. No background process may still be running when you send it.
