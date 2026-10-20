# HonorSMP update 1.2.1

Change the existing HonorSMP mod in this folder (~/AI Everything/MC Mods/Honor Mod). These come from Hector testing 1.2.0 on the real server. Keep everything else as it is.

## How to work

Read README.md and this file. Use delegate_task for each numbered item with the exact files it touches, one at a time. Subagents reply with a short summary only. Commit after each item passes `./gradlew build`. After a compaction, check `git log --oneline` and continue. Don't stop halfway.

## Changes

1. Netherite smithing bug: Hector put a diamond sword, a netherite upgrade template and a netherite ingot in a smithing table. He got the diamond sword and the ingot back but the template was lost. Whatever path this takes (blocked output, or the result being taken and then converted), the player must end up with all three: the same diamond item (enchants, durability, name), the template and the ingot. Write a test that reproduces the missing template first, then fix it. Check the found-netherite conversion path too, so it never eats a template.

2. Full reset: `/honor reset all` (ops only). Wipes everything: every player's honor, civs, taxes, bounties and bounty boxes, cases, appeals, fines, treasury, logs and anti-farm data. Judges stay judges. Run it twice within 10 seconds to confirm, and back up data.json to a timestamped file first. Items in bounty boxes, the treasury and open chest UIs are deleted, not returned (say so in the confirm message). Announce it in chat.

3. The judge can rule on any case, including cases they are part of. Remove the "You are part of this case" block and the greyed-out state. (Keep the JudgeRules tests updated.)

4. Rename cases: in the case menu add a "Rename" button (name tag icon) that opens the sign editor; the new name shows in /honor cases, the case book title and the Discord webhook. Also `/honor case <id> rename <name>`.

5. Back button everywhere: every chest GUI page that was opened from another page has a Back button (arrow, bottom left) that returns to the page it came from, keeping list scroll/page position. That includes case menu, set-amount sign (cancelling the sign returns to the case menu), rename sign, bounty list loot view, fine views, treasury if opened from a menu, and any paged lists. Top-level pages get a Close button (barrier) instead.

6. Bounty alt exploit: bounties now pay on any kill, so an outlaw could kill his own main with an alt on the same IP and cash in what others put on him. Kills between players on the same IP must not pay out a player-set bounty (the bounty stays on the target) and get flagged as a case. Every other kill still pays. Add a test.

## Also

- README, CHANGELOG (1.2.1), discord-posts.md (add /honor reset all, case rename, appeals note) updated. No dashes as punctuation in the Discord posts.
- Bump to 1.2.1 and build. Start it on a local test server with 1.2.0 data and check it loads.
- Commit as Hector after each item, no Co-Authored-By or "Generated with Claude" lines. Push to origin main at the end.

## Reporting

Only message for a question, an error you can't fix, or when done. Final report: what changed, what wasn't tested, where the jar is. No background process may still be running at the end.
