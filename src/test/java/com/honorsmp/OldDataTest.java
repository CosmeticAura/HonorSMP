package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class OldDataTest {
    @Test
    void oldDataWithClaimsLoadsAndDropsThem() {
        String json = "{\"version\":1,\"nextCaseId\":3,"
                + "\"players\":{\"u1\":{\"uuid\":\"u1\",\"name\":\"A\",\"score\":50,\"blockPenaltyToday\":7,\"containerPenaltyToday\":2}},"
                + "\"civs\":{\"c1\":{\"id\":\"c1\",\"name\":\"Rome\",\"leader\":\"u1\",\"members\":[\"u1\"]}},"
                + "\"claims\":{\"minecraft:overworld|1|2\":\"c1\"},\"claimLimits\":{\"c1\":9},"
                + "\"cases\":[{\"id\":1,\"killerName\":\"A\",\"land\":\"Rome\"}]}";
        HonorData d = HonorData.parse(json);
        assertEquals(50, d.players.get("u1").score);
        assertEquals("Rome", d.civs.get("c1").name);
        assertEquals(1, d.cases.size());
        String out = new Gson().toJson(d);
        assertFalse(out.contains("claim"));
        assertFalse(out.contains("land"));
    }
}
