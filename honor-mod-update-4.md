# HonorSMP update 1.3.0

Change the existing HonorSMP mod in this folder (~/AI Everything/MC Mods/Honor Mod). Keep everything else as it is.

## How to work

Read README.md and this file. Use delegate_task for the work with the exact files it touches. Subagents reply with a short summary only. Commit after each change passes `./gradlew build`. After a compaction, check `git log --oneline` and continue. Don't stop halfway.

## 1. /donate

- `/donate` opens a 27 slot chest UI (same dupe-safe chest code as the bounty chest, with the bottom row reserved for a Confirm button (green wool), a Back/Close button and an info item showing how much honor the current contents are worth and how much the player can still earn today).
- Only food can go in (anything with a food component, plus cake). Anything else is refused and stays in the player's inventory.
- Honor is based on the food's hunger value: 1 honor per 20 hunger points donated, rounded down (a stack of bread is 64 x 5 = 320 hunger = 16 honor). Leftover hunger points carry over to the player's next donation so nothing is wasted.
- Daily cap: at most 25 honor a day from donating (real day, resets at midnight server time). Once the cap is hit, the chest says so and refuses more food. Rotten flesh, spider eyes, poisonous potatoes, pufferfish and suspicious stew are worth 0 so they can't be farmed.
- Clicking Confirm takes the food, gives the honor through the normal honor change path (logged as "Donated food"), and puts the food into the judges' treasury. Closing the chest without confirming gives everything back. Disconnect or crash with it open: items go back to the player like the other chests.
- Judges can't use /donate (honor doesn't apply to them).
- Every number goes in the config (honorPerHungerPoints = 20, donateDailyCap = 25, the zero-value food list).
- Tests: honor math with carry-over, daily cap, zero-value foods, non-food refused.

## 2. Turn off the playtime bonus by default

Honor should only come from PvP, judge awards and now donations. Change the config defaults so the "hours online" bonus is off (onlineBonusHonor = 0) and don't create a +0 log entry when it's 0. The live server already has it set to 0.

## Also

- README, CHANGELOG (1.3.0) and discord-posts.md (add /donate to commands and a short line in how-it-works under ways to gain honor) updated. No dashes as punctuation in the Discord posts.
- Bump to 1.3.0 and build. Start it on a local test server with 1.2.1 data and check it loads.
- Commit as Hector, no Co-Authored-By or "Generated with Claude" lines. Push to origin main at the end.

## Reporting

Only message for a question, an error you can't fix, or when done. Final report: what changed, what wasn't tested, where the jar is. No background process may still be running at the end.
