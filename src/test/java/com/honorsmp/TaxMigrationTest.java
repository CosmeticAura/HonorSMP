package com.honorsmp;

import static org.junit.jupiter.api.Assertions.*;

import com.honorsmp.logic.HonorConfig;
import org.junit.jupiter.api.Test;

/** 1.4.0 files that still carry tax fields must load, keep civs and treasury, and drop the tax fields on save. */
class TaxMigrationTest {
    private static final String OLD_DATA = """
            {
              "version": 2,
              "treasuryEmeralds": 0,
              "treasury": [ {"id": "minecraft:emerald", "count": 24}, {"id": "minecraft:diamond", "count": 3} ],
              "civs": {
                "c1": {"id": "c1", "name": "Alpha", "leader": "u1", "members": ["u1", "u2"], "created": 1000,
                       "taxDue": 99999, "taxPaid": true, "taxPenaltyDays": 2, "taxWarned": true},
                "c2": {"id": "c2", "name": "Beta", "leader": "u3", "members": ["u3"], "created": 2000,
                       "taxDue": 5, "taxPaid": false, "taxPenaltyDays": 0, "taxWarned": false}
              }
            }
            """;

    @Test
    void oldDataLoadsAndTaxFieldsAreNotWrittenBack() {
        HonorData d = HonorData.parse(OLD_DATA);
        assertEquals(2, d.civs.size());
        assertEquals("Alpha", d.civs.get("c1").name);
        assertEquals(2, d.civs.get("c1").members.size());
        assertEquals("Beta", d.civs.get("c2").name);
        assertEquals(2, d.treasury.size());
        assertEquals(24, d.treasury.get(0).getAsJsonObject().get("count").getAsInt());

        String out = Honor.GSON.toJson(d);
        assertFalse(out.toLowerCase().contains("tax"), out);
        HonorData back = HonorData.parse(out);
        assertEquals(2, back.civs.size());
        assertEquals(2, back.treasury.size());
    }

    @Test
    void oldConfigWithTaxKeysLoads() {
        HonorConfig c = Honor.GSON.fromJson(
                "{\"taxPerMember\": 8, \"taxIntervalDays\": 7, \"taxLatePenaltyHonor\": 10, \"taxWarnHours\": 24, \"civNameMaxLength\": 20}",
                HonorConfig.class);
        assertEquals(20, c.civNameMaxLength);
        assertFalse(Honor.GSON.toJson(c).toLowerCase().contains("tax"));
    }
}
