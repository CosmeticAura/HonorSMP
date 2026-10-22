package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 1.6.1: civ chests are removed and every stack still in one moves to the civ leader's /donation box. */
class CivChestMigrationTest {
    // 1.6.0-style data: "Judge" has bread and more in its chest, "Empty" has no chest, "Ghost" has an
    // empty chest list and a leader (u9) who is not in players (never logged in since), "Open" is a
    // chest that was open at shutdown (saveData wrote the live screen into it) and its leader already
    // has items waiting in /donation.
    private static final String DATA = """
            {
              "version": 2,
              "players": { "u1": { "uuid": "u1", "name": "Hector" } },
              "civs": {
                "c1": {"id": "c1", "name": "Judge", "leader": "u1", "members": ["u1", "u2"], "created": 1,
                       "chest": [ {"id": "minecraft:bread", "count": 64}, {"id": "minecraft:bread", "count": 64},
                                  {"id": "minecraft:bread", "count": 17}, {"id": "minecraft:diamond", "count": 3},
                                  {"id": "minecraft:diamond_sword", "components": {"minecraft:damage": 12}} ]},
                "c2": {"id": "c2", "name": "Empty", "leader": "u3", "members": ["u3"], "created": 2},
                "c3": {"id": "c3", "name": "Ghost", "leader": "u9", "members": ["u9"], "created": 3, "chest": []},
                "c4": {"id": "c4", "name": "Open", "leader": "u4", "members": ["u4", "u5"], "created": 4,
                       "chest": [ {"id": "minecraft:iron_ingot", "count": 40} ]},
                "c5": {"id": "c5", "name": "Never", "leader": "u8", "members": ["u8"], "created": 5,
                       "chest": [ {"id": "minecraft:cobblestone", "count": 64} ]}
              },
              "donateBoxes": { "u4": [ {"id": "minecraft:iron_ingot", "count": 5} ] },
              "donateNotices": { "u4": [ "You got 3 apple from X's donation. Use /donation to claim it." ] },
              "civChestNotices": { "u7": 12 }
            }
            """;

    private static long sum(List<JsonElement> l) {
        long n = 0;
        for (JsonElement e : l) n += e.getAsJsonObject().has("count") ? e.getAsJsonObject().get("count").getAsLong() : 1;
        return n;
    }

    @Test
    void everyStackMovesToTheLeadersBox() {
        HonorData d = HonorData.parse(DATA);
        var r = CivChestMigration.run(d);

        // Judge: all 5 stacks (64+64+17 bread, 3 diamonds, 1 sword) land in u1's box, in order
        List<JsonElement> box = d.donateBoxes.get("u1");
        assertEquals(5, box.size());
        assertEquals(64 + 64 + 17 + 3 + 1, sum(box));
        assertEquals("minecraft:bread", box.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals(12, box.get(4).getAsJsonObject().getAsJsonObject("components").get("minecraft:damage").getAsInt());

        // "Open": the open-at-shutdown contents are added to what the leader already had, nothing replaced
        assertEquals(2, d.donateBoxes.get("u4").size());
        assertEquals(45, sum(d.donateBoxes.get("u4")));
        // a leader who never logged in since still gets the box (it is keyed by uuid only)
        assertEquals(64, sum(d.donateBoxes.get("u8")));
        assertFalse(d.players.containsKey("u8"));

        // totals: 5 + 1 + 1 stacks over 3 civs
        assertEquals(7, r.stacks());
        assertEquals(149 + 40 + 64, r.items());
        assertEquals(3, r.civs());
        assertEquals(8, d.donateBoxes.values().stream().mapToInt(List::size).sum(), "7 moved + 1 already waiting");
    }

    @Test
    void chestDataIsDroppedAndNotWrittenAgain() {
        HonorData d = HonorData.parse(DATA);
        CivChestMigration.run(d);
        for (HonorData.Civ c : d.civs.values()) assertNull(c.chest, c.name);
        String out = Honor.GSON.toJson(d);
        assertFalse(out.contains("\"chest\""), out);
        assertFalse(out.contains("civChestNotices"), out);
        assertEquals(5, d.civs.size()); // the civs themselves are untouched
    }

    @Test
    void leadersWithItemsAreToldOnLogin() {
        HonorData d = HonorData.parse(DATA);
        CivChestMigration.run(d);
        for (String leader : List.of("u1", "u4", "u8")) {
            assertTrue(d.donateNotices.get(leader).contains(CivChestMigration.NOTICE), leader);
        }
        assertEquals("Your civ chest was removed. Its items are in /donation.", CivChestMigration.NOTICE);
        // the earlier notice u4 had is kept
        assertEquals(2, d.donateNotices.get("u4").size());
        // empty chests and civs without a chest say nothing and create no box
        assertNull(d.donateNotices.get("u3"));
        assertNull(d.donateNotices.get("u9"));
        assertNull(d.donateBoxes.get("u3"));
        assertNull(d.donateBoxes.get("u9"));
        // a 1.5.0 disband notice that had not been shown yet survives as a donation notice
        assertEquals(1, d.donateNotices.get("u7").size());
        assertTrue(d.donateNotices.get("u7").get(0).contains("12 items"));
    }

    @Test
    void secondRunMovesNothing() {
        HonorData d = HonorData.parse(DATA);
        CivChestMigration.run(d);
        String once = Honor.GSON.toJson(d);
        var r = CivChestMigration.run(d);
        assertEquals(0, r.stacks());
        assertEquals(once, Honor.GSON.toJson(d)); // no duplicate stacks, no duplicate notice

        // and the same after a save and reload, as happens on the next start
        HonorData back = HonorData.parse(once);
        assertEquals(0, CivChestMigration.run(back).stacks());
        assertEquals(5, back.donateBoxes.get("u1").size());
        assertEquals(1, back.donateNotices.get("u1").size());
    }

    @Test
    void emptyChestsAndNoChestsChangeNothing() {
        HonorData d = HonorData.parse("""
                {"civs": {"a": {"id": "a", "name": "A", "leader": "u1", "members": ["u1"], "chest": []},
                          "b": {"id": "b", "name": "B", "leader": "u2", "members": ["u2"]}}}
                """);
        var r = CivChestMigration.run(d);
        assertEquals(0, r.stacks());
        assertTrue(d.donateBoxes.isEmpty());
        assertTrue(d.donateNotices.isEmpty());
        assertNull(d.civs.get("a").chest);
    }

    @Test
    void oneLeaderWithTwoCivsKeepsBoth() {
        HonorData d = HonorData.parse("""
                {"civs": {"a": {"id": "a", "name": "A", "leader": "u1", "members": ["u1"], "chest": [{"id": "minecraft:bread", "count": 10}]},
                          "b": {"id": "b", "name": "B", "leader": "u1", "members": ["u1"], "chest": [{"id": "minecraft:bread", "count": 20}]}}}
                """);
        CivChestMigration.run(d);
        assertEquals(30, sum(d.donateBoxes.get("u1")));
        assertEquals(1, d.donateNotices.get("u1").size()); // the same notice is not repeated
    }
}
